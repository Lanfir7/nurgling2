package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsSearchTest {
    private static final SettingsSearch.Entry QOL = SettingsSearch.tab("General", "Quality of life");
    private static final SettingsSearch.Entry NIGHT = SettingsSearch.setting("General", "Quality of life", "Night Vision");
    private static final SettingsSearch.Entry DRINK = SettingsSearch.setting("General", "Quality of life", "Auto drink");
    private static final SettingsSearch.Entry MAP = SettingsSearch.tab("General", "Map Settings");
    private static final SettingsSearch.Entry MAPPER = SettingsSearch.tab("General", "Auto Mapper");
    private static final List<SettingsSearch.Entry> CATALOG = Arrays.asList(QOL, NIGHT, DRINK, MAP, MAPPER);

    @Test
    void emptyQueryReturnsNothing() {
        assertTrue(SettingsSearch.query(CATALOG, "").isEmpty());
        assertTrue(SettingsSearch.query(CATALOG, "   ").isEmpty());
    }

    @Test
    void findsTabBySubstring() {
        List<String> found = names(SettingsSearch.query(CATALOG, "qol"));
        assertEquals("Quality of life", found.get(0));
    }

    @Test
    void findsSettingAndShowsTabPath() {
        List<SettingsSearch.Match> found = SettingsSearch.query(CATALOG, "night");
        assertEquals("Quality of life › Night Vision", found.get(0).display());
    }

    @Test
    void caseInsensitiveAndExactBeatsPartial() {
        List<String> found = names(SettingsSearch.query(CATALOG, "Map Settings"));
        assertEquals("Map Settings", found.get(0));
    }

    @Test
    void allTokensMustMatch() {
        assertTrue(names(SettingsSearch.query(CATALOG, "night drink")).isEmpty());
        assertEquals("Quality of life › Night Vision",
                SettingsSearch.query(CATALOG, "qol night").get(0).display());
    }

    @Test
    void limitsResults() {
        List<SettingsSearch.Entry> many = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++)
            many.add(SettingsSearch.setting("General", "Tab", "Night " + i));
        assertEquals(SettingsSearch.LIMIT, SettingsSearch.query(many, "night").size());
    }

    @Test
    void orderedLettersFindTranslatedSetting() {
        SettingsSearch.Entry vision = SettingsSearch.setting("Общие", "Удобство", "Ночное зрение", "qol.night_vision");
        assertEquals(vision, SettingsSearch.query(Arrays.asList(vision), "нчзр").get(0).entry);
        assertTrue(SettingsSearch.query(Arrays.asList(vision), "зрнч").isEmpty());
        assertEquals(vision, SettingsSearch.query(Arrays.asList(vision), "night vision").get(0).entry);
    }

    @Test
    void exactAndPrefixStayAheadOfFuzzyMatches() {
        SettingsSearch.Entry fuzzy = SettingsSearch.setting("General", "Other", "Nightly Screen");
        SettingsSearch.Entry prefix = SettingsSearch.setting("General", "Other", "Night Vision");
        SettingsSearch.Entry exact = SettingsSearch.setting("General", "Other", "Night");
        assertEquals(Arrays.asList(exact, prefix, fuzzy),
                SettingsSearch.query(Arrays.asList(fuzzy, prefix, exact), "night").stream()
                        .map(m -> m.entry).collect(Collectors.toList()));
    }

    @Test
    void punctuationAndMixedTokensAreSafe() {
        SettingsSearch.Entry vision = SettingsSearch.setting("Общие", "Удобство", "Ночное зрение", "qol.night_vision");
        assertTrue(SettingsSearch.query(Arrays.asList(vision), "---").isEmpty());
        assertEquals(vision, SettingsSearch.query(Arrays.asList(vision), "night-vision").get(0).entry);
        assertTrue(SettingsSearch.query(Arrays.asList(vision), "night drink").isEmpty());
    }

    private static List<String> names(List<SettingsSearch.Match> matches) {
        return matches.stream().map(SettingsSearch.Match::display).collect(Collectors.toList());
    }
}
