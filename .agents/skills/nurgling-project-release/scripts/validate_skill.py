from pathlib import Path


skill = Path(__file__).resolve().parents[1] / "SKILL.md"
text = skill.read_text(encoding="utf-8")

required = (
    "python tools/release_notes.py --check",
    "ant release",
    "git add -A",
    "git commit",
    "git push",
    "release/release-notes.json",
    "src/nurgling/news/releases.json",
    "build.num",
    "origin",
)
missing = [item for item in required if item not in text]
if missing:
    raise SystemExit(f"Missing required workflow markers: {missing}")

if text.count("git commit") < 2:
    raise SystemExit("The workflow must define separate source and release commits")

print("Skill workflow markers OK")
