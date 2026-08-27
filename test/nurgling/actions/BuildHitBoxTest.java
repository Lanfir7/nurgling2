package nurgling.actions;

import nurgling.NHitBox;
import nurgling.actions.bots.SelectAreaWithLiveGhosts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BuildHitBoxTest {
    @Test
    void cupboardHitBoxComesFromCatalogWhenPlobGhostIsNotReady() {
        NHitBox box = Build.resolveHitBox(null, null, "Cupboard");
        assertNotNull(box);
        assertEquals(SelectAreaWithLiveGhosts.hitBoxForBuilding("Cupboard", null).begin, box.begin);
        assertEquals(SelectAreaWithLiveGhosts.hitBoxForBuilding("Cupboard", null).end, box.end);
    }

    @Test
    void customHitBoxWinsOverCatalog() {
        NHitBox custom = NHitBox.findCustom("gfx/terobjs/moundbed");
        assertSame(custom, Build.resolveHitBox(null, custom, "Cupboard"));
    }

    @Test
    void readyPlobHitBoxWins() {
        NHitBox fromPlob = NHitBox.findCustom("gfx/terobjs/cupboard");
        NHitBox custom = NHitBox.findCustom("gfx/terobjs/moundbed");
        assertSame(fromPlob, Build.resolveHitBox(fromPlob, custom, "Cupboard"));
    }
}
