package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AreaListSelectionTest {
    @Test
    void rebuildWithoutExplicitIdKeepsCurrentArea() {
        int requested = AreaListSelection.implicitRebuildId(7);
        assertEquals(7, requested);
        assertEquals(Integer.valueOf(7), AreaListSelection.keptAreaId(requested, List.of(1, 7, 9)));
    }

    @Test
    void simulatedRenameRebuildDoesNotJumpToLastItem() {
        int renamedId = 3;
        List<Integer> afterRename = List.of(1, 3, 20);
        int requested = AreaListSelection.implicitRebuildId(renamedId);
        assertEquals(Integer.valueOf(3), AreaListSelection.keptAreaId(requested, afterRename));
        assertNotEquals(Integer.valueOf(20), AreaListSelection.keptAreaId(requested, afterRename));
    }

    @Test
    void deletedAreaFallsBackToLastItem() {
        int requested = AreaListSelection.implicitRebuildId(7);
        assertNull(AreaListSelection.keptAreaId(requested, List.of(1, 9)));
    }

    @Test
    void folderRowHasNoAreaIdAndFallsBack() {
        int requested = AreaListSelection.implicitRebuildId(null);
        assertEquals(-1, requested);
        assertNull(AreaListSelection.keptAreaId(requested, List.of(1, 7, 9)));
    }

    @Test
    void folderNavigationDoesNotLockSelectionIfAreaNotInNewPath() {
        int requested = AreaListSelection.implicitRebuildId(7);
        assertNull(AreaListSelection.keptAreaId(requested, List.of(10, 11)));
    }

    @Test
    void explicitIdWinsOverPreviousSelection() {
        assertEquals(Integer.valueOf(9), AreaListSelection.keptAreaId(9, List.of(1, 7, 9)));
    }

    @Test
    void showPathWithoutIdUsesImplicitRebuildId() throws Exception {
        String src = new String(Files.readAllBytes(Paths.get("src/nurgling/widgets/NAreasWidget.java")), StandardCharsets.UTF_8);
        assertTrue(src.contains("AreaListSelection.implicitRebuildId"), src);
        assertTrue(src.contains("AreaListSelection.keptAreaId"), src);
        assertFalse(src.contains("showPath(path, -1)"), src);
    }
}
