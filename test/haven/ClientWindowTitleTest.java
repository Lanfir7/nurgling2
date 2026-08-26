package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientWindowTitleTest {
    @Test
    void titleUsesNurglingEvolution() {
        assertEquals("Haven & Hearth (Nurgling Evolution)", Client.windowTitle(null));
        assertEquals("Haven & Hearth (Nurgling Evolution) \u2013 Lanfir", Client.windowTitle("Lanfir"));
    }
}
