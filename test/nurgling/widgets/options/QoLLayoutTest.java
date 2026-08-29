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

    @Test
    void normalSettingsViewportKeepsTwoReadableColumns() {
        QoLLayout layout = QoLLayout.forSettings(540, 12, 2, 720, 610);

        assertEquals(2, layout.columns);
        assertEquals(264, layout.cardWidth);
    }

    @Test
    void indentedCheckboxFitsInsideMinimumCardWidth() {
        assertEquals(215, QoLLayout.optionWidth(245, 22, 230, 8));
    }

    @Test
    void checkboxCannotDriftLeftOfCardInset() {
        assertEquals(12, QoLLayout.optionX(-5, 12));
        assertEquals(22, QoLLayout.optionX(22, 12));
    }
}
