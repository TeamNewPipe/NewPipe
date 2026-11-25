#!/usr/bin/env python3
"""Embed a PNG in a v2/v3 WizeStream recipe; app import performs full validation."""
import base64
import json
import re
import sys
from pathlib import Path

if len(sys.argv) != 4:
    raise SystemExit("Usage: embed_asset.py theme.json asset-id image.png")
recipe_path, asset_id, image_path = sys.argv[1:]
if not re.fullmatch(r"[a-z][a-z0-9_-]{0,31}", asset_id):
    raise SystemExit("Invalid asset ID")
data = Path(image_path).read_bytes()
if not data.startswith(b"\x89PNG\r\n\x1a\n") or len(data) > 96 * 1024:
    raise SystemExit("Use a PNG no larger than 96 KiB")
path = Path(recipe_path)
recipe = json.loads(path.read_text(encoding="utf-8"))
version = recipe.get("version")
if recipe.get("format") != "wizestream-theme" or type(version) is not int or version not in (2, 3):
    raise SystemExit("Use a version 2 or 3 WizeStream recipe")
assets = recipe.setdefault("assets", {})
assets[asset_id] = base64.b64encode(data).decode("ascii")
if len(assets) > 6:
    raise SystemExit("At most six assets are supported")
output = json.dumps(recipe, indent=2, ensure_ascii=False) + "\n"
if len(output.encode("utf-8")) > 512 * 1024:
    raise SystemExit("Complete pack exceeds 512 KiB")
path.write_text(output, encoding="utf-8")
print("Embedded asset. Set its reference, then import in WizeStream to validate.")
