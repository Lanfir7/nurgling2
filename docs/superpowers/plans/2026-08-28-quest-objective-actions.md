# Quest Objective Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add quest-scoped tree map icons plus forage, rock-highlight, and craft-opening buttons to objective rows in both quest interfaces.

**Architecture:** Parse each server condition once into `QCond`, resolve it through a pure shared catalog, and keep UI dispatch separate from resource classification. A per-UI claim controller applies session-local effective-visibility overrides to exact vanilla `GobIcon.Setting.ID` values without mutating the saved `show` preference. Both quest views render the same immutable action specification.

**Tech Stack:** Java 17, Haven widget framework, vanilla `GobIcon.Settings` and `MenuGrid`, existing `Forageables`, `VSpec`, `RockResourceMapper`, `TileHighlight`, Ant, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-28-quest-objective-actions-design.md`

## Global Constraints

- The feature applies only to active quest objectives.
- Craft actions open the learned recipe and never start crafting.
- Automatic icon transitions are session-local and are not persisted with `GobIcon.Settings.save()`.
- A manually enabled icon remains enabled after quest completion.
- Ready objectives retain tree claims until the whole quest leaves the current list.
- Server-provided custom full-journal condition widgets remain unchanged.
- Unknown or ambiguous matches render without an action.

---

### Task 1: Normalize item targets in quest conditions

**Files:**
- Modify: `src/nurgling/widgets/quest/QCond.java`
- Create: `test/nurgling/widgets/quest/QCondTest.java`

**Interfaces:**
- Produces: `public final String itemTarget`, containing the normalized display item for `PICK`, `BRING`, and `CREATE`, or `null`.
- Preserves: `bringItem` and `gobTarget` compatibility for existing overlays.

- [ ] **Step 1: Write failing parser tests**

```java
@Test void bringExposesDisplayItem() {
    QCond cond = new QCond(7, false, "Bring a Board of Oak to Jenny", null);
    assertEquals(QCond.Verb.BRING, cond.verb);
    assertEquals("board of oak", cond.itemTarget);
}

@Test void pickExposesDisplayItemWithoutChangingGobTarget() {
    QCond cond = new QCond(7, false, "Pick a Chiming Bluebell", null);
    assertEquals("chiming bluebell", cond.itemTarget);
    assertNotNull(cond.gobTarget);
}

@Test void createExposesDisplayItem() {
    assertEquals("stone axe", new QCond(7, false, "Create a Stone Axe", null).itemTarget);
}

@Test void malformedObjectivesHaveNoItemTarget() {
    assertNull(new QCond(7, false, "Create", null).itemTarget);
    assertNull(new QCond(7, false, "Bring to Jenny", null).itemTarget);
}
```

- [ ] **Step 2: Run the parser test and verify RED**

Run: `ant test`

Expected: compilation fails because `itemTarget` does not exist.

- [ ] **Step 3: Implement minimal safe target parsing**

Add `itemTarget` and article/recipient-aware parsing. `BRING` uses the existing `bringItem`; `PICK` keeps `gobTarget` but extracts the display tail; `CREATE` extracts the display tail. Normalize whitespace, apostrophes, status suffix exclusion, and lowercase with `Locale.ROOT`.

- [ ] **Step 4: Run the parser test and verify GREEN**

Run: `ant test`. Expected: all tests, including `QCondTest`, pass.

- [ ] **Step 5: Commit**

```bash
git add src/nurgling/widgets/quest/QCond.java test/nurgling/widgets/quest/QCondTest.java
git commit -m "feat: parse quest objective item targets"
```

### Task 2: Resolve objective resources without UI dependencies

**Files:**
- Create: `src/nurgling/widgets/quest/QuestObjectiveAction.java`
- Create: `src/nurgling/widgets/quest/QuestObjectiveActionResolver.java`
- Modify: `src/nurgling/tools/VSpec.java`
- Modify: `src/nurgling/tools/RockResourceMapper.java`
- Create: `test/nurgling/widgets/quest/QuestObjectiveActionResolverTest.java`
- Create: `test/nurgling/tools/VSpecTreeProductsTest.java`
- Create: `test/nurgling/tools/RockResourceMapperTest.java`

**Interfaces:**
- Produces: `QuestObjectiveAction.Kind { FORAGE_TERRAIN, ROCK_TERRAIN, CRAFT }` and immutable `targets`.
- Produces: `QuestObjectiveActionResolver.resolve(QCond)` for visible row actions.
- Produces: `QuestObjectiveActionResolver.treeResources(QCond)` for icon claims, even when `cond.ready` is true.
- Produces: `VSpec.treeResourcesForProduct(String)` and `RockResourceMapper.getTileResourcesForItem(String)`.

- [ ] **Step 1: Write failing resource-catalog tests**

```java
@Test void fruitAndLogProductsResolveToLivingTree() {
    assertTrue(VSpec.treeResourcesForProduct("Acacia Pod").contains("gfx/terobjs/trees/acacia"));
    assertTrue(VSpec.treeResourcesForProduct("Board of Oak").contains("gfx/terobjs/trees/oak"));
    assertTrue(VSpec.treeResourcesForProduct("Block of Oak").contains("gfx/terobjs/trees/oak"));
}

@Test void quartzResolvesToExactMineTile() {
    assertEquals(Collections.singleton("gfx/tiles/rocks/quartz"),
                 RockResourceMapper.getTileResourcesForItem("Quartz"));
}
```

- [ ] **Step 2: Run catalog tests and verify RED**

Run: `ant test`.

Expected: compilation fails because both reverse lookup methods are absent.

- [ ] **Step 3: Implement exact reverse indexes**

Build lazy immutable normalized indexes. For `VSpec.object`, accept living-tree keys directly and translate a `-log` key to the matching living-tree key only when that tree exists. For rocks, reverse the mapper's existing gob/tile associations and match exact normalized display/resource leaves.

- [ ] **Step 4: Run catalog tests and verify GREEN**

Expected: both catalog test classes pass.

- [ ] **Step 5: Write failing resolver tests**

```java
@Test void forageObjectiveReturnsCanonicalBiomes() {
    QuestObjectiveAction action = resolver.resolve(new QCond(1, false, "Pick a Chiming Bluebell", null));
    assertEquals(QuestObjectiveAction.Kind.FORAGE_TERRAIN, action.kind);
    assertFalse(action.targets.isEmpty());
}

@Test void rockObjectiveReturnsExactTile() {
    QuestObjectiveAction action = resolver.resolve(new QCond(1, false, "Bring a Quartz to Jenny", null));
    assertEquals(Collections.singletonList("gfx/tiles/rocks/quartz"), action.targets);
}

@Test void createObjectiveRequestsCraftLookup() {
    QuestObjectiveAction action = resolver.resolve(new QCond(1, false, "Create a Stone Axe", null));
    assertEquals(QuestObjectiveAction.Kind.CRAFT, action.kind);
    assertEquals(Collections.singletonList("stone axe"), action.targets);
}

@Test void readyAndUnknownObjectivesHaveNoButtonAction() {
    assertNull(resolver.resolve(new QCond(1, true, "Pick a Chiming Bluebell", null)));
    assertNull(resolver.resolve(new QCond(1, false, "Admire the sunset", null)));
}
```

- [ ] **Step 6: Run resolver tests and verify RED**

Expected: compilation fails because the action classes do not exist.

- [ ] **Step 7: Implement the immutable action model and resolver**

Exact-match forage names from `Forageables.all()`, then exact-match rocks. `CREATE` returns a normalized recipe name for runtime pagina resolution. Tree lookup is independent of row action lookup and does not reject ready objectives.

- [ ] **Step 8: Run all Task 2 tests and verify GREEN**

Expected: all three classes pass.

- [ ] **Step 9: Commit**

```bash
git add src/nurgling/widgets/quest/QuestObjectiveAction.java src/nurgling/widgets/quest/QuestObjectiveActionResolver.java src/nurgling/tools/VSpec.java src/nurgling/tools/RockResourceMapper.java test/nurgling/widgets/quest/QuestObjectiveActionResolverTest.java test/nurgling/tools/VSpecTreeProductsTest.java test/nurgling/tools/RockResourceMapperTest.java
git commit -m "feat: resolve quest objective actions"
```

### Task 3: Manage temporary vanilla icon-setting claims

**Files:**
- Create: `src/nurgling/widgets/quest/QuestTreeIconClaims.java`
- Create: `src/nurgling/widgets/quest/QuestTreeIconController.java`
- Modify: `src/nurgling/widgets/quest/QuestModel.java`
- Modify: `src/nurgling/widgets/NQuestInfo.java`
- Create: `test/nurgling/widgets/quest/QuestTreeIconClaimsTest.java`

**Interfaces:**
- Produces: `QuestTreeIconClaims<K>.reconcile(...)` as generic, reference-counted override state logic.
- Produces: `QuestTreeIconController.reconcile(Collection<QuestModel.TQuest>, GobIcon.Settings)` and `restore(GobIcon.Settings)`.

- [ ] **Step 1: Write failing claim-state tests**

```java
@Test void restoresOnlyIconsInitiallyDisabled() {
    QuestTreeIconClaims claims = new QuestTreeIconClaims();
    Map<String, Boolean> current = states("oak", false, "apple", true);
    current = claims.reconcile(requirements(1, "oak", "apple"), current);
    assertEquals(states("oak", true, "apple", true), current);
    current = claims.reconcile(Collections.emptyMap(), current);
    assertEquals(states("oak", false, "apple", true), current);
}

@Test void sharedIconWaitsForLastQuest() {
    QuestTreeIconClaims claims = new QuestTreeIconClaims();
    Map<String, Boolean> current = claims.reconcile(requirements(1, "oak", 2, "oak"), states("oak", false));
    assertTrue(current.get("oak"));
    assertTrue(claims.reconcile(requirements(2, "oak"), current).get("oak"));
    assertFalse(claims.reconcile(Collections.emptyMap(), current).get("oak"));
}
```

- [ ] **Step 2: Run claim tests and verify RED**

Expected: compilation fails because `QuestTreeIconClaims` does not exist.

- [ ] **Step 3: Implement reference-counted pure claim state**

Track quest-to-setting-id requirements and add/remove transient overrides as the shared claims change. Do not treat objective readiness as a release signal.

- [ ] **Step 4: Run claim tests and verify GREEN**

Expected: all claim tests pass.

- [ ] **Step 5: Implement the GobIcon adapter and tracker integration**

`QuestTreeIconController` maps tree resources to every loaded full `GobIcon.Setting.ID` by exact resource name and applies transient effective-visibility overrides. `GobIcon.Settings.save()` continues to serialize only `Setting.show`. `NQuestInfo.tick` reconciles after `QuestModel.tick`; removal from `QuestModel.quests()` releases claims. Destruction/session replacement removes outstanding overrides.

- [ ] **Step 6: Compile and run quest tests**

Run: `ant test`.

Expected: compilation and tests succeed.

- [ ] **Step 7: Commit**

```bash
git add src/nurgling/widgets/quest/QuestTreeIconClaims.java src/nurgling/widgets/quest/QuestTreeIconController.java src/nurgling/widgets/quest/QuestModel.java src/nurgling/widgets/NQuestInfo.java test/nurgling/widgets/quest/QuestTreeIconClaimsTest.java
git commit -m "feat: claim tree icons for active quests"
```

### Task 4: Dispatch terrain and crafting actions

**Files:**
- Create: `src/nurgling/widgets/quest/QuestObjectiveActions.java`
- Modify: `src/nurgling/widgets/MapToolsWindow.java`
- Modify: `src/nurgling/widgets/TerrainSearchPanel.java`
- Create: `test/nurgling/widgets/quest/QuestObjectiveActionsTest.java`
- Modify: `test/nurgling/widgets/TerrainSearchPanelTest.java`

**Interfaces:**
- Produces: `MapToolsWindow.openTerrainResources(Collection<String>)`.
- Produces: `TerrainSearchPanel.selectResources(Collection<String>)`.
- Produces: `QuestObjectiveActions.findCraftPagina(MenuGrid, String)` and `execute(NGameUI, QuestObjectiveAction)`.

- [ ] **Step 1: Write failing exact-highlight and craft lookup tests**

```java
@Test void exactRockResourcesBypassFuzzyTerrainExpansion() {
    assertEquals(Collections.singleton("gfx/tiles/rocks/quartz"),
                 TerrainSearchPanel.normalizedResources(Collections.singleton("gfx/tiles/rocks/quartz")));
}

@Test void craftLookupRejectsZeroOrMultipleExactNames() {
    assertNull(QuestObjectiveActions.uniqueCraftCandidate("stone axe", Collections.emptyList()));
    assertNull(QuestObjectiveActions.uniqueCraftCandidate("stone axe", Arrays.asList("Stone Axe", "Stone Axe")));
    assertEquals("Stone Axe", QuestObjectiveActions.uniqueCraftCandidate("stone axe", Collections.singletonList("Stone Axe")));
}
```

- [ ] **Step 2: Run tests and verify RED**

Expected: compilation fails because the exact-resource and craft-action APIs are absent.

- [ ] **Step 3: Implement exact resource selection and learned pagina dispatch**

`selectResources` replaces `TileHighlight` with exact non-empty resource names and refreshes the minimap. Craft lookup snapshots `menu.paginae`, resolves loaded `PagButton.act().name` values without blocking, requires exactly one normalized match, then calls `menu.use(button, new MenuGrid.Interaction(), false)`.

- [ ] **Step 4: Run Task 4 tests and verify GREEN**

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/nurgling/widgets/quest/QuestObjectiveActions.java src/nurgling/widgets/MapToolsWindow.java src/nurgling/widgets/TerrainSearchPanel.java test/nurgling/widgets/quest/QuestObjectiveActionsTest.java test/nurgling/widgets/TerrainSearchPanelTest.java
git commit -m "feat: dispatch quest resource shortcuts"
```

### Task 5: Render action buttons in both quest views

**Files:**
- Create: `src/nurgling/widgets/quest/QuestObjectiveActionButton.java`
- Create: `src/nurgling/widgets/quest/QuestObjectiveRowLayout.java`
- Modify: `src/nurgling/widgets/NQuestInfo.java`
- Modify: `src/nurgling/NQuestBox.java`
- Create: `test/nurgling/widgets/quest/QuestObjectiveRowLayoutTest.java`

**Interfaces:**
- Produces: `QuestObjectiveRowLayout.textWidth(int rowWidth, int textOffset, boolean hasAction)`.
- Produces: a button widget that renders a map or craft glyph, exposes a tooltip, and consumes only clicks inside itself.

- [ ] **Step 1: Write failing row-layout tests**

```java
@Test void actionButtonReservesRightEdgeWithoutNegativeTextWidth() {
    assertEquals(180, QuestObjectiveRowLayout.textWidth(220, 20, true));
    assertEquals(200, QuestObjectiveRowLayout.textWidth(220, 20, false));
    assertEquals(0, QuestObjectiveRowLayout.textWidth(10, 20, true));
}
```

- [ ] **Step 2: Run the layout test and verify RED**

Expected: compilation fails because `QuestObjectiveRowLayout` does not exist.

- [ ] **Step 3: Implement the row button and compact tracker integration**

Carry each row's `QCond` or resolved action alongside its display text. Elide against the reserved button area. Add the button as a `CondRow` child; its event consumes the click, while the remaining row continues to call `openQuest`.

- [ ] **Step 4: Implement full-journal condition rows in `NQuestBox`**

Override `layoutc`. Reuse vanilla condition widgets whenever `wdata != null`; otherwise create an action-capable ordinary row from `new QCond(id, cond.done != 0, cond.desc, cond.status)`. Preserve condition widget identity/update reuse, text color, status, wrapping, and vertical sizing.

- [ ] **Step 5: Run targeted tests and compile**

Run: `ant test`.

Expected: all targeted tests pass and both modified widget classes compile.

- [ ] **Step 6: Commit**

```bash
git add src/nurgling/widgets/quest/QuestObjectiveActionButton.java src/nurgling/widgets/quest/QuestObjectiveRowLayout.java src/nurgling/widgets/NQuestInfo.java src/nurgling/NQuestBox.java test/nurgling/widgets/quest/QuestObjectiveRowLayoutTest.java
git commit -m "feat: add quest objective action buttons"
```

### Task 6: Full verification

**Files:**
- Verify all files changed by Tasks 1-5.

**Interfaces:**
- Consumes: the complete feature.
- Produces: fresh test and build evidence.

- [ ] **Step 1: Run all tests**

Run: `ant test`

Expected: JUnit reports zero failures.

- [ ] **Step 2: Run the production build**

Run: `ant jar`

Expected: Ant exits successfully and produces the client jar.

- [ ] **Step 3: Inspect the final diff**

Run: `git diff --check 15a8a94..HEAD` and `git status --short`.

Expected: no whitespace errors; only the pre-existing user changes and the feature's intended files are present.

- [ ] **Step 4: Perform requirement checklist review**

Confirm from code and tests: both UIs have buttons; forage and rock use exact existing highlights; crafting opens one learned recipe only; tree icons are automatic, reference-counted, non-persistent, and manually enabled states survive; unsupported tasks remain unchanged.
