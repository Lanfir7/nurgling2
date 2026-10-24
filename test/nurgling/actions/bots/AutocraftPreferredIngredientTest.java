package nurgling.actions.bots;

import nurgling.tools.VariantCraftOptions;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutocraftPreferredIngredientTest {
    @Test
    void exactSameIconChoiceUsesItsNameAndPlainInputAreaFallback() {
        List<JSONObject> milk = VariantCraftOptions.options("Milk");
        assertTrue(milk.stream().anyMatch(o -> "Cowsmilk".equals(o.optString("name"))));
        assertTrue(milk.stream().anyMatch(o -> "Goatsmilk".equals(o.optString("name"))));

        JSONObject milkChoice = AutocraftBot.preferredOption("Milk", "Goatsmilk", false, "Goatsmilk"::equals);
        assertNotNull(milkChoice);
        assertEquals("Goatsmilk", milkChoice.getString("name"));

        JSONObject preferred = AutocraftBot.preferredOption("Butter", "Butter (Goatsmilk)", false, "Butter"::equals);
        assertNotNull(preferred);
        assertEquals("Butter (Goatsmilk)", preferred.getString("name"));
    }

    @Test
    void explicitUnavailableChoiceCannotFallBackToAnotherKind() {
        assertNull(AutocraftBot.preferredOption("Milk", "Goatsmilk", false, name -> false));
        assertNull(AutocraftBot.preferredOption("Milk", "Unknown milk", true, name -> true));
        assertNull(AutocraftBot.preferredOption("Milk", null, true, name -> true));
    }

    @Test
    void ordinaryCategoryPreferredItemStillUsesItsExactArea() {
        JSONObject preferred = AutocraftBot.preferredOption("Nuts", "Walnut", false, "Walnut"::equals);
        assertNotNull(preferred);
        assertEquals("Walnut", preferred.getString("name"));
        assertNull(AutocraftBot.preferredOption("Nuts", "Walnut", false, "Hazelnut"::equals));
    }

    @Test
    void selectedZoneLocalZoneAndSubcraftCanProvideAnExactChoiceWithoutGlobalArea() {
        for (boolean[] sourceMode : new boolean[][] {
                {true, false, false}, {false, true, false}, {false, false, true}}) {
            boolean hasDedicatedSource = AutocraftBot.hasDedicatedSource(
                    sourceMode[0], sourceMode[1], sourceMode[2]);
            JSONObject preferred = AutocraftBot.preferredOption("Nuts", "Walnut", hasDedicatedSource,
                    name -> false);
            assertNotNull(preferred);
            assertEquals("Walnut", preferred.getString("name"));
        }
        assertFalse(AutocraftBot.hasDedicatedSource(false, false, false));
    }
}
