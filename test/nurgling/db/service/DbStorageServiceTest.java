package nurgling.db.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DbStorageServiceTest {
    @Test
    void sharedMapCleanupKeepsUserMarkersOutOfDeletionScope() {
        List<String> tables = DbStorageService.sharedMapTables();

        assertEquals(List.of("map_grids", "map_grid_placements"), tables);
        assertFalse(tables.contains("map_markers"));
    }

    @Test
    void diskUsageUsesReadableBinaryUnits() {
        assertEquals("?", DbStorageService.humanBytes(-1));
        assertEquals("1023 B", DbStorageService.humanBytes(1023));
        assertEquals("1.0 KB", DbStorageService.humanBytes(1024));
        assertEquals("1.5 MB", DbStorageService.humanBytes(1572864));
    }
}
