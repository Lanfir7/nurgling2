# Builder Resource Calculator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline; user said «делай»). Steps use checkbox (`- [ ]`) syntax for tracking. Do **not** commit unless the user asks (user git rule).

**Goal:** After zone selection, `MultiAreaConfirm` shows building count and total materials from each builder’s current recipe × ghost count.

**Architecture:** Static `BuildRecipes` table keyed by menu name. `SelectAreaWithLiveGhosts` passes `buildingName` into `MultiAreaConfirm`. Dialog multiplies and renders via `L10n`. Bots keep gathering as they do today.

**Tech Stack:** Java 8, JUnit 5 via `ant test`, Haven `Window`/`Label`, `nurgling.i18n.L10n`.

## Global Constraints

- Java 8: no `var`, no `List.of` / `Map.of`.
- Recipe numbers copied from current `Build*.java`, not wiki.
- Trellis, Cellar, world blueprint editor: out of scope.
- Missing recipe or `buildings <= 0`: empty list, dialog unchanged besides existing summary.
- Smoke Shed thatch vs bough is one line `thatch_or_bough` count 6.
- No git commits unless the user requests them.

## File structure

- Create: `src/nurgling/actions/bots/BuildRecipes.java` — static recipes + `totals`.
- Create: `test/nurgling/actions/bots/BuildRecipesTest.java`
- Modify: `src/nurgling/widgets/bots/MultiAreaConfirm.java`
- Modify: `src/nurgling/actions/bots/SelectAreaWithLiveGhosts.java` (constructor call only)
- Modify: `src/lang/messages.properties`, `src/lang/messages_ru.properties`

---

### Task 1: BuildRecipes + tests

**Files:**
- Create: `test/nurgling/actions/bots/BuildRecipesTest.java`
- Create: `src/nurgling/actions/bots/BuildRecipes.java`

**Interfaces:**
- Produces: `BuildRecipes.Line(String materialId, int count)`, `BuildRecipes.of(String)`, `BuildRecipes.totals(String, int)`, `BuildRecipes.slug(String)`

- [ ] **Step 1: Write the failing test**

```java
package nurgling.actions.bots;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildRecipesTest {
    @Test
    void dryingFrameTimesGhostCount() {
        List<BuildRecipes.Line> totals = BuildRecipes.totals("Drying Frame", 24);
        assertEquals(3, totals.size());
        assertEquals("branch", totals.get(0).materialId);
        assertEquals(120, totals.get(0).count);
        assertEquals("bough", totals.get(1).materialId);
        assertEquals(48, totals.get(1).count);
        assertEquals("string", totals.get(2).materialId);
        assertEquals(48, totals.get(2).count);
    }

    @Test
    void cupboardPerBuilding() {
        List<BuildRecipes.Line> one = BuildRecipes.totals("Cupboard", 1);
        assertEquals(1, one.size());
        assertEquals("board", one.get(0).materialId);
        assertEquals(8, one.get(0).count);
    }

    @Test
    void smokeShedKeepsThatchOrBoughAsOneLine() {
        List<BuildRecipes.Line> totals = BuildRecipes.totals("Smoke Shed", 2);
        assertEquals(4, totals.size());
        assertEquals("board", totals.get(0).materialId);
        assertEquals(24, totals.get(0).count);
        assertEquals("block", totals.get(1).materialId);
        assertEquals(8, totals.get(1).count);
        assertEquals("thatch_or_bough", totals.get(2).materialId);
        assertEquals(12, totals.get(2).count);
        assertEquals("brick", totals.get(3).materialId);
        assertEquals(20, totals.get(3).count);
    }

    @Test
    void unknownOrZeroIsEmpty() {
        assertTrue(BuildRecipes.totals("Not A Building", 10).isEmpty());
        assertTrue(BuildRecipes.totals("Drying Frame", 0).isEmpty());
        assertTrue(BuildRecipes.totals(null, 5).isEmpty());
    }

    @Test
    void slugMapsMenuNameToL10nSuffix() {
        assertEquals("drying_frame", BuildRecipes.slug("Drying Frame"));
        assertEquals("wooden_chest", BuildRecipes.slug("Wooden Chest"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `ant test`  
Expected: compile or test fail — `BuildRecipes` missing.

- [ ] **Step 3: Write `BuildRecipes`**

Java 8. `LinkedHashMap` preserves display order. `Line.count` is per-building in `of()`, multiplied in `totals()`.

Recipes (menu name → lines):

- Cupboard: board 8
- Barrel: board 5
- Cheese Rack: board 6, block 4
- Crate: board 4
- Wooden Chest: board 4, nugget 4
- Drying Frame: branch 5, bough 2, string 2
- Herbalist Table: block 4, board 4, finer_plant_fibre 8
- Kiln: clay 35
- Large Chest: board 5, metal_bar 2, leather 4, rope 2, bone_glue 3
- Mound Bed: mulch 12, straw 6
- Smoke Shed: board 12, block 4, thatch_or_bough 6, brick 10
- Stone Casket: stone 20, nugget 2
- Tar Kiln: stone 35, clay 50
- Tanning Tub: board 4, block 2
- Dream Catcher: bough 4, string 2

```java
public static List<Line> totals(String buildingName, int buildings) {
    if (buildings <= 0)
        return Collections.emptyList();
    List<Line> recipe = of(buildingName);
    if (recipe.isEmpty())
        return Collections.emptyList();
    List<Line> out = new ArrayList<Line>(recipe.size());
    for (Line line : recipe)
        out.add(new Line(line.materialId, line.count * buildings));
    return out;
}

public static String slug(String buildingName) {
    if (buildingName == null)
        return "";
    return buildingName.toLowerCase(Locale.ROOT).replace(' ', '_');
}
```

- [ ] **Step 4: Run tests — expect PASS for `BuildRecipesTest`**

Run: `ant test`

- [ ] **Step 5: Commit** — skip (user git rule).

---

### Task 2: Dialog + L10n + wire-up

**Files:**
- Modify: `src/nurgling/widgets/bots/MultiAreaConfirm.java`
- Modify: `src/nurgling/actions/bots/SelectAreaWithLiveGhosts.java` line ~185
- Modify: `src/lang/messages.properties`
- Modify: `src/lang/messages_ru.properties`

**Interfaces:**
- Consumes: `BuildRecipes.totals`, `BuildRecipes.slug`, `Line.materialId`, `Line.count`
- Produces: `MultiAreaConfirm(String buildingName, int positionsSoFar, int areasSoFar)`

- [ ] **Step 1: Localization keys**

EN (`messages.properties`):

```
build.calc.item={0} — {1}
build.calc.need=Need:
build.name.cupboard=Cupboard
build.name.barrel=Barrel
build.name.cheese_rack=Cheese Rack
build.name.crate=Crate
build.name.wooden_chest=Wooden Chest
build.name.drying_frame=Drying Frame
build.name.herbalist_table=Herbalist Table
build.name.kiln=Kiln
build.name.large_chest=Large Chest
build.name.mound_bed=Mound Bed
build.name.smoke_shed=Smoke Shed
build.name.stone_casket=Stone Casket
build.name.tar_kiln=Tar Kiln
build.name.tanning_tub=Tanning Tub
build.name.dream_catcher=Dream Catcher
build.mat.board=Board
build.mat.block=Block
build.mat.branch=Branch
build.mat.bough=Bough
build.mat.string=String
build.mat.nugget=Nugget
build.mat.finer_plant_fibre=Finer Plant Fibre
build.mat.clay=Clay
build.mat.metal_bar=Metal Bar
build.mat.leather=Leather
build.mat.rope=Rope
build.mat.bone_glue=Bone Glue
build.mat.mulch=Mulch
build.mat.straw=Straw
build.mat.thatch_or_bough=Thatch / Bough
build.mat.brick=Brick
build.mat.stone=Stone
```

RU (`messages_ru.properties`):

```
build.calc.item={0} — {1} шт
build.calc.need=Нужно:
build.name.cupboard=Шкаф
build.name.barrel=Бочка
build.name.cheese_rack=Сырная полка
build.name.crate=Ящик
build.name.wooden_chest=Сундук
build.name.drying_frame=Сушилка
build.name.herbalist_table=Стол травника
build.name.kiln=Печь
build.name.large_chest=Большой сундук
build.name.mound_bed=Грядка
build.name.smoke_shed=Коптильня
build.name.stone_casket=Каменная шкатулка
build.name.tar_kiln=Дёгтевая печь
build.name.tanning_tub=Дубильный чан
build.name.dream_catcher=Ловец снов
build.mat.board=Доски
build.mat.block=Блоки
build.mat.branch=Бренчи
build.mat.bough=Броучи
build.mat.string=Стринги
build.mat.nugget=Наггеты
build.mat.finer_plant_fibre=Тонкое волокно
build.mat.clay=Глина
build.mat.metal_bar=Слитки
build.mat.leather=Кожа
build.mat.rope=Верёвка
build.mat.bone_glue=Костный клей
build.mat.mulch=Мульча
build.mat.straw=Солома
build.mat.thatch_or_bough=Солома / Броучи
build.mat.brick=Кирпичи
build.mat.stone=Камень
```

- [ ] **Step 2: `MultiAreaConfirm` shows totals between summary and buttons**

Constructor becomes `(String buildingName, int positionsSoFar, int areasSoFar)`. After the summary `Label`, if `BuildRecipes.totals(buildingName, positionsSoFar)` is non-empty, add:

1. `L10n.get("build.calc.item", L10n.get("build.name." + slug), positionsSoFar)`
2. `L10n.get("build.calc.need")`
3. one label per line: `L10n.get("build.calc.item", L10n.get("build.mat." + materialId), count)`

Then existing buttons. `pack()`. Keep `State` / `check()` / close behavior.

- [ ] **Step 3: Wire `SelectAreaWithLiveGhosts`**

Replace:

```java
MultiAreaConfirm confirm = new MultiAreaConfirm(totalPositions, areasSelected);
```

with:

```java
MultiAreaConfirm confirm = new MultiAreaConfirm(buildingName, totalPositions, areasSelected);
```

- [ ] **Step 4: `ant test` — all pass**

- [ ] **Step 5: Commit** — skip.
