# Semantic Font Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route every user-visible client font through configurable semantic roles while keeping varied, context-appropriate defaults close to the current client.

**Architecture:** A versioned `FontSettings` stores per-role overrides. `FontTheme` resolves inheritance, loads families, caches foundries, and publishes a revision used to invalidate rendered text. Generic primitives and then each feature area migrate from direct AWT/`Text` font construction to explicit `FontRole` requests.

**Tech Stack:** Java 8, Haven widget/rendering classes, Nurgling `NConfig`, JSON, JUnit 5, Ant.

**Spec:** `docs/superpowers/specs/2026-09-07-semantic-font-settings-design.md`

## Global Constraints

- Shipped defaults must remain varied and visually close to the current client; do not seed one universal family.
- Every client-rendered user-visible text path must resolve through a `FontRole`; unknown text falls back to `SYSTEM`.
- Tooltip body text uses `SYSTEM`, tooltip titles use `HEADING`, and technical tooltip lines use `SECONDARY`.
- Chat input and rendered chat messages use the same `CHAT` role.
- Existing five font settings and six item-overlay font settings must migrate without data loss.
- Font changes apply to the running client without restart and trigger relayout when metrics change.
- Semantic feature colors remain authoritative unless a role explicitly enables a color override.
- Existing unrelated working-tree changes must not be staged or rewritten.

---

### Task 1: Define semantic roles and shipped defaults

**Files:**
- Create: `src/nurgling/fonts/FontRoleGroup.java`
- Create: `src/nurgling/fonts/FontEffect.java`
- Create: `src/nurgling/fonts/FontRoleConfig.java`
- Create: `src/nurgling/fonts/FontRole.java`
- Create: `src/nurgling/fonts/ResolvedFontStyle.java`
- Test: `test/nurgling/fonts/FontRoleTest.java`

**Interfaces:**
- Produces: `FontRoleGroup`, `FontEffect`, immutable/copyable `FontRoleConfig`, `FontRole.defaults()`, `FontRole.parent()`, and immutable `ResolvedFontStyle`.
- Default roles: `SYSTEM`, `HEADING`, `WINDOW_TITLE`, `SECONDARY`, `CHAT`, `NOTIFICATION`, `HUD_INFO`, `METER`, `HOTKEY`, `COMBAT_UI`, `QUEST_TITLE`, `QUEST_OBJECTIVE`, `ITEM_QUALITY`, `ITEM_STACK`, `ITEM_AMOUNT`, `ITEM_STUDY`, `ITEM_PROGRESS`, `ITEM_VOLUME`, `CHARACTER_NAME`, `OBJECT_LABEL`, `MAP_LABEL`, `NAVIGATION_LABEL`, `FLOATING_VALUE`, `CONSOLE`.

- [ ] **Step 1: Write the failing defaults test**

```java
package nurgling.fonts;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class FontRoleTest {
    @Test void everyRoleHasACompleteShippedDefault() {
        for (FontRole role : FontRole.values()) {
            FontRoleConfig cfg = role.defaults();
            assertNotNull(cfg.family, role.name());
            assertTrue(cfg.size >= 8 && cfg.size <= 24, role.name());
            assertNotNull(cfg.effect, role.name());
        }
    }

    @Test void defaultsAreDeliberatelyNotUniversal() {
        assertEquals("Sans", FontRole.SYSTEM.defaults().family);
        assertEquals("Open Sans Semibold", FontRole.WINDOW_TITLE.defaults().family);
        assertEquals("Open Sans Semibold", FontRole.HUD_INFO.defaults().family);
        assertEquals("Monospaced", FontRole.CONSOLE.defaults().family);
        assertNotEquals(FontRole.SYSTEM.defaults().family, FontRole.CONSOLE.defaults().family);
    }

    @Test void roleCatalogIsComplete() {
        assertEquals(24, EnumSet.allOf(FontRole.class).size());
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `ant test`

Expected: compilation fails because `nurgling.fonts.FontRole` and related types do not exist.

- [ ] **Step 3: Implement the role model**

Use this public shape:

```java
public enum FontRoleGroup { GENERAL, COMMUNICATION, GAME_UI, QUESTS, ITEMS, WORLD_MAP, TECHNICAL }
public enum FontEffect { NONE, SHADOW, BLUR, OUTLINE }

public final class FontRoleConfig {
    public String family;
    public int size;
    public int style;               // java.awt.Font.PLAIN/BOLD/ITALIC
    public FontEffect effect;
    public boolean inherit;
    public boolean overrideColor;
    public Color color;
    public FontRoleConfig copy() {
        FontRoleConfig out = new FontRoleConfig();
        out.family = family;
        out.size = size;
        out.style = style;
        out.effect = effect;
        out.inherit = inherit;
        out.overrideColor = overrideColor;
        out.color = color;
        return out;
    }
}

public final class ResolvedFontStyle {
    public final String family;
    public final int size;
    public final int style;
    public final FontEffect effect;
    public final boolean overrideColor;
    public final Color color;
    public ResolvedFontStyle(FontRoleConfig cfg) {
        this.family = cfg.family;
        this.size = cfg.size;
        this.style = cfg.style;
        this.effect = cfg.effect;
        this.overrideColor = cfg.overrideColor;
        this.color = cfg.color;
    }
}

public enum FontRole {
    SYSTEM(FontRoleGroup.GENERAL, null, defaults("Sans", 12, Font.PLAIN, FontEffect.NONE)),
    HEADING(FontRoleGroup.GENERAL, SYSTEM, defaults("Open Sans Semibold", 14, Font.PLAIN, FontEffect.NONE)),
    WINDOW_TITLE(FontRoleGroup.GENERAL, HEADING, defaults("Open Sans Semibold", 12, Font.PLAIN, FontEffect.NONE)),
    SECONDARY(FontRoleGroup.GENERAL, SYSTEM, defaults("Open Sans", 10, Font.PLAIN, FontEffect.NONE)),
    CHAT(FontRoleGroup.COMMUNICATION, SYSTEM, defaults("Sans", 14, Font.PLAIN, FontEffect.NONE)),
    NOTIFICATION(FontRoleGroup.COMMUNICATION, SYSTEM, defaults("Sans", 14, Font.PLAIN, FontEffect.SHADOW)),
    HUD_INFO(FontRoleGroup.GAME_UI, SYSTEM, defaults("Open Sans Semibold", 12, Font.PLAIN, FontEffect.BLUR)),
    METER(FontRoleGroup.GAME_UI, HUD_INFO, defaults("Sans", 12, Font.PLAIN, FontEffect.BLUR)),
    HOTKEY(FontRoleGroup.GAME_UI, HUD_INFO, defaults("Sans", 12, Font.BOLD, FontEffect.OUTLINE)),
    COMBAT_UI(FontRoleGroup.GAME_UI, HUD_INFO, defaults("Sans", 16, Font.BOLD, FontEffect.OUTLINE)),
    QUEST_TITLE(FontRoleGroup.QUESTS, HEADING, defaults("Sans", 12, Font.BOLD, FontEffect.NONE)),
    QUEST_OBJECTIVE(FontRoleGroup.QUESTS, SYSTEM, defaults("Sans", 11, Font.PLAIN, FontEffect.NONE)),
    ITEM_QUALITY(FontRoleGroup.ITEMS, SYSTEM, defaults("Sans", 10, Font.BOLD, FontEffect.NONE)),
    ITEM_STACK(FontRoleGroup.ITEMS, ITEM_QUALITY, defaults("Sans", 10, Font.BOLD, FontEffect.NONE)),
    ITEM_AMOUNT(FontRoleGroup.ITEMS, ITEM_QUALITY, defaults("Sans", 10, Font.BOLD, FontEffect.NONE)),
    ITEM_STUDY(FontRoleGroup.ITEMS, SYSTEM, defaults("Sans", 10, Font.BOLD, FontEffect.NONE)),
    ITEM_PROGRESS(FontRoleGroup.ITEMS, SYSTEM, defaults("Sans", 10, Font.PLAIN, FontEffect.NONE)),
    ITEM_VOLUME(FontRoleGroup.ITEMS, SYSTEM, defaults("Sans", 10, Font.PLAIN, FontEffect.NONE)),
    CHARACTER_NAME(FontRoleGroup.WORLD_MAP, SYSTEM, defaults("Sans", 12, Font.PLAIN, FontEffect.OUTLINE)),
    OBJECT_LABEL(FontRoleGroup.WORLD_MAP, SYSTEM, defaults("Sans", 12, Font.PLAIN, FontEffect.OUTLINE)),
    MAP_LABEL(FontRoleGroup.WORLD_MAP, SYSTEM, defaults("Open Sans Semibold", 12, Font.PLAIN, FontEffect.NONE)),
    NAVIGATION_LABEL(FontRoleGroup.WORLD_MAP, SYSTEM, defaults("Sans", 11, Font.PLAIN, FontEffect.OUTLINE)),
    FLOATING_VALUE(FontRoleGroup.WORLD_MAP, SYSTEM, defaults("Sans", 12, Font.BOLD, FontEffect.OUTLINE)),
    CONSOLE(FontRoleGroup.TECHNICAL, SYSTEM, defaults("Monospaced", 12, Font.PLAIN, FontEffect.NONE));
}
```

Set current item colors in their defaults: study yellow `(255,255,50)`, progress orange `(234,164,101)`, and volume green `(65,255,115)`. Do not set `overrideColor` for roles whose colors carry gameplay meaning.

- [ ] **Step 4: Run tests and verify GREEN**

Run: `ant test`

Expected: `FontRoleTest` passes and existing tests remain green.

- [ ] **Step 5: Commit**

```bash
git add src/nurgling/fonts/FontRoleGroup.java src/nurgling/fonts/FontEffect.java src/nurgling/fonts/FontRoleConfig.java src/nurgling/fonts/FontRole.java src/nurgling/fonts/ResolvedFontStyle.java test/nurgling/fonts/FontRoleTest.java
git commit -m "feat: define semantic font roles"
```

### Task 2: Add versioned persistence and legacy migration

**Files:**
- Modify: `src/nurgling/conf/FontSettings.java`
- Modify: `src/nurgling/NConfig.java`
- Modify: `src/nurgling/conf/ItemQualityOverlaySettings.java`
- Test: `test/nurgling/conf/FontSettingsTest.java`
- Create: `test/nurgling/conf/FontSettingsMigrationTest.java`

**Interfaces:**
- Consumes: `FontRole`, `FontRoleConfig`.
- Produces: `FontSettings.config(FontRole)`, `FontSettings.set(FontRole, FontRoleConfig)`, `FontSettings.roles()`, `FontSettings.CURRENT_SCHEMA = 2`, and idempotent legacy migration.

- [ ] **Step 1: Write failing JSON and migration tests**

```java
@Test void roleSettingsRoundTripThroughJson() {
    FontSettings src = new FontSettings();
    FontRoleConfig chat = src.config(FontRole.CHAT).copy();
    chat.family = "Roboto";
    chat.size = 15;
    src.set(FontRole.CHAT, chat);
    FontSettings dst = new FontSettings(src.toJson().toMap());
    assertEquals("Roboto", dst.config(FontRole.CHAT).family);
    assertEquals(15, dst.config(FontRole.CHAT).size);
}

@Test void legacyFieldsSeedSemanticRoles() {
    Map<String, Object> legacy = legacyFontMap("Serif", "Open Sans Semibold", "Roboto", "Sans", "Open Sans");
    FontSettings migrated = new FontSettings(legacy);
    assertEquals("Serif", migrated.config(FontRole.SYSTEM).family);
    assertEquals("Open Sans Semibold", migrated.config(FontRole.WINDOW_TITLE).family);
    assertEquals("Roboto", migrated.config(FontRole.QUEST_TITLE).family);
    assertEquals("Sans", migrated.config(FontRole.OBJECT_LABEL).family);
    assertEquals("Open Sans", migrated.config(FontRole.CHARACTER_NAME).family);
}

private static Map<String, Object> legacyFontMap(String system, String ui, String quests,
                                                  String barrels, String characters) {
    Map<String, Object> map = new HashMap<>();
    map.put("default", legacyConfig(system, 12));
    map.put("ui", legacyConfig(ui, 12));
    map.put("quests", legacyConfig(quests, 12));
    map.put("barrels", legacyConfig(barrels, 12));
    map.put("characters", legacyConfig(characters, 12));
    return map;
}

private static Map<String, Object> legacyConfig(String family, int size) {
    Map<String, Object> cfg = new HashMap<>();
    Map<String, Object> color = new HashMap<>();
    color.put("red", 0);
    color.put("green", 0);
    color.put("blue", 0);
    color.put("alpha", 255);
    cfg.put("family", family);
    cfg.put("size", size);
    cfg.put("isColorable", false);
    cfg.put("color", color);
    return cfg;
}

@Test void migrationIsIdempotent() {
    FontSettings once = new FontSettings(legacyFontMap("Sans", "Open Sans Semibold", "Sans", "Sans", "Sans"));
    FontSettings twice = new FontSettings(once.toJson().toMap());
    assertEquals(once.toJson().toString(), twice.toJson().toString());
}
```

Add a second fixture using the six `NConfig.Key` item-overlay settings and assert that family, size, default color, and background/effect are copied into the six item roles.

- [ ] **Step 2: Run tests and verify RED**

Run: `ant test`

Expected: tests fail because semantic role persistence and migration APIs are absent.

- [ ] **Step 3: Implement schema 2 persistence**

Store JSON as:

```json
{
  "type": "FontSettings",
  "schema": 2,
  "roles": {
    "SYSTEM": {"family":"Sans","size":12,"style":0,"effect":"NONE","inherit":false,"overrideColor":false},
    "WINDOW_TITLE": {"family":"Open Sans Semibold","size":12,"style":0,"effect":"NONE","inherit":false,"overrideColor":false}
  }
}
```

Deserialize by enum name, ignore unknown future roles, fill missing roles from `FontRole.defaults()`, clamp sizes to `8..24`, validate AWT style/effect values, and retain legacy fields only as migration input. Add `FontSettings.migrateItemOverlays(Map<NConfig.Key, ItemQualityOverlaySettings>)` and invoke it once after `NConfig` has loaded both fonts and item settings.

- [ ] **Step 4: Run tests and verify GREEN**

Run: `ant test`

Expected: persistence, migration, and existing configuration tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/nurgling/conf/FontSettings.java src/nurgling/NConfig.java src/nurgling/conf/ItemQualityOverlaySettings.java test/nurgling/conf/FontSettingsTest.java test/nurgling/conf/FontSettingsMigrationTest.java
git commit -m "feat: migrate font settings to semantic roles"
```

### Task 3: Implement the runtime font theme and revision invalidation

**Files:**
- Create: `src/nurgling/fonts/FontFamilyRegistry.java`
- Create: `src/nurgling/fonts/FontTheme.java`
- Modify: `src/nurgling/conf/FontSettings.java`
- Modify: `src/haven/Widget.java`
- Modify: `src/nurgling/NUI.java`
- Test: `test/nurgling/fonts/FontThemeTest.java`

**Interfaces:**
- Consumes: role configuration from Task 2.
- Produces: `FontTheme.current()`, `FontTheme.install(FontSettings)`, `FontTheme.registerRoot(Widget)`, `FontTheme.unregisterRoot(Widget)`, `FontTheme.revision()`, `resolve(FontRole)`, `font(FontRole)`, `foundry(FontRole)`, `foundry(FontRole, Color)`, `foundry(FontRole, int, Color)`, `furnace(FontRole, Color)`, and `richFoundry(FontRole)`.
- Produces: `Widget.fontThemeChanged(long revision)` recursively notifies existing widget trees.

- [ ] **Step 1: Write the failing resolver test**

```java
@Test void inheritedRoleTracksParentAndRevisionChanges() {
    FontSettings settings = new FontSettings();
    FontRoleConfig objective = settings.config(FontRole.QUEST_OBJECTIVE).copy();
    objective.inherit = true;
    settings.set(FontRole.QUEST_OBJECTIVE, objective);
    FontTheme.install(settings);
    long before = FontTheme.revision();

    FontRoleConfig system = settings.config(FontRole.SYSTEM).copy();
    system.family = "Roboto";
    system.size = 13;
    settings.set(FontRole.SYSTEM, system);
    FontTheme.install(settings);

    assertTrue(FontTheme.revision() > before);
    assertEquals("Roboto", FontTheme.resolve(FontRole.QUEST_OBJECTIVE).family);
    assertEquals(13, FontTheme.resolve(FontRole.QUEST_OBJECTIVE).size);
}

@Test void malformedFamilyFallsBackWithoutThrowing() {
    FontSettings settings = new FontSettings();
    FontRoleConfig cfg = settings.config(FontRole.SYSTEM).copy();
    cfg.family = "missing-family";
    settings.set(FontRole.SYSTEM, cfg);
    assertDoesNotThrow(() -> FontTheme.install(settings));
    assertNotNull(FontTheme.foundry(FontRole.SYSTEM).font);
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `ant test`

Expected: compilation fails because `FontTheme` is absent.

- [ ] **Step 3: Implement family loading and resolution**

Move bundled family loading out of `FontSettings` into `FontFamilyRegistry`. Expose exact labels `Roboto`, `Open Sans`, `Open Sans Semibold`, `Sans`, `Serif`, `Fraktur`, and `Monospaced`. Relabel the current Helvetica resource as `Helvetica`; do not expose it as `Inter` unless a real Inter resource is added.

Resolve inheritance with a visited `EnumSet<FontRole>` and fall back to the child shipped default if a cycle is detected. Cache foundries by `(revision, role, sizeDelta, color, effect)` and apply `UI.scale` once.

- [ ] **Step 4: Add recursive widget invalidation**

Add to `Widget`:

```java
public void fontThemeChanged(long revision) {
    for (Widget w = child; w != null; w = w.next)
        w.fontThemeChanged(revision);
}
```

`NUI` registers its root after construction and unregisters it on destroy. `FontTheme` keeps weak root references. `FontTheme.install` increments the revision and dispatches `root.fontThemeChanged(revision)` on each registered UI thread. Do not call widget code from settings deserialization.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `ant test`

Expected: resolver, fallback, revision, and existing tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/nurgling/fonts/FontFamilyRegistry.java src/nurgling/fonts/FontTheme.java src/nurgling/conf/FontSettings.java src/haven/Widget.java src/nurgling/NUI.java test/nurgling/fonts/FontThemeTest.java
git commit -m "feat: add live semantic font theme"
```

### Task 4: Replace the font settings panel with grouped role editing

**Files:**
- Create: `src/nurgling/widgets/nsettings/FontSettingsCatalog.java`
- Create: `src/nurgling/widgets/nsettings/FontRoleEditor.java`
- Rewrite: `src/nurgling/widgets/nsettings/Fonts.java`
- Modify: `src/nurgling/widgets/nsettings/ItemOverlaySettings.java`
- Modify: `src/nurgling/widgets/NSettingsWindow.java`
- Modify: `src/nurgling/widgets/SettingsSearch.java`
- Modify: `src/lang/messages.properties`
- Modify: `src/lang/messages_ru.properties`
- Test: `test/nurgling/widgets/nsettings/FontSettingsCatalogTest.java`

**Interfaces:**
- Consumes: role configuration and `FontTheme.install`.
- Produces: grouped catalog containing every role exactly once and reusable `FontRoleEditor` bound to one `FontRoleConfig`.

- [ ] **Step 1: Write the failing catalog test**

```java
@Test void catalogContainsEveryRoleExactlyOnceInSpecOrder() {
    List<FontRole> flattened = FontSettingsCatalog.groups().stream()
        .flatMap(group -> group.roles.stream())
        .collect(Collectors.toList());
    assertEquals(EnumSet.allOf(FontRole.class), EnumSet.copyOf(flattened));
    assertEquals(flattened.size(), new HashSet<>(flattened).size());
    assertEquals(FontRole.SYSTEM, flattened.get(0));
    assertEquals(FontRole.CONSOLE, flattened.get(flattened.size() - 1));
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `ant test`

Expected: compilation fails because `FontSettingsCatalog` is absent.

- [ ] **Step 3: Implement the grouped UI**

Render seven collapsible groups matching `FontRoleGroup`. Each role row contains localized name, inheritance checkbox, live preview, and edit button. `FontRoleEditor` exposes family, size, plain/bold/italic style, optional color override, optional effect, reset-role, and reset-group actions. Saving clones all editor values into one `FontSettings`, writes `NConfig.Key.fonts`, then calls `FontTheme.install(saved)`.

The top row has shipped-default reset, “one font everywhere”, export JSON, and import JSON. Export places `FontSettings.toJson().toString(2)` into `ui.wnd.clipboard(Clipboard.Std.CLIPBOARD)` using `Clipboard.Item<CharSequence>`. Import reads text through `ReadLine.PCLine.cliptext(...)`, validates it by constructing `FontSettings`, replaces editor state only on success, and reports malformed JSON through `ui.error` without changing the saved theme.

Remove the old single `FontType` selector. In `ItemOverlaySettings`, remove family/size controls for quality, stack, amount, study, progress, and volume while keeping enable, corner, thresholds, colors, backgrounds, and time formats. Add localized text directing users to the shared Fonts panel.

- [ ] **Step 4: Add localization and settings search entries**

Add English and Russian keys for seven groups, 24 roles, inherit, family, size, style, color override, effect, preview, reset role/group/all, import/export, and “font is configured in Fonts”. Register every role label and alias with `SettingsSearch` so searches such as “chat font”, “шрифт шкалы”, and “tooltip” open and scroll to the correct row.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `ant test`

Expected: catalog/search tests and the full suite pass.

- [ ] **Step 6: Commit**

```bash
git add src/nurgling/widgets/nsettings/FontSettingsCatalog.java src/nurgling/widgets/nsettings/FontRoleEditor.java src/nurgling/widgets/nsettings/Fonts.java src/nurgling/widgets/nsettings/ItemOverlaySettings.java src/nurgling/widgets/NSettingsWindow.java src/nurgling/widgets/SettingsSearch.java src/lang/messages.properties src/lang/messages_ru.properties test/nurgling/widgets/nsettings/FontSettingsCatalogTest.java
git commit -m "feat: group semantic font settings"
```

### Task 5: Migrate generic text primitives and legacy RichText

**Files:**
- Modify: `src/haven/Text.java`
- Modify: `src/haven/RichText.java`
- Modify: `src/haven/Label.java`
- Modify: `src/haven/ILabel.java`
- Modify: `src/haven/Button.java`
- Modify: `src/haven/TextEntry.java`
- Modify: `src/haven/Textlog.java`
- Modify: `src/haven/RichTextBox.java`
- Modify: `src/haven/FlowerMenu.java`
- Modify: `src/haven/SListMenu.java`
- Modify: `src/haven/Window.java`
- Modify: `src/nurgling/NWindowDeco.java`
- Modify: `src/haven/RootWidget.java`
- Modify: `src/haven/HelpWnd.java`
- Modify: `src/haven/FastText.java`
- Modify: `src/haven/LoginScreen.java`
- Modify: `src/haven/Charlist.java`
- Modify: `src/haven/GridList.java`
- Modify: `src/haven/MenuGrid.java`
- Modify: `src/haven/ISBox.java`
- Test: `test/haven/SemanticFontPrimitiveTest.java`
- Test: `test/nurgling/fonts/RichTextFontRoleTest.java`

**Interfaces:**
- Consumes: `FontTheme` and revision notification.
- Produces: role-aware overloads `Text.render(FontRole, String, Color)`, `RichText.render(FontRole, String, int, Object...)`, `new Label(String, FontRole)`, and revision-aware cached text in core widgets.

- [ ] **Step 1: Write failing primitive routing tests**

```java
@Test void genericWidgetsUseSystemAndWindowCaptionUsesWindowTitle() {
    assertEquals(FontRole.SYSTEM, Label.DEFAULT_FONT_ROLE);
    assertEquals(FontRole.SYSTEM, Button.DEFAULT_FONT_ROLE);
    assertEquals(FontRole.SYSTEM, TextEntry.DEFAULT_FONT_ROLE);
    assertEquals(FontRole.SYSTEM, FlowerMenu.DEFAULT_FONT_ROLE);
    assertEquals(FontRole.WINDOW_TITLE, NWindowDeco.TITLE_FONT_ROLE);
}

@Test void richTextDefaultsToSystemAndMapsLegacyFamilies() {
    assertEquals(FontRole.SYSTEM, RichText.DEFAULT_FONT_ROLE);
    assertEquals(FontRole.SYSTEM, RichText.roleForLegacyFamily("SansSerif"));
    assertEquals(FontRole.HEADING, RichText.roleForLegacyFamily("serif"));
    assertEquals(FontRole.WINDOW_TITLE, RichText.roleForLegacyFamily("fraktur"));
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `ant test`

Expected: compilation fails because default role constants and overloads are absent.

- [ ] **Step 3: Route core primitives through roles**

Make default `Label`, `Button`, `TextEntry`, menu/list, help, login and generic text rendering use `SYSTEM`; window captions use `WINDOW_TITLE`; shortcut text uses `HOTKEY`; secondary menu metadata uses `SECONDARY`. Keep `Text.mono` only as a compatibility alias for `CONSOLE`.

Each widget storing a rendered `Text`/`Tex` also stores `fontRevision`; override `fontThemeChanged` to dispose its texture, render again, resize from new metrics, and call `pack`/parent relayout only when dimensions changed.

- [ ] **Step 4: Route RichText and compatibility families**

Change RichText default attributes from raw `TextAttribute.FAMILY = "SansSerif"` to the resolved `SYSTEM` font. Add role-aware render overloads. Map legacy family directives as asserted above; preserve explicitly embedded resource fonts only for non-UI artwork and previews.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `ant test`

Expected: primitive/RichText tests pass and existing UI tests remain green.

- [ ] **Step 6: Commit**

```bash
git add src/haven/Text.java src/haven/RichText.java src/haven/Label.java src/haven/ILabel.java src/haven/Button.java src/haven/TextEntry.java src/haven/Textlog.java src/haven/RichTextBox.java src/haven/FlowerMenu.java src/haven/SListMenu.java src/haven/Window.java src/nurgling/NWindowDeco.java src/haven/RootWidget.java src/haven/HelpWnd.java src/haven/FastText.java src/haven/LoginScreen.java src/haven/Charlist.java src/haven/GridList.java src/haven/MenuGrid.java src/haven/ISBox.java test/haven/SemanticFontPrimitiveTest.java test/nurgling/fonts/RichTextFontRoleTest.java
git commit -m "refactor: route core text through font roles"
```

### Task 6: Migrate chat, quests, windows, descriptions, and tooltips

**Files:**
- Modify: `src/haven/ChatUI.java`
- Modify: `src/nurgling/NChatUI.java`
- Modify: `src/haven/QuestWnd.java`
- Modify: `src/nurgling/NQuestWnd.java`
- Modify: `src/nurgling/widgets/NQuestInfo.java`
- Modify: `src/nurgling/NQuestBox.java`
- Modify: `src/nurgling/widgets/QuestHeadingFont.java`
- Modify: `src/haven/CharWnd.java`
- Modify: `src/nurgling/NSAttrWnd.java`
- Modify: `src/haven/SkillWnd.java`
- Modify: `src/nurgling/NSkillWnd.java`
- Modify: `src/haven/WoundWnd.java`
- Modify: `src/nurgling/NWoundBox.java`
- Modify: `src/haven/FightWnd.java`
- Modify: `src/nurgling/NFightWnd.java`
- Modify: `src/nurgling/NInventory.java`
- Modify: `src/nurgling/widgets/NMakewindow.java`
- Modify: `src/nurgling/NTooltip.java`
- Modify: `src/nurgling/NRecipeTooltip.java`
- Modify: `src/nurgling/styles/TooltipStyle.java`
- Modify: `src/nurgling/iteminfo/NCuriosity.java`
- Modify: `src/nurgling/iteminfo/NFoodInfo.java`
- Modify: `src/nurgling/iteminfo/NKilnInfo.java`
- Modify: `src/nurgling/widgets/AgentTextlog.java`
- Modify: `src/nurgling/widgets/NTextArea.java`
- Test: `test/nurgling/fonts/FeatureFontRoutingTest.java`

**Interfaces:**
- Consumes: core role-aware render APIs.
- Produces: explicit role constants on representative feature roots and revision-aware rebuilding of their cached textures/layouts.

- [ ] **Step 1: Write failing feature-role tests**

```java
@Test void communicationAndQuestRolesMatchTheDesign() {
    assertEquals(FontRole.CHAT, ChatUI.FONT_ROLE);
    assertEquals(FontRole.CHAT, ChatUI.INPUT_FONT_ROLE);
    assertEquals(FontRole.QUEST_TITLE, NQuestInfo.TITLE_FONT_ROLE);
    assertEquals(FontRole.QUEST_OBJECTIVE, NQuestInfo.OBJECTIVE_FONT_ROLE);
}

@Test void tooltipRolesSeparateTitlesFromBodies() {
    assertEquals(FontRole.HEADING, TooltipStyle.TITLE_FONT_ROLE);
    assertEquals(FontRole.SYSTEM, TooltipStyle.BODY_FONT_ROLE);
    assertEquals(FontRole.SECONDARY, TooltipStyle.TECHNICAL_FONT_ROLE);
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `ant test`

Expected: tests fail because feature role constants are absent.

- [ ] **Step 3: Migrate communication and quests**

Add `TextEntry` constructors accepting a `FontRole`; generic input defaults to `SYSTEM`, while ChatUI passes `CHAT`. Use `CHAT` for message body, sender name, channel name and both normal/quick input. Keep timestamp/sender colors. Use `QUEST_TITLE` for group/title/badge text and `QUEST_OBJECTIVE` for objective/progress/distance rows. Use `SYSTEM` for quest descriptions and `WINDOW_TITLE` for captions. On revision changes, rebuild chat line caches, selector labels, quest row textures and row heights.

- [ ] **Step 4: Migrate content windows and tooltips**

Use `HEADING` for item/skill/wound/recipe names and section headings; `SYSTEM` for prose, attributes and normal tooltip lines; `SECONDARY` for resource paths/hints. Remove direct Open Sans loading from `TooltipStyle`, `NTooltip`, `NRecipeTooltip`, `NQuestBox`, `NSkillWnd`, `NFightWnd`, and `NWoundBox` in favor of `FontTheme`.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `ant test`

Expected: feature routing tests pass; quest Cyrillic and tooltip tests remain green.

- [ ] **Step 6: Commit**

```bash
git add src/haven/ChatUI.java src/haven/QuestWnd.java src/haven/CharWnd.java src/haven/SkillWnd.java src/haven/WoundWnd.java src/haven/FightWnd.java src/nurgling/NChatUI.java src/nurgling/NQuestWnd.java src/nurgling/widgets/NQuestInfo.java src/nurgling/NQuestBox.java src/nurgling/widgets/QuestHeadingFont.java src/nurgling/NSAttrWnd.java src/nurgling/NSkillWnd.java src/nurgling/NWoundBox.java src/nurgling/NFightWnd.java src/nurgling/NInventory.java src/nurgling/widgets/NMakewindow.java src/nurgling/NTooltip.java src/nurgling/NRecipeTooltip.java src/nurgling/styles/TooltipStyle.java src/nurgling/iteminfo/NCuriosity.java src/nurgling/iteminfo/NFoodInfo.java src/nurgling/iteminfo/NKilnInfo.java src/nurgling/widgets/AgentTextlog.java src/nurgling/widgets/NTextArea.java test/nurgling/fonts/FeatureFontRoutingTest.java
git commit -m "refactor: apply font roles to chat quests and tooltips"
```

### Task 7: Unify item overlay font configuration

**Files:**
- Create: `src/nurgling/fonts/ItemOverlayFontRoles.java`
- Modify: `src/haven/GItem.java`
- Modify: `src/haven/res/ui/tt/q/quality/Quality.java`
- Modify: `src/haven/res/ui/tt/stackn/Stack.java`
- Modify: `src/haven/res/ui/tt/cn/CustomName.java`
- Modify: `src/haven/res/ui/tt/drying/Drying.java`
- Modify: `src/haven/res/ui/tt/expire/Expiring.java`
- Modify: `src/haven/res/ui/tt/slots/ISlots.java`
- Modify: `src/nurgling/iteminfo/NCuriosity.java`
- Test: `test/nurgling/fonts/ItemOverlayFontRolesTest.java`

**Interfaces:**
- Produces: `ItemOverlayFontRoles.forKey(NConfig.Key)` mapping the six existing overlay keys to semantic roles.

- [ ] **Step 1: Write the failing mapping test**

```java
@Test void everyItemOverlayKeyHasItsOwnRole() {
    assertEquals(FontRole.ITEM_QUALITY, ItemOverlayFontRoles.forKey(NConfig.Key.itemQualityOverlay));
    assertEquals(FontRole.ITEM_STACK, ItemOverlayFontRoles.forKey(NConfig.Key.stackQualityOverlay));
    assertEquals(FontRole.ITEM_AMOUNT, ItemOverlayFontRoles.forKey(NConfig.Key.amountOverlay));
    assertEquals(FontRole.ITEM_STUDY, ItemOverlayFontRoles.forKey(NConfig.Key.studyInfoOverlay));
    assertEquals(FontRole.ITEM_PROGRESS, ItemOverlayFontRoles.forKey(NConfig.Key.progressOverlay));
    assertEquals(FontRole.ITEM_VOLUME, ItemOverlayFontRoles.forKey(NConfig.Key.volumeOverlay));
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `ant test`

Expected: compilation fails because `ItemOverlayFontRoles` is absent.

- [ ] **Step 3: Route item rendering through the six roles**

Replace per-overlay `FontSettings.getFont(settings.fontFamily)` logic with `FontTheme.foundry(role, semanticColor)`. Keep corner, thresholds, visibility, background and time-format settings in `ItemQualityOverlaySettings`. Compare theme revision in overlay caches so visible inventory items rerender immediately.

- [ ] **Step 4: Run tests and verify GREEN**

Run: `ant test`

Expected: item role mapping, overlay serialization, and rendering logic tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/nurgling/fonts/ItemOverlayFontRoles.java src/haven/GItem.java src/haven/res/ui/tt/q/quality/Quality.java src/haven/res/ui/tt/stackn/Stack.java src/haven/res/ui/tt/cn/CustomName.java src/haven/res/ui/tt/drying/Drying.java src/haven/res/ui/tt/expire/Expiring.java src/haven/res/ui/tt/slots/ISlots.java src/nurgling/iteminfo/NCuriosity.java test/nurgling/fonts/ItemOverlayFontRolesTest.java
git commit -m "refactor: centralize item overlay fonts"
```

### Task 8: Migrate HUD, meters, combat, notifications, and session UI

**Files:**
- Modify: `src/nurgling/NStyle.java`
- Modify: `src/haven/IMeter.java`
- Modify: `src/nurgling/widgets/DrinkMeter.java`
- Modify: `src/nurgling/widgets/NCal.java`
- Modify: `src/nurgling/widgets/compass/NCompassBar.java`
- Modify: `src/nurgling/widgets/AllowVisitingStatusBuff.java`
- Modify: `src/nurgling/widgets/CrimeStatusBuff.java`
- Modify: `src/nurgling/widgets/SwimmingStatusBuff.java`
- Modify: `src/nurgling/widgets/TrackingStatusBuff.java`
- Modify: `src/nurgling/widgets/NAlarmWdg.java`
- Modify: `src/nurgling/widgets/NDraggableWidget.java`
- Modify: `src/haven/Buff.java`
- Modify: `src/haven/Fightsess.java`
- Modify: `src/haven/NFightsess.java`
- Modify: `src/nurgling/NFightWnd.java`
- Modify: `src/nurgling/sessions/SessionTabBar.java`
- Modify: `src/nurgling/widgets/NTabStrip.java`
- Test: `test/nurgling/fonts/HudFontRoutingTest.java`

**Interfaces:**
- Produces: `NCal.FONT_ROLE = HUD_INFO`, `IMeter.FONT_ROLE = METER`, `NFightsess.FONT_ROLE = COMBAT_UI`, and role-aware `NStyle` accessors replacing static foundry fields.

- [ ] **Step 1: Write the failing HUD role test**

```java
@Test void hudUsesPurposeSpecificRoles() {
    assertEquals(FontRole.HUD_INFO, NCal.FONT_ROLE);
    assertEquals(FontRole.METER, IMeter.FONT_ROLE);
    assertEquals(FontRole.METER, DrinkMeter.FONT_ROLE);
    assertEquals(FontRole.COMBAT_UI, NFightsess.FONT_ROLE);
    assertEquals(FontRole.HOTKEY, NStyle.HOTKEY_FONT_ROLE);
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `ant test`

Expected: tests fail because HUD role constants are absent.

- [ ] **Step 3: Replace static HUD foundries with theme accessors**

Convert `NStyle.meter`, `gmeter`, `cmeter`, `hotkey`, openings, IP and slot-number foundries into methods that obtain revision-cached `FontTheme` furnaces. Calendar/status uses `HUD_INFO`; all four meter types and drink tooltip body use `METER`/`SYSTEM`; hotkeys use `HOTKEY`; openings/IP/combat values use `COMBAT_UI`; notifications and alarms use `NOTIFICATION`.

- [ ] **Step 4: Add live cache rebuilding**

Invalidate `NCal.CachedLine`, `IMeter.text`, `DrinkMeter.text/cachedTip`, session-tab labels, compass captions, buff labels, combat textures and notification textures on revision changes. Recompute row/bar centering from new metrics.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `ant test`

Expected: HUD routing tests pass and the full suite remains green.

- [ ] **Step 6: Commit**

```bash
git add src/nurgling/NStyle.java src/haven/IMeter.java src/nurgling/widgets/DrinkMeter.java src/nurgling/widgets/NCal.java src/nurgling/widgets/compass/NCompassBar.java src/nurgling/widgets/AllowVisitingStatusBuff.java src/nurgling/widgets/CrimeStatusBuff.java src/nurgling/widgets/SwimmingStatusBuff.java src/nurgling/widgets/TrackingStatusBuff.java src/nurgling/widgets/NAlarmWdg.java src/nurgling/widgets/NDraggableWidget.java src/haven/Buff.java src/haven/Fightsess.java src/haven/NFightsess.java src/nurgling/NFightWnd.java src/nurgling/sessions/SessionTabBar.java src/nurgling/widgets/NTabStrip.java test/nurgling/fonts/HudFontRoutingTest.java
git commit -m "refactor: apply font roles to hud and combat"
```

### Task 9: Migrate world, character, object, map, navigation, and floating text

**Files:**
- Modify: `src/haven/MapView.java`
- Modify: `src/haven/Polity.java`
- Modify: `src/haven/res/gfx/fx/floatimg/FloatText.java`
- Modify: `src/haven/res/ui/obj/buddy/InfoPart.java`
- Modify: `src/nurgling/NMapView.java`
- Modify: `src/nurgling/widgets/NMiniMap.java`
- Modify: `src/nurgling/widgets/NMapWnd.java`
- Modify: `src/nurgling/widgets/LabeledMinimapMark.java`
- Modify: `src/nurgling/widgets/NLayoutPicker.java`
- Modify: `src/nurgling/widgets/LocalizedResourceTimersWindow.java`
- Modify: `src/nurgling/widgets/NCharTagStrip.java`
- Modify: `src/nurgling/widgets/NCharTagsWnd.java`
- Modify: `src/nurgling/widgets/NBuddyWnd.java`
- Modify: `src/nurgling/overlays/NBarrelOverlay.java`
- Modify: `src/nurgling/overlays/NDMGOverlay.java`
- Modify: `src/nurgling/overlays/NCombatHpBar.java`
- Modify: `src/nurgling/overlays/NCustomResult.java`
- Modify: `src/nurgling/overlays/NCheckResult.java`
- Modify: `src/nurgling/overlays/NGobHealthOverlay.java`
- Modify: `src/nurgling/overlays/NGobConfigLabel.java`
- Modify: `src/nurgling/overlays/NQuestGiver.java`
- Modify: `src/nurgling/overlays/NSpeedometerOverlay.java`
- Modify: `src/nurgling/overlays/NStorageTrailOverlay.java`
- Modify: `src/nurgling/overlays/NZoneMeasureLabelSprite.java`
- Modify: `src/nurgling/overlays/NWaypointOverlay.java`
- Modify: `src/nurgling/overlays/NTreeScaleOl.java`
- Modify: `src/nurgling/overlays/QualityOl.java`
- Modify: `src/nurgling/overlays/QuarryartzTileOverlay.java`
- Modify: `src/nurgling/widgets/GobConfigWindow.java`
- Test: `test/nurgling/fonts/WorldFontRoutingTest.java`

**Interfaces:**
- Produces: explicit role constants for map labels, character names, object labels, navigation labels and floating values.

- [ ] **Step 1: Write the failing world-role test**

```java
@Test void worldTextIsSeparatedByMeaning() {
    assertEquals(FontRole.MAP_LABEL, LabeledMinimapMark.FONT_ROLE);
    assertEquals(FontRole.OBJECT_LABEL, NBarrelOverlay.FONT_ROLE);
    assertEquals(FontRole.NAVIGATION_LABEL, NWaypointOverlay.FONT_ROLE);
    assertEquals(FontRole.FLOATING_VALUE, NDMGOverlay.FONT_ROLE);
    assertEquals(FontRole.CHARACTER_NAME, NCharTagStrip.FONT_ROLE);
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `ant test`

Expected: tests fail because world role constants are absent.

- [ ] **Step 3: Migrate map, identity and object labels**

Use `MAP_LABEL` for map/minimap marker/search/political labels; `CHARACTER_NAME` for player/kin/buddy/party labels; `OBJECT_LABEL` for barrels, containers, quest givers, area titles and configured gob labels. Preserve existing feature colors and outlines.

- [ ] **Step 4: Migrate navigation and floating values**

Use `NAVIGATION_LABEL` for waypoint ETA, storage trails and zone measurements. Use `FLOATING_VALUE` for damage, gob HP, quality, speed, tree scale, quarry checks and generic world results. Dispose/rebuild cached textures when the theme revision changes.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `ant test`

Expected: world routing tests pass and all existing overlay/map tests remain green.

- [ ] **Step 6: Commit**

```bash
git add src/haven/MapView.java src/haven/Polity.java src/haven/res/gfx/fx/floatimg/FloatText.java src/haven/res/ui/obj/buddy/InfoPart.java src/nurgling/NMapView.java src/nurgling/widgets/NMiniMap.java src/nurgling/widgets/NMapWnd.java src/nurgling/widgets/LabeledMinimapMark.java src/nurgling/widgets/NLayoutPicker.java src/nurgling/widgets/LocalizedResourceTimersWindow.java src/nurgling/widgets/NCharTagStrip.java src/nurgling/widgets/NCharTagsWnd.java src/nurgling/widgets/NBuddyWnd.java src/nurgling/overlays/NBarrelOverlay.java src/nurgling/overlays/NDMGOverlay.java src/nurgling/overlays/NCombatHpBar.java src/nurgling/overlays/NCustomResult.java src/nurgling/overlays/NCheckResult.java src/nurgling/overlays/NGobHealthOverlay.java src/nurgling/overlays/NGobConfigLabel.java src/nurgling/overlays/NQuestGiver.java src/nurgling/overlays/NSpeedometerOverlay.java src/nurgling/overlays/NStorageTrailOverlay.java src/nurgling/overlays/NZoneMeasureLabelSprite.java src/nurgling/overlays/NWaypointOverlay.java src/nurgling/overlays/NTreeScaleOl.java src/nurgling/overlays/QualityOl.java src/nurgling/overlays/QuarryartzTileOverlay.java src/nurgling/widgets/GobConfigWindow.java test/nurgling/fonts/WorldFontRoutingTest.java
git commit -m "refactor: apply font roles to world and map text"
```

### Task 10: Migrate console/debug text and enforce complete coverage

**Files:**
- Modify: `src/haven/ConsoleHost.java`
- Modify: `src/nurgling/NMapView.java`
- Modify: `src/nurgling/tools/MarkdownToImageRenderer.java`
- Modify: `src/nurgling/widgets/bots/MasterMinerWnd.java`
- Modify: `src/nurgling/widgets/bots/FishingTarget.java`
- Modify: `src/nurgling/widgets/NCatSelection.java`
- Modify: `src/nurgling/widgets/NSearchWidget.java`
- Modify: `src/nurgling/widgets/NEquipory.java`
- Modify: `src/nurgling/widgets/StudyDeskPlannerWidget.java`
- Modify: `src/nurgling/widgets/StudyDeskInventoryExtension.java`
- Modify: `src/nurgling/equipment/EquipmentPresetIcons.java`
- Modify: `src/nurgling/contextmenu/MacroPetalBadge.java`
- Modify: `src/nurgling/scenarios/ScenarioIcons.java`
- Modify: `src/nurgling/NSteamBoot.java`
- Create: `test/nurgling/fonts/FontRoutingAuditTest.java`

**Interfaces:**
- Consumes: every role and rendering API.
- Produces: a repository-wide guard against unmanaged user-visible font creation.

- [ ] **Step 1: Write the failing source audit**

```java
@Test void userVisibleFontsCannotBypassFontTheme() throws IOException {
    Pattern unmanaged = Pattern.compile(
        "new\\s+(?:Text|RichText)\\.Foundry|new\\s+Font\\s*\\(|Text\\.(?:sans|serif|dfont|fraktur)"
    );
    Set<Path> allow = new HashSet<>(Arrays.asList(
        Paths.get("src/nurgling/fonts/FontFamilyRegistry.java"),
        Paths.get("src/nurgling/fonts/FontTheme.java"),
        Paths.get("src/haven/Text.java"),
        Paths.get("src/haven/RichText.java")
    ));
    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(Paths.get("src"))) {
        paths.filter(p -> p.toString().endsWith(".java"))
             .filter(p -> !allow.contains(p))
             .forEach(p -> collectMatches(unmanaged, p, violations));
    }
    assertEquals(Collections.emptyList(), violations);
}
```

Keep `collectMatches` in the test class; report `path:line:text`. Add a narrowly documented allowlist only for non-user-visible renderer internals or preview/test fixtures.

- [ ] **Step 2: Run the test and verify RED**

Run: `ant test`

Expected: `FontRoutingAuditTest` reports the remaining direct font sites with file and line.

- [ ] **Step 3: Route the remaining sites**

Use `CONSOLE` for console, command and inspection/debug output; map remaining visible widgets to the closest semantic role. For icon-generated letters/numbers, use `HOTKEY`, `SECONDARY`, or `FLOATING_VALUE` according to their meaning. Do not silence a visible violation with an allowlist.

- [ ] **Step 4: Run the audit and full tests**

Run: `ant test`

Expected: no unmanaged font violations and all JUnit tests pass.

- [ ] **Step 5: Build the client**

Run: `ant bin`

Expected: build succeeds and produces `bin/hafen.jar` without Java compilation errors.

- [ ] **Step 6: Perform the manual visual matrix**

At normal and increased UI scale, change each role to a deliberately distinct family/size and verify only its intended surfaces change. Cover: generic settings controls, window titles, tooltip title/body/resource path, chat input/sent message/channel, notification, calendar, all meters, hotkeys, combat, quest title/objective, all six item overlays, character name, barrel/object label, map/minimap label, waypoint/measurement, floating damage/HP, and console. Reset to shipped defaults and confirm the result remains close to the pre-change client.

- [ ] **Step 7: Commit**

```bash
git add src/haven/ConsoleHost.java src/nurgling/NMapView.java src/nurgling/tools/MarkdownToImageRenderer.java src/nurgling/widgets/bots/MasterMinerWnd.java src/nurgling/widgets/bots/FishingTarget.java src/nurgling/widgets/NCatSelection.java src/nurgling/widgets/NSearchWidget.java src/nurgling/widgets/NEquipory.java src/nurgling/widgets/StudyDeskPlannerWidget.java src/nurgling/widgets/StudyDeskInventoryExtension.java src/nurgling/equipment/EquipmentPresetIcons.java src/nurgling/contextmenu/MacroPetalBadge.java src/nurgling/scenarios/ScenarioIcons.java src/nurgling/NSteamBoot.java test/nurgling/fonts/FontRoutingAuditTest.java
git commit -m "test: enforce semantic font routing"
```

### Task 11: Final regression verification and documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-09-07-semantic-font-settings-design.md` only if implementation details differ from the approved design
- Create: `docs/font-settings.md`

**Interfaces:**
- Produces: user-facing role reference and verified release artifact.

- [ ] **Step 1: Write the user guide**

Document every group/role, shipped default, inheritance behavior, live preview/save/reset, migration, and which role controls tooltip headings versus bodies. Include the exact location in Nurgling Settings and note that item layout/color thresholds remain in Item Overlay Settings.

- [ ] **Step 2: Run clean verification**

Run: `ant clean test bin`

Expected: all tests pass and `bin/hafen.jar` is rebuilt successfully.

- [ ] **Step 3: Inspect the final diff**

Run: `git status --short` and `git diff --check 65ba861..HEAD`

Expected: only font-system work plus pre-existing unrelated user changes are present; no whitespace errors, generated caches, or accidental resource files are added.

- [ ] **Step 4: Commit documentation**

```bash
git add docs/font-settings.md docs/superpowers/specs/2026-09-07-semantic-font-settings-design.md
git commit -m "docs: explain semantic font settings"
```
