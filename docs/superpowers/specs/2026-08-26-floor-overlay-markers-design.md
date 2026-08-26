# Floor overlay markers (vanilla + prospecting)

**Date:** 2026-08-26  
**Status:** approved design

## Goal

When the other-floor map overlay is on, the underlay is already a semi-transparent copy of that floor's tiles. Also draw that floor's **vanilla map markers** (`PMarker` / `SMarker`) and **prospecting-macro locations**, with the same alpha, aligned by the existing floor `tileOffset`.

## Non-goals

- Trees, fish, quarryartz, labeled marks, animals, forage, timers, waypoints.
- Click, focus, or context menu on overlay markers. `mousedown` / `markerat` stay current-floor only.
- Changing overlay alpha, floor picker, or tile alignment.
- Drawing overlay markers into an offscreen framebuffer.

## Current gap

`MinimapFloorOverlayRenderer.render` blits dest-segment tiles with `floorOverlayAlpha`, then returns.

- `NMiniMap.drawmarkers` only walks current `display` grids (current segment).
- `drawProspectingLocations` only loads `getProspectingLocationsForSegment(sessloc.seg.id)`.

Dest-segment vanilla and prospecting marks never appear.

## Draw path

Keep drawing inside `MinimapFloorOverlayRenderer` after dest tiles, still under `g.chcolor(255, 255, 255, overlayAlpha())`.

1. Current-floor tiles (`drawmap`).
2. Overlay tiles + overlay vanilla + overlay prospecting (same alpha).
3. Current-floor markers and the rest of `drawparts` (opaque, on top).

Overlay drawing must not change `DisplayMarker.sc` on current-floor marks (current-floor hover uses `sc`).

## Coordinates

Same mapping as overlay tiles:

`srcTc = destTc.add(link.tileOffset)`  
screen = `srcTc` scaled like current-floor tiles (`currentScale`, `dloc.tc`, `scalef()`, map center).

Extract a small pure helper (dest tile + offset + view params → screen `Coord`) and unit-test it. Skip off-screen marks.

## Vanilla markers

Reuse overlay `DisplayGrid` cache already built for tiles. For each visible dest grid, `disp.markers(true)` and draw `mark.draw(g, screenPos)` when:

- `!mm.filter(mark)`
- current map search pattern is empty, or marker name contains it (case-insensitive), same as `NMiniMap.drawmarkers`

Do not apply `permIconScale` / reflection scaling. Ghost icons at native size are enough.

## Prospecting markers

`gui.prospectingLocationService.getProspectingLocationsForSegment(link.toSegId)`.

Skip when:

- `!mm.showProspectingIcons`
- map `markcfg` is hide-all
- search pattern is set and resource type does not contain it

Reuse existing overlay icon path / cache / fallback from `drawProspectingLocations` (extract shared draw-one-icon if that avoids a real copy; do not fork icon loading). Apply `prospectIconScale`. Screen position uses the dest→src helper above.

## Tooltip (hover only)

Overlay marks show a tooltip on hover. They must not become clickable.

In `NMiniMap.tooltip`, after all current-floor checks (trees, fish, prospecting, labeled, vanilla `markerat`), if nothing hit:

1. Overlay prospecting on `link.toSegId`, same 10px screen threshold as current prospecting, dest→src helper. Text = resource type (same as current floor).
2. Overlay vanilla: dest-grid `markers(false)`, hit-test with dest→src screen position (do not write `mark.sc`). Tip = existing `DisplayMarker.tooltip()`.

Current-floor hits always win when overlapping. Overlay hit-test is tooltip-only: do not add dest-segment marks to `markerat`, `mousedown`, or `mousehover`.

Skip overlay tooltip when overlay is off, no `FloorLink`, hide-all, or the mark would not be drawn (filter / search / prospecting toggle).

## Errors

- No selected `FloorLink`, overlay disabled, or dest segment missing → no overlay marks (same as tiles).
- `Loading` on a dest marker icon → skip that mark, keep the rest.
- Missing prospecting service / GUI → skip prospecting overlay only.

## Tests

- Helper: dest tile + offset maps to expected screen coord; off-screen rejected.
- Optional: given dest segment id, overlay collect uses that id, not `sessloc.seg.id`.
- No GPU / `GOut` tests.
