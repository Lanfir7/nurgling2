package nurgling.craftatlas.quality;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityWorkshopModelTest {
    private static final double EPSILON = 0.0000001;

    @Test
    void calculatesTheVerifiedRecipeChainWithoutRounding() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.watch(QualityWorkshopModel.Key.BONE_ASH);
        model.watch(QualityWorkshopModel.Key.ASH);
        model.watch(QualityWorkshopModel.Key.LYE);
        model.watch(QualityWorkshopModel.Key.BRICK);
        model.watch(QualityWorkshopModel.Key.POTTERS_WHEEL);
        set(model, QualityWorkshopModel.Key.BONES, 20, QualityWorkshopModel.Key.FUEL, 12,
                QualityWorkshopModel.Key.KILN, 8, QualityWorkshopModel.Key.CAULDRON, 20,
                QualityWorkshopModel.Key.WATER, 12, QualityWorkshopModel.Key.FELDSPAR, 20,
                QualityWorkshopModel.Key.PIT_CLAY, 10, QualityWorkshopModel.Key.SALT_WATER, 10,
                QualityWorkshopModel.Key.CAVE_CLAY, 10, QualityWorkshopModel.Key.BALL_CLAY, 20,
                QualityWorkshopModel.Key.ACRE_CLAY, 20, QualityWorkshopModel.Key.GRAY_CLAY, 20,
                QualityWorkshopModel.Key.DEXTERITY, 100, QualityWorkshopModel.Key.INTELLIGENCE, 100,
                QualityWorkshopModel.Key.MASONRY, 100);

        assertEquals(15, model.baseValue(QualityWorkshopModel.Key.BONE_ASH), EPSILON);
        assertEquals(15.5, model.baseValue(QualityWorkshopModel.Key.LYE), EPSILON);
        assertEquals(12.875, model.baseValue(QualityWorkshopModel.Key.SOAP_CLAY), EPSILON);
        assertEquals(15, model.baseValue(QualityWorkshopModel.Key.BONE_CLAY), EPSILON);
        assertEquals(15, model.baseValue(QualityWorkshopModel.Key.BRICK), EPSILON);
        assertEquals(16.125, model.baseValue(QualityWorkshopModel.Key.POTTER_CLAY), EPSILON);
    }

    @Test
    void promotesBrickAndFreezesItsBaseResultWhenRemoved() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        set(model, QualityWorkshopModel.Key.BALL_CLAY, 20, QualityWorkshopModel.Key.FUEL, 12,
                QualityWorkshopModel.Key.KILN, 8);
        assertTrue(model.inputs().containsKey(QualityWorkshopModel.Key.BRICK));

        model.watch(QualityWorkshopModel.Key.BRICK);
        assertFalse(model.inputs().containsKey(QualityWorkshopModel.Key.BRICK));
        assertEquals(Arrays.asList(QualityWorkshopModel.Key.BRICK),
                model.inputs().get(QualityWorkshopModel.Key.FUEL));
        assertEquals(15, model.baseValue(QualityWorkshopModel.Key.BRICK), EPSILON);

        model.unwatch(QualityWorkshopModel.Key.BRICK);
        assertEquals(15, model.manual(QualityWorkshopModel.Key.BRICK), EPSILON);
        model.setManual(QualityWorkshopModel.Key.BALL_CLAY, 80);
        assertEquals(15, model.baseValue(QualityWorkshopModel.Key.BRICK), EPSILON);
        List<QualityWorkshopModel.Key> consumers = model.inputs().get(QualityWorkshopModel.Key.BRICK);
        assertTrue(consumers.contains(QualityWorkshopModel.Key.POTTER_CLAY));
        assertTrue(consumers.contains(QualityWorkshopModel.Key.COADE_CLAY));
    }

    @Test
    void expandsSharedBoneAshInputsAndWatchesSelectedRecipeSources() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.watch(QualityWorkshopModel.Key.BONE_ASH);
        model.watch(QualityWorkshopModel.Key.BRICK);
        Map<QualityWorkshopModel.Key, List<QualityWorkshopModel.Key>> inputs = model.inputs();
        assertTrue(inputs.get(QualityWorkshopModel.Key.FUEL).contains(QualityWorkshopModel.Key.BONE_ASH));
        assertTrue(inputs.get(QualityWorkshopModel.Key.FUEL).contains(QualityWorkshopModel.Key.BRICK));

        model.unwatch(QualityWorkshopModel.Key.SOAP_CLAY);
        model.setBoneClaySource(QualityWorkshopModel.Key.SOAP_CLAY);
        assertTrue(model.watched().contains(QualityWorkshopModel.Key.SOAP_CLAY));
        model.setBrickClaySource(QualityWorkshopModel.Key.BONE_CLAY);
        assertTrue(model.watched().contains(QualityWorkshopModel.Key.BONE_CLAY));
        for(QualityWorkshopModel.Key recipe : QualityWorkshopModel.recipes()) model.watch(recipe);
        for(QualityWorkshopModel.Key recipe : QualityWorkshopModel.recipes())
            assertTrue(Double.isFinite(model.baseValue(recipe)));
    }

    @Test
    void independentlyGeneratesPotterClayAndKeepsTheInitialBrickSource() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.unwatch(QualityWorkshopModel.Key.BONE_CLAY);
        for(QualityWorkshopModel.Key key : QualityWorkshopModel.Key.values()) model.setManual(key, 100);
        model.setManual(QualityWorkshopModel.Key.DEXTERITY, 10);
        model.setManual(QualityWorkshopModel.Key.MASONRY, 10);
        model.setGenerations(3);

        List<QualityWorkshopModel.Iteration> iterations = model.iterations();
        assertEquals(3, iterations.size());
        assertEquals(100, iterations.get(0).brickQuality, EPSILON);
        assertEquals(55, iterations.get(0).clayQuality, EPSILON);
        assertEquals(77.5, iterations.get(1).brickQuality, EPSILON);
        assertTrue(iterations.get(1).clayQuality < iterations.get(0).clayQuality);
        assertEquals(100, model.baseValue(QualityWorkshopModel.Key.BRICK), EPSILON);
        assertEquals(iterations.get(2).clayQuality, model.value(QualityWorkshopModel.Key.POTTER_CLAY), EPSILON);
        assertTrue(model.inputs().containsKey(QualityWorkshopModel.Key.FUEL));
        assertTrue(model.inputs().containsKey(QualityWorkshopModel.Key.KILN));
    }

    @Test
    void appliesEachCharacterSoftcap() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        for(QualityWorkshopModel.Key key : QualityWorkshopModel.Key.values()) model.setManual(key, 100);
        model.setManual(QualityWorkshopModel.Key.DEXTERITY, 25);
        model.setManual(QualityWorkshopModel.Key.INTELLIGENCE, 25);
        model.setManual(QualityWorkshopModel.Key.MASONRY, 100);

        assertEquals(75, model.baseValue(QualityWorkshopModel.Key.SOAP_CLAY), EPSILON);
        assertEquals(75, model.baseValue(QualityWorkshopModel.Key.BONE_CLAY), EPSILON);
        model.unwatch(QualityWorkshopModel.Key.BONE_CLAY);
        model.setManual(QualityWorkshopModel.Key.BONE_CLAY, 100);
        assertEquals(75, model.baseValue(QualityWorkshopModel.Key.POTTER_CLAY), EPSILON);
        assertEquals(75, model.baseValue(QualityWorkshopModel.Key.COADE_CLAY), EPSILON);
    }

    @Test
    void validatesDecimalManualValuesAndSafelyLoadsJson() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        assertTrue(model.setManual(QualityWorkshopModel.Key.FUEL, 12.25));
        assertFalse(model.setManual(QualityWorkshopModel.Key.FUEL, Double.NaN));
        assertFalse(model.setManual(QualityWorkshopModel.Key.FUEL, 0.99));
        assertFalse(model.setManual(QualityWorkshopModel.Key.FUEL, 100000.01));
        assertEquals(12.25, model.manual(QualityWorkshopModel.Key.FUEL), EPSILON);

        model.snapshot();
        assertEquals(model.value(QualityWorkshopModel.Key.POTTER_CLAY), model.baseline(QualityWorkshopModel.Key.POTTER_CLAY), EPSILON);
        model.watch(QualityWorkshopModel.Key.BRICK);
        assertNull(model.baseline(QualityWorkshopModel.Key.BRICK));
        QualityWorkshopModel restored = QualityWorkshopModel.fromJson(model.toJson());
        assertEquals(12.25, restored.manual(QualityWorkshopModel.Key.FUEL), EPSILON);
        assertEquals(model.baseline(QualityWorkshopModel.Key.POTTER_CLAY), restored.baseline(QualityWorkshopModel.Key.POTTER_CLAY), EPSILON);

        JSONObject malformed = new JSONObject().put("watched", new JSONArray().put("UNKNOWN").put(17))
                .put("manual", new JSONObject().put("FUEL", "bad").put("UNKNOWN", 99))
                .put("boneClaySource", "BAD").put("brickClaySource", "PIT_CLAY").put("generations", 99)
                .put("baseline", new JSONObject().put("POTTER_CLAY", -5));
        QualityWorkshopModel safe = QualityWorkshopModel.fromJson(malformed);
        assertTrue(safe.watched().isEmpty());
        assertEquals(10, safe.manual(QualityWorkshopModel.Key.FUEL), EPSILON);
        assertEquals(1, safe.generations());
        assertNull(safe.baseline(QualityWorkshopModel.Key.POTTER_CLAY));
    }

    @Test
    void restoresAnExplicitlyUnwatchedRecipeSourceWithoutResurrectingIt() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.setBoneClaySource(QualityWorkshopModel.Key.SOAP_CLAY);
        model.unwatch(QualityWorkshopModel.Key.SOAP_CLAY);
        model.setManual(QualityWorkshopModel.Key.SOAP_CLAY, 42.5);
        model.unwatch(QualityWorkshopModel.Key.BONE_CLAY);
        model.unwatch(QualityWorkshopModel.Key.POTTER_CLAY);
        model.unwatch(QualityWorkshopModel.Key.COADE_CLAY);

        QualityWorkshopModel restored = QualityWorkshopModel.fromJson(model.toJson());
        assertTrue(restored.watched().isEmpty());
        assertFalse(restored.watched().contains(QualityWorkshopModel.Key.SOAP_CLAY));
        assertEquals(QualityWorkshopModel.Key.SOAP_CLAY, restored.boneClaySource());
        assertEquals(42.5, restored.manual(QualityWorkshopModel.Key.SOAP_CLAY), EPSILON);
    }

    @Test
    void persistsIndependentHiddenCardsWithoutChangingTheCalculationGraph() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        set(model, QualityWorkshopModel.Key.BONES, 20, QualityWorkshopModel.Key.FUEL, 12,
                QualityWorkshopModel.Key.KILN, 8, QualityWorkshopModel.Key.FELDSPAR, 20,
                QualityWorkshopModel.Key.PIT_CLAY, 10, QualityWorkshopModel.Key.INTELLIGENCE, 100,
                QualityWorkshopModel.Key.MASONRY, 100);
        model.setInputHidden(QualityWorkshopModel.Key.BONE_ASH, true);
        assertEquals(13.333333333333334, model.baseValue(QualityWorkshopModel.Key.BONE_CLAY), EPSILON);
        model.watch(QualityWorkshopModel.Key.BONE_ASH);
        assertEquals(15, model.baseValue(QualityWorkshopModel.Key.BONE_CLAY), EPSILON);
        assertTrue(model.inputHidden(QualityWorkshopModel.Key.BONE_ASH));
        double potterBefore = model.value(QualityWorkshopModel.Key.POTTER_CLAY);
        Map<QualityWorkshopModel.Key, List<QualityWorkshopModel.Key>> inputsBefore = model.inputs();
        List<QualityWorkshopModel.Key> watchedBefore = model.watched();

        model.setResultHidden(QualityWorkshopModel.Key.POTTER_CLAY, true);
        model.setResultHidden(QualityWorkshopModel.Key.FUEL, true);
        model.setHiddenInputsExpanded(true);
        model.setHiddenResultsExpanded(true);

        assertTrue(model.inputHidden(QualityWorkshopModel.Key.BONE_ASH));
        assertTrue(model.resultHidden(QualityWorkshopModel.Key.POTTER_CLAY));
        assertFalse(model.resultHidden(QualityWorkshopModel.Key.FUEL));
        assertEquals(inputsBefore, model.inputs());
        assertEquals(watchedBefore, model.watched());
        assertEquals(potterBefore, model.value(QualityWorkshopModel.Key.POTTER_CLAY), EPSILON);

        QualityWorkshopModel restored = QualityWorkshopModel.fromJson(model.toJson());
        assertTrue(restored.inputHidden(QualityWorkshopModel.Key.BONE_ASH));
        assertTrue(restored.resultHidden(QualityWorkshopModel.Key.POTTER_CLAY));
        assertTrue(restored.hiddenInputsExpanded());
        assertTrue(restored.hiddenResultsExpanded());
        restored.unwatch(QualityWorkshopModel.Key.BONE_ASH);
        assertTrue(restored.inputHidden(QualityWorkshopModel.Key.BONE_ASH));
        assertTrue(restored.inputs().containsKey(QualityWorkshopModel.Key.BONE_ASH));
    }

    @Test
    void defaultsAndMalformedHiddenCardStateAreSafe() {
        QualityWorkshopModel defaults = new QualityWorkshopModel();
        assertFalse(defaults.inputHidden(QualityWorkshopModel.Key.FUEL));
        assertFalse(defaults.resultHidden(QualityWorkshopModel.Key.POTTER_CLAY));
        assertFalse(defaults.hiddenInputsExpanded());
        assertFalse(defaults.hiddenResultsExpanded());

        JSONObject malformed = new JSONObject()
                .put("hiddenInputs", new JSONArray().put("FUEL").put("UNKNOWN").put(7))
                .put("hiddenResults", new JSONArray().put("POTTER_CLAY").put("FUEL").put("UNKNOWN"))
                .put("hiddenInputsExpanded", true).put("hiddenResultsExpanded", true);
        QualityWorkshopModel restored = QualityWorkshopModel.fromJson(malformed);
        assertTrue(restored.inputHidden(QualityWorkshopModel.Key.FUEL));
        assertTrue(restored.resultHidden(QualityWorkshopModel.Key.POTTER_CLAY));
        assertFalse(restored.resultHidden(QualityWorkshopModel.Key.FUEL));
        assertTrue(restored.hiddenInputsExpanded());
        assertTrue(restored.hiddenResultsExpanded());
    }

    @Test
    void calculatesConstructionSmeltingAxeAndMiningWithoutQuantityWeighting() {
        QualityWorkshopModel construction = new QualityWorkshopModel();
        construction.watch(QualityWorkshopModel.Key.ORE_SMELTER);
        set(construction, QualityWorkshopModel.Key.BRICK, 219, QualityWorkshopModel.Key.STONE, 420,
                QualityWorkshopModel.Key.HARD_METAL, 300);
        assertEquals(313, construction.baseValue(QualityWorkshopModel.Key.ORE_SMELTER), EPSILON);

        construction.watch(QualityWorkshopModel.Key.STACK_FURNACE);
        set(construction, QualityWorkshopModel.Key.BALL_CLAY, 100, QualityWorkshopModel.Key.STONE, 200,
                QualityWorkshopModel.Key.BOARD, 300, QualityWorkshopModel.Key.BLOCK, 400,
                QualityWorkshopModel.Key.LEATHER, 500);
        assertEquals(300, construction.baseValue(QualityWorkshopModel.Key.STACK_FURNACE), EPSILON);

        QualityWorkshopModel pitStack = new QualityWorkshopModel();
        pitStack.watch(QualityWorkshopModel.Key.STACK_FURNACE);
        pitStack.setStackClaySource(QualityWorkshopModel.Key.PIT_CLAY);
        set(pitStack, QualityWorkshopModel.Key.PIT_CLAY, 100, QualityWorkshopModel.Key.STONE, 200,
                QualityWorkshopModel.Key.BOARD, 300, QualityWorkshopModel.Key.BLOCK, 400,
                QualityWorkshopModel.Key.LEATHER, 500);
        assertEquals(300, pitStack.baseValue(QualityWorkshopModel.Key.STACK_FURNACE), EPSILON);
        QualityWorkshopModel restoredStack = QualityWorkshopModel.fromJson(pitStack.toJson());
        assertEquals(QualityWorkshopModel.Key.PIT_CLAY, restoredStack.stackClaySource());
        assertEquals(300, restoredStack.baseValue(QualityWorkshopModel.Key.STACK_FURNACE), EPSILON);

        QualityWorkshopModel smelting = new QualityWorkshopModel();
        smelting.watch(QualityWorkshopModel.Key.SMELTED_METAL);
        set(smelting, QualityWorkshopModel.Key.ORE, 100, QualityWorkshopModel.Key.ORE_SMELTER, 80,
                QualityWorkshopModel.Key.COAL, 60, QualityWorkshopModel.Key.STACK_FURNACE, 50,
                QualityWorkshopModel.Key.FUEL, 70);
        assertEquals(85, smelting.baseValue(QualityWorkshopModel.Key.SMELTED_METAL), EPSILON);
        smelting.setSmeltingFurnace(QualityWorkshopModel.Key.STACK_FURNACE);
        assertFalse(smelting.watched().contains(QualityWorkshopModel.Key.STACK_FURNACE));
        assertEquals(80, smelting.baseValue(QualityWorkshopModel.Key.SMELTED_METAL), EPSILON);

        QualityWorkshopModel mining = new QualityWorkshopModel();
        mining.watch(QualityWorkshopModel.Key.STONE_AXE);
        mining.watch(QualityWorkshopModel.Key.MINED_STONE);
        set(mining, QualityWorkshopModel.Key.STONE, 100, QualityWorkshopModel.Key.BRANCH, 80,
                QualityWorkshopModel.Key.INTELLIGENCE, 25, QualityWorkshopModel.Key.SURVIVAL, 100,
                QualityWorkshopModel.Key.STONE_WALL, 100, QualityWorkshopModel.Key.MASONRY, 60);
        assertEquals(70, mining.baseValue(QualityWorkshopModel.Key.STONE_AXE), EPSILON);
        assertEquals(60, mining.baseValue(QualityWorkshopModel.Key.MINED_STONE), EPSILON);

        QualityWorkshopModel masonryFirst = new QualityWorkshopModel();
        masonryFirst.watch(QualityWorkshopModel.Key.MINED_STONE);
        set(masonryFirst, QualityWorkshopModel.Key.STONE_AXE, 10, QualityWorkshopModel.Key.STONE_WALL, 100,
                QualityWorkshopModel.Key.MASONRY, 60);
        assertEquals(35, masonryFirst.baseValue(QualityWorkshopModel.Key.MINED_STONE), EPSILON);
    }

    @Test
    void miningRepeatsWithNewAxesAndKeepsInitialAxeIndependent() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.watch(QualityWorkshopModel.Key.MINED_STONE);
        set(model, QualityWorkshopModel.Key.STONE, 10, QualityWorkshopModel.Key.STONE_AXE, 10,
                QualityWorkshopModel.Key.BRANCH, 100, QualityWorkshopModel.Key.INTELLIGENCE, 100,
                QualityWorkshopModel.Key.SURVIVAL, 100, QualityWorkshopModel.Key.STONE_WALL, 100,
                QualityWorkshopModel.Key.MASONRY, 100);
        model.setMiningGenerations(3);

        List<QualityWorkshopModel.MiningIteration> rows = model.miningIterations();
        assertEquals(3, rows.size());
        assertEquals(10, rows.get(0).inputStoneQuality, EPSILON);
        assertEquals(10, rows.get(0).axeQuality, EPSILON);
        assertEquals(55, rows.get(0).stoneQuality, EPSILON);
        assertEquals(55, rows.get(1).inputStoneQuality, EPSILON);
        assertEquals(77.5, rows.get(1).axeQuality, EPSILON);
        assertEquals(80.3125, rows.get(1).stoneQuality, EPSILON);
        assertEquals(rows.get(2).stoneQuality, model.value(QualityWorkshopModel.Key.MINED_STONE), EPSILON);
        assertEquals(55, model.baseValue(QualityWorkshopModel.Key.MINED_STONE), EPSILON);
        assertTrue(model.inputs().containsKey(QualityWorkshopModel.Key.BRANCH));
        assertTrue(model.inputs().containsKey(QualityWorkshopModel.Key.INTELLIGENCE));
        assertTrue(model.inputs().containsKey(QualityWorkshopModel.Key.SURVIVAL));
    }

    @Test
    void selectorChoicesPersistWithoutWatchingRecipesDuringRestore() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.setSmeltingFurnace(QualityWorkshopModel.Key.STACK_FURNACE);
        model.setSmeltingOreSource(QualityWorkshopModel.Key.MINED_ORE);
        model.setStackClaySource(QualityWorkshopModel.Key.POTTER_CLAY);
        model.setMiningGenerations(4);
        model.unwatch(QualityWorkshopModel.Key.MINED_ORE);
        model.unwatch(QualityWorkshopModel.Key.POTTER_CLAY);

        QualityWorkshopModel restored = QualityWorkshopModel.fromJson(model.toJson());
        assertEquals(QualityWorkshopModel.Key.STACK_FURNACE, restored.smeltingFurnace());
        assertEquals(QualityWorkshopModel.Key.MINED_ORE, restored.smeltingOreSource());
        assertEquals(QualityWorkshopModel.Key.POTTER_CLAY, restored.stackClaySource());
        assertEquals(4, restored.miningGenerations());
        assertFalse(restored.watched().contains(QualityWorkshopModel.Key.MINED_ORE));
        assertFalse(restored.watched().contains(QualityWorkshopModel.Key.POTTER_CLAY));
    }

    @Test
    void kilnUsesFrozenClayInputAndExpandsInsteadOfAppearingAsAManualRepeatInput() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.watch(QualityWorkshopModel.Key.KILN);
        model.watch(QualityWorkshopModel.Key.BRICK);
        set(model, QualityWorkshopModel.Key.KILN_CLAY, 40, QualityWorkshopModel.Key.BALL_CLAY, 20,
                QualityWorkshopModel.Key.FUEL, 12, QualityWorkshopModel.Key.DEXTERITY, 100,
                QualityWorkshopModel.Key.MASONRY, 100);
        assertEquals(40, model.baseValue(QualityWorkshopModel.Key.KILN), EPSILON);
        assertEquals(23, model.baseValue(QualityWorkshopModel.Key.BRICK), EPSILON);

        model.setGenerations(2);
        assertEquals(19.21875, model.iterations().get(1).brickQuality, EPSILON);
        assertFalse(model.inputs().containsKey(QualityWorkshopModel.Key.KILN));
        assertTrue(model.inputs().containsKey(QualityWorkshopModel.Key.KILN_CLAY));
    }

    @Test
    void anvilUsesMetalAndCastingMaterialWithoutFurnaceQualityAndRestoresFrozenSources() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.watch(QualityWorkshopModel.Key.ANVIL);
        set(model, QualityWorkshopModel.Key.HARD_METAL, 100, QualityWorkshopModel.Key.CASTING_MATERIAL, 40);
        assertEquals(85, model.value(QualityWorkshopModel.Key.ANVIL), EPSILON);
        model.setAnvilFurnace(QualityWorkshopModel.Key.STACK_FURNACE);
        model.setManual(QualityWorkshopModel.Key.STACK_FURNACE, 999);
        assertEquals(85, model.value(QualityWorkshopModel.Key.ANVIL), EPSILON);
        assertFalse(model.dependencies(QualityWorkshopModel.Key.ANVIL).contains(QualityWorkshopModel.Key.STACK_FURNACE));
        model.setAnvilCastingSource(QualityWorkshopModel.Key.BONE_CLAY);
        assertTrue(model.watched().contains(QualityWorkshopModel.Key.BONE_CLAY));
        model.unwatch(QualityWorkshopModel.Key.BONE_CLAY);
        model.setManual(QualityWorkshopModel.Key.BONE_CLAY, 80);
        assertEquals(95, model.value(QualityWorkshopModel.Key.ANVIL), EPSILON);
        assertTrue(model.setDesiredAmount(QualityWorkshopModel.Key.ANVIL, 3));
        assertTrue(model.setPotterOutputPerCraft(4));
        QualityWorkshopModel restored = QualityWorkshopModel.fromJson(model.toJson());
        assertEquals(3, restored.desiredAmount(QualityWorkshopModel.Key.ANVIL));
        assertEquals(4, restored.potterOutputPerCraft());
        assertEquals(QualityWorkshopModel.Key.BONE_CLAY, restored.anvilCastingSource());
        assertEquals(QualityWorkshopModel.Key.STACK_FURNACE, restored.anvilFurnace());
        assertFalse(restored.watched().contains(QualityWorkshopModel.Key.BONE_CLAY));
        assertEquals(95, restored.value(QualityWorkshopModel.Key.ANVIL), EPSILON);
    }

    @Test
    void validatesDesiredCountsAndSafelyLoadsOldOrMalformedScenarios() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        assertEquals(0, model.desiredAmount(QualityWorkshopModel.Key.ANVIL));
        assertTrue(model.setDesiredAmount(QualityWorkshopModel.Key.ANVIL, 2));
        for(double n : new double[]{-1, 0.5, Double.NaN, Double.POSITIVE_INFINITY, 1000001})
            assertFalse(model.setDesiredAmount(QualityWorkshopModel.Key.ANVIL, n));
        assertEquals(2, model.desiredAmount(QualityWorkshopModel.Key.ANVIL));
        assertTrue(model.setDesiredAmount(QualityWorkshopModel.Key.LYE, 0.5));
        assertTrue(model.setDesiredAmount(QualityWorkshopModel.Key.ASH, 0.2));
        assertFalse(model.setDesiredAmount(QualityWorkshopModel.Key.BONES, 2));
        assertFalse(model.setDesiredAmount(null, 2));
        assertFalse(model.setPotterOutputPerCraft(-1));
        assertFalse(model.setPotterOutputPerCraft(1001));
        QualityWorkshopModel old = QualityWorkshopModel.fromJson(new JSONObject());
        assertEquals(0, old.desiredAmount(QualityWorkshopModel.Key.POTTER_CLAY));
        assertEquals(0, old.potterOutputPerCraft());
        JSONObject bad = new JSONObject().put("desiredAmounts", new JSONObject().put("ANVIL", -3).put("LYE", "broken").put("UNKNOWN", 10));
        QualityWorkshopModel restored = QualityWorkshopModel.fromJson(bad);
        assertEquals(0, restored.desiredAmount(QualityWorkshopModel.Key.ANVIL));
        assertEquals(0, restored.desiredAmount(QualityWorkshopModel.Key.LYE));
    }

    private static void set(QualityWorkshopModel model, Object... values) {
        for(int index = 0; index < values.length; index += 2)
            assertTrue(model.setManual((QualityWorkshopModel.Key) values[index], ((Number) values[index + 1]).doubleValue()));
    }
}
