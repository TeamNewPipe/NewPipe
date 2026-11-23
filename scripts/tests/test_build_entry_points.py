"""Run the shared shell entry point with a recording Gradle executable."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "build.sh"
RECORDER = """#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys

Path(os.environ["BUILD_ARGS_LOG"]).write_text(json.dumps({
    "command": Path(sys.argv[0]).name,
    "arguments": sys.argv[1:],
    "directory": os.getcwd(),
}))
sys.exit(int(os.environ.get("BUILD_EXIT_CODE", "0")))
"""


class BuildEntryPointTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.script = self.root / "scripts/build.sh"
        self.script.parent.mkdir()
        shutil.copyfile(SCRIPT, self.script)
        self.log = self.root / "arguments.json"
        for path in (self.root / "gradlew", self.root / "scripts/reproducible-build.sh"):
            path.write_text(RECORDER, encoding="utf-8")
            path.chmod(0o755)

    def run_build(self, *arguments, exit_code=0):
        result = subprocess.run(
            ["bash", str(self.script), *arguments], cwd=self.script.parent,
            env={**os.environ, "BUILD_ARGS_LOG": str(self.log), "BUILD_EXIT_CODE": str(exit_code)},
            text=True, capture_output=True, check=False,
        )
        return result

    def invocation(self):
        invocation = json.loads(self.log.read_text(encoding="utf-8"))
        self.assertEqual(str(self.root), invocation["directory"])
        return invocation

    def test_default_build_keeps_debug_tasks_and_formatting_disabled(self):
        result = self.run_build()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([
            "clean", "assembleDebug", "lintDebug", "testDebugUnitTest",
            "--stacktrace", "-DskipFormatKtlint",
        ], self.invocation()["arguments"])

    def test_connected_tests_disable_formatting_and_preserve_gradle_arguments(self):
        extra = [
            "-Pandroid.testInstrumentationRunnerArguments.class=example.First,example.Second",
            "-Dorg.gradle.jvmargs=-Xmx2048M --add-opens java.base/java.lang=ALL-UNNAMED",
        ]
        result = self.run_build("connected", *extra)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([
            "connectedCheck", "--stacktrace", "-DskipFormatKtlint", *extra,
        ], self.invocation()["arguments"])

    def test_other_gradle_modes_keep_formatting_disabled(self):
        for mode, task in (
            ("debug", "assembleDebug"), ("nightly", "assembleNightly"),
            ("checkstyle", "runCheckstyle"),
        ):
            with self.subTest(mode=mode):
                result = self.run_build(mode, "--max-workers=2")
                self.assertEqual(0, result.returncode, result.stderr)
                arguments = self.invocation()["arguments"]
                self.assertIn(task, arguments)
                self.assertIn("-DskipFormatKtlint", arguments)
                self.assertEqual("--max-workers=2", arguments[-1])

    def test_release_still_delegates_to_reproducible_build(self):
        result = self.run_build("release", "--verify", "-PreleaseAbi=x86_64")
        self.assertEqual(0, result.returncode, result.stderr)
        invocation = self.invocation()
        self.assertEqual("reproducible-build.sh", invocation["command"])
        self.assertEqual(["--verify", "-PreleaseAbi=x86_64"], invocation["arguments"])

    def test_gradle_failure_is_propagated(self):
        result = self.run_build("connected", exit_code=7)
        self.assertEqual(7, result.returncode)

    def test_unknown_mode_fails_without_starting_gradle(self):
        result = self.run_build("unknown")
        self.assertEqual(2, result.returncode)
        self.assertIn("Usage:", result.stderr)
        self.assertFalse(self.log.exists())


if __name__ == "__main__":
    unittest.main()
