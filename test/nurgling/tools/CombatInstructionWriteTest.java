package nurgling.tools;

import haven.DTarget;
import haven.Widget;
import nurgling.NFightWnd;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatInstructionWriteTest {
    @Test
    void parchmentRmbSendsItemactWithMoveIdAndModflags() {
        Rec fight = new Rec();
        assertTrue(CombatInstructionWrite.iteminteract(fight, 42, 1));
        assertEquals("itemact", fight.msg);
        assertArrayEquals(new Object[]{42, 1}, fight.args);
    }

    @Test
    void combatMoveRowAcceptsHeldItemInteract() {
        assertTrue(DTarget.class.isAssignableFrom(NFightWnd.MoveItem.class));
    }

    private static final class Rec extends Widget {
        String msg;
        Object[] args;

        @Override
        public void wdgmsg(String msg, Object... args) {
            this.msg = msg;
            this.args = args;
        }
    }
}
