package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandLoadoutTest {
    @Test
    void emptyHandsNeedClearNotEquip() {
        HandLoadout empty = new HandLoadout(null, null);
        assertTrue(empty.isEmpty());
        assertTrue(empty.restoreEquipNames().isEmpty());
    }

    @Test
    void twoHandedWeaponIsEquippedOnce() {
        HandLoadout twoHand = new HandLoadout("Boar Spear", "Boar Spear");
        assertEquals(List.of("Boar Spear"), twoHand.restoreEquipNames());
    }

    @Test
    void eachHandRestoredSeparately() {
        HandLoadout loadout = new HandLoadout("Shield", "Sword");
        assertEquals(List.of("Shield", "Sword"), loadout.restoreEquipNames());
        assertFalse(loadout.isEmpty());
    }

    @Test
    void oneHandedLeavesOtherEmpty() {
        assertEquals(List.of("Axe"), new HandLoadout("Axe", null).restoreEquipNames());
        assertEquals(List.of("Bow"), new HandLoadout(null, "Bow").restoreEquipNames());
    }

    @Test
    void sameLoadoutNeedsNoRestore() {
        HandLoadout a = new HandLoadout("Shield", "Sword");
        assertTrue(a.sameAs(new HandLoadout("Shield", "Sword")));
        assertFalse(a.sameAs(new HandLoadout("Cleaver", "Sword")));
        assertFalse(a.sameAs(new HandLoadout(null, null)));
    }
}
