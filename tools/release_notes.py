"""Build the small, offline player feed from reviewed, immutable change notes."""
import argparse
import datetime
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

LANGUAGES = ("ru", "en")
MAX_RELEASES = 30


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def localized(value, limit, label):
    if not isinstance(value, dict):
        raise ValueError(f"{label}: expected ru/en text")
    for language in LANGUAGES:
        text = value.get(language)
        if not isinstance(text, str) or not text.strip() or len(text) > limit:
            raise ValueError(f"{label}.{language}: expected 1–{limit} characters")
        if any(ord(c) < 32 for c in text):
            raise ValueError(f"{label}.{language}: use one plain-text paragraph")
    return {language: value[language].strip() for language in LANGUAGES}


def load_notes(directory):
    if not Path(directory).is_dir():
        raise ValueError(f"Notes directory does not exist: {directory}")
    notes = []
    ids = set()
    for path in sorted(Path(directory).glob("*.json")):
        note = read_json(path)
        note_id = note.get("id", "")
        if not re.fullmatch(r"[a-z0-9][a-z0-9-]{2,99}", note_id) or note_id in ids:
            raise ValueError(f"{path}: invalid or duplicate id")
        ids.add(note_id)
        priority = note.get("priority", 0)
        if type(priority) is not int or not 0 <= priority <= 10:
            raise ValueError(f"{path}: priority must be 0–10")
        notes.append({"id": note_id, "priority": priority,
                      "summary": localized(note.get("summary"), 180, str(path)),
                      "detail": localized(note.get("detail"), 500, str(path))})
    return sorted(notes, key=lambda note: (-note["priority"], note["id"]))


def make_feed(notes, previous, version, date):
    if previous.get("schema") != 1 or not isinstance(previous.get("releases"), list):
        raise ValueError("Unsupported previous release feed")
    processed = previous.get("processed", [])
    if not isinstance(processed, list) or not all(isinstance(i, str) for i in processed):
        raise ValueError("Invalid processed note ids")
    releases = previous["releases"]
    if any(not isinstance(r, dict) or not isinstance(r.get("id"), str) for r in releases):
        raise ValueError("Invalid previous release entries")
    consumed = set(processed)
    pending = [note for note in notes if note["id"] not in consumed]
    if pending:
        if any(r["id"] == version for r in releases):
            raise ValueError("Version already published; increment it before adding notes")
        entry = {"id": version, "date": date,
                 "title": {"ru": "Новое в клиенте", "en": "Client updates"},
                 "summary": {lang: [n["summary"][lang] for n in pending[:3]] for lang in LANGUAGES},
                 "details": {lang: [n["detail"][lang] for n in pending] for lang in LANGUAGES}}
        releases = [entry] + releases
    return {"schema": 1, "processed": sorted(set(processed) | {n["id"] for n in pending}),
            "releases": releases[:MAX_RELEASES]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--notes", default="changes")
    parser.add_argument("--previous", default="release/release-notes.json")
    parser.add_argument("--output", default="build/release-notes.json")
    parser.add_argument("--snapshot", default="src/nurgling/news/releases.json")
    parser.add_argument("--next-build", action="store_true",
                        help="prepare notes for the build number ant release will create")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    notes = load_notes(args.notes)
    if args.check:
        print(f"Validated {len(notes)} player notes")
        return
    version_base = next(p.attrib["value"] for p in ET.parse("build.xml").getroot().findall("property")
                        if p.attrib.get("name") == "version.num")
    match = re.search(r"^build.number\s*=\s*(\d+)\s*$", Path("build.num").read_text(), re.M)
    if not match:
        raise ValueError("build.num must contain build.number")
    build_number = int(match[1]) + (1 if args.next_build else 0)
    version = f"{version_base}.{build_number}"
    history = Path(args.previous) if Path(args.previous).exists() else Path(args.snapshot)
    previous = read_json(history) if history.exists() else {"schema": 1, "releases": []}
    feed = make_feed(notes, previous, version, datetime.datetime.now(datetime.timezone.utc).date().isoformat())
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    serialized = json.dumps(feed, ensure_ascii=False, indent=2) + "\n"
    output.write_text(serialized, encoding="utf-8")
    # Keep the built history with the source, so ordinary builds and clean builds
    # retain it without turning new change fragments into release announcements.
    snapshot = Path(args.snapshot)
    snapshot.parent.mkdir(parents=True, exist_ok=True)
    snapshot.write_text(serialized, encoding="utf-8")
    print(f"Prepared player feed for {version}: {len(feed['releases'])} releases")


if __name__ == "__main__":
    main()
