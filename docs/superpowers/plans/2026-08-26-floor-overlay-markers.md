# Floor Overlay Markers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the other-floor overlay is on, draw that floor's vanilla `PMarker`/`SMarker` and prospecting-macro locations with the same alpha as overlay tiles, aligned by `tileOffset`, with hover tooltip and no click.

**Architecture:** Pure `FloorOverlayMarkerLogic` owns dest→screen mapping, search, prospecting visibility, and hover hit. `MinimapFloorOverlayRenderer` draws overlay vanilla + prospecting after overlay tiles (same `chcolor` alpha) and answers overlay tooltips. `NMiniMap.tooltip` calls the renderer only after current-floor hits miss. Clicks stay on current-floor `markerat` / `mousedown`.

**Tech Stack:** Java 8, JUnit 5 (`ant test`), existing `MinimapFloorOverlayRenderer`, `DisplayGrid.markers`, `ProspectingLocationService`.

**Spec:** `docs/superpowers/specs/2026-08-26-floor-overlay-markers-design.md`

## Global Constraints

- Overlay marks: vanilla (`PMarker`/`SMarker`) and prospecting only. No trees, fish, quarryartz, labeled, animals, forage, timers, waypoints.
- Same alpha as overlay tiles (`MinimapFloorOverlayRenderer.overlayAlpha()`).
- Screen mapping must match overlay tiles: `srcTc = destTc.add(tileOffset)`, then `UI.scale(srcTc).mul(currentScale) - dloc.tc.div(scalef()) + hsz`.
- Do not write `DisplayMarker.sc` for overlay marks.
- Tooltip-only for overlay: do not add dest-segment marks to `markerat`, `mousedown`, or `mousehover`.
- Current-floor tooltip hits always win. Overlay order: prospecting, then vanilla.
- Hide-all, map search, and `showProspectingIcons` apply to overlay the same as current floor.
- No GPU / `GOut` tests.
- No commits unless the user asks.
- `ant test` is the verification command.

## File map

| File | Role |
|---|---|
| `src/nurgling/tools/FloorOverlayMarkerLogic.java` | Pure dest→screen, search, visibility, hover |
| `test/nurgling/tools/FloorOverlayMarkerLogicTest.java` | Unit tests for that helper |
| `src/nurgling/overlays/map/MinimapFloorOverlayRenderer.java` | Draw overlay vanilla + prospecting; overlay tooltip |
| `src/nurgling/widgets/NMiniMap.java` | Shared prospecting-icon draw; tooltip hook; search/hide-all accessors |

---

### Task 1: Pure coord / filter logic (TDD)

**Files:**
- Create: `src/nurgling/tools/FloorOverlayMarkerLogic.java`
- Create: `test/nurgling/tools/FloorOverlayMarkerLogicTest.java`

**Produces:**
- `Coord srcTile(Coord destTc, Coord tileOffset)`
- `Coord destToScreen(Coord destTc, Coord tileOffset, Coord dlocTc, float scalef, float currentScale, Coord hsz, float uiScale)`
- `boolean onScreen(Coord screen, Coord mapSz)`
- `boolean matchesSearch(String name, String pattern)`
- `boolean shouldShowProspecting(boolean showIcons, boolean hideAll)`
- `boolean overlayActive(boolean enabled, long destSegId, long currentSegId)`
- `boolean hoverHit(Coord mouse, Coord markScreen, int thresholdPx)`
- `int overlayTooltipKind(boolean prospectHit, boolean vanillaHit)` — `0` none, `1` prospecting, `2` vanilla; prospecting wins if both

- [ ] **Step 1: Write failing tests**

```java
package nurgling.tools;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FloorOverlayMarkerLogicTest {
    @Test
    void srcTileAddsOffset() {
        assertEquals(new Coord(150, 80),
                FloorOverlayMarkerLogic.srcTile(new Coord(100, 100), new Coord(50, -20)));
    }

    @Test
    void destToScreenMatchesOverlayTileFormula() {
        Coord destTc = new Coord(10, 20);
        Coord offset = new Coord(50, -20);
        Coord dlocTc = new Coord(100, 100);
        float scalef = 2f;
        float currentScale = 0.5f;
        Coord hsz = new Coord(200, 150);
        float uiScale = 1f;

        Coord screen = FloorOverlayMarkerLogic.destToScreen(
                destTc, offset, dlocTc, scalef, currentScale, hsz, uiScale);

        Coord src = destTc.add(offset);
        Coord dlocDiv = dlocTc.div((double) scalef);
        int x = (int) Math.round(src.x * uiScale * currentScale - dlocDiv.x + hsz.x);
        int y = (int) Math.round(src.y * uiScale * currentScale - dlocDiv.y + hsz.y);
        assertEquals(new Coord(x, y), screen);
    }

    @Test
    void onScreenRejectsOutside() {
        Coord sz = new Coord(100, 80);
        assertTrue(FloorOverlayMarkerLogic.onScreen(new Coord(0, 0), sz));
        assertTrue(FloorOverlayMarkerLogic.onScreen(new Coord(100, 80), sz));
        assertFalse(FloorOverlayMarkerLogic.onScreen(new Coord(-1, 10), sz));
        assertFalse(FloorOverlayMarkerLogic.onScreen(new Coord(10, 81), sz));
    }

    @Test
    void searchEmptyShowsAllNullNameHiddenWhenSearching() {
        assertTrue(FloorOverlayMarkerLogic.matchesSearch("Iron", null));
        assertTrue(FloorOverlayMarkerLogic.matchesSearch("Iron", "  "));
        assertTrue(FloorOverlayMarkerLogic.matchesSearch("Cassiterite", "cass"));
        assertFalse(FloorOverlayMarkerLogic.matchesSearch("Iron", "gold"));
        assertFalse(FloorOverlayMarkerLogic.matchesSearch(null, "x"));
    }

    @Test
    void prospectingRespectsToggleAndHideAll() {
        assertTrue(FloorOverlayMarkerLogic.shouldShowProspecting(true, false));
        assertFalse(FloorOverlayMarkerLogic.shouldShowProspecting(false, false));
        assertFalse(FloorOverlayMarkerLogic.shouldShowProspecting(true, true));
    }

    @Test
    void overlayInactiveOnSameSegmentOrDisabled() {
        assertTrue(FloorOverlayMarkerLogic.overlayActive(true, 20L, 10L));
        assertFalse(FloorOverlayMarkerLogic.overlayActive(true, 10L, 10L));
        assertFalse(FloorOverlayMarkerLogic.overlayActive(false, 20L, 10L));
    }

    @Test
    void hoverUsesPixelThreshold() {
        Coord mark = new Coord(50, 50);
        assertTrue(FloorOverlayMarkerLogic.hoverHit(new Coord(55, 50), mark, 10));
        assertFalse(FloorOverlayMarkerLogic.hoverHit(new Coord(70, 50), mark, 10));
    }

    @Test
    void overlayTooltipPrefersProspectingThenVanilla() {
        assertEquals(1, FloorOverlayMarkerLogic.overlayTooltipKind(true, true));
        assertEquals(1, FloorOverlayMarkerLogic.overlayTooltipKind(true, false));
        assertEquals(2, FloorOverlayMarkerLogic.overlayTooltipKind(false, true));
        assertEquals(0, FloorOverlayMarkerLogic.overlayTooltipKind(false, false));
    }
}
```

- [ ] **Step 2: Run tests, expect fail**

Run: `ant test`

Expected: compile or assertion failure on `FloorOverlayMarkerLogic` missing.

- [ ] **Step 3: Minimal implementation**

```java
package nurgling.tools;

import haven.Coord;

public final class FloorOverlayMarkerLogic {
    private FloorOverlayMarkerLogic() {}

    public static Coord srcTile(Coord destTc, Coord tileOffset) {
        return destTc.add(tileOffset);
    }

    public static Coord destToScreen(Coord destTc, Coord tileOffset, Coord dlocTc, float scalef,
                                    float currentScale, Coord hsz, float uiScale) {
        Coord src = srcTile(destTc, tileOffset);
        Coord dlocDiv = dlocTc.div((double) scalef);
        double x = src.x * uiScale * currentScale - dlocDiv.x + hsz.x;
        double y = src.y * uiScale * currentScale - dlocDiv.y + hsz.y;
        return new Coord((int) Math.round(x), (int) Math.round(y));
    }

    public static boolean onScreen(Coord screen, Coord mapSz) {
        return screen.x >= 0 && screen.y >= 0 && screen.x <= mapSz.x && screen.y <= mapSz.y;
    }

    public static boolean matchesSearch(String name, String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return true;
        }
        if (name == null) {
            return false;
        }
        return name.toLowerCase().contains(pattern.trim().toLowerCase());
    }

    public static boolean shouldShowProspecting(boolean showIcons, boolean hideAll) {
        return showIcons && !hideAll;
    }

    public static boolean overlayActive(boolean enabled, long destSegId, long currentSegId) {
        return enabled && destSegId != currentSegId;
    }

    public static boolean hoverHit(Coord mouse, Coord markScreen, int thresholdPx) {
        return mouse.dist(markScreen) < thresholdPx;
    }

    public static int overlayTooltipKind(boolean prospectHit, boolean vanillaHit) {
        if (prospectHit) {
            return 1;
        }
        if (vanillaHit) {
            return 2;
        }
        return 0;
    }
}
```

- [ ] **Step 4: Run tests, expect pass**

Run: `ant test`

Expected: `FloorOverlayMarkerLogicTest` PASS.

- [ ] **Step 5: Commit**

Skip unless the user asked.

---

### Task 2: Draw overlay vanilla + prospecting

**Files:**
- Modify: `src/nurgling/overlays/map/MinimapFloorOverlayRenderer.java`
- Modify: `src/nurgling/widgets/NMiniMap.java` (`drawProspectingLocations`, new public helpers)

**Consumes:** `FloorOverlayMarkerLogic` methods from Task 1.

**Produces:**
- `NMiniMap.markerSearchPattern()` → `String` (nullable)
- `NMiniMap.markersHidden()` → `boolean`
- `NMiniMap.drawProspectingIconAt(GOut g, Coord screenPos, String resourceType)`
- Overlay renderer draws dest vanilla + dest prospecting after tiles, same alpha; does not assign `mark.sc`

- [ ] **Step 1: Add NMiniMap accessors and extract prospecting icon draw**

Add these methods on `NMiniMap` (near `drawProspectingLocations`). Then make `drawProspectingLocations` call `drawProspectingIconAt` instead of inlining icon draw.

```java
public String markerSearchPattern() {
    Widget parentWidget = this.parent;
    while (parentWidget != null) {
        if (parentWidget instanceof NMapWnd) {
            return ((NMapWnd) parentWidget).markerSearchPattern;
        }
        parentWidget = parentWidget.parent;
    }
    return null;
}

public boolean markersHidden() {
    NGameUI gui = NUtils.getGameUI();
    MapWnd mapwnd = (gui != null) ? gui.mapfile : null;
    return mapwnd != null && Utils.eq(mapwnd.markcfg, MapWnd.MarkerConfig.hideall);
}

public void drawProspectingIconAt(GOut g, Coord screenPos, String resourceType) {
    try {
        String iconResourcePath = getProspectingIconPath(resourceType);
        TexI tex = null;
        if (iconResourcePath != null) {
            tex = prospectingIconCache.get(iconResourcePath);
            if (tex == null) {
                try {
                    Resource iconRes = Resource.remote().loadwait(iconResourcePath);
                    BufferedImage icon = iconRes.layer(Resource.imgc).img;
                    tex = new TexI(icon);
                    prospectingIconCache.put(iconResourcePath, tex);
                } catch (Exception e) {
                    tex = tryLoadProspectingIcon(resourceType);
                }
            }
        } else {
            tex = tryLoadProspectingIcon(resourceType);
        }
        if (tex != null) {
            Object scaleObj = NConfig.get(NConfig.Key.prospectIconScale);
            int scalePercent = 100;
            if (scaleObj instanceof Number) {
                scalePercent = ((Number) scaleObj).intValue();
            }
            float scaleMultiplier = scalePercent / 100.0f;
            int dsz = Math.max(tex.sz().y, tex.sz().x);
            int targetSize = (int) (UI.scale(18) * scaleMultiplier);
            g.aimage(tex, screenPos, 0.5, 0.5,
                    UI.scale(targetSize * tex.sz().x / dsz, targetSize * tex.sz().y / dsz));
        } else {
            drawFallbackProspectingIcon(g, screenPos, resourceType);
        }
    } catch (Exception e) {
        g.chcolor(128, 128, 128, 255);
        g.fellipse(screenPos, new Coord(UI.scale(6), UI.scale(6)));
        g.chcolor();
    }
}
```

Replace the inner try-body of `drawProspectingLocations` (from `String resourceType = prospectingLoc.getResourceType();` through the catch fallback) with:

```java
drawProspectingIconAt(g, screenPos, prospectingLoc.getResourceType());
```

Leave segment fetch as `sessloc.seg.id` for current-floor draw. Also switch the existing search-pattern walk in `drawProspectingLocations` to `markerSearchPattern()` if that is a one-line change without touching other tooltip duplication.

- [ ] **Step 2: Draw overlay marks in the renderer**

In `MinimapFloorOverlayRenderer.render`, keep the existing tile blit inside `g.chcolor(255, 255, 255, alpha)`. After the tile loop, still inside that try, call `drawOverlayMarks(mm, g, link, items, alpha)`.

Add field:

```java
private List<DrawItem> lastItems = Collections.emptyList();
private FloorOverlayAligner.FloorLink lastLink = null;
```

At the start of `render`, if early-return, set `lastItems = Collections.emptyList(); lastLink = null;`. After `collectVisible`, store `lastItems = items; lastLink = link;`.

Add import: `nurgling.tools.FloorOverlayMarkerLogic`, `haven.Loading`, `haven.Utils` not needed, `nurgling.NGameUI` already there.

```java
private void drawOverlayMarks(NMiniMap mm, GOut g, FloorOverlayAligner.FloorLink link,
                             List<DrawItem> items, int alpha) {
    if (mm.dloc == null || !FloorOverlayMarkerLogic.overlayActive(true, link.toSegId, mm.dloc.seg.id)) {
        return;
    }
    Coord hsz = mm.sz.div(2);
    float uiScale = haven.UI.scale(1f);
    String pattern = mm.markerSearchPattern();
    boolean hideAll = mm.markersHidden();

    for (DrawItem item : items) {
        for (MiniMap.DisplayMarker mark : item.disp.markers(true)) {
            if (mm.filter(mark)) {
                continue;
            }
            if (!FloorOverlayMarkerLogic.matchesSearch(mark.m.nm, pattern)) {
                continue;
            }
            Coord screen = FloorOverlayMarkerLogic.destToScreen(
                    mark.m.tc, link.tileOffset, mm.dloc.tc, mm.scalef(),
                    mm.getCurrentScale(), hsz, uiScale);
            if (!FloorOverlayMarkerLogic.onScreen(screen, mm.sz)) {
                continue;
            }
            try {
                mark.draw(g, screen);
            } catch (Loading ignored) {
            }
            // do not assign mark.sc
        }
    }

    if (!FloorOverlayMarkerLogic.shouldShowProspecting(mm.showProspectingIcons, hideAll)) {
        return;
    }
    NGameUI gui = NUtils.getGameUI();
    if (gui == null || gui.prospectingLocationService == null) {
        return;
    }
    for (nurgling.ProspectingLocation loc : gui.prospectingLocationService.getProspectingLocationsForSegment(link.toSegId)) {
        if (!FloorOverlayMarkerLogic.matchesSearch(loc.getResourceType(), pattern)) {
            continue;
        }
        Coord screen = FloorOverlayMarkerLogic.destToScreen(
                loc.getTileCoords(), link.tileOffset, mm.dloc.tc, mm.scalef(),
                mm.getCurrentScale(), hsz, uiScale);
        if (!FloorOverlayMarkerLogic.onScreen(screen, mm.sz)) {
            continue;
        }
        g.chcolor(255, 255, 255, alpha);
        mm.drawProspectingIconAt(g, screen, loc.getResourceType());
        g.chcolor(255, 255, 255, alpha);
    }
}
```

Re-apply overlay alpha after each prospecting icon because `drawFallbackProspectingIcon` calls `g.chcolor()`.

Do not use `permIconScale` / reflection for overlay vanilla.

- [ ] **Step 3: Run tests**

Run: `ant test`

Expected: PASS (logic tests still pass; no new GPU tests).

- [ ] **Step 4: Commit**

Skip unless the user asked.

---

### Task 3: Overlay hover tooltip (no click)

**Files:**
- Modify: `src/nurgling/overlays/map/MinimapFloorOverlayRenderer.java`
- Modify: `src/nurgling/widgets/NMiniMap.java` (`tooltip` only)

**Consumes:** `lastItems` / `lastLink` from Task 2; `FloorOverlayMarkerLogic.hoverHit` / `destToScreen` / `matchesSearch` / `shouldShowProspecting`.

**Produces:**
- `MinimapFloorOverlayRenderer.tooltip(NMiniMap mm, Coord c)` → `Object` (`Text` / `TexI`) or `null`
- `NMiniMap.tooltip` asks overlay only after current-floor trees/fish/prospecting/labeled/vanilla miss, before terrain
- Overlay marks remain absent from `markerat` / `mousedown` / `mousehover`

- [ ] **Step 1: Add renderer tooltip**

```java
public Object tooltip(NMiniMap mm, Coord c) {
    if (mm.dloc == null || lastLink == null || lastItems.isEmpty()) {
        return null;
    }
    if (!enabled() || !FloorOverlayMarkerLogic.overlayActive(true, lastLink.toSegId, mm.dloc.seg.id)) {
        return null;
    }
    Coord hsz = mm.sz.div(2);
    float uiScale = haven.UI.scale(1f);
    String pattern = mm.markerSearchPattern();
    boolean hideAll = mm.markersHidden();
    int prospectThreshold = haven.UI.scale(10);

    if (FloorOverlayMarkerLogic.shouldShowProspecting(mm.showProspectingIcons, hideAll)) {
        NGameUI gui = NUtils.getGameUI();
        if (gui != null && gui.prospectingLocationService != null) {
            for (nurgling.ProspectingLocation loc : gui.prospectingLocationService.getProspectingLocationsForSegment(lastLink.toSegId)) {
                if (!FloorOverlayMarkerLogic.matchesSearch(loc.getResourceType(), pattern)) {
                    continue;
                }
                Coord screen = FloorOverlayMarkerLogic.destToScreen(
                        loc.getTileCoords(), lastLink.tileOffset, mm.dloc.tc, mm.scalef(),
                        mm.getCurrentScale(), hsz, uiScale);
                if (FloorOverlayMarkerLogic.hoverHit(c, screen, prospectThreshold)) {
                    String resourceType = loc.getResourceType();
                    return haven.Text.render(resourceType != null ? resourceType : "Unknown");
                }
            }
        }
    }

    for (DrawItem item : lastItems) {
        for (MiniMap.DisplayMarker mark : item.disp.markers(false)) {
            if (mm.filter(mark) || !FloorOverlayMarkerLogic.matchesSearch(mark.m.nm, pattern)) {
                continue;
            }
            Coord screen = FloorOverlayMarkerLogic.destToScreen(
                    mark.m.tc, lastLink.tileOffset, mm.dloc.tc, mm.scalef(),
                    mm.getCurrentScale(), hsz, uiScale);
            try {
                haven.GobIcon.Icon icon = mark.icon();
                if (icon != null && icon.checkhit(c.sub(screen))) {
                    return new haven.TexI(mark.tooltip());
                }
            } catch (Loading ignored) {
            }
        }
    }
    return null;
}
```

Do not assign `mark.sc`. If `hideAll` is true, vanilla overlay on the big map is already hidden by `mm.filter` (`MapWnd.View` uses `markcfg`). Still skip overlay prospecting via `shouldShowProspecting`.

- [ ] **Step 2: Hook NMiniMap.tooltip after current-floor vanilla, before terrain**

In `NMiniMap.tooltip`, after the `DisplayMarker mark = markerat(tc);` block (the `if(mark != null) return new TexI(mark.tooltip())`), before `getTerrainTooltip`:

```java
Object overlayTip = floorOverlayRenderer.tooltip(this, c);
if (overlayTip != null) {
    return overlayTip;
}
```

Do not call overlay tooltip earlier (current-floor must win). Do not change `mousedown`, `markerat`, or `mousehover`.

- [ ] **Step 3: Run tests**

Run: `ant test`

Expected: PASS.

- [ ] **Step 4: Manual check**

With floor overlay on: dest-floor tiles ghosted; dest vanilla + prospecting ghosted at the same offset; hover shows dest names; click still focuses/moves as current-floor only; hide-all and search hide overlay marks too.

- [ ] **Step 5: Commit**

Skip unless the user asked.

---

## Spec coverage

| Spec | Task |
|---|---|
| Overlay vanilla + prospecting, same alpha | 2 |
| `srcTc = destTc + tileOffset`, tile-matching screen formula | 1, 2 |
| Skip off-screen | 1, 2 |
| `DisplayGrid.markers`, no permIconScale | 2 |
| Prospecting `link.toSegId`, toggle, hide-all, search | 1, 2 |
| Draw order: current tiles → overlay tiles+marks → current marks | 2 (renderer still called from `drawmap`) |
| Do not write overlay `mark.sc` | 2, 3 |
| Tooltip after current-floor, prospecting then vanilla | 1 (`overlayTooltipKind`), 3 |
| No click / `markerat` / `mousedown` / `mousehover` | 3 |
| Loading skip, missing GUI skip | 2, 3 |
| Helper tests, no GOut tests | 1 |
