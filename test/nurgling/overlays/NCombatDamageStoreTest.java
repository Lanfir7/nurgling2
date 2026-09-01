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

    private final Object session = new Object();
    private final Object otherSession = new Object();

    @BeforeEach
    @AfterEach
    void resetStore() {
        NCombatDamageStore.clearAll();
    }

    @Test
    void recordsChannelsByGobIdAndTotalsHpDealt() {
        NCombatDamageStore.record(session, 42L, 0, 30);
        NCombatDamageStore.record(session, 42L, 1, 10);
        NCombatDamageStore.record(session, 42L, 2, 5);
        NCombatDamageStore.record(session, 7L, 0, 8);

        assertArrayEquals(new int[]{30, 10, 5}, NCombatDamageStore.snapshot(session, 42L));
        assertEquals(CreatureHp.hpDealt(30, 10, 5), NCombatDamageStore.total(session, 42L));
        assertEquals(8, NCombatDamageStore.total(session, 7L));
        assertTrue(NCombatDamageStore.contains(session, 42L));
        assertFalse(NCombatDamageStore.contains(session, 99L));
        assertEquals(0, NCombatDamageStore.total(session, 99L));
    }

    @Test
    void sameGobIdDoesNotCollideAcrossSessions() {
        NCombatDamageStore.replace(session, 1L, 10, 0, 0);
        NCombatDamageStore.replace(otherSession, 1L, 99, 5, 0);
        assertEquals(10, NCombatDamageStore.total(session, 1L));
        assertEquals(CreatureHp.hpDealt(99, 5, 0), NCombatDamageStore.total(otherSession, 1L));
        NCombatDamageStore.clearSession(session);
        assertFalse(NCombatDamageStore.contains(session, 1L));
        assertTrue(NCombatDamageStore.contains(otherSession, 1L));
    }

    @Test
    void overlayGoneThenNewOverlayRestoresViaSeedAndReplace() {
        int[] overlay = new int[3];
        overlay[0] += 30;
        overlay[1] += 10;
        NCombatDamageStore.replace(session, 99L, overlay[0], overlay[1], overlay[2]);

        int[] returned = new int[3];
        NCombatDamageStore.copyInto(session, 99L, returned);
        assertArrayEquals(new int[]{30, 10, 0}, returned);
        returned[0] += 5;
        NCombatDamageStore.replace(session, 99L, returned[0], returned[1], returned[2]);
        assertEquals(CreatureHp.hpDealt(35, 10, 0), NCombatDamageStore.total(session, 99L));
    }

    @Test
    void storeIsSourceOfTruthWhenOverlayIsGone() {
        NCombatDamageStore.replace(session, 5L, 45, 0, 20);
        assertEquals(45, NCombatDamageStore.total(session, 5L));
        assertEquals(0, NCombatDamageStore.total(session, 6L));
    }

    @Test
    void clearIdAndClearAllRemoveStoredTotals() {
        NCombatDamageStore.replace(session, 11L, 10, 0, 0);
        NCombatDamageStore.replace(session, 12L, 4, 1, 0);

        NCombatDamageStore.clear(session, 11L);
        assertFalse(NCombatDamageStore.contains(session, 11L));
        int[] seeded = new int[3];
        NCombatDamageStore.copyInto(session, 11L, seeded);
        assertArrayEquals(new int[]{0, 0, 0}, seeded);
        assertTrue(NCombatDamageStore.contains(session, 12L));

        NCombatDamageStore.clearAll();
        assertFalse(NCombatDamageStore.contains(session, 12L));
        assertEquals(0, NCombatDamageStore.total(session, 12L));
    }

    @Test
    void nullSessionIsIgnored() {
        NCombatDamageStore.replace(null, 1L, 10, 0, 0);
        NCombatDamageStore.record(null, 1L, 0, 4);
        assertFalse(NCombatDamageStore.contains(null, 1L));
        assertEquals(0, NCombatDamageStore.total(null, 1L));
        int[] dest = new int[]{9, 9, 9};
        NCombatDamageStore.copyInto(null, 1L, dest);
        assertArrayEquals(new int[]{0, 0, 0}, dest);
    }
}
