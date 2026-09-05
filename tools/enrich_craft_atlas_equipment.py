#!/usr/bin/env python3
"""Add equipment recipes from Ring of Brodgar to the offline Craft Atlas."""

from __future__ import annotations

import concurrent.futures
import json
from pathlib import Path
import re
import unicodedata
from urllib.parse import urljoin

from lxml import html
import requests


SOURCE = "https://ringofbrodgar.com/wiki/Tables/Gildable_Equipment"
STATS_SOURCE = "https://ringofbrodgar.com/wiki/Equipment_Table"
TARGET = Path(__file__).resolve().parents[1] / "src/nurgling/craftatlas/wiki-reference.json"
HEADERS = {"User-Agent": "Nurgling Craft Atlas equipment snapshot/1.0"}
CATEGORIES = {
    "Shoes": "equipment-shoes",
    "Pants": "equipment-pants",
    "Shirts": "equipment-shirts",
    "Shoulders": "equipment-shoulders",
    "Hats": "equipment-hats",
    "Capes": "equipment-capes",
    "Cloaks": "equipment-cloaks",
    "Rings": "equipment-rings",
}
STAT_NAMES = {
    "STR": "Strength", "AGI": "Agility", "INT": "Intelligence", "CON": "Constitution",
    "PER": "Perception", "PRC": "Perception", "CHA": "Charisma", "CSM": "Charisma",
    "DEX": "Dexterity", "WIL": "Will", "PSY": "Psyche",
}
CHANCE = re.compile(r"(\d+(?:\.\d+)?)%\s*[-–]\s*(\d+(?:\.\d+)?)%")
QUANTITY = re.compile(r"^(.*?)\s*[x×]\s*(\d+)\s*$", re.I)
LEADING_QUANTITY = re.compile(r"^(\d+)\s*[x×]\s*(.*?)$", re.I)
NUMBER = re.compile(r"[-+]?\d+(?:\.\d+)?")


def clean(value: str) -> str:
    return " ".join(value.split())


def slug(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).lower()
    return re.sub(r"^-+|-+$", "", re.sub(r"[^\w]+", "-", value))


def own_table(heading):
    for sibling in heading.itersiblings():
        if sibling.tag == "h3":
            return None
        if sibling.tag == "table":
            return sibling
    return None


def table_items() -> list[dict]:
    page = html.fromstring(requests.get(SOURCE, headers=HEADERS, timeout=30).content)
    items = []
    for heading in page.xpath("//h3"):
        title = clean(heading.text_content())
        category = CATEGORIES.get(title)
        table = own_table(heading)
        if category is None or table is None:
            continue
        for row in table.xpath(".//tr[td]"):
            cells = row.xpath("./td")
            if len(cells) < 4:
                continue
            link = cells[0].xpath(".//a[@href][1]")
            name = clean(cells[0].text_content())
            chance = CHANCE.search(clean(cells[2].text_content()))
            if not name or not link or chance is None:
                continue
            attributes = [clean(a.text_content()) for a in cells[1].xpath(".//a") if clean(a.text_content())]
            if not attributes:
                attributes = [part.strip() for part in clean(cells[1].text_content()).split(",") if part.strip()]
            items.append({
                "name": name,
                "url": urljoin(SOURCE, link[0].get("href")),
                "categories": [category],
                "slots": clean(cells[3].text_content()),
                "gilding": {
                    "min": float(chance.group(1)) / 100,
                    "max": float(chance.group(2)) / 100,
                    "attributes": attributes,
                },
            })
    return items


def categories_for_slots(slots: str) -> list[str]:
    mapping = {
        "1L": "equipment-hats",
        "3L": "equipment-shoulders",
        "4L": "equipment-shirts",
        "7L": "equipment-rings",
        "7R": "equipment-rings",
        "8L": "equipment-cloaks",
        "10L": "equipment-pants",
        "11L": "equipment-capes",
        "11R": "equipment-shoes",
    }
    result = []
    for code in re.findall(r"(?:1[01]|[1-9])[LR]", slots.upper()):
        category = mapping.get(code)
        if category is not None and category not in result:
            result.append(category)
    return result


def stats_table_items() -> list[dict]:
    page = html.fromstring(requests.get(STATS_SOURCE, headers=HEADERS, timeout=30).content)
    items = {}
    for row in page.xpath("//table//tr[td]"):
        cells = row.xpath("./td")
        if len(cells) < 14:
            continue
        links = [link for link in cells[0].xpath(".//a[@href]") if clean(link.text_content())]
        if not links:
            continue
        name = clean(links[-1].text_content())
        slots = clean(cells[-1].text_content())
        if not name or not slots:
            continue
        items[name] = {
            "name": name,
            "url": urljoin(STATS_SOURCE, links[-1].get("href")),
            "categories": categories_for_slots(slots),
            "slots": slots,
        }
    return list(items.values())


def row_value(document, prefix: str):
    for row in document.xpath("//tr"):
        cells = row.xpath("./th|./td")
        if len(cells) < 2:
            continue
        key = clean(cells[0].text_content())
        if key.startswith(prefix):
            return cells[-1]
    return None


def ingredients(cell) -> list[dict]:
    if cell is None:
        return []
    result = []
    for part in re.split(r"\s*(?:[;,]|\band\b)\s*|[\r\n]+", clean(cell.text_content()), flags=re.I):
        part = part.strip()
        if not part or part.lower() in {"none", "(none)", "unknown", "(none or unknown)"}:
            continue
        match = QUANTITY.match(part)
        leading = LEADING_QUANTITY.match(part) if match is None else None
        name = (match.group(1) if match else leading.group(2) if leading else part).strip()
        quantity = int(match.group(2)) if match else int(leading.group(1)) if leading else 1
        result.append({"resource": "wiki-item:" + slug(name), "name": name, "quantity": quantity})
    return result


def skills(cell) -> list[dict]:
    if cell is None:
        return []
    names = [clean(link.text_content()) for link in cell.xpath(".//a") if clean(link.text_content())]
    if not names:
        text = clean(cell.text_content())
        if text.lower() not in {"none", "(none)", "unknown", "(none or unknown)"}:
            names = [part.strip() for part in text.split(",") if part.strip()]
    return [{"kind": "SKILL", "name": name, "description": "Ring of Brodgar requirement"}
            for name in names]


def production_requirements(cell) -> list[dict]:
    if cell is None:
        return []
    names = [clean(link.text_content()) for link in cell.xpath(".//a") if clean(link.text_content())]
    if not names:
        names = [clean(cell.text_content())]
    names = [part.strip() for name in names
             for part in re.split(r"\s*(?:[;,]|\band\b)\s*", name, flags=re.I) if part.strip()]
    stations = ("anvil", "cauldron", "crucible", "kiln", "oven", "smelter", "forge", "loom",
                "quern", "workbench", "table", "churn", "press", "fireplace")
    result = []
    for name in dict.fromkeys(names):
        kind = "STATION" if any(station in name.casefold() for station in stations) else "TOOL"
        result.append({
            "kind": kind, "resource": "wiki-item:" + slug(name), "name": name,
            "description": "Ring of Brodgar production requirement",
        })
    return result


def normalize_catalog_entry(entry: dict) -> dict:
    normalized_inputs = []
    for source in entry.get("inputs", []):
        source_name = source.get("name", "")
        parts = [part.strip() for part in re.split(r"\s*(?:[;,]|\band\b)\s*", source_name, flags=re.I)
                 if part.strip()]
        for part in parts or [source_name]:
            match = QUANTITY.match(part)
            leading = LEADING_QUANTITY.match(part) if match is None else None
            name = (match.group(1) if match else leading.group(2) if leading else part).strip()
            value = dict(source)
            value["name"] = name
            value["quantity"] = int(match.group(2)) if match else (
                int(leading.group(1)) if leading else int(source.get("quantity", 1)))
            if str(value.get("resource", "")).startswith("wiki-item:"):
                value["resource"] = "wiki-item:" + slug(name)
            normalized_inputs.append(value)
    if normalized_inputs:
        entry["inputs"] = normalized_inputs

    normalized_requirements = []
    for source in entry.get("requirements", []):
        name = source.get("name", "")
        if name.strip().casefold() in {"hand", "none", "nothing", "(none)", "unknown"}:
            continue
        parts = [name]
        if source.get("kind") in {"STATION", "TOOL"}:
            parts = [part.strip() for part in re.split(r"\s*(?:[;,]|\band\b)\s*", name, flags=re.I)
                     if part.strip()]
        for part in parts:
            value = dict(source)
            value["name"] = part
            if str(value.get("resource", "")).startswith("wiki-item:"):
                value["resource"] = "wiki-item:" + slug(part)
            if source.get("kind") in {"STATION", "TOOL"}:
                stations = ("anvil", "cauldron", "crucible", "kiln", "oven", "smelter", "forge", "loom",
                            "quern", "workbench", "table", "churn", "press", "fireplace", "fire")
                value["kind"] = "STATION" if any(station in part.casefold() for station in stations) else "TOOL"
            normalized_requirements.append(value)
    if normalized_requirements:
        entry["requirements"] = normalized_requirements
    return entry


def bonuses(document) -> list[dict]:
    result = []
    for row in document.xpath("//tr"):
        cells = row.xpath("./th|./td")
        if len(cells) != 2:
            continue
        label = clean(cells[0].text_content())
        if not label.endswith(" Bonus"):
            continue
        match = NUMBER.search(clean(cells[1].text_content()))
        if match is None:
            continue
        name = STAT_NAMES.get(label[:-6].strip().upper(), label[:-6].strip())
        result.append({"resource": "equipment:" + slug(name), "name": name, "value": float(match.group())})
    return result


def enrich(item: dict) -> dict:
    document = html.fromstring(requests.get(item["url"], headers=HEADERS, timeout=30).content)
    entry = {
        "id": "wiki:equipment-" + slug(item["name"]),
        "name": item["name"],
        "output": "wiki-item:" + slug(item["name"]),
        "categories": ["equipment"] + item.get("categories", []),
        "description": "Ring of Brodgar: " + item["url"],
        "equipmentSlots": [item["slots"]],
        "inputs": ingredients(row_value(document, "Object(s) Required")),
        "requirements": skills(row_value(document, "Skill(s) Required")),
    }
    if "gilding" in item:
        entry["gilding"] = item["gilding"]
    produced = row_value(document, "Produced By")
    if produced is not None and clean(produced.text_content()).lower() not in {"hand", "none", "nothing", "(none)"}:
        entry["requirements"].extend(production_requirements(produced))
    item_bonuses = bonuses(document)
    if item_bonuses:
        entry["bonuses"] = item_bonuses
    return entry


def main() -> None:
    by_name = {item["name"]: item for item in stats_table_items()}
    for item in table_items():
        previous = by_name.get(item["name"])
        if previous is not None:
            categories = list(dict.fromkeys(previous.get("categories", []) + item.get("categories", [])))
            previous.update(item)
            previous["categories"] = categories
        else:
            by_name[item["name"]] = item
    items = list(by_name.values())
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as executor:
        entries = list(executor.map(enrich, items))
    entries.sort(key=lambda value: (value["categories"][1] if len(value["categories"]) > 1 else "",
                                    value["name"].casefold()))

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    data["entries"] = [entry for entry in data.get("entries", [])
                       if "equipment" not in entry.get("categories", [])] + entries
    data["entries"] = [normalize_catalog_entry(entry) for entry in data["entries"]]
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Added {len(entries)} equipment entries to {TARGET}")


if __name__ == "__main__":
    main()
