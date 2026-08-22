# Builder resource calculator

**Date:** 2026-08-22  
**Status:** draft for review

## Goal

After the user selects a build zone and ghosts appear, the “Add another area?” dialog shows how many buildings fit and the total materials those ghosts need. Amounts come from each builder bot’s current recipe, multiplied by ghost count.

## Non-goals

- Do not change what bots gather or how they build.
- Do not add the calculator to Trellis, Cellar, or the world blueprint editor (they do not use this dialog).
- Do not read live inventory or construction-material zones. Display planned totals only.
- Do not refactor `Build.Ingredient` / `BuildMaterialHelper` into a shared recipe source.

## Data

New `nurgling.actions.bots.BuildRecipes`.

- Key: menu name already passed to `SelectAreaWithLiveGhosts` (`"Drying Frame"`, `"Wooden Chest"`, …).
- Value: ordered list of `(materialId, countPerBuilding)`.
- `totals(buildingName, buildings)` returns the same lines with `countPerBuilding * buildings`.
- Unknown name or `buildings <= 0` → empty list (dialog falls back to the current summary only).

Recipe table (copy from current `Build*.java`, not from wiki):

| Building name     | Materials per 1 |
|-------------------|-----------------|
| Cupboard          | Board 8 |
| Barrel            | Board 5 |
| Cheese Rack       | Board 6, Block 4 |
| Crate             | Board 4 |
| Wooden Chest      | Board 4, Nugget 4 |
| Drying Frame      | Branch 5, Bough 2, String 2 |
| Herbalist Table   | Block 4, Board 4, Finer Plant Fibre 8 |
| Kiln              | Clay 35 |
| Large Chest       | Board 5, Metal Bar 2, Leather 4, Rope 2, Bone Glue 3 |
| Mound Bed         | Mulch 12, Straw 6 |
| Smoke Shed        | Board 12, Block 4, Thatch/Bough 6, Brick 10 |
| Stone Casket      | Stone 20, Nugget 2 |
| Tar Kiln          | Stone 35, Clay 50 |
| Tanning Tub       | Board 4, Block 2 |
| Dream Catcher     | Bough 4, String 2 |

Smoke Shed thatch vs bough is one line: `thatch_or_bough`, count 6. The dialog does not pick which one.

`BuildCupboardFromZone` and `BuildFromLogs` reuse Cupboard / Crate keys.

If a bot recipe later changes, update that bot **and** this table.

## Flow

1. User selects a zone. Ghosts accumulate as today.
2. `SelectAreaWithLiveGhosts` opens `MultiAreaConfirm(buildingName, ghostCount, areaCount)`.
3. Dialog looks up `BuildRecipes.totals(buildingName, ghostCount)`.
4. Buttons and states (`ADD_ANOTHER` / `BUILD` / `CANCELLED`) stay the same.

## UI

Insert between the existing “N area(s) selected, M building(s) queued.” line and the two buttons:

```
{building} — {count}
Need:
{material} — {total}
…
```

Example, 24 drying frames:

```
Drying Frame — 24
Need:
Branch — 120
Bough — 48
String — 48
```

Window grows with `pack()`. Buttons stay “Add another area” / “Start building”.

If the recipe list is empty, omit the calculator block (current dialog).

## Localization

All new strings through `L10n.get`. Keys in `src/lang/messages.properties` and `messages_ru.properties`.

| Key | EN | RU |
|-----|----|----|
| `build.calc.item` | `{0} — {1}` | `{0} — {1} шт` |
| `build.calc.need` | `Need:` | `Нужно:` |
| `build.name.*` | English building name | Russian name |
| `build.mat.*` | English material | Russian material |

`materialId` maps 1:1 to `build.mat.*` (`branch`, `board`, `thatch_or_bough`, …).  
Building menu name maps to `build.name.*` (`drying_frame`, `wooden_chest`, …).

Existing English button captions may stay as they are.

## Error handling

- Missing recipe: no extra lines, no exception.
- `pack()` after adding labels so a long recipe (Large Chest) still fits.
- Localization miss: `L10n` already returns `[key]`; do not crash.

## Testing

Unit tests on `BuildRecipes` only (same style as `FriedFishMaterialsTest`). No GUI.

- Drying Frame × 24 → Branch 120, Bough 48, String 48.
- Cupboard × 1 → Board 8.
- Smoke Shed × 2 → Board 24, Block 8, Thatch/Bough 12, Brick 20.
- Unknown name → empty.
- Zero buildings → empty.
