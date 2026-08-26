# Area ring appearance settings

**Date:** 2026-08-26  
**Status:** implemented

## Goal

Make `NAreaRad` rings (animals and beehives) less harsh by exposing every visual parameter in settings, and show a working mountain-goat ring.

## Non-goals

- Critter click-circles (`NCritterCircle`) stay as they are.
- Do not add per-animal colors (one animal fill/edge pair for all animals).
- Do not restyle trough / mound-bed colors this pass; they only inherit shared band height and line width.
- Do not remove the existing World checkboxes for beehive / trough / mound bed.

## Shared style (all `NAreaRad` overlays)

Stored in `NConfig`, defaults match current look:

| Key | Default | Meaning |
|---|---|---|
| `areaRadBandHeight` | `10` | Cylinder half-height (`+h` / `-h`). Lower = thinner band. Range 1–30. |
| `areaRadLineWidth` | `4` | Edge `LineWidth`. Range 1–10. |
| `areaRadFillAlpha` | `128` | Alpha applied to fill color. Range 0–255. |

## Separate colors

| Key | Default |
|---|---|
| `areaRadAnimalFill` | `(192, 0, 0)` |
| `areaRadAnimalEdge` | `(255, 224, 96)` |
| `areaRadBeehiveFill` | `(0, 163, 192)` |
| `areaRadBeehiveEdge` | `(0, 192, 0)` |

Fill alpha comes from `areaRadFillAlpha`. Edge stays opaque unless the stored color already has alpha.

Animals (`NAreaRange`) use animal colors. Beehives (`NBeehiveRadius`) use beehive colors. Trough / mound bed keep their hardcoded colors.

## UI

All of this lives in the existing **Animal Aggression Radii** panel (`NRingSettings`), same settings category:

1. Shared sliders: band height, line width, fill alpha.
2. Animal fill + edge color pickers.
3. Beehive fill + edge color pickers, plus vis checkbox and radius (default 150) bound to `showBeehiveRadius` / new `beehiveRadius`.
4. Existing per-animal vis + radius list.

World beehive checkbox stays; it is the same `showBeehiveRadius` key.

Changes apply live: overlays rebuild fill/edge materials and band height on tick when config changes. No relog.

## Mountain goat

Game path is `gfx/kritter/goat/wildgoat`. Defaults currently have the dead path `gfx/kritter/wildgoat/wildgoat`.

On load:

- Add `gfx/kritter/goat/wildgoat` with radius 100 if missing.
- If the old wrong path exists, copy vis/radius onto the new entry (if new was just inserted) then drop the old path.

## Overlay

`nurgling.overlays.NAreaRad` reads style from config instead of baking `±10` and `LineWidth(4)` forever. Color ctor still exists for trough / mound bed (their colors), but line width and band height always come from shared keys.

`NBeehiveRadius` uses `beehiveRadius` instead of hardcoded `150f`.

## Tests

- Goat migration: wrong path only → becomes `gfx/kritter/goat/wildgoat` with same vis/radius; both paths → keep new, drop old; already correct → unchanged.
- Style defaults: missing keys resolve to current look (height 10, width 4, alpha 128, current colors).
