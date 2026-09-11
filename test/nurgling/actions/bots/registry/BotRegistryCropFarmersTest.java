package nurgling.actions.bots.registry;

import nurgling.actions.bots.RadishFarmerQ;
import nurgling.actions.bots.WatermelonFarmerQ;
import nurgling.actions.bots.WhiteOnionFarmerQ;
import nurgling.actions.bots.farmers.RadishFarmer;
import nurgling.actions.bots.farmers.WatermelonFarmer;
import nurgling.actions.bots.farmers.WhiteOnionFarmer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotRegistryCropFarmersTest {
    @Test
    void watermelonRadishAndWhiteOnionFarmersAreRegistered() {
        assertFarmingBot("watermelon", WatermelonFarmer.class, "watermelon", "bot.watermelon.title");
        assertFarmingBot("radish", RadishFarmer.class, "radish", "bot.radish.title");
        assertFarmingBot("white_onion", WhiteOnionFarmer.class, "white_onion", "bot.white_onion.title");

        assertQualityBot("watermelonq", WatermelonFarmerQ.class, "watermelonq", "bot.watermelonq.title");
        assertQualityBot("radishq", RadishFarmerQ.class, "radishq", "bot.radishq.title");
        assertQualityBot("white_onionq", WhiteOnionFarmerQ.class, "white_onionq", "bot.white_onionq.title");
    }

    private static void assertFarmingBot(String id, Class<?> clazz, String icon, String titleKey) {
        BotDescriptor bot = BotRegistry.byId(id);
        assertNotNull(bot, id);
        assertEquals(BotDescriptor.BotType.FARMING, bot.type);
        assertEquals(clazz, bot.clazz);
        assertEquals(icon, bot.iconPath);
        assertEquals(titleKey, bot.titleKey);
        assertTrue(bot.allowedAsStepInScenario);
        assertTrue(bot.allowedAsItemInBotMenu);
    }

    private static void assertQualityBot(String id, Class<?> clazz, String icon, String titleKey) {
        BotDescriptor bot = BotRegistry.byId(id);
        assertNotNull(bot, id);
        assertEquals(BotDescriptor.BotType.FARMING_QUALITY, bot.type);
        assertEquals(clazz, bot.clazz);
        assertEquals(icon, bot.iconPath);
        assertEquals(titleKey, bot.titleKey);
        assertTrue(bot.allowedAsStepInScenario);
        assertTrue(bot.allowedAsItemInBotMenu);
    }
}
