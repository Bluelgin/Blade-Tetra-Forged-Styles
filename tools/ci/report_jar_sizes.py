#!/usr/bin/env python3
"""Measure byte sizes and hashes of packaged Forge artifacts."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile


def category(name: str) -> str:
    if name.endswith(".class"):
        return "java"
    if name.endswith(".jar"):
        return "embedded_jars"
    if name.endswith((".ogg", ".wav", ".mp3")):
        return "audio"
    if name.endswith((".png", ".dds", ".obj", ".mtl", ".bbmodel")) or "/models/" in name:
        return "models_and_textures"
    return "other"


def measure(path: Path) -> dict:
    categories = {}
    with ZipFile(path) as jar:
        for entry in jar.infolist():
            if entry.is_dir():
                continue
            group = categories.setdefault(category(entry.filename), {
                "entries": 0, "compressed": 0, "uncompressed": 0
            })
            group["entries"] += 1
            group["compressed"] += entry.compress_size
            group["uncompressed"] += entry.file_size
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return {
        "file": path.name,
        "bytes": path.stat().st_size,
        "uncompressed_bytes": sum(group["uncompressed"] for group in categories.values()),
        "sha256": digest,
        "entries_by_category": categories,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("jars", type=Path, nargs="+")
    args = parser.parse_args()
    report = {"commit": args.commit, "artifacts": [measure(path) for path in args.jars]}
    content = json.dumps(report, indent=2, sort_keys=True)
    args.output.write_text(content + "\n", encoding="utf-8")
    print(content)


if __name__ == "__main__":
    main()
