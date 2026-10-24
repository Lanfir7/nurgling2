package nurgling.animals;

import nurgling.conf.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HerdRankTest {
    private static final AnimalFacts COW = new AnimalFacts("id", "A", false, false, false, 100, 120)
            .with("meat", 30).with("meatq", 20).with("milk", 25).with("milkq", 30)
            .with("wool", 12).with("woolq", 40).with("hideq", 50)
            .with("truffle", 15).with("stam", 80).with("end", 60).with("mb", 45);

    private static double pct(double eq, double weight, int stat) { return eq * weight * stat / 100.; }
    private static double threshold(int stat, double first, double at, double second) {
        return stat * first + (stat > at ? (stat - at) * (second - first) : 0);
    }
    private static void same(HerdRules rules, double oldSum) {
        assertEquals(Math.round(oldSum * 10) / 10., rules.rank(COW).rank, 0.0000001);
    }

    @Test void allSixSpeciesMatchTheOldRankTermsAndOrder() {
        double eq = 102; // q=100, target=110, overbreed=.2
        CowsHerd cows = new CowsHerd("c");
        cows.breedingGap = 10; cows.coverbreed = .2;
        cows.meatq = 1.3; cows.meatquan1 = .4; cows.meatquanth = 20; cows.meatquan2 = .8;
        cows.milkq = .5; cows.milkquan1 = .2; cows.milkquanth = 10; cows.milkquan2 = .6; cows.hideq = .1;
        same(HerdRulesFactory.of(cows), pct(eq, 1.3, 20) + threshold(30, .4, 20, .8)
                + pct(eq, .5, 30) + threshold(25, .2, 10, .6) + pct(eq, .1, 50));

        GoatsHerd goats = new GoatsHerd("g");
        goats.breedingGap = 10; goats.coverbreed = .2;
        goats.meatq = 1.3; goats.meatquan1 = .4; goats.meatquanth = 20; goats.meatquan2 = .8;
        goats.milkq = .5; goats.milkquan1 = .2; goats.milkquanth = 10; goats.milkquan2 = .6;
        goats.woolq = .7; goats.woolquan1 = .3; goats.woolquanth = 8; goats.woolquan2 = .9; goats.hideq = .1;
        same(HerdRulesFactory.of(goats), pct(eq, 1.3, 20) + threshold(30, .4, 20, .8)
                + pct(eq, .5, 30) + threshold(25, .2, 10, .6)
                + pct(eq, .7, 40) + threshold(12, .3, 8, .9) + pct(eq, .1, 50));

        SheepsHerd sheep = new SheepsHerd("s");
        sheep.breedingGap = 10; sheep.coverbreed = .2;
        sheep.meatq = 1.3; sheep.meatquan1 = .4; sheep.meatquanth = 20; sheep.meatquan2 = .8;
        sheep.milkq = .5; sheep.milkquan1 = .2; sheep.milkquanth = 10; sheep.milkquan2 = .6;
        sheep.woolq = .7; sheep.woolquan1 = .3; sheep.woolquanth = 8; sheep.woolquan2 = .9; sheep.hideq = .1;
        same(HerdRulesFactory.of(sheep), pct(eq, 1.3, 20) + threshold(30, .4, 20, .8)
                + pct(eq, .5, 30) + threshold(25, .2, 10, .6)
                + pct(eq, .7, 40) + threshold(12, .3, 8, .9) + pct(eq, .1, 50));

        PigsHerd pigs = new PigsHerd("p");
        pigs.breedingGap = 10; pigs.coverbreed = .2;
        pigs.trufquan1 = .3; pigs.trufquanth = 10; pigs.trufquan2 = .8;
        pigs.meatq = 1.3; pigs.meatquan1 = .4; pigs.meatquanth = 20; pigs.meatquan2 = .8; pigs.hideq = .1;
        same(HerdRulesFactory.of(pigs), threshold(15, .3, 10, .8) + pct(eq, 1.3, 20)
                + threshold(30, .4, 20, .8) + pct(eq, .1, 50));

        HorseHerd horses = new HorseHerd("h");
        horses.breedingGap = 10; horses.coverbreed = .2;
        horses.meatq = 1.3; horses.meatquan1 = .4; horses.meatquanth = 20; horses.meatquan2 = .8;
        horses.stam1 = .2; horses.stamth = 50; horses.stam2 = .6;
        horses.enduran = .7; horses.meta = .9; horses.hideq = .1;
        same(HerdRulesFactory.of(horses), pct(eq, 1.3, 20) + threshold(30, .4, 20, .8)
                + threshold(80, .2, 50, .6) + .7 * 60 + .9 * 45 + pct(eq, .1, 50));

        TeimDeerHerd deer = new TeimDeerHerd("d");
        deer.breedingGap = 10; deer.coverbreed = .2;
        deer.meatq = 1.3; deer.meatquan1 = .4; deer.meatquanth = 20; deer.meatquan2 = .8; deer.hideq = .1;
        same(HerdRulesFactory.of(deer), pct(eq, 1.3, 20) + threshold(30, .4, 20, .8) + pct(eq, .1, 50));
    }

    @Test void breedingQualityFlagsZeroFallbackAndNegativeRankStayCompatible() {
        CowsHerd h = new CowsHerd("c");
        h.ignoreBD = true;
        h.disable_q_percentage = true;
        h.meatq = -2;
        h.milkq = 0;
        HerdRules r = HerdRulesFactory.of(h);
        assertEquals(-40, r.rank(COW).rank, 0.00001); // -2 * meatq stat 20, with quality ignored
        h.meatq = 0;
        assertEquals(100, HerdRulesFactory.of(h).rank(COW).rank, 0.00001);
        assertTrue(HerdRulesFactory.of(h).rank(COW).fallback);
    }
}
