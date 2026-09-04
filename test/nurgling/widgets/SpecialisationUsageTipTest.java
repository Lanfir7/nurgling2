package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialisationUsageTipTest {
    @Test
    void showsUnusedMessageWhenNoBotNeedsTheSpecialisation() {
        String tip = SpecialisationUsageTip.build(
                "Smelters", List.of(), "No bot uses this specialisation.", "unused", "unused");

        assertTrue(tip.contains("No bot uses this specialisation."));
    }

    @Test
    void limitsLongBotListsAndReportsTheHiddenCount() {
        List<String> bots = new ArrayList<>();
        for(int index = 1; index <= 17; index++)
            bots.add("Bot " + index);

        String tip = SpecialisationUsageTip.build(
                "Smelters", bots, "unused", "Used by 17 bots:", "...and 2 more");

        assertTrue(tip.contains("• Bot 15"));
        assertFalse(tip.contains("• Bot 16"));
        assertTrue(tip.contains("...and 2 more"));
    }
}
