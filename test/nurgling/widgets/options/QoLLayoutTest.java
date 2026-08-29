package nurgling.widgets.options;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QoLLayoutTest {
    @Test
    void wideUsesTwoColumns() {
        QoLLayout layout = QoLLayout.calculate(700, 12, 280, 720, 610);

        assertEquals(2, layout.columns);
        assertEquals(layout.leftPosition.y, layout.rightPosition.y);
        assertEquals(344, layout.cardWidth);
        assertEquals(720, layout.contentHeight);
    }

    @Test
    void narrowStacksBothCards() {
        QoLLayout layout = QoLLayout.calculate(480, 12, 280, 720, 610);

        assertEquals(1, layout.columns);
        assertEquals(480, layout.cardWidth);
        assertTrue(layout.rightPosition.y >= 732);
        assertTrue(layout.contentHeight >= 1342);
    }
}
