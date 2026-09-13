package nurgling.actions.bots.registry;

import nurgling.actions.bots.DuckMaster;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotRegistryAnimalsTest {
    private static final List<String> ANIMAL_IDS = List.of(
            "goats", "sheeps", "pigs", "horses", "cows", "reindeers",
            "chicken", "duck", "rabbit", "bee", "trufflepig"
    );

    @Test
    void byTypeAnimalsContainsExactlyTheElevenAnimalBots() {
        List<BotDescriptor> animals = BotRegistry.byType(BotDescriptor.BotType.ANIMALS);
        List<String> ids = animals.stream().map(bot -> bot.id).collect(Collectors.toList());

        assertEquals(ANIMAL_IDS, ids);
        assertEquals(11, animals.size());
    }

    @Test
    void eachAnimalBotIsAnimalsAndNotFarming() {
        for (String id : ANIMAL_IDS) {
            BotDescriptor bot = BotRegistry.byId(id);
            assertNotNull(bot, id);
            assertEquals(BotDescriptor.BotType.ANIMALS, bot.type, id);
        }

        Set<String> farmingIds = BotRegistry.byType(BotDescriptor.BotType.FARMING).stream()
                .map(bot -> bot.id)
                .collect(Collectors.toSet());
        for (String id : ANIMAL_IDS) {
            assertFalse(farmingIds.contains(id), id);
        }

        assertTrue(farmingIds.contains("turnip"));
    }

    @Test
    void duckMasterIsRegisteredAsAnimals() {
        BotDescriptor duck = BotRegistry.byId("duck");
        assertNotNull(duck);
        assertEquals(BotDescriptor.BotType.ANIMALS, duck.type);
        assertEquals(DuckMaster.class, duck.clazz);
        assertTrue(BotRegistry.byType(BotDescriptor.BotType.ANIMALS).stream()
                .anyMatch(bot -> DuckMaster.class.equals(bot.clazz)));
    }
}
