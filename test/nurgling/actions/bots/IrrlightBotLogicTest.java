package nurgling.actions.bots;

import nurgling.NUtils;
import nurgling.widgets.Specialisation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrrlightBotLogicTest {
    private static final int BRANCHES = 1;
    private static final int COAL = 2;
    private static final int FIRE = 4;

    @Test
    void nuggetsWinTheCycleSoALeftoverBarIsSpentFirst() {
        assertEquals("paginae/craft/denuggify", IrrlightBot.nextCycleRecipe(1, 10));
        assertEquals("gfx/invobjs/bar-", IrrlightBot.nextCycleOutput(1, 10));
        assertEquals("paginae/craft/nuggify", IrrlightBot.nextCycleRecipe(1, 0));
        assertEquals("gfx/invobjs/nugget-", IrrlightBot.nextCycleOutput(1, 0));
        assertNull(IrrlightBot.nextCycleRecipe(0, 0));
        assertNull(IrrlightBot.nextCycleOutput(0, 0));
    }

    @Test
    void onlyANormalCrucibleMatchesTheBotAlias() {
        assertTrue(IrrlightBot.matchesNormalCrucible("gfx/terobjs/crucible"));
        assertFalse(IrrlightBot.matchesNormalCrucible("gfx/terobjs/steelcrucible"));
        assertFalse(IrrlightBot.matchesNormalCrucible("gfx/terobjs/smelter"));
        assertFalse(IrrlightBot.matchesNormalCrucible(null));
    }

    @Test
    void irrblossCritterIsTheCaptureTarget() {
        assertTrue(IrrlightBot.matchesIrrbloss("gfx/kritter/irrbloss"));
        assertFalse(IrrlightBot.matchesIrrbloss("gfx/invobjs/irrbloss"));
        assertFalse(IrrlightBot.matchesIrrbloss("gfx/kritter/bat"));
    }

    @Test
    void fuelMaskAcceptsBranchesOrCoalAndIgnoresFlameAlone() {
        LightObject.LightConfig config = LightObject.getConfig("gfx/terobjs/crucible");
        assertNotNull(config);
        assertEquals(1 | 2, config.fuelFlag);
        assertEquals(FIRE, config.fireFlag);

        assertTrue(IrrlightBot.holdsFuel(BRANCHES));
        assertTrue(IrrlightBot.holdsFuel(COAL));
        assertTrue(IrrlightBot.holdsFuel(BRANCHES | FIRE));
        assertFalse(IrrlightBot.holdsFuel(0));
        assertFalse(IrrlightBot.holdsFuel(FIRE));
        assertTrue(IrrlightBot.isAlight(FIRE));
        assertFalse(IrrlightBot.isAlight(BRANCHES | COAL));
    }

    @Test
    void nutilsReadinessUsesTheSameBranchOrCoalMask() {
        assertTrue(NUtils.crucibleHoldsFuel(BRANCHES));
        assertTrue(NUtils.crucibleHoldsFuel(COAL));
        assertTrue(NUtils.crucibleHoldsFuel(BRANCHES | FIRE));
        assertFalse(NUtils.crucibleHoldsFuel(0));
        assertFalse(NUtils.crucibleHoldsFuel(FIRE));
    }

    @Test
    void workstationFuelMaskAppliesOnlyToTheNormalCrucible() {
        assertTrue(NUtils.usesCrucibleFuelMask("gfx/terobjs/crucible"));
        assertFalse(NUtils.usesCrucibleFuelMask("gfx/terobjs/steelcrucible"));
        assertFalse(NUtils.usesCrucibleFuelMask("custom-crucible"));
        assertFalse(NUtils.usesCrucibleFuelMask(null));

        assertFalse(NUtils.isWorkStationReady("gfx/terobjs/crucible", 0));
        assertTrue(NUtils.isWorkStationReady("gfx/terobjs/crucible", BRANCHES));
        assertTrue(NUtils.isWorkStationReady("gfx/terobjs/crucible", COAL));
        assertTrue(NUtils.isWorkStationReady("gfx/terobjs/steelcrucible", 0));
        assertTrue(NUtils.isWorkStationReady("gfx/terobjs/steelcrucible", FIRE));
        assertTrue(NUtils.isWorkStationReady("paginae/bld/crucible", 0));
    }

    @Test
    void loadingInventoryIsNotAZeroCountOrNothingToSmelt() {
        assertEquals(IrrlightBot.COUNT_LOADING,
                IrrlightBot.accumulateCount(0, null, true, "gfx/invobjs/bar-"));
        assertEquals(0, IrrlightBot.accumulateCount(0, "gfx/invobjs/branch", false, "gfx/invobjs/bar-"));
        assertEquals(1, IrrlightBot.accumulateCount(0, "gfx/invobjs/bar-iron", false, "gfx/invobjs/bar-"));
        assertEquals(IrrlightBot.COUNT_LOADING,
                IrrlightBot.accumulateCount(2, "gfx/invobjs/bar-iron", true, "gfx/invobjs/bar-"));

        assertTrue(IrrlightBot.shouldRetryCount(IrrlightBot.COUNT_LOADING, 0));
        assertTrue(IrrlightBot.shouldRetryCount(0, IrrlightBot.COUNT_LOADING));
        assertFalse(IrrlightBot.shouldRetryCount(0, 0));
        assertFalse(IrrlightBot.shouldRetryCount(1, 0));

        assertTrue(IrrlightBot.nothingToSmelt(0, 0));
        assertFalse(IrrlightBot.nothingToSmelt(IrrlightBot.COUNT_LOADING, 0));
        assertFalse(IrrlightBot.nothingToSmelt(0, IrrlightBot.COUNT_LOADING));
        assertFalse(IrrlightBot.nothingToSmelt(1, 0));
    }

    @Test
    void irrblossInterruptsDrinkHomePrepareAndUseWaits() {
        assertTrue(IrrlightBot.observesIrrbloss(IrrlightBot.WaitPhase.DRINK));
        assertTrue(IrrlightBot.observesIrrbloss(IrrlightBot.WaitPhase.GO_HOME));
        assertTrue(IrrlightBot.observesIrrbloss(IrrlightBot.WaitPhase.PREPARE_CRUCIBLE));
        assertTrue(IrrlightBot.observesIrrbloss(IrrlightBot.WaitPhase.USE_CRUCIBLE));
        assertTrue(IrrlightBot.observesIrrbloss(IrrlightBot.WaitPhase.WATCH));
        assertTrue(IrrlightBot.observesIrrbloss(IrrlightBot.WaitPhase.RECIPE));
        assertFalse(IrrlightBot.observesIrrbloss(IrrlightBot.WaitPhase.CHASE));
    }

    @Test
    void maybeDeliverSucceedsWithoutDeliveryWhenInventoryIsTightButNoResolvedIrrlightsExist() {
        assertEquals(IrrlightBot.DeliverDecision.SKIP, IrrlightBot.decideDelivery(4, 0, 0));
        assertEquals(IrrlightBot.DeliverDecision.SKIP, IrrlightBot.decideDelivery(5, 0, 0));
        assertEquals(IrrlightBot.DeliverDecision.SKIP, IrrlightBot.decideDelivery(8, 3, 3));
        assertEquals(IrrlightBot.DeliverDecision.NO_ZONE, IrrlightBot.decideDelivery(4, 2, 0));
        assertEquals(IrrlightBot.DeliverDecision.TRANSFER, IrrlightBot.decideDelivery(4, 2, 1));
    }

    @Test
    void recipeResourceWaitUsesIrrblossAwareMechanismForUpToTenSeconds() throws Exception {
        assertEquals(IrrlightBot.WaitPhase.RECIPE, IrrlightBot.recipeResourceWaitPhase());
        assertTrue(IrrlightBot.observesIrrbloss(IrrlightBot.recipeResourceWaitPhase()));
        assertEquals(10_000L, IrrlightBot.RECIPE_TIMEOUT);
        assertTrue(IrrlightBot.recipeWaitAborted(IrrlightBot.Wake.IRRLIGHT));
        assertTrue(IrrlightBot.recipeWaitAborted(IrrlightBot.Wake.ERROR));
        assertFalse(IrrlightBot.recipeWaitAborted(IrrlightBot.Wake.TIMEOUT));
        assertFalse(IrrlightBot.recipeWaitAborted(IrrlightBot.Wake.DONE));

        String src = Files.readString(Path.of("src/nurgling/actions/bots/IrrlightBot.java"), StandardCharsets.UTF_8);
        int start = src.indexOf("private MenuGrid.PagButton recipeButton");
        int end = src.indexOf("private static String resnm", start);
        assertTrue(start >= 0 && end > start);
        String body = src.substring(start, end);
        assertTrue(body.contains("waitDuring(recipeResourceWaitPhase()"));
        assertFalse(body.contains("WaitDuration"));
    }

    @Test
    void applyTansyTextDescribesTopUpToTenStacks() throws Exception {
        Properties en = loadProperties("src/lang/messages.properties");
        Properties ru = loadProperties("src/lang/messages_ru.properties");
        String enDesc = en.getProperty("bot.apply_tansy.desc");
        String ruDesc = ru.getProperty("bot.apply_tansy.desc");
        assertNotNull(enDesc);
        assertNotNull(ruDesc);
        assertTrue(enDesc.contains("10"));
        assertTrue(enDesc.toLowerCase().contains("stack"));
        assertFalse(enDesc.contains("unless the Scent of Tansy buff is already active"));
        assertTrue(ruDesc.contains("10"));
        assertTrue(ruDesc.toLowerCase().contains("стак"));
        assertFalse(ruDesc.contains("если эффект \"Запах пижмы\" ещё не активен"));
    }

    private static Properties loadProperties(String path) throws Exception {
        Properties p = new Properties();
        try (InputStreamReader in = new InputStreamReader(Files.newInputStream(Path.of(path)), StandardCharsets.UTF_8)) {
            p.load(in);
        }
        return p;
    }

    @Test
    void outputAndFuelAreasAreIrrlightAndBranchFuel() {
        assertEquals("Irrlight", IrrlightBot.outputItemName());
        assertEquals("Branch", IrrlightBot.fuelSubtype());
        assertEquals(Specialisation.SpecName.fuel, IrrlightBot.fuelSpec());
    }

    @Test
    void iconResourcesArePresent() {
        Path icons = Path.of("resources/src/nurgling/bots/icons/irrlight");
        assertTrue(Files.isRegularFile(icons.resolve("meta")));
        assertTrue(Files.isRegularFile(icons.resolve("u.res/image/image_0.png")));
        assertTrue(Files.isRegularFile(icons.resolve("d.res/image/image_0.png")));
        assertTrue(Files.isRegularFile(icons.resolve("h.res/image/image_0.png")));
    }
}
