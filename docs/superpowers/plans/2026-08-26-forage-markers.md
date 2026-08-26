# Foraging Map Markers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** World `Pick` of a gob that yields Q ≥ 40 places a persistent labeled map mark in a dedicated Foraging layer, with toggle, search by name/quality, and the same delete path as other labeled marks.

**Architecture:** Pure `ForageMarkerLogic` owns threshold, Pick filter, forage id, search, and 11-tile keep-best-Q. `ForagePickupMarker` records the gob at flower-menu Pick, waits up to 2s for item quality+sprite, then `LabeledMarkService.addForageMark`. Map UI reuses `LabeledMinimapMark` with `locationId` prefix `forage_`.

**Tech Stack:** Java 8, JUnit 5 (`ant test`), `LabeledMarkService`, `NMiniMap` / `NMapWnd`, Haven flower menu + inventory.

**Spec:** `docs/superpowers/specs/2026-08-26-forage-markers-design.md`

## Global Constraints

- Reuse `LabeledMarkService`; do not add `ForageLocationService` or vanilla `MapFile` markers.
- Place only when action name is exactly `Pick` (not `Pick up`), target is a world gob, gob name does not contain `gardenpot`, and `quality >= 40`.
- `locationId` starts with `forage_`. Label format `q` + rounded quality (`String.format("q%.0f", quality)`).
- Dedup radius 11 tiles (Chebyshev, same as `LabeledMinimapMark.isNear`), same segment, same `resourceType`, forage marks only; keep higher Q.
- Default `NConfig.Key.showForagingIcons` is `true`.
- Do not change clay / water / ore / gem / animal mark behavior except: `isOreSpotMark` must return false for forage ids (otherwise forage would follow the ore-spot toggle).
- Failures in placement are swallowed.
- No commits unless the user asks.
- `ant test` is the verification command.

## File map

| File | Role |
|---|---|
| `src/nurgling/tools/ForageMarkerLogic.java` | Pure Pick/Q/search/dedup/id helpers |
| `test/nurgling/tools/ForageMarkerLogicTest.java` | Unit tests for those helpers |
| `src/nurgling/ForagePickupMarker.java` | Pending Pick + quality wait + place call |
| `src/nurgling/LabeledMarkService.java` | `addForageMark` (persist, 11-tile keep-best) |
| `src/nurgling/NFlowerMenu.java` | `noteWorldPick` after gob `Pick` |
| `src/nurgling/NInventory.java` | Watch new main-inv items |
| `src/nurgling/NGItem.java` | Tick until Q/sprite or 2s timeout |
| `src/nurgling/NConfig.java` | `showForagingIcons` key + default true |
| `src/nurgling/widgets/NMiniMap.java` | Layer flag, draw/tooltip/click/search/hideall |
| `src/nurgling/widgets/NMapWnd.java` | Toggle + RMB search |
| `src/nurgling/widgets/ForagingSearchWindow.java` | Type + min Q search |
| `src/nurgling/NGameUI.java` | `foragingSearchWindow` field |

---

### Task 1: Pure logic (TDD)

**Files:**
- Create: `src/nurgling/tools/ForageMarkerLogic.java`
- Create: `test/nurgling/tools/ForageMarkerLogicTest.java`

**Produces:**
- `ForageMarkerLogic.MIN_QUALITY = 40d`
- `ForageMarkerLogic.DEDUP_RADIUS = 11`
- `ForageMarkerLogic.QUALITY_WAIT_MS = 2000L`
- `ForageMarkerLogic.ID_PREFIX = "forage_"`
- `boolean isPickAction(String name)`
- `boolean isGardenPot(String gobName)`
- `boolean shouldPlace(Float quality)`
- `boolean isForageId(String locationId)`
- `String formatLabel(double quality)`
- `double parseQuality(String label)`
- `String forageLocationId(long segmentId, int tileX, int tileY, String resourceType)`
- `boolean matchesMapSearch(String resourceType, String label, String pattern)`
- `boolean matchesWindowSearch(String resourceType, String label, String selectedType, Double minQuality)`
- `Neighbor` / `Dedup` / `Dedup decideDedup(double newQuality, List<Neighbor> nearbySameType)`

- [ ] **Step 1: Write failing tests**

```java
package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ForageMarkerLogicTest {
    @Test
    void pickAcceptedPickUpRejected() {
        assertTrue(ForageMarkerLogic.isPickAction("Pick"));
        assertFalse(ForageMarkerLogic.isPickAction("Pick up"));
        assertFalse(ForageMarkerLogic.isPickAction("Harvest"));
        assertFalse(ForageMarkerLogic.isPickAction(null));
    }

    @Test
    void gardenPotByName() {
        assertTrue(ForageMarkerLogic.isGardenPot("gfx/terobjs/gardenpot"));
        assertTrue(ForageMarkerLogic.isGardenPot("GardenPot"));
        assertFalse(ForageMarkerLogic.isGardenPot("gfx/terobjs/herbs/blueberry"));
        assertFalse(ForageMarkerLogic.isGardenPot(null));
    }

    @Test
    void qualityThreshold() {
        assertTrue(ForageMarkerLogic.shouldPlace(40f));
        assertTrue(ForageMarkerLogic.shouldPlace(40.1f));
        assertFalse(ForageMarkerLogic.shouldPlace(39.9f));
        assertFalse(ForageMarkerLogic.shouldPlace(null));
    }

    @Test
    void forageIdPrefix() {
        assertTrue(ForageMarkerLogic.isForageId("forage_1_2_3_Blueberries"));
        assertFalse(ForageMarkerLogic.isForageId("animal_1"));
        assertFalse(ForageMarkerLogic.isForageId("labeled_1_2_3_q20"));
        assertFalse(ForageMarkerLogic.isForageId(null));
    }

    @Test
    void labelFormatAndParse() {
        assertEquals("q45", ForageMarkerLogic.formatLabel(45.4));
        assertEquals(45.0, ForageMarkerLogic.parseQuality("q45"), 0.01);
        assertEquals(0.0, ForageMarkerLogic.parseQuality("nope"), 0.01);
        assertEquals(0.0, ForageMarkerLogic.parseQuality(null), 0.01);
    }

    @Test
    void forageLocationIdStartsWithPrefix() {
        String id = ForageMarkerLogic.forageLocationId(9, 1, 2, "Blueberries");
        assertTrue(id.startsWith("forage_"));
        assertTrue(id.contains("Blueberries") || id.contains("Blueberr"));
    }

    @Test
    void mapSearchNameOrQuality() {
        assertTrue(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", ""));
        assertTrue(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", "blue"));
        assertTrue(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", "q45"));
        assertTrue(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", "45"));
        assertFalse(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", "chantrelle"));
    }

    @Test
    void windowSearchTypeAndMinQuality() {
        assertTrue(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Any", null));
        assertTrue(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Blueberries", 40.0));
        assertTrue(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Any", 45.0));
        assertFalse(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Chantrelles", null));
        assertFalse(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Any", 46.0));
    }

    @Test
    void dedupKeepsHigherQualityInsideRadiusList() {
        ForageMarkerLogic.Neighbor weak = new ForageMarkerLogic.Neighbor("forage_old", 30);
        ForageMarkerLogic.Dedup better = ForageMarkerLogic.decideDedup(50, Collections.singletonList(weak));
        assertFalse(better.skip);
        assertEquals(Collections.singletonList("forage_old"), better.removeIds);

        ForageMarkerLogic.Neighbor strong = new ForageMarkerLogic.Neighbor("forage_hi", 60);
        ForageMarkerLogic.Dedup worse = ForageMarkerLogic.decideDedup(50, Collections.singletonList(strong));
        assertTrue(worse.skip);
        assertTrue(worse.removeIds.isEmpty());

        ForageMarkerLogic.Dedup many = ForageMarkerLogic.decideDedup(50, Arrays.asList(
                new ForageMarkerLogic.Neighbor("a", 20),
                new ForageMarkerLogic.Neighbor("b", 35)));
        assertFalse(many.skip);
        assertEquals(Arrays.asList("a", "b"), many.removeIds);

        ForageMarkerLogic.Dedup none = ForageMarkerLogic.decideDedup(50, Collections.emptyList());
        assertFalse(none.skip);
        assertTrue(none.removeIds.isEmpty());
    }
}
```

- [ ] **Step 2: Run tests — expect compile/fail on missing `ForageMarkerLogic`**

```
ant test -Dtest.include=**/ForageMarkerLogicTest.class
```

If the ant target ignores that property, run `ant test` and expect failure to compile `ForageMarkerLogicTest`.

- [ ] **Step 3: Implement `ForageMarkerLogic`**

```java
package nurgling.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ForageMarkerLogic {
    public static final double MIN_QUALITY = 40;
    public static final int DEDUP_RADIUS = 11;
    public static final long QUALITY_WAIT_MS = 2000L;
    public static final String ID_PREFIX = "forage_";

    private ForageMarkerLogic() {}

    public static boolean isPickAction(String name) {
        return "Pick".equals(name);
    }

    public static boolean isGardenPot(String gobName) {
        return gobName != null && gobName.toLowerCase(Locale.ROOT).contains("gardenpot");
    }

    public static boolean shouldPlace(Float quality) {
        return quality != null && quality >= MIN_QUALITY;
    }

    public static boolean isForageId(String locationId) {
        return locationId != null && locationId.startsWith(ID_PREFIX);
    }

    public static String formatLabel(double quality) {
        return String.format(Locale.US, "q%.0f", quality);
    }

    public static double parseQuality(String label) {
        if (label == null || !label.startsWith("q")) return 0;
        try {
            return Double.parseDouble(label.substring(1).trim().replace(',', '.'));
        } catch (Exception e) {
            return 0;
        }
    }

    public static String forageLocationId(long segmentId, int tileX, int tileY, String resourceType) {
        String type = resourceType != null ? resourceType.replaceAll("[^a-zA-Z0-9]", "_") : "item";
        return ID_PREFIX + segmentId + "_" + tileX + "_" + tileY + "_" + type + "_" + System.currentTimeMillis();
    }

    public static boolean matchesMapSearch(String resourceType, String label, String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) return true;
        String p = pattern.toLowerCase(Locale.ROOT);
        String type = resourceType != null ? resourceType.toLowerCase(Locale.ROOT) : "";
        String lab = label != null ? label.toLowerCase(Locale.ROOT) : "";
        return type.contains(p) || lab.contains(p);
    }

    public static boolean matchesWindowSearch(String resourceType, String label, String selectedType, Double minQuality) {
        if (resourceType == null) return false;
        if (selectedType != null && !"Any".equals(selectedType) && !selectedType.equals(resourceType)) {
            return false;
        }
        if (minQuality != null) {
            return parseQuality(label) >= minQuality;
        }
        return true;
    }

    public static final class Neighbor {
        public final String locationId;
        public final double quality;
        public Neighbor(String locationId, double quality) {
            this.locationId = locationId;
            this.quality = quality;
        }
    }

    public static final class Dedup {
        public final boolean skip;
        public final List<String> removeIds;
        public Dedup(boolean skip, List<String> removeIds) {
            this.skip = skip;
            this.removeIds = removeIds;
        }
    }

    public static Dedup decideDedup(double newQuality, List<Neighbor> nearbySameType) {
        if (nearbySameType == null || nearbySameType.isEmpty()) {
            return new Dedup(false, Collections.emptyList());
        }
        List<String> remove = new ArrayList<String>();
        for (Neighbor n : nearbySameType) {
            if (n.quality >= newQuality) {
                return new Dedup(true, Collections.emptyList());
            }
            remove.add(n.locationId);
        }
        return new Dedup(false, remove);
    }
}
```

**Do not** put `System.currentTimeMillis()` into the tested uniqueness of `forageLocationId` beyond prefix/type. The test only checks prefix and type fragment.

- [ ] **Step 4: `ant test` — `ForageMarkerLogicTest` passes**

---

### Task 2: Persist forage marks

**Files:**
- Modify: `src/nurgling/LabeledMarkService.java` (add method after `addLabeledMark`)

**Consumes:** `ForageMarkerLogic` from Task 1  
**Produces:** `void addForageMark(String label, String resourceType, long segmentId, Coord tileCoords, java.awt.image.BufferedImage iconImage)`

- [ ] **Step 1: Add `addForageMark`**

Insert after `addLabeledMark(...)` (around line 296):

```java
    public void addForageMark(String label, String resourceType, long segmentId,
                             haven.Coord tileCoords, java.awt.image.BufferedImage iconImage) {
        if (resourceType == null || tileCoords == null) return;
        lock.writeLock().lock();
        try {
            java.util.List<nurgling.widgets.LabeledMinimapMark> sameType =
                resourceTypeIndex.getOrDefault(resourceType, new java.util.ArrayList<nurgling.widgets.LabeledMinimapMark>());
            java.util.List<nurgling.tools.ForageMarkerLogic.Neighbor> nearby =
                new java.util.ArrayList<nurgling.tools.ForageMarkerLogic.Neighbor>();
            for (nurgling.widgets.LabeledMinimapMark mark : sameType) {
                if (!nurgling.tools.ForageMarkerLogic.isForageId(mark.getLocationId())) continue;
                if (mark.segmentId != segmentId) continue;
                if (!mark.isNear(segmentId, tileCoords, nurgling.tools.ForageMarkerLogic.DEDUP_RADIUS)) continue;
                nearby.add(new nurgling.tools.ForageMarkerLogic.Neighbor(
                    mark.getLocationId(), nurgling.tools.ForageMarkerLogic.parseQuality(mark.label)));
            }
            nurgling.tools.ForageMarkerLogic.Dedup dedup =
                nurgling.tools.ForageMarkerLogic.decideDedup(nurgling.tools.ForageMarkerLogic.parseQuality(label), nearby);
            if (dedup.skip) return;
            for (String id : dedup.removeIds) {
                removeMarkFromIndexes(id);
            }
            String locationId = nurgling.tools.ForageMarkerLogic.forageLocationId(
                segmentId, tileCoords.x, tileCoords.y, resourceType);
            nurgling.widgets.LabeledMinimapMark mark = new nurgling.widgets.LabeledMinimapMark(
                locationId, label, resourceType, segmentId, tileCoords, iconImage, null);
            labeledMarks.put(locationId, mark);
            addMarkToIndexes(mark);
            scheduleSave();
        } finally {
            lock.writeLock().unlock();
        }
    }
```

Use existing imports if `Coord` / `LabeledMinimapMark` / `BufferedImage` are already imported; do not duplicate FQNs if the file already imports them. Prefer the file's existing import style.

`LabeledMinimapMark` already has constructor `(locationId, label, resourceType, segmentId, tileCoords, iconImage, labelColor)`. Forage marks **must** persist (do not skip `forage_` in `saveLabeledMarksOptimized`; only `animal_` stays skipped).

- [ ] **Step 2: `ant test` — still green**

---

### Task 3: Pick hook

**Files:**
- Create: `src/nurgling/ForagePickupMarker.java`
- Modify: `src/nurgling/NFlowerMenu.java` (`nchoose`, after `setLastAction(option.name, actions.gob)` ~line 254)
- Modify: `src/nurgling/NInventory.java` (`addchild` ~397)
- Modify: `src/nurgling/NGItem.java` (`tick` after `super.tick(dt)`)

**Consumes:** `ForageMarkerLogic`, `LabeledMarkService.addForageMark`  
**Produces:** `ForagePickupMarker.noteWorldPick(Gob gob)`, `onInventoryItem(NInventory inv, GItem item)`, `onItemTick(NGItem item)`

- [ ] **Step 1: Implement `ForagePickupMarker`**

```java
package nurgling;

import haven.*;
import nurgling.tools.ForageMarkerLogic;

import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;

public final class ForagePickupMarker {
    private static final Object LOCK = new Object();
    private static Pending pending;
    private static WeakReference<NGItem> watching;
    private static long watchStartMs;

    private static final class Pending {
        final long segmentId;
        final Coord tileCoords;
        final long createdMs;
        Pending(long segmentId, Coord tileCoords) {
            this.segmentId = segmentId;
            this.tileCoords = tileCoords;
            this.createdMs = System.currentTimeMillis();
        }
    }

    private ForagePickupMarker() {}

    public static void noteWorldPick(Gob gob) {
        try {
            if (gob == null) return;
            String gobName = (gob.ngob != null) ? gob.ngob.name : null;
            if (ForageMarkerLogic.isGardenPot(gobName)) return;
            NGameUI gui = NUtils.getGameUI();
            if (gui == null || gui.mmap == null || gui.mmap.sessloc == null) return;
            long segmentId = gui.mmap.sessloc.seg.id;
            Coord tileCoords = gob.rc.floor(MCache.tilesz).add(gui.mmap.sessloc.tc);
            synchronized (LOCK) {
                pending = new Pending(segmentId, tileCoords);
                watching = null;
            }
        } catch (Exception ignored) {
        }
    }

    public static void onInventoryItem(NInventory inv, GItem item) {
        try {
            NGameUI gui = NUtils.getGameUI();
            if (gui == null || gui.maininv != inv) return;
            if (!(item instanceof NGItem)) return;
            synchronized (LOCK) {
                if (pending == null) return;
                if (System.currentTimeMillis() - pending.createdMs > ForageMarkerLogic.QUALITY_WAIT_MS) {
                    pending = null;
                    return;
                }
                watching = new WeakReference<NGItem>((NGItem) item);
                watchStartMs = System.currentTimeMillis();
            }
        } catch (Exception ignored) {
        }
    }

    public static void onItemTick(NGItem item) {
        try {
            Pending p;
            synchronized (LOCK) {
                if (watching == null || watching.get() != item) return;
                if (System.currentTimeMillis() - watchStartMs > ForageMarkerLogic.QUALITY_WAIT_MS) {
                    watching = null;
                    pending = null;
                    return;
                }
                p = pending;
            }
            if (p == null) return;
            if (!ForageMarkerLogic.shouldPlace(item.quality)) {
                if (item.quality != null) {
                    synchronized (LOCK) {
                        watching = null;
                        pending = null;
                    }
                }
                return;
            }
            String name = item.name();
            BufferedImage icon = iconOf(item);
            if (name == null || icon == null) return;
            NGameUI gui = NUtils.getGameUI();
            if (gui == null || gui.labeledMarkService == null) return;
            String label = ForageMarkerLogic.formatLabel(item.quality);
            gui.labeledMarkService.addForageMark(label, name, p.segmentId, p.tileCoords, icon);
            synchronized (LOCK) {
                watching = null;
                pending = null;
            }
        } catch (Exception ignored) {
        }
    }

    private static BufferedImage iconOf(NGItem item) {
        try {
            GSprite spr = item.spr;
            if (spr instanceof StaticGSprite) {
                return ((StaticGSprite) spr).img.img;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
```

- [ ] **Step 2: Hook `NFlowerMenu.nchoose`**

Immediately after:

```java
                    NUtils.getUI().core.setLastAction(option.name, actions.gob);
```

add:

```java
                    if (ForageMarkerLogic.isPickAction(option.name)) {
                        ForagePickupMarker.noteWorldPick(actions.gob);
                    }
```

Imports: `nurgling.tools.ForageMarkerLogic` (or FQCN). `ForagePickupMarker` is same package `nurgling`.

Do **not** call `noteWorldPick` when `actions.item != null`.

- [ ] **Step 3: Hook `NInventory.addchild`**

```java
    @Override
    public void addchild(Widget child, Object... args) {
        super.addchild(child, args);
        if (child instanceof GItem) {
            ForagePickupMarker.onInventoryItem(this, (GItem) child);
        }
    }
```

- [ ] **Step 4: Hook `NGItem.tick`**

Right after `super.tick(dt);`:

```java
        ForagePickupMarker.onItemTick(this);
```

- [ ] **Step 5: `ant test` — still green**

---

### Task 4: Map layer

**Files:**
- Modify: `src/nurgling/NConfig.java` — add `showForagingIcons` next to `showAnimalIcons`; `conf.put(Key.showForagingIcons, true);` next to `showAnimalIcons` default
- Modify: `src/nurgling/widgets/NMiniMap.java`

**Consumes:** `ForageMarkerLogic.isForageId`, `matchesMapSearch`  
**Produces:** `NMiniMap.showForagingIcons`, `isForageMark(mark)`

- [ ] **Step 1: Config key**

In `NConfig.Key` enum, after `showAnimalIcons,`:

```java
        showForagingIcons,
```

In defaults, after `conf.put(Key.showAnimalIcons, true);`:

```java
        conf.put(Key.showForagingIcons, true);
```

- [ ] **Step 2: `NMiniMap` flag + load**

Field next to `showAnimalIcons`:

```java
    public boolean showForagingIcons = true;
```

In `loadToggleStates()`:

```java
        Boolean foraging = (Boolean) NConfig.get(NConfig.Key.showForagingIcons);
        if (foraging != null) showForagingIcons = foraging;
```

Next to `isAnimalMark`:

```java
    private static boolean isForageMark(LabeledMinimapMark mark) {
        return mark != null && nurgling.tools.ForageMarkerLogic.isForageId(mark.getLocationId());
    }
```

In `isOreSpotMark`, after the animal check:

```java
        if (isForageMark(mark)) return false;
```

- [ ] **Step 3: Draw / tooltip / hit-test / search / hideall**

Helper to skip a forage mark (use in `drawLabeledMarks`, tooltip labeled-mark loop ~1674, and `labeledMarkAt`):

```java
    private boolean skipForageMark(LabeledMinimapMark mark) {
        if (!isForageMark(mark)) return false;
        if (!showForagingIcons) return true;
        MapWnd mapwnd = (NUtils.getGameUI() != null) ? NUtils.getGameUI().mapfile : null;
        if (mapwnd != null && Utils.eq(mapwnd.markcfg, MapWnd.MarkerConfig.hideall)) return true;
        String markerSearchPattern = null;
        Widget parentWidget = this.parent;
        while (parentWidget != null) {
            if (parentWidget instanceof NMapWnd) {
                markerSearchPattern = ((NMapWnd) parentWidget).markerSearchPattern;
                break;
            }
            parentWidget = parentWidget.parent;
        }
        return !nurgling.tools.ForageMarkerLogic.matchesMapSearch(
            mark.resourceType, mark.label, markerSearchPattern);
    }
```

At the start of each labeled-mark loop, after animal skip:

```java
            if (skipForageMark(mark)) continue;
```

Tooltip for forage (in the labeled-mark tooltip loop, before `return Text.render(resourceType);`):

```java
                            if (isForageMark(mark)) {
                                String lab = mark.label != null ? mark.label : "";
                                return Text.render(resourceType + (lab.isEmpty() ? "" : " " + lab));
                            }
```

RMB delete in the labeled-mark `mouseup`/`mousedown` branch (~2922): **before** `isOreSpotMark`, handle forage like quarryartz (Shift+RMB deletes):

```java
                    } else if (isForageMark(labeledMark)) {
                        if ((ui.modflags() & UI.MOD_SHIFT) != 0) {
                            gui.labeledMarkService.removeMark(labeledMark);
                            gui.msg("Удалена метка " + labeledMark.resourceType + " " + labeledMark.label, java.awt.Color.YELLOW);
                            return true;
                        }
                        return true;
                    } else if(isOreSpotMark(labeledMark)) {
```

Keep the existing quarryartz branch first. Insert forage after quarryartz, before ore spots.

- [ ] **Step 4: `ant test` — still green**

---

### Task 5: Big-map toggle + search window

**Files:**
- Create: `src/nurgling/widgets/ForagingSearchWindow.java`
- Modify: `src/nurgling/NGameUI.java` — field `public nurgling.widgets.ForagingSearchWindow foragingSearchWindow = null;` next to `gemstoneSearchWindow`
- Modify: `src/nurgling/widgets/NMapWnd.java`

**Consumes:** `ForageMarkerLogic`, `showForagingIcons`, `labeledMarkService.removeMark`  
**Produces:** Foraging toggle on big map; RMB opens search

- [ ] **Step 1: `ForagingSearchWindow`**

Copy `src/nurgling/widgets/GemstoneSearchWindow.java` to `ForagingSearchWindow.java` and apply **all** of these substitutions (no other behavior):

- Class name `ForagingSearchWindow`
- Window title `"Foraging Search"`
- Comments/strings: Gemstone → Foraging, gemstone → forage item
- Label `"Gemstone type:"` → `"Item type:"`
- Field `gemstoneTypeDropdown` → `itemTypeDropdown`; `gemstoneTypes` → `itemTypes`; `gemstoneDropdownY` → `itemDropdownY`
- `getDistinctGemstoneTypes`: filter `ForageMarkerLogic.isForageId(mark.getLocationId())` instead of `MasterMiner.isGemstone`
- `performSearch` filter: `ForageMarkerLogic.isForageId` instead of `isGemstone`; quality keep when `ForageMarkerLogic.matchesWindowSearch(mark.resourceType, mark.label, selectedType, Double.isNaN(finalThreshold) ? null : Double.valueOf(finalThreshold))`
- Sort by `ForageMarkerLogic.parseQuality`
- `parseQuality` method: delete local copy; call `ForageMarkerLogic.parseQuality`
- Pan message: `"Map moved to " + mark.resourceType + " " + mark.label`
- Missing segment: `"Forage location is in a different area"`
- Error: `"Error panning to forage location"`
- Remove unused `MasterMiner` import; add `import nurgling.tools.ForageMarkerLogic;`

Dropdown still has `"Any"` first. × still calls `removeMark` then `performSearch()`. LMB still `panMapToLocation`.

- [ ] **Step 2: `NMapWnd` button**

Field:

```java
    MapToggleButton foragingBtn;
```

In constructor, after `animalsBtn` setup and **before** `vectorClearBtn`:

```java
        btnPos = btnPos.sub(animalsBtn.sz.x + btnSpacing, 0);
        foragingBtn = add(new MapToggleButton("tree", "Toggle Foraging markers (Right-click: Foraging Search)", this::openForagingSearch), btnPos);
        foragingBtn.a = getForagingIconsState();
        foragingBtn.changed(val -> setForagingIconsState(val));
```

Then vector button uses `foragingBtn.sz` for spacing:

```java
        btnPos = btnPos.sub(foragingBtn.sz.x + btnSpacing, 0);
        vectorClearBtn = add(...)
```

Get/set (same pattern as animals):

```java
    private boolean getForagingIconsState() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            return ((NMiniMap) gui.mmap).showForagingIcons;
        return true;
    }

    private void setForagingIconsState(boolean val) {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            ((NMiniMap) gui.mmap).showForagingIcons = val;
        if(view instanceof NMiniMap)
            ((NMiniMap) view).showForagingIcons = val;
        NConfig.set(NConfig.Key.showForagingIcons, val);
        NConfig.needUpdate();
    }

    private void openForagingSearch() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null) {
            if(gui.foragingSearchWindow != null) {
                if(gui.foragingSearchWindow.visible()) {
                    gui.foragingSearchWindow.hide();
                } else {
                    gui.foragingSearchWindow.show();
                    gui.foragingSearchWindow.raise();
                }
            } else {
                gui.foragingSearchWindow = new ForagingSearchWindow(gui);
                gui.add(gui.foragingSearchWindow, new Coord(100, 100));
                gui.foragingSearchWindow.show();
            }
        }
    }
```

`resize`: include `foragingBtn != null` in the null-check; place it after `animalsBtn` and before `vectorClearBtn` (same as constructor order).

- [ ] **Step 3: `ant test` — full suite, 0 failures (includes `ForageMarkerLogicTest`)**

---

## Spec coverage

| Spec | Task |
|---|---|
| Pick / not Pick up / not garden pot / Q ≥ 40 | 1, 3 |
| Wait 2s for quality | 3 |
| Manual + Forager bot (flower `nchoose`) | 3 |
| `forage_` id, item name, `q45`, item icon, gob tiles, persist | 1, 2, 3 |
| Dedup 11 tiles keep best Q | 1, 2 |
| Toggle Foraging, persist config, both maps | 4, 5 |
| Hideall + bottom search name/Q | 4 |
| Search window type + min Q, pan, × delete | 5 |
| Shift+RMB delete | 4 |
| No vanilla markers / no second service | all |
| Tests listed in spec | 1 |
