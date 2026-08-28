package nurgling.widgets.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class QCondTest {
    @Test
    void bringExposesDisplayItem() {
        QCond cond = new QCond(7, false, "Bring a Board of Oak to Jenny", null);

        assertEquals(QCond.Verb.BRING, cond.verb);
        assertEquals("board of oak", cond.itemTarget);
    }

    @Test
    void pickExposesDisplayItemWithoutChangingGobTarget() {
        QCond cond = new QCond(7, false, "Pick a Chiming Bluebell", null);

        assertEquals("chiming bluebell", cond.itemTarget);
        assertNotNull(cond.gobTarget);
    }

    @Test
    void createExposesDisplayItem() {
        assertEquals("stone axe", new QCond(7, false, "Create a Stone Axe", null).itemTarget);
    }

    @Test
    void malformedObjectivesHaveNoItemTarget() {
        assertNull(new QCond(7, false, "Create", null).itemTarget);
        assertNull(new QCond(7, false, "Bring to Jenny", null).itemTarget);
    }
}
