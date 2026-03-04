package nurgling.actions;

import haven.Coord2d;
import haven.Gob;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;

public class ChopAndRemoveStump implements Action {
    private static final NAlias STUMP_ALIAS = new NAlias("stump");
    private static final long WAIT_TIMEOUT_MS = 180000L;
    private static final double SEARCH_RADIUS = 25.0;

    private final long treeId;
    private final Coord2d treePos;

    public ChopAndRemoveStump(long treeId, Coord2d treePos) {
        this.treeId = treeId;
        this.treePos = treePos;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        Gob stump = waitForStump();
        if (stump == null) {
            return Results.ERROR("Stump not found after chop");
        }
        return new RemoveStump(stump.id).run(gui);
    }

    private Gob waitForStump() throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Gob tree = Finder.findGob(treeId);
            if (tree != null && isStump(tree)) {
                return tree;
            }
            Gob stump = Finder.findGob(treePos, STUMP_ALIAS, null, SEARCH_RADIUS);
            if (stump != null) {
                return stump;
            }
            Thread.sleep(200);
        }
        return null;
    }

    private static boolean isStump(Gob gob) {
        return gob != null && gob.ngob != null && gob.ngob.name != null && gob.ngob.name.contains("stump");
    }
}
