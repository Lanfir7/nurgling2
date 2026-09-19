package nurgling.overlays;

import nurgling.conf.NCritterCircleConf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NCritterCircleTest {
    private static final String STOAT = "gfx/kritter/stoat/stoat";
    private static final String SQUIRREL = "gfx/kritter/squirrel/squirrel";

    @Test
    void dumbledoreIsRecognizedAsCatchableCritter() {
        assertTrue(NCritterCircle.isCritter("gfx/kritter/dumbledore/dumbledore"));
    }

    @Test
    void woodScorpionIsRecognizedAsCatchableCritter() {
        assertTrue(NCritterCircle.isCritter("gfx/kritter/woodscorpion/woodscorpion"));
    }

    @Test
    void stoatIsHitboxNotForageCritter() {
        assertFalse(NCritterCircle.isCritter(STOAT));
        assertTrue(NCritterCircle.hasCircle(STOAT));
        assertTrue(NCritterCircle.keepWhenDead(STOAT));
    }

    @Test
    void squirrelStaysForageCritterAndDropsCircleWhenDead() {
        assertTrue(NCritterCircle.isCritter(SQUIRREL));
        assertTrue(NCritterCircle.hasCircle(SQUIRREL));
        assertFalse(NCritterCircle.keepWhenDead(SQUIRREL));
    }

    @Test
    void canAttachCircleKeepsDeadStoatAndRejectsDeadSquirrel() {
        assertTrue(NCritterCircle.canAttachCircle(null, STOAT));
        assertTrue(NCritterCircle.canAttachCircle("gfx/kritter/stoat/dead", STOAT));
        assertTrue(NCritterCircle.canAttachCircle(null, SQUIRREL));
        assertFalse(NCritterCircle.canAttachCircle("gfx/kritter/squirrel/dead", SQUIRREL));
        assertFalse(NCritterCircle.canAttachCircle("idle", "gfx/kritter/fox/fox"));
    }

    @Test
    void defaultConfigsAndCirclePathsIncludeStoat() {
        assertTrue(NCritterCircle.circlePaths().contains(STOAT));
        assertTrue(NCritterCircle.circlePaths().contains(SQUIRREL));
        List<String> paths = new ArrayList<String>();
        for (NCritterCircleConf conf : NCritterCircle.buildDefaultConfigs())
            paths.add(conf.path);
        assertTrue(paths.contains(STOAT));
        assertEquals(NCritterCircle.circlePaths().size(), paths.size());
    }

    @Test
    void appendMissingConfigsAddsStoatOnce() {
        ArrayList<Object> settings = new ArrayList<Object>();
        NCritterCircle.appendMissingConfigs(settings, NCritterCircle.circlePaths());
        int first = countPath(settings, STOAT);
        NCritterCircle.appendMissingConfigs(settings, NCritterCircle.circlePaths());
        assertEquals(1, first);
        assertEquals(1, countPath(settings, STOAT));
    }

    private static int countPath(List<Object> settings, String path) {
        int n = 0;
        for (Object item : settings) {
            if (item instanceof NCritterCircleConf && path.equals(((NCritterCircleConf) item).path))
                n++;
        }
        return n;
    }
}
