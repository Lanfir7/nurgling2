# Unified Hotkeys Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one categorized settings page that can rebind every gameplay keyboard, mouse-button, mouse-wheel, and modifier-mode action while preserving existing keyboard preferences and action semantics.

**Architecture:** Add a typed `InputGesture` value, adapters for legacy `KeyBinding` and new persisted mouse/wheel/modifier-mode bindings, then expose both through a metadata registry. Existing widgets keep owning behavior, but resolve events through registered actions; staged UI changes are committed only by the settings page Save button. Context-aware conflict detection permits reuse only when event-delivery contexts do not overlap.

**Tech Stack:** Java 8 source compatibility, Haven widget toolkit, `Utils` preferences, Java `ResourceBundle` localization, JUnit Jupiter 1.14.2, Ant.

**Spec:** `docs/superpowers/specs/2026-09-07-unified-hotkeys-design.md`

## Global Constraints

- Include gameplay actions only; text editing, focus traversal, dialog confirmation, and ordinary widget navigation remain fixed.
- A target-dependent action keeps its input family: keyboard actions accept keys, mouse-target actions accept mouse buttons, wheel actions accept wheel gestures, and modifier modes accept one held modifier.
- Preserve all existing `keybind/<id>` preferences and existing `KeyBinding` identifiers.
- Store new mouse, wheel, and modifier-mode overrides under `gesturebind/<id>`; an empty value means default and `n` means disabled.
- Categories are: All, Windows, World, Inventory, Map, Crafting, Combat, Action Menu, Belts, Sessions, Automation.
- A global conflict context overlaps every context of the same input family; non-global contexts conflict only when their context sets intersect.
- Settings edits, category resets, and reset-all remain staged until Save; Cancel discards them.
- When a server action derives meaning from modifier flags, send its original canonical flags after rebinding rather than the newly pressed physical flags.
- Static labels and controls must exist in both `src/lang/messages.properties` and `src/lang/messages_ru.properties`.
- Dynamic `scm/*` and `wgk/*` actions appear after their resources/widgets are received.
- Do not add runtime dependencies or raise the Java source/target level above 1.8.

---

### Task 1: Typed keyboard, mouse, wheel, and modifier gestures

**Files:**
- Create: `src/nurgling/hotkeys/InputGesture.java`
- Test: `test/nurgling/hotkeys/InputGestureTest.java`

**Interfaces:**
- Produces: `InputGesture.Type { NONE, KEY, MOUSE_BUTTON, MOUSE_WHEEL, MODIFIER }`
- Produces: `InputGesture.none()`, `key(KeyMatch)`, `mouse(int, int, int)`, `wheel(int, int, int)`, `modifier(int)`
- Produces: `matches(KeyEvent, int)`, `matchesMouse(int, int)`, `matchesWheel(int, int)`, `matchesModifiers(int)`
- Produces: `encode()`, `decode(String)`, `displayName()`

- [ ] **Step 1: Write failing value/codec tests**

```java
package nurgling.hotkeys;

import haven.KeyMatch;
import org.junit.jupiter.api.Test;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import static org.junit.jupiter.api.Assertions.*;

class InputGestureTest {
    @Test void mouseGestureRequiresButtonAndMaskedModifiers() {
        InputGesture g = InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S);
        assertTrue(g.matchesMouse(1, KeyMatch.S));
        assertFalse(g.matchesMouse(3, KeyMatch.S));
        assertFalse(g.matchesMouse(1, KeyMatch.S | KeyMatch.C));
    }

    @Test void wheelGestureStoresDirectionNotMagnitude() {
        InputGesture up = InputGesture.wheel(-1, KeyMatch.MODS, KeyMatch.S);
        assertTrue(up.matchesWheel(-4, KeyMatch.S));
        assertFalse(up.matchesWheel(2, KeyMatch.S));
    }

    @Test void keyboardGestureDelegatesLegacyKeyMatchRules() {
        InputGesture g = InputGesture.key(KeyMatch.forcode(KeyEvent.VK_Q, KeyMatch.C));
        KeyEvent q = new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED, 1,
                KeyEvent.CTRL_DOWN_MASK, KeyEvent.VK_Q, 'Q');
        assertTrue(g.matches(q, 0));
    }

    @Test void everyGestureRoundTripsAndCorruptionIsRejected() {
        InputGesture[] values = {
                InputGesture.none(),
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_F, KeyMatch.C | KeyMatch.S)),
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C),
                InputGesture.wheel(1, KeyMatch.MODS, KeyMatch.S),
                InputGesture.modifier(KeyMatch.C)
        };
        for(InputGesture value : values)
            assertEquals(value, InputGesture.decode(value.encode()));
        assertThrows(IllegalArgumentException.class, () -> InputGesture.decode("b:broken"));
    }

    @Test void modifierModeRequiresExactlyOneHeldModifier() {
        InputGesture ctrl = InputGesture.modifier(KeyMatch.C);
        assertTrue(ctrl.matchesModifiers(KeyMatch.C));
        assertFalse(ctrl.matchesModifiers(KeyMatch.C | KeyMatch.S));
        assertThrows(IllegalArgumentException.class,
                () -> InputGesture.modifier(KeyMatch.C | KeyMatch.S));
        assertThrows(IllegalArgumentException.class,
                () -> InputGesture.modifier(1 << 12));
    }
}
```

- [ ] **Step 2: Run the suite and verify RED**

Run: `rtk ant test`

Expected: test compilation fails because `nurgling.hotkeys.InputGesture` does not exist.

- [ ] **Step 3: Implement the immutable gesture value**

Use the following wire format and matching rules in `InputGesture.java`:

```java
public final class InputGesture {
    public enum Type { NONE, KEY, MOUSE_BUTTON, MOUSE_WHEEL, MODIFIER }

    private final Type type;
    private final KeyMatch key;
    private final int code;
    private final int modmask;
    private final int modmatch;

    public static InputGesture none() { return new InputGesture(Type.NONE, null, 0, 0, 0); }
    public static InputGesture key(KeyMatch key) {
        if(key == null || key == KeyMatch.nil) return none();
        return new InputGesture(Type.KEY, key, 0, key.modmask, key.modmatch);
    }
    public static InputGesture mouse(int button, int mask, int match) {
        return new InputGesture(Type.MOUSE_BUTTON, null, button, mask, match);
    }
    public static InputGesture wheel(int direction, int mask, int match) {
        if(direction == 0) throw new IllegalArgumentException("wheel direction is zero");
        return new InputGesture(Type.MOUSE_WHEEL, null, Integer.signum(direction), mask, match);
    }
    public static InputGesture modifier(int mod) {
        if(Integer.bitCount(mod) != 1 || (mod & ~KeyMatch.MODS) != 0)
            throw new IllegalArgumentException("modifier gesture must contain one bit");
        return new InputGesture(Type.MODIFIER, null, mod, 0, 0);
    }

    private boolean mods(int actual) {
        return (actual & modmask) == (modmatch & modmask);
    }
    public boolean matches(KeyEvent event, int ignored) {
        return type == Type.KEY && key.match(event, ignored);
    }
    public boolean matchesMouse(int button, int mods) {
        return type == Type.MOUSE_BUTTON && code == button && mods(mods);
    }
    public boolean matchesWheel(int amount, int mods) {
        return type == Type.MOUSE_WHEEL && Integer.signum(amount) == code && mods(mods);
    }
    public boolean matchesModifiers(int mods) {
        return type == Type.MODIFIER && code == (mods & KeyMatch.MODS);
    }

    public String encode() {
        switch(type) {
        case NONE: return "n";
        case KEY: return "k:" + key.reduce();
        case MOUSE_BUTTON: return "b:" + code + ":" + modmask + ":" + modmatch;
        case MOUSE_WHEEL: return "w:" + code + ":" + modmask + ":" + modmatch;
        case MODIFIER: return "m:" + code;
        default: throw new AssertionError(type);
        }
    }
}
```

Implement `decode`, accessors, `equals`, `hashCode`, and `displayName` without locale-specific strings. `decode` must reject modifier payloads with zero or multiple bits. `displayName` must use `KeyMatch.name()` for keys, `LMB/MMB/RMB/Button N` for buttons, `Wheel Up/Wheel Down` for the wheel, and `Shift/Ctrl/Alt` for modifier modes; button and wheel names are prefixed by modifiers from `modmatch`.

- [ ] **Step 4: Run the suite and verify GREEN**

Run: `rtk ant test`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
rtk git add src/nurgling/hotkeys/InputGesture.java test/nurgling/hotkeys/InputGestureTest.java
rtk git commit -m "feat: add typed input gestures"
```

---

### Task 2: Legacy-compatible persisted bindings

**Files:**
- Create: `src/nurgling/hotkeys/PreferenceStore.java`
- Create: `src/nurgling/hotkeys/HotkeyBinding.java`
- Create: `src/nurgling/hotkeys/KeyBindingHotkey.java`
- Create: `src/nurgling/hotkeys/GestureBinding.java`
- Test: `test/nurgling/hotkeys/HotkeyBindingTest.java`

**Interfaces:**
- Consumes: `InputGesture`
- Produces: `PreferenceStore.get(String, String)` and `set(String, String)`
- Produces: `HotkeyBinding.id()`, `defaultGesture()`, `current()`, `set(InputGesture)`, `reset()`
- Produces: `KeyBindingHotkey(KeyBinding)` and `GestureBinding(String, InputGesture, PreferenceStore)`

- [ ] **Step 1: Write failing adapter and persistence tests**

```java
class HotkeyBindingTest {
    @Test void legacyAdapterKeepsTheOriginalPreferenceIdentity() {
        KeyBinding key = KeyBinding.get("test/unified-hotkeys/legacy",
                KeyMatch.forcode(KeyEvent.VK_Q, 0));
        KeyBindingHotkey binding = new KeyBindingHotkey(key);
        binding.set(InputGesture.key(KeyMatch.forcode(KeyEvent.VK_E, KeyMatch.C)));
        assertEquals("test/unified-hotkeys/legacy", binding.id());
        assertEquals(KeyEvent.VK_E, key.key().code);
        binding.reset();
        assertEquals(KeyEvent.VK_Q, key.key().code);
    }

    @Test void gestureBindingLoadsCustomDisabledAndCorruptValues() {
        MemoryPreferences prefs = new MemoryPreferences();
        InputGesture def = InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S);
        GestureBinding binding = new GestureBinding("item-transfer", def, prefs);
        binding.set(InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C));
        assertEquals(binding.current(), new GestureBinding("item-transfer", def, prefs).current());
        binding.set(InputGesture.none());
        assertEquals(InputGesture.Type.NONE, binding.current().type());
        prefs.set("gesturebind/item-transfer", "broken");
        assertEquals(def, new GestureBinding("item-transfer", def, prefs).current());
    }
}
```

The test-local `MemoryPreferences` is a `HashMap<String, String>` implementation of `PreferenceStore`; it must stay in the test file.

- [ ] **Step 2: Run the suite and verify RED**

Run: `rtk ant test`

Expected: test compilation fails because the four binding types do not exist.

- [ ] **Step 3: Implement the binding boundary**

```java
public interface HotkeyBinding {
    String id();
    InputGesture defaultGesture();
    InputGesture current();
    void set(InputGesture gesture);
    void reset();
}

public interface PreferenceStore {
    String get(String key, String fallback);
    void set(String key, String value);

    PreferenceStore SYSTEM = new PreferenceStore() {
        public String get(String key, String fallback) { return Utils.getpref(key, fallback); }
        public void set(String key, String value) { Utils.setpref(key, value); }
    };
}
```

`KeyBindingHotkey.set(InputGesture.none())` calls `KeyBinding.set(KeyMatch.nil)`, a keyboard gesture calls `KeyBinding.set(gesture.keyMatch())`, and `reset()` calls `KeyBinding.set(null)`. Reject mouse, wheel, and modifier-mode types with `IllegalArgumentException`.

`GestureBinding` reads `gesturebind/<id>` once in its constructor, uses the default for empty or malformed data, persists `InputGesture.encode()` from `set`, and persists an empty string from `reset`. Accept mouse, wheel, modifier-mode, and `NONE` values; reject `KEY` values so the adapter ownership stays unambiguous.

- [ ] **Step 4: Run the suite and verify GREEN**

Run: `rtk ant test`

Expected: all tests pass, including the legacy key reset test.

- [ ] **Step 5: Commit**

```bash
rtk git add src/nurgling/hotkeys test/nurgling/hotkeys/HotkeyBindingTest.java
rtk git commit -m "feat: adapt persisted hotkey bindings"
```

---

### Task 3: Metadata registry, context conflicts, and staged edits

**Files:**
- Create: `src/nurgling/hotkeys/HotkeyCategory.java`
- Create: `src/nurgling/hotkeys/HotkeyContext.java`
- Create: `src/nurgling/hotkeys/HotkeyAction.java`
- Create: `src/nurgling/hotkeys/HotkeyConflict.java`
- Create: `src/nurgling/hotkeys/HotkeyRegistry.java`
- Create: `src/nurgling/hotkeys/HotkeyDraftModel.java`
- Test: `test/nurgling/hotkeys/HotkeyRegistryTest.java`
- Test: `test/nurgling/hotkeys/HotkeyDraftModelTest.java`

**Interfaces:**
- Consumes: `HotkeyBinding`, `InputGesture`
- Produces: immutable `HotkeyAction`
- Produces: ordered `HotkeyRegistry.register`, `snapshot`, `find`, `conflicts`, `addListener`, `removeListener`
- Produces: `HotkeyDraftModel.assign`, `reset`, `resetCategory`, `resetAll`, `effective`, `conflicts`, `save`, `cancel`

- [ ] **Step 1: Write failing conflict and duplicate-registration tests**

```java
@Test void globalConflictsWithEveryContextButSeparateSurfacesCanReuseGesture() {
    InputGesture g = InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C);
    HotkeyRegistry registry = registryWithGlobalInventoryAndMap(g);
    assertEquals(2, registry.conflicts("global", g).size());

    HotkeyRegistry surfaces = new HotkeyRegistry();
    surfaces.register(action("inventory", HotkeyContext.INVENTORY_ITEM_GENERIC, g));
    surfaces.register(action("map", HotkeyContext.WORLD_SURFACE, g));
    assertTrue(surfaces.conflicts("inventory", g).isEmpty());
}

@Test void incompatibleDuplicateIdFailsLoudly() {
    HotkeyRegistry registry = new HotkeyRegistry();
    registry.register(action("same", HotkeyContext.WORLD_SURFACE,
            InputGesture.mouse(1, KeyMatch.MODS, 0)));
    assertThrows(IllegalStateException.class, () -> registry.register(
            action("same", HotkeyContext.INVENTORY_ITEM_GENERIC,
                    InputGesture.mouse(1, KeyMatch.MODS, 0))));
}
```

- [ ] **Step 2: Write failing staged replacement tests**

```java
@Test void replacementDisablesOldActionAndSaveIsAtomicFromTheModel() {
    MemoryBinding old = binding("old", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_Q, 0)));
    MemoryBinding next = binding("next", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_W, 0)));
    HotkeyRegistry registry = registryWithGlobal(old, next);
    HotkeyDraftModel draft = new HotkeyDraftModel(registry);
    HotkeyConflict conflict = draft.assign("next", old.current()).get(0);
    draft.replace(conflict);
    assertEquals(InputGesture.Type.NONE, draft.effective("old").type());
    assertEquals(KeyEvent.VK_W, next.current().keyMatch().code);
    draft.save();
    assertEquals(InputGesture.Type.NONE, old.current().type());
    assertEquals(KeyEvent.VK_Q, next.current().keyMatch().code);
}

@Test void cancelAndCategoryResetDoNotWriteBindings() {
    HotkeyDraftModel draft = new HotkeyDraftModel(registry);
    draft.resetCategory(HotkeyCategory.MAP);
    draft.cancel();
    assertEquals(original, binding.current());
}
```

- [ ] **Step 3: Run the suite and verify RED**

Run: `rtk ant test`

Expected: test compilation fails because registry and draft types do not exist.

- [ ] **Step 4: Implement immutable action metadata and registry ordering**

`HotkeyAction` must have this constructor contract:

```java
public HotkeyAction(String id, String labelKey, String literalLabel,
                    HotkeyCategory category, Set<HotkeyContext> contexts,
                    Set<InputGesture.Type> allowedTypes,
                    HotkeyBinding binding, Integer canonicalMods,
                    int order, boolean dynamic)
```

Declare exact event-delivery contexts: `GLOBAL`, `WORLD_SURFACE`, `MAP_SURFACE`, `MINIMAP_SURFACE`, `INVENTORY_ITEM_GENERIC`, `INVENTORY_ITEM_NURGLING`, `INVENTORY_BACKGROUND`, `HELD_ITEM`, `CRAFT_WINDOW`, `COMBAT_UI`, `FLOWER_MENU_MODE`, `MENU_SEARCH_MODE`, `ROSTER_BUTTON_MODE`, `BUDDY_WINDOW`, `WOUND_WINDOW`, `LAYOUT_EDIT`, `COMPASS_WIDGET`, `LAND_SURVEY`, `RESOURCE_TIMERS_WINDOW`, `MAP_ICON_SETTINGS`, `ACTION_MENU`, `BELT`, and `SESSION_SWITCHER`. Two non-global actions overlap only when their context sets intersect; `GLOBAL` overlaps every context for the same input type.

Use enum declaration order for categories, then `order`, localized label, and `id` for stable row ordering. `label()` returns `literalLabel`, otherwise `L10n.get(labelKey)`, otherwise `id`.

`HotkeyRegistry.register` is idempotent only when every metadata field and binding identity agree. Notify listeners after releasing the registry lock so a UI listener may safely call `snapshot()`.

- [ ] **Step 5: Implement staged edits and conflict replacement**

Store drafts as exact operations rather than only effective values:

```java
private enum ChangeKind { SET, RESET }
private static final class Change {
    final ChangeKind kind;
    final InputGesture value;
}
```

`reset*` stages `RESET`; `assign` stages `SET`; `effective` uses the default for `RESET`, the staged value for `SET`, and the binding's current value otherwise. `save()` first builds an ordered list of operations, validates all conflicts against the final draft state, applies them, then clears the draft. `replace(conflict)` stages `NONE` for the conflicting old action and the requested value for the selected action.

- [ ] **Step 6: Run the suite and verify GREEN**

Run: `rtk ant test`

Expected: all registry and draft tests pass.

- [ ] **Step 7: Commit**

```bash
rtk git add src/nurgling/hotkeys test/nurgling/hotkeys
rtk git commit -m "feat: register and stage hotkey actions"
```

---

### Task 4: Register all current keyboard actions and dynamic bindings

**Files:**
- Create: `src/nurgling/hotkeys/Hotkeys.java`
- Create: `src/nurgling/hotkeys/HotkeyCatalog.java`
- Modify: `src/haven/KeyBinding.java:31-87`
- Modify: `src/haven/MenuGrid.java:170-179`
- Modify: `src/haven/Widget.java:789-799`
- Modify: `src/nurgling/conf/NToolBeltProp.java:29-43`
- Test: `test/nurgling/hotkeys/HotkeyCatalogTest.java`

**Interfaces:**
- Consumes: every existing static `KeyBinding`
- Produces: `Hotkeys.registry()` and named action constants
- Produces: `HotkeyCatalog.registerCore`, `registerMenuAction`, `registerWidgetAction`, `registerBelt`
- Produces: read-only `KeyBinding.snapshot()` for completeness tests

- [ ] **Step 1: Write the failing core-catalog test**

```java
@Test void coreCatalogContainsPreviouslyListedAndPreviouslyHiddenBindings() {
    HotkeyRegistry registry = new HotkeyRegistry();
    HotkeyCatalog.registerCore(registry);
    String[] ids = {"inv", "equ", "areas", "cookbook", "craft-atlas", "storage",
            "cam-left", "mapwnd/prov", "make/one", "fgt/0", "quickaction",
            "mwnd_fog", "session-next", "belt00"};
    for(String id : ids)
        assertNotNull(registry.find(id), id);
}

@Test void dynamicRegistrationIsVisibleAndIdempotent() {
    HotkeyRegistry registry = new HotkeyRegistry();
    KeyBinding binding = KeyBinding.get("scm/test/action", KeyMatch.nil);
    HotkeyCatalog.registerMenuAction(registry, binding, "Test action");
    HotkeyCatalog.registerMenuAction(registry, binding, "Test action");
    assertEquals(1, registry.snapshot().size());
}
```

- [ ] **Step 2: Run the suite and verify RED**

Run: `rtk ant test`

Expected: compilation fails because `Hotkeys`, `HotkeyCatalog`, and `KeyBinding.snapshot()` are absent.

- [ ] **Step 3: Add a safe binding snapshot**

```java
public static Collection<KeyBinding> snapshot() {
    synchronized(bindings) {
        return(Collections.unmodifiableList(new ArrayList<>(bindings.values())));
    }
}
```

- [ ] **Step 4: Implement the static catalog**

Register the following groups with `new KeyBindingHotkey(binding)` and `allowedTypes = EnumSet.of(KEY)`:

| Category | Context | Binding sources |
|---|---|---|
| Windows | GLOBAL | `GameUI.kb_inv`, `kb_equ`, `kb_chr`, `kb_bud`, `kb_areas`, `kb_cookbook`, `kb_craftAtlas`, `kb_searchWidget`, `kb_blueprints`, `kb_baseplanner`, `kb_storage`, `kb_opt` |
| Map | GLOBAL | `GameUI.kb_map`, `kb_claim`, `kb_vil`, `kb_rlm`, `kb_ico`; `MapView.kb_grid`; all `MapWnd.kb_*`; all `NMiniMapWnd.kb_*` |
| Windows | GLOBAL | `GameUI.kb_srch`, `kb_shoot`, `kb_hide`, `kb_logout`, `kb_switchchr`, `kb_instantLogout`, `kb_sort`; `ChatUI.kb_quick` |
| Map | GLOBAL | `MapView.kb_camleft`, `kb_camright`, `kb_camin`, `kb_camout`, `kb_camreset`; all static `NMapView.kb_*` |
| World | GLOBAL | `Speedget.kb_speedup`, `kb_speeddn`, and `kb_speeds[0..3]` |
| Crafting | CRAFT_WINDOW | `Makewindow.kb_make`, `kb_makeall`, `MenuSearch.kb_itemcraft` |
| Combat | COMBAT_UI | `Fightsess.kb_acts[0..9]`, `Fightsess.kb_relcycle` |
| Action Menu | ACTION_MENU | `MenuGrid.kb_root`, `kb_back`, `kb_next` |
| Sessions | SESSION_SWITCHER | all `SessionTabBar.kb_session*` fields |
| Belts | BELT | twelve keys from each configured `NToolBeltProp` |
| Windows | GLOBAL | `LoginScreen.kb_savtoken`, `kb_deltoken` |

Exclude `ConsoleHost.kb_histprev` and `kb_histnext`: they navigate editable command history rather than trigger a game action.

Keep one action per binding ID even where `NCraftWindow` and `NMakewindow` expose the same `make/one` and `make/all` objects.

- [ ] **Step 5: Wire dynamic action registration**

Change `MenuGrid.PagButton.binding()` to register its localized resource name before returning:

```java
public KeyBinding binding() {
    KeyBinding key = KeyBinding.get("scm/" + res.name, hotkey());
    HotkeyCatalog.registerMenuAction(Hotkeys.registry(), key, name());
    return key;
}
```

In the `Widget.uimsg("gk")` named-binding branch, save the binding in a local, call `setgkey`, then call `registerWidgetAction` with `(String) args[1]` as the fallback literal label. In `NToolBeltProp`, call `registerBelt` after each slot binding is created or restored.

- [ ] **Step 6: Run the suite and verify GREEN**

Run: `rtk ant test`

Expected: all catalog tests pass and existing tests still pass.

- [ ] **Step 7: Commit**

```bash
rtk git add src/haven/KeyBinding.java src/haven/MenuGrid.java src/haven/Widget.java src/nurgling/conf/NToolBeltProp.java src/nurgling/hotkeys test/nurgling/hotkeys/HotkeyCatalogTest.java
rtk git commit -m "feat: catalog keyboard hotkeys"
```

---

### Task 5: Filtering, tab layout, capture policy, and conflict presentation model

**Files:**
- Create: `src/nurgling/widgets/nsettings/HotkeySettingsModel.java`
- Create: `src/nurgling/widgets/nsettings/HotkeyTabLayout.java`
- Create: `src/nurgling/widgets/nsettings/HotkeyCapturePolicy.java`
- Create: `src/nurgling/widgets/nsettings/HotkeySettingsLayout.java`
- Test: `test/nurgling/widgets/nsettings/HotkeySettingsModelTest.java`
- Test: `test/nurgling/widgets/nsettings/HotkeyTabLayoutTest.java`
- Test: `test/nurgling/widgets/nsettings/HotkeyCapturePolicyTest.java`

**Interfaces:**
- Consumes: `HotkeyRegistry`, `HotkeyDraftModel`
- Produces: `selectCategory`, `setQuery`, `setConflictsOnly`, `visibleActions`, `selectedCategory`
- Produces: `HotkeyTabLayout.visibleRange` and tab rectangles for horizontal scrolling
- Produces: capture decisions `CANCEL`, `RESET`, `DISABLE`, `ASSIGN`, `REJECT_TYPE`, `IGNORE_MODIFIER`

- [ ] **Step 1: Write failing filter and search tests**

```java
@Test void querySearchesEveryCategoryAndClearingRestoresPreviousTab() {
    HotkeySettingsModel model = modelWith("Inventory", "Transfer item", "Map", "Quick marker");
    model.selectCategory(HotkeyCategory.INVENTORY);
    model.setQuery("marker");
    assertEquals(Arrays.asList("quick-marker"), ids(model.visibleActions()));
    model.setQuery("");
    assertEquals(HotkeyCategory.INVENTORY, model.selectedCategory());
    assertEquals(Arrays.asList("transfer-item"), ids(model.visibleActions()));
}

@Test void conflictsOnlyUsesDraftValues() {
    model.draft().assign("second", model.draft().effective("first"));
    model.setConflictsOnly(true);
    assertEquals(new HashSet<>(Arrays.asList("first", "second")), idSet(model.visibleActions()));
}
```

- [ ] **Step 2: Write failing tab and capture-policy tests**

```java
@Test void narrowTabStripKeepsSelectedTabReachable() {
    HotkeyTabLayout layout = HotkeyTabLayout.calculate(widths(70, 80, 90, 100), 180, 3, 6);
    assertTrue(layout.rect(3).x >= 0);
    assertTrue(layout.rect(3).right() <= 180);
    assertTrue(layout.canScrollLeft());
}

@Test void captureCommandsAndInputFamiliesAreDeterministic() {
    assertEquals(CANCEL, HotkeyCapturePolicy.key(KeyEvent.VK_ESCAPE, allowed(MOUSE_BUTTON)).kind);
    assertEquals(RESET, HotkeyCapturePolicy.key(KeyEvent.VK_BACK_SPACE, allowed(MOUSE_BUTTON)).kind);
    assertEquals(DISABLE, HotkeyCapturePolicy.key(KeyEvent.VK_DELETE, allowed(MOUSE_BUTTON)).kind);
    assertEquals(REJECT_TYPE, HotkeyCapturePolicy.key(KeyEvent.VK_Q, allowed(MOUSE_BUTTON)).kind);
    assertEquals(ASSIGN, HotkeyCapturePolicy.mouse(3, KeyMatch.C, allowed(MOUSE_BUTTON)).kind);
    assertEquals(ASSIGN, HotkeyCapturePolicy.key(KeyEvent.VK_CONTROL, allowed(MODIFIER)).kind);
    assertEquals(IGNORE_MODIFIER,
            HotkeyCapturePolicy.key(KeyEvent.VK_CONTROL, allowed(MOUSE_BUTTON)).kind);
}
```

- [ ] **Step 3: Run the suite and verify RED**

Run: `rtk ant test`

Expected: the four new production classes are missing.

- [ ] **Step 4: Implement the pure models**

Search case-insensitively over localized action label, category label, context labels, and technical ID. While query is non-empty, ignore the selected category; when cleared, use the stored selection. Conflict-only filtering is applied after text/category filtering.

`HotkeyTabLayout.calculate(int[] widths, int viewportWidth, int selectedIndex, int gap)` returns immutable rectangles translated so the selected tab is fully visible. Clamp scrolling to `[contentWidth - viewportWidth, 0]` and expose left/right availability.

`HotkeyCapturePolicy` turns a bare Shift/Ctrl/Alt key into the corresponding one-bit `MODIFIER` gesture only when that family is allowed. Otherwise it returns `IGNORE_MODIFIER`. It treats Escape/Backspace/Delete as commands for every allowed family and rejects an event whose family is absent from `HotkeyAction.allowedTypes()`.

- [ ] **Step 5: Run the suite and verify GREEN**

Run: `rtk ant test`

Expected: all pure model tests pass.

- [ ] **Step 6: Commit**

```bash
rtk git add src/nurgling/widgets/nsettings/HotkeySettingsModel.java src/nurgling/widgets/nsettings/HotkeyTabLayout.java src/nurgling/widgets/nsettings/HotkeyCapturePolicy.java src/nurgling/widgets/nsettings/HotkeySettingsLayout.java test/nurgling/widgets/nsettings
rtk git commit -m "feat: model hotkey settings navigation"
```

---

### Task 6: Build the categorized settings page and replace the legacy editor

**Files:**
- Create: `src/nurgling/widgets/NHotkeyCapture.java`
- Create: `src/nurgling/widgets/nsettings/HotkeySettings.java`
- Create: `src/nurgling/widgets/nsettings/HotkeyActionRow.java`
- Modify: `src/nurgling/widgets/NSettingsWindow.java:99-147,234-279`
- Modify: `src/nurgling/widgets/nsettings/ObjectHiding.java:87-90`
- Modify: `src/haven/OptWnd.java:877-1078,1102,1158-1173`
- Delete: `src/nurgling/widgets/NKeyBindButton.java`
- Modify: `src/lang/messages.properties`
- Modify: `src/lang/messages_ru.properties`
- Test: `test/nurgling/widgets/nsettings/HotkeySettingsLayoutTest.java`
- Test: `test/nurgling/widgets/NSettingsHotkeyNavigationTest.java`

**Interfaces:**
- Consumes: `Hotkeys.registry()`, `HotkeySettingsModel`, `HotkeyCapturePolicy`
- Produces: one `Panel` implementation with staged `load()` and `save()`
- Produces: `NSettingsWindow.showPage(String id)`
- Produces: reusable capture widget that consumes keyboard, mouse-button, and wheel events while armed

- [ ] **Step 1: Write failing page layout and navigation tests**

```java
@Test void rowsStartBelowPinnedSearchTabsAndConflictFilter() {
    HotkeySettingsLayout layout = HotkeySettingsLayout.calculate(560, 478, 24, 28, 24, 30);
    assertTrue(layout.rows.y >= layout.filter.y + layout.filter.h);
    assertEquals(478, layout.viewport.h);
    assertTrue(layout.capture.x + layout.capture.w <= 560);
}

@Test void hotkeyPageHasStableNavigationId() {
    assertEquals("hotkeys", NSettingsWindow.HOTKEY_PAGE_ID);
}
```

- [ ] **Step 2: Run the suite and verify RED**

Run: `rtk ant test`

Expected: missing `HotkeySettingsLayout` and `HOTKEY_PAGE_ID`.

- [ ] **Step 3: Implement capture without firing gameplay actions**

`NHotkeyCapture` takes `(int width, HotkeyAction action, Consumer<HotkeyCapturePolicy.Decision> sink)`. On click it acquires both `ui.grabkeys(this)` and `ui.grabmouse(this)`, changes its label to `…`, and consumes grabbed `keydown`, `mousedown`, and `mousewheel`. Always release both grabs on Cancel, Reset, Disable, Assign, removal, or hide. Use event `ev.b`, `ev.a`, and `ui.modflags()` to construct the candidate gesture; a bare modifier is accepted only for a `MODIFIER` action.

- [ ] **Step 4: Implement the page widgets**

`HotkeySettings` owns one `HotkeySettingsModel`. Build the header outside the internal row `Scrollport` so search, tabs, arrows, and conflict filter stay pinned. `HotkeyActionRow` shows label, localized context summary, capture button, and reset button.

On capture:

```java
List<HotkeyConflict> conflicts = model.assign(action.id(), decision.gesture);
if(conflicts.isEmpty()) {
    rebuildRows();
} else {
    showConflict(action, conflicts.get(0),
            () -> { model.replace(conflicts.get(0)); rebuildRows(); },
            this::rebuildRows);
}
```

`load()` calls `draft.cancel()`, rebuilds from persisted bindings, and preserves the selected category. `save()` rejects unresolved conflicts, calls `draft.save()`, invokes `NConfig.needUpdate()`, and rebuilds.

- [ ] **Step 5: Integrate a stable settings-page ID**

Add `id` to `NSettingsWindow.SettingsItem`, retain the current name-only constructor as a delegating overload, and implement:

```java
public static final String HOTKEY_PAGE_ID = "hotkeys";

public boolean showPage(String id) {
    SettingsItem item = findById(id);
    if(item == null) return false;
    expandAncestors(item);
    list.update();
    showSettings(item);
    return true;
}
```

Add `new SettingsItem(HOTKEY_PAGE_ID, L10n.get("nsettings.item.hotkeys"), new HotkeySettings(), container)` under General.

- [ ] **Step 6: Remove alternate editors**

Delete `OptWnd.BindingPanel` and `OptWnd.PointBind`. Change the main `opt.main.keybind` button to select `nqolwnd` and call `nqolwnd.settingsWindow.showPage(NSettingsWindow.HOTKEY_PAGE_ID)`. Remove the local hotkey row from `ObjectHiding`; its action is editable only on the new page. Delete `NKeyBindButton.java` after `rtk rg -n "NKeyBindButton" src` returns no references.

- [ ] **Step 7: Add page/category/control localization**

Add matching keys in both bundles for:

```properties
nsettings.item.hotkeys=Hotkeys
hotkeys.tab.all=All
hotkeys.tab.windows=Windows
hotkeys.tab.world=World
hotkeys.tab.inventory=Inventory
hotkeys.tab.map=Map
hotkeys.tab.crafting=Crafting
hotkeys.tab.combat=Combat
hotkeys.tab.action_menu=Action Menu
hotkeys.tab.belts=Belts
hotkeys.tab.sessions=Sessions
hotkeys.tab.automation=Automation
hotkeys.search=Search actions
hotkeys.conflicts_only=Conflicts only
hotkeys.reset=Reset
hotkeys.reset_category=Reset category
hotkeys.reset_all=Reset all
hotkeys.replace=Replace previous
hotkeys.cancel=Cancel
hotkeys.capture=Press a key, modifier, mouse button, or wheel
hotkeys.capture.wrong_type=This action requires: %s
hotkeys.unassigned=None
```

Use natural Russian translations in `messages_ru.properties`; keep `%s` identical.

- [ ] **Step 8: Run tests and verify GREEN**

Run: `rtk ant test`

Expected: all tests pass; no source reference to `BindingPanel`, `PointBind`, or `NKeyBindButton` remains.

- [ ] **Step 9: Commit**

```bash
rtk git add src/haven/OptWnd.java src/nurgling/widgets src/lang test/nurgling/widgets
rtk git commit -m "feat: add categorized hotkey settings"
```

---

### Task 7: Convert inventory, item, wheel, and held-item actions

**Files:**
- Modify: `src/nurgling/hotkeys/Hotkeys.java`
- Create: `src/nurgling/hotkeys/HotkeyResolver.java`
- Modify: `src/haven/DTarget.java:29-83`
- Modify: `src/haven/WItem.java:180-213`
- Modify: `src/nurgling/NWItem.java:308-345`
- Modify: `src/haven/Inventory.java:113-124`
- Modify: `src/nurgling/NInventory.java:1730-1940`
- Modify: `src/haven/ItemDrag.java:53-82`
- Modify: `src/haven/MapView.java:2592-2610`
- Test: `test/nurgling/hotkeys/InventoryHotkeysTest.java`
- Test: `test/haven/DTargetExplicitModifiersTest.java`

**Interfaces:**
- Consumes: registry-backed mouse and wheel actions
- Produces: ordered `HotkeyResolver.firstMouse` and `firstWheel`
- Produces: `DTarget.Interact.mods` and a three-argument `iteminteract(Coord, Coord, int)` compatibility overload

- [ ] **Step 1: Write failing default-action tests**

Register and assert this exact table:

| ID | Default | Context | Canonical server meaning |
|---|---|---|---|
| `item.take` | LMB | GENERIC + NURGLING item | `take` |
| `item.interact` | RMB | GENERIC + NURGLING item | `iact`, mods 0 |
| `item.transfer.one` | Shift+LMB | GENERIC + NURGLING item | `transfer`, count 1 |
| `item.transfer.all` | Ctrl+Shift+LMB | GENERIC + NURGLING item | `transfer`, count -1 |
| `item.drop.one` | Ctrl+LMB | GENERIC + NURGLING item | `drop`, count 1 |
| `item.drop.all` | Ctrl+Alt+LMB | INVENTORY_ITEM_GENERIC | `drop`, count -1 outside `NInventory` |
| `item.recipes` | Alt+RMB | INVENTORY_ITEM_NURGLING | local recipe search |
| `item.transfer_same.desc` | Alt+Shift+LMB | INVENTORY_ITEM_NURGLING | `transfer-same`, false |
| `item.transfer_same.asc` | Alt+Shift+RMB | INVENTORY_ITEM_NURGLING | `transfer-same`, true |
| `item.drop_same.desc` | Ctrl+Alt+LMB | INVENTORY_ITEM_NURGLING | `drop-same`, false in `NInventory` |
| `item.drop_same.asc` | Ctrl+Alt+RMB | INVENTORY_ITEM_NURGLING | `drop-same`, true |
| `inventory.transfer_to_main` | Shift+Wheel Up | INVENTORY_BACKGROUND | `invxf` to main inventory |
| `inventory.transfer_from_main` | Shift+Wheel Down | INVENTORY_BACKGROUND | `invxf` from main inventory |
| `held.drop_on_target` | LMB | HELD_ITEM | `Drop` dispatch |
| `held.interact_with_target` | RMB | HELD_ITEM | `Interact` dispatch, mods 0 |
| `held.open_without_using` | Alt+RMB | HELD_ITEM | regular map RMB, mods 0 |
| `held.light_from_fire` | Ctrl+Alt+RMB | HELD_ITEM | `itemact`, mods `UI.MOD_CTRL | UI.MOD_META` |

Register every “GENERIC + NURGLING item” row with both exact contexts. The overlapping `item.drop.all` and `item.drop_same.desc` defaults do not conflict because `item.drop.all` excludes `NInventory` while the specialized action applies only to `NInventory`; express those as `INVENTORY_ITEM_GENERIC` and `INVENTORY_ITEM_NURGLING` contexts rather than one shared context.

- [ ] **Step 2: Write failing explicit-server-modifier test**

```java
@Test void interactCarriesCanonicalModifiersAcrossDispatch() {
    DTarget.Interact event = new DTarget.Interact(Coord.z, null,
            UI.MOD_CTRL | UI.MOD_META);
    assertEquals(UI.MOD_CTRL | UI.MOD_META, event.mods);
    DTarget.Interact derived = event.derive(Coord.of(4, 5));
    assertEquals(event.mods, derived.mods);
}
```

- [ ] **Step 3: Run the suite and verify RED**

Run: `rtk ant test`

Expected: gesture actions and the explicit-modifier constructor are missing.

- [ ] **Step 4: Add explicit semantic modifiers to `DTarget.Interact`**

```java
public default boolean iteminteract(Coord cc, Coord ul, int mods) {
    return iteminteract(cc, ul);
}
public default boolean iteminteract(Interact ev) {
    return iteminteract(ev.c, ev.c.sub(ev.src.doff), ev.mods);
}

public static class Interact extends ItemEvent {
    public final int mods;
    public Interact(Coord c, ItemDrag src, int mods) { super(c, src); this.mods = mods; }
    public Interact(Coord c, ItemDrag src) { this(c, src, src.ui.modflags()); }
    public Interact(Interact from, Coord c) { super(from, c); this.mods = from.mods; }
    public Interact derive(Coord c) { return new Interact(this, c); }
}
```

Keep the old constructor and two-argument `iteminteract` behavior for source compatibility. Override the three-argument method in `MapView` and `WItem` and use the supplied `mods` in outgoing `itemact` messages.

- [ ] **Step 5: Replace direct item conditions with ordered action resolution**

Resolve specialized `NWItem` actions before generic `WItem` actions, preserving current precedence. Resolve `held.open_without_using` before `held.light_from_fire`, then generic held interaction. Pass `HotkeyAction.canonicalMods()` into `DTarget.Interact` for the light action. Do not mutate `ui.modctrl`, `ui.modshift`, or `ui.modmeta` to emulate a binding.

- [ ] **Step 6: Run tests and verify GREEN**

Run: `rtk ant test`

Expected: all tests pass; changing a gesture in the test registry changes resolution while command/canonical values remain unchanged.

- [ ] **Step 7: Commit**

```bash
rtk git add src/haven/DTarget.java src/haven/WItem.java src/haven/Inventory.java src/haven/ItemDrag.java src/haven/MapView.java src/nurgling/NWItem.java src/nurgling/NInventory.java src/nurgling/hotkeys test/haven test/nurgling/hotkeys
rtk git commit -m "feat: make item gestures configurable"
```

---

### Task 8: Convert world, map, crafting, combat, and window gestures

**Files:**
- Modify: `src/nurgling/hotkeys/Hotkeys.java`
- Modify: `src/nurgling/NMapView.java:1591-1834`
- Modify: `src/haven/MapView.java:2420-2620`
- Modify: `src/nurgling/widgets/NMiniMap.java:3260-3660`
- Modify: `src/nurgling/widgets/NMapWnd.java:700-780`
- Modify: `src/nurgling/widgets/NMiniMapWnd.java:370-430`
- Modify: `src/haven/MapWnd.java:110-205`
- Modify: `src/nurgling/NFlowerMenu.java:47-278`
- Modify: `src/nurgling/widgets/NMakewindow.java:500-550`
- Modify: `src/haven/FightWnd.java:184-190`
- Modify: `src/haven/ISBox.java:90-116`
- Modify: `src/nurgling/widgets/NBuddyWnd.java:130-145`
- Modify: `src/nurgling/NWoundBox.java:380-400`
- Modify: `src/nurgling/widgets/NDraggableWidget.java:200-225`
- Modify: `src/nurgling/widgets/compass/NCompassWidget.java:40-110`
- Modify: `src/nurgling/NGameUI.java:1699-1718`
- Modify: `src/nurgling/widgets/LocalizedResourceTimersWindow.java:205-216`
- Modify: `src/haven/UI.java:924-934`
- Modify: `src/haven/GobIcon.java:983-992`
- Modify: `src/haven/MenuSearch.java:190-204`
- Modify: `src/haven/res/ui/croster/RosterButton.java:41-51`
- Modify: `src/haven/res/ui/stackinv/ItemStack.java:60-68`
- Modify: `src/haven/res/ui/surv/LandSurvey.java:159-171`
- Modify: `src/haven/Fightsess.java:395,427-469`
- Delete: `src/nurgling/QuickMapMarkerGesture.java`
- Test: `test/nurgling/hotkeys/GameplayGestureCatalogTest.java`
- Test: `test/nurgling/hotkeys/GameplayGestureDispatchTest.java`

**Interfaces:**
- Consumes: `HotkeyResolver` and explicit canonical modifier transport from Task 7
- Produces: registered actions for every current direct gameplay modifier condition in these handlers

- [ ] **Step 1: Write the failing gameplay gesture catalog test**

Assert this mapping table by ID, default gesture, category, context, and canonical modifier value where present:

| ID | Default gesture | Context |
|---|---|---|
| `world.planner.remove_ghost` | Shift+RMB | WORLD_SURFACE |
| `world.planner.clone_ghost` | MMB | WORLD_SURFACE |
| `world.share_chat_area` | Ctrl+Alt+LMB | WORLD_SURFACE |
| `map.quick_marker` | Alt+MMB | WORLD_SURFACE |
| `world.toggle_object_ring` | Ctrl+MMB | WORLD_SURFACE |
| `world.context_menu` | Ctrl+RMB | WORLD_SURFACE |
| `world.queue_waypoint` | Alt+LMB | WORLD_SURFACE |
| `world.ping` | Alt+Shift+LMB | WORLD_SURFACE |
| `world.placement.rotate_left` | Left Arrow | WORLD_SURFACE |
| `world.placement.rotate_right` | Right Arrow | WORLD_SURFACE |
| `world.selection.rotate` | R | WORLD_SURFACE |
| `world.selection.toggle_grid` | C | WORLD_SURFACE |
| `map.marker.delete` | Shift+RMB | MAP_SURFACE |
| `map.marker.edit` | Alt+LMB | MAP_SURFACE |
| `map.marker.waypoint` | Shift+LMB | MAP_SURFACE |
| `map.ping` | Alt+Shift+LMB | MAP_SURFACE |
| `flower.force_manual` | Shift | FLOWER_MENU_MODE |
| `flower.control_mode` | Ctrl | FLOWER_MENU_MODE |
| `action_menu.keep_search_open` | Ctrl | MENU_SEARCH_MODE |
| `action_menu.open_all_rosters` | Shift | ROSTER_BUTTON_MODE |
| `craft.show_recipes` | Alt+RMB | CRAFT_WINDOW |
| `combat.action_points.increase` | Shift+Wheel Up | COMBAT_UI |
| `combat.action_points.decrease` | Shift+Wheel Down | COMBAT_UI |
| `fgt-cycle` | Ctrl+Tab | COMBAT_UI |
| `fgt-cycle-prev` | Ctrl+Shift+Tab | COMBAT_UI |
| `stockpile.transfer_all` | Shift+LMB | INVENTORY_BACKGROUND |
| `stockpile.transfer_out` | Wheel Up | INVENTORY_BACKGROUND |
| `stockpile.transfer_in` | Wheel Down | INVENTORY_BACKGROUND |
| `inventory.stack.transfer_to_main` | Shift+Wheel Up | INVENTORY_BACKGROUND |
| `inventory.stack.transfer_from_main` | Shift+Wheel Down | INVENTORY_BACKGROUND |
| `buddy.pull_mode` | Shift+LMB | BUDDY_WINDOW |
| `wound.find_treatment_storage` | Ctrl+RMB | WOUND_WINDOW |
| `layout.undo` | Ctrl+Z | LAYOUT_EDIT |
| `layout.compass_resize` | Ctrl+LMB | COMPASS_WIDGET |
| `world.survey.new_selection` | Shift+LMB | LAND_SURVEY |
| `window.db_stats.toggle` | F11 | GLOBAL |
| `window.agent.toggle` | F10 | GLOBAL |
| `window.resource_timers.refresh` | F5 | RESOURCE_TIMERS_WINDOW |
| `window.map_icons.toggle_selected` | Space | MAP_ICON_SETTINGS |
| `system.rendering.toggle` | F8 | GLOBAL |

The two flower-mode rows and the two action-menu-mode rows use `InputGesture.Type.MODIFIER`. Use the exact wheel-up and wheel-down rows above because their signs select opposite commands. Use separate map-window and minimap contexts when their surfaces receive events independently.

- [ ] **Step 2: Write failing dispatch tests for priority and canonical semantics**

```java
@Test void ctrlRightClickStillChoosesWorldContextMenuAfterRebinding() {
    HotkeyAction action = registry.find("world.context_menu");
    draft.assign(action.id(), InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.S));
    assertEquals("world.context_menu",
            resolver.firstMouse(worldActions, 3, KeyMatch.S).id());
    assertNull(resolver.firstMouse(worldActions, 3, KeyMatch.C));
}

@Test void reboundWorldPingKeepsOriginalServerModifierFlags() {
    HotkeyAction ping = registry.find("world.ping");
    assertEquals(Integer.valueOf(UI.MOD_META | UI.MOD_SHIFT), ping.canonicalMods());
}
```

- [ ] **Step 3: Run the suite and verify RED**

Run: `rtk ant test`

Expected: the catalog lacks the listed gesture IDs.

- [ ] **Step 4: Register the mappings and replace direct branches**

For local client actions, replace conditions directly with `Hotkeys.<ACTION>.matches...`. For server-routed map clicks, extend the existing `MapView.Click`/placement path to accept explicit modifier flags, retain the current constructor as a `ui.modflags()` delegating overload, and pass the action's `canonicalMods`. Preserve the current handler order in `NMapView.mousedown`: planner, selection modes, quick marker, ring toggle, icon click-through, context menu, hold steering, base handler.

Remove the pre-dispatch F8 branch from `UI.keydown` and handle `system.rendering.toggle` in `NGameUI` after normal grabbed-key dispatch. This ensures an armed hotkey capture consumes F8 without toggling rendering. The stockpile wheel actions keep canonical server modifiers `0` after rebinding.

Replace `QuickMapMarkerGesture.matches(...)` with `Hotkeys.MAP_QUICK_MARKER.current().matchesMouse(...)`, then delete the one-purpose class and update its test into `GameplayGestureDispatchTest`.

- [ ] **Step 5: Convert secondary-window gesture branches**

Use the mapping table rather than raw `ui.mod*` checks for the listed handlers. Split the old `fgt-cycle` behavior: preserve its ID and stored binding for next target, remove the old ignored-Shift mask from its `KeyBinding.get` call, and create `fgt-cycle-prev` with default Ctrl+Shift+Tab so both directions can be changed independently. Keep these non-actions fixed and do not register them: Shift-expanded item tooltip, Ctrl+F focusing a search field, Shift+Tab focus traversal in `NCraftWindow`, chat history/scroll navigation, and text paste shortcuts.

- [ ] **Step 6: Run tests and verify GREEN**

Run: `rtk ant test`

Expected: all dispatch tests pass and the previous feature-specific gesture tests either still pass or have been moved without losing assertions.

- [ ] **Step 7: Commit**

```bash
rtk git add src/haven src/nurgling test/haven test/nurgling
rtk git commit -m "feat: make gameplay gestures configurable"
```

---

### Task 9: Enforce completeness, finish localization, and verify the client

**Files:**
- Create: `test/nurgling/hotkeys/GameplayHotkeyAuditTest.java`
- Create: `test/nurgling/hotkeys/system-shortcut-allowlist.txt`
- Modify: `src/lang/messages.properties`
- Modify: `src/lang/messages_ru.properties`

**Interfaces:**
- Consumes: source tree and `HotkeyCatalog`
- Produces: a build-breaking guard against uncatalogued `KeyBinding.get(...)` IDs and direct gameplay shortcut conditions

- [ ] **Step 1: Write a failing source-audit test**

The test walks `src/haven` and `src/nurgling`, reads `.java` files as UTF-8, and reports `relative/path.java:line: normalized-condition`. Detect:

```java
private static final Pattern DIRECT_CONDITION = Pattern.compile(
    "\\b(?:if|else\\s+if)\\s*\\([^\\n]*(?:ui\\.mod(?:shift|ctrl|meta)|ev\\.mods|ev\\.code|KeyEvent\\.VK_)");
private static final Pattern KEY_ID = Pattern.compile(
    "KeyBinding\\.get\\(\\s*\"([^\"]+)\"");
```

For `KEY_ID`, require the literal ID in `HotkeyCatalog.knownStaticIds()`; allow only the explicit dynamic prefixes `scm/`, `wgk/`, belt names constructed by `NToolBeltProp`, and test-only IDs. For direct conditions, require either a `Hotkeys.` reference in the same condition/method or an exact normalized entry in the allowlist.

- [ ] **Step 2: Run the audit and verify RED**

Run: `rtk ant test`

Expected: the new test fails on the exact fixed system/navigation conditions listed in Step 3 because the allowlist is initially empty; it must not report a handler from the explicit Task 7-8 file lists.

- [ ] **Step 3: Create the narrow system allowlist**

Allow only these classes/reasons, matching the exact condition text emitted by Step 1 rather than whole files:

| Area | Allowed reason |
|---|---|
| `Widget`, `TextEntry`, `ReadLine`, `NTextArea`, `SearchWidget` | activation, cancellation, focus traversal, editing |
| `Charlist`, `ChatUI`, `ConsoleHost` | list/history navigation |
| `DynresWindow`, search fields including `CraftAtlasWindow` | paste or focus-search |
| `KeyMatch`, `NHotkeyCapture`, `HotkeyCapturePolicy` | binding capture itself |
| `AWTCompat`, `UI`, toolkit implementations | low-level event conversion |
| `Debug`, `UILoop` | developer-only debug keys |
| `WItem`, `NGItem`, `ISlots` tooltip methods | expanded tooltip display only |
| `NCraftWindow` | Shift+Tab focus traversal only |

Use the actual class names present in the scan; do not add wildcard directory exemptions. Every allowlist line includes `path|normalized condition|reason`.

- [ ] **Step 4: Verify the explicit gameplay conversion set**

Run `rtk ant test`. Expected: the audit has zero unclassified results after the exact allowlist is added. Assert in `GameplayHotkeyAuditTest` that the Task 7-8 handlers (`WItem`, `NWItem`, `Inventory`, `NInventory`, `ItemDrag`, `MapView`, `NMapView`, `NMiniMap`, `NMapWnd`, `NMiniMapWnd`, `NFlowerMenu`, `NMakewindow`, `FightWnd`, `ISBox`, `NBuddyWnd`, `NWoundBox`, `NDraggableWidget`, `NCompassWidget`, `NGameUI`, `LocalizedResourceTimersWindow`, `GobIcon`, `MenuSearch`, `RosterButton`, `ItemStack`, `LandSurvey`, and `Fightsess`) have no direct shortcut condition unless that condition contains a `Hotkeys.` match. No one of these production files may be added to the system allowlist.

- [ ] **Step 5: Complete action localization and verify bundle parity**

Add one `hotkeys.action.<id>` key for every static catalog action in both bundles. Add a parity assertion to `GameplayHotkeyAuditTest` that loads both property files and verifies every static action label key exists and has a non-empty value. Dynamic action names remain resource-provided.

- [ ] **Step 6: Run full verification**

Run:

```bash
rtk ant test
rtk ant
rtk git diff --check
rtk rg -n "BindingPanel|PointBind|NKeyBindButton" src test
```

Expected: tests and build pass; `git diff --check` is silent; the final search returns no matches.

- [ ] **Step 7: Review the acceptance checklist against the spec**

Confirm from tests and catalog output:

1. One editor exists and the old Options button opens it.
2. Existing `keybind/*` values survive.
3. Keyboard, modifier-mode, mouse-button, and wheel assignments can be changed, disabled, and reset.
4. Inventory transfers and `held.light_from_fire` are present.
5. Context conflicts replace the old action atomically.
6. Search, tabs, conflict filter, category reset, reset-all, Save, and Cancel are covered.
7. Dynamic action registration updates an open model.
8. English and Russian label coverage is complete.
9. The source audit has no unexplained gameplay shortcut.

- [ ] **Step 8: Commit**

```bash
rtk git add src test
rtk git commit -m "test: enforce complete hotkey coverage"
```
