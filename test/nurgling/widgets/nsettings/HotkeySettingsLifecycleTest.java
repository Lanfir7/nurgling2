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
        Resource.local().add(new Resource.FileSource(Paths.get("resources", "compiled", "res")));
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

        window.remove();
        assertEquals(0, registry.listenerCount());
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
