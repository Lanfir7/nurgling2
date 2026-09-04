#!/usr/bin/env python3
"""Build compact offline icon sheets for the bundled Craft Atlas wiki snapshot."""

from __future__ import annotations

import concurrent.futures
import hashlib
import io
import json
import pathlib
import re
import time
import urllib.parse
import urllib.request

from PIL import Image, ImageDraw, ImageFont


ROOT = pathlib.Path(__file__).resolve().parents[1]
CATALOG = ROOT / "src/nurgling/craftatlas/wiki-reference.json"
OUTPUT = ROOT / "src/nurgling/craftatlas"
CELL = 64
SHEET = 1024
PER_ROW = SHEET // CELL
USER_AGENT = "Nurgling Craft Atlas icon snapshot/1.0"


def names_from_catalog() -> list[str]:
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    names: set[str] = set()
    for entry in data["entries"]:
        names.add(entry["name"])
        names.update(item["name"] for item in entry.get("inputs", []))
        names.update(req["name"] for req in entry.get("requirements", []) if req.get("resource"))
    return sorted(names, key=str.casefold)


def wiki_url(name: str) -> str:
    filename = urllib.parse.quote(name.replace(" ", "_") + ".png", safe="")
    return f"https://ringofbrodgar.com/wiki/Special:Redirect/file/{filename}?width=64"


def fetch_icon(name: str) -> tuple[str, Image.Image | None]:
    candidates = [name]
    if ";" in name:
        candidates.append(name.split(";", 1)[0].strip())
    cleaned = re.sub(r"^\d+\s+", "", name).strip()
    cleaned = re.sub(r"\s+x\d+(?:[.,]\d+)?(?:\s*(?:kg|l|liters?))?$", "", cleaned, flags=re.I).strip()
    cleaned = re.sub(r"\s*\([^)]*(?:kg|liters?|\bx\d+|\bwith\b)[^)]*\)\s*$", "", cleaned, flags=re.I).strip()
    cleaned = re.sub(r"\s+\d+(?:[.,]\d+)?\s*(?:kg|l)\s*$", "", cleaned, flags=re.I).strip()
    if cleaned and cleaned not in candidates:
        candidates.append(cleaned)
    if cleaned.lower().startswith("any "):
        candidates.append(cleaned[4:].strip())
    for candidate in candidates:
        request = urllib.request.Request(wiki_url(candidate), headers={"User-Agent": USER_AGENT})
        for attempt in range(2):
            try:
                with urllib.request.urlopen(request, timeout=20) as response:
                    image = Image.open(io.BytesIO(response.read())).convert("RGBA")
                    image.load()
                    return name, image
            except Exception:
                if attempt == 0:
                    time.sleep(0.15)
    return name, None


def placeholder(name: str) -> Image.Image:
    digest = hashlib.sha256(name.encode("utf-8")).digest()
    color = (48 + digest[0] % 96, 58 + digest[1] % 88, 62 + digest[2] % 82, 255)
    image = Image.new("RGBA", (CELL, CELL), (18, 23, 27, 255))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((4, 4, CELL - 5, CELL - 5), radius=9, fill=color,
                           outline=(202, 166, 92, 220), width=2)
    words = [part for part in name.replace("-", " ").split() if part]
    label = "".join(part[0] for part in words[:2]).upper() or "?"
    font = ImageFont.load_default(size=20)
    box = draw.textbbox((0, 0), label, font=font)
    draw.text(((CELL - (box[2] - box[0])) / 2, (CELL - (box[3] - box[1])) / 2 - 2),
              label, font=font, fill=(247, 244, 227, 255))
    return image


def fitted(image: Image.Image | None, name: str) -> Image.Image:
    if image is None:
        return placeholder(name)
    result = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    image.thumbnail((CELL - 8, CELL - 8), Image.Resampling.LANCZOS)
    result.alpha_composite(image, ((CELL - image.width) // 2, (CELL - image.height) // 2))
    return result


def main() -> None:
    names = names_from_catalog()
    images: dict[str, Image.Image | None] = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as executor:
        for name, image in executor.map(fetch_icon, names):
            images[name] = image

    for old in OUTPUT.glob("wiki-icons-*.png"):
        old.unlink()
    sheets: list[Image.Image] = []
    manifest: dict[str, dict[str, int | bool]] = {}
    cells_per_sheet = PER_ROW * PER_ROW
    for index, name in enumerate(names):
        sheet_index = index // cells_per_sheet
        while len(sheets) <= sheet_index:
            sheets.append(Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0)))
        cell_index = index % cells_per_sheet
        x = (cell_index % PER_ROW) * CELL
        y = (cell_index // PER_ROW) * CELL
        sheets[sheet_index].alpha_composite(fitted(images[name], name), (x, y))
        manifest[name] = {"sheet": sheet_index, "x": x, "y": y,
                          "fallback": images[name] is None}

    sheet_names = []
    for index, sheet in enumerate(sheets):
        filename = f"wiki-icons-{index}.png"
        sheet.save(OUTPUT / filename, optimize=True)
        sheet_names.append(filename)
    payload = {
        "source": "https://ringofbrodgar.com/",
        "cell": CELL,
        "sheets": sheet_names,
        "icons": manifest,
    }
    (OUTPUT / "wiki-icons.json").write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    loaded = sum(image is not None for image in images.values())
    print(f"Craft Atlas icons: {loaded}/{len(names)} wiki images, {len(sheets)} sheets")


if __name__ == "__main__":
    main()
