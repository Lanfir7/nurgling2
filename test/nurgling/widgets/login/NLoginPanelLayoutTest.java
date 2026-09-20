package nurgling.widgets.login;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NLoginPanelLayoutTest {
    @Test
    void accountRowsRespectDefaultAndSafeMinimum() {
        int rowHeight = 30;
        int defaultRows = 8;
        int reserved = 100;
        assertEquals(defaultRows, NLoginPanel.accountRowsForBudget(-1, reserved, rowHeight, defaultRows));
        assertEquals(3, NLoginPanel.accountRowsForBudget(reserved + 1, reserved, rowHeight, defaultRows));
        assertEquals(5, NLoginPanel.accountRowsForBudget(reserved + (5 * rowHeight), reserved, rowHeight, defaultRows));
    }
}
