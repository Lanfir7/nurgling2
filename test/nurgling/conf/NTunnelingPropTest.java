package nurgling.conf;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NTunnelingPropTest {

    @Test
    void jsonRoundTripsPassageWidthAndLateral() {
        NTunnelingProp original = new NTunnelingProp("bob", "chr2");
        original.direction = 2;
        original.tunnelSide = 1;
        original.supportType = 3;
        original.wingOption = 3;
        original.wingSide = 1;
        original.maxLateral = 8;
        original.doubleTunnel = true;
        original.wingEast = true;
        original.wingWest = true;

        JSONObject json = original.toJson();
        NTunnelingProp loaded = new NTunnelingProp(new HashMap<>(json.toMap()));

        assertEquals(2, loaded.direction);
        assertEquals(1, loaded.tunnelSide);
        assertEquals(3, loaded.supportType);
        assertEquals(3, loaded.wingOption);
        assertEquals(1, loaded.wingSide);
        assertEquals(8, loaded.maxLateral);
        assertTrue(loaded.doubleTunnel);
        assertTrue(loaded.wingEast);
        assertTrue(loaded.wingWest);
    }

    @Test
    void missingWidthStaysSingleTile() {
        HashMap<String, Object> raw = new HashMap<>();
        raw.put("username", "alice");
        raw.put("chrid", "chr1");
        raw.put("tunnelSide", 1);

        NTunnelingProp loaded = new NTunnelingProp(raw);

        assertEquals(1, loaded.tunnelSide);
        assertFalse(loaded.doubleTunnel);
        assertEquals(5, loaded.maxLateral);
        assertEquals(2, loaded.supportType);
    }
}
