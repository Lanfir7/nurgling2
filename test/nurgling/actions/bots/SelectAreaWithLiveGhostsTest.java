package nurgling.actions.bots;

import nurgling.NHitBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectAreaWithLiveGhostsTest {
    @Test
    void cupboardCanPreviewWithoutWaitingForPlob() {
        NHitBox box = SelectAreaWithLiveGhosts.hitBoxForBuilding("Cupboard", null);
        String res = SelectAreaWithLiveGhosts.resourceNameForBuilding("Cupboard");
        assertNotNull(box);
        assertEquals("gfx/terobjs/cupboard", res);
        assertTrue(SelectAreaWithLiveGhosts.canPreviewWithoutPlob(box, res));
    }

    @Test
    void customHitBoxIsUsedWhenProvided() {
        NHitBox custom = NHitBox.findCustom("gfx/terobjs/moundbed");
        assertSame(custom, SelectAreaWithLiveGhosts.hitBoxForBuilding("Mound Bed", custom));
        assertTrue(SelectAreaWithLiveGhosts.canPreviewWithoutPlob(custom, "gfx/terobjs/moundbed"));
    }

    @Test
    void unknownBuildingStillNeedsPlob() {
        assertFalse(SelectAreaWithLiveGhosts.canPreviewWithoutPlob(
            SelectAreaWithLiveGhosts.hitBoxForBuilding("Dream Catcher", null),
            SelectAreaWithLiveGhosts.resourceNameForBuilding("Dream Catcher")));
    }

    @Test
    void selectionProceedsWithoutHolograms() {
        assertTrue(SelectAreaWithLiveGhosts.selectionCanProceed(1, false));
        assertFalse(SelectAreaWithLiveGhosts.selectionCanProceed(0, false));
        assertFalse(SelectAreaWithLiveGhosts.selectionCanProceed(1, true));
    }

    @Test
    void activateBuildMenuSkipsUnloadedPaginae() {
        assertFalse(SelectAreaWithLiveGhosts.matchesBuildButton(null, "Cupboard"));
        assertFalse(SelectAreaWithLiveGhosts.matchesBuildButton(() -> {
            throw new haven.Loading("pagina still loading");
        }, "Cupboard"));
    }
}
