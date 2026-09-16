import hashlib
from pathlib import Path
import tempfile
import unittest

from finalize_release_manifest import SCRIPTS, finalize


class ManifestFinalizationTest(unittest.TestCase):
    def test_adds_exact_script_bytes_preserves_version_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = root / "ver"
            manifest.write_text("2.103.206\nhafen.jar=abc\n", encoding="utf-8")
            for name in SCRIPTS:
                (root / name).write_bytes(b"@echo off\r\nrem master\r\n")
            finalize(root, manifest)
            once = manifest.read_bytes()
            finalize(root, manifest)
            self.assertEqual(once, manifest.read_bytes())
            self.assertTrue(once.startswith(b"2.103.206\n"))
            self.assertIn(b"hafen.jar=abc\n", once)
            self.assertNotIn(b"nurgling_launcher.jar", once)
            for name in SCRIPTS:
                digest = hashlib.sha256((root / name).read_bytes()).hexdigest()
                self.assertIn((name + "=" + digest).encode(), once)

    def test_rejects_unsafe_launcher_replacement(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = root / "ver"
            manifest.write_text("2.103.206\nnurgling_launcher.jar=abc\n", encoding="utf-8")
            before = manifest.read_bytes()
            with self.assertRaisesRegex(ValueError, "cannot safely"):
                finalize(root, manifest)
            self.assertEqual(before, manifest.read_bytes())

    def test_missing_script_keeps_original_manifest(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = root / "ver"
            manifest.write_text("2.103.206\nhafen.jar=abc\n", encoding="utf-8")
            before = manifest.read_bytes()
            with self.assertRaises(FileNotFoundError):
                finalize(root, manifest)
            self.assertEqual(before, manifest.read_bytes())


if __name__ == "__main__":
    unittest.main()
