# Quest Pointer Map Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline; user said «делай»). Steps use checkbox (`- [ ]`) syntax for tracking. Do **not** commit unless the user asks (user git rule).

**Goal:** Right-click on a quest-giver compass draws a finite map line to the icon distance, with a treasure X at the end; dowsing rays stay infinite.

**Architecture:** `DirectionalVector.showEndpoint` (default false). Pointer path `NMapView.addDirectionalVector` sets it true. `NMiniMap` draws origin→target + X when true, else the existing 10000-tile ray.

**Tech Stack:** Java, JUnit 5 via `ant test`, Haven `GOut` / `NMiniMap`.

## Global Constraints

- Dowsing (`NProspecting`) must keep `showEndpoint=false`.
- Clear UX unchanged.
- No 3D-world mark.
- No git commits unless the user requests them.

## File structure

- Modify: `src/nurgling/tools/DirectionalVector.java`
- Modify: `src/nurgling/NMapView.java` (`addDirectionalVector`)
- Modify: `src/nurgling/widgets/NMiniMap.java` (vector draw loop)
- Create: `test/nurgling/tools/DirectionalVectorTest.java`

---

### Task 1: DirectionalVector flag + mapEndTile

**Files:**
- Create: `test/nurgling/tools/DirectionalVectorTest.java`
- Modify: `src/nurgling/tools/DirectionalVector.java`

**Interfaces:**
- Produces: `boolean showEndpoint`; constructors `(origin, target, name, gobId)`, `(..., Color)`, `(..., Color, boolean)`, `(..., boolean)`; `Coord2d mapEndTile(double infiniteRayLength)`

- [x] **Step 1: Write the failing test**

```java
package nurgling.tools;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectionalVectorTest {
    private static final Coord ORIGIN = new Coord(10, 20);
    private static final Coord TARGET = new Coord(40, 60);

    @Test
    void defaultConstructorHasNoEndpoint() {
        DirectionalVector v = new DirectionalVector(ORIGIN, TARGET, "Ekhagen", -1);
        assertFalse(v.showEndpoint);
        assertEquals(v.getTilePointAt(10000), v.mapEndTile(10000));
    }

    @Test
    void colorConstructorHasNoEndpoint() {
        DirectionalVector v = new DirectionalVector(ORIGIN, TARGET, "Dowse Edge 1", -1, Color.RED);
        assertFalse(v.showEndpoint);
    }

    @Test
    void endpointConstructorStopsAtTarget() {
        DirectionalVector v = new DirectionalVector(ORIGIN, TARGET, "Ekhagen", -1, true);
        assertTrue(v.showEndpoint);
        assertEquals(new haven.Coord2d(TARGET), v.mapEndTile(10000));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `ant test -Dtest.arg=--select-class=nurgling.tools.DirectionalVectorTest`  
If ant has no such property, run `ant test` and expect compile failure on `showEndpoint` / `mapEndTile`.

- [ ] **Step 3: Minimal implementation**

Add `public final boolean showEndpoint`. Full constructor takes `(..., Color color, boolean showEndpoint)`. Overloads: color-only → `false`; 4-arg → `false`; `(..., boolean showEndpoint)` → `COLORS[0]` + flag.

```java
public Coord2d mapEndTile(double infiniteRayLength) {
    if(showEndpoint) {
        return new Coord2d(targetTileCoords);
    }
    return getTilePointAt(infiniteRayLength);
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `ant test`

- [ ] **Step 5: Skip commit** (user git rule)

---

### Task 2: Pointer path sets the flag; minimap draws finite line + X

**Files:**
- Modify: `src/nurgling/NMapView.java` around `addDirectionalVector`
- Modify: `src/nurgling/widgets/NMiniMap.java` vector draw loop (~554–581)

**Interfaces:**
- Consumes: `DirectionalVector.showEndpoint`, `mapEndTile(double)`
- Produces: pointer vectors with `showEndpoint=true`; X in `vector.color` when target is on-screen

- [ ] **Step 1: `addDirectionalVector` uses the boolean constructor**

```java
nurgling.tools.DirectionalVector vector = new nurgling.tools.DirectionalVector(
    originTileCoords, targetTileCoords, targetName, targetGobId, true
);
```

- [ ] **Step 2: NMiniMap draws endpoint or infinite ray**

Replace far-point-only drawing:

```java
Coord2d endTiles = vector.mapEndTile(10000);
Coord endScreenPos = vector.showEndpoint
    ? vector.targetTileCoords.sub(dloc.tc).div(scalef()).add(hsz)
    : new Coord((int)endTiles.x, (int)endTiles.y).sub(dloc.tc).div(scalef()).add(hsz);

Coord2d[] clipped = clipLineToRect(new Coord2d(originScreenPos), new Coord2d(endScreenPos), new Coord2d(sz));
if(clipped != null && vector.color != null) {
    g.chcolor(vector.color.getRed(), vector.color.getGreen(), vector.color.getBlue(), vector.color.getAlpha());
    g.line(clipped[0].floor(), clipped[1].floor(), 2);
    g.chcolor();
}
if(vector.showEndpoint && vector.color != null
        && endScreenPos.x >= 0 && endScreenPos.y >= 0
        && endScreenPos.x < sz.x && endScreenPos.y < sz.y) {
    int arm = UI.scale(5);
    g.chcolor(vector.color.getRed(), vector.color.getGreen(), vector.color.getBlue(), vector.color.getAlpha());
    g.line(endScreenPos.add(-arm, -arm), endScreenPos.add(arm, arm), 2);
    g.line(endScreenPos.add(-arm, arm), endScreenPos.add(arm, -arm), 2);
    g.chcolor();
}
```

- [ ] **Step 3: Run `ant test` — all pass; NProspecting still uses color constructor (no flag)**

- [ ] **Step 4: Skip commit**
