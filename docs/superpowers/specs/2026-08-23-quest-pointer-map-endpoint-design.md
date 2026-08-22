# Quest pointer map endpoint

**Date:** 2026-08-23  
**Status:** draft for review

## Goal

Ctrl/RMB on a quest-giver compass pointer draws a **finite** line on the map, length equal to the distance shown on the icon (e.g. `Ekhagen (1306.7m)`), with a treasure-style **X** at the end.

## Non-goals

- Do not change dowsing / prospecting rays (they stay infinite).
- Do not draw the mark in the 3D world, only on the map (minimap / map window).
- Do not change Clear UX, colors, or `TrackingVectorWindow`.
- Do not require Ctrl; keep the existing pointer right-click that already draws a vector.

## Data

Extend `nurgling.tools.DirectionalVector` with `boolean showEndpoint` (default `false`).

- `false` — current infinite ray (`getTilePointAt(10000)`).
- `true` — line from `originTileCoords` to `targetTileCoords`, plus an X at the target.

Endpoint is the click’s existing `Pointer.tc()` converted to tile coords (same as today). Tooltip meters are `player.rc.dist(tc) / 11.0`, so that point already sits at the displayed distance. The line must end at `targetTileCoords`, not at a 10000-tile far point.

Dowsing (`NProspecting.addConeVectors`) keeps constructing vectors without `showEndpoint`.

## Flow

1. User right-clicks the compass icon (`Pointer.mousedown`, button 3).
2. `NPointerClickHandler.handleRightClick` is unchanged. It already calls `addDirectionalVector`, which now sets `showEndpoint=true`. Dowsing adds vectors directly and never goes through this method.
3. `NMiniMap` draws:
   - `showEndpoint`: clip origin→target, draw the line, draw an X in `vector.color` at the target screen position (even if the line is clipped, draw the X when the target is on-screen).
   - otherwise: existing infinite ray.
4. Clear (`TrackingVectorWindow` / map “clear vectors”) removes all vectors, including endpoint marks.

Origin stays fixed at the click position (does not follow the player), same as current rays.

## UI

X: two short crossing strokes, same color/alpha as the line, size ~8–12 UI-scaled pixels so it stays readable on zoomed map. No extra label; the vector already has `targetName`.

## Errors

- No GUI / map / player / `sessloc`: return, draw nothing (current handler).
- Distance unavailable or origin equals target: skip adding the vector (current `equals` guard).
- Draw errors on one vector: skip that vector, continue the rest (current try/catch).

## Tests

Pure unit tests on `DirectionalVector` (no game UI):

- `showEndpoint=false` → `getTilePointAt(10000)` still used by drawing contract; flag is false.
- `showEndpoint=true` → endpoint equals constructor `targetTileCoords`.
- Dowsing-style constructor (no flag) → `showEndpoint` is false.

Do not UI-test minimap drawing.

## Files

- `src/nurgling/tools/DirectionalVector.java` — flag + constructors (default `false`).
- `src/nurgling/NMapView.java` — `addDirectionalVector` always sets `showEndpoint=true`.
- `src/nurgling/widgets/NMiniMap.java` — finite line + X when the flag is set.
- `test/nurgling/tools/DirectionalVectorTest.java` — new.

`NPointerClickHandler` and `NProspecting` unchanged.
