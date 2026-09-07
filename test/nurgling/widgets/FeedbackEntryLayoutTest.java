package nurgling.widgets;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedbackEntryLayoutTest {
    @Test
    void feedbackButtonSitsDirectlyBelowNurglingSettings() {
        assertEquals(Coord.of(210, 35),
                FeedbackEntryLayout.below(Coord.of(210, 0), Coord.of(200, 30), 5));
    }
}
