package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WoundTreatmentsTest {

    @Test
    void lookupIgnoresPunctuationAndCase() {
        WoundTreatments table = WoundTreatments.parse(
                "{\"Punch-Sore\":[\"Mud Ointment\",\"Opium\"]}");

        assertEquals(List.of("Mud Ointment", "Opium"), table.lookup("Punch Sore"));
        assertEquals(List.of("Mud Ointment", "Opium"), table.lookup("punch-sore"));
    }

    @Test
    void unknownWoundHasNoTreatments() {
        WoundTreatments table = WoundTreatments.parse("{\"Nicks & Knacks\":[\"Yarrow\"]}");

        assertTrue(table.lookup("Starvation").isEmpty());
        assertTrue(table.lookup(null).isEmpty());
    }

    @Test
    void skipsEmptyAndNaEntries() {
        WoundTreatments table = WoundTreatments.parse(
                "{\"Nettle Burn\":[\"N/A\"],\"Henpecked\":[\"Waybroad\"]}");

        assertTrue(table.lookup("Nettle Burn").isEmpty());
        assertEquals(List.of("Waybroad"), table.lookup("Henpecked"));
    }

    @Test
    void bundledCatalogMapsNicksAndKnacks() {
        List<String> items = WoundTreatments.forWound("Nicks & Knacks");
        assertEquals(List.of("Yarrow", "Honey Wayband"), items);
    }

    @Test
    void leechBurnAliasMatchesLeechBurns() {
        assertEquals(WoundTreatments.forWound("Leech Burns"), WoundTreatments.forWound("Leech Burn"));
        assertEquals(List.of("Toad Butter"), WoundTreatments.forWound("Leech Burn"));
    }

    @Test
    void iconResourcesIncludeVSpecAndJarFallback() {
        List<String> yarrow = WoundTreatments.iconResources("Yarrow");
        assertTrue(yarrow.contains("gfx/invobjs/herbs/yarrow"));

        List<String> glue = WoundTreatments.iconResources("Wound Glue");
        assertTrue(glue.contains("gfx/invobjs/jar-woundglue"));
        assertTrue(glue.contains("gfx/invobjs/woundglue"));
    }

    @Test
    void silkSutureKitUsesInvobjSilksuture() {
        List<String> paths = WoundTreatments.iconResources("Silk Suture Kit");
        assertTrue(paths.contains("gfx/invobjs/silksuture"));
    }

    @Test
    void treatTipMarkupAppendsFadedControlHint() {
        String markup = WoundTreatments.treatTipMarkup("Gauze", "Click = Recipe\nCtrl + Click = Search at base");

        assertTrue(markup.startsWith("Gauze"));
        assertTrue(markup.contains("$col[140,140,140]{Click = Recipe"));
        assertTrue(markup.contains("Ctrl + Click = Search at base}"));
    }

    @Test
    void treatTipMarkupWithoutHintIsJustTheName() {
        assertEquals("Yarrow", WoundTreatments.treatTipMarkup("Yarrow", null));
        assertEquals("Yarrow", WoundTreatments.treatTipMarkup("Yarrow", ""));
    }

    @Test
    void leftCtrlClickOpensStorageSearch() {
        assertTrue(WoundTreatments.isStorageSearchClick(1, true));
        assertFalse(WoundTreatments.isStorageSearchClick(1, false));
        assertFalse(WoundTreatments.isStorageSearchClick(3, true));
    }
}
