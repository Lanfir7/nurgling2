package nurgling.widgets.nsettings;

import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyRegistry;
import haven.Resource;
import nurgling.widgets.NSettingsWindow;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HotkeySettingsLifecycleTest {
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

    private static HotkeySettings detachedPage(HotkeyRegistry registry) throws Exception {
        HotkeySettings page = (HotkeySettings) unsafe().allocateInstance(HotkeySettings.class);
        Consumer<List<HotkeyAction>> listener = ignored -> { };
        setObject(page, "model", new HotkeySettingsModel(registry));
        setObject(page, "registryListener", listener);
        setBoolean(page, "registryListening", true);
        registry.addListener(listener);
        return page;
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
