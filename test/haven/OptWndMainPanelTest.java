package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class OptWndMainPanelTest {
    @Test
    void mainMenuDoesNotRequestAnOuterScrollbar() {
        assertFalse(new OptWnd.MainPanel().usesOuterScroll());
    }
}
