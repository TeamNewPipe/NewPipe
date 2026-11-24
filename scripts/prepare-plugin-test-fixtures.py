#!/usr/bin/env python3
"""Fetch pinned, third-party compatibility fixtures into the Android test build directory.

These files are not application assets and must not be uploaded as release artifacts.
The tests themselves are offline; no repository content or package is trusted without
matching the checked-in digest and exact byte count below.
"""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import tempfile
import urllib.request


ROOT = Path(__file__).resolve().parents[1]
FIXTURES = (
    (
        "twitch-5.ppp",
        "https://github.com/MohrJonas/TwitchPipePipePlugin/releases/download/repo/twitch-5.ppp",
        239727,
        "3b48b2fd3c773c36f1f3ab7ae4e18af0eb92cd3975f004ba074120b1e4f4228a",
    ),
    (
        "twitch-5-LICENSE.txt",
        "https://raw.githubusercontent.com/MohrJonas/TwitchPipePipePlugin/"
        "9204d76a1d45271b9887fef2ef0561ce07819a84/LICENSE",
        1931,
        "08c1c6ac745870031ef39014c9b3cc20b841444e24341c289b9a5932191e936c",
    ),
)


def verified(content: bytes, expected_size: int, expected_sha256: str) -> bool:
    return len(content) == expected_size and hashlib.sha256(content).hexdigest() == expected_sha256


def prepare(output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    for name, url, expected_size, expected_sha256 in FIXTURES:
        target = output / name
        if target.is_file() and verified(target.read_bytes(), expected_size, expected_sha256):
            print(f"Verified cached Android test fixture: {name}")
            continue
        request = urllib.request.Request(url, headers={"User-Agent": "WizeStream-compatibility-tests"})
        with urllib.request.urlopen(request, timeout=30) as response:
            content = response.read(expected_size + 1)
        if not verified(content, expected_size, expected_sha256):
            raise SystemExit(f"Refusing {name}: fixture size or SHA-256 does not match the pinned release")
        with tempfile.NamedTemporaryFile(dir=output, prefix=f".{name}.", delete=False) as temporary:
            temporary.write(content)
            temporary_path = Path(temporary.name)
        try:
            temporary_path.replace(target)
        finally:
            temporary_path.unlink(missing_ok=True)
        print(f"Prepared verified Android test fixture: {name}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        type=Path,
        default=ROOT / "app/build/generated/plugin-test-assets",
        help="Generated Android test asset directory (never app/src/main/assets)",
    )
    args = parser.parse_args()
    output = args.output.resolve()
    main_assets = ROOT / "app/src/main/assets"
    if output == main_assets or main_assets in output.parents:
        raise SystemExit("Third-party plugin fixtures must not be placed in application assets")
    prepare(output)


if __name__ == "__main__":
    main()
