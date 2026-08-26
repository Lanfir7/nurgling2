package nurgling.conf;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NAreaRadMigrateTest {

    static final String OLD = "gfx/kritter/wildgoat/wildgoat";
    static final String NEW = "gfx/kritter/goat/wildgoat";

    @Test
    void oldPathOnlyBecomesNewWithSameVisAndRadius() {
        ArrayList<NAreaRad> rads = new ArrayList<>();
        NAreaRad old = new NAreaRad(OLD, 77);
        old.vis = false;
        rads.add(old);

        assertTrue(NAreaRad.migrateList(rads));
        assertEquals(1, rads.size());
        assertEquals(NEW, rads.get(0).name);
        assertEquals(77, rads.get(0).radius);
        assertFalse(rads.get(0).vis);
    }

    @Test
    void bothPathsKeepsNewAndDropsOld() {
        ArrayList<NAreaRad> rads = new ArrayList<>();
        NAreaRad kept = new NAreaRad(NEW, 120);
        kept.vis = true;
        rads.add(new NAreaRad(OLD, 50));
        rads.add(kept);

        assertTrue(NAreaRad.migrateList(rads));
        assertEquals(1, rads.size());
        assertEquals(NEW, rads.get(0).name);
        assertEquals(120, rads.get(0).radius);
        assertTrue(rads.get(0).vis);
    }

    @Test
    void alreadyCorrectIsUnchanged() {
        ArrayList<NAreaRad> rads = new ArrayList<>();
        rads.add(new NAreaRad(NEW, 100));

        assertFalse(NAreaRad.migrateList(rads));
        assertEquals(1, rads.size());
        assertEquals(NEW, rads.get(0).name);
        assertEquals(100, rads.get(0).radius);
    }

    @Test
    void missingAddsDefaultGoat() {
        ArrayList<NAreaRad> rads = new ArrayList<>();
        rads.add(new NAreaRad("gfx/kritter/boar/boar", 100));

        assertTrue(NAreaRad.migrateList(rads));
        NAreaRad goat = null;
        for (NAreaRad r : rads) {
            if (NEW.equals(r.name)) goat = r;
        }
        assertEquals(NEW, goat.name);
        assertEquals(100, goat.radius);
        assertTrue(goat.vis);
        assertEquals(2, rads.size());
    }
}
