package nurgling.craftatlas.quality;

import nurgling.craftatlas.quality.QualityWorkshopModel.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityWorkshopQuantitiesTest {
    private static final double EPSILON = 0.0000001;

    @Test
    void aggregatesSharedInputsBeforeRoundingCoadeBatches() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.watch(Key.BRICK);
        model.setDesiredAmount(Key.COADE_CLAY, 12);

        QualityWorkshopQuantities.Plan plan = QualityWorkshopQuantities.calculate(model);

        assertEquals(12, plan.production.get(Key.COADE_CLAY), EPSILON);
        assertEquals(2, plan.production.get(Key.BRICK), EPSILON);
        assertEquals(14, plan.materials.get(Key.BALL_CLAY), EPSILON);
        assertEquals(2, plan.materials.get(Key.FLINT), EPSILON);
        assertEquals(1, plan.equipment.get(Key.KILN), EPSILON);
        assertTrue(plan.warnings.contains("quern_required"));
        assertTrue(plan.warnings.contains("kiln_fuel"));
    }

    @Test
    void expandsPowdersLinearlyAndSharesBoneAshDemand() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.watch(Key.BONE_ASH);
        model.watch(Key.ASH);
        model.watch(Key.LYE);
        model.setBoneClaySource(Key.SOAP_CLAY);
        model.setDesiredAmount(Key.BONE_CLAY, 1);

        QualityWorkshopQuantities.Plan plan = QualityWorkshopQuantities.calculate(model);

        assertEquals(1, plan.production.get(Key.BONE_CLAY), EPSILON);
        assertEquals(1, plan.production.get(Key.SOAP_CLAY), EPSILON);
        assertEquals(0.02, plan.production.get(Key.LYE), EPSILON);
        assertEquals(0.2, plan.production.get(Key.ASH), EPSILON);
        assertEquals(6, plan.production.get(Key.BONE_ASH), EPSILON);
        assertEquals(6, plan.materials.get(Key.BONES), EPSILON);
        assertEquals(0.1, plan.materials.get(Key.SALT_WATER), EPSILON);
        assertEquals(1, plan.equipment.get(Key.CAULDRON), EPSILON);
        assertTrue(plan.warnings.contains("cauldron_water"));
    }

    @Test
    void sharesWatchedKilnBetweenItsOwnTargetAndBrickProduction() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.watch(Key.KILN);
        model.watch(Key.BRICK);
        model.setDesiredAmount(Key.KILN, 1);
        model.setDesiredAmount(Key.BRICK, 1);

        QualityWorkshopQuantities.Plan plan = QualityWorkshopQuantities.calculate(model);

        assertEquals(1, plan.production.get(Key.KILN), EPSILON);
        assertEquals(35, plan.materials.get(Key.KILN_CLAY), EPSILON);
        assertEquals(1, plan.production.get(Key.BRICK), EPSILON);
        assertFalse(plan.equipment.containsKey(Key.KILN));
    }

    @Test
    void plansAnvilCastingAndTreatsItsLitFurnaceAsReusableEquipment() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.watch(Key.ANVIL);
        model.setAnvilCastingSource(Key.SAND);
        model.setDesiredAmount(Key.ANVIL, 1);

        QualityWorkshopQuantities.Plan plan = QualityWorkshopQuantities.calculate(model);

        assertEquals(1, plan.production.get(Key.ANVIL), EPSILON);
        assertEquals(10, plan.materials.get(Key.SAND), EPSILON);
        assertEquals(5, plan.materials.get(Key.HARD_METAL), EPSILON);
        assertEquals(1, plan.equipment.get(Key.ORE_SMELTER), EPSILON);
        assertTrue(plan.warnings.contains("casting_fuel"));
    }

    @Test
    void leavesStochasticSmeltingAsAnExplicitUnresolvedMaterial() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.watch(Key.SMELTED_METAL);
        model.setDesiredAmount(Key.SMELTED_METAL, 3);

        QualityWorkshopQuantities.Plan plan = QualityWorkshopQuantities.calculate(model);

        assertEquals(3, plan.materials.get(Key.SMELTED_METAL), EPSILON);
        assertFalse(plan.production.containsKey(Key.SMELTED_METAL));
        assertFalse(plan.materials.containsKey(Key.ORE));
        assertTrue(plan.warnings.contains("smelted_metal_yield"));
        assertEquals(1, plan.equipment.get(Key.ORE_SMELTER), EPSILON);
    }

    @Test
    void countsSerialMiningAxesWhileSharingTheInitialWatchedAxe() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.watch(Key.MINED_STONE);
        model.watch(Key.STONE_AXE);
        model.setMiningGenerations(3);
        model.setDesiredAmount(Key.STONE_AXE, 1);
        model.setDesiredAmount(Key.MINED_STONE, 2);

        QualityWorkshopQuantities.Plan plan = QualityWorkshopQuantities.calculate(model);

        assertEquals(4, plan.production.get(Key.MINED_STONE), EPSILON);
        assertEquals(3, plan.production.get(Key.STONE_AXE), EPSILON);
        assertEquals(1, plan.materials.get(Key.STONE), EPSILON);
        assertEquals(3, plan.materials.get(Key.BRANCH), EPSILON);
    }

    @Test
    void keepsRequestedInitialQualityAxesSeparateFromLaterGenerationAxes() {
        for(int requested : new int[]{2, 5}) {
            QualityWorkshopModel model = new QualityWorkshopModel();
            model.watch(Key.MINED_STONE);
            model.watch(Key.STONE_AXE);
            model.setMiningGenerations(3);
            model.setDesiredAmount(Key.STONE_AXE, requested);
            model.setDesiredAmount(Key.MINED_STONE, 100);

            QualityWorkshopQuantities.Plan plan = QualityWorkshopQuantities.calculate(model);
            assertEquals(requested + 2, plan.production.get(Key.STONE_AXE), EPSILON);
            assertEquals(requested, plan.materials.get(Key.STONE), EPSILON);
            assertEquals(requested + 2, plan.materials.get(Key.BRANCH), EPSILON);
            assertEquals(102, plan.production.get(Key.MINED_STONE), EPSILON);
        }
    }

    @Test
    void marksUnverifiedPotterClayYieldInsteadOfInventingOne() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.setDesiredAmount(Key.POTTER_CLAY, 1);

        QualityWorkshopQuantities.Plan plan = QualityWorkshopQuantities.calculate(model);

        assertEquals(1, plan.materials.get(Key.POTTER_CLAY), EPSILON);
        assertFalse(plan.production.containsKey(Key.POTTER_CLAY));
        assertTrue(plan.warnings.contains("potter_clay_yield"));
    }

    @Test
    void plansKnownPotterYieldAcrossFiniteGenerationsWithoutReexpandingTheRefiredBrick() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.setPotterOutputPerCraft(4);
        model.setGenerations(2);
        model.watch(Key.BRICK);
        model.setDesiredAmount(Key.POTTER_CLAY, 10);

        QualityWorkshopQuantities.Plan plan = QualityWorkshopQuantities.calculate(model);

        assertEquals(16, plan.production.get(Key.POTTER_CLAY), EPSILON);
        assertEquals(4, plan.materials.get(Key.ACRE_CLAY), EPSILON);
        assertEquals(4, plan.materials.get(Key.GRAY_CLAY), EPSILON);
        assertEquals(4, plan.production.get(Key.BRICK), EPSILON);
        assertEquals(1, plan.equipment.get(Key.POTTERS_WHEEL), EPSILON);
        assertEquals(1, plan.equipment.get(Key.KILN), EPSILON);
        assertTrue(plan.warnings.contains("kiln_fuel"));
    }

    @Test
    void toleratesBinaryAccumulationAtExactAshBatchBoundaries() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.watch(Key.ASH);
        model.setDesiredAmount(Key.ASH, 0.6000000000000001);

        QualityWorkshopQuantities.Plan plan = QualityWorkshopQuantities.calculate(model);

        assertEquals(0.6, plan.production.get(Key.ASH), EPSILON);
        assertEquals(3, plan.materials.get(Key.BONE_ASH), EPSILON);
    }

    @Test
    void potterClayUsedOnlyAsABaseIngredientDoesNotStartItsRepeatKilnLoop() {
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.setPotterOutputPerCraft(4);
        model.setGenerations(3);
        model.setStackClaySource(Key.POTTER_CLAY);
        model.watch(Key.STACK_FURNACE);
        model.setDesiredAmount(Key.STACK_FURNACE, 1);

        QualityWorkshopQuantities.Plan plan = QualityWorkshopQuantities.calculate(model);

        assertFalse(plan.equipment.containsKey(Key.KILN));
        assertFalse(plan.warnings.contains("kiln_fuel"));
    }
}
