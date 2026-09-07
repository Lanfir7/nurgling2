package nurgling.widgets.nsettings;

import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyRegistry;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HotkeySettingsLifecycleTest {
    @Test
    void removalUnregistersRegistryListenerExactlyOnce() throws Exception {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeySettings page = (HotkeySettings) unsafe().allocateInstance(HotkeySettings.class);
        Consumer<List<HotkeyAction>> listener = ignored -> { };
        setObject(page, "model", new HotkeySettingsModel(registry));
        setObject(page, "registryListener", listener);
        setBoolean(page, "registryListening", true);
        registry.addListener(listener);
        assertEquals(1, registry.listenerCount());

        page.remove();
        page.remove();

        assertEquals(0, registry.listenerCount());
    }

    private static void setObject(Object target, String name, Object value) throws Exception {
        Field field = HotkeySettings.class.getDeclaredField(name);
        unsafe().putObject(target, unsafe().objectFieldOffset(field), value);
    }

    private static void setBoolean(Object target, String name, boolean value) throws Exception {
        Field field = HotkeySettings.class.getDeclaredField(name);
        unsafe().putBoolean(target, unsafe().objectFieldOffset(field), value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
