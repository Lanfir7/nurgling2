package nurgling.actions.bots;

import nurgling.NUtils;
import nurgling.widgets.Specialisation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
    void crucibleIdentityPrefersLiveIdThenHashThenNearbyRefind() {
        assertTrue(IrrlightBot.crucibleStillValid("gfx/terobjs/crucible"));
        assertFalse(IrrlightBot.crucibleStillValid("gfx/terobjs/steelcrucible"));
        assertFalse(IrrlightBot.crucibleStillValid("gfx/terobjs/smelter"));
        assertFalse(IrrlightBot.crucibleStillValid(null));

        assertFalse(IrrlightBot.allowsNearbyFallback(true));
        assertTrue(IrrlightBot.allowsNearbyFallback(false));

        // LIVE_ID: validated live gob id wins even if hashHit and nearby exist.
        assertEquals(IrrlightBot.CrucibleResolve.LIVE_ID,
                IrrlightBot.resolveAfterReload(11L, true, "22", 33L));
        assertEquals(11L, IrrlightBot.resolvedCrucibleId(11L, true, "22", 33L));
        assertEquals(IrrlightBot.CrucibleResolve.LIVE_ID,
                IrrlightBot.resolveAfterReload(11L, false, "22", 33L));
        assertEquals(11L, IrrlightBot.resolvedCrucibleId(11L, false, "22", 33L));

        // HASH: live id missing, stored hash currently hits a validated normal crucible; nearby ignored.
        assertEquals(IrrlightBot.CrucibleResolve.HASH,
                IrrlightBot.resolveAfterReload(null, true, "22", 33L));
        assertEquals(22L, IrrlightBot.resolvedCrucibleId(null, true, "22", 33L));

        // Empty hashHitId is not a hash hit.
        assertEquals(IrrlightBot.CrucibleResolve.WAIT_STORED,
                IrrlightBot.resolveAfterReload(null, true, "", 33L));
        assertEquals(IrrlightBot.CrucibleResolve.WAIT_STORED,
                IrrlightBot.resolveAfterReload(null, true, null, 33L));

        // WAIT_STORED: stable hash was captured; live id + hash hit absent → not nearby 33.
        assertNull(IrrlightBot.resolvedCrucibleId(null, true, null, 33L));
        assertNull(IrrlightBot.resolvedCrucibleId(null, true, "", 33L));
        assertFalse(IrrlightBot.shouldCaptureChosenHash(IrrlightBot.CrucibleResolve.WAIT_STORED));
        assertEquals("abc", IrrlightBot.nextStableHash("abc", IrrlightBot.CrucibleResolve.WAIT_STORED, "nearby-hash"));

        // NEARBY allowed only when a hash was never captured.
        assertEquals(IrrlightBot.CrucibleResolve.NEARBY,
                IrrlightBot.resolveAfterReload(null, false, null, 33L));
        assertEquals(33L, IrrlightBot.resolvedCrucibleId(null, false, null, 33L));
        assertEquals(IrrlightBot.CrucibleResolve.NEARBY,
                IrrlightBot.resolveAfterReload(null, false, "", 33L));
        assertEquals(33L, IrrlightBot.resolvedCrucibleId(null, false, "", 33L));
        assertTrue(IrrlightBot.shouldCaptureChosenHash(IrrlightBot.CrucibleResolve.NEARBY));
        assertEquals("newhash", IrrlightBot.nextStableHash(null, IrrlightBot.CrucibleResolve.NEARBY, "newhash"));

        assertEquals(IrrlightBot.CrucibleResolve.MISSING,
                IrrlightBot.resolveAfterReload(null, false, null, null));
        assertNull(IrrlightBot.resolvedCrucibleId(null, false, null, null));
        assertFalse(IrrlightBot.shouldCaptureChosenHash(IrrlightBot.CrucibleResolve.MISSING));

        // Second arg is hasStoredHash, not a current hash hit.
        assertFalse(IrrlightBot.shouldRefindWhenMissing(true, false));
        assertFalse(IrrlightBot.shouldRefindWhenMissing(false, true));
        assertFalse(IrrlightBot.shouldRefindWhenMissing(true, true));
        assertTrue(IrrlightBot.shouldRefindWhenMissing(false, false));
    }

    @Test
    void knownStoredHashSuppressesArbitraryNearbyFallbackAndCannotBeOverwrittenByTemporaryMiss() {
        assertTrue(IrrlightBot.hasStableHash("abc"));
        assertFalse(IrrlightBot.hasStableHash(null));
        assertFalse(IrrlightBot.hasStableHash(""));

        assertFalse(IrrlightBot.allowsNearbyFallback(true));
        assertFalse(IrrlightBot.shouldRefindWhenMissing(false, true));

        assertEquals(IrrlightBot.CrucibleResolve.WAIT_STORED,
                IrrlightBot.resolveAfterReload(null, true, "", 33L));
        assertEquals(IrrlightBot.CrucibleResolve.WAIT_STORED,
                IrrlightBot.resolveAfterReload(null, true, null, 33L));
        assertNull(IrrlightBot.resolvedCrucibleId(null, true, "", 33L));
        assertNull(IrrlightBot.resolvedCrucibleId(null, true, null, 33L));
        assertFalse(IrrlightBot.shouldCaptureChosenHash(IrrlightBot.CrucibleResolve.WAIT_STORED));

        assertEquals("abc", IrrlightBot.nextStableHash("abc", IrrlightBot.CrucibleResolve.WAIT_STORED, "other-gob-hash"));
        assertEquals("abc", IrrlightBot.nextStableHash("abc", IrrlightBot.CrucibleResolve.NEARBY, "other-gob-hash"));
        assertEquals("abc", IrrlightBot.nextStableHash("abc", IrrlightBot.CrucibleResolve.MISSING, "other-gob-hash"));
        assertEquals("newhash", IrrlightBot.nextStableHash(null, IrrlightBot.CrucibleResolve.NEARBY, "newhash"));
        assertEquals("newhash", IrrlightBot.nextStableHash("", IrrlightBot.CrucibleResolve.NEARBY, "newhash"));
    }

    @Test
    void crucibleReloadWaitStaysIrrblossAwareAndBounded() {
        assertEquals(IrrlightBot.WaitPhase.WATCH, IrrlightBot.crucibleReloadWaitPhase());
        assertTrue(IrrlightBot.observesIrrbloss(IrrlightBot.crucibleReloadWaitPhase()));
        assertEquals(15_000L, IrrlightBot.CRUCIBLE_RELOAD_TIMEOUT);
        assertFalse(IrrlightBot.observesIrrbloss(IrrlightBot.WaitPhase.CHASE));
    }

    @Test
    void resourceRestoreUsesRestoreResourcesThresholdsWithoutCallingIt() throws Exception {
        assertFalse(IrrlightBot.needsStaminaRestore(-1));
        assertTrue(IrrlightBot.needsStaminaRestore(0));
        assertTrue(IrrlightBot.needsStaminaRestore(0.49));
        assertFalse(IrrlightBot.needsStaminaRestore(0.5));
        assertFalse(IrrlightBot.needsStaminaRestore(0.9));

        assertFalse(IrrlightBot.needsEnergyRestore(-1));
        assertTrue(IrrlightBot.needsEnergyRestore(0));
        assertTrue(IrrlightBot.needsEnergyRestore(0.34));
        assertFalse(IrrlightBot.needsEnergyRestore(0.35));
        assertFalse(IrrlightBot.needsEnergyRestore(0.8));

        assertTrue(IrrlightBot.needsResourceRestore(0.4, 0.8));
        assertTrue(IrrlightBot.needsResourceRestore(0.9, 0.3));
        assertTrue(IrrlightBot.needsResourceRestore(0.4, 0.3));
        assertFalse(IrrlightBot.needsResourceRestore(0.5, 0.35));
        assertFalse(IrrlightBot.needsResourceRestore(-1, -1));
        assertFalse(IrrlightBot.needsResourceRestore(0.9, 0.8));

        assertEquals(IrrlightBot.WaitPhase.DRINK, IrrlightBot.resourceRestoreWaitPhase());
        assertTrue(IrrlightBot.observesIrrbloss(IrrlightBot.resourceRestoreWaitPhase()));

        String src = Files.readString(Path.of("src/nurgling/actions/bots/IrrlightBot.java"), StandardCharsets.UTF_8);
        assertFalse(src.contains("new RestoreResources()"));
        assertFalse(src.contains("import nurgling.actions.RestoreResources"));
        assertFalse(src.contains("new Eater("));
        int drink = src.indexOf("private Wake drink(");
        int restore = src.indexOf("private Wake restoreEnergy(");
        int drinkEnd = src.indexOf("private Wake restoreEnergy(", drink);
        assertTrue(drink >= 0 && restore > drink);
        String drinkBody = src.substring(drink, drinkEnd);
        assertTrue(drinkBody.contains("waitDuring(WaitPhase.DRINK"));
        String restoreBody = src.substring(restore, src.indexOf("private Wake openRecipe", restore));
        assertTrue(restoreBody.contains("waitDuring(resourceRestoreWaitPhase()"));
        assertTrue(restoreBody.contains("Wake.IRRLIGHT"));
        assertFalse(restoreBody.contains("WaitDuration"));
    }

    @Test
    void exactConfiguredFoodUsesStringEqualsNotPrefix() {
        assertTrue(IrrlightBot.isExactConfiguredFood("Roast Meat", Arrays.asList("Bread", "Roast Meat")));
        assertFalse(IrrlightBot.isExactConfiguredFood("Roast", Arrays.asList("Roast Meat")));
        assertFalse(IrrlightBot.isExactConfiguredFood("Roast Meat", Arrays.asList("Roast")));
        assertFalse(IrrlightBot.isExactConfiguredFood(null, Arrays.asList("Bread")));
        assertFalse(IrrlightBot.isExactConfiguredFood("Bread", null));

        assertEquals("Bread", IrrlightBot.firstConfiguredFoodName(
                Arrays.asList("Honey", "Bread"), Arrays.asList("Bread", "Roast Meat")));
        assertNull(IrrlightBot.firstConfiguredFoodName(
                Arrays.asList("Honey", "Cheese"), Arrays.asList("Bread")));
        assertNull(IrrlightBot.firstConfiguredFoodName(
                Arrays.asList("Roast"), Arrays.asList("Roast Meat")));
    }

    @Test
    void energyRestoreAllowsOvershootWhenHungry() {
        assertTrue(IrrlightBot.needsEnergyRestore(0.34));
        assertFalse(IrrlightBot.shouldRefuseFoodForOvershoot(0.34, 50.0));
        assertTrue(0.34 + 50.0 / 100.0 >= 0.81);
        assertTrue(IrrlightBot.shouldEatConfiguredFood(0.34, 50.0));
        assertFalse(IrrlightBot.shouldEatConfiguredFood(0.35, 50.0));
        assertFalse(IrrlightBot.shouldEatConfiguredFood(0.20, null));
    }

    @Test
    void decideEnergyRestoreTreatsMissingConfiguredFoodAsFatal() {
        assertEquals(IrrlightBot.EnergyRestoreDecision.SKIP,
                IrrlightBot.decideEnergyRestore(0.35, true));
        assertEquals(IrrlightBot.EnergyRestoreDecision.SKIP,
                IrrlightBot.decideEnergyRestore(0.80, false));
        assertEquals(IrrlightBot.EnergyRestoreDecision.NO_CONFIGURED_FOOD,
                IrrlightBot.decideEnergyRestore(0.34, false));
        assertEquals(IrrlightBot.EnergyRestoreDecision.EAT,
                IrrlightBot.decideEnergyRestore(0.34, true));
        assertEquals(IrrlightBot.EnergyRestoreDecision.EAT,
                IrrlightBot.decideEnergyRestore(0.0, true));
    }

    @Test
    void energyWakeErrorIsFatalAndMessagesAreActionable() {
        assertTrue(IrrlightBot.energyWakeIsFatal(IrrlightBot.Wake.ERROR));
        assertFalse(IrrlightBot.energyWakeIsFatal(IrrlightBot.Wake.IRRLIGHT));
        assertFalse(IrrlightBot.energyWakeIsFatal(IrrlightBot.Wake.DONE));
        assertFalse(IrrlightBot.energyWakeIsFatal(IrrlightBot.Wake.TIMEOUT));

        String noFood = IrrlightBot.noConfiguredFoodMessage();
        assertNotNull(noFood);
        assertFalse(noFood.isEmpty());
        assertTrue(noFood.toLowerCase().contains("food"));

        String eatFailed = IrrlightBot.eatFailedMessage();
        assertNotNull(eatFailed);
        assertFalse(eatFailed.isEmpty());
        assertTrue(eatFailed.toLowerCase().contains("eat"));
    }

    @Test
    void restoreEnergyBiteTimeoutIsFatal() throws Exception {
        assertTrue(IrrlightBot.energyBiteWakeIsFatal(IrrlightBot.Wake.TIMEOUT));
        assertTrue(IrrlightBot.energyBiteWakeIsFatal(IrrlightBot.Wake.ERROR));
        assertFalse(IrrlightBot.energyBiteWakeIsFatal(IrrlightBot.Wake.IRRLIGHT));
        assertFalse(IrrlightBot.energyBiteWakeIsFatal(IrrlightBot.Wake.DONE));

        String src = Files.readString(Path.of("src/nurgling/actions/bots/IrrlightBot.java"), StandardCharsets.UTF_8);
        int restore = src.indexOf("private Wake restoreEnergy(");
        int restoreEnd = src.indexOf("private Wake openRecipe", restore);
        assertTrue(restore >= 0 && restoreEnd > restore);
        String restoreBody = src.substring(restore, restoreEnd);
        String compact = restoreBody.replaceAll("\\s+", " ");
        assertTrue(restoreBody.contains("IsSuccess"));
        assertTrue(restoreBody.contains("Wake bite = waitDuring(resourceRestoreWaitPhase()"));
        assertTrue(compact.contains("if (bite == Wake.IRRLIGHT) return Wake.IRRLIGHT;"));
        assertTrue(compact.contains("if (bite == Wake.ERROR) { energyError = eatFailedMessage(); return Wake.ERROR; }"));
        assertTrue(compact.contains("if (energyBiteWakeIsFatal(bite)) { energyError = eatFailedMessage(); return Wake.ERROR; }"));
        assertFalse(compact.contains("if (bite != Wake.DONE) break;"));
        assertFalse(compact.contains("if (bite == Wake.TIMEOUT) break;"));
        assertFalse(compact.contains("if (energyBiteWakeIsFatal(bite)) break"));
        assertFalse(compact.contains("if (bite != Wake.DONE) return Wake.DONE"));
    }

    @Test
    void restoreEnergySourceScanRequiresConfiguredFoodAndEatResult() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/actions/bots/IrrlightBot.java"), StandardCharsets.UTF_8);
        int restore = src.indexOf("private Wake restoreEnergy(");
        int restoreEnd = src.indexOf("private Wake openRecipe", restore);
        assertTrue(restore >= 0 && restoreEnd > restore);
        String restoreBody = src.substring(restore, restoreEnd);
        assertFalse(restoreBody.contains("0.81"));
        assertTrue(restoreBody.contains("FoodContainer.getFoodNames"));
        assertTrue(restoreBody.contains("isExactConfiguredFood"));
        assertTrue(restoreBody.contains("((NGItem)"));
        assertTrue(restoreBody.contains(".name()"));
        assertTrue(restoreBody.contains("SelectFlowerAction"));
        assertTrue(restoreBody.contains("IsSuccess"));
        assertTrue(restoreBody.contains("noConfiguredFoodMessage"));
        assertTrue(restoreBody.contains("eatFailedMessage"));
        assertTrue(restoreBody.contains("Wake.ERROR"));
        assertFalse(restoreBody.contains("new RestoreResources()"));
        assertFalse(restoreBody.contains("new Eater("));
        assertFalse(restoreBody.contains("NAlias"));
    }

    @Test
    void restoreEnergyErrorStopsTheMainLoop() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/actions/bots/IrrlightBot.java"), StandardCharsets.UTF_8);
        int run = src.indexOf("public Results run(NGameUI gui)");
        int runEnd = src.indexOf("private Results prepareCrucible", run);
        assertTrue(run >= 0 && runEnd > run);
        String body = src.substring(run, runEnd);
        assertTrue(body.contains("Wake energyWake = restoreEnergy(gui)"));
        assertTrue(body.contains("energyWake == Wake.IRRLIGHT"));
        assertTrue(body.contains("energyWakeIsFatal(energyWake)")
                || body.contains("energyWake == Wake.ERROR"));
        assertTrue(body.contains("Results.ERROR"));
        assertTrue(body.contains("noConfiguredFoodMessage()")
                || body.contains("eatFailedMessage()"));
        assertTrue(body.contains("return Results.ERROR(\"NO WATER\")"));
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
