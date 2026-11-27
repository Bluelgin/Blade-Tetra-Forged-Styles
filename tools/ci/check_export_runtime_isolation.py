#!/usr/bin/env python3
"""Prevent standalone Jar-in-Jar sources from acquiring Blade Tetra/Tetra imports.

This static gate complements, but does not replace, clean receiving-client/server tests.
Gradle relocates explicitly shared Java sources into the standalone runtime.
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
    "SaltyFishHitFeedback.java",
    "NativeFusionEngine.java",
    "NativeFusionLifecycle.java",
    "FusionSourceExecution.java",
    "SealedFusionSources.java",
}

def imports(source: str):
    return re.findall(r"(?m)^\s*import\s+(?:static\s+)?([^;\s]+)\s*;", source)


def main():
    files = sorted(RUNTIME.rglob("*.java"))
    files.extend(COMBAT / name for name in sorted(COPIED))
    files.append(ROOT / "src/main/java/dev/bladetetra/compat/fusion/RecastingFusionCompatibility.java")
    # The generated runtime rewrites this pure-Java exporter contract's package.
    # Audit it alongside the independent runtime sources.
    files.append(ROOT / "src/main/java/dev/bladetetra/export/PortableCapabilityCatalog.java")
    if not files or not all(file.is_file() for file in files):
        raise SystemExit("Missing standalone runtime source")
    for file in files:
        source = file.read_text(encoding="utf-8")
        if file.is_relative_to(RUNTIME):
            # Mixin adapters must live outside the core runtime package to
            # preserve a stable JNI/JPMS boundary with the separate test probe.
            expected = "dev.btsealed.mixin" if file.parent.name == "mixin" else "dev.btsealed.runtime"
            if not re.search(r"(?m)^\s*package\s+" + re.escape(expected) + r"\s*;", source):
                raise SystemExit(f"Runtime source has unexpected package: {file} (expected {expected})")
        for imported in imports(source):
            if (file.parent == COMBAT and file.name in {"FusionSourceExecution.java", "NativeFusionEngine.java"}
                    and imported == "dev.bladetetra.compat.fusion.RecastingFusionCompatibility"):
                continue
            if imported.startswith("dev.bladetetra.") and (
                file.is_relative_to(RUNTIME) or not imported.startswith("dev.bladetetra.combat.")
            ):
                raise SystemExit(f"Standalone runtime references Blade Tetra: {file}: {imported}")
            if imported.startswith(("se.mickelus.tetra.", "dev.bladetetra.export.")):
                raise SystemExit(f"Standalone runtime imports a source-only dependency: {file}: {imported}")
    print(f"BLADE_EXPORT_RUNTIME_ISOLATION_PASS: checked {len(files)} sources")


if __name__ == "__main__":
    main()
