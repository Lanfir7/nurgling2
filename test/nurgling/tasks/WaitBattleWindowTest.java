package nurgling.tasks;

import haven.Fightview;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class WaitBattleWindowTest {
    @Test
    void waitsWhileFightviewHasNotBeenCreated() {
        WaitBattleWindow wait = new WaitBattleWindow() {
            @Override
            Fightview fightview() {
                return null;
            }
        };

        assertFalse(wait.check());
    }
}
