package nurgling;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

class NRecipeTooltipTest {
    @Test
    void tunnelDescriptionsShowSupportDimensionsWithoutTotal() throws Exception {
        assertEquals("1\u00d75", supportAreaText("Timber Tunnel"));
        assertEquals("2\u00d78", supportAreaText("Reinforced Tunnel"));
        assertEquals("3\u00d715", supportAreaText("Stone Arch Tunnel"));
        assertNull(supportAreaText("Chest"));
    }

    private static String supportAreaText(String name) throws Exception {
        try {
            Method method = NRecipeTooltip.class.getDeclaredMethod("supportAreaText", String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, name);
        } catch (NoSuchMethodException e) {
            fail("Tunnel support dimensions are not implemented");
            return null;
        }
    }
}
