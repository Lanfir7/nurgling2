package nurgling.conf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NCombatDataPredictionTest {
    @Test
    void unknownLoadoutDoesNotShowFalseZeroDamage() {
        int damage = NCombatData.predictedDamage(
                "cleave",
                new int[]{0, 0, 100, 0},
                NCombatData.Loadout.UNKNOWN);

        assertEquals(-1, damage);
    }

    @Test
    void meleePredictionCombinesWeaponQualityStrengthAndOpening() {
        NCombatData.Loadout loadout = new NCombatData.Loadout(100, 40, 40);

        int damage = NCombatData.predictedDamage(
                "cleave",
                new int[]{0, 0, 50, 0},
                loadout);

        assertEquals(75, damage);
    }

    @Test
    void incompleteOpeningDataDoesNotCrashPrediction() {
        NCombatData.Loadout loadout = new NCombatData.Loadout(100, 40, 40);

        assertEquals(-1, NCombatData.predictedDamage("cleave", new int[]{50}, loadout));
    }

    @Test
    void missingUiKeepsLoadoutUnknown() {
        assertNull(NCombatData.readLoadout(null));
    }
}
