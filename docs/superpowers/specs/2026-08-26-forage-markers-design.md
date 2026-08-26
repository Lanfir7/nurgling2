# Foraging map markers (Q40+)

**Date:** 2026-08-26  
**Status:** approved design

## Goal

Any world `Pick` of a gob that yields an inventory item with quality ≥ 40 places a persistent labeled map mark (item icon + `q45`) in a dedicated Foraging layer. The big map can toggle the layer, search by name and quality, and delete marks the same way as clay / gemstone labeled marks.

## Non-goals

- Do not use vanilla `MapFile` PMarker/SMarker flags.
- Do not create a second persistence service (`ForageLocationService`).
- Do not mark `Pick up` (boats, skis), inventory flower actions, or garden pots.
- Do not mark items with Q < 40.
- Do not change clay / water / ore / gem / animal mark behavior.

## Trigger

A mark is considered only when all of these hold:

1. Flower menu action name is exactly `Pick` (not `Pick up`).
2. The target is a world gob (`lastActions.gob != null`), not an inventory item.
3. The gob resource name does not contain `gardenpot`.
4. A new item appears in the player main inventory after that Pick.
5. Item quality is known and `quality >= 40`.

Quality arrives later via tooltip (`NGItem.quality`). Wait up to 2 seconds for a non-null quality, then decide. If it never arrives, skip.

Applies to both manual Pick and the Forager bot (`SelectFlowerAction("Pick", gob)`).

## Mark data

Reuse `LabeledMarkService` / `LabeledMinimapMark`.

- `locationId` starts with `forage_` (identifier for the layer, like `animal_`).
- `resourceType` = item display name (e.g. `Blueberries`).
- `label` = `q` + rounded quality, same format as clay (`q45`).
- `iconImage` = item sprite at pick time.
- Coordinates = gob tile in the current map segment (saved at Pick, because the gob may despawn). Persist to the existing labeled-marks file (unlike animals).

`isForageMark(mark)` is `locationId.startsWith("forage_")`.

## Dedup

Radius **11 tiles**, same segment, same `resourceType`, forage marks only.

- Nearby existing Q ≥ new Q → skip the new mark.
- Nearby existing Q < new Q → remove the weaker mark, add the new one.
- Several weaker neighbors in radius → remove all of them, keep the new one.

## Map UI

Same pattern as trees / gemstones on `NMapWnd`:

- Toggle button **Foraging** on the big map. State stored in `NConfig.Key.showForagingIcons` (default `true`). Applied to both minimap and big map (`NMiniMap.showForagingIcons`).
- Left click: show / hide forage marks.
- Right click: open `ForagingSearchWindow`.
- Hide-all markers (`markcfg.hideall`) also hides forage marks.

Drawing goes through existing `drawLabeledMarks`. When the Foraging layer is off, skip `isForageMark`. When other layers are off, forage marks still show if Foraging is on.

Tooltip: `resourceType` + `label` (e.g. `Blueberries q45`).

Deletion: existing Shift+RMB on a labeled mark calls `labeledMarkService.removeMark`. Same in the search window via ×.

## Search

`ForagingSearchWindow` copies `GemstoneSearchWindow`:

- Type dropdown from distinct forage `resourceType` values, plus `Any`.
- Quality threshold field: keep marks with parsed Q **≥** the number (empty = no Q filter).
- Results sorted by Q descending. Left click focuses the mark on the map. × deletes.

The existing bottom map search box also filters forage marks: case-insensitive contains on `resourceType` **or** `label` (so `blue` and `q45` / `45` both work).

## Errors

- No `mmap.sessloc`, no gob coords, or no item sprite → do not place a mark.
- Quality timeout / null / < 40 → do not place a mark.
- Failures in mark placement are swallowed (same as `CheckClay`).

## Tests

Pure logic, no live map:

- `quality >= 40` places; `39.9` does not.
- Dedup: same type within 11 tiles keeps the higher Q; outside 11 tiles both stay; different type both stay.
- `isForageMark` only matches `forage_` ids.
- Search: type filter, Q ≥ threshold, name substring on type/label.
- `Pick` accepted; `Pick up` rejected.
