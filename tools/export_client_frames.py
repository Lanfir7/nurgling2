#!/usr/bin/env python3
"""Export the active visual client's retained frame metrics without using its console.

Uses a one-shot Java Attach agent. No classes are redefined and capture continues.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from uuid import uuid4


ROOT = Path(__file__).resolve().parent.parent
SOURCES = (ROOT / "tools/java/FrameMetricsExport.java",
           ROOT / "tools/java/FrameMetricsAttach.java")
CSV_HEADER = ["interval_ms", "total_ms", "tick_ms", "draw_ms", "submit_ms",
              "sync_wait_ms", "limit_wait_ms", "background", "rendering"]
CSV_HEADER_WITH_EPOCH = CSV_HEADER + ["end_epoch_ms"]


class ExportError(RuntimeError):
    pass


def jdk_tools(jdk_bin: Path | None = None) -> tuple[Path, Path, Path]:
    if jdk_bin is None:
        found = shutil.which("jcmd")
        if not found:
            raise ExportError("jcmd was not found; pass --jdk-bin for a local JDK bin directory")
        jdk_bin = Path(found).resolve().parent
    suffix = ".exe" if os.name == "nt" else ""
    tools = tuple(jdk_bin / (name + suffix) for name in ("javac", "jar", "java"))
    if not all(path.is_file() for path in tools):
        raise ExportError(f"javac, jar and java must all exist beside jcmd in {jdk_bin}")
    return tools


def run_checked(args: list[str], timeout: int) -> None:
    try:
        result = subprocess.run(args, capture_output=True, text=True,
                                timeout=timeout, check=False)
    except subprocess.TimeoutExpired as exc:
        raise ExportError(f"Timed out after {timeout}s: {Path(args[0]).name}") from exc
    except OSError as exc:
        raise ExportError(f"Could not start {Path(args[0]).name}: {exc}") from exc
    if result.returncode:
        detail = (result.stderr or result.stdout).strip()
        raise ExportError(f"{Path(args[0]).name} failed ({result.returncode}): {detail}")


def build_agent(javac: Path, jar: Path, build_root: Path) -> Path:
    digest = hashlib.sha256(b"".join(source.read_bytes() for source in SOURCES)).hexdigest()[:12]
    run_dir = build_root / f"frame-agent-{digest}-{uuid4().hex[:8]}"
    classes = run_dir / "classes"
    classes.mkdir(parents=True)
    manifest = run_dir / "manifest.mf"
    manifest.write_text("Manifest-Version: 1.0\n"
                        "Agent-Class: codex.frameexport.FrameMetricsExport\n\n",
                        encoding="utf-8")
    run_checked([str(javac), "--add-modules", "jdk.attach", "-d", str(classes),
                 *(str(source) for source in SOURCES)], timeout=60)
    output = run_dir / "frame-agent.jar"
    run_checked([str(jar), "--create", "--file", str(output), "--manifest",
                 str(manifest), "-C", str(classes), "."], timeout=30)
    return output


def validate_csv(path: Path, expected_rows: int) -> int:
    if not path.is_file():
        raise ExportError(f"Agent reported success but CSV is missing: {path}")
    with path.open("r", encoding="utf-8", newline="") as stream:
        rows = csv.reader(stream)
        header = next(rows, None)
        if header not in (CSV_HEADER, CSV_HEADER_WITH_EPOCH):
            raise ExportError(f"Exported CSV has no valid frame header: {path}")
        count = 0
        for row in rows:
            if len(row) != len(header):
                raise ExportError(f"Exported CSV contains a malformed row: {path}")
            count += 1
    if count == 0 or count != expected_rows:
        raise ExportError(f"CSV row count {count} differs from snapshot {expected_rows}: {path}")
    return count


def export(pid: int, output: Path | None = None, jdk_bin: Path | None = None) -> tuple[Path, str]:
    if pid <= 0:
        raise ExportError("PID must be a positive integer")
    build_root = ROOT / "build/performance"
    build_root.mkdir(parents=True, exist_ok=True)
    if output is None:
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        output = build_root / f"client-frames-{pid}-{stamp}-{uuid4().hex[:8]}.csv"
    output = output.expanduser().resolve()
    if output.suffix.lower() != ".csv":
        raise ExportError("Output path must end in .csv")
    if output.exists():
        raise ExportError(f"Output already exists; refusing to overwrite: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    javac, jar, java = jdk_tools(jdk_bin)
    agent = build_agent(javac, jar, build_root)
    status = output.parent / f".frame-export-status-{uuid4().hex}.txt"
    try:
        run_checked([str(java), "--add-modules", "jdk.attach", "-cp", str(agent),
                     "codex.frameexport.FrameMetricsAttach", str(pid), str(agent),
                     str(output), str(status)], timeout=60)
        if not status.is_file():
            raise ExportError("Attach returned without agent status; the target may reject dynamic agents")
        code, _, details = status.read_text(encoding="utf-8").strip().partition("|")
        if code != "OK":
            raise ExportError(f"Client frame export {code}: {details}")
        raw_count, separator, summary = details.partition("|")
        if not separator:
            raise ExportError(f"Agent returned malformed success status: {details}")
        validate_csv(output, int(raw_count))
        return output, summary
    finally:
        status.unlink(missing_ok=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pid", type=int, help="PID of the running Java client")
    parser.add_argument("--output", type=Path, help="new CSV path (never overwritten)")
    parser.add_argument("--jdk-bin", type=Path, help="JDK bin directory containing jcmd")
    args = parser.parse_args(argv)
    try:
        path, summary = export(args.pid, args.output, args.jdk_bin)
    except (ExportError, ValueError) as exc:
        print(f"Frame export failed: {exc}", file=sys.stderr)
        return 1
    print(path)
    print(summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
