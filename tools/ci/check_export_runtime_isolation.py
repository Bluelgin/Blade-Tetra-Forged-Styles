#!/usr/bin/env python3
"""Prevent standalone Jar-in-Jar sources from acquiring Blade Tetra/Tetra imports.

This static gate complements, but does not replace, clean receiving-client/server tests.
The Gradle copy task rewrites only 'dev.bladetetra.combat' package references.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = ROOT / "src/exportRuntime/java"
COMBAT = ROOT / "src/main/java/dev/bladetetra/combat"
COPIED = {
    "StyleBranchRules.java",
    "StyleCommonCommands.java",
    "StyleTargeting.java",
    "DangakuCircleSlashRules.java",
    "DangakuSpinRules.java",
    "DangakuCombatValues.java",
}

def imports(source: str):
    return re.findall(r"(?m)^\s*import\s+(?:static\s+)?([^;\s]+)\s*;", source)


def main():
    files = sorted(RUNTIME.rglob("*.java"))
    files.extend(COMBAT / name for name in sorted(COPIED))
    if not files or not all(file.is_file() for file in files):
        raise SystemExit("Missing standalone runtime source")
    for file in files:
        source = file.read_text(encoding="utf-8")
        if file.is_relative_to(RUNTIME):
            if not re.search(r"(?m)^\s*package\s+dev\.btsealed\.runtime\s*;", source):
                raise SystemExit(f"Runtime source has unexpected package: {file}")
        for imported in imports(source):
            if imported.startswith("dev.bladetetra.") and (
                file.is_relative_to(RUNTIME) or not imported.startswith("dev.bladetetra.combat.")
            ):
                raise SystemExit(f"Standalone runtime references Blade Tetra: {file}: {imported}")
            if imported.startswith(("se.mickelus.tetra.", "dev.bladetetra.export.")):
                raise SystemExit(f"Standalone runtime imports a source-only dependency: {file}: {imported}")
    print(f"BLADE_EXPORT_RUNTIME_ISOLATION_PASS: checked {len(files)} sources")


if __name__ == "__main__":
    main()
