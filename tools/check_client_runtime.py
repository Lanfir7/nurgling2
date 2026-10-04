"""Refuse to overwrite/delete installed JARs while a JVM is using that runtime."""
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys


def java_processes():
    if os.name == "nt":
        command = ("$ErrorActionPreference='Stop'; Get-CimInstance Win32_Process "
                   "-Filter \"Name = 'java.exe' OR Name = 'javaw.exe'\" | "
                   "Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress")
        result = subprocess.run(["powershell", "-NoProfile", "-Command", command],
                                check=True, capture_output=True, text=True, timeout=20,
                                creationflags=subprocess.CREATE_NO_WINDOW)
        records = json.loads(result.stdout) if result.stdout.strip() else []
        if isinstance(records, dict):
            records = [records]
        for record in records:
            if record.get("CommandLine") is None:
                raise RuntimeError("Cannot inspect a running Java process")
            yield record["ProcessId"], record["CommandLine"], None
    else:
        result = subprocess.run(["ps", "-eo", "pid=,comm=,args="], check=True,
                                capture_output=True, text=True, timeout=20)
        for line in result.stdout.splitlines():
            parts = line.strip().split(None, 2)
            if len(parts) != 3 or Path(parts[1]).name not in ("java", "javaw"):
                continue
            pid = int(parts[0])
            try:
                cwd = Path(f"/proc/{pid}/cwd").resolve(strict=True)
            except (FileNotFoundError, PermissionError):
                cwd = None
            yield pid, parts[2], cwd


def uses_runtime(command, directory, cwd=None):
    directory = str(Path(directory).resolve()).replace("\\", "/")
    command = command.replace("\\", "/")
    if os.name == "nt":
        directory, command = directory.casefold(), command.casefold()
    # Covers -jar and explicit classpaths, including bin/* and bin/classes.
    if re.search(re.escape(directory) + r"(?=[/;:\s\"']|$)", command):
        return True
    # Windows does not expose another process's cwd. Be conservative for the
    # conventional relative bin/... launch rather than risking a live rewrite.
    tokens = shlex.split(command, posix=False)
    for index, token in enumerate(tokens[:-1]):
        if token not in ("-jar", "-cp", "-classpath", "--class-path"):
            continue
        for entry in tokens[index + 1].strip('"\'').split(os.pathsep):
            candidate = Path(entry)
            if not candidate.is_absolute():
                # Resolve ./ and ../ before comparing; on Windows use the
                # project's parent as a conservative cwd when unavailable.
                candidate = (Path(cwd) if cwd is not None else Path(directory).parent) / candidate
            candidate = candidate.resolve()
            if candidate == Path(directory) or Path(directory) in candidate.parents:
                return True
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    try:
        pids = [pid for pid, command, cwd in java_processes()
                if uses_runtime(command, args.directory, cwd)]
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"Cannot verify client runtime safety: {error}", file=sys.stderr)
        return 1
    if pids:
        print("Refusing to modify files of a running client (PID " +
              ", ".join(map(str, pids)) + "). Use ant run to start a separate build, "
              "or close clients using bin before ant bin/clean.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
