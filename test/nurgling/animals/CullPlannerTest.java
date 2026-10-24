package nurgling.animals;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CullPlannerTest {
    private static HerdRules rules(boolean saveYoung, boolean noSlaughter) {
        return new HerdRules.Builder("cows", "saved")
                .flags(saveYoung, noSlaughter, false, true)
                .keep(1, 1).breeding(0, 0)
                .term(RankTerm.linear("meat", "Meat", 1, Color.RED)).build();
    }

    private static AnimalFacts cow(String id, boolean male, boolean young, boolean dead, int rank) {
        return new AnimalFacts(id, id, male, young, dead, 10, 10).with("meat", rank);
    }

    @Test void plansPerSexAndProtectsYoungAndDead() {
        List<AnimalFacts> herd = List.of(
                cow("fbest", false, false, false, 90), cow("fworse", false, false, false, 50),
                cow("fyoung", false, true, false, 10), cow("mbest", true, false, false, 80),
                cow("mdead", true, false, true, 1));
        CullPlanner.Plan p = CullPlanner.plan(herd, rules(true, false), rules(true, false));
        assertEquals(CullPlanner.Verdict.BREEDER, p.outcome("fbest").verdict);
        assertEquals(CullPlanner.Verdict.KILL, p.outcome("fworse").verdict);
        assertEquals(CullPlanner.Verdict.SAVED, p.outcome("fyoung").verdict);
        assertEquals(CullPlanner.Verdict.BREEDER, p.outcome("mbest").verdict);
        assertEquals(CullPlanner.Verdict.DEAD, p.outcome("mdead").verdict);
        assertEquals(1, p.count(CullPlanner.Verdict.KILL));
        assertEquals(0, CullPlanner.plan(herd, rules(true, true), rules(true, true)).count(CullPlanner.Verdict.KILL));
    }

    @Test void missingRankPresetAndInvalidStatsNeverPredictSlaughter() {
        List<AnimalFacts> herd = List.of(cow("seen", false, false, false, 90),
                new AnimalFacts("bad", "bad", false, false, false, Double.NaN, 10).with("meat", 1));
        CullPlanner.Plan unbound = CullPlanner.plan(herd, null, rules(false, false));
        assertEquals(CullPlanner.Verdict.UNCERTAIN, unbound.outcome("seen").verdict);
        assertEquals(0, unbound.count(CullPlanner.Verdict.KILL));
        CullPlanner.Plan invalid = CullPlanner.plan(herd, rules(false, false), rules(false, false));
        assertEquals(CullPlanner.Verdict.UNCERTAIN, invalid.outcome("bad").verdict);
        assertEquals(CullPlanner.Verdict.UNCERTAIN, invalid.outcome("seen").verdict);
        assertEquals(0, invalid.count(CullPlanner.Verdict.KILL));
    }

    @Test void missingPotentialBreederAndDuplicateIdsMakeWholePenUncertain() {
        HerdRules r = rules(false, false);
        AnimalFacts low = cow("low", false, false, false, 3);
        AnimalFacts highButUnread = new AnimalFacts("high", "high", false, false, false, Double.NaN, 10).with("meat", 100);
        assertEquals(CullPlanner.Verdict.UNCERTAIN,
                CullPlanner.plan(List.of(low, highButUnread), r, r).outcome("low").verdict);
        CullPlanner.Plan duplicate = CullPlanner.plan(List.of(low, cow("low", false, false, false, 50)), r, r);
        assertEquals(CullPlanner.Verdict.UNCERTAIN, duplicate.outcome("low").verdict);
        assertEquals(0, duplicate.count(CullPlanner.Verdict.KILL));
    }

    @Test void unsavedCurrentPresetDoesNotForecastCull() {
        CullPlanner.Plan plan = CullPlanner.plan(List.of(cow("a", false, false, false, 1)), rules(false, false), null);
        assertEquals(CullPlanner.Verdict.UNCERTAIN, plan.outcome("a").verdict);
    }
}
