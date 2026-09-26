package nurgling.areas;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AreaSnapshotPileFillDirectionTest {
    @Test void directionDifferenceIsRoutingOnly() {
        NArea before = area(PileFillDirection.LEFT_TO_RIGHT);
        NArea after = area(PileFillDirection.BOTTOM_TO_TOP);
        assertEquals(EnumSet.of(AreaFieldGroup.ROUTING),
            AreaSnapshot.diff(AreaSnapshot.of(before), AreaSnapshot.of(after)));
    }

    @Test void routingMergeTakesRemoteDirection() {
        AreaSnapshot local = AreaSnapshot.of(area(PileFillDirection.LEFT_TO_RIGHT));
        AreaSnapshot remote = AreaSnapshot.of(area(PileFillDirection.TOP_TO_BOTTOM));
        JSONObject merged = AreaSnapshot.buildMergedJson(
            1, "uuid", local, remote, EnumSet.of(AreaFieldGroup.ROUTING), 2);
        assertEquals("TOP_TO_BOTTOM", merged.getString(NArea.PILE_FILL_DIRECTION_JSON));
    }

    @Test void qualitySurvivesConcurrentAreaChanges() {
        NArea localArea = area(PileFillDirection.LEFT_TO_RIGHT);
        NArea remoteArea = area(PileFillDirection.LEFT_TO_RIGHT);
        localArea.maxQuality = 145;
        remoteArea.maxQuality = 140;
        AreaSnapshot local = AreaSnapshot.of(localArea);
        AreaSnapshot remote = AreaSnapshot.of(remoteArea);
        JSONObject merged = AreaSnapshot.buildMergedJson(
            1, "uuid", local, remote, EnumSet.of(AreaFieldGroup.COSMETIC), 2);
        assertEquals(145, merged.getInt(NArea.MAX_QUALITY_JSON));
    }

    @Test void resetQualitySurvivesVersionMerge() {
        NArea localArea = area(PileFillDirection.LEFT_TO_RIGHT);
        NArea remoteArea = area(PileFillDirection.LEFT_TO_RIGHT);
        localArea.maxQuality = -1;
        remoteArea.maxQuality = 145;
        JSONObject merged = AreaSnapshot.buildMergedJson(
            1, "uuid", AreaSnapshot.of(localArea), AreaSnapshot.of(remoteArea),
            EnumSet.noneOf(AreaFieldGroup.class), 2);
        assertEquals(-1, merged.getInt(NArea.MAX_QUALITY_JSON));
    }

    private NArea area(PileFillDirection direction) {
        NArea area = new NArea("zone");
        area.id = 7;
        area.space = new NArea.Space();
        area.color = new Color(1, 2, 3, 4);
        area.pileFillDirection = direction;
        return area;
    }
}
