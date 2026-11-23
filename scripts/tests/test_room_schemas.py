"""Exercise schema validation against real temporary Git checkouts."""

from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "check-room-schemas.py"
SCHEMAS = "app/schemas/org.schabi.newpipe.database.AppDatabase"
MIGRATIONS = "app/src/main/java/org/schabi/newpipe/database/Migrations.kt"


class RoomSchemaValidationTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.script = self.root / "scripts/check-room-schemas.py"
        self.script.parent.mkdir()
        shutil.copyfile(SCRIPT, self.script)
        self.git("init", "--quiet")
        self.write(MIGRATIONS, "const val DB_VER_CURRENT = DB_VER_32\n")
        self.write(f"{SCHEMAS}/31.json", '{"database":{"version":31}}\n')
        self.write(f"{SCHEMAS}/32.json", '{"database":{"version":32}}\n')
        self.commit()

    def git(self, *arguments):
        return subprocess.check_output(
            ["git", *arguments], cwd=self.root, stderr=subprocess.STDOUT,
        ).decode().strip()

    def write(self, path, content):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

    def commit(self):
        self.git("add", ".")
        self.git(
            "-c", "user.name=Schema test", "-c", "user.email=schema@example.invalid",
            "-c", "commit.gpgsign=false", "commit", "--quiet", "-m", "Fixture",
        )

    def check(self):
        before_head = self.git("rev-parse", "HEAD")
        before_status = self.git("status", "--porcelain", "--untracked-files=all")
        result = subprocess.run(
            [sys.executable, "-B", str(self.script)], cwd=self.root,
            text=True, capture_output=True, check=False,
        )
        self.assertEqual(before_head, self.git("rev-parse", "HEAD"))
        self.assertEqual(before_status, self.git("status", "--porcelain", "--untracked-files=all"))
        return result

    def test_matching_export_passes(self):
        result = self.check()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(self.git("rev-parse", "--short=12", "HEAD"), result.stdout)
        self.assertIn(f"{SCHEMAS}/32.json", result.stdout)

    def test_changed_current_or_historical_schema_fails(self):
        for version in (31, 32):
            with self.subTest(version=version):
                path = f"{SCHEMAS}/{version}.json"
                original = (self.root / path).read_text(encoding="utf-8")
                self.write(path, original + "\n")
                result = self.check()
                self.assertEqual(1, result.returncode, result.stderr)
                self.assertIn(f"Generated schema differs from HEAD: {path}", result.stderr)
                self.write(path, original)

    def test_staging_changed_export_does_not_make_it_match_head(self):
        path = f"{SCHEMAS}/32.json"
        self.write(path, '{"database":{"version":32,"changed":true}}\n')
        self.git("add", path)
        result = self.check()
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn(f"Generated schema differs from HEAD: {path}", result.stderr)

    def test_untracked_new_current_schema_fails_until_committed(self):
        self.write(MIGRATIONS, "const val DB_VER_CURRENT = DB_VER_33\n")
        self.commit()
        path = f"{SCHEMAS}/33.json"
        self.write(path, '{"database":{"version":33}}\n')
        result = self.check()
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn(f"Schema is not committed at HEAD: {path}", result.stderr)
        self.git("add", path)
        self.assertEqual(1, self.check().returncode)
        self.commit()
        result = self.check()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_missing_current_schema_fails_even_without_git_changes(self):
        self.write(MIGRATIONS, "const val DB_VER_CURRENT = DB_VER_33\n")
        self.commit()
        result = self.check()
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn(f"Missing current database schema: {SCHEMAS}/33.json", result.stderr)

    def test_deleted_historical_schema_fails(self):
        path = f"{SCHEMAS}/31.json"
        (self.root / path).unlink()
        result = self.check()
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn(f"Missing committed schema: {path}", result.stderr)

    def test_untracked_and_ignored_json_in_other_database_are_checked(self):
        path = "app/schemas/example.OtherDatabase/1.json"
        self.write(path, '{"database":{"version":1}}\n')
        for ignored in (False, True):
            with self.subTest(ignored=ignored):
                if ignored:
                    self.write(".gitignore", "app/schemas/example.OtherDatabase/\n")
                result = self.check()
                self.assertEqual(1, result.returncode, result.stderr)
                self.assertIn(f"Schema is not committed at HEAD: {path}", result.stderr)

    def test_unrelated_build_output_does_not_fail_schema_check(self):
        self.write("app/build/results.json", "{}\n")
        self.write(f"{SCHEMAS}/README.md", "Schema documentation\n")
        result = self.check()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_invalid_current_version_fails_clearly(self):
        self.write(MIGRATIONS, "const val DB_VER_CURRENT = UNKNOWN\n")
        result = self.check()
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertIn("Could not read DB_VER_CURRENT", result.stderr)

    def test_detached_checkout_is_validated_without_following_newer_branch(self):
        checked_out = self.git("rev-parse", "HEAD")
        self.write(f"{SCHEMAS}/32.json", '{"database":{"version":32,"newer":true}}\n')
        self.commit()
        self.git("checkout", "--quiet", "--detach", checked_out)
        result = self.check()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(checked_out[:12], result.stdout)
        self.assertEqual("HEAD", self.git("rev-parse", "--abbrev-ref", "HEAD"))


if __name__ == "__main__":
    unittest.main()
