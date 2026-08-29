package nurgling;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NConfigEquipOnAttackTest {

    private NConfig previous;
    private boolean savedCurrent;

    @AfterEach
    void restoreCurrent() {
        if (savedCurrent) {
            NConfig.current = previous;
        }
    }

    @Test
    void keyRoundTripsForJsonLoad() {
        assertEquals(NConfig.Key.equipSwordShieldOnAttack, NConfig.Key.valueOf("equipSwordShieldOnAttack"));
    }

    @Test
    void missingConfigIsOff() {
        previous = NConfig.current;
        savedCurrent = true;
        NConfig.current = null;
        Object val = NConfig.get(NConfig.Key.equipSwordShieldOnAttack);
        assertNull(val);
        assertFalse(val instanceof Boolean && (Boolean) val);
    }

    @Test
    void loadSaveBoolPattern() {
        assertFalse(asBool(null));
        assertFalse(asBool("nope"));
        assertFalse(asBool(Boolean.FALSE));
        assertTrue(asBool(Boolean.TRUE));
    }

    private static boolean asBool(Object val) {
        return val instanceof Boolean ? (Boolean) val : false;
    }
}
