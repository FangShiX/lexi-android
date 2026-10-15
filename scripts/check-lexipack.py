#!/usr/bin/env python3
"""Fast structural check for large Lexi packs without importing them into Android."""
import argparse, json, zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("pack", type=Path)
args = parser.parse_args()

seen = set()
forms = 0
def normalized_headword(item: dict) -> str:
    explicit = item.get("normalizedHeadword")
    if explicit:
        return str(explicit)
    return str(item.get("word", "")).replace("_", " ").strip().lower().strip(".,;:!?\"'()[]")

with zipfile.ZipFile(args.pack) as archive:
    manifest = json.loads(archive.read("manifest.json"))
    with archive.open("entries.jsonl") as entries:
        for line_number, raw in enumerate(entries, 1):
            item = json.loads(raw)
            key = normalized_headword(item)
            if not key:
                raise SystemExit(f"missing word at line {line_number}")
            if key in seen:
                raise SystemExit(f"duplicate normalizedHeadword at line {line_number}: {key}")
            seen.add(key)
            forms += len(item.get("forms", []))

expected = manifest["source"]["entryCount"]
if expected != len(seen):
    raise SystemExit(f"manifest={expected}, actual={len(seen)}")
print(json.dumps({"entries": len(seen), "forms": forms, "schemaVersion": manifest["schemaVersion"]}))
