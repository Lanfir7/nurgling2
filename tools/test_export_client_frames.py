"""Focused checks for the one-shot frame exporter (no live client required)."""

import csv
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock


MODULE_PATH = Path(__file__).with_name("export_client_frames.py")
SPEC = importlib.util.spec_from_file_location("export_client_frames", MODULE_PATH)
exporter = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(exporter)


class FrameExportTests(unittest.TestCase):
    def test_existing_output_is_rejected_before_attach_or_compile(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "frames.csv"
            output.write_text("existing", encoding="utf-8")
            with mock.patch.object(exporter, "jdk_tools") as tools:
                with self.assertRaisesRegex(exporter.ExportError, "refusing to overwrite"):
                    exporter.export(42, output)
            tools.assert_not_called()
            self.assertEqual(output.read_text(encoding="utf-8"), "existing")

    def test_csv_must_contain_header_and_every_reported_frame(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "frames.csv"
            with output.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.writer(stream)
                writer.writerow(exporter.CSV_HEADER)
            with self.assertRaisesRegex(exporter.ExportError, "row count 0"):
                exporter.validate_csv(output, 1)
            with output.open("a", encoding="utf-8", newline="") as stream:
                writer = csv.writer(stream)
                writer.writerow(["1"] * len(exporter.CSV_HEADER))
            self.assertEqual(exporter.validate_csv(output, 1), 1)
            with self.assertRaisesRegex(exporter.ExportError, "differs from snapshot"):
                exporter.validate_csv(output, 2)
            output.write_text("unrelated\n1,2\n", encoding="utf-8")
            with self.assertRaisesRegex(exporter.ExportError, "header"):
                exporter.validate_csv(output, 1)

    def test_csv_with_epoch_column_is_accepted(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "frames.csv"
            with output.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.writer(stream)
                writer.writerow(exporter.CSV_HEADER_WITH_EPOCH)
                writer.writerow(["1"] * len(exporter.CSV_HEADER_WITH_EPOCH))
            self.assertEqual(exporter.validate_csv(output, 1), 1)

    def test_failure_status_never_reports_success_and_removes_status_file(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "frames.csv"

            def fake_attach(args, timeout):
                if "codex.frameexport.FrameMetricsAttach" in args:
                    Path(args[-1]).write_text("NO_FRAMES|Capture has no frames\n", encoding="utf-8")

            with mock.patch.object(exporter, "jdk_tools", return_value=(Path("javac"), Path("jar"), Path("java"))), \
                    mock.patch.object(exporter, "build_agent", return_value=Path("agent.jar")), \
                    mock.patch.object(exporter, "run_checked", side_effect=fake_attach):
                with self.assertRaisesRegex(exporter.ExportError, "NO_FRAMES"):
                    exporter.export(42, output)
            self.assertFalse(output.exists())
            self.assertFalse(list(Path(temporary).glob(".frame-export-status-*.txt")))

    @unittest.skipUnless(os.environ.get("FRAME_EXPORT_ATTACH_INTEGRATION") == "1",
                         "set FRAME_EXPORT_ATTACH_INTEGRATION=1 to attach to a synthetic JVM")
    def test_one_shot_attach_to_synthetic_visual_client(self):
        javac, _, java = exporter.jdk_tools()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            haven = root / "haven"
            haven.mkdir()
            (haven / "UI.java").write_text("""
package haven;
public class UI {
    private static final UI ACTIVE = new UI();
    public static UI getInstance() { return ACTIVE; }
    public UILoop getLoop() { return new UILoop(); }
}
""", encoding="utf-8")
            (haven / "UILoop.java").write_text("""
package haven;
public class UILoop { public final FrameMetrics metrics = new FrameMetrics(); }
""", encoding="utf-8")
            (haven / "FrameMetrics.java").write_text("""
package haven;
import java.io.IOException;
import java.io.Writer;
public class FrameMetrics {
    public Snapshot snapshot() { return new Snapshot(); }
    public boolean enabled() { return true; }
    public static class Snapshot {
        public int size() { return 1; }
        public String summary() { return "Synthetic frame"; }
        public void writeCsv(Writer writer) throws IOException {
            writer.write("interval_ms,total_ms,tick_ms,draw_ms,submit_ms,sync_wait_ms,limit_wait_ms,background,rendering\\n");
            writer.write("16,10,3,2,1,2,0,false,true\\n");
        }
    }
}
""", encoding="utf-8")
            (root / "FakeClient.java").write_text("""
public class FakeClient {
    public static void main(String[] args) throws Exception {
        System.out.println("READY");
        Thread.sleep(30000);
    }
}
""", encoding="utf-8")
            subprocess.run([str(javac), "-d", str(root),
                            *(str(source) for source in haven.glob("*.java")),
                            str(root / "FakeClient.java")], check=True, timeout=30)
            child = subprocess.Popen([str(java), "-cp", str(root), "FakeClient"],
                                     stdout=subprocess.PIPE, text=True)
            try:
                self.assertEqual(child.stdout.readline().strip(), "READY")
                output = root / "frames.csv"
                path, summary = exporter.export(child.pid, output)
                self.assertEqual(path, output)
                self.assertEqual(summary, "Synthetic frame")
                self.assertEqual(exporter.validate_csv(output, 1), 1)
            finally:
                child.terminate()
                child.wait(timeout=10)


if __name__ == "__main__":
    unittest.main()
