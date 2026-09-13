package nurgling.cookbook;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SmokeWoodTest {

    @BeforeEach
    void resetWarningFlag() {
        SmokeWood.resetSessionWarning();
    }

    @AfterEach
    void resetWarningFlagAfter() {
        SmokeWood.resetSessionWarning();
    }

    @Test
    void extractNullListIsEmpty() {
        assertTrue(SmokeWood.extract(null).isEmpty());
    }

    @Test
    void extractSkipsNullEntries() {
        List<Object> infos = new ArrayList<Object>();
        infos.add(null);
        infos.add(new nurgling.cookbook.smoked.Smoke("Oak", Double.valueOf(1.0)));
        infos.add(null);
        List<SmokeWood> woods = SmokeWood.extract(infos);
        assertEquals(1, woods.size());
        assertEquals("Oak", woods.get(0).name);
        assertEquals(100.0, woods.get(0).percentage, 0.0);
    }

    @Test
    void missingPercentDefaultsToOneHundred() {
        nurgling.cookbook.smoked.Smoke oak = new nurgling.cookbook.smoked.Smoke("Oak", null);
        List<SmokeWood> woods = SmokeWood.extract(Collections.singletonList(oak));
        assertEquals(1, woods.size());
        assertEquals(100.0, woods.get(0).percentage, 0.0);
    }

    @Test
    void unreadablePercentDefaultsToOneHundred() {
        nurgling.cookbook.smoked.Smoke oak = new nurgling.cookbook.smoked.Smoke("Oak", null);
        oak.val = null;
        oak.percentage = null;
        List<SmokeWood> woods = SmokeWood.extract(Collections.singletonList(oak));
        assertEquals(100.0, woods.get(0).percentage, 0.0);
    }

    @Test
    void percentageFieldUsedWhenValAbsent() {
        nurgling.cookbook.smoked.Smoke oak = new nurgling.cookbook.smoked.Smoke("Oak", null, Double.valueOf(40.0), "gfx/terobjs/trees/oak");
        List<SmokeWood> woods = SmokeWood.extract(Collections.singletonList(oak));
        assertEquals(1, woods.size());
        assertEquals("Oak", woods.get(0).name);
        assertEquals(40.0, woods.get(0).percentage, 0.0);
    }

    @Test
    void valFractionConvertedToPercent() {
        nurgling.cookbook.smoked.Smoke birch = new nurgling.cookbook.smoked.Smoke("Birch", Double.valueOf(0.5));
        List<SmokeWood> woods = SmokeWood.extract(Collections.singletonList(birch));
        assertEquals(50.0, woods.get(0).percentage, 0.0);
    }

    @Test
    void extractSortsByNameThenPercentAndKeepsEveryWood() {
        List<Object> infos = Arrays.asList(
                new nurgling.cookbook.smoked.Smoke("Birch", Double.valueOf(0.5)),
                new nurgling.cookbook.smoked.Smoke("Apple", Double.valueOf(1.0)),
                new nurgling.cookbook.smoked.Smoke("Apple", Double.valueOf(0.4))
        );
        List<SmokeWood> woods = SmokeWood.extract(infos);
        assertEquals(3, woods.size());
        assertEquals("Apple", woods.get(0).name);
        assertEquals(40.0, woods.get(0).percentage, 0.0);
        assertEquals("Apple", woods.get(1).name);
        assertEquals(100.0, woods.get(1).percentage, 0.0);
        assertEquals("Birch", woods.get(2).name);
        assertEquals(50.0, woods.get(2).percentage, 0.0);
    }

    @Test
    void signatureIsStableAcrossInputOrder() {
        List<SmokeWood> a = Arrays.asList(
                new SmokeWood("Birch", 50.0),
                new SmokeWood("Apple", 100.0),
                new SmokeWood("Apple", 40.0)
        );
        List<SmokeWood> b = Arrays.asList(
                new SmokeWood("Apple", 40.0),
                new SmokeWood("Birch", 50.0),
                new SmokeWood("Apple", 100.0)
        );
        assertEquals(SmokeWood.signature(a), SmokeWood.signature(b));
        assertEquals("Apple40.0Apple100.0Birch50.0", SmokeWood.signature(a));
    }

    @Test
    void signatureNullListIsEmpty() {
        assertEquals("", SmokeWood.signature(null));
    }

    @Test
    void loneWoodWithoutShareKeepsLegacyHashText() {
        assertEquals("Apple tree100.0", SmokeWood.signature(
                Collections.singletonList(new SmokeWood("Apple tree", 100.0))));
    }

    @Test
    void ignoresClassesThatAreNotTheSmokedTooltip() {
        List<Object> infos = Arrays.asList(
                new Smoke("Wrong package", Double.valueOf(0.9)),
                new IngredientLike("Carrot", Double.valueOf(0.25)),
                new nurgling.cookbook.smoked.Smoke("Oak", Double.valueOf(1.0))
        );
        List<SmokeWood> woods = SmokeWood.extract(infos);
        assertEquals(1, woods.size());
        assertEquals("Oak", woods.get(0).name);
    }

    @Test
    void incompatibleClassWarnsOnce() {
        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream intercept = new PrintStream(captured);
        System.setOut(intercept);
        try {
            nurgling.cookbook.smoked.incompat.Smoke bad = new nurgling.cookbook.smoked.incompat.Smoke();
            bad.title = "Pine";
            List<Object> infos = Collections.<Object>singletonList(bad);
            SmokeWood.extract(infos);
            SmokeWood.extract(infos);
            intercept.flush();
            String log = new String(captured.toByteArray(), StandardCharsets.UTF_8);
            int first = log.indexOf("[Cookbook] cannot read smoking wood from");
            assertTrue(first >= 0, log);
            assertEquals(-1, log.indexOf("[Cookbook] cannot read smoking wood from", first + 1), log);
        } finally {
            System.setOut(previous);
        }
    }

    /** simpleName Smoke, but binary name does not contain "smoked". */
    public static final class Smoke {
        public String name;
        public Double val;

        Smoke(String name, Double val) {
            this.name = name;
            this.val = val;
        }
    }

    public static final class IngredientLike {
        public String name;
        public Double val;

        IngredientLike(String name, Double val) {
            this.name = name;
            this.val = val;
        }
    }
}
