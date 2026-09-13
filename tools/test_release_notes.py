import tempfile
import unittest
from pathlib import Path
import json
import subprocess
import sys

from release_notes import load_notes, make_feed


def note(identifier, priority=0):
    return {"id": identifier, "priority": priority,
            "summary": {"ru": identifier, "en": identifier},
            "detail": {"ru": identifier + " detail", "en": identifier + " detail"}}


class ReleaseNotesTest(unittest.TestCase):
    def test_preparation_retains_history_outside_build_and_reuses_it(self):
        script = Path(__file__).with_name("release_notes.py").resolve()
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "changes").mkdir()
            (root / "changes/first.json").write_text(json.dumps(note("first")), encoding="utf-8")
            (root / "build.xml").write_text('<project><property name="version.num" value="1.0"/></project>')
            (root / "build.num").write_text('build.number=1')
            subprocess.run([sys.executable, str(script)], cwd=root, check=True, capture_output=True)
            snapshot = root / "src/nurgling/news/releases.json"
            first = json.loads(snapshot.read_text(encoding="utf-8"))
            self.assertEqual(first["releases"][0]["id"], "1.0.1")
            (root / "build/release-notes.json").unlink()
            (root / "build.num").write_text('build.number=2')
            subprocess.run([sys.executable, str(script)], cwd=root, check=True, capture_output=True)
            self.assertEqual(first, json.loads(snapshot.read_text(encoding="utf-8")))
            (root / "changes/second.json").write_text(json.dumps(note("second")), encoding="utf-8")
            subprocess.run([sys.executable, str(script)], cwd=root, check=True, capture_output=True)
            history = json.loads(snapshot.read_text(encoding="utf-8"))
            self.assertEqual([r["id"] for r in history["releases"]], ["1.0.2", "1.0.1"])

    def test_next_build_flag_prepares_the_version_ant_release_will_create(self):
        script = Path(__file__).with_name("release_notes.py").resolve()
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "changes").mkdir()
            (root / "changes/first.json").write_text(json.dumps(note("first")), encoding="utf-8")
            (root / "build.xml").write_text('<project><property name="version.num" value="1.0"/></project>')
            (root / "build.num").write_text('build.number=7')
            subprocess.run([sys.executable, str(script), "--next-build"], cwd=root,
                           check=True, capture_output=True)
            feed = json.loads((root / "build/release-notes.json").read_text(encoding="utf-8"))
            self.assertEqual("1.0.8", feed["releases"][0]["id"])

    def test_summary_is_bounded_and_details_preserve_every_change(self):
        feed = make_feed([note(str(i)) for i in range(7)], {"schema": 1, "releases": []}, "1.2.3", "2026-09-13")
        self.assertEqual(len(feed["releases"][0]["summary"]["ru"]), 3)
        self.assertEqual(len(feed["releases"][0]["details"]["en"]), 7)

    def test_followup_build_does_not_repeat_news(self):
        first = make_feed([note("first")], {"schema": 1, "releases": []}, "1.0", "2026-09-13")
        self.assertEqual(first, make_feed([note("first")], first, "1.1", "2026-09-14"))
        second = make_feed([note("first"), note("second")], first, "1.1", "2026-09-14")
        self.assertEqual(second["releases"][0]["summary"]["en"], ["second"])
        self.assertEqual(len(second["releases"]), 2)

    def test_same_version_cannot_be_rewritten_with_new_notes(self):
        previous = {"schema": 1, "releases": [{"id": "1.0"}]}
        with self.assertRaises(ValueError):
            make_feed([note("new")], previous, "1.0", "2026-09-13")

    def test_history_limit_keeps_consumed_ids(self):
        previous = {"schema": 1, "releases": [{"id": str(i)} for i in range(30)], "processed": ["old"]}
        feed = make_feed([note("new")], previous, "31", "2026-09-13")
        self.assertEqual(len(feed["releases"]), 30)
        self.assertIn("old", feed["processed"])

    def test_invalid_previous_feed_fails_instead_of_losing_history(self):
        with self.assertRaises(ValueError):
            make_feed([], {"schema": 2, "releases": []}, "1", "2026-09-13")

    def test_validation_and_priority(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)
            (path / "a.json").write_text(json.dumps(note("small", 0)), encoding="utf-8")
            (path / "b.json").write_text(json.dumps(note("major", 10)), encoding="utf-8")
            self.assertEqual(load_notes(path)[0]["id"], "major")
            invalid = note("bad")
            invalid["summary"]["ru"] = "x" * 181
            (path / "c.json").write_text(json.dumps(invalid), encoding="utf-8")
            with self.assertRaises(ValueError):
                load_notes(path)


if __name__ == "__main__":
    unittest.main()
