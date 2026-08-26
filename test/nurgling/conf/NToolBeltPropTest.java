package nurgling.conf;

import haven.KeyMatch;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NToolBeltPropTest {
    @Test
    void belt0DefaultsAreNumberRowThroughEquals() {
        int[] expect = {
                KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4,
                KeyEvent.VK_5, KeyEvent.VK_6, KeyEvent.VK_7, KeyEvent.VK_8,
                KeyEvent.VK_9, KeyEvent.VK_0, KeyEvent.VK_MINUS, KeyEvent.VK_EQUALS
        };
        for (int i = 0; i < expect.length; i++) {
            assertEquals(expect[i], NToolBeltProp.defaultKey("belt0", i).code, "slot " + i);
            assertEquals(0, NToolBeltProp.defaultKey("belt0", i).modmatch);
        }
    }

    @Test
    void otherBeltsHaveNoDefaultKeys() {
        assertEquals(KeyMatch.nil, NToolBeltProp.defaultKey("belt1", 0));
        assertEquals(KeyMatch.nil, NToolBeltProp.defaultKey("belt0", 12));
    }
}
