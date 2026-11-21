#!/usr/bin/env python3
"""Boot an isolated real Forge server with ONLY generated blades, SlashBlade and a probe.

No Gradle userdev classes, Tetra or Blade Tetra are put on this receiving server's
classpath. Run after tools/ci/run_suite.py export_package[ _1965 ] and
./gradlew -PexportRuntimeProbe reobfExportRuntimeProbeJar.

Requires network access for the official Forge installer (cached per version).
This is an explicit, expensive acceptance test; it does not run during ordinary CI.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import threading
import urllib.request
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
EXPECTED_SWORDS = frozenset({
    "bt_export_custom", "bt_export_custom_second", "bt_export_dangaku",
})
REQUIRED_MARKERS = (
    "BLADE_EXPORT_RUNTIME_PROBE_PASS",
    "BLADE_EXPORT_PRECISION_CLOCK_PASS",
    "BLADE_EXPORT_PRECISION_RANK_PASS",
)
CURSE_IDS = {"1.9.63": "8018222", "1.9.65": "8090912"}


def inspect_exports(directory: Path, slashblade_version: str) -> list[Path]:
    if not directory.is_dir():
        raise RuntimeError(f"Missing generated add-on directory: {directory}")
    exports = sorted(directory.glob("*.jar"))
    if len(exports) != len(EXPECTED_SWORDS):
        raise RuntimeError(f"Expected exactly 3 generated JARs; found {len(exports)}")
    seen = set()
    runtimes = set()
    for file in exports:
        with ZipFile(file) as archive:
            names = set(archive.namelist())
            if len(names) != len(archive.namelist()):
                raise RuntimeError(f"Duplicate entries in {file}")
            required = {"blade-export.json", "META-INF/mods.toml",
                        "META-INF/jarjar/metadata.json"}
            if not required.issubset(names):
                raise RuntimeError(f"Missing signed package entries in {file}")
            manifest = json.loads(archive.read("blade-export.json"))
            hashes = manifest.get("sha256")
            if not isinstance(hashes, dict) or set(hashes) != names - {"blade-export.json"}:
                raise RuntimeError(f"Invalid sealed package certificate coverage: {file}")
            for entry, expected in hashes.items():
                if hashlib.sha256(archive.read(entry)).hexdigest() != expected:
                    raise RuntimeError(f"Tampered exported JAR entry: {file}: {entry}")
            blade_id = manifest["blade"]
            namespace, separator, blade_name = blade_id.partition(":")
            if not separator or blade_name != "sealed_blade" or namespace not in EXPECTED_SWORDS:
                raise RuntimeError(f"Unexpected exported blade ID: {blade_id}")
            if namespace in seen:
                raise RuntimeError(f"Duplicate generated blade: {namespace}")
            seen.add(namespace)
            dependencies = {entry["modId"]: entry["version"] for entry in manifest["dependencies"]}
            if dependencies.get("slashblade") != slashblade_version:
                raise RuntimeError(f"{file} targets SlashBlade {dependencies.get('slashblade')}, "
                                   f"not {slashblade_version}")
            if "tetra" in dependencies or "blade_tetra" in dependencies:
                raise RuntimeError(f"{file} references the source mod")
            descriptor = archive.read("META-INF/mods.toml").decode("utf-8")
            if re.search(r'modId\s*=\s*"(?:tetra|blade_tetra)"', descriptor):
                raise RuntimeError(f"{file} declares an unwanted source dependency")
            nested = json.loads(archive.read("META-INF/jarjar/metadata.json"))["jars"]
            if len(nested) != 1:
                raise RuntimeError(f"{file} must contain exactly one shared runtime")
            info = nested[0]
            if info["identifier"] != {"group": "dev.btsealed", "artifact": "bt-export-runtime"}:
                raise RuntimeError(f"Unexpected nested runtime: {info['identifier']}")
            version = info["version"]["artifactVersion"]
            runtimes.add(version)
            if info["path"] not in names:
                raise RuntimeError(f"Missing runtime binary in {file}")
    if seen != EXPECTED_SWORDS or len(runtimes) != 1:
        raise RuntimeError(f"Export identity or shared runtime inconsistent: {seen}, {runtimes}")
    print(f"BLADE_EXPORT_CLEAN_INPUTS_PASS: {len(exports)} independent JARs "
          f"(shared runtime {next(iter(runtimes))}, SlashBlade {slashblade_version})", flush=True)
    return exports


def find_slashblade(version: str) -> Path:
    suffix = CURSE_IDS[version]
    gradle = Path(os.environ.get("GRADLE_USER_HOME", "~/.gradle")).expanduser()
    cache = gradle / "caches/modules-2/files-2.1/curse.maven/slashblade-resharped-1022428" / suffix
    jars = sorted(cache.glob("**/*.jar"))
    if not jars:
        raise RuntimeError(f"No original SlashBlade {version} JAR in {cache}. "
                           "First run the corresponding Gradle export_package suite.")
    return jars[0]


def fetch_installer(version: str) -> Path:
    cache = ROOT / "build/clean-receiver/installers"
    cache.mkdir(parents=True, exist_ok=True)
    destination = cache / f"forge-{version}-installer.jar"
    if destination.is_file() and destination.stat().st_size > 1_000_000:
        return destination
    url = ("https://maven.minecraftforge.net/net/minecraftforge/forge/"
           f"{version}/forge-{version}-installer.jar")
    temporary = destination.with_suffix(".tmp")
    try:
        with urllib.request.urlopen(url, timeout=120) as source, temporary.open("wb") as output:
            shutil.copyfileobj(source, output)
        if temporary.stat().st_size <= 1_000_000:
            raise RuntimeError("Forge installer was unexpectedly small")
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return destination


def run_with_log(args: list[str], directory: Path, path: Path, timeout: int) -> str:
    print("Running:", " ".join(args), flush=True)
    with path.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(args, cwd=directory, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, errors="replace",
                                   start_new_session=True)
        timed_out = threading.Event()

        def kill():
            timed_out.set()
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass

        watchdog = threading.Timer(timeout, kill)
        watchdog.daemon = True
        watchdog.start()
        try:
            for line in process.stdout:
                print(line, end="", flush=True)
                output.write(line)
            status = process.wait()
        finally:
            watchdog.cancel()
    if timed_out.is_set() or status:
        raise RuntimeError(f"{args[0]} failed: exit={status}, timeout={timed_out.is_set()}, log={path}")
    return path.read_text(encoding="utf-8", errors="replace")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--slashblade", choices=tuple(CURSE_IDS), required=True)
    parser.add_argument("--fixture-dir", type=Path)
    parser.add_argument("--probe-jar", type=Path, default=ROOT / "build/export-runtime/bt-export-runtime-probe.jar")
    parser.add_argument("--forge", default="1.20.1-47.4.0")
    parser.add_argument("--timeout", type=int, default=600)
    opts = parser.parse_args()

    profile = "blade-export-package-smoke" + ("-1965" if opts.slashblade == "1.9.65" else "")
    fixture_dir = opts.fixture_dir or ROOT / "build" / profile / "blade-exports"
    exports = inspect_exports(fixture_dir, opts.slashblade)
    if not opts.probe_jar.is_file():
        raise RuntimeError("Missing receiver probe: run ./gradlew -PexportRuntimeProbe "
                           "reobfExportRuntimeProbeJar first")
    slashblade_jar = find_slashblade(opts.slashblade)
    forge_installer = fetch_installer(opts.forge)
    receiver = ROOT / "build/clean-receiver" / opts.slashblade.replace(".", "")
    if receiver.exists():
        shutil.rmtree(receiver)
    receiver.mkdir(parents=True)
    # Dedicated isolated installation: no project source sets or userdev mods.
    run_with_log(["java", "-jar", str(forge_installer), "--installServer"],
                 receiver, receiver / "installer.log", 600)
    args_file = receiver / "libraries/net/minecraftforge/forge" / opts.forge / "unix_args.txt"
    if not args_file.is_file():
        raise RuntimeError("Forge installer did not produce the dedicated-server launch arguments")
    mods = receiver / "mods"
    mods.mkdir()
    for file in [*exports, slashblade_jar, opts.probe_jar]:
        dst = mods / file.name
        if dst.exists():
            raise RuntimeError(f"Duplicate mod filename: {dst.name}")
        shutil.copy2(file, dst)
    (receiver / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (receiver / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
        "spawn-protection=0\ngenerate-structures=false\n"
        "level-type=minecraft:flat\n", encoding="utf-8")
    (receiver / "user_jvm_args.txt").write_text(
        "-Xms512M\n-Xmx3G\n", encoding="utf-8")
    text = run_with_log(["java", "@user_jvm_args.txt", "@" + str(args_file.relative_to(receiver)), "nogui"],
                        receiver, receiver / "receiver.log", opts.timeout)
    missing = [marker for marker in REQUIRED_MARKERS if marker not in text]
    if missing:
        raise RuntimeError(f"Receiver exited but did not prove expected behavior: {missing}")
    print(f"BLADE_EXPORT_CLEAN_RECEIVER_PASS: SlashBlade {opts.slashblade}, "
          f"source-free Forge {opts.forge}, {len(exports)} generated blades", flush=True)


if __name__ == "__main__":
    main()
