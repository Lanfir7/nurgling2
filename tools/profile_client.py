"""Record a bounded client JFR and summarize selected performance events."""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import json
from itertools import chain
from pathlib import Path
import re
import shutil
import subprocess
import sys
import threading
import time
from typing import Iterable
from uuid import uuid4


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = ROOT / "build" / "performance"
EVENT_GROUPS = (
    ("jdk.ExecutionSample,jdk.NativeMethodSample", 128),
    ("jdk.ObjectAllocationSample", 1),
    ("jdk.GCPhasePause,jdk.JavaMonitorEnter,jdk.FileRead,jdk.FileWrite,jdk.SocketRead,jdk.SocketWrite", 32),
    ("jdk.ThreadSleep,jdk.ThreadPark,jdk.JavaMonitorWait", 32),
)


class ProfileError(Exception):
    pass


def required_tool(name: str) -> str:
    path = shutil.which(name)
    if not path:
        raise ProfileError(f"JDK tool '{name}' was not found on PATH. Install/use a JDK with JFR tools.")
    return path


def command_error(tool: str, output: str) -> ProfileError:
    lower = output.lower()
    if any(term in lower for term in ("no such process", "not found", "doesn't exist", "does not exist")):
        return ProfileError("Client PID is no longer running. Check the PID and retry.")
    if any(term in lower for term in ("attachnotsupported", "could not attach", "access denied")):
        return ProfileError("Could not attach to the client. Run this tool as the same user as the Java client.")
    if "flight recorder" in lower and ("not available" in lower or "not enabled" in lower):
        return ProfileError("Flight Recorder is unavailable in the target JVM.")
    if tool == "jfr":
        return ProfileError("JFR analysis failed. Check that the recording is complete and readable.")
    return ProfileError("JFR command failed. Check the client PID, JDK version, and target JVM logs.")


def run_checked(args: list[str], *, timeout: int = 30) -> str:
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)
    except FileNotFoundError as exc:
        raise ProfileError(f"JDK tool '{Path(args[0]).name}' was not found on PATH.") from exc
    except subprocess.TimeoutExpired as exc:
        raise ProfileError(f"JDK tool '{Path(args[0]).name}' timed out.") from exc
    if result.returncode:
        raise command_error(Path(args[0]).stem, (result.stderr or "") + "\n" + (result.stdout or ""))
    return result.stdout


def record(pid: int, duration: int, label: str, max_size_mb: int) -> Path:
    if pid <= 0:
        raise ProfileError("PID must be a positive integer.")
    if not 10 <= duration <= 300:
        raise ProfileError("Duration must be 10–300 seconds.")
    if not 16 <= max_size_mb <= 256:
        raise ProfileError("Max size must be 16–256 MB.")
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,32}", label):
        raise ProfileError("Name must be 1–32 ASCII letters, digits, '_' or '-'.")
    jcmd = required_tool("jcmd")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    suffix = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "_" + uuid4().hex[:8]
    name = f"client_{label}_{suffix}"
    path = OUTPUT_DIR / f"{name}.jfr"
    output = run_checked([
        jcmd, str(pid), "JFR.start", f"name={name}", "settings=profile",
        f"duration={duration}s", f'filename="{path}"', f"maxsize={max_size_mb}M",
        "disk=true", "jdk.InitialEnvironmentVariable#enabled=false",
        "jdk.InitialSystemProperty#enabled=false", "jdk.JVMInformation#enabled=false",
        "jdk.SystemProcess#enabled=false", "jdk.InitialSecurityProperty#enabled=false",
        "jdk.StringFlag#enabled=false",
    ])
    if "Started recording" not in output:
        raise command_error("jcmd", output)
    deadline = time.monotonic() + duration + 45
    while time.monotonic() < deadline:
        if path.is_file() and path.stat().st_size > 0:
            return path
        time.sleep(1)
    raise ProfileError("Recording started but its output did not appear in time; check the client and JFR status.")


def seconds(value: object) -> float:
    if isinstance(value, (int, float)):
        return float(value) / 1_000_000_000  # JFR raw timespans are nanoseconds.
    if not isinstance(value, str):
        return 0.0
    value = value.strip()
    iso = re.fullmatch(r"PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?", value)
    if iso:
        return sum(float(part or 0) * factor for part, factor in zip(iso.groups(), (3600, 60, 1)))
    unit = re.fullmatch(r"([\d.]+)\s*(ns|us|µs|ms|s)", value)
    if unit:
        return float(unit[1]) * {"ns": 1e-9, "us": 1e-6, "µs": 1e-6, "ms": 1e-3, "s": 1}[unit[2]]
    return 0.0


def method_name(frame: dict) -> str:
    method = frame.get("method") or {}
    owner = method.get("type") or {}
    return f"{owner.get('name', '?')}.{method.get('name', '?')}"


def thread_name(values: dict) -> str:
    thread = values.get("sampledThread") or values.get("eventThread") or {}
    return thread.get("javaName") or thread.get("osName") or "<unknown thread>"


def summarize(events: Iterable[dict]) -> dict:
    cpu = defaultdict(Counter)
    native = defaultdict(Counter)
    inclusive = defaultdict(Counter)
    allocation = Counter()
    gc_pauses = []
    monitors = []
    ui_monitors = []
    waits = []
    ui_waits = []
    io = []
    counts = Counter()
    for event in events:
        kind = event.get("type", "").rsplit(".", 1)[-1]
        values = event.get("values") or {}
        counts[kind] += 1
        frames = (values.get("stackTrace") or {}).get("frames") or []
        leaf = method_name(frames[0]) if frames else "<no stack>"
        if kind == "ExecutionSample":
            thread = thread_name(values)
            cpu[thread][leaf] += 1
            for method in {method_name(frame) for frame in frames}:
                inclusive[thread][method] += 1
        elif kind == "NativeMethodSample":
            native[thread_name(values)][leaf] += 1
        elif kind == "ObjectAllocationSample":
            # The sampled event's weight estimates allocated bytes; event count is not bytes.
            allocation[(thread_name(values), leaf)] += int(values.get("weight") or 0)
        elif kind == "GCPhasePause":
            gc_pauses.append(seconds(values.get("duration")))
        elif kind == "JavaMonitorEnter":
            monitors.append((seconds(values.get("duration")), thread_name(values), leaf))
            if thread_name(values).startswith("Haven UI"):
                owner = values.get("previousOwner") or {}
                monitor = values.get("monitorClass") or {}
                ui_monitors.append((seconds(values.get("duration")), thread_name(values),
                                    str(values.get("startTime") or "<unknown time>"), leaf,
                                    str(monitor.get("name") or "<unknown lock>"),
                                    "address=" + str(values.get("address", "?")),
                                    "previousOwner=" + str(owner.get("javaName") or "<unknown>")
                                    + "#" + str(owner.get("javaThreadId", "?"))))
        elif kind in ("ThreadSleep", "ThreadPark", "JavaMonitorWait"):
            thread = thread_name(values)
            caller = next((method_name(frame) for frame in frames
                           if method_name(frame).startswith(("haven/", "haven.", "nurgling/", "nurgling."))), leaf)
            item = (seconds(values.get("duration")), kind, thread,
                    str(values.get("startTime") or "<unknown time>"), caller)
            waits.append(item)
            # Long-lived idle background threads must not hide short UI stalls.
            if thread.startswith("Haven UI"):
                ui_waits.append(item)
        elif kind in ("FileRead", "FileWrite", "SocketRead", "SocketWrite"):
            io.append((seconds(values.get("duration")), kind, thread_name(values), leaf))
    return {
        "counts": counts,
        "cpu": cpu,
        "native": native,
        "inclusive": inclusive,
        "allocation": allocation,
        "gc_pauses": gc_pauses,
        "monitors": monitors,
        "ui_monitors": ui_monitors,
        "waits": waits,
        "ui_waits": ui_waits,
        "io": io,
    }


def format_report(summary: dict, path: Path) -> str:
    lines = [f"JFR: {path}", "CPU: Java execution samples by thread (counts, not elapsed time):"]
    cpu = summary["cpu"]
    if cpu:
        for thread, methods in sorted(cpu.items(), key=lambda item: -sum(item[1].values()))[:8]:
            total = sum(methods.values())
            lines.append(f"  {thread}: {total} samples")
            lines.extend(f"    leaf {count:>5}  {method}" for method, count in methods.most_common(5))
            lines.extend(f"    stack {count:>4}  {method}" for method, count in summary["inclusive"][thread].most_common(8))
            client = ((method, count) for method, count in summary["inclusive"][thread].items()
                      if method.startswith(("nurgling/", "nurgling.")))
            lines.extend(f"    client {count:>4} ({count / total:5.1%})  {method}"
                         for method, count in sorted(client, key=lambda item: -item[1])[:8])
    else:
        lines.append("  No execution samples.")
    lines.append("Native method samples (separate from Java execution samples):")
    native = summary["native"]
    if native:
        for thread, methods in sorted(native.items(), key=lambda item: -sum(item[1].values()))[:5]:
            lines.append(f"  {thread}: {sum(methods.values())} samples")
            lines.extend(f"    {count:>5}  {method}" for method, count in methods.most_common(3))
    else:
        lines.append("  None recorded.")
    lines.append("Sampled allocation: estimated bytes from ObjectAllocationSample weights:")
    if summary["allocation"]:
        lines.extend(f"  {weight / 1048576:7.1f} MiB  {thread}  {method}" for (thread, method), weight in summary["allocation"].most_common(8))
    else:
        lines.append("  No sampled allocation events.")
    pauses = summary["gc_pauses"]
    lines.append(f"GC phase pauses: {len(pauses)}; total {sum(pauses) * 1000:.1f} ms; max {max(pauses, default=0) * 1000:.1f} ms")
    for heading, key in (("Longest monitor waits", "monitors"),
                         ("Longest UI monitor acquisition stalls", "ui_monitors"),
                         ("Longest UI sleeps/parks/condition waits", "ui_waits"),
                         ("Longest sleeps/parks/condition waits across threads", "waits"),
                         ("Longest file/socket operations", "io")):
        lines.append(f"{heading} (recorded threshold events only):")
        items = sorted(summary[key], reverse=True)[:8]
        if not items:
            lines.append("  None recorded.")
        for item in items:
            duration, *details = item
            lines.append(f"  {duration * 1000:7.1f} ms  {'  '.join(details)}")
    lines.append("Sleeping/waiting threads and ordinary idle time are not FPS or CPU usage measurements.")
    return "\n".join(lines)


def parse_event_chunks(chunks: Iterable[bytes]) -> Iterable[dict]:
    """Read jfr print's event array without holding the entire JSON output."""
    header = bytearray()
    started = False
    current = bytearray()
    depth = 0
    quoted = False
    escaped = False
    marker = b'"events": ['
    for chunk in chunks:
        if not started:
            header.extend(chunk)
            index = header.find(marker)
            if index < 0:
                if len(header) > 4096:
                    raise ProfileError("JFR returned unexpected JSON structure.")
                continue
            chunk = bytes(header[index + len(marker):])
            header.clear()
            started = True
        start = 0
        for index, char in enumerate(chunk):
            if depth == 0:
                if char == ord("{"):
                    depth = 1
                    start = index
                continue
            if quoted:
                if escaped:
                    escaped = False
                elif char == ord("\\"):
                    escaped = True
                elif char == ord('"'):
                    quoted = False
            elif char == ord('"'):
                quoted = True
            elif char == ord("{"):
                depth += 1
            elif char == ord("}"):
                depth -= 1
                if depth == 0:
                    current.extend(chunk[start:index + 1])
                    try:
                        yield json.loads(current)
                    except (ValueError, TypeError) as exc:
                        raise ProfileError("JFR returned invalid event JSON.") from exc
                    current.clear()
                    start = index + 1
        if depth:
            current.extend(chunk[start:])
            if len(current) > 8 * 1024 * 1024:
                raise ProfileError("A JFR event is unexpectedly large.")
    if not started or depth:
        raise ProfileError("JFR output was incomplete.")


def pipe_chunks(pipe) -> Iterable[bytes]:
    while chunk := pipe.read(65536):
        yield chunk


def jfr_events(jfr: str, path: Path, events: str, depth: int) -> Iterable[dict]:
    try:
        process = subprocess.Popen(
            [jfr, "print", "--json", "--stack-depth", str(depth), "--events", events, str(path)],
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
        )
    except FileNotFoundError as exc:
        raise ProfileError("JFR analysis tool is unavailable.") from exc
    timer = threading.Timer(180, process.kill)
    timer.start()
    try:
        assert process.stdout is not None
        try:
            yield from parse_event_chunks(pipe_chunks(process.stdout))
        except ProfileError as exc:
            if process.poll() is None:
                process.kill()
            process.wait()
            if process.returncode:
                raise ProfileError("JFR analysis failed or timed out. Check that the recording is complete and readable.") from exc
            raise
        process.wait()
        if process.returncode:
            raise ProfileError("JFR analysis failed or timed out. Check that the recording is complete and readable.")
    finally:
        timer.cancel()
        if process.poll() is None:
            process.kill()
            process.wait()
        if process.stdout is not None:
            process.stdout.close()


def analyze(path: Path) -> str:
    if not path.is_file() or path.stat().st_size == 0:
        raise ProfileError("Recording does not exist or is empty; wait until it finishes.")
    jfr = required_tool("jfr")
    events = chain.from_iterable(jfr_events(jfr, path, names, depth) for names, depth in EVENT_GROUPS)
    return format_report(summarize(events), path)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Record and inspect short Java Flight Recorder client profiles.")
    sub = parser.add_subparsers(dest="action", required=True)
    capture = sub.add_parser("record", help="Record a running Java client by explicit PID")
    capture.add_argument("--pid", type=int, required=True)
    capture.add_argument("--duration", type=int, default=30, help="seconds, 10–300 (default: 30)")
    capture.add_argument("--name", default="session", help="short recording label")
    capture.add_argument("--max-size-mb", type=int, default=64, help="JFR retained data cap, 16–256 MB")
    inspect = sub.add_parser("analyze", help="Summarize an existing .jfr recording")
    inspect.add_argument("recording", type=Path)
    args = parser.parse_args(argv)
    try:
        if args.action == "record":
            path = record(args.pid, args.duration, args.name, args.max_size_mb)
            print(f"Recording saved: {path}")
            print(analyze(path))
        else:
            print(analyze(args.recording))
    except ProfileError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
