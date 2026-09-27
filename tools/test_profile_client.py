import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import profile_client as profiler


def event(kind, values):
    return {"type": "jdk." + kind, "values": values}


class ProfileClientTests(unittest.TestCase):
    def test_ui_monitor_stall_keeps_time_lock_and_previous_owner(self):
        stall = event("JavaMonitorEnter", {
            "eventThread": {"javaName": "Haven UI thread"},
            "previousOwner": {"javaName": "Loader thread", "javaThreadId": 600},
            "monitorClass": {"name": "nurgling/NUI"}, "address": 2073723129120,
            "duration": "PT3.512S", "startTime": "2026-09-27T17:09:37.587+03:00",
            "stackTrace": {"frames": [{"method": {
                "type": {"name": "haven/UILoop"}, "name": "display"}}]},
        })
        idle = event("JavaMonitorEnter", {
            "eventThread": {"javaName": "background"}, "duration": "PT60S"})
        summary = profiler.summarize([stall] + [idle] * 10)
        self.assertFalse(summary["cpu"])
        report = profiler.format_report(summary, Path("sample.jfr"))
        self.assertIn("Longest UI monitor acquisition stalls", report)
        self.assertIn("2026-09-27T17:09:37.587+03:00", report)
        self.assertIn("nurgling/NUI", report)
        self.assertIn("2073723129120", report)
        self.assertIn("Loader thread#600", report)
        self.assertIn("haven/UILoop.display", report)

    def test_ui_waits_keep_caller_and_time_separate_from_cpu(self):
        frames = [
            {"method": {"type": {"name": "java/lang/Thread"}, "name": "sleep"}},
            {"method": {"type": {"name": "nurgling/navigation/PortalTraversalTracker"},
                        "name": "onGridChanged"}},
        ]
        stamp = "2026-09-27T16:48:44.090+03:00"
        events = [event(kind, {"eventThread": {"javaName": "Haven UI thread"},
                               "duration": "PT0.1S", "startTime": stamp,
                               "stackTrace": {"frames": frames}})
                  for kind in ("ThreadSleep", "ThreadPark", "JavaMonitorWait")]
        events.append(event("ThreadSleep", {"eventThread": {"javaName": "background"},
                                             "duration": "PT60S"}))
        summary = profiler.summarize(events)
        self.assertFalse(summary["cpu"])
        self.assertEqual(3, len(summary["ui_waits"]))
        self.assertEqual(4, len(summary["waits"]))
        report = profiler.format_report(summary, Path("sample.jfr"))
        self.assertIn("Longest UI sleeps/parks/condition waits", report)
        self.assertIn(stamp, report)
        self.assertIn("PortalTraversalTracker.onGridChanged", report)
        for kind in ("ThreadSleep", "ThreadPark", "JavaMonitorWait"):
            self.assertIn(kind, report)
            self.assertTrue(any("jdk." + kind in events for events, _ in profiler.EVENT_GROUPS))

    def test_summarizes_samples_weights_and_stalls(self):
        frame = {"method": {"type": {"name": "haven.UI"}, "name": "draw"}}
        stack = {"frames": [frame]}
        thread = {"javaName": "Haven UI"}
        summary = profiler.summarize([
            event("ExecutionSample", {"sampledThread": thread, "stackTrace": stack}),
            event("ExecutionSample", {"sampledThread": thread, "stackTrace": stack}),
            event("ObjectAllocationSample", {"eventThread": thread, "stackTrace": stack, "weight": 1048576}),
            event("GCPhasePause", {"duration": "PT0.012S"}),
            event("JavaMonitorEnter", {"eventThread": thread, "duration": "30 ms", "stackTrace": stack}),
            event("FileRead", {"eventThread": thread, "duration": 20000000, "stackTrace": stack}),
            event("ThreadSleep", {"duration": "1 s"}),
        ])
        self.assertEqual(2, summary["cpu"]["Haven UI"]["haven.UI.draw"])
        self.assertEqual(2, summary["inclusive"]["Haven UI"]["haven.UI.draw"])
        self.assertEqual(1048576, summary["allocation"][("Haven UI", "haven.UI.draw")])
        self.assertEqual([0.012], summary["gc_pauses"])
        self.assertAlmostEqual(0.03, summary["monitors"][0][0])
        self.assertAlmostEqual(0.02, summary["io"][0][0])
        report = profiler.format_report(summary, Path("sample.jfr"))
        self.assertIn("counts, not elapsed time", report)
        self.assertIn("1.0 MiB", report)
        self.assertIn("Sleeping/waiting", report)

    def test_client_inclusive_methods_show_thread_sample_share(self):
        frame = lambda name: {"method": {"type": {"name": "nurgling/NConfig"}, "name": name}}
        sample = event("ExecutionSample", {"sampledThread": {"javaName": "Haven UI"},
                                           "stackTrace": {"frames": [frame("write"), frame("write")]}})
        summary = profiler.summarize([sample, sample])
        report = profiler.format_report(summary, Path("sample.jfr"))
        self.assertEqual(2, summary["inclusive"]["Haven UI"]["nurgling/NConfig.write"])
        self.assertIn("client    2 (100.0%)  nurgling/NConfig.write", report)

    def test_jcmd_failure_has_helpful_message_without_raw_output(self):
        result = subprocess.CompletedProcess(["jcmd"], 1, "secret=value", "No such process")
        with patch.object(profiler.subprocess, "run", return_value=result):
            with self.assertRaisesRegex(profiler.ProfileError, "PID is no longer running") as raised:
                profiler.run_checked(["jcmd", "999", "JFR.start"])
        self.assertNotIn("secret", str(raised.exception))

    def test_record_validates_bounds_before_starting_tool(self):
        with patch.object(profiler, "required_tool") as tool:
            for args in ((1, 9, "x", 64), (1, 30, "bad name", 64), (1, 30, "x", 257)):
                with self.assertRaises(profiler.ProfileError):
                    profiler.record(*args)
            tool.assert_not_called()

    def test_missing_jfr_tool_reports_jdk_requirement(self):
        with patch.object(profiler.shutil, "which", return_value=None):
            with self.assertRaisesRegex(profiler.ProfileError, "JDK tool 'jfr'"):
                profiler.required_tool("jfr")

    def test_record_uses_quoted_unique_path_and_disables_sensitive_events(self):
        with tempfile.TemporaryDirectory(prefix="client profile ") as directory:
            output_dir = Path(directory)

            def fake_run(args):
                filename = next(arg for arg in args if arg.startswith("filename="))
                Path(filename.removeprefix('filename="').removesuffix('"')).write_bytes(b"jfr")
                return "Started recording 1."

            with patch.object(profiler, "OUTPUT_DIR", output_dir), \
                 patch.object(profiler, "required_tool", return_value="jcmd"), \
                 patch.object(profiler, "run_checked", side_effect=fake_run) as command:
                path = profiler.record(12345, 10, "walk", 64)
            self.assertEqual(output_dir, path.parent)
            self.assertIn("client_walk_", path.name)
            args = command.call_args.args[0]
            self.assertIn(f'filename="{path}"', args)
            self.assertIn("jdk.InitialEnvironmentVariable#enabled=false", args)
            self.assertIn("jdk.InitialSystemProperty#enabled=false", args)
            self.assertIn("jdk.SystemProcess#enabled=false", args)
            self.assertIn("jdk.InitialSecurityProperty#enabled=false", args)
            self.assertIn("jdk.StringFlag#enabled=false", args)

    def test_stream_parser_handles_chunks_and_quoted_braces(self):
        data = b'{"recording":{"events": [{"type":"jdk.X","values":{"s":"} \\\" {"}}, {"type":"jdk.Y","values":{}}]}}'
        events = list(profiler.parse_event_chunks(data[index:index + 7] for index in range(0, len(data), 7)))
        self.assertEqual(["jdk.X", "jdk.Y"], [event["type"] for event in events])
        self.assertEqual('} " {', events[0]["values"]["s"])

    def test_stream_parser_rejects_incomplete_event(self):
        with self.assertRaisesRegex(profiler.ProfileError, "incomplete"):
            list(profiler.parse_event_chunks([b'{"recording":{"events": [{"type":"jdk.X"']))

    def test_jfr_process_failure_is_reported_without_raw_output(self):
        process = subprocess.Popen(["python", "-c", "import sys; sys.exit(2)"], stdout=subprocess.PIPE)
        with patch.object(profiler.subprocess, "Popen", return_value=process):
            with self.assertRaisesRegex(profiler.ProfileError, "JFR analysis failed"):
                list(profiler.jfr_events("jfr", Path("sample.jfr"), "jdk.ExecutionSample", 128))


if __name__ == "__main__":
    unittest.main()
