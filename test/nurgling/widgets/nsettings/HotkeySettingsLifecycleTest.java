package nurgling.widgets.nsettings;

import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyRegistry;
import haven.Button;
import haven.Coord;
import haven.Label;
import haven.Resource;
import haven.UI;
import haven.Widget;
import nurgling.widgets.NSettingsWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotkeySettingsLifecycleTest {
    @Test void mouseClickArmsCaptureAndAllowsRepeatedReassignment() throws Exception {
        UI ui = (UI)unsafe().allocateInstance(UI.class);
        List<UI.Grab> grabs = new java.util.concurrent.CopyOnWriteArrayList<>();
        setObject(ui, "grabs", grabs);
        CountingBinding binding = new CountingBinding("capture", nurgling.hotkeys.InputGesture.none());
        List<HotkeyCapturePolicy.Decision> decisions = new ArrayList<>();
        nurgling.widgets.NHotkeyCapture capture = new nurgling.widgets.NHotkeyCapture(
                120, action("capture", "Capture", binding), decisions::add) {
            protected void depress() { }
            protected void unpress() { }
        };
        capture.ui = ui;
        for(int button : new int[] {2, 3}) {
            assertTrue(capture.mousedown(new Widget.MouseDownEvent(Coord.of(2, 2), 1)));
            assertTrue(capture.mouseup((Widget.MouseUpEvent)new Widget.MouseUpEvent(
                    Coord.of(2, 2), 1).grabbed(true)));
            assertTrue(capture.armed(), "a normal mouse click must enter capture mode");
            assertEquals(2, grabs.size(), "the button's initial mouse grab must be released");
            assertTrue(capture.mousedown((Widget.MouseDownEvent)new Widget.MouseDownEvent(
                    Coord.of(2, 2), button).grabbed(true)));
            assertFalse(capture.armed());
            assertTrue(grabs.isEmpty());
            assertEquals(nurgling.hotkeys.InputGesture.mouse(button, haven.KeyMatch.MODS, 0),
                    decisions.get(decisions.size() - 1).gesture());
        }
        assertEquals(2, decisions.size());
    }

    private nurgling.NUI previousUi;

    @BeforeEach void installTestUi() throws Exception {
        previousUi = nurgling.sessions.ThreadLocalUI.get();
        nurgling.NUI local = (nurgling.NUI)unsafe().allocateInstance(nurgling.NUI.class);
        local.sessionConfig = new nurgling.NConfig();
        nurgling.sessions.ThreadLocalUI.set(local);
    }

    @AfterEach void restoreTestUi() {
        if(previousUi == null) nurgling.sessions.ThreadLocalUI.clear();
        else nurgling.sessions.ThreadLocalUI.set(previousUi);
    }

    @Test void openingBuildsEachActionRowOnlyOnce() {
        HotkeyRegistry registry = new HotkeyRegistry();
        CountingBinding binding = new CountingBinding("single-action",
                nurgling.hotkeys.InputGesture.none());
        registry.register(action("single-action", "Single action", binding));
        HotkeySettingsModel model = new HotkeySettingsModel(registry);
        binding.currentCalls = 0;

        new HotkeySettings(model);

        assertEquals(3, binding.currentCalls,
                "opening must not render the complete action list more than once");
    }

    @Test void unchangedLoadAndResizeReuseActionRows() throws Exception {
        HotkeyRegistry registry = new HotkeyRegistry();
        CountingBinding binding = new CountingBinding("stable-action",
                nurgling.hotkeys.InputGesture.none());
        registry.register(action("stable-action", "Stable action", binding));
        HotkeySettings page = new HotkeySettings(new HotkeySettingsModel(registry));
        HotkeyActionRow original = firstActionRow(page);

        page.load();
        page.resize(page.sz);

        assertSame(original, firstActionRow(page),
                "unchanged lifecycle refreshes must reuse the already rendered rows");
    }

    @Test void reusedActionRowReceivesTheChangedDraftGesture() throws Exception {
        HotkeyRegistry registry = new HotkeyRegistry();
        CountingBinding binding = new CountingBinding("updated-action",
                nurgling.hotkeys.InputGesture.none());
        registry.register(action("updated-action", "Updated action", binding));
        HotkeySettingsModel model = new HotkeySettingsModel(registry);
        HotkeySettings page = new HotkeySettings(model);
        HotkeyActionRow row = firstActionRow(page);
        nurgling.hotkeys.InputGesture changed = nurgling.hotkeys.InputGesture.mouse(2, 0, 0);

        model.assign("updated-action", changed);
        page.resize(page.sz);

        assertSame(row, firstActionRow(page));
        assertEquals(changed, row.capture().gesture());
    }

    @Test void conflictPromptReplacesResetControlsWithoutTextOverlap() throws Exception {
        HotkeyRegistry registry = new HotkeyRegistry();
        nurgling.hotkeys.InputGesture gesture = nurgling.hotkeys.InputGesture.mouse(1, 7, 0);
        CountingBinding first = new CountingBinding("first-conflict", gesture);
        CountingBinding second = new CountingBinding("second-conflict", gesture);
        registry.register(action("first-conflict",
                "A very long localized action description which must not cover the decision buttons", first));
        registry.register(action("second-conflict",
                "Another very long localized action description", second));
        HotkeySettings page = new HotkeySettings(new HotkeySettingsModel(registry));

        page.save();

        Button resetCategory = (Button)findField(HotkeySettings.class, "resetCategory").get(page);
        Button resetAll = (Button)findField(HotkeySettings.class, "resetAll").get(page);
        Widget conflict = (Widget)findField(HotkeySettings.class, "conflictBox").get(page);
        assertNotNull(conflict);
        assertFalse(resetCategory.visible, "the conflict prompt must replace the reset controls");
        assertFalse(resetAll.visible, "the conflict prompt must replace the reset controls");
        Label label = null;
        List<Button> buttons = new ArrayList<>();
        int firstButtonX = Integer.MAX_VALUE;
        for(Widget child : conflict.children()) {
            if(child instanceof Label) label = (Label)child;
            if(child instanceof Button) {
                buttons.add((Button)child);
                firstButtonX = Math.min(firstButtonX, child.c.x);
            }
        }
        assertNotNull(label);
        assertTrue(label.c.x + label.sz.x + UI.scale(4) <= firstButtonX,
                "the conflict description must be fitted before its decision buttons");

        buttons.sort((left, right) -> Integer.compare(left.c.x, right.c.x));
        Coord wideSize = page.sz;
        int firstPreferredWidth = buttons.get(0).sz.x;
        int secondPreferredWidth = buttons.get(1).sz.x;
        for(Button button : buttons)
            setObject(button, "surf", new haven.TexI(haven.TexI.mkbuf(button.sz)));
        page.resize(Coord.of(UI.scale(150), page.sz.y));
        assertTrue(buttons.get(0).c.x + buttons.get(0).sz.x <= buttons.get(1).c.x,
                "conflict decision buttons must not overlap in a narrow viewport");
        for(Button button : buttons)
            assertNull(findField(haven.SIWidget.class, "surf").get(button),
                    "resizing a rendered conflict button must invalidate its cached surface");

        for(Button button : buttons)
            setObject(button, "surf", new haven.TexI(haven.TexI.mkbuf(button.sz)));
        page.resize(wideSize);
        assertEquals(firstPreferredWidth, buttons.get(0).sz.x);
        assertEquals(secondPreferredWidth, buttons.get(1).sz.x);
        for(Button button : buttons)
            assertNull(findField(haven.SIWidget.class, "surf").get(button));
    }

    @Test void localizedCategoryTabsFitTheirTextAndMarkTheSelection() throws Exception {
        nurgling.NUI oldUI = nurgling.sessions.ThreadLocalUI.get();
        nurgling.NUI local = (nurgling.NUI)unsafe().allocateInstance(nurgling.NUI.class);
        local.sessionConfig = new nurgling.NConfig();
        nurgling.sessions.ThreadLocalUI.set(local);
        java.util.Locale previous = nurgling.i18n.L10n.getLocale();
        try {
            nurgling.i18n.L10n.setLocale(java.util.Locale.forLanguageTag("ru"));
            HotkeySettings page = new HotkeySettings(new HotkeySettingsModel(new HotkeyRegistry()));
            @SuppressWarnings("unchecked")
            List<Button> tabs = (List<Button>)findField(HotkeySettings.class, "tabButtons").get(page);
            Widget tabsHost = (Widget)findField(HotkeySettings.class, "tabsHost").get(page);

            for(Button tab : tabs)
                assertTrue(tab.sz.x >= tab.text.sz().x + Button.margin,
                        "localized tab text must fit inside its button: " + tab.text.text);
            assertTrue(tabsHost.sz.y >= tabs.get(0).sz.y,
                    "the selected-state underline must not be clipped by the tab viewport");
            assertNotNull(tabs.get(0).tint, "selected category must be visibly marked");

            tabs.get(1).click();
            assertNull(tabs.get(0).tint);
            assertNotNull(tabs.get(1).tint);

            Button previousVisible = null;
            for(Button tab : tabs) {
                if(!tab.visible)
                    continue;
                if(previousVisible != null)
                    assertTrue(previousVisible.c.x + previousVisible.sz.x <= tab.c.x,
                            "resized localized tabs must be laid out again");
                previousVisible = tab;
            }
            Button resetCategory = findButton(page, nurgling.i18n.L10n.get("hotkeys.reset_category"));
            Button resetAll = findButton(page, nurgling.i18n.L10n.get("hotkeys.reset_all"));
            assertTrue(resetCategory.c.x + resetCategory.sz.x + UI.scale(5) <= resetAll.c.x,
                    "resized reset buttons must be laid out again");
        } finally {
            nurgling.i18n.L10n.setLocale(previous);
            if(oldUI == null) nurgling.sessions.ThreadLocalUI.clear();
            else nurgling.sessions.ThreadLocalUI.set(oldUI);
        }
    }

    @Test void longActionTextIsEllipsizedWithoutOverlappingControls() throws Exception {
        nurgling.NUI oldUI = nurgling.sessions.ThreadLocalUI.get();
        nurgling.NUI local = (nurgling.NUI)unsafe().allocateInstance(nurgling.NUI.class);
        local.sessionConfig = new nurgling.NConfig();
        nurgling.sessions.ThreadLocalUI.set(local);
        try {
            String fullLabel = "Очень длинное локализованное название действия, которое не должно заходить на кнопку назначения клавиши";
            nurgling.hotkeys.PreferenceStore preferences = new nurgling.hotkeys.PreferenceStore() {
                public String get(String key, String fallback) { return fallback; }
                public void set(String key, String value) { }
            };
            nurgling.hotkeys.InputGesture gesture = nurgling.hotkeys.InputGesture.none();
            HotkeyAction action = new HotkeyAction("long-action", null, fullLabel,
                    nurgling.hotkeys.HotkeyCategory.WORLD,
                    java.util.EnumSet.of(nurgling.hotkeys.HotkeyContext.WORLD_SURFACE),
                    java.util.EnumSet.of(nurgling.hotkeys.InputGesture.Type.KEY),
                    new nurgling.hotkeys.GestureBinding("long-action", gesture, preferences),
                    null, 0, false);

            HotkeyActionRow row = new HotkeyActionRow(UI.scale(560), action, gesture,
                    ignored -> { }, () -> { });
            Widget label = (Widget)findField(HotkeyActionRow.class, "actionLabel").get(row);

            assertTrue(label.c.x + label.sz.x <= row.capture().c.x - UI.scale(4),
                    "action label must not overlap the binding control");
            assertEquals(fullLabel, label.tooltip(Coord.z, null),
                    "the complete localized label must remain available as a tooltip");

        } finally {
            if(oldUI == null) nurgling.sessions.ThreadLocalUI.clear();
            else nurgling.sessions.ThreadLocalUI.set(oldUI);
        }
    }

    @Test void contextTextIsVisuallyMutedBelowTheAction() throws Exception {
        nurgling.NUI oldUI = nurgling.sessions.ThreadLocalUI.get();
        nurgling.NUI local = (nurgling.NUI)unsafe().allocateInstance(nurgling.NUI.class);
        local.sessionConfig = new nurgling.NConfig();
        nurgling.sessions.ThreadLocalUI.set(local);
        try {
            nurgling.hotkeys.PreferenceStore preferences = new nurgling.hotkeys.PreferenceStore() {
                public String get(String key, String fallback) { return fallback; }
                public void set(String key, String value) { }
            };
            nurgling.hotkeys.InputGesture gesture = nurgling.hotkeys.InputGesture.none();
            HotkeyAction action = new HotkeyAction("muted-context", null, "Transfer one item",
                    nurgling.hotkeys.HotkeyCategory.INVENTORY,
                    java.util.EnumSet.of(nurgling.hotkeys.HotkeyContext.INVENTORY_ITEM_GENERIC),
                    java.util.EnumSet.of(nurgling.hotkeys.InputGesture.Type.KEY),
                    new nurgling.hotkeys.GestureBinding("muted-context", gesture, preferences),
                    null, 0, false);

            HotkeyActionRow row = new HotkeyActionRow(UI.scale(560), action, gesture,
                    ignored -> { }, () -> { });
            Label actionLabel = (Label)findField(HotkeyActionRow.class, "actionLabel").get(row);
            Label contextLabel = (Label)findField(HotkeyActionRow.class, "contextLabel").get(row);
            Color contextColor = contextLabel.col;

            assertEquals(Color.WHITE, actionLabel.col);
            assertEquals(contextColor.getRed(), contextColor.getGreen());
            assertEquals(contextColor.getGreen(), contextColor.getBlue());
            assertTrue(contextColor.getRed() < actionLabel.col.getRed(),
                    "context must be visually quieter than the action");
            assertTrue(contextColor.getRed() >= 100,
                    "muted context must remain readable on the dark background");
        } finally {
            if(oldUI == null) nurgling.sessions.ThreadLocalUI.clear();
            else nurgling.sessions.ThreadLocalUI.set(oldUI);
        }
    }

    @Test void captureInputFamilyFeedbackUsesTheSelectedLanguage() throws Exception {
        nurgling.NUI oldUI = nurgling.sessions.ThreadLocalUI.get();
        nurgling.NUI local = (nurgling.NUI)unsafe().allocateInstance(nurgling.NUI.class);
        local.sessionConfig = new nurgling.NConfig();
        nurgling.sessions.ThreadLocalUI.set(local);
        java.util.Locale previous = nurgling.i18n.L10n.getLocale();
        try {
            nurgling.i18n.L10n.setLocale(java.util.Locale.forLanguageTag("ru"));
            String[] ids = {"world.placement.rotate_left", "held.drop_on_ground",
                    "world.placement.fine_wheel_left", "world.placement.free_position"};
            String[] labels = {"клавиша", "кнопка мыши", "колесо мыши", "модификатор"};
            java.lang.reflect.Method method = nurgling.widgets.NHotkeyCapture.class.getDeclaredMethod("requiredTypes");
            method.setAccessible(true);
            for(int i = 0; i < ids.length; i++) {
                Object capture = unsafe().allocateInstance(nurgling.widgets.NHotkeyCapture.class);
                setObject(capture, "action", nurgling.hotkeys.Hotkeys.action(ids[i]));
                assertEquals(labels[i], method.invoke(capture));
            }
        } finally {
            nurgling.i18n.L10n.setLocale(previous);
            if(oldUI == null) nurgling.sessions.ThreadLocalUI.clear();
            else nurgling.sessions.ThreadLocalUI.set(oldUI);
        }
    }
    @Test void unresolvedConflictIsShownWithoutThrowingOrSaving() throws Exception {
        nurgling.NUI oldUI = nurgling.sessions.ThreadLocalUI.get();
        nurgling.NUI local = (nurgling.NUI)unsafe().allocateInstance(nurgling.NUI.class);
        local.sessionConfig = new nurgling.NConfig();
        nurgling.sessions.ThreadLocalUI.set(local);
        try {
        HotkeyRegistry registry = new HotkeyRegistry();
        nurgling.hotkeys.InputGesture gesture = nurgling.hotkeys.InputGesture.mouse(1, 7, 0);
        nurgling.hotkeys.PreferenceStore preferences = new nurgling.hotkeys.PreferenceStore() {
            public String get(String k, String fallback) { return fallback; }
            public void set(String k, String v) { throw new AssertionError("must not save conflicts"); }
        };
        for(String id : new String[]{"one", "two"})
            registry.register(new HotkeyAction(id, null, id, nurgling.hotkeys.HotkeyCategory.WORLD,
                    java.util.EnumSet.of(nurgling.hotkeys.HotkeyContext.WORLD_SURFACE),
                    java.util.EnumSet.of(gesture.type()), new nurgling.hotkeys.GestureBinding(id, gesture, preferences),
                    null, 0, false));
        HotkeySettings page = new HotkeySettings(new HotkeySettingsModel(registry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(page::save);
        org.junit.jupiter.api.Assertions.assertNotNull(findField(HotkeySettings.class, "conflictBox").get(page));
        } finally {
            if(oldUI == null) nurgling.sessions.ThreadLocalUI.clear();
            else nurgling.sessions.ThreadLocalUI.set(oldUI);
        }
    }

    @Test void selectingPresetResetsTheRebuiltActionListToItsTop() throws Exception {
        nurgling.NUI oldUI = nurgling.sessions.ThreadLocalUI.get();
        nurgling.NUI local = (nurgling.NUI)unsafe().allocateInstance(nurgling.NUI.class);
        local.sessionConfig = new nurgling.NConfig();
        nurgling.sessions.ThreadLocalUI.set(local);
        try {
            HotkeyRegistry registry = new HotkeyRegistry();
            nurgling.hotkeys.PreferenceStore preferences = new nurgling.hotkeys.PreferenceStore() {
                public String get(String key, String fallback) { return fallback; }
                public void set(String key, String value) { }
            };
            for(int i = 0; i < 20; i++) {
                String id = "scroll-action-" + i;
                registry.register(new HotkeyAction(id, null, id,
                        nurgling.hotkeys.HotkeyCategory.WINDOWS,
                        java.util.EnumSet.of(nurgling.hotkeys.HotkeyContext.GLOBAL),
                        java.util.EnumSet.of(nurgling.hotkeys.InputGesture.Type.KEY),
                        new nurgling.hotkeys.GestureBinding(id,
                                nurgling.hotkeys.InputGesture.none(), preferences), null, i, false));
            }
            HotkeySettings page = new HotkeySettings(new HotkeySettingsModel(registry));
            haven.Scrollport scroll = (haven.Scrollport)findField(
                    HotkeySettings.class, "rowsScroll").get(page);
            assertTrue(scroll.bar.max > 0);
            scroll.bar.ch(scroll.bar.max);
            assertTrue(scroll.cont.sy > 0);

            page.controls().select(nurgling.hotkeys.presets.HotkeyPresetCatalog.ENDER_ID);

            assertEquals(0, scroll.bar.val);
            assertEquals(0, scroll.cont.sy);
        } finally {
            if(oldUI == null) nurgling.sessions.ThreadLocalUI.clear();
            else nurgling.sessions.ThreadLocalUI.set(oldUI);
        }
    }

    @Test void hotkeyPageIsBuiltOnlyOnFirstOpen() throws Exception {
        nurgling.NUI oldUI = nurgling.sessions.ThreadLocalUI.get();
        nurgling.NUI local = (nurgling.NUI)unsafe().allocateInstance(nurgling.NUI.class);
        local.sessionConfig = new nurgling.NConfig();
        nurgling.sessions.ThreadLocalUI.set(local);
        HotkeyRegistry registry = nurgling.hotkeys.Hotkeys.registry();
        int listenersBefore = registry.listenerCount();
        NSettingsWindow window = null;
        try {
            window = new NSettingsWindow();
            assertEquals(listenersBefore, registry.listenerCount(),
                    "constructing the hidden settings window must not build the hotkey page");

            org.junit.jupiter.api.Assertions.assertTrue(window.showPage(NSettingsWindow.HOTKEY_PAGE_ID));
            assertEquals(listenersBefore + 1, registry.listenerCount());

            HotkeySettings page = (HotkeySettings)window.currentPanel;
            int clipboardGeneration = page.controls().clipboardGeneration();
            window.hide();
            assertTrue(page.controls().clipboardGeneration() > clipboardGeneration,
                    "hiding settings must invalidate pending clipboard callbacks");

            org.junit.jupiter.api.Assertions.assertTrue(window.showPage(NSettingsWindow.HOTKEY_PAGE_ID));
            assertEquals(listenersBefore + 1, registry.listenerCount(),
                    "reopening the page must reuse the same instance");
        } finally {
            if(window != null)
                window.destroy();
            if(oldUI == null) nurgling.sessions.ThreadLocalUI.clear();
            else nurgling.sessions.ThreadLocalUI.set(oldUI);
        }
        assertEquals(listenersBefore, registry.listenerCount());
    }
    static {
        Resource.local().add(new Resource.FileSource(Paths.get("resources", "compiled", "res")));
        try {
            java.net.URLClassLoader resources = new java.net.URLClassLoader(new java.net.URL[]{
                    Paths.get("bin", "builtin-res.jar").toUri().toURL(), Paths.get("bin", "hafen-res.jar").toUri().toURL()});
            Resource.local().add(name -> {
                java.io.InputStream stream = resources.getResourceAsStream("res/" + name + ".res");
                if(stream == null) throw new java.io.FileNotFoundException(name);
                return stream;
            });
        } catch(java.net.MalformedURLException e) { throw new AssertionError(e); }
    }

    @Test
    void removalUnregistersRegistryListenerExactlyOnce() throws Exception {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeySettings page = detachedPage(registry);
        assertEquals(1, registry.listenerCount());

        page.remove();
        page.remove();

        assertEquals(0, registry.listenerCount());
    }

    @Test
    void parentRemovalDisposesNestedHotkeyPage() throws Exception {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeySettings page = detachedPage(registry);
        NSettingsWindow window = parentWith(page);
        window.remove();
        assertEquals(0, registry.listenerCount());
    }

    @Test
    void parentDestroyDisposesNestedHotkeyPageIdempotently() throws Exception {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeySettings page = detachedPage(registry);
        NSettingsWindow window = parentWith(page);

        window.destroy();
        assertEquals(0, registry.listenerCount());
        window.destroy();
        window.remove();
        assertEquals(0, registry.listenerCount());
    }

    @Test
    void destroyingPageStopsPresetClipboardCallbacks() throws Exception {
        nurgling.NUI oldUI = nurgling.sessions.ThreadLocalUI.get();
        nurgling.NUI local = (nurgling.NUI)unsafe().allocateInstance(nurgling.NUI.class);
        local.sessionConfig = new nurgling.NConfig();
        nurgling.sessions.ThreadLocalUI.set(local);
        try {
            HotkeySettings page = new HotkeySettings(new HotkeySettingsModel(new HotkeyRegistry()));
            assertTrue(page.controls().acceptsClipboardResult());
            page.disposeLifecycle();
            org.junit.jupiter.api.Assertions.assertFalse(page.controls().acceptsClipboardResult());
            org.junit.jupiter.api.Assertions.assertFalse(page.controls().hasOpenPrompt());
        } finally {
            if(oldUI == null) nurgling.sessions.ThreadLocalUI.clear();
            else nurgling.sessions.ThreadLocalUI.set(oldUI);
        }
    }

    private static NSettingsWindow parentWith(HotkeySettings page) throws Exception {
        NSettingsWindow window = (NSettingsWindow) unsafe().allocateInstance(NSettingsWindow.class);
        Object category = unsafe().allocateInstance(
                Class.forName("nurgling.widgets.NSettingsWindow$SettingsCategory"));
        setObject(category, "panel", page);
        setObject(category, "children", new ArrayList<Object>());
        Object list = unsafe().allocateInstance(
                Class.forName("nurgling.widgets.NSettingsWindow$SettingsList"));
        List<Object> categories = new ArrayList<Object>();
        categories.add(category);
        setObject(list, "categories", categories);
        setObject(window, "list", list);
        return window;
    }

    private static Button findButton(Widget parent, String text) {
        for(Widget child : parent.children())
            if(child instanceof Button && ((Button)child).text.text.equals(text))
                return (Button)child;
        throw new AssertionError("button not found: " + text);
    }

    private static HotkeySettings detachedPage(HotkeyRegistry registry) throws Exception {
        HotkeySettings page = (HotkeySettings) unsafe().allocateInstance(HotkeySettings.class);
        Consumer<List<HotkeyAction>> listener = ignored -> { };
        setObject(page, "model", new HotkeySettingsModel(registry));
        setObject(page, "registryListener", listener);
        setBoolean(page, "registryListening", true);
        registry.addListener(listener);
        return page;
    }

    private static HotkeyActionRow firstActionRow(HotkeySettings page) throws Exception {
        Widget rows = (Widget)findField(HotkeySettings.class, "rows").get(page);
        for(Widget child : rows.children())
            if(child instanceof HotkeyActionRow)
                return (HotkeyActionRow)child;
        throw new AssertionError("action row not found");
    }

    private static HotkeyAction action(String id, String label,
                                       nurgling.hotkeys.HotkeyBinding binding) {
        return new HotkeyAction(id, null, label, nurgling.hotkeys.HotkeyCategory.WORLD,
                java.util.EnumSet.of(nurgling.hotkeys.HotkeyContext.WORLD_SURFACE),
                java.util.EnumSet.of(nurgling.hotkeys.InputGesture.Type.MOUSE_BUTTON),
                binding, null, 0, false);
    }

    private static final class CountingBinding implements nurgling.hotkeys.HotkeyBinding {
        private final String id;
        private final nurgling.hotkeys.InputGesture defaultGesture;
        private nurgling.hotkeys.InputGesture current;
        int currentCalls;

        CountingBinding(String id, nurgling.hotkeys.InputGesture defaultGesture) {
            this.id = id;
            this.defaultGesture = defaultGesture;
            this.current = defaultGesture;
        }

        public String id() { return id; }
        public nurgling.hotkeys.InputGesture defaultGesture() { return defaultGesture; }
        public nurgling.hotkeys.InputGesture current() { currentCalls++; return current; }
        public void set(nurgling.hotkeys.InputGesture gesture) { current = gesture; }
        public void reset() { current = defaultGesture; }
    }

    private static void setObject(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        unsafe().putObject(target, unsafe().objectFieldOffset(field), value);
    }

    private static void setBoolean(Object target, String name, boolean value) throws Exception {
        Field field = findField(target.getClass(), name);
        unsafe().putBoolean(target, unsafe().objectFieldOffset(field), value);
    }

    private static Field findField(Class<?> type, String name) throws Exception {
        for(Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch(NoSuchFieldException ignored) {
                // Search inherited SettingsItem fields as well.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
