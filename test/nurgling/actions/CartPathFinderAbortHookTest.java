package nurgling.actions;

import haven.Coord2d;
import nurgling.NGameUI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CartPathFinderAbortHookTest {
    @Test
    void cartPathFinderOverridesAbortAwareWalkHook() throws Exception {
        Method hook = CartPathFinder.class.getDeclaredMethod("walkTo", NGameUI.class, Coord2d.class,
                BooleanSupplier.class);

        assertEquals(CartPathFinder.class, hook.getDeclaringClass());
    }
}
