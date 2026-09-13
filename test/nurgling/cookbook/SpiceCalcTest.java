package nurgling.cookbook;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpiceCalcTest {
    private static final double EPS = 1e-9;

    @Test
    void pepperMultiplierFollowsOnePlusHalfSqrtQOverTen() {
        assertEquals(1.5, SpiceCalc.Spice.PEPPER.multiplier(10), EPS);
        assertEquals(2.0, SpiceCalc.Spice.PEPPER.multiplier(40), EPS);
    }

    @Test
    void truffleMultiplierFollowsPoint84SqrtQOverTen() {
        assertEquals(0.84, SpiceCalc.Spice.BLACK_TRUFFLE.multiplier(10), EPS);
        assertEquals(1.68, SpiceCalc.Spice.BLACK_TRUFFLE.multiplier(40), EPS);
        assertEquals(0.84, SpiceCalc.Spice.WHITE_TRUFFLE.multiplier(10), EPS);
        assertEquals(1.68, SpiceCalc.Spice.WHITE_TRUFFLE.multiplier(40), EPS);
    }

    @Test
    void pepperScalesEveryFepAndHunger() {
        SpiceCalc.Result q10 = apply(dishStr10Hunger4(), SpiceCalc.Spice.PEPPER, 10);
        assertEquals(15.0, fep(q10, FepAttr.STR, 1), EPS);
        assertEquals(6.0, q10.hunger, EPS);
        assertEquals(1, q10.feps.size());

        SpiceCalc.Result q40 = apply(dishStr10Hunger4(), SpiceCalc.Spice.PEPPER, 40);
        assertEquals(20.0, fep(q40, FepAttr.STR, 1), EPS);
        assertEquals(8.0, q40.hunger, EPS);
    }

    @Test
    void blackTruffleScalesFepsAndAddsWillQuarterShare() {
        SpiceCalc.Result q10 = apply(dishStr10Hunger4(), SpiceCalc.Spice.BLACK_TRUFFLE, 10);
        assertEquals(8.4, fep(q10, FepAttr.STR, 1), EPS);
        assertEquals(2.1, fep(q10, FepAttr.WIL, 1), EPS);
        assertEquals(4.2, q10.hunger, EPS);

        SpiceCalc.Result q40 = apply(dishStr10Hunger4(), SpiceCalc.Spice.BLACK_TRUFFLE, 40);
        assertEquals(16.8, fep(q40, FepAttr.STR, 1), EPS);
        assertEquals(4.2, fep(q40, FepAttr.WIL, 1), EPS);
        assertEquals(8.4, q40.hunger, EPS);
    }

    @Test
    void whiteTruffleAddsQuarterShareToStrengthPlusOne() {
        SpiceCalc.Result r = apply(dishStr10Hunger4(), SpiceCalc.Spice.WHITE_TRUFFLE, 10);
        assertEquals(10.5, fep(r, FepAttr.STR, 1), EPS);
        assertFalse(has(r, FepAttr.WIL, 1));
        assertEquals(4.2, r.hunger, EPS);
    }

    @Test
    void whiteTruffleOnMissingStrengthCreatesTierOneEntry() {
        List<FepValue> agi = Collections.singletonList(fepOf(FepAttr.AGI, 1, 10.0));
        SpiceCalc.Result r = apply(agi, 4.0, SpiceCalc.Spice.WHITE_TRUFFLE, 10);
        assertEquals(8.4, fep(r, FepAttr.AGI, 1), EPS);
        assertEquals(2.1, fep(r, FepAttr.STR, 1), EPS);
        assertEquals(4.2, r.hunger, EPS);
    }

    @Test
    void truffleHungerUsesTierWeightedRatio() {
        List<FepValue> str2 = Collections.singletonList(fepOf(FepAttr.STR, 2, 10.0));
        SpiceCalc.Result black = apply(str2, 4.0, SpiceCalc.Spice.BLACK_TRUFFLE, 10);
        assertEquals(8.4, fep(black, FepAttr.STR, 2), EPS);
        assertEquals(2.1, fep(black, FepAttr.WIL, 1), EPS);
        assertFalse(has(black, FepAttr.STR, 1));
        assertEquals(3.78, black.hunger, EPS);

        SpiceCalc.Result white = apply(str2, 4.0, SpiceCalc.Spice.WHITE_TRUFFLE, 10);
        assertEquals(8.4, fep(white, FepAttr.STR, 2), EPS);
        assertEquals(2.1, fep(white, FepAttr.STR, 1), EPS);
        assertEquals(3.78, white.hunger, EPS);
    }

    @Test
    void spicesApplyInDeclarationOrderBlackWhitePepper() {
        assertArrayEquals(new SpiceCalc.Spice[] {
                SpiceCalc.Spice.BLACK_TRUFFLE,
                SpiceCalc.Spice.WHITE_TRUFFLE,
                SpiceCalc.Spice.PEPPER
        }, SpiceCalc.Spice.values());

        Map<SpiceCalc.Spice, Double> both = qualities(
                SpiceCalc.Spice.PEPPER, 10.0,
                SpiceCalc.Spice.BLACK_TRUFFLE, 10.0);
        SpiceCalc.Result r = SpiceCalc.apply(dishStr10Hunger4(), 4.0, both);
        assertEquals(12.6, fep(r, FepAttr.STR, 1), EPS);
        assertEquals(3.15, fep(r, FepAttr.WIL, 1), EPS);
        assertEquals(6.3, r.hunger, EPS);
    }

    @Test
    void emptyFepListWithTruffleSkipsAndLeavesHungerUnchanged() {
        List<FepValue> empty = Collections.emptyList();
        SpiceCalc.Result black = apply(empty, 4.0, SpiceCalc.Spice.BLACK_TRUFFLE, 10);
        assertTrue(black.feps.isEmpty());
        assertEquals(4.0, black.hunger, EPS);

        SpiceCalc.Result white = apply(empty, 4.0, SpiceCalc.Spice.WHITE_TRUFFLE, 40);
        assertTrue(white.feps.isEmpty());
        assertEquals(4.0, white.hunger, EPS);
    }

    @Test
    void emptyFepListWithPepperStillScalesHunger() {
        SpiceCalc.Result r = apply(Collections.<FepValue>emptyList(), 4.0, SpiceCalc.Spice.PEPPER, 10);
        assertTrue(r.feps.isEmpty());
        assertEquals(6.0, r.hunger, EPS);
    }

    @Test
    void emptyQualitiesReturnsSameFepValuesAndHunger() {
        List<FepValue> input = new ArrayList<FepValue>(Arrays.asList(
                fepOf(FepAttr.STR, 1, 10.0),
                fepOf(FepAttr.AGI, 2, 3.5)));
        SpiceCalc.Result r = SpiceCalc.apply(input, 4.0, Collections.<SpiceCalc.Spice, Double>emptyMap());
        assertEquals(10.0, fep(r, FepAttr.STR, 1), EPS);
        assertEquals(3.5, fep(r, FepAttr.AGI, 2), EPS);
        assertEquals(2, r.feps.size());
        assertEquals(4.0, r.hunger, EPS);
        assertEquals(10.0, input.get(0).value, EPS);
        assertEquals(3.5, input.get(1).value, EPS);
    }

    @Test
    void missingZeroAndNegativeQualitiesAreSkipped() {
        Map<SpiceCalc.Spice, Double> q = new EnumMap<SpiceCalc.Spice, Double>(SpiceCalc.Spice.class);
        q.put(SpiceCalc.Spice.PEPPER, 0.0);
        q.put(SpiceCalc.Spice.BLACK_TRUFFLE, -5.0);
        SpiceCalc.Result r = SpiceCalc.apply(dishStr10Hunger4(), 4.0, q);
        assertEquals(10.0, fep(r, FepAttr.STR, 1), EPS);
        assertFalse(has(r, FepAttr.WIL, 1));
        assertEquals(4.0, r.hunger, EPS);
        assertEquals(1, r.feps.size());
    }

    @Test
    void nullFepsTreatedAsEmptyList() {
        SpiceCalc.Result none = SpiceCalc.apply(null, 4.0, Collections.<SpiceCalc.Spice, Double>emptyMap());
        assertNotNull(none);
        assertTrue(none.feps.isEmpty());
        assertEquals(4.0, none.hunger, EPS);

        SpiceCalc.Result truffle = apply(null, 4.0, SpiceCalc.Spice.BLACK_TRUFFLE, 10);
        assertTrue(truffle.feps.isEmpty());
        assertEquals(4.0, truffle.hunger, EPS);
    }

    @Test
    void nullQualitiesTreatedAsNoSpices() {
        List<FepValue> input = dishStr10Hunger4();
        SpiceCalc.Result r = SpiceCalc.apply(input, 4.0, null);
        assertEquals(10.0, fep(r, FepAttr.STR, 1), EPS);
        assertEquals(4.0, r.hunger, EPS);
        assertEquals(1, r.feps.size());
    }

    private static List<FepValue> dishStr10Hunger4() {
        return Collections.singletonList(fepOf(FepAttr.STR, 1, 10.0));
    }

    private static FepValue fepOf(FepAttr attr, int tier, double value) {
        return new FepValue(attr, tier, attr.fepName(tier), value);
    }

    private static SpiceCalc.Result apply(List<FepValue> feps, SpiceCalc.Spice spice, double quality) {
        return apply(feps, 4.0, spice, quality);
    }

    private static SpiceCalc.Result apply(List<FepValue> feps, double hunger, SpiceCalc.Spice spice, double quality) {
        return SpiceCalc.apply(feps, hunger, qualities(spice, quality));
    }

    private static Map<SpiceCalc.Spice, Double> qualities(SpiceCalc.Spice spice, double quality) {
        Map<SpiceCalc.Spice, Double> m = new EnumMap<SpiceCalc.Spice, Double>(SpiceCalc.Spice.class);
        m.put(spice, quality);
        return m;
    }

    private static Map<SpiceCalc.Spice, Double> qualities(SpiceCalc.Spice a, double qa, SpiceCalc.Spice b, double qb) {
        Map<SpiceCalc.Spice, Double> m = qualities(a, qa);
        m.put(b, qb);
        return m;
    }

    private static double fep(SpiceCalc.Result r, FepAttr attr, int tier) {
        double v = 0;
        boolean found = false;
        for(FepValue f : r.feps) {
            if(f.is(attr, tier)) {
                v += f.value;
                found = true;
            }
        }
        assertTrue(found, "missing " + attr.code + tier);
        return v;
    }

    private static boolean has(SpiceCalc.Result r, FepAttr attr, int tier) {
        for(FepValue f : r.feps) {
            if(f.is(attr, tier))
                return true;
        }
        return false;
    }
}
