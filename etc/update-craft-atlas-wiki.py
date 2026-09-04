#!/usr/bin/env python3
"""Build the offline Craft Atlas reference snapshot from Ring of Brodgar."""

import argparse
import datetime as dt
import json
import re
import time
import unicodedata
import urllib.parse
import urllib.request
from pathlib import Path


API = "https://ringofbrodgar.com/api.php"
SOURCE = "https://ringofbrodgar.com/"
USER_AGENT = "Nurgling-Craft-Atlas/1.0 (offline reference snapshot)"
CATEGORIES = {
    "Category:Foods": "foods",
    "Category:Gilding Objects": "gildings",
}
FOOD_STATS = {
    "str": "Strength", "agi": "Agility", "int": "Intelligence", "con": "Constitution",
    "per": "Perception", "cha": "Charisma", "dex": "Dexterity", "wil": "Will", "psy": "Psyche",
}


def api(params):
    query = dict(params)
    query.update({"format": "json", "formatversion": "2"})
    request = urllib.request.Request(API + "?" + urllib.parse.urlencode(query), headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)


def category_members(category):
    titles, continuation = [], None
    while True:
        params = {"action": "query", "list": "categorymembers", "cmtitle": category,
                  "cmnamespace": "0", "cmlimit": "500"}
        if continuation:
            params["cmcontinue"] = continuation
        result = api(params)
        titles.extend(row["title"] for row in result["query"]["categorymembers"])
        continuation = result.get("continue", {}).get("cmcontinue")
        if not continuation:
            return titles


def page_sources(titles):
    pages = {}
    for offset in range(0, len(titles), 40):
        result = api({"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
                      "redirects": "1", "titles": "|".join(titles[offset:offset + 40])})
        for page in result.get("query", {}).get("pages", []):
            revisions = page.get("revisions") or []
            if revisions:
                pages[page["title"]] = revisions[0]["slots"]["main"].get("content", "")
        time.sleep(0.05)
    return pages


def infobox_fields(text):
    match = re.search(r"\{\{\s*infobox\s+metaobj\b", text, re.I)
    if not match:
        return {}
    start, pos, depth = match.start(), match.start(), 0
    while pos < len(text) - 1:
        pair = text[pos:pos + 2]
        if pair == "{{":
            depth += 1
            pos += 2
            continue
        if pair == "}}":
            depth -= 1
            pos += 2
            if depth == 0:
                block = text[start:pos - 2]
                break
            continue
        pos += 1
    else:
        return {}
    fields = {}
    pattern = re.compile(r"^\s*\|\s*([\w]+)\s*=\s*(.*?)(?=^\s*\|\s*[\w]+\s*=|\Z)", re.M | re.S)
    for key, value in pattern.findall(block):
        fields[key.lower()] = value.strip().rstrip("| \t\r\n").strip()
    return fields


def slug(value):
    normalized = unicodedata.normalize("NFKC", value or "").lower()
    return re.sub(r"^-+|-+$", "", re.sub(r"[^\w]+", "-", normalized, flags=re.UNICODE))


def item_resource(name):
    return "wiki-item:" + slug(name)


def clean(value, title=""):
    value = (value or "").replace("{{PAGENAME}}", title).replace("{{PAGENAMEE}}", title.replace(" ", "_"))
    value = re.sub(r"<!--.*?-->", "", value, flags=re.S)
    value = re.sub(r"\[\[[^\]|]*::([^\]|]+)(?:\|([^\]]+))?\]\]", lambda m: m.group(2) or m.group(1), value)
    value = re.sub(r"\[\[([^\]|]+)(?:\|([^\]]+))?\]\]", lambda m: m.group(2) or m.group(1), value)
    value = re.sub(r"\{\{[^{}]*\}\}", "", value)
    value = re.sub(r"<[^>]+>", " ", value)
    return re.sub(r"\s+", " ", value).strip(" ,\n\t")


def linked_values(value, title=""):
    output = []
    for part in re.split(r",\s*(?![^\[]*\]\])", value or ""):
        links = re.findall(r"\[\[(?:[^\]|]*::)?([^\]|]+)(?:\|([^\]]+))?\]\]", part)
        if links:
            target, label = links[0]
            name = clean(label or target, title)
            rendered = clean(part, title)
            if rendered and rendered != name:
                name = rendered
            output.append((clean(target, title), name))
        else:
            name = clean(part, title)
            if name and name.lower() not in {"none", "unknown", "(none or unknown)"}:
                output.append((name, name))
    return output


def number(value):
    match = re.search(r"[-+]?\d+(?:\.\d+)?", clean(value))
    return float(match.group(0)) if match else None


def referenced_recipe(value, title):
    name = clean(value, title).replace("_", " ")
    return re.sub(r"\s+", " ", name).strip() or None


def make_entry(title, text, categories):
    fields = infobox_fields(text)
    entry = {
        "id": "wiki:" + slug(title),
        "name": title,
        "output": item_resource(title),
        "categories": sorted(categories),
        "description": "Ring of Brodgar: " + SOURCE + "wiki/" + urllib.parse.quote(title.replace(" ", "_")),
        "inputs": [],
        "requirements": [],
        "bonuses": [],
    }
    for target, name in linked_values(fields.get("objectsreq", ""), title):
        entry["inputs"].append({"resource": item_resource(target), "name": name, "quantity": 1})
    for target, name in linked_values(fields.get("skillreq", ""), title):
        entry["requirements"].append({"kind": "SKILL", "name": name, "description": "Ring of Brodgar"})
    for target, name in linked_values(fields.get("discoveryreq", ""), title):
        entry["requirements"].append({"kind": "DISCOVERY", "name": name, "description": "Ring of Brodgar"})
    for target, name in linked_values(fields.get("producedby", ""), title):
        if name.lower() != "hand":
            entry["requirements"].append({"kind": "STATION", "resource": item_resource(target), "name": name,
                                          "description": "Ring of Brodgar"})

    if "gildings" in categories:
        chance = clean(fields.get("gildpct", ""), title)
        if chance:
            entry["bonuses"].append({"resource": "gild:chance", "name": "Gild chance: " + chance})
        for key in ("gild1", "gild2", "gild3", "gild4"):
            rendered = clean(fields.get(key, ""), title)
            match = re.match(r"(.+?)\s*([+-]\s*\d+(?:\.\d+)?)\s*$", rendered)
            if match:
                attribute = match.group(1).strip()
                entry["bonuses"].append({"resource": "gild:" + slug(attribute), "name": attribute,
                                         "value": float(match.group(2).replace(" ", ""))})
            elif rendered:
                entry["bonuses"].append({"resource": "gild:" + slug(rendered), "name": rendered})

    if "foods" in categories:
        for field, label in (("energy", "Energy"), ("hunger", "Hunger")):
            value = number(fields.get(field, ""))
            if value is not None:
                entry["bonuses"].append({"resource": "food:" + field, "name": label, "value": value})
        for field, label in FOOD_STATS.items():
            value = number(fields.get(field, ""))
            if value not in (None, 0.0):
                entry["bonuses"].append({"resource": "food:" + field, "name": label, "value": value})
    return entry, referenced_recipe(fields.get("inc_recipe", ""), title)


def build():
    category_by_title = {}
    for category, atlas_category in CATEGORIES.items():
        for title in category_members(category):
            category_by_title.setdefault(title, set()).add(atlas_category)

    sources = page_sources(sorted(category_by_title))
    references = set()
    for title, text in sources.items():
        recipe = referenced_recipe(infobox_fields(text).get("inc_recipe", ""), title)
        if recipe:
            references.add(recipe)
    missing = sorted(references - set(sources))
    if missing:
        sources.update(page_sources(missing))
    for title in missing:
        category_by_title.setdefault(title, set()).add("foods")

    entries = []
    for title in sorted(category_by_title, key=str.casefold):
        entry, _ = make_entry(title, sources.get(title, ""), category_by_title[title])
        entries.append(entry)
    return {
        "source": SOURCE,
        "generatedAt": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat(),
        "entries": entries,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="src/nurgling/craftatlas/wiki-reference.json")
    args = parser.parse_args()
    payload = build()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(payload['entries'])} entries to {output}")


if __name__ == "__main__":
    main()
