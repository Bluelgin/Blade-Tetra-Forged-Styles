"""Offline contract tests for the real, source-free Forge receiver acceptance harness."""
import hashlib
import io
import json
import urllib.error
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from zipfile import ZipFile

import run_clean_receiver
from run_clean_receiver import EXPECTED_SWORDS, inspect_exports, verify_probe_isolation


def make_package(directory: Path, namespace: str, *, slashblade: str = "1.9.63",
                 runtime: str = "1.3.0", source_dependency: bool = False,
                 corrupt: bool = False) -> Path:
    entries = {
        "META-INF/mods.toml": (
            f'modLoader="lowcodefml"\n[[mods]]\nmodId="{namespace}"\n').encode(),
        "META-INF/jarjar/metadata.json": json.dumps({
            "jars": [{"identifier": {"group": "dev.btsealed", "artifact": "bt-export-runtime"},
                      "version": {"artifactVersion": runtime, "range": f"[{runtime}]"},
                      "path": f"META-INF/jarjar/bt-export-runtime-{runtime}.jar"}]
        }).encode(),
        f"META-INF/jarjar/bt-export-runtime-{runtime}.jar": b"sample shared runtime contents",
    }
    dependencies = [{"modId": "slashblade", "version": slashblade}]
    if source_dependency:
        dependencies.append({"modId": "blade_tetra", "version": "1.6.7"})
    manifest = {
        "blade": namespace + ":sealed_blade",
        "dependencies": dependencies,
        "sha256": {name: hashlib.sha256(data).hexdigest() for name, data in entries.items()},
    }
    out = directory / (namespace + ".jar")
    with ZipFile(out, "w") as archive:
        for name, data in entries.items():
            archive.writestr(name, b"tampered" if corrupt and name.endswith("sealed_blade.json") else data)
        archive.writestr("blade-export.json", json.dumps(manifest))
    return out


class CleanReceiverContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)

    def make_five(self, **overrides):
        for name in EXPECTED_SWORDS:
            make_package(self.directory, name, **overrides)

    def test_complete_independent_packages(self):
        self.make_five()
        self.assertEqual(len(inspect_exports(self.directory, "1.9.63")), 5)

    def test_65_package_versions_must_match_target(self):
        self.make_five(slashblade="1.9.65")
        self.assertEqual(len(inspect_exports(self.directory, "1.9.65")), 5)
        with self.assertRaisesRegex(RuntimeError, "targets SlashBlade"):
            inspect_exports(self.directory, "1.9.63")

    def test_refuse_source_dependency(self):
        self.make_five(source_dependency=True)
        with self.assertRaisesRegex(RuntimeError, "source mod"):
            inspect_exports(self.directory, "1.9.63")

    def test_detect_tampered_package_hash(self):
        self.make_five(corrupt=True)
        with self.assertRaisesRegex(RuntimeError, "Tampered"):
            inspect_exports(self.directory, "1.9.63")

    def test_reject_mixed_runtime_versions(self):
        self.make_five()
        make_package(self.directory, "bt_export_dangaku", runtime="1.1.0")
        with self.assertRaisesRegex(RuntimeError, "runtime inconsistent"):
            inspect_exports(self.directory, "1.9.63")

    def test_refuse_missing_fixture(self):
        make_package(self.directory, "bt_export_custom")
        with self.assertRaisesRegex(RuntimeError, "Expected exactly 5"):
            inspect_exports(self.directory, "1.9.63")
    def test_probe_must_not_share_runtime_module_package(self):
        probe = self.directory / "receiver-probe.jar"
        with ZipFile(probe, "w") as jar:
            jar.writestr("dev/btsealed/probe/ExportRuntimeProbe.class", b"probe")
            jar.writestr("dev/btsealed/runtime/PortableRuntimeSelfTest.class", b"bad")
        with self.assertRaisesRegex(RuntimeError, "runtime module package"):
            verify_probe_isolation(probe)
        with ZipFile(probe, "w") as jar:
            jar.writestr("dev/btsealed/probe/ExportRuntimeProbe.class", b"probe")
            jar.writestr("dev/btsealed/probe/PortableRuntimeSelfTest.class", b"good")
        verify_probe_isolation(probe)

    def test_pinned_forge_mirror_fallback_and_cache(self):
        payload = b"sample verifier installer" * 60000
        sha1 = hashlib.sha1(payload).hexdigest()
        seen = []

        def download(request, timeout=0):
            url = request.full_url
            seen.append(url)
            if "maven.minecraftforge.net" in url:
                raise urllib.error.HTTPError(url, 403, "CDN refused CI runner", {}, None)
            return io.BytesIO(payload)

        with patch.object(run_clean_receiver, "ROOT", self.directory), \
             patch.dict(run_clean_receiver.FORGE_INSTALLER_SHA1,
                        {"1.20.1-47.4.0": sha1}), \
             patch("urllib.request.urlopen", side_effect=download):
            path = run_clean_receiver.fetch_installer("1.20.1-47.4.0")
            self.assertEqual(path.read_bytes(), payload)
            self.assertGreaterEqual(len(seen), 2)
            self.assertEqual(run_clean_receiver.fetch_installer("1.20.1-47.4.0"), path)
            self.assertEqual(len(seen), 2, "verified installer cache caused a second download")

    def test_reject_forge_mirror_with_wrong_signature(self):
        payload = b"untrusted installer" * 80000
        with patch.object(run_clean_receiver, "ROOT", self.directory), \
             patch("urllib.request.urlopen", side_effect=lambda request, timeout=0: io.BytesIO(payload)):
            with self.assertRaisesRegex(RuntimeError, "verified Forge installer"):
                run_clean_receiver.fetch_installer("1.20.1-47.4.0")
        self.assertFalse((self.directory / "build/clean-receiver/installers/"
                          "forge-1.20.1-47.4.0-installer.jar").exists())



if __name__ == "__main__":
    unittest.main()
