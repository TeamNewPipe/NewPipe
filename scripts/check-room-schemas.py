#!/usr/bin/env python3
"""Validate Room's exported schemas against the exact checked-out commit."""

from __future__ import annotations

from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = "app/schemas"
DATABASE_SCHEMAS = f"{SCHEMAS}/org.schabi.newpipe.database.AppDatabase"
MIGRATIONS = "app/src/main/java/org/schabi/newpipe/database/Migrations.kt"


def git(*arguments: str) -> bytes:
    return subprocess.check_output(["git", *arguments], cwd=ROOT)


def main() -> int:
    try:
        revision = git("rev-parse", "HEAD").decode().strip()
        migrations = (ROOT / MIGRATIONS).read_text(encoding="utf-8")
        version = re.search(
            r"^\s*const\s+val\s+DB_VER_CURRENT\s*=\s*DB_VER_(\d+)\b",
            migrations,
            re.MULTILINE,
        )
        if version is None:
            raise ValueError(f"Could not read DB_VER_CURRENT in {MIGRATIONS}")

        current = f"{DATABASE_SCHEMAS}/{version.group(1)}.json"
        committed_paths = git(
            "ls-tree", "-r", "--name-only", "-z", revision, "--", SCHEMAS,
        ).decode().split("\0")
        committed = {path for path in committed_paths if path.endswith(".json")}
        generated = {
            path.relative_to(ROOT).as_posix()
            for path in (ROOT / SCHEMAS).rglob("*.json")
            if path.is_file()
        }
        problems = []
        if current not in generated:
            problems.append(f"Missing current database schema: {current}")
        for path in sorted(committed - generated):
            problems.append(f"Missing committed schema: {path}")
        for path in sorted(generated - committed):
            problems.append(f"Schema is not committed at HEAD: {path}")
        for path in sorted(committed & generated):
            if (ROOT / path).read_bytes() != git("show", f"{revision}:{path}"):
                problems.append(f"Generated schema differs from HEAD: {path}")
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"Room schema validation could not run: {error}", file=sys.stderr)
        return 2

    if problems:
        print(f"Room schemas do not match checked-out commit {revision[:12]}:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        print(
            "Run scripts/build.sh debug, review the generated JSON, and commit it with the "
            "database changes and any required app-version bump. CI does not update branches.",
            file=sys.stderr,
        )
        return 1

    print(f"Room schemas match checked-out commit {revision[:12]} (current: {current}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
