package nurgling.overlays;

import nurgling.tools.CreatureHp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NCombatDamageStoreTest {

    @BeforeEach
    @AfterEach
    void resetStore() {
        NCombatDamageStore.clearAll();
    }

    @Test
    void recordsChannelsByGobIdAndTotalsHpDealt() {
        NCombatDamageStore.record(42L, 0, 30);
        NCombatDamageStore.record(42L, 1, 10);
        NCombatDamageStore.record(42L, 2, 5);
        NCombatDamageStore.record(7L, 0, 8);

        assertArrayEquals(new int[]{30, 10, 5}, NCombatDamageStore.snapshot(42L));
        assertEquals(CreatureHp.hpDealt(30, 10, 5), NCombatDamageStore.total(42L));
        assertEquals(8, NCombatDamageStore.total(7L));
        assertTrue(NCombatDamageStore.contains(42L));
        assertFalse(NCombatDamageStore.contains(99L));
        assertEquals(0, NCombatDamageStore.total(99L));
    }

    @Test
    void newOverlaySimulationRestoresStoredTotals() {
        NCombatDamageStore.replace(99L, 30, 10, 5);

        int[] firstOverlay = new int[3];
        NCombatDamageStore.copyInto(99L, firstOverlay);
        assertEquals(CreatureHp.hpDealt(30, 10, 5), CreatureHp.hpDealt(firstOverlay[0], firstOverlay[1], firstOverlay[2]));

        int[] returnedOverlay = new int[3];
        NCombatDamageStore.copyInto(99L, returnedOverlay);
        assertArrayEquals(firstOverlay, returnedOverlay);
        assertEquals(CreatureHp.hpDealt(30, 10, 5), NCombatDamageStore.total(99L));
    }

    @Test
    void storeIsSourceOfTruthWhenOverlayIsGone() {
        NCombatDamageStore.replace(5L, 45, 0, 20);
        assertEquals(45, NCombatDamageStore.total(5L));
        assertEquals(0, NCombatDamageStore.total(6L));
    }

    @Test
    void clearIdAndClearAllRemoveStoredTotals() {
        NCombatDamageStore.replace(11L, 10, 0, 0);
        NCombatDamageStore.replace(12L, 4, 1, 0);

        NCombatDamageStore.clear(11L);
        assertFalse(NCombatDamageStore.contains(11L));
        int[] seeded = new int[3];
        NCombatDamageStore.copyInto(11L, seeded);
        assertArrayEquals(new int[]{0, 0, 0}, seeded);
        assertTrue(NCombatDamageStore.contains(12L));

        NCombatDamageStore.clearAll();
        assertFalse(NCombatDamageStore.contains(12L));
        assertEquals(0, NCombatDamageStore.total(12L));
    }
}
