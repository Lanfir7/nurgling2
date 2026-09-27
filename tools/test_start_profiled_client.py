import tempfile
from pathlib import Path
import unittest

from start_profiled_client import prepare_runtime, backup_player_settings


class RuntimeSnapshotTest(unittest.TestCase):
    def test_settings_copy_survives_client_backup_rotation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            data = root / "player"
            data.mkdir()
            current = data / "nconfig.nurgling.json"
            previous = data / "nconfig.nurgling.json.bak"
            current.write_bytes(b'{"showGrid":true}')
            previous.write_bytes(b'{"showGrid":false}')
            backup = root / "run" / "settings-backup"
            backup_player_settings(data, backup)
            current.write_bytes(b"changed")
            previous.write_bytes(b"rotated")
            self.assertEqual(b'{"showGrid":true}', (backup / current.name).read_bytes())
            self.assertEqual(b'{"showGrid":false}', (backup / previous.name).read_bytes())
            with self.assertRaises(FileExistsError):
                backup_player_settings(data, backup)

    def test_running_snapshot_survives_rebuild_and_preserves_server_config(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            jar = root / "build.jar"
            config = root / "server.properties"
            jar.write_bytes(b"first build")
            config.write_bytes(b"server=test-server\n")
            runtime = prepare_runtime(jar, config, root / "run")
            jar.unlink()
            jar.write_bytes(b"new build")
            config.write_bytes(b"new configuration")
            self.assertEqual(b"first build", runtime.read_bytes())
            self.assertEqual(b"server=test-server\n",
                             runtime.with_name("haven-config.properties").read_bytes())

    def test_existing_snapshot_cannot_be_overwritten(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            jar = root / "build.jar"
            config = root / "server.properties"
            jar.write_bytes(b"original")
            config.write_bytes(b"server=test-server\n")
            runtime = prepare_runtime(jar, config, root / "run")
            jar.write_bytes(b"new build")
            with self.assertRaises(FileExistsError):
                prepare_runtime(jar, config, runtime.parent)
            self.assertEqual(b"original", runtime.read_bytes())


if __name__ == "__main__":
    unittest.main()
