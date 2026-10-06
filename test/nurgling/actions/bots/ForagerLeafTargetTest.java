package nurgling.actions.bots;

import haven.Coord2d;
import haven.Gob;
import nurgling.routes.ForagerAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForagerLeafTargetTest {

    @Test
    void emptyTreeIsNotApproachedForLeaves() {
        Gob maple = gob("gfx/terobjs/trees/maple");
        ForagerAction leaves = leafAction();

        assertFalse(Forager.leafTargetReady(leaves, maple));
    }

    @Test
    void barkOnTheSameTreeIsStillApproached() {
        Gob maple = gob("gfx/terobjs/trees/maple");
        ForagerAction bark = new ForagerAction("gfx/terobjs/trees/maple", ForagerAction.ActionType.FLOWER_ACTION, "Take bark");
        bark.sourceItemName = "Treebark";
        bark.sourceItemResource = "gfx/invobjs/bark";

        assertTrue(Forager.leafTargetReady(bark, maple));
    }

    @Test
    void leafItemOnANonTreeStaysEligible() {
        Gob lettuce = gob("gfx/terobjs/plants/lettuce");
        ForagerAction leaves = leafAction();

        assertTrue(Forager.leafTargetReady(leaves, lettuce));
    }

    @Test
    void knownLeafIconCountsEvenWithoutTheWordInTheAction() {
        Gob maple = gob("gfx/terobjs/trees/maple");
        ForagerAction leaves = new ForagerAction("gfx/terobjs/trees/maple", ForagerAction.ActionType.FLOWER_ACTION, "Pick");
        leaves.sourceItemResource = "gfx/invobjs/leaf-maple";

        assertFalse(Forager.leafTargetReady(leaves, maple));
    }

    private static ForagerAction leafAction() {
        ForagerAction action = new ForagerAction("gfx/terobjs/trees/maple", ForagerAction.ActionType.FLOWER_ACTION, "Pick leaf");
        action.sourceItemName = "Maple Leaf";
        action.sourceItemResource = "gfx/invobjs/leaf-maple";
        return action;
    }

    private static Gob gob(String resName) {
        Gob gob = new Gob(null, Coord2d.of(0, 0), 1);
        gob.ngob.name = resName;
        return gob;
    }
}
