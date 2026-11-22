"""Behavioral coverage for development and publication changelog validation."""

from contextlib import redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "generate-changelog.py"
SPEC = importlib.util.spec_from_file_location("generate_changelog", SCRIPT)
CHANGELOG = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHANGELOG)

HISTORY = """## WizeStream 1.49.1 (`1049001`)

- Published download fix.
"""
DEVELOPMENT = """# WizeStream changelogs

## Unreleased

- Pending playback fix.

""" + HISTORY
NUMBERED = """# WizeStream changelogs

## Unreleased

- Work for a later release.

## WizeStream 1.49.2 (`1049002`)

- Prepared release fix.

""" + HISTORY


class ChangelogValidationTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.source = self.root / "docs/changelogs.md"
        self.target = self.root / "app/src/main/assets/changelog.html"
        self.dates = self.root / "docs/release-dates.json"
        self.config = self.root / "buildSrc/src/main/kotlin/ProjectConfig.kt"
        for path in (self.source, self.target, self.config):
            path.parent.mkdir(parents=True, exist_ok=True)
        self.write_version(1, 49, 2)
        self.dates.write_text(json.dumps({"1.49.1": "2026-11-21"}), encoding="utf-8")
        configuration = patch.multiple(
            CHANGELOG, ROOT=self.root, SOURCE=self.source, TARGET=self.target,
            RELEASE_DATES=self.dates,
        )
        configuration.start()
        self.addCleanup(configuration.stop)

    def write_version(self, major, minor, patch_version):
        self.config.write_text(
            f"const val WIZESTREAM_VERSION_MAJOR = {major}\n"
            f"const val WIZESTREAM_VERSION_MINOR = {minor}\n"
            f"const val WIZESTREAM_VERSION_PATCH = {patch_version}\n",
            encoding="utf-8",
        )

    def run_main(self, *arguments):
        output = io.StringIO()
        with patch.object(CHANGELOG.sys, "argv", [str(SCRIPT), *arguments]), redirect_stdout(output):
            CHANGELOG.main()
        return output.getvalue()

    def test_development_notes_render_without_claiming_a_numbered_release(self):
        result = CHANGELOG.generate(DEVELOPMENT)
        self.assertIn('<section id="unreleased">', result)
        self.assertIn("Pending playback fix.", result)
        self.assertIn('data-version="1.49.1" data-version-code="1049001"', result)
        self.assertNotIn('data-version="1.49.2"', result)
        self.assertNotIn("release-1049002", result)

    def test_development_build_requires_nonempty_unreleased_notes(self):
        for markdown in (HISTORY, "## Unreleased\n\n" + HISTORY, "## Unreleased\n\n---\n\n" + HISTORY):
            with self.subTest(markdown=markdown):
                with self.assertRaisesRegex(ValueError, "development notes under ## Unreleased"):
                    CHANGELOG.generate(markdown)

    def test_unreleased_does_not_mask_an_empty_or_mismatched_current_heading(self):
        malformed = (
            "## WizeStream 1.49.2 (`1049002`)\n\n" + HISTORY,
            HISTORY + "\n## WizeStream 1.49.2 (`1049000`)\n\n- Wrong code.\n",
            "## WizeStream 1.49.3 (`1049002`)\n\n- Wrong name.\n\n" + HISTORY,
        )
        for releases in malformed:
            with self.subTest(releases=releases):
                with self.assertRaisesRegex(ValueError, "numbered release notes for WizeStream 1.49.2"):
                    CHANGELOG.generate("## Unreleased\n\n- Pending fix.\n\n" + releases)

    def test_older_configured_version_cannot_fall_back_to_unreleased(self):
        self.write_version(1, 48, 9)
        with self.assertRaisesRegex(ValueError, "numbered release notes for WizeStream 1.48.9"):
            CHANGELOG.generate(DEVELOPMENT)

    def test_known_publication_date_requires_numbered_notes(self):
        self.dates.write_text(json.dumps({"1.49.2": "2026-11-22"}), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "numbered release notes for WizeStream 1.49.2"):
            CHANGELOG.generate(DEVELOPMENT)

    def test_publication_mode_rejects_unreleased_fallback(self):
        with self.assertRaisesRegex(ValueError, "numbered release notes for WizeStream 1.49.2"):
            CHANGELOG.generate(DEVELOPMENT, require_release=True)

    def test_matching_numbered_notes_work_in_both_modes_with_later_work_pending(self):
        for require_release in (False, True):
            with self.subTest(require_release=require_release):
                result = CHANGELOG.generate(NUMBERED, require_release=require_release)
                self.assertIn('data-version="1.49.2" data-version-code="1049002"', result)
                self.assertIn("Prepared release fix.", result)
                self.assertIn("Work for a later release.", result)

    def test_release_codes_still_have_to_be_unique_and_newest_first(self):
        out_of_order = HISTORY + "\n## WizeStream 1.49.2 (`1049002`)\n\n- Newer fix.\n"
        for markdown in (NUMBERED + "\n" + HISTORY, out_of_order):
            with self.subTest(markdown=markdown):
                with self.assertRaisesRegex(ValueError, "unique and newest first"):
                    CHANGELOG.generate(markdown)

    def test_check_accepts_current_development_asset_but_rejects_stale_output(self):
        self.source.write_text(DEVELOPMENT, encoding="utf-8")
        self.target.write_text(CHANGELOG.generate(DEVELOPMENT), encoding="utf-8")
        self.assertIn("matches docs/changelogs.md", self.run_main("--check"))
        self.target.write_text("stale asset", encoding="utf-8")
        with self.assertRaisesRegex(SystemExit, "Changelog asset is stale"):
            self.run_main("--check")
        self.assertEqual("stale asset", self.target.read_text(encoding="utf-8"))

    def test_cli_requires_numbered_notes_for_release_checks_without_rewriting_asset(self):
        self.source.write_text(DEVELOPMENT, encoding="utf-8")
        generated = CHANGELOG.generate(DEVELOPMENT)
        self.target.write_text(generated, encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "numbered release notes for WizeStream 1.49.2"):
            self.run_main("--check", "--require-release")
        self.assertEqual(generated, self.target.read_text(encoding="utf-8"))

        self.source.write_text(NUMBERED, encoding="utf-8")
        self.target.write_text(CHANGELOG.generate(NUMBERED), encoding="utf-8")
        self.assertIn("matches docs/changelogs.md", self.run_main("--check", "--require-release"))


if __name__ == "__main__":
    unittest.main()
