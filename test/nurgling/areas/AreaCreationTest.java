package nurgling.areas;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class AreaCreationTest {
    @Test void initializeNewResetsSyncIdentityAndMarksEveryGroup() {
        NArea area = new NArea("zone");
        area.uuid = "old";
        area.version = 9;
        area.baselineVersion = 9;
        area.synced = true;
        area.lastUpdated = 123L;

        AreaCreation.initializeNew(area);

        assertNotNull(area.uuid);
        assertNotEquals("old", area.uuid);
        assertEquals(0, area.version);
        assertEquals(0, area.baselineVersion);
        assertNull(area.baselineSnapshot);
        assertFalse(area.synced);
        assertEquals(0L, area.lastUpdated);
        assertEquals(EnumSet.allOf(AreaFieldGroup.class), area.dirtyGroups);
    }

    @Test void duplicateKeepsDirectionButGetsIndependentIdentity() {
        NArea source = new NArea("source");
        source.id = 3;
        source.space = new NArea.Space();
        source.uuid = "source-uuid";
        source.version = 7;
        source.pileFillDirection = PileFillDirection.RIGHT_TO_LEFT;
        source.color = new Color(0, 0, 0, 56);

        NArea copy = AreaCreation.duplicate(source, 4, "source");

        assertEquals(4, copy.id);
        assertEquals("source", copy.name);
        assertEquals(PileFillDirection.RIGHT_TO_LEFT, copy.pileFillDirection);
        assertNotEquals(source.uuid, copy.uuid);
        assertEquals(0, copy.version);
        assertEquals(EnumSet.allOf(AreaFieldGroup.class), copy.dirtyGroups);
        assertNotEquals(source.color, copy.color);
    }
}
