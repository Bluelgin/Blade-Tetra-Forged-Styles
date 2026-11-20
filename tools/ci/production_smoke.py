#!/usr/bin/env python3
"""Opt-in production client test: real reobfuscated JAR, original dependencies, isolated Forge."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "build" / "production-smoke"
INSTANCE = BASE / "instance"
MODS = INSTANCE / "mods"
CLIENT_PASS = "BLADE_TETRA_PRODUCTION_CLIENT_PASS"
RENDERERS_PASS = "BLADE_TETRA_PRODUCTION_RENDERERS_PASS"
CLIENT_FAIL = "BLADE_TETRA_PRODUCTION_CLIENT_FAIL"
RENDERERS_FAIL = "BLADE_TETRA_PRODUCTION_RENDERERS_FAIL"
CRASH_MARKERS = (CLIENT_FAIL, RENDERERS_FAIL, "Unreported exception thrown!", "---- Minecraft Crash Report ----")


def release_jar(mods: Path) -> Path:
    candidates = sorted(mods.glob("blade_tetra-*.jar"))
    candidates = [f for f in candidates if not f.name.endswith(("-sources.jar", "-dev.jar"))]
    if len(candidates) != 1:
        raise RuntimeError(f"Expected exactly one packaged Blade Tetra release JAR: {candidates}")
    return candidates[0]


def inspect_artifact(mods: Path) -> dict:
    jar = release_jar(mods)
    sha256 = hashlib.sha256(jar.read_bytes()).hexdigest()
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        required = {
            "META-INF/mods.toml",
            "dev/bladetetra/client/BladeTetraEntityRendererRegistration.class",
            "dev/bladetetra/client/ProductionRendererSmoke.class",
            "dev/bladetetra/client/ProductionClientSmoke.class",
            "blade_tetra.mixins.json",
        }
        absent = required - names
        if absent:
            raise RuntimeError(f"Wrong or incomplete production JAR: {sorted(absent)}")
        if "blade_tetra.dev.mixins.json" in names or any(
                name.startswith("dev/bladetetra/client/kyouka/KyoukaMotionSmokeClient")
                for name in names):
            raise RuntimeError("Development-only classes/config leaked into release JAR")
        mods_toml = archive.read("META-INF/mods.toml").decode("utf-8")
        if not re.search(r'modId\s*=\s*["\x27]blade_tetra["\x27]', mods_toml):
            raise RuntimeError("Release JAR does not declare blade_tetra")
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
        if "MixinConfigs: blade_tetra.mixins.json" not in manifest:
            raise RuntimeError("Release JAR mixin manifest is missing")

    dependencies = sorted(p.name for p in mods.glob("*.jar") if p != jar)
    if len(dependencies) < 3:
        raise RuntimeError(f"Production launcher is missing runtime dependency JARs: {dependencies}")
    return {"jar": jar.name, "sha256": sha256, "dependencies": dependencies,
            "size_bytes": jar.stat().st_size}


def find_crash(text: str) -> str | None:
    for token in CRASH_MARKERS:
        if token in text:
            return token
    return None


def await_real_client(process: subprocess.Popen, latest_log: Path, timeout: int) -> None:
    deadline = time.monotonic() + timeout
    verified_at = None
    while time.monotonic() < deadline:
        # Forge writes latest.log itself. Do not trust the launcher's own stdout
        # or a previous run's markers; this directory is fresh for each run.
        log = latest_log.read_text(errors="replace") if latest_log.is_file() else ""
        crash = find_crash(log)
        if crash:
            raise RuntimeError(f"Production client crash reported: {crash}")
        if CLIENT_PASS in log and RENDERERS_PASS in log:
            if verified_at is None:
                verified_at = time.monotonic()
                print("Renderer map and client menu markers observed; checking stability...", flush=True)
            if time.monotonic() - verified_at >= 5:
                if process.poll() not in (None, 0):
                    raise RuntimeError(f"Production client quit abnormally: {process.returncode}")
                return
        if process.poll() is not None:
            raise RuntimeError(f"Forge client exited before confirmation (code {process.returncode}); "
                               f"renderers={RENDERERS_PASS in log}, menu={CLIENT_PASS in log}")
        time.sleep(1)
    raise TimeoutError(f"Forge production client did not reach verified menu within {timeout}s")


def launch_production_client(forge_version: str, timeout: int) -> None:
    try:
        import minecraft_launcher_lib
    except ImportError as exc:
        raise RuntimeError("Install the optional minecraft-launcher-lib==8.0 for the real-client mode") from exc

    java = shutil.which("java")
    if not java:
        raise RuntimeError("Java 17 must be available on PATH")

    launcher_root = BASE / "launcher"
    launcher_root.mkdir(parents=True, exist_ok=True)
    INSTANCE.mkdir(parents=True, exist_ok=True)
    (INSTANCE / "logs").mkdir(exist_ok=True)
    latest_log = INSTANCE / "logs" / "latest.log"
    if latest_log.exists():
        latest_log.unlink()

    full_version = f"1.20.1-{forge_version}"
    print(f"Preparing separate Minecraft 1.20.1 / Forge {forge_version} client...", flush=True)
    minecraft_launcher_lib.install.install_minecraft_version("1.20.1", str(launcher_root))
    minecraft_launcher_lib.forge.install_forge_version(full_version, str(launcher_root), java=java)
    installed = minecraft_launcher_lib.forge.forge_to_installed_version(full_version)

    options = minecraft_launcher_lib.utils.generate_test_options()
    options.update({
        "gameDirectory": str(INSTANCE),
        "executablePath": java,
        "jvmArguments": ["-Xms512m", "-Xmx3G", "-Dblade_tetra.productionSmoke=true"],
        "customResolution": True,
        "resolutionWidth": "960",
        "resolutionHeight": "540",
    })
    command = minecraft_launcher_lib.command.get_minecraft_command(installed, str(launcher_root), options)
    if "-Dblade_tetra.productionSmoke=true" not in command:
        raise RuntimeError("Forge launch command did not include the production smoke JVM flag")
    if not any("forgeclient" in arg for arg in command):
        raise RuntimeError("Launch command does not appear to target the Forge client")

    env = dict(os.environ)
    env.setdefault("LIBGL_ALWAYS_SOFTWARE", "true")
    env.setdefault("MESA_GL_VERSION_OVERRIDE", "4.5")
    logfile = BASE / "launcher-run.log"
    print("Launching isolated packaged Forge client (no runClient / sourceSets.main)...", flush=True)
    with logfile.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=INSTANCE, stdout=output, stderr=subprocess.STDOUT,
                                   env=env, start_new_session=(os.name == "posix"))
        try:
            await_real_client(process, latest_log, timeout)
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("artifact", "client"), default="client")
    parser.add_argument("--forge", default="47.4.13")
    parser.add_argument("--timeout", type=int, default=480)
    args = parser.parse_args()
    if not re.fullmatch(r"47\.\d+\.\d+", args.forge):
        parser.error("Expected Forge version such as 47.4.13")
    if not 30 <= args.timeout <= 900:
        parser.error("timeout must be between 30 and 900 seconds")
    BASE.mkdir(parents=True, exist_ok=True)
    report = {"mode": args.mode, "forge": args.forge, "status": "failed"}
    try:
        report.update(inspect_artifact(MODS))
        print(f"Production JAR: {report['jar']} / SHA-256 {report['sha256']}", flush=True)
        print(f"Original dependency JARs: {', '.join(report['dependencies'])}", flush=True)
        if args.mode == "artifact":
            print("BLADE_TETRA_PRODUCTION_ARTIFACT_PASS (JAR audit only; client NOT launched)", flush=True)
        else:
            launch_production_client(args.forge, args.timeout)
            print("BLADE_TETRA_PRODUCTION_SMOKE_PASS (real Forge client)", flush=True)
        report["status"] = "passed"
        return 0
    except Exception as failure:
        report["error"] = str(failure)
        print(f"BLADE_TETRA_PRODUCTION_SMOKE_FAIL: {failure}", file=sys.stderr, flush=True)
        return 1
    finally:
        (BASE / "result.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    raise SystemExit(main())
