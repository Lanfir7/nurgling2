package nurgling.map;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedMarkerCodeTest {
    @Test
    void playerCanRenameSharedBarterMarkerWithoutChangingPayload() {
        String payload = "-NGM1-TX3PR-IWGXW-CFT3S-W6BN3-52K25-QAAEA-AX777-4QABA-ZV2KI";
        SharedMarkerCode.Marker original = SharedMarkerCode.decode("Barter Stand" + payload);
        SharedMarkerCode.Marker renamed = SharedMarkerCode.decode("Мой бартер стенд такой то" + payload);
        assertEquals("Barter Stand", original.name);
        assertEquals("Мой бартер стенд такой то", renamed.name);
        assertEquals(original.gridId, renamed.gridId);
        assertEquals(original.local, renamed.local);
        assertEquals(original.color, renamed.color);
    }

    @Test
    void roundTripPreservesVisibleNameAndMapLocation() {
        String encoded = SharedMarkerCode.encode(
            "Market Ланфира", "world-16", 8_765_432_109L,
            new Coord(37, 82), new Color(12, 34, 56));

        assertTrue(encoded.matches("Market Ланфира-NGM1-(?:[A-Z2-7]{5}-){8}[A-Z2-7]{5}"));

        SharedMarkerCode.Marker marker = SharedMarkerCode.decode(encoded);
        assertEquals("Market Ланфира", marker.name);
        assertEquals(8_765_432_109L, marker.gridId);
        assertEquals(new Coord(37, 82), marker.local);
        assertEquals(new Color(12, 34, 56).getRGB(), marker.color.getRGB());
        assertTrue(marker.belongsTo("world-16"));
        assertFalse(marker.belongsTo("world-15"));
    }

    @Test
    void discordBackticksAroundCodeDoNotBecomePartOfTheName() {
        String encoded = SharedMarkerCode.encode(
            "Northern shop", "world-16", 42L, new Coord(1, 2), Color.YELLOW);
        int codeStart = encoded.indexOf("NGM1-");
        String pasted = encoded.substring(0, codeStart) + "`" + encoded.substring(codeStart) + "`";

        assertEquals("Northern shop", SharedMarkerCode.decode(pasted).name);
    }

    @Test
    void changedPayloadIsRejectedByChecksum() {
        String encoded = SharedMarkerCode.encode(
            "Shop", "world-16", 42L, new Coord(1, 2), Color.YELLOW);
        int last = encoded.length() - 1;
        char replacement = encoded.charAt(last) == 'A' ? 'B' : 'A';
        String changed = encoded.substring(0, last) + replacement;

        assertThrows(IllegalArgumentException.class, () -> SharedMarkerCode.decode(changed));
    }

    @Test
    void textWithoutCompleteMarkerCodeIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> SharedMarkerCode.decode("Shop-NGM1-6FJQ8-K3M2X"));
    }
}
