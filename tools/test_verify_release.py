import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from verify_release import VerificationError, verify_release


class VerifyReleaseTests(unittest.TestCase):
    def make_release(self) -> Path:
        self.tempdir = tempfile.TemporaryDirectory()
        root = Path(self.tempdir.name)
        files = {
            "hafen.jar": b"hafen",
            "nurgling_launcher.jar": b"launcher",
            "nurgling-res.jar": b"resources",
            "run_updater.bat": b"updater",
            "run_updater8.bat": b"updater8",
            "run_updater_latest.bat": b"latest",
            "run_updater_stable.bat": b"stable",
        }
        for name, content in files.items():
            (root / name).write_bytes(content)
        sounds = root / "AlarmSounds"
        sounds.mkdir()
        (sounds / "ND_Test.wav").write_bytes(b"sound")
        notes = {
            "schema": 1,
            "processed": ["2026-09-17-example"],
            "releases": [{
                "id": "2.103.206", "date": "2026-09-17",
                "title": {"ru": "Заголовок", "en": "Title"},
                "summary": {"ru": ["Кратко"], "en": ["Summary"]},
                "details": {"ru": ["Подробно"], "en": ["Details"]},
            }],
        }
        (root / "release-notes.json").write_text(json.dumps(notes), encoding="utf-8")
        entries = []
        for file in sorted(root.rglob("*")):
            if file.is_file() and file.name != "nurgling_launcher.jar":
                name = file.relative_to(root).as_posix()
                entries.append(f"{name}={hashlib.sha256(file.read_bytes()).hexdigest()}")
        (root / "ver").write_text("2.103.206\n" + "\n".join(entries) + "\n", encoding="utf-8")
        return root

    def tearDown(self) -> None:
        self.tempdir.cleanup()

    def test_accepts_complete_release(self) -> None:
        self.assertEqual("2.103.206", verify_release(self.make_release(), "2.103.206"))

    def test_rejects_duplicate_manifest_key(self) -> None:
        root = self.make_release()
        with (root / "ver").open("a", encoding="utf-8") as stream:
            stream.write("hafen.jar=" + "0" * 64 + "\n")
        with self.assertRaisesRegex(VerificationError, "duplicate"):
            verify_release(root)

    def test_rejects_manifest_hash_mismatch(self) -> None:
        root = self.make_release()
        (root / "hafen.jar").write_bytes(b"modified")
        with self.assertRaisesRegex(VerificationError, "hash mismatch"):
            verify_release(root)

    def test_rejects_version_downgrade(self) -> None:
        root = self.make_release()
        with self.assertRaisesRegex(VerificationError, "older"):
            verify_release(root, minimum_version="2.103.207")

    def test_rejects_manifest_path_traversal(self) -> None:
        root = self.make_release()
        with (root / "ver").open("a", encoding="utf-8") as stream:
            stream.write("../outside=" + "0" * 64 + "\n")
        with self.assertRaisesRegex(VerificationError, "unsafe"):
            verify_release(root)

    def test_rejects_missing_batch_hash(self) -> None:
        root = self.make_release()
        lines = (root / "ver").read_text(encoding="utf-8").splitlines()
        (root / "ver").write_text("\n".join(line for line in lines if not line.startswith("run_updater.bat=")) + "\n", encoding="utf-8")
        with self.assertRaisesRegex(VerificationError, "required"):
            verify_release(root)

    def test_launcher_must_exist_but_is_not_manifested(self) -> None:
        root = self.make_release()
        self.assertEqual("2.103.206", verify_release(root))
        (root / "nurgling_launcher.jar").unlink()
        with self.assertRaisesRegex(VerificationError, "required files"):
            verify_release(root)

    def test_rejects_launcher_manifest_entry(self) -> None:
        root = self.make_release()
        with (root / "ver").open("a", encoding="utf-8") as stream:
            stream.write("nurgling_launcher.jar=" + "0" * 64 + "\n")
        with self.assertRaisesRegex(VerificationError, "must not hash"):
            verify_release(root)

    def test_rejects_missing_wav_manifest_entry(self) -> None:
        root = self.make_release()
        lines = (root / "ver").read_text(encoding="utf-8").splitlines()
        (root / "ver").write_text("\n".join(line for line in lines if not line.startswith("AlarmSounds/")) + "\n", encoding="utf-8")
        with self.assertRaisesRegex(VerificationError, "WAV"):
            verify_release(root)

    def test_rejects_invalid_notes_history(self) -> None:
        root = self.make_release()
        notes = json.loads((root / "release-notes.json").read_text(encoding="utf-8"))
        notes["processed"].append(notes["processed"][0])
        (root / "release-notes.json").write_text(json.dumps(notes), encoding="utf-8")
        self._rehash(root, "release-notes.json")
        with self.assertRaisesRegex(VerificationError, "duplicates"):
            verify_release(root)

    @staticmethod
    def _rehash(root: Path, name: str) -> None:
        lines = (root / "ver").read_text(encoding="utf-8").splitlines()
        digest = hashlib.sha256((root / name).read_bytes()).hexdigest()
        (root / "ver").write_text("\n".join(
            f"{name}={digest}" if line.startswith(f"{name}=") else line for line in lines
        ) + "\n", encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
