package nurgling.db;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageTableInfoTest {
    @Test
    void tilesBetweenRoundsHypotenuse() {
        Coord origin = Coord.of(0, 0);
        assertEquals(0, StorageTableInfo.tilesBetween(origin, origin));
        assertEquals(10, StorageTableInfo.tilesBetween(origin,
                Coord.of(10 * StorageOrphanPolicy.POSRES_PER_TILE, 0)));
        assertEquals(-1, StorageTableInfo.tilesBetween(origin, null));
    }

    @Test
    void distanceLabelUsesDashForUnknown() {
        assertEquals("—", StorageTableInfo.distanceLabel(-1));
        assertEquals("12", StorageTableInfo.distanceLabel(12));
    }

    @Test
    void containerTitleUsesContcapsThenPretty() {
        assertEquals("Chest", StorageTableInfo.containerTitle("gfx/terobjs/chest"));
        assertEquals("Cupboard", StorageTableInfo.containerTitle("gfx/terobjs/cupboard"));
        assertEquals("—", StorageTableInfo.containerTitle(null));
        assertEquals("Barrel", StorageTableInfo.containerTitle("gfx/terobjs/barrel"));
    }

    @Test
    void storageLabelShowsExtraCount() {
        assertEquals("—", StorageTableInfo.storageLabel(null, List.of()));
        assertEquals("Chest", StorageTableInfo.storageLabel("Chest", List.of("Chest", "Chest")));
        assertEquals("Chest +1", StorageTableInfo.storageLabel("Chest", List.of("Chest", "Cupboard")));
    }
}
