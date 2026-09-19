# Glimmer ore heatmap

Date: 2026-09-18

## Goal

When mining, the system line `Something glimmers in the vein.` means ore is still in the rock within Chebyshev distance 3 of the tile that just became floor. Show that as a heatmap on remaining rock so the player can triangulate.

## Scope

World overlay: gold fill plus a heat number on candidate rock tiles. Lives until logout. Toggle in mining settings. Works for manual mining and bots without changing bots.

Out of scope: bot pathing to hot tiles, minimap dots, manual clear button, other syslog lines, translated glimmer text.

## Behavior

- Count a sample only when a mineable rock tile becomes cave floor, not on each swing.
- Chebyshev radius 3: `max(|dx|, |dy|) <= 3` (square 7×7, diagonals included).
- If the glimmer line arrives for that tile: every still-rock tile in the square gets +1 heat.
- If the tile finishes with no glimmer in a short wait: every tile in the square is excluded. Exclusion beats heat.
- Floor and already-mined tiles are never painted. When a candidate is mined, it disappears from the overlay.
- Paint remaining rock with gold intensity by heat and a digit (1, 2, 3, …), same visual language as minesweeper numbers. Minesweeper stays on floor; glimmer stays on walls.
- Overlay lasts until session end (logout / character change). Empty heatmap is invisible.
- Setting off: no new samples, overlay hidden.

## Architecture

Tracker is pure tile math. Overlay only reads a snapshot.

```text
rock -> floor near player
        | wait syslog
        v
GlimmerHeatmap  --+ heat / exclude
        |
        v
virtual gob per candidate tile  (gold fill + digit)
```

- Hook `GameUI.msg` / `ui.msg` for the exact line `Something glimmers in the vein.` Bind it to the most recent pending completed mine tile (message can lag the tile change).
- Detect completion the same way mining bots already do: tile resource before/after is no longer the same mineable rock. Reuse that mineable-rock check, do not invent a second list.
- `GlimmerHeatmap`: list of `{center, positive|negative}`. Derived map: still-rock, not excluded, heat ≥ 1. Tests cover this class only.
- Overlay follows `MinesweeperDangerMarkers`: one virtual gob per visible candidate, sprite with fill + number. Rebuild when the derived map changes. Cap to tiles near the player so a long session cannot spawn thousands of gobs.
- `NConfig.Key` boolean in mining settings, default on.

## Empty and error states

- No samples yet: nothing drawn.
- Glimmer with no pending mine tile: ignore the line.
- Two tiles finishing in one wait window: oldest pending tile consumes the next glimmer or timeout, FIFO.
- Unknown / not-yet-loaded map tile: skip that cell until the grid exists; do not crash.
- Setting toggled off: hide gobs, keep data until logout so turning it back on restores the same heat.

## Testing

New `test/nurgling/.../GlimmerHeatmapTest.java`:

- Chebyshev 3 is a 7×7 square, not Manhattan.
- One glimmer paints remaining rock in that square with heat 1.
- Silent completion excludes that square even if a later glimmer overlaps.
- Mined centers are not painted.
- FIFO bind of a late syslog line to the oldest pending tile.

No overlay widget test.

## Player note

After implementation, add a bilingual `changes/` note: while mining, nearby rock shows a gold heatmap when the vein glimmers, so ore is easier to triangulate.
