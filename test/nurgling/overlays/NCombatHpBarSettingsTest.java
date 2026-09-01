package nurgling.overlays;

import nurgling.NConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NCombatHpBarSettingsTest {

    @Test
    void negativeOffsetIsAcceptedAndClampedToRange() {
        assertEquals(-5f, NCombatHpBarStyle.clampOffset(-5), 0.001f);
        assertEquals(-20f, NCombatHpBarStyle.clampOffset(-25), 0.001f);
        assertEquals(40f, NCombatHpBarStyle.clampOffset(99), 0.001f);
        assertEquals((int) Math.round(NCombatHpBarStyle.OFFSET_MIN * 10), -200);
        assertEquals((int) Math.round(NCombatHpBarStyle.OFFSET_MAX * 10), 400);
        assertEquals(NCombatHpBarStyle.DEF_OFFSET, NCombatHpBarStyle.clampOffset(null), 0.001f);
        assertEquals(NCombatHpBarStyle.DEF_OFFSET, NCombatHpBarStyle.clampOffset("nope"), 0.001f);
        assertEquals(13.2f, NCombatHpBarStyle.clampOffset(13.2), 0.001f);
        assertEquals(13.2f, NCombatHpBarStyle.clampOffset(13.2f), 0.001f);
    }

    @Test
    void widthIsClampedToRange() {
        assertEquals(NCombatHpBarStyle.WIDTH_MIN, NCombatHpBarStyle.clampWidth(10));
        assertEquals(NCombatHpBarStyle.WIDTH_MAX, NCombatHpBarStyle.clampWidth(999));
        assertEquals(78, NCombatHpBarStyle.clampWidth(78));
        assertEquals(NCombatHpBarStyle.DEF_WIDTH, NCombatHpBarStyle.clampWidth(null));
        assertEquals(NCombatHpBarStyle.DEF_WIDTH, NCombatHpBarStyle.clampWidth("nope"));
        assertEquals(40, NCombatHpBarStyle.clampWidth(40));
        assertEquals(160, NCombatHpBarStyle.clampWidth(160));
    }

    @Test
    void configKeysExist() {
        assertEquals(NConfig.Key.combatCreatureHpBarOffset, NConfig.Key.valueOf("combatCreatureHpBarOffset"));
        assertEquals(NConfig.Key.combatCreatureHpBarWidth, NConfig.Key.valueOf("combatCreatureHpBarWidth"));
    }

    @Test
    void missingConfigUsesDefaults() {
        NConfig previous = NConfig.current;
        NConfig.current = null;
        try {
            assertEquals(NCombatHpBarStyle.DEF_OFFSET, NCombatHpBarStyle.offsetZ(), 0.001f);
            assertEquals(NCombatHpBarStyle.DEF_WIDTH, NCombatHpBarStyle.unscaledWidth());
        } finally {
            NConfig.current = previous;
        }
    }
}
