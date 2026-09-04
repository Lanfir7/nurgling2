# Craft Atlas Resource Planner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add VSpec-aware inventory and warehouse material selection, automatic quality projection, and multi-slot resource collection to Craft Atlas.

**Architecture:** A pure planner converts per-slot candidates and user selections into deterministic allocations, shortages, and projected quality. A runtime source adapts live inventory and `CraftIngredientStock` rows into planner candidates, while a collector executes only the warehouse allocations through the existing storage-fetch bot. The existing Atlas details and window widgets host selectors and footer controls without moving database or bot logic into rendering code.

**Tech Stack:** Java 8, Haven widget toolkit, Nurgling `VSpec` and storage services, JUnit 5, Ant.

**Spec:** `docs/superpowers/specs/2026-09-05-craft-atlas-resource-planner-design.md`

## Global Constraints

- Inventory candidates are marked with `★`; warehouse candidates include their storage label.
- Default to the highest-quality inventory candidate when one exists, otherwise the highest-quality warehouse candidate.
- A concrete selection may consume only that material, starting at the selected quality and falling back through lower-quality batches of the same material.
- `All matching` may consume different members of the slot's `VSpec` group.
- Validate every required slot before starting any transfer; shortages start no bot work.
- Auto quality uses quantity-weighted slot averages followed by the arithmetic mean of required slots.
- Reference-only recipes stay readable, but collection requires complete observed input resources.
- Preserve the current `Open craft` behavior and responsive Atlas layout.

---

### Task 1: Pure material planning model

**Files:**
- Create: `src/nurgling/craftatlas/CraftAtlasMaterialPlanner.java`
- Test: `test/nurgling/craftatlas/CraftAtlasMaterialPlannerTest.java`

**Interfaces:**
- Produces: `Candidate`, `Source`, `Selection`, `SlotRequest`, `Allocation`, `SlotPlan`, and `Plan` nested immutable types.
- Produces: `Selection defaultSelection(List<Candidate> candidates)` plus `Selection.ignored()` for optional inputs.
- Produces: `Plan plan(List<SlotRequest> slots, Map<Integer, List<Candidate>> candidates, Map<Integer, Selection> selections, int craftCount)`.
- Consumes: only JDK collections; this class must not import Haven widgets, database classes, or `GroupedItem`.

- [ ] **Step 1: Write failing tests for default selection and deterministic ordering**

```java
@Test
void defaultsToBestInventoryCandidateBeforeHigherWarehouseQuality() {
    Candidate inv = candidate("inv-linen-80", "Linen Cloth", 80, 2, Source.INVENTORY);
    Candidate db = candidate("db-linen-120", "Linen Cloth", 120, 8, Source.STORAGE);

    Selection selected = CraftAtlasMaterialPlanner.defaultSelection(List.of(db, inv));

    assertEquals("Linen Cloth", selected.material);
    assertEquals("inv-linen-80", selected.preferredCandidateId);
}

@Test
void defaultsToHighestWarehouseQualityWhenInventoryIsEmpty() {
    Selection selected = CraftAtlasMaterialPlanner.defaultSelection(List.of(
            candidate("db-90", "Linen Cloth", 90, 5, Source.STORAGE),
            candidate("db-120", "Linen Cloth", 120, 1, Source.STORAGE)));
    assertEquals("db-120", selected.preferredCandidateId);
}
```

- [ ] **Step 2: Run the suite and verify RED**

Run: `rtk ant test`

Expected: compilation fails because `CraftAtlasMaterialPlanner` does not exist.

- [ ] **Step 3: Add the immutable planner types and default selection**

```java
public final class CraftAtlasMaterialPlanner {
    public enum Source { INVENTORY, STORAGE }

    public static final class Candidate {
        public final String id, material, location;
        public final double quality;
        public final int count;
        public final Source source;
        // Validate non-empty id/material, positive count, finite positive quality.
    }

    public static final class Selection {
        public enum Mode { ALL, PREFERRED, IGNORED }
        public final Mode mode;
        public final String material, preferredCandidateId;
        public static Selection all() { return new Selection(null, null); }
        public static Selection ignored() { return new Selection(Mode.IGNORED, null, null); }
        public static Selection preferred(Candidate value) {
            return new Selection(Mode.PREFERRED, value.material, value.id);
        }
        public boolean isAll() { return mode == Mode.ALL; }
        public boolean isIgnored() { return mode == Mode.IGNORED; }
    }

    public static Selection defaultSelection(List<Candidate> candidates) {
        return candidates.stream()
                .filter(c -> c.source == Source.INVENTORY)
                .sorted(CANDIDATE_ORDER)
                .findFirst()
                .map(Selection::preferred)
                .orElseGet(() -> candidates.stream().sorted(CANDIDATE_ORDER)
                        .findFirst().map(Selection::preferred).orElse(Selection.all()));
    }
}
```

Define one comparator: quality descending, inventory before storage for equal quality, material case-insensitive, then id. Use it for both displayed candidates and allocation fallback.

- [ ] **Step 4: Run tests and verify GREEN**

Run: `rtk ant test`

Expected: all tests pass.

- [ ] **Step 5: Write failing allocation tests**

```java
@Test
void selectedMaterialFallsBackOnlyThroughItsLowerQualities() {
    SlotRequest fabric = new SlotRequest(0, 3, false,
            List.of("Linen Cloth", "Hemp Cloth"));
    List<Candidate> stock = List.of(
            candidate("linen-100", "Linen Cloth", 100, 2, Source.STORAGE),
            candidate("hemp-99", "Hemp Cloth", 99, 20, Source.STORAGE),
            candidate("linen-92", "Linen Cloth", 92, 10, Source.STORAGE));

    Plan plan = CraftAtlasMaterialPlanner.plan(List.of(fabric), Map.of(0, stock),
            Map.of(0, Selection.preferred(stock.get(0))), 2);

    assertTrue(plan.complete);
    assertEquals(List.of(2, 4), plan.slots.get(0).allocations.stream()
            .map(a -> a.count).collect(Collectors.toList()));
    assertTrue(plan.slots.get(0).allocations.stream()
            .allMatch(a -> a.material.equals("Linen Cloth")));
}

@Test
void allMatchingCanMixVSpecMembersByQuality() {
    SlotRequest fabric = new SlotRequest(0, 4, false,
            List.of("Linen Cloth", "Hemp Cloth"));
    List<Candidate> stock = List.of(
            candidate("linen-110", "Linen Cloth", 110, 1, Source.STORAGE),
            candidate("hemp-105", "Hemp Cloth", 105, 3, Source.STORAGE),
            candidate("linen-90", "Linen Cloth", 90, 8, Source.STORAGE));
    Plan plan = CraftAtlasMaterialPlanner.plan(List.of(fabric), Map.of(0, stock),
            Map.of(0, Selection.all()), 1);
    assertEquals(List.of("linen-110", "hemp-105"), plan.slots.get(0).allocations.stream()
            .map(a -> a.candidateId).collect(Collectors.toList()));
}

@Test
void anyShortageMakesWholePlanIncomplete() {
    List<SlotRequest> slots = List.of(
            new SlotRequest(0, 1, false, List.of("Glue")),
            new SlotRequest(1, 2, false, List.of("Board")));
    Plan plan = CraftAtlasMaterialPlanner.plan(slots, Map.of(
            0, List.of(candidate("glue", "Glue", 50, 1, Source.INVENTORY)),
            1, List.of(candidate("board", "Board", 50, 1, Source.STORAGE))),
            Map.of(), 1);
    assertFalse(plan.complete);
    assertEquals(1, plan.slots.get(1).missing);
    assertNull(plan.quality);
}

@Test
void qualityWeightsBatchesWithinSlotButNotRecipeSlots() {
    List<SlotRequest> slots = List.of(
            new SlotRequest(0, 4, false, List.of("Cloth")),
            new SlotRequest(1, 1, false, List.of("Glue")));
    Plan plan = CraftAtlasMaterialPlanner.plan(slots, Map.of(
            0, List.of(candidate("cloth-100", "Cloth", 100, 1, Source.STORAGE),
                    candidate("cloth-80", "Cloth", 80, 3, Source.STORAGE)),
            1, List.of(candidate("glue-40", "Glue", 40, 1, Source.STORAGE))),
            Map.of(0, Selection.all(), 1, Selection.all()), 1);
    assertEquals(62.5, plan.quality, 0.001);
}

@Test
void ignoredOptionalSlotAddsNoDemandOrQuality() {
    SlotRequest optional = new SlotRequest(0, 5, true, List.of("Pepper"));
    Plan plan = CraftAtlasMaterialPlanner.plan(List.of(optional), Map.of(),
            Map.of(0, Selection.ignored()), 10);
    assertTrue(plan.complete);
    assertTrue(plan.slots.get(0).allocations.isEmpty());
    assertNull(plan.quality);
}
```

- [ ] **Step 6: Run tests and verify RED**

Run: `rtk ant test`

Expected: failures show missing `plan` behavior.

- [ ] **Step 7: Implement allocation, shortage, and quality calculation**

```java
int need = Math.multiplyExact(slot.unitsPerCraft, Math.max(1, craftCount));
List<Candidate> allowed = orderedCandidates.stream()
        .filter(c -> selection.isAll() || c.material.equals(selection.material))
        .collect(Collectors.toList());
movePreferredCandidateToFront(allowed, selection.preferredCandidateId);
for (Candidate candidate : allowed) {
    int take = Math.min(need - supplied, candidate.count);
    if (take > 0) allocations.add(new Allocation(candidate.id, candidate.material,
            candidate.quality, take, candidate.source));
}
double slotQuality = allocations.stream().mapToDouble(a -> a.quality * a.count).sum() / supplied;
```

An ignored selection is valid only for an optional slot and produces zero demand. A required slot with `Selection.ignored()` remains incomplete. Return `null` quality when any required slot is short or has invalid quality. Compute craft quality with `CraftSlotQuality.meanOfSlotAverages` so the Atlas and normal craft window share one rule.

- [ ] **Step 8: Run tests and commit**

Run: `rtk ant test`

Expected: all tests pass.

```bash
rtk git add src/nurgling/craftatlas/CraftAtlasMaterialPlanner.java test/nurgling/craftatlas/CraftAtlasMaterialPlannerTest.java
rtk git commit -m "feat: plan craft atlas material allocations"
```

---

### Task 2: Build mixed inventory and warehouse candidate snapshots

**Files:**
- Create: `src/nurgling/craftatlas/CraftAtlasMaterialSource.java`
- Modify: `src/nurgling/craftatlas/CraftAtlasEntry.java`
- Modify: `src/nurgling/craftatlas/MenuCraftCatalog.java`
- Modify: `src/nurgling/tools/CraftIngredientStock.java`
- Test: `test/nurgling/craftatlas/CraftAtlasMaterialSourceTest.java`
- Modify test: `test/nurgling/craftatlas/MenuCraftCatalogTest.java`
- Test: `test/nurgling/tools/CraftIngredientStockTest.java`

**Interfaces:**
- Consumes: `CraftAtlasEntry.InputSlot`, `CraftIngredientStock.namesFor`, `CraftIngredientStock.search`, `VSpec`, `NGameUI.getInventory()`, and planner `Candidate`.
- Produces: `CraftAtlasEntry.inputsObserved`, set only from a captured server Make window.
- Produces: `Snapshot load(CraftAtlasEntry entry)` with `Map<Integer,List<Candidate>> candidatesBySlot`, `Map<String,GroupedItem> storageByCandidateId`, `List<SlotRequest> slots`, and `boolean collectible`.
- Produces: package-visible pure helpers `allowedNames(InputSlot)` and `merge(int slot, Collection<InventorySample>, List<GroupedItem>)` for tests.

- [ ] **Step 1: Write failing tests for VSpec expansion and mixed source rows**

```java
@Test
void expandsEachVSpecOptionAndRemovesDuplicateNames() {
    VSpec.categories.put("Fabric", jsonNames("Linen Cloth", "Hemp Cloth"));
    try {
        InputSlot slot = new InputSlot(2, false, List.of(
                new IngredientOption("gfx/invobjs/fabric", "Fabric")));
        assertEquals(List.of("Linen Cloth", "Hemp Cloth"),
                CraftAtlasMaterialSource.allowedNames(slot));
    } finally {
        VSpec.categories.remove("Fabric");
    }
}

@Test
void mergeMarksInventoryAndKeepsStoragePhysicalRowsSeparate() {
    SnapshotRows rows = CraftAtlasMaterialSource.merge(0,
            List.of(new InventorySample("Linen Cloth", 90, 2)),
            storageRows("Linen Cloth", 90, 4));
    assertEquals(2, rows.candidates.size());
    assertEquals(Source.INVENTORY, rows.candidates.get(0).source);
    assertEquals(Source.STORAGE, rows.candidates.get(1).source);
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `rtk ant test`

Expected: compilation fails because `CraftAtlasMaterialSource` is missing.

- [ ] **Step 3: Implement name expansion and snapshot assembly**

First add `boolean inputsObserved` to `CraftAtlasEntry` and its builder. In `MenuCraftCatalog.build`, set it when an observation exists and contains at least one input. In `merge`, preserve the live value. Add a catalog test proving an open page backed only by wiki inputs remains non-collectible until its Make-window observation is recorded.

For each `IngredientOption`, call:

```java
boolean grouped = VSpec.categories.containsKey(option.name);
names.addAll(CraftIngredientStock.namesFor(option.name, grouped, null));
```

Use a `LinkedHashSet` to keep stable order. Search warehouse rows once per slot using the union of names. Read the player's main inventory recursively: a top-level ordinary item contributes one unit; a stack contributes its leaf items so counts and qualities match the items that can actually be transferred. Read `NGItem.name()` and quality using the same fallbacks as `NMakewindow.playerInvSamples`: direct `quality`, stack tooltip quality, then nested item average.

Candidate IDs must remain stable during one snapshot:

```java
"slot:" + slotIndex + ":inventory:" + normalizedName + ":" + formattedQuality
"slot:" + slotIndex + ":storage:" + normalizedName + ":" + formattedQuality
```

Store the `GroupedItem` under the storage candidate ID for later collection. Set `Snapshot.collectible` from `entry.inputsObserved`; wiki inputs may populate selectors for reference but never enable collection. Return empty candidates without throwing when the database is unavailable or a live resource is still loading.

- [ ] **Step 4: Add a public stable quality key to `CraftIngredientStock`**

Add:

```java
public static String qualityKey(double quality) {
    return String.format(Locale.ROOT, "%.2f", quality);
}
```

Use it in `groupByQuality` instead of locale-sensitive `String.format("%.2f", ...)`. Extend `CraftIngredientStockTest` to set a comma-decimal locale and prove qualities still group identically.

- [ ] **Step 5: Run tests and commit**

Run: `rtk ant test`

Expected: all tests pass.

```bash
rtk git add src/nurgling/craftatlas/CraftAtlasMaterialSource.java src/nurgling/craftatlas/CraftAtlasEntry.java src/nurgling/craftatlas/MenuCraftCatalog.java src/nurgling/tools/CraftIngredientStock.java test/nurgling/craftatlas/CraftAtlasMaterialSourceTest.java test/nurgling/craftatlas/MenuCraftCatalogTest.java test/nurgling/tools/CraftIngredientStockTest.java
rtk git commit -m "feat: load craft atlas inventory and storage stock"
```

---

### Task 3: Execute a complete warehouse allocation safely

**Files:**
- Create: `src/nurgling/actions/bots/CraftAtlasResourceCollector.java`
- Modify: `src/nurgling/actions/bots/FetchStorageItemBot.java`
- Test: `test/nurgling/actions/bots/CraftAtlasResourceCollectorTest.java`
- Test: `test/nurgling/actions/bots/FetchStorageItemBotTest.java`

**Interfaces:**
- Consumes: planner `Plan` and `Allocation`, material-source `Snapshot.storageByCandidateId`, `GroupedItem`, `BotExecutor`, and `FetchStorageItemBot`.
- Produces: `CraftAtlasResourceCollector(Plan plan, Map<String,GroupedItem> storageRows)` implementing `Action`.
- Produces: `FetchStorageItemBot.actualCollected()` and strict success semantics: requested count must be fully collected.
- Produces: package-visible `requests(Plan, Map<String,GroupedItem>)` returning ordered immutable `FetchRequest` values for deterministic tests.

- [ ] **Step 1: Write failing request-order and all-or-nothing tests**

```java
@Test
void emitsOnlyStorageAllocationsInPlannerOrder() {
    Plan plan = planWith(
            allocation("inv-100", "Linen Cloth", 100, 2, Source.INVENTORY),
            allocation("db-100", "Linen Cloth", 100, 3, Source.STORAGE),
            allocation("db-92", "Linen Cloth", 92, 1, Source.STORAGE));

    List<FetchRequest> requests = CraftAtlasResourceCollector.requests(plan, storageMap);

    assertEquals(List.of("db-100", "db-92"), requests.stream()
            .map(r -> r.candidateId).collect(Collectors.toList()));
    assertEquals(List.of(3, 1), requests.stream().map(r -> r.count).collect(Collectors.toList()));
}

@Test
void rejectsIncompletePlanBeforeBuildingRequests() {
    assertThrows(IllegalArgumentException.class,
            () -> CraftAtlasResourceCollector.requests(incompletePlan(), Map.of()));
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `rtk ant test`

Expected: compilation fails because the collector is missing.

- [ ] **Step 3: Implement request conversion and sequential action**

```java
for (FetchRequest request : requests) {
    FetchStorageItemBot fetch = new FetchStorageItemBot(request.group, request.count,
            request.group.items);
    Results result = fetch.run(gui);
    if (!result.IsSuccess() || fetch.actualCollected() != request.count) {
        gui.error(L10n.get("craft_atlas.collect_shortage")
                .replace("{0}", request.material)
                .replace("{1}", Integer.toString(request.count - fetch.actualCollected())));
        return Results.FAIL();
    }
}
return Results.SUCCESS();
```

Before the loop, reject missing storage-row IDs. Keep a single collector bot job so two button presses cannot interleave navigation.

- [ ] **Step 4: Make `FetchStorageItemBot` report exact collection**

Add an instance field reset at the start of `run`, set it from the final inventory delta, and expose:

```java
public int actualCollected() { return actualCollected; }
```

Return success only when `actualCollected >= targetCount`; partial retrieval is failure. Keep database deletion limited to records actually transferred. Add package-visible `static boolean isComplete(int target, int actual)` and test `isComplete(4, 4)`, `isComplete(4, 5)`, and `!isComplete(4, 3)` without constructing `NGameUI`.

- [ ] **Step 5: Run tests and commit**

Run: `rtk ant test`

Expected: all tests pass.

```bash
rtk git add src/nurgling/actions/bots/CraftAtlasResourceCollector.java src/nurgling/actions/bots/FetchStorageItemBot.java test/nurgling/actions/bots/CraftAtlasResourceCollectorTest.java test/nurgling/actions/bots/FetchStorageItemBotTest.java
rtk git commit -m "feat: collect planned craft atlas resources"
```

---

### Task 4: Ingredient dropdowns and automatic quality in details

**Files:**
- Create: `src/nurgling/widgets/craftatlas/CraftAtlasIngredientSelector.java`
- Modify: `src/nurgling/widgets/craftatlas/CraftAtlasDetails.java`
- Test: `test/nurgling/widgets/craftatlas/CraftAtlasIngredientSelectorTest.java`
- Modify test: `test/nurgling/widgets/craftatlas/CraftAtlasDetailsTest.java`

**Interfaces:**
- Consumes: source `Snapshot`, planner `Selection` and `Plan`, existing input `DetailRow` positions, `Dropbox`, `CheckBox`, `TextEntry`.
- Produces: `CraftAtlasIngredientSelector(int width, Consumer<Selection> changed)` with `setCandidates(List<Candidate>, Selection)` and `Selection selection()`.
- Produces from details: `setCraftCount(int)`, `Plan materialPlan()`, `Snapshot materialSnapshot()`, `boolean canCollect()`, and `refreshMaterials()`.
- Produces: `setPlanChanged(Runnable)` callback for footer enablement.

- [ ] **Step 1: Write failing selector formatting and default tests**

```java
@Test
void inventoryCandidateIsFormattedWithStarAndLocation() {
    Candidate value = candidate("inv", "Linen Cloth", 120, 3, Source.INVENTORY, "Inventory");
    assertEquals("★ Linen Cloth · Q120 · 3 pcs. · Inventory",
            CraftAtlasIngredientSelector.label(value, ENGLISH_LABELS));
}

@Test
void allMatchingChoiceHasNoConcreteMaterial() {
    assertTrue(CraftAtlasIngredientSelector.allMatching().selection.isAll());
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `rtk ant test`

Expected: compilation fails because the selector is missing.

- [ ] **Step 3: Implement the dropdown widget**

Subclass `Dropbox<CraftAtlasIngredientSelector.Choice>`. For an optional slot, the first choice is `Do not use` and maps to `Selection.ignored()`. The next choice is `All matching` only for slots with more than one allowed concrete name. Remaining choices follow the planner comparator. Render localized labels and cap the opened list at ten rows. `change` stores the choice and invokes the callback. Optional slots default to `Do not use`; selecting a stock row includes that slot in demand and quality.

- [ ] **Step 4: Write failing details-state tests**

Add pure/static layout assertions rather than booting a rendering session:

```java
@Test
void qualityControlsFitInsideHeader() {
    HeaderControls c = CraftAtlasDetails.headerControls(620, 1.0);
    assertTrue(c.qualityEntry.right() <= c.autoBox.x);
    assertTrue(c.autoLabel.right() <= 620);
}

@Test
void autoQualityDisablesManualEntryAndUsesCurrentPlan() {
    // Supply a complete q80/q100 plan, enable Auto, assert displayed quality is 90
    // and the manual TextEntry is disabled.
}
```

- [ ] **Step 5: Integrate selectors into `CraftAtlasDetails`**

On selected-entry change:

1. destroy old selector children;
2. load a fresh material snapshot;
3. preserve selections by `recipeResource + slotIndex` when the material still exists;
4. otherwise apply `defaultSelection`;
5. rebuild the planner result for the current craft count;
6. place each selector at the right side of its `INPUT` row.

Move selectors whenever rows scroll or the details widget resizes. Hide a selector when its ingredient row is outside the clipped body. Keep the left icon/name area clickable for recipe navigation.

Add an `Auto` checkbox beside the shifted quality entry. With Auto enabled, disable the entry and feed `plan.quality` into the existing `CraftAtlasQuality.project`. With Auto disabled, preserve current manual parsing. Do not display zero when the plan quality is unavailable.

- [ ] **Step 6: Run tests and commit**

Run: `rtk ant test`

Expected: all tests pass.

```bash
rtk git add src/nurgling/widgets/craftatlas/CraftAtlasIngredientSelector.java src/nurgling/widgets/craftatlas/CraftAtlasDetails.java test/nurgling/widgets/craftatlas/CraftAtlasIngredientSelectorTest.java test/nurgling/widgets/craftatlas/CraftAtlasDetailsTest.java
rtk git commit -m "feat: select craft atlas ingredient stock"
```

---

### Task 5: Footer quantity and Collect resources action

**Files:**
- Modify: `src/nurgling/widgets/craftatlas/CraftAtlasWindow.java`
- Modify: `src/nurgling/widgets/craftatlas/CraftAtlasLayout.java`
- Modify test: `test/nurgling/widgets/craftatlas/CraftAtlasWindowLayoutTest.java`
- Modify test: `test/nurgling/widgets/craftatlas/CraftAtlasLayoutTest.java`

**Interfaces:**
- Consumes: `CraftAtlasDetails.setCraftCount`, `canCollect`, `materialPlan`, `materialSnapshot`; `CraftAtlasResourceCollector`; `BotExecutor.runAsync`.
- Produces: quantity `TextEntry`, `Collect resources` button, and a static footer-layout helper testable without live UI.

- [ ] **Step 1: Write failing footer-bound and quantity-validation tests**

```java
@Test
void countCollectAndOpenButtonsFitWideFooter() {
    FooterControls c = CraftAtlasWindow.footerControls(Coord.of(760, 44), 1.0);
    assertTrue(c.count.right() <= c.collect.x);
    assertTrue(c.collect.right() <= c.open.x);
    assertTrue(c.open.right() <= 760);
}

@Test
void countCollectAndOpenButtonsFitNarrowDetailsPage() {
    FooterControls c = CraftAtlasWindow.footerControls(Coord.of(520, 44), 1.0);
    assertTrue(c.left() >= 0);
    assertTrue(c.right() <= 520);
}
```

Reuse `CraftTarget.parse` for validation and assert blank, zero, and non-numeric counts disable collection.

- [ ] **Step 2: Run tests and verify RED**

Run: `rtk ant test`

Expected: failures show missing footer controls.

- [ ] **Step 3: Add footer widgets and state wiring**

Construct:

```java
craftCount = add(new TextEntry(UI.scale(52), "1") {
    @Override protected void changed() {
        super.changed();
        refreshCollectionState();
    }
});
collectResources = add(new Button(UI.scale(150), L10n.get("craft_atlas.collect_resources"))
        .action(this::collectResources));
```

`refreshCollectionState` parses the count, calls `details.setCraftCount(count)`, and enables collection only when `details.canCollect()` is true. `collectResources` rebuilds the snapshot and plan immediately before dispatch, rejects shortages with the localized slot name/count, then starts exactly one `CraftAtlasResourceCollector` through `BotExecutor.runAsync`.

Store the `Thread` returned by `BotExecutor.runAsync` in `collectionThread`. While it is alive, keep the button disabled. In `CraftAtlasWindow.tick`, detect the transition to a finished thread, clear the field, call `details.refreshMaterials()`, and recompute button state. This keeps all widget mutations on the UI tick.

- [ ] **Step 4: Update responsive layout**

Place controls from right to left as Open craft, Collect resources, and count. Keep the favorite button at the left. If the footer is too narrow for full labels, retain both buttons at their minimum usable width and place the count immediately before Collect; the layout helper must keep all rectangles within the footer for both wide and narrow tests.

- [ ] **Step 5: Run tests and commit**

Run: `rtk ant test`

Expected: all tests pass.

```bash
rtk git add src/nurgling/widgets/craftatlas/CraftAtlasWindow.java src/nurgling/widgets/craftatlas/CraftAtlasLayout.java test/nurgling/widgets/craftatlas/CraftAtlasWindowLayoutTest.java test/nurgling/widgets/craftatlas/CraftAtlasLayoutTest.java
rtk git commit -m "feat: add craft atlas resource collection controls"
```

---

### Task 6: Localization, integration coverage, and release artifact

**Files:**
- Modify: `src/lang/messages.properties`
- Modify: `src/lang/messages_ru.properties`
- Modify: `test/nurgling/widgets/craftatlas/CraftAtlasLocalizationTest.java`
- Modify: `test/nurgling/widgets/craftatlas/CraftAtlasIntegrationTest.java`
- Modify: `bin/hafen.jar`

**Interfaces:**
- Consumes: all prior tasks.
- Produces: complete English/Russian UI copy and the rebuilt client artifact.

- [ ] **Step 1: Write failing localization coverage**

Require these keys in both bundles:

```text
craft_atlas.auto
craft_atlas.all_matching
craft_atlas.do_not_use
craft_atlas.inventory
craft_atlas.collect_count
craft_atlas.collect_resources
craft_atlas.collect_shortage
craft_atlas.collect_unavailable
craft_atlas.quality_unavailable
```

Extend `CraftAtlasLocalizationTest` to assert the values are present, non-empty, and not equal to their keys.

- [ ] **Step 2: Run tests and verify RED**

Run: `rtk ant test`

Expected: localization coverage fails for the new keys.

- [ ] **Step 3: Add English and Russian text**

Use concise labels:

```properties
# English
craft_atlas.auto=Auto
craft_atlas.all_matching=All matching
craft_atlas.do_not_use=Do not use
craft_atlas.inventory=Inventory
craft_atlas.collect_count=Craft count
craft_atlas.collect_resources=Collect resources
craft_atlas.collect_shortage=Not enough {0}: missing {1}
craft_atlas.collect_unavailable=This recipe has no complete observed material data
craft_atlas.quality_unavailable=Quality unavailable
```

```properties
# Russian
craft_atlas.auto=Авто
craft_atlas.all_matching=Все подходящие
craft_atlas.do_not_use=Не использовать
craft_atlas.inventory=Инвентарь
craft_atlas.collect_count=Количество крафтов
craft_atlas.collect_resources=Собрать ресурсы
craft_atlas.collect_shortage=Не хватает {0}: {1} шт.
craft_atlas.collect_unavailable=Для рецепта нет полных данных о материалах
craft_atlas.quality_unavailable=Качество недоступно
```

- [ ] **Step 4: Add one integration test for the complete flow**

Build an observed recipe entry with a Fabric input, feed inventory Linen Q100 x2 and storage Linen Q92 x4 plus Hemp Q99, select Linen Q100, request two crafts of three units, and assert:

- the plan is complete;
- allocations are Linen Q100 x2 then Linen Q92 x4;
- Hemp is not allocated;
- automatic projected quality is `(100×2 + 92×4) / 6`;
- the collector requests only the Q92 warehouse group for four units.

- [ ] **Step 5: Run focused and full verification**

Run: `rtk ant test`

Expected: all tests pass with zero failures.

Run: `rtk ant jar`

Expected: build succeeds and updates `bin/hafen.jar`.

- [ ] **Step 6: Review the final diff and commit**

Run: `rtk git diff --check`

Expected: no whitespace errors.

Run: `rtk git status --short`

Verify only intended Craft Atlas files and the already-authorized `bin/hafen.jar` are included; leave unrelated user work untouched.

```bash
rtk git add src/lang/messages.properties src/lang/messages_ru.properties test/nurgling/widgets/craftatlas/CraftAtlasLocalizationTest.java test/nurgling/widgets/craftatlas/CraftAtlasIntegrationTest.java bin/hafen.jar
rtk git commit -m "feat: finish craft atlas resource planner"
```
