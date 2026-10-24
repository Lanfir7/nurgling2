package nurgling.widgets;

import nurgling.NConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TunnelingDialogRoadSettingsTest {
    @TempDir Path temp;
    private final NConfig prior = NConfig.current;

    @AfterEach void restore() { NConfig.current = prior; }

    private void config() throws Exception {
        NConfig current = new NConfig();
        Field path = NConfig.class.getDeclaredField("path");
        path.setAccessible(true);
        path.set(current, temp.resolve("tunneler.json").toString());
        NConfig.current = current;
    }

    private static void invoke(TunnelingDialog dialog, String method) throws Exception {
        Method action = TunnelingDialog.class.getDeclaredMethod(method);
        action.setAccessible(true);
        action.invoke(dialog);
    }

    @Test
    void missingRoadSettingIsOffAndSpacingIsClamped() {
        TunnelingDialog.RoadSettings old = TunnelingDialog.RoadSettings.from("");
        assertFalse(old.enabled);
        assertEquals(30, old.spacing);
        assertFalse(TunnelingDialog.RoadSettings.from("{broken").enabled);
        assertEquals(5, TunnelingDialog.RoadSettings.from(Map.of("enabled", true, "spacing", -8)).spacing);
        assertEquals(30, TunnelingDialog.RoadSettings.from(Map.of("enabled", true, "spacing", 99)).spacing);
    }

    @Test
    void cancelKeepsPriorSettingsAndStartPublishesOnlyRoadChoice() throws Exception {
        config();
        NConfig.set(NConfig.Key.tunnelingOreStop, "old ore choice");
        NConfig.set(NConfig.Key.milestones, "old routes");
        TunnelingDialog cancel = new TunnelingDialog();
        boolean[] cancelledRoad = {false};
        int[] cancelledSpacing = {27};
        cancel.setRoadReferences(cancelledRoad, cancelledSpacing);
        invoke(cancel, "cancel");
        assertFalse(cancelledRoad[0]);
        assertEquals(27, cancelledSpacing[0]);
        assertEquals("", NConfig.get(NConfig.Key.tunnelingRoad));

        TunnelingDialog start = new TunnelingDialog();
        Field road = TunnelingDialog.class.getDeclaredField("buildRoad");
        road.setAccessible(true);
        road.set(start, true);
        Field spacing = TunnelingDialog.class.getDeclaredField("roadSpacing");
        spacing.setAccessible(true);
        spacing.set(start, 99);
        boolean[] selectedRoad = {false};
        int[] selectedSpacing = {0};
        start.setRoadReferences(selectedRoad, selectedSpacing);
        invoke(start, "confirm");
        assertTrue(selectedRoad[0]);
        assertEquals(30, selectedSpacing[0]);
        assertEquals(true, ((Map<?, ?>) NConfig.get(NConfig.Key.tunnelingRoad)).get("enabled"));
        assertEquals(false, ((Map<?, ?>) NConfig.get(NConfig.Key.tunnelingOreStop)).get("enabled"));
        assertEquals("old routes", NConfig.get(NConfig.Key.milestones));
    }
}
