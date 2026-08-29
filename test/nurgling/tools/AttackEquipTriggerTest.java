package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackEquipTriggerTest {

    @Test
    void settingOffNeverStarts() {
        assertFalse(AttackEquipTrigger.shouldStart(false, true, false));
        AttackEquipTrigger t = new AttackEquipTrigger();
        assertFalse(t.requestStart(false));
        assertFalse(t.onCursorChanged("gfx/hud/curs/atk", false));
    }

    @Test
    void settingOnAndAttackActivationStartsOnce() {
        AttackEquipTrigger t = new AttackEquipTrigger();
        assertTrue(t.requestStart(true));
        assertFalse(t.requestStart(true));
        assertFalse(t.onCursorChanged("gfx/hud/curs/atk", true));
    }

    @Test
    void secondActivationWhileRunningDoesNotStart() {
        AttackEquipTrigger t = new AttackEquipTrigger();
        assertTrue(t.requestStart(true));
        assertFalse(t.requestStart(true));
        t.finished();
        assertTrue(t.requestStart(true));
    }

    @Test
    void paginaeThenCursorStartOnlyOnce() {
        AttackEquipTrigger t = new AttackEquipTrigger();
        assertFalse(t.onPaginaeUsed("paginae/act/atk", new String[] {"atk"}, "gfx/hud/curs/arw", true));
        assertTrue(t.onCursorChanged("gfx/hud/curs/atk", true));
        assertFalse(t.onCursorChanged("gfx/hud/curs/atk", true));
    }

    @Test
    void cancelBeforeCursorDoesNotStart() {
        AttackEquipTrigger t = new AttackEquipTrigger();
        assertFalse(t.onPaginaeUsed("paginae/act/atk", new String[] {"atk"}, "gfx/hud/curs/arw", true));
        assertFalse(t.onCursorChanged("gfx/hud/curs/arw", true));
        assertFalse(t.onCursorChanged("gfx/hud/curs/atk", true));
    }

    @Test
    void gobClickWhileAlreadyAtkDoesNotRetrigger() {
        AttackEquipTrigger t = new AttackEquipTrigger();
        assertTrue(t.onPaginaeUsed("paginae/act/atk", new String[] {"atk"}, "gfx/hud/curs/atk", true));
        assertFalse(t.onCursorChanged("gfx/hud/curs/atk", true));
        t.finished();
        assertFalse(t.onCursorChanged("gfx/hud/curs/atk", true));
    }

    @Test
    void cursorAloneWithoutPaginaeDoesNotStart() {
        AttackEquipTrigger t = new AttackEquipTrigger();
        assertFalse(t.onCursorChanged("gfx/hud/curs/atk", true));
    }

    @Test
    void vanillaAttackPaginaeNotCombatMoves() {
        assertTrue(AttackEquipTrigger.isVanillaAttackPaginae("paginae/act/atk"));
        assertTrue(AttackEquipTrigger.isVanillaAttackAction(new String[] {"atk"}));
        assertFalse(AttackEquipTrigger.isVanillaAttackPaginae("paginae/atk/chop"));
        assertFalse(AttackEquipTrigger.isVanillaAttackPaginae("paginae/act/swim"));
        assertFalse(AttackEquipTrigger.isVanillaAttack("paginae/atk/chop", new String[] {"atk"}));
        assertFalse(AttackEquipTrigger.isVanillaAttackPaginae(null));
        assertFalse(AttackEquipTrigger.isVanillaAttackAction(new String[] {"chop"}));
        assertFalse(AttackEquipTrigger.isVanillaAttackAction(new String[0]));
        assertFalse(AttackEquipTrigger.isVanillaAttackAction(null));
    }

    @Test
    void attackCursorName() {
        assertTrue(AttackEquipTrigger.isAttackCursor("gfx/hud/curs/atk"));
        assertFalse(AttackEquipTrigger.isAttackCursor("gfx/hud/curs/arw"));
        assertFalse(AttackEquipTrigger.isAttackCursor("gfx/hud/curs/hand"));
        assertFalse(AttackEquipTrigger.isAttackCursor(null));
    }

    @Test
    void shouldStartPureDecision() {
        assertFalse(AttackEquipTrigger.shouldStart(false, true, false));
        assertTrue(AttackEquipTrigger.shouldStart(true, true, false));
        assertFalse(AttackEquipTrigger.shouldStart(true, true, true));
        assertFalse(AttackEquipTrigger.shouldStart(true, false, false));
    }
}
