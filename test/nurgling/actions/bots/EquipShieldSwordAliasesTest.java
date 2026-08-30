package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipShieldSwordAliasesTest {
    @Test
    void shieldAliasAcceptsShieldVariants() {
        assertTrue(EquipShieldSword.SHIELDS.matches("Wooden RoundShield"));
        assertTrue(EquipShieldSword.SHIELDS.matches("Leather Shield"));
        assertFalse(EquipShieldSword.SHIELDS.matches("Bronze Sword"));
    }

    @Test
    void swordAliasAcceptsBronzeAndMansSwordVariants() {
        assertTrue(EquipShieldSword.SWORDS.matches("Bronze Sword"));
        assertTrue(EquipShieldSword.SWORDS.matches("Hirdsman's Sword"));
        assertTrue(EquipShieldSword.SWORDS.matches("Fyrdsman's Sword"));
        assertFalse(EquipShieldSword.SWORDS.matches("Cutblade"));
    }
}
