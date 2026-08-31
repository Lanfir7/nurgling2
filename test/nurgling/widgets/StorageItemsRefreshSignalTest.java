package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class StorageItemsRefreshSignalTest {
    @Test
    void changeDuringLoadIsKeptUntilVisibleWindowCanReload() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("nurgling.widgets.StorageItemsRefreshSignal");
        } catch (ClassNotFoundException e) {
            fail("Storage changes need a pending refresh signal");
            return;
        }

        Object signal = type.getDeclaredConstructor().newInstance();
        Method request = type.getDeclaredMethod("request");
        Method take = type.getDeclaredMethod("take", boolean.class, boolean.class);
        assertNotNull(request);
        assertNotNull(take);

        request.invoke(signal);
        assertFalse((Boolean) take.invoke(signal, true, true));
        assertFalse((Boolean) take.invoke(signal, false, false));
        assertTrue((Boolean) take.invoke(signal, true, false));
        assertFalse((Boolean) take.invoke(signal, true, false));
    }
}
