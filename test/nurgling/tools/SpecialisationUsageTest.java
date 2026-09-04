package nurgling.tools;

import nurgling.actions.bots.registry.BotDescriptor;
import nurgling.actions.bots.registry.BotRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialisationUsageTest {
    @Test
    void scanAssociatesLivestockBotsWithTheirRequiredSpecialisations() {
        Map<String, List<String>> usage = SpecialisationUsage.scan();

        assertTrue(usage.get("cows").contains("Cow Manager"));
        assertTrue(usage.get("goats").contains("Goat Manager"));
        assertTrue(usage.get("horses").contains("Horse Manager"));
        assertTrue(usage.get("pigs").contains("Pig Manager"));
        assertTrue(usage.get("sheeps").contains("Sheep Manager"));
        assertTrue(usage.get("deadkritter").containsAll(List.of(
                "Cow Manager", "Goat Manager", "Horse Manager", "Pig Manager", "Sheep Manager")));
    }

    @Test
    void botRegistryViewCannotBeMutated() {
        List<BotDescriptor> bots = BotRegistry.all();

        assertThrows(UnsupportedOperationException.class, () -> bots.set(0, bots.get(0)));
    }
}
