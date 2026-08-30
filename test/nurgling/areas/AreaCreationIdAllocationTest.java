package nurgling.areas;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AreaCreationIdAllocationTest {
    @Test void duplicateAllocationSkipsTombstonedDatabaseIdsBeyondLiveAreas() {
        NArea liveArea = new NArea("live");
        liveArea.id = 4;

        assertEquals(42, AreaCreation.nextAvailableAreaId(
                Collections.singletonList(liveArea), 41));
    }
}
