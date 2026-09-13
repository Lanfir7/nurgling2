package nurgling.cookbook;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SearchQueryTest {
    private static final CookbookRow PIE = dish("Apple Pie", ings("Barley Flour", "Apple"),
            feps("Strength +1", 8.0, "Will +1", 2.0));
    private static final CookbookRow ROULADE = dish("Salt Roulade", ings("Pork", "Salt"),
            feps("Strength +2", 6.0, "Perception +1", 4.0));
    private static final CookbookRow TROLL = dish("Troll's Nose", ings("Troll Fat", "Salt"),
            feps("Charisma +1", 8.0, "Will +1", 2.0));
    private static final CookbookRow BROTH = dish("Will Broth", ings("Water"),
            feps("Will +1", 3.0, "Strength +1", 7.0));
    private static final CookbookRow BLAND = dish("Water", Collections.<String, Recipe.IngredientInfo>emptyMap(),
            Collections.<String, Recipe.Fep>emptyMap());
    private static final CookbookRow BELLY = dish("Smoked Belly", ings("Pork Belly"),
            feps("Agility +1", 3.0));

    @Test
    void nullAndBlankInputIsEmptyAndMatchesAnyRow() {
        CookbookRow[] rows = {PIE, ROULADE, TROLL, BROTH, BLAND, BELLY};
        for(SearchQuery q : new SearchQuery[] {
                SearchQuery.parse(null),
                SearchQuery.parse(""),
                SearchQuery.parse("   "),
                SearchQuery.parse(" ; ; "),
                SearchQuery.parse(";")
        }) {
            assertTrue(q.isEmpty());
            for(CookbookRow row : rows)
                assertTrue(q.test(row), row.name());
        }
    }

    @Test
    void plainSearchMatchesNameIngredientsCaseInsensitive() {
        assertTrue(SearchQuery.parse("PIE").test(PIE));
        assertTrue(SearchQuery.parse("pie").test(PIE));
        assertTrue(SearchQuery.parse("Apple Pie").test(PIE));
        assertFalse(SearchQuery.parse("pie").test(ROULADE));

        assertTrue(SearchQuery.parse("barley").test(PIE));
        assertTrue(SearchQuery.parse("FLOUR").test(PIE));
        assertFalse(SearchQuery.parse("barley").test(ROULADE));

        assertTrue(SearchQuery.parse("pork").test(ROULADE));
        assertFalse(SearchQuery.parse("pork").test(PIE));
    }

    @Test
    void quotedNameIsExactAfterStripAndTrim() {
        SearchQuery exact = SearchQuery.parse("name:\"Salt Roulade\"");
        assertTrue(exact.test(ROULADE));
        assertFalse(exact.test(dish("Salt Roulade Extra", ings("Pork"), feps("Strength +2", 6.0))));
        assertFalse(exact.test(PIE));

        SearchQuery padded = SearchQuery.parse("  name:  \"Salt Roulade\"  ");
        assertTrue(padded.test(ROULADE));
        assertFalse(padded.test(dish("Salt Roulade Extra", ings("Pork"), feps("Strength +2", 6.0))));
    }

    @Test
    void unquotedNameIsSubstringAndIgnoresOuterSpaces() {
        SearchQuery q = SearchQuery.parse(" name:roulade ");
        assertTrue(q.test(ROULADE));
        assertTrue(q.test(dish("Salt Roulade Extra", ings("Pork"), feps("Strength +2", 6.0))));
        assertFalse(q.test(PIE));

        SearchQuery full = SearchQuery.parse("name:Salt Roulade");
        assertTrue(full.test(ROULADE));
        assertTrue(full.test(dish("Salt Roulade Extra", ings("Pork"), feps("Strength +2", 6.0))));
    }

    @Test
    void fromTokenIsCaseInsensitiveAndQuotedIsExact() {
        assertTrue(SearchQuery.parse("from:pork").test(ROULADE));
        assertTrue(SearchQuery.parse("from:Pork").test(ROULADE));
        assertTrue(SearchQuery.parse("from:pork").test(BELLY));
        assertFalse(SearchQuery.parse("from:pork").test(PIE));

        SearchQuery exact = SearchQuery.parse("from:\"Pork\"");
        assertTrue(exact.test(ROULADE));
        assertFalse(exact.test(BELLY));
        assertTrue(SearchQuery.parse("  from:  \"Pork\"  ").test(ROULADE));
    }

    @Test
    void excludeNameAndFromNegate() {
        assertFalse(SearchQuery.parse("-from:salt").test(ROULADE));
        assertFalse(SearchQuery.parse("-from:salt").test(TROLL));
        assertTrue(SearchQuery.parse("-from:salt").test(PIE));
        assertTrue(SearchQuery.parse("-from:salt").test(BELLY));

        assertFalse(SearchQuery.parse("-name:troll").test(TROLL));
        assertTrue(SearchQuery.parse("-name:troll").test(PIE));
        assertTrue(SearchQuery.parse("-name:troll").test(ROULADE));
    }

    @Test
    void emptyNameAndFromAfterTrimAreDropped() {
        assertTrue(SearchQuery.parse("name:").isEmpty());
        assertTrue(SearchQuery.parse("from:").isEmpty());
        assertTrue(SearchQuery.parse("name:   ").isEmpty());
        assertTrue(SearchQuery.parse("from:\t").isEmpty());
        assertTrue(SearchQuery.parse("-name:").isEmpty());
        assertTrue(SearchQuery.parse("-from:").isEmpty());
        assertTrue(SearchQuery.parse("name:;from:").isEmpty());

        SearchQuery leftover = SearchQuery.parse("name:;pie");
        assertFalse(leftover.isEmpty());
        assertTrue(leftover.test(PIE));
        assertFalse(leftover.test(ROULADE));
    }

    @Test
    void emptyNameFromStillMatchesAnyWhenDropped() {
        CookbookRow row = anyRow();
        assertTrue(SearchQuery.parse("name:").test(row));
        assertTrue(SearchQuery.parse("from:").test(row));
    }

    @Test
    void str2AbsoluteBoundRequiresPresentTierAndValue() {
        SearchQuery q = SearchQuery.parse("str2>5");
        assertTrue(q.test(ROULADE));
        assertFalse(q.test(PIE));
        assertFalse(q.test(dish("Weak +2", ings("Pork"), feps("Strength +2", 5.0))));
        assertFalse(q.test(dish("Only +1", ings("Pork"), feps("Strength +1", 9.0))));
        assertFalse(q.test(BLAND));
        assertTrue(SearchQuery.parse("STR2>5").test(ROULADE));
    }

    @Test
    void fepOperatorsCompareAbsoluteValues() {
        CookbookRow six = ROULADE;
        assertFalse(SearchQuery.parse("str2<6").test(six));
        assertTrue(SearchQuery.parse("str2<7").test(six));
        assertTrue(SearchQuery.parse("str2<=6").test(six));
        assertFalse(SearchQuery.parse("str2<=5").test(six));
        assertTrue(SearchQuery.parse("str2=6").test(six));
        assertTrue(SearchQuery.parse("str2>=6").test(six));
        assertFalse(SearchQuery.parse("str2>6").test(six));
        assertTrue(SearchQuery.parse("str2>5").test(six));
        assertTrue(SearchQuery.parse("str2 >= 6").test(six));
    }

    @Test
    void percentShareUsesTotalAndIsZeroWhenTotalIsZero() {
        SearchQuery q = SearchQuery.parse("wil>=30%");
        assertTrue(q.test(BROTH));
        assertFalse(q.test(PIE));
        assertFalse(q.test(TROLL));
        assertFalse(q.test(ROULADE));
        assertFalse(q.test(BLAND));
        assertTrue(SearchQuery.parse("wil>=0%").test(BLAND));
        assertFalse(SearchQuery.parse("wil>0%").test(BLAND));
    }

    @Test
    void prcAndCsmAliasesMapToPerceptionAndCharisma() {
        assertTrue(SearchQuery.parse("prc>3").test(ROULADE));
        assertFalse(SearchQuery.parse("prc>3").test(TROLL));
        assertTrue(SearchQuery.parse("per>3").test(ROULADE));

        assertTrue(SearchQuery.parse("csm>=8").test(TROLL));
        assertFalse(SearchQuery.parse("csm>=8").test(ROULADE));
        assertTrue(SearchQuery.parse("cha>=8").test(TROLL));
    }

    @Test
    void semicolonJoinsPartsWithAnd() {
        SearchQuery q = SearchQuery.parse("from:pork;str2>5");
        assertTrue(q.test(ROULADE));
        assertFalse(q.test(BELLY));
        assertFalse(q.test(PIE));

        SearchQuery named = SearchQuery.parse(" name:roulade ; -from:apple ");
        assertTrue(named.test(ROULADE));
        assertFalse(named.test(PIE));
        assertFalse(SearchQuery.parse("from:pork;from:apple").test(ROULADE));
    }

    @Test
    void unparsedTokenIsPlainHaystackAndNeverThrows() {
        assertDoesNotThrow(() -> SearchQuery.parse("str1>5"));
        assertDoesNotThrow(() -> SearchQuery.parse("!!!"));
        assertDoesNotThrow(() -> SearchQuery.parse("namee:pie"));
        assertDoesNotThrow(() -> SearchQuery.parse("str3>5;not a token"));

        assertFalse(SearchQuery.parse("str1>5").test(PIE));
        assertTrue(SearchQuery.parse("str1>5").test(dish("str1>5 soup", ings("Water"), feps("Strength +1", 9.0))));
        assertTrue(SearchQuery.parse("Apple Pie").test(PIE));
        assertFalse(SearchQuery.parse("namee:pie").test(PIE));
        assertTrue(SearchQuery.parse("namee:pie").test(dish("namee:pie", ings("Salt"), feps("Will +1", 1.0))));
    }

    private static CookbookRow anyRow() {
        return PIE;
    }

    private static CookbookRow dish(String name, Map<String, Recipe.IngredientInfo> ingredients,
                                    Map<String, Recipe.Fep> feps) {
        return new CookbookRow(new Recipe("h-" + name, name, "gfx/invobjs/food", 1.0, 10, ingredients, feps),
                Collections.<SpiceCalc.Spice, Double>emptyMap());
    }

    private static Map<String, Recipe.IngredientInfo> ings(String... names) {
        Map<String, Recipe.IngredientInfo> m = new LinkedHashMap<String, Recipe.IngredientInfo>();
        for(String n : names)
            m.put(n, new Recipe.IngredientInfo(1.0));
        return m;
    }

    private static Map<String, Recipe.Fep> feps(Object... pairs) {
        Map<String, Recipe.Fep> m = new LinkedHashMap<String, Recipe.Fep>();
        for(int i = 0; i < pairs.length; i += 2)
            m.put((String)pairs[i], new Recipe.Fep(((Number)pairs[i + 1]).doubleValue(), 1.0));
        return m;
    }
}
