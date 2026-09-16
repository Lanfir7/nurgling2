"""Include legacy launch scripts without asking a running updater to replace itself."""
import argparse
import hashlib
from pathlib import Path

SCRIPTS = ("run_updater.bat", "run_updater8.bat", "run_updater_latest.bat", "run_updater_stable.bat")


def finalize(release_dir: Path, manifest: Path) -> None:
    lines = manifest.read_text(encoding="utf-8").splitlines()
    if not lines or not lines[0].strip():
        raise ValueError("Missing release version")
    entries = {}
    for line in lines[1:]:
        name, digest = line.split("=", 1)
        if name in entries:
            raise ValueError("Duplicate manifest entry: " + name)
        if name.lower() == "nurgling_launcher.jar":
            raise ValueError("A running legacy launcher cannot safely replace itself")
        entries[name] = digest
    for name in SCRIPTS:
        entries[name] = hashlib.sha256((release_dir / name).read_bytes()).hexdigest()
    content = lines[0] + "\n" + "".join(name + "=" + digest + "\n" for name, digest in sorted(entries.items()))
    manifest.write_bytes(content.encode("utf-8"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-dir", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    args = parser.parse_args()
    finalize(args.release_dir, args.manifest)
