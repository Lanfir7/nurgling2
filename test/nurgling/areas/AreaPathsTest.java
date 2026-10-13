package nurgling.areas;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class AreaPathsTest {
    @Test
    void renamingAndMovingKeepSiblingPrefixesSeparate() {
        assertEquals("/Barn/Tools", AreaPaths.reparent("/Base/Tools", "/Base", "/Barn"));
        assertEquals("/Baseball/Tools", AreaPaths.reparent("/Baseball/Tools", "/Base", "/Barn"));
        assertEquals("/Barn", AreaPaths.reparent("/Base", "/Base", "/Barn"));
        assertEquals(Arrays.asList("/Base", "/Baseball"),
                AreaPaths.childFolders(Arrays.asList("/Base/Tools", "/Baseball/Tools"), ""));
    }

    @Test
    void rejectsDescendantMovesAndAcceptsSiblingDestination() {
        assertFalse(AreaPaths.canMoveFolder("/Base", "/Base/Tools"));
        assertFalse(AreaPaths.canDrop(Collections.emptyList(),
                Collections.singleton("/Base"), "/Base/Tools"));
        assertTrue(AreaPaths.canDrop(Collections.emptyList(),
                Collections.singleton("/Base"), "/Baseball"));
        assertFalse(AreaPaths.canDrop(Collections.singleton("/Base"),
                Collections.emptyList(), "/Base"));
    }

    @Test
    void normalizesTypedFolderNamesWithoutEmptySegments() {
        assertEquals("Farm/Fields", AreaPaths.clean(" / Farm // Fields / "));
        assertEquals("/Home/Farm/Fields", AreaPaths.join("/Home", AreaPaths.clean("/ Farm // Fields")));
    }
}
