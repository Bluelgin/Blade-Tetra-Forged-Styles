"""Fast dependency-free regression tests for opt-in production JAR gate."""
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

from production_smoke import inspect_artifact, find_crash, release_jar


class ProductionSmokeGateTest(unittest.TestCase):
    def write_fixture(self, mods: Path, *, leak=False, omit=None):
        mods.mkdir(parents=True, exist_ok=True)
        jar = mods / "blade_tetra-1.6.7.jar"
        entries = {
            "META-INF/mods.toml": '[[mods]]\nmodId="blade_tetra"\n',
            "META-INF/MANIFEST.MF": "Manifest-Version: 1.0\nMixinConfigs: blade_tetra.mixins.json\n",
            "blade_tetra.mixins.json": "{}",
            "dev/bladetetra/client/BladeTetraEntityRendererRegistration.class": "",
            "dev/bladetetra/client/ProductionRendererSmoke.class": "",
            "dev/bladetetra/client/ProductionClientSmoke.class": "",
        }
        if omit is not None:
            del entries[omit]
        if leak:
            entries["blade_tetra.dev.mixins.json"] = "{}"
        with ZipFile(jar, "w") as archive:
            for name, content in entries.items():
                archive.writestr(name, content)
        for name in ("tetra.jar", "slashblade.jar", "mutil.jar", "playeranimator.jar"):
            (mods / name).write_bytes(b"fixture")
        return jar

    def test_a_valid_release_artifact_is_recognized_without_a_client(self):
        with tempfile.TemporaryDirectory() as directory:
            mods = Path(directory)
            jar = self.write_fixture(mods)
            self.assertEqual(release_jar(mods), jar)
            details = inspect_artifact(mods)
            self.assertEqual(details["jar"], jar.name)
            self.assertEqual(len(details["sha256"]), 64)
            self.assertEqual(len(details["dependencies"]), 4)

    def test_dev_mixin_cannot_ship_in_production_smoke(self):
        with tempfile.TemporaryDirectory() as directory:
            mods = Path(directory)
            self.write_fixture(mods, leak=True)
            with self.assertRaisesRegex(RuntimeError, "Development-only"):
                inspect_artifact(mods)

    def test_missing_probe_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            mods = Path(directory)
            self.write_fixture(mods, omit="dev/bladetetra/client/ProductionRendererSmoke.class")
            with self.assertRaisesRegex(RuntimeError, "incomplete production"):
                inspect_artifact(mods)

    def test_duplicate_release_jars_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            mods = Path(directory)
            self.write_fixture(mods)
            (mods / "blade_tetra-1.6.8.jar").write_bytes(b"duplicate")
            with self.assertRaisesRegex(RuntimeError, "exactly one"):
                release_jar(mods)

    def test_only_game_markers_count_as_confirmation(self):
        self.assertIsNone(find_crash("BLADE_TETRA_PRODUCTION_RENDERERS_PASS"))
        self.assertEqual(find_crash("Unreported exception thrown!"),
                         "Unreported exception thrown!")
        self.assertEqual(find_crash("BLADE_TETRA_PRODUCTION_CLIENT_FAIL"),
                         "BLADE_TETRA_PRODUCTION_CLIENT_FAIL")


if __name__ == "__main__":
    unittest.main()
