#!/usr/bin/env python3
"""Validate an already-built release directory before it is published."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path, PurePosixPath
from typing import Any


VERSION_RE = re.compile(r"\d+\.\d+\.\d+\Z")
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z")
REQUIRED_FILES = {
    "hafen.jar",
    "nurgling-res.jar",
    "release-notes.json",
}
REQUIRED_UNHASHED_FILES = {"nurgling_launcher.jar"}
REQUIRED_BATCH_FILES = {
    "run_updater.bat",
    "run_updater8.bat",
    "run_updater_latest.bat",
    "run_updater_stable.bat",
}


class VerificationError(ValueError):
    pass


def _safe_manifest_path(value: str) -> PurePosixPath:
    if not value or "\\" in value:
        raise VerificationError(f"unsafe manifest path: {value!r}")
    path = PurePosixPath(value)
    if (
        value != path.as_posix()
        or path.is_absolute()
        or any(part in {"", ".", ".."} or ":" in part for part in path.parts)
    ):
        raise VerificationError(f"unsafe manifest path: {value!r}")
    return path


def _parse_manifest(path: Path) -> tuple[str, dict[str, str]]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise VerificationError(f"cannot read manifest: {exc}") from exc
    if not lines or not VERSION_RE.fullmatch(lines[0].strip()):
        raise VerificationError("manifest first line must be a semantic release version")

    manifest: dict[str, str] = {}
    for number, raw_line in enumerate(lines[1:], start=2):
        if not raw_line:
            raise VerificationError(f"manifest line {number} is empty")
        name, separator, digest = raw_line.partition("=")
        if not separator or not SHA256_RE.fullmatch(digest):
            raise VerificationError(f"manifest line {number} must be path=lowercase-sha256")
        _safe_manifest_path(name)
        if name in manifest:
            raise VerificationError(f"duplicate manifest entry: {name}")
        manifest[name] = digest
    return lines[0].strip(), manifest


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _validate_notes(path: Path) -> None:
    try:
        data: Any = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise VerificationError(f"invalid release-notes.json: {exc}") from exc
    if not isinstance(data, dict) or data.get("schema") != 1:
        raise VerificationError("release-notes.json must contain schema 1")
    processed = data.get("processed")
    releases = data.get("releases")
    if not isinstance(processed, list) or not all(isinstance(item, str) and item for item in processed):
        raise VerificationError("release-notes.json processed history is invalid")
    if len(processed) != len(set(processed)):
        raise VerificationError("release-notes.json processed history contains duplicates")
    if not isinstance(releases, list):
        raise VerificationError("release-notes.json releases history is invalid")
    release_ids: set[str] = set()
    for release in releases:
        if not isinstance(release, dict):
            raise VerificationError("release-notes.json contains an invalid release")
        release_id = release.get("id")
        if not isinstance(release_id, str) or not VERSION_RE.fullmatch(release_id):
            raise VerificationError("release-notes.json release id is invalid")
        if release_id in release_ids:
            raise VerificationError("release-notes.json contains duplicate release ids")
        release_ids.add(release_id)
        for key in ("date", "title", "summary", "details"):
            if key not in release:
                raise VerificationError(f"release-notes.json release {release_id} lacks {key}")
        for key in ("title", "summary", "details"):
            localized = release[key]
            if not isinstance(localized, dict) or not all(lang in localized for lang in ("ru", "en")):
                raise VerificationError(f"release-notes.json release {release_id} has invalid {key}")


def _version_key(version: str) -> tuple[int, int, int]:
    if not VERSION_RE.fullmatch(version):
        raise VerificationError("version must be numeric major.minor.build")
    major, minor, build = (int(part) for part in version.split("."))
    return major, minor, build


def verify_release(
    directory: Path, expected_version: str | None = None, minimum_version: str | None = None
) -> str:
    root = directory.resolve()
    if not root.is_dir():
        raise VerificationError(f"release directory does not exist: {directory}")
    version, manifest = _parse_manifest(root / "ver")
    if expected_version is not None:
        _version_key(expected_version)
        if version != expected_version:
            raise VerificationError(f"manifest version {version} does not match expected version {expected_version}")
    if minimum_version is not None and _version_key(version) < _version_key(minimum_version):
        raise VerificationError(f"manifest version {version} is older than published version {minimum_version}")

    required = REQUIRED_FILES | REQUIRED_BATCH_FILES
    missing = sorted(required - manifest.keys())
    if missing:
        raise VerificationError("manifest lacks required entries: " + ", ".join(missing))
    missing_unhashed = sorted(name for name in REQUIRED_UNHASHED_FILES if not (root / name).is_file())
    if missing_unhashed:
        raise VerificationError("release lacks required files: " + ", ".join(missing_unhashed))
    if "nurgling_launcher.jar" in manifest:
        raise VerificationError("manifest must not hash nurgling_launcher.jar while it may be running")
    if not any(name.startswith("AlarmSounds/") and name.lower().endswith(".wav") for name in manifest):
        raise VerificationError("manifest lacks an AlarmSounds WAV entry")

    for name, expected_hash in manifest.items():
        target = root / _safe_manifest_path(name)
        if target.is_symlink():
            raise VerificationError(f"manifest file must not be a symlink: {name}")
        target = target.resolve()
        try:
            target.relative_to(root)
        except ValueError as exc:
            raise VerificationError(f"manifest path escapes release directory: {name}") from exc
        if not target.is_file():
            raise VerificationError(f"manifest file is missing or not a regular file: {name}")
        actual_hash = _sha256(target)
        if actual_hash != expected_hash:
            raise VerificationError(f"hash mismatch for {name}")

    _validate_notes(root / "release-notes.json")
    return version


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--expected-version")
    parser.add_argument("--minimum-version")
    args = parser.parse_args(argv)
    try:
        version = verify_release(args.directory, args.expected_version, args.minimum_version)
    except VerificationError as exc:
        print(f"release verification failed: {exc}", file=sys.stderr)
        return 1
    print(f"release verified: {version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
