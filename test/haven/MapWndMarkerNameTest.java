package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapWndMarkerNameTest {
    @Test
    void resourceBasenameBecomesReadableMarkerName() {
        assertEquals("Rabbit Hutch", MarkerNameFormatter.prettify("rabbit_hutch"));
        assertEquals("Cave Ladder", MarkerNameFormatter.prettify("cave-ladder"));
        assertEquals("Rabbithutch", MarkerNameFormatter.prettify("rabbithutch"));
    }
}
