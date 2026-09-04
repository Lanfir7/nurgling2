# Craft Atlas Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a separate Craft Atlas window that searches the current character's known recipes, presents gilding data and typed requirements, follows clickable ingredient/station/tool recipe links inside the Atlas, and opens the selected recipe in the existing craft window.

**Architecture:** A pure immutable catalog model is populated from a synchronized `MenuGrid` snapshot and observations already received by `NMakewindow`. Pure search, graph, history, and controller classes sit between the model and a focused Haven widget tree. The Atlas never owns `NMakewindow`; its execution bridge resolves the current `Pagina` again and invokes the normal menu action.

**Tech Stack:** Java 8, Haven widget framework, existing `MenuGrid`/`NMakewindow` protocol data, `org.json`, Nurgling `L10n`, JUnit 5, Apache Ant.

**Spec:** `docs/superpowers/specs/2026-09-03-craft-atlas-design.md`

## Global Constraints

- This plan implements only phase 1 from the spec. Cookbook/FEP integration and Ring of Brodgar import require separate plans after this deliverable works.
- There are no Compare, Check materials, or Details actions and no stock indicators.
- `Open craft` opens the selected available recipe in the existing `NCraftWindow`; it does not start crafting and does not reparent `NMakewindow`.
- Consumable inputs and `STATION`, `TOOL`, `SKILL`, `DISCOVERY` requirements are different model fields and UI sections.
- Ingredient, station, and tool links navigate inside the Atlas; skills show description only.
- Missing data is unknown, never zero. Category inputs and alternatives keep their slot semantics.
- All persistent identifiers are resource names. Localized display names never authorize a server action.
- Runtime UI changes occur on the Haven UI thread. Search works on immutable snapshots and applies only to the matching catalog revision.
- No SQL, network I/O, `loadwait`, or complete-list texture construction is allowed from `draw`.
- All visible strings use `L10n` keys in both `src/lang/messages.properties` and `src/lang/messages_ru.properties`.
- Use `UI.scale` exactly once for each logical layout dimension.
- Preserve all unrelated working-tree changes. Stage only files named by the current task.

---

## File Map

New domain files under `src/nurgling/craftatlas/`:

- `CraftAtlasEntry.java` — immutable recipe, input slot, output, requirement, bonus, and availability types.
- `CraftAtlasSnapshot.java` — immutable revisioned list plus indexes by recipe and output resource.
- `CraftRecipeGraph.java` — resolves an input/output resource to all producing recipes without recursive traversal.
- `CraftAtlasHistory.java` — browser-like back/forward state for recipe cards.
- `CraftAtlasSearch.java` — normalized token search and filters over one snapshot.
- `CraftAtlasPreferences.java` — DB-independent favorites, recent recipes, window state, and column widths.
- `CraftAtlasObservationStore.java` — session-safe JSON persistence for observed recipe inputs, outputs, tools, and quality modifiers.
- `CraftRequirementClassifier.java` — deterministic resource-based station/tool classification for protocol requirements.
- `MenuCraftCatalog.java` — builds snapshot entries from current `MenuGrid.Pagina` objects plus observations.
- `CraftAtlasController.java` — selected recipe, result list, link choice, history, revision checks, and open-craft command.

New UI files under `src/nurgling/widgets/craftatlas/`:

- `CraftAtlasWindow.java` — window composition, responsive two/three-pane behavior, lifecycle.
- `CraftAtlasRecipeList.java` — virtualized visible recipe rows and selection.
- `CraftAtlasDetails.java` — clickable ingredients, requirements, breadcrumb/history, and the single Open craft action.
- `CraftAtlasRecipeChooser.java` — compact chooser when a resource has several producing recipes.

Existing files modified:

- `src/haven/MenuGrid.java` — synchronized recipe snapshot/lookup API; no catalog UI code.
- `src/nurgling/widgets/NMakewindow.java` — publish protocol observations after inputs/outputs/tools settle.
- `src/haven/GameUI.java` — own/toggle the Atlas and add its menu button/key binding.
- `src/lang/messages.properties`, `src/lang/messages_ru.properties` — Atlas copy.

Tests under `test/nurgling/craftatlas/` cover model, graph, history, search, observations, catalog, and controller. `test/nurgling/widgets/craftatlas/` covers deterministic layout and row hit targets without a live server.

---

### Task 1: Immutable recipe model and snapshot

**Files:**
- Create: `src/nurgling/craftatlas/CraftAtlasEntry.java`
- Create: `src/nurgling/craftatlas/CraftAtlasSnapshot.java`
- Test: `test/nurgling/craftatlas/CraftAtlasEntryTest.java`

**Interfaces:**
- Consumes: Java collections and resource-name strings only.
- Produces: `CraftAtlasEntry`, nested `InputSlot`, `IngredientOption`, `Requirement`, `Bonus`; `CraftAtlasSnapshot.of(long, Collection<CraftAtlasEntry>)`.

- [ ] **Step 1: Write failing model tests**

```java
class CraftAtlasEntryTest {
    @Test void keepsAlternativesInsideOneInputSlot() {
        CraftAtlasEntry.InputSlot slot = new CraftAtlasEntry.InputSlot(2, false, Arrays.asList(
            new CraftAtlasEntry.IngredientOption("gfx/invobjs/glue", "Glue"),
            new CraftAtlasEntry.IngredientOption("gfx/invobjs/fishglue", "Fish Glue")));
        assertEquals(2, slot.quantity);
        assertEquals(2, slot.options.size());
    }

    @Test void requirementsAreNotConsumableInputs() {
        CraftAtlasEntry e = CraftAtlasEntry.builder("paginae/craft/testaxe", "Test Axe")
            .input(new CraftAtlasEntry.InputSlot(1, false,
                Collections.singletonList(new CraftAtlasEntry.IngredientOption("gfx/invobjs/glue", "Glue"))))
            .requirement(new CraftAtlasEntry.Requirement(CraftAtlasEntry.RequirementKind.STATION,
                "gfx/terobjs/workbench", "Workbench", null))
            .build();
        assertEquals(1, e.inputs.size());
        assertEquals(CraftAtlasEntry.RequirementKind.STATION, e.requirements.get(0).kind);
    }
}
```

- [ ] **Step 2: Run the suite and confirm the new test fails to compile**

Run: `ant test`

Expected: FAIL because `CraftAtlasEntry` and `CraftAtlasSnapshot` do not exist.

- [ ] **Step 3: Implement immutable model types**

```java
public final class CraftAtlasEntry {
    public enum Availability { OPEN, UNAVAILABLE_NOW, CHECKING, REFERENCE_ONLY }
    public enum RequirementKind { STATION, TOOL, SKILL, DISCOVERY }
    public final String recipeResource, displayName, outputResource;
    public final Availability availability;
    public final List<InputSlot> inputs;
    public final List<Requirement> requirements;
    public final List<Bonus> bonuses;

    public static Builder builder(String recipeResource, String displayName) { return new Builder(recipeResource, displayName); }

    public static final class InputSlot {
        public final int quantity;
        public final boolean optional;
        public final List<IngredientOption> options;
        public InputSlot(int quantity, boolean optional, List<IngredientOption> options) { /* defensive copy */ }
    }
    public static final class IngredientOption {
        public final String resource, name;
        public IngredientOption(String resource, String name) { this.resource = resource; this.name = name; }
    }
    public static final class Requirement {
        public final RequirementKind kind;
        public final String resource, name, description;
        public Requirement(RequirementKind kind, String resource, String name, String description) { /* assign */ }
    }
    public static final class Bonus {
        public final String attributeResource, name;
        public final Double value;
        public Bonus(String attributeResource, String name, Double value) { /* assign; null means unknown */ }
    }
}
```

`CraftAtlasSnapshot` must defensively copy and expose unmodifiable collections. Reject null/empty recipe resources and duplicate recipe resources with `IllegalArgumentException`; do not silently overwrite.

- [ ] **Step 4: Run tests and commit the domain boundary**

Run: `ant test`

Expected: PASS, including the two new tests.

```bash
git add src/nurgling/craftatlas/CraftAtlasEntry.java src/nurgling/craftatlas/CraftAtlasSnapshot.java test/nurgling/craftatlas/CraftAtlasEntryTest.java
git commit -m "feat: add craft atlas recipe model"
```

---

### Task 2: Recipe graph and browser history

**Files:**
- Create: `src/nurgling/craftatlas/CraftRecipeGraph.java`
- Create: `src/nurgling/craftatlas/CraftAtlasHistory.java`
- Test: `test/nurgling/craftatlas/CraftRecipeGraphTest.java`
- Test: `test/nurgling/craftatlas/CraftAtlasHistoryTest.java`

**Interfaces:**
- Consumes: `CraftAtlasSnapshot` from Task 1.
- Produces: `CraftRecipeGraph.producers(String)`, `CraftRecipeGraph.linkState(String, List<String>)`; `CraftAtlasHistory.visit(CardState)`, `back()`, `forward()`.

- [ ] **Step 1: Write failing graph tests**

```java
@Test void returnsEveryRecipeThatProducesGlue() {
    CraftRecipeGraph graph = new CraftRecipeGraph(snapshot(glueRecipe("bone-glue"), glueRecipe("fish-glue")));
    assertEquals(Arrays.asList("bone-glue", "fish-glue"),
        graph.producers("gfx/invobjs/glue").stream()
            .map(e -> e.recipeResource).sorted().collect(Collectors.toList()));
}

@Test void reportsCycleWithoutRecursing() {
    CraftRecipeGraph graph = new CraftRecipeGraph(snapshot(recipe("a", "out-a", "out-b"), recipe("b", "out-b", "out-a")));
    assertEquals(CraftRecipeGraph.LinkState.CYCLE, graph.linkState("out-a", Arrays.asList("a", "b")));
}
```

- [ ] **Step 2: Write failing history tests**

```java
@Test void visitingAfterBackDropsForwardBranch() {
    CraftAtlasHistory h = new CraftAtlasHistory();
    h.visit(new CardState("axe", 18, Collections.emptySet()));
    h.visit(new CardState("glue", 42, Collections.singleton("requirements")));
    assertEquals("axe", h.back().recipeResource);
    h.visit(new CardState("workbench", 0, Collections.emptySet()));
    assertFalse(h.canForward());
}
```

- [ ] **Step 3: Run tests and confirm missing-class failures**

Run: `ant test`

Expected: FAIL for missing graph/history classes.

- [ ] **Step 4: Implement non-recursive indexes and bounded history**

```java
public final class CraftRecipeGraph {
    public enum LinkState { NONE, SINGLE, MULTIPLE, CYCLE }
    private final Map<String, List<CraftAtlasEntry>> byOutput;
    public List<CraftAtlasEntry> producers(String outputResource) { return byOutput.getOrDefault(outputResource, Collections.emptyList()); }
    public LinkState linkState(String outputResource, List<String> activeRecipePath) { /* membership + producer count only */ }
}

public final class CraftAtlasHistory {
    public static final int LIMIT = 64;
    public static final class CardState {
        public final String recipeResource;
        public final int scroll;
        public final Set<String> expandedGroups;
        /* constructor with defensive copy */
    }
    public void visit(CardState state) { /* truncate forward; append; cap from front */ }
    public CardState back() { /* return current state after decrement */ }
    public CardState forward() { /* return current state after increment */ }
}
```

- [ ] **Step 5: Run tests and commit navigation primitives**

Run: `ant test`

Expected: PASS with producer multiplicity, cycle, forward-branch, scroll restoration, and 64-entry bound covered.

```bash
git add src/nurgling/craftatlas/CraftRecipeGraph.java src/nurgling/craftatlas/CraftAtlasHistory.java test/nurgling/craftatlas/CraftRecipeGraphTest.java test/nurgling/craftatlas/CraftAtlasHistoryTest.java
git commit -m "feat: add craft recipe graph navigation"
```

---

### Task 3: Search and profile preferences

**Files:**
- Create: `src/nurgling/craftatlas/CraftAtlasSearch.java`
- Create: `src/nurgling/craftatlas/CraftAtlasPreferences.java`
- Test: `test/nurgling/craftatlas/CraftAtlasSearchTest.java`
- Test: `test/nurgling/craftatlas/CraftAtlasPreferencesTest.java`

**Interfaces:**
- Consumes: immutable entries/snapshots.
- Produces: `CraftAtlasSearch.query(snapshot, Query)` and DB-independent preferences keyed by `Config.userpath()` plus recipe resource.

- [ ] **Step 1: Write failing search tests for name, bonus, ingredient, AND tokens, `ё/е`, and unknown-last numeric sort**

```java
@Test void matchesAcrossBonusAndIngredientWithAndSemantics() {
    Query q = Query.text("выживание клей");
    assertEquals(Collections.singletonList("test-axe"), resources(CraftAtlasSearch.query(snapshot, q)));
}

@Test void unknownBonusSortsAfterKnownValues() {
    Query q = Query.builder().bonus("gfx/hud/chr/survive").descending(true).build();
    assertEquals(Arrays.asList("plus-three", "plus-one", "unknown"), resources(CraftAtlasSearch.query(snapshot, q)));
}
```

- [ ] **Step 2: Write failing preference round-trip test using a temporary file**

Persist only favorites, recent recipe resources (maximum 50), last section, window size/position, and per-section column widths. A corrupt file must return defaults and preserve the bad file for diagnosis.

- [ ] **Step 3: Run tests and confirm failures**

Run: `ant test`

Expected: FAIL for missing search/preference classes.

- [ ] **Step 4: Implement normalized indexing and atomic JSON preferences**

```java
static String normalize(String s) {
    return java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT).replace('ё', 'е').trim().replaceAll("\\s+", " ");
}
```

Use `NFileUtils.writeAtomically` for preferences. Do not use `FavoriteRecipeService`: Atlas favorites are resource IDs and must work when the database is disabled.

- [ ] **Step 5: Run tests and commit search/preferences**

Run: `ant test`

Expected: PASS.

```bash
git add src/nurgling/craftatlas/CraftAtlasSearch.java src/nurgling/craftatlas/CraftAtlasPreferences.java test/nurgling/craftatlas/CraftAtlasSearchTest.java test/nurgling/craftatlas/CraftAtlasPreferencesTest.java
git commit -m "feat: add craft atlas search and preferences"
```

---

### Task 4: Observe recipes and build the live catalog

**Files:**
- Create: `src/nurgling/craftatlas/CraftAtlasObservation.java`
- Create: `src/nurgling/craftatlas/CraftAtlasObservationStore.java`
- Create: `src/nurgling/craftatlas/CraftRequirementClassifier.java`
- Create: `src/nurgling/craftatlas/MenuCraftCatalog.java`
- Modify: `src/haven/MenuGrid.java`
- Modify: `src/nurgling/widgets/NMakewindow.java`
- Test: `test/nurgling/craftatlas/CraftAtlasObservationStoreTest.java`
- Test: `test/nurgling/craftatlas/CraftRequirementClassifierTest.java`
- Test: `test/nurgling/craftatlas/MenuCraftCatalogTest.java`

**Interfaces:**
- Consumes: `MenuGrid.recipeSnapshot()`, `Pagina.button().info()`, `NMakewindow` input/output/tool messages, existing `RecipeIngredientCache` data.
- Produces: `MenuGrid.recipeSnapshot()`, `MenuGrid.recipeByResource(String)`, `CraftAtlasObservationStore.record(Observation)`, `MenuCraftCatalog.rebuild()`.

- [ ] **Step 1: Write failing observation tests**

Cover tool resources, input quantities, output resources, merge-by-resource, corrupt JSON, and session/profile isolation. Assert a requirement never appears in `inputs`.

Also cover classification: `gfx/terobjs/...` is `STATION`, `gfx/invobjs/...` is `TOOL`, and an unknown prefix remains an explicitly unknown tool requirement rather than being dropped. The classifier accepts a future trusted source override so wiki data can correct a resource without changing graph code.

- [ ] **Step 2: Write failing catalog tests with fake page records**

```java
@Test void observationAddsToolRequirementAndKeepsRecipeOpen() {
    Observation observed = observation("paginae/craft/testaxe", "gfx/invobjs/glue", "gfx/terobjs/workbench");
    CraftAtlasSnapshot snap = MenuCraftCatalog.fromRecords(7, pages("paginae/craft/testaxe"), mapOf(observed));
    CraftAtlasEntry e = snap.byRecipe("paginae/craft/testaxe");
    assertEquals(CraftAtlasEntry.Availability.OPEN, e.availability);
    assertEquals(CraftAtlasEntry.RequirementKind.TOOL, e.requirements.get(0).kind);
}
```

- [ ] **Step 3: Run tests and confirm failures**

Run: `ant test`

Expected: FAIL for missing observation/catalog APIs.

- [ ] **Step 4: Add synchronized read APIs to `MenuGrid`**

```java
public List<Pagina> recipeSnapshot() {
    synchronized(paginae) {
        return paginae.stream().filter(this::isCraftAction).collect(Collectors.toList());
    }
}

public Pagina recipeByResource(String resource) {
    synchronized(paginae) {
        for(Pagina p : paginae) if(resource.equals(p.res().name)) return p;
    }
    return null;
}
```

Implement `isCraftAction` by walking `Pagina.parent()` to the `paginae/act/craft` root, with `Loading` returning false for this snapshot only. Do not identify craft actions by localized names.

- [ ] **Step 5: Capture a complete `NMakewindow` observation**

Add `maybePublishCraftAtlasObservation()` after `inpop`, `opop`, `qmod`, and `tool` messages and from `tick` until published. Publish only when `recipeResource`, names, inputs, and outputs are resolved. Derive requirement resources from `tools`; classify `gfx/terobjs/...` as `STATION`, `gfx/invobjs/...` as `TOOL`, and preserve unknown prefixes with unknown text instead of guessing or dropping them. A later trusted source may override the classification.

```java
CraftAtlasObservationStore.current().record(CraftAtlasObservation.from(
    recipeResource, rcpnm, inputs, outputs, tools, qmod));
```

Do not call resource `loadwait`; if a name/resource is still `Loading`, retry on a later tick.

- [ ] **Step 6: Build catalog snapshots and merge precedence**

Precedence per field: live `Pagina.info()` > current observation > `RecipeIngredientCache` > unknown. Availability comes only from the current `MenuGrid` snapshot. Keep observation-only producers as `UNAVAILABLE_NOW` so ingredient links work without authorizing Open craft.

- [ ] **Step 7: Run tests and commit observation/catalog integration**

Run: `ant test`

Expected: PASS, including existing `RecipeIngredientCacheTest` and `NMakewindowCraftQualityTest`.

```bash
git add src/haven/MenuGrid.java src/nurgling/widgets/NMakewindow.java src/nurgling/craftatlas/CraftAtlasObservation.java src/nurgling/craftatlas/CraftAtlasObservationStore.java src/nurgling/craftatlas/CraftRequirementClassifier.java src/nurgling/craftatlas/MenuCraftCatalog.java test/nurgling/craftatlas/CraftAtlasObservationStoreTest.java test/nurgling/craftatlas/CraftRequirementClassifierTest.java test/nurgling/craftatlas/MenuCraftCatalogTest.java
git commit -m "feat: build craft atlas catalog from live recipes"
```

---

### Task 5: Controller, internal links, and safe Open craft

**Files:**
- Create: `src/nurgling/craftatlas/CraftAtlasController.java`
- Create: `src/nurgling/craftatlas/CraftExecutionBridge.java`
- Test: `test/nurgling/craftatlas/CraftAtlasControllerTest.java`
- Test: `test/nurgling/craftatlas/CraftExecutionBridgeTest.java`

**Interfaces:**
- Consumes: snapshot/search/graph/history and a current `MenuGrid` supplier.
- Produces: listener-ready `ViewState`, `openIngredient(resource)`, `openRequirement(requirement)`, `back()`, `forward()`, `openCraft()`.

- [ ] **Step 1: Write failing controller tests for one/many/no producers**

```java
@Test void oneProducerNavigatesAndManyProducersRequestsChoice() {
    controller.openIngredient("glue-one");
    assertEquals("glue-recipe", controller.state().selected.recipeResource);
    controller.openIngredient("glue-many");
    assertEquals(2, controller.state().choices.size());
}

@Test void searchResultsRemainStableWhileFollowingIngredient() {
    List<String> before = ids(controller.state().results);
    controller.openIngredient("gfx/invobjs/glue");
    assertEquals(before, ids(controller.state().results));
}
```

- [ ] **Step 2: Write failing execution tests using a fake resolver/use callback**

Test OPEN invokes exactly once, missing/replaced pagina refuses, `REFERENCE_ONLY` refuses, double click while pending invokes once, and completion clears the pending guard.

- [ ] **Step 3: Run tests and confirm failures**

Run: `ant test`

Expected: FAIL for missing controller/bridge.

- [ ] **Step 4: Implement controller view state and requirement routing**

```java
public void openRequirement(CraftAtlasEntry.Requirement r) {
    if(r.kind == RequirementKind.SKILL || r.kind == RequirementKind.DISCOVERY) {
        state = state.withRequirementDescription(r);
    } else if(r.resource != null) {
        openIngredient(r.resource);
    }
}
```

Store the active recipe path separately from history to report cycles. A cycle sets `cycleResource` in `ViewState`; it never auto-calls `openIngredient` again.

- [ ] **Step 5: Implement bridge against current `Pagina` identity**

```java
public boolean open(String recipeResource) {
    MenuGrid.Pagina page = menu.recipeByResource(recipeResource);
    if(page == null || pending) return false;
    pending = true;
    page.button().use(new MenuGrid.Interaction());
    return true;
}
```

Clear `pending` when `NGameUI.addchild(..., "craft")` attaches the resulting `NMakewindow`, or after a two-second UI-time timeout so a rejected server action does not lock the button forever. Re-resolve on every click; never retain a stale `Pagina` for execution.

- [ ] **Step 6: Run tests and commit controller/bridge**

Run: `ant test`

Expected: PASS.

```bash
git add src/nurgling/craftatlas/CraftAtlasController.java src/nurgling/craftatlas/CraftExecutionBridge.java test/nurgling/craftatlas/CraftAtlasControllerTest.java test/nurgling/craftatlas/CraftExecutionBridgeTest.java
git commit -m "feat: navigate craft dependencies and open recipes"
```

---

### Task 6: Atlas widgets and responsive layout

**Files:**
- Create: `src/nurgling/widgets/craftatlas/CraftAtlasLayout.java`
- Create: `src/nurgling/widgets/craftatlas/CraftAtlasRecipeList.java`
- Create: `src/nurgling/widgets/craftatlas/CraftAtlasDetails.java`
- Create: `src/nurgling/widgets/craftatlas/CraftAtlasRecipeChooser.java`
- Create: `src/nurgling/widgets/craftatlas/CraftAtlasWindow.java`
- Test: `test/nurgling/widgets/craftatlas/CraftAtlasLayoutTest.java`
- Test: `test/nurgling/widgets/craftatlas/CraftAtlasDetailsTest.java`

**Interfaces:**
- Consumes: `CraftAtlasController.ViewState` and controller actions.
- Produces: one `Window` with search/sidebar/list/details and deterministic row hit targets.

- [ ] **Step 1: Write failing deterministic layout tests**

Cover 1160×700 three-pane mode, 800×600 details-as-page mode, 125%/150% scaled dimensions, last-row reachability, and fixed header/search bounds.

- [ ] **Step 2: Write failing details interaction tests**

Use a pure `DetailRow`/hit-map builder. Assert ingredient links get hit targets only when graph state is SINGLE/MULTIPLE/CYCLE, station/tool requirements use the same link target, skill requirements use description targets, and ordinary rows are not clickable.

- [ ] **Step 3: Run tests and confirm failures**

Run: `ant test`

Expected: FAIL for missing layout/details classes.

- [ ] **Step 4: Implement virtualized recipe rows**

`CraftAtlasRecipeList` must render only the visible range computed from scroll value and row height. Cache icon/name textures per visible entry and dispose them when the snapshot revision changes or widget is destroyed. The list emits selection only; it never calls server actions.

- [ ] **Step 5: Implement details, breadcrumbs, chooser, and the single action**

Order: title/status → bonuses → inputs → requirements → explanatory source/status → one Open craft button. Ingredient/requirement rows include an arrow only when clickable. Multiple producers open `CraftAtlasRecipeChooser`; the chooser is bounded by the window and keyboard-accessible. Remove/omit all compare, materials, and details controls.

- [ ] **Step 6: Compose `CraftAtlasWindow` and lifecycle**

Subscribe once to controller state in `added`; unsubscribe/dispose textures in `destroy`. `Ctrl+F` focuses search. Escape closes chooser first, returns from narrow details page second, then hides the Atlas. Back/forward buttons call controller history without changing search text or list scroll.

- [ ] **Step 7: Run tests and commit widgets**

Run: `ant test`

Expected: PASS.

```bash
git add src/nurgling/widgets/craftatlas test/nurgling/widgets/craftatlas
git commit -m "feat: add craft atlas window"
```

---

### Task 7: GameUI integration and localization

**Files:**
- Modify: `src/haven/GameUI.java`
- Modify: `src/nurgling/NGameUI.java`
- Modify: `src/lang/messages.properties`
- Modify: `src/lang/messages_ru.properties`
- Test: `test/nurgling/widgets/craftatlas/CraftAtlasIntegrationTest.java`
- Test: `test/nurgling/widgets/craftatlas/CraftAtlasL10nTest.java`

**Interfaces:**
- Consumes: `CraftAtlasWindow`, `NGameUI.addchild("craft")`, existing `MainMenu` conventions.
- Produces: `GameUI.craftAtlas`, `kb_craftAtlas`, main-menu toggle, bridge completion notification.

- [ ] **Step 1: Write failing source/integration tests**

Assert `GameUI.initHeavyWidgets()` creates/hides one Atlas, MainMenu toggle references the same field, key binding name is `craft-atlas`, and `NGameUI.addchild` notifies the bridge after a craft child is attached. L10n test loads every `craft_atlas.*` key in default and Russian bundles and rejects fallback-to-key output.

- [ ] **Step 2: Run tests and confirm failures**

Run: `ant test`

Expected: FAIL because integration and keys do not exist.

- [ ] **Step 3: Add ownership, menu button, and key binding**

```java
public CraftAtlasWindow craftAtlas;
public static final KeyBinding kb_craftAtlas = KeyBinding.get("craft-atlas", KeyMatch.nil);
```

Create/hide the window in `initHeavyWidgets`. Add a second-row `MenuCheckBox` using existing `wndstate`/`togglewnd` conventions. Use a new resource path only if an actual three-state icon asset is added in the same task; otherwise reuse the encyclopedia icon and document that temporary choice in the commit.

- [ ] **Step 4: Add exact English and Russian strings**

Include keys for title, sections, statuses, search placeholder, inputs, requirements, station/tool/skill/discovery, back/forward, recipe choices, cycle message, no recipe, normal craft-window hint, and Open craft. Do not add Compare, Check materials, or Details keys.

- [ ] **Step 5: Notify bridge without changing craft ownership**

After existing `craftwnd.add(child)` completes, call `craftAtlas.onCraftWindowOpened()` if the Atlas exists. Do not hide, destroy, or reparent either window.

- [ ] **Step 6: Run tests and commit integration**

Run: `ant test`

Expected: PASS.

```bash
git add src/haven/GameUI.java src/nurgling/NGameUI.java src/lang/messages.properties src/lang/messages_ru.properties test/nurgling/widgets/craftatlas/CraftAtlasIntegrationTest.java test/nurgling/widgets/craftatlas/CraftAtlasL10nTest.java
git commit -m "feat: integrate craft atlas into game ui"
```

---

### Task 8: Full verification and manual acceptance

**Files:**
- Modify only files required by failures found below.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: verified phase-1 deliverable and a clean review diff limited to Craft Atlas work.

- [ ] **Step 1: Run the complete automated suite from a fresh compilation**

Run: `ant clean test`

Expected: BUILD SUCCESSFUL and zero failed tests. If dependency download fails, preserve the exact error; do not report tests as passing.

- [ ] **Step 2: Build the distributable client**

Run: `ant bin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Inspect structural scope**

Run: `git diff --check` and `git status --short`.

Expected: no whitespace errors; unrelated pre-existing files may remain modified but are not staged by this feature.

- [ ] **Step 4: Perform manual in-game acceptance at 100%, 125%, and 150% UI scale**

Verify all of the following and record actual observations in the implementation turn:

1. Atlas opens from its menu button and key binding and remains inside 1280×720 and 1920×1080.
2. Search by recipe, bonus, and ingredient updates without freezing.
3. A recipe ingredient with one producer opens that recipe; multiple producers show a chooser; Back restores the prior card scroll and leaves search results unchanged.
4. A cyclic fixture/data path shows the cycle label and remains responsive.
5. Tools/stations are in Requirements, never in consumed Inputs; skill click shows description.
6. A reference/unavailable recipe has disabled Open craft. An available recipe opens the normal `NCraftWindow` and does not craft automatically.
7. Closing Atlas leaves the normal craft window open. Existing crafting bots still see `craftwnd.makeWidget`.
8. No Compare, Check materials, Details, stock status, or embedded craft controls are visible.

- [ ] **Step 5: Commit only verification-driven fixes**

```bash
git add <exact Craft Atlas files changed by verification>
git commit -m "fix: finish craft atlas phase one verification"
```

Skip this commit if verification required no changes.
