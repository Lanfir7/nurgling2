package nurgling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CredoBonusFormatterTest {
    private static final String RAW = "$col[255,255,0]{\n"
        + "* Strength +15\n"
        + "* Masonry +15\n"
        + "* Faster mining\n"
        + "}";

    @Test
    void completedLevelsBecomeGreenChecksAndFutureLevelsStayYellow() {
        assertEquals(
            "$col[96,232,96]{$img[credodone,h=0.8ln] Strength +15}\n"
                + "$col[96,232,96]{$img[credodone,h=0.8ln] Masonry +15}\n"
                + "$col[255,218,64]{• Faster mining}",
            CredoBonusFormatter.format(RAW, 2, false));
    }

    @Test
    void acquiredCredoMarksEveryBonusCompleted() {
        String formatted = CredoBonusFormatter.format(RAW, 0, true);

        assertEquals(3, formatted.split("\\$img\\[credodone,h=0\\.8ln]", -1).length - 1);
        assertFalse(formatted.contains("$col[255,218,64]"));
    }

    @Test
    void currentLevelCountsOnlyPreviouslyCompletedBonuses() {
        assertEquals(0, CredoBonusFormatter.completedBonuses(1));
        assertEquals(1, CredoBonusFormatter.completedBonuses(2));
        assertEquals(4, CredoBonusFormatter.completedBonuses(5));
    }

    @Test
    void acquiredCredoReformatsEveryServerColorBlock() {
        String raw = "$col[96,232,96]{Strength +15 & Masonry +15}\n"
            + "$col[255,218,64]{* Significant chance to localize cave-ins.\n"
            + "* Ore mined smelts faster.\n"
            + "* Chance to pulverize tiles when mining.\n"
            + "* Ability to sense ore ahead when mining.}";

        assertEquals(
            "$col[96,232,96]{$img[credodone,h=0.8ln] Strength +15 & Masonry +15}\n"
                + "$col[96,232,96]{$img[credodone,h=0.8ln] Significant chance to localize cave-ins.}\n"
                + "$col[96,232,96]{$img[credodone,h=0.8ln] Ore mined smelts faster.}\n"
                + "$col[96,232,96]{$img[credodone,h=0.8ln] Chance to pulverize tiles when mining.}\n"
                + "$col[96,232,96]{$img[credodone,h=0.8ln] Ability to sense ore ahead when mining.}",
            CredoBonusFormatter.format(raw, 0, true));
    }
}
