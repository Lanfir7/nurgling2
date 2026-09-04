package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KFCHenAliasTest {
    @Test
    void chickenHenAliasRejectsDuckHens() {
        assertTrue(KFC.HEN.matches("Hen"));
        assertFalse(KFC.HEN.matches("Duck Hen"));
        assertFalse(KFC.HEN.matches("Dead Duck Hen"));
    }
}
