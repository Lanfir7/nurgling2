package nurgling.actions.bots.registry;

import nurgling.NConfig;
import nurgling.actions.bots.ApplyTansyIfMissing;
import nurgling.actions.bots.GateBot;
import nurgling.pf.NPFMap;
import nurgling.tools.VSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotRegistryForagerIntegrationTest {

    @Test
    void gateBotIsForagerStepOnly() {
        BotDescriptor gate = BotRegistry.byId("gate");
        assertNotNull(gate);
        assertEquals(GateBot.class, gate.clazz);
        assertFalse(gate.allowedAsStepInScenario);
        assertFalse(gate.allowedAsItemInBotMenu);
        assertTrue(gate.allowedAsForagerStep);
        assertEquals("open", gate.defaultSettings.get("mode"));
    }

    @Test
    void applyTansyIsScenarioPrepNotForagerStep() {
        BotDescriptor tansy = BotRegistry.byId("apply_tansy");
        assertNotNull(tansy);
        assertEquals(ApplyTansyIfMissing.class, tansy.clazz);
        assertTrue(tansy.allowedAsStepInScenario);
        assertFalse(tansy.allowedAsItemInBotMenu);
        assertFalse(tansy.allowedAsForagerStep);
    }

    @Test
    void coracleIsAllowedAsForagerStep() {
        BotDescriptor coracle = BotRegistry.byId("coracle");
        assertNotNull(coracle);
        assertTrue(coracle.allowedAsForagerStep);
        assertFalse(coracle.allowedAsStepInScenario);
    }

    @Test
    void foragerConfigKeysAndWaterClassificationExist() {
        assertNotNull(NConfig.Key.milestones);
        assertNotNull(NConfig.Key.milestoneTracking);
        assertTrue(NPFMap.isValidWaterTileName("gfx/tiles/water"));
        assertTrue(NPFMap.isValidWaterTileName("gfx/tiles/bog"));
        assertFalse(NPFMap.isValidWaterTileName("gfx/tiles/grass"));
        assertEquals("Take bark", VSpec.VERIFIED_CATEGORY_ACTION.get("Bark"));
        assertFalse(VSpec.getGobsForItem("Acacia Pod").isEmpty());
    }
}
