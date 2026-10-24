package nurgling.widgets;

import haven.Coord;
import nurgling.NConfig;
import nurgling.actions.bots.OreWatch;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TunnelingDialogOreSettingsTest {
    @Test
    void missingSettingsAreDisabledButOfferAllKnownOres() {
        TunnelingDialog.OreSettings missing = TunnelingDialog.OreSettings.from("");
        assertFalse(missing.enabled);
        assertEquals(OreWatch.ores().keySet(), missing.ores);
    }

    @Test
    void explicitEmptyAndInvalidKeysRoundTripWithoutWidening() {
        Map<String, Object> saved = new HashMap<>();
        saved.put("enabled", true);
        saved.put("ores", Arrays.asList("hematite", "not-an-ore"));
        TunnelingDialog.OreSettings loaded = TunnelingDialog.OreSettings.from(saved);
        assertTrue(loaded.enabled);
        assertEquals(Set.of("hematite"), loaded.ores);
        assertEquals(loaded.ores, TunnelingDialog.OreSettings.from(loaded.saved()).ores);

        saved.put("ores", Arrays.asList());
        assertTrue(TunnelingDialog.OreSettings.from(saved).ores.isEmpty());
    }

    @Test
    void pickerStaysWithinParentNearEdges() {
        NConfig previous = NConfig.current;
        NConfig.current = new NConfig();
        try {
        Coord parent = new Coord(500, 400);
        Coord picker = new Coord(200, 300);
        Coord placed = TunnelingDialog.pickerPosition(new Coord(450, 350),
                new Coord(300, 200), picker, parent);
        assertTrue(placed.x >= 0 && placed.x + picker.x <= parent.x);
        assertTrue(placed.y >= 0 && placed.y + picker.y <= parent.y);
        } finally {
            NConfig.current = previous;
        }
    }
}
