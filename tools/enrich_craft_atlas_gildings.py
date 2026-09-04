#!/usr/bin/env python3
"""Add insertion ranges and matching attributes from Ring of Brodgar to the offline atlas."""

from html.parser import HTMLParser
import json
from pathlib import Path
import re
from urllib.request import Request, urlopen


SOURCE = "https://ringofbrodgar.com/wiki/Gilding"
TARGET = Path(__file__).resolve().parents[1] / "src/nurgling/craftatlas/wiki-reference.json"
CHANCE = re.compile(r"(\d+(?:\.\d+)?)%\s*[-–]\s*(\d+(?:\.\d+)?)%")


class GildingTable(HTMLParser):
    def __init__(self):
        super().__init__()
        self.row = None
        self.cell = None
        self.rows = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "tr":
            self.row = []
        elif tag == "td" and self.row is not None:
            self.cell = {"class": attrs.get("class", ""), "text": []}
        elif tag == "br" and self.cell is not None:
            self.cell["text"].append("\n")

    def handle_data(self, data):
        if self.cell is not None:
            self.cell["text"].append(data)

    def handle_endtag(self, tag):
        if tag == "td" and self.cell is not None:
            self.cell["text"] = "".join(self.cell["text"]).strip()
            self.row.append(self.cell)
            self.cell = None
        elif tag == "tr" and self.row is not None:
            self.rows.append(self.row)
            self.row = None


def main():
    request = Request(SOURCE, headers={"User-Agent": "Nurgling Craft Atlas updater"})
    parser = GildingTable()
    parser.feed(urlopen(request).read().decode("utf-8"))

    by_name = {}
    for row in parser.rows:
        name = next((cell["text"] for cell in row if "Name" in cell["class"]), "")
        chance_text = next((cell["text"] for cell in row if "Gild-Chance" in cell["class"]), "")
        attributes_text = next((cell["text"] for cell in row if "Gild--Attributes" in cell["class"]), "")
        chance = CHANCE.search(chance_text)
        if not name or chance is None:
            continue
        attributes = [value.strip() for value in attributes_text.splitlines() if value.strip()]
        by_name[name] = {
            "min": float(chance.group(1)) / 100,
            "max": float(chance.group(2)) / 100,
            "attributes": attributes,
        }

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    updated = 0
    for entry in data.get("entries", []):
        gilding = by_name.get(entry.get("name"))
        if gilding is not None:
            entry["gilding"] = gilding
            updated += 1
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Updated {updated} gildings in {TARGET}")


if __name__ == "__main__":
    main()
