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

    private static void set(QualityWorkshopModel model, Object... values) {
        for(int index = 0; index < values.length; index += 2)
            assertTrue(model.setManual((QualityWorkshopModel.Key) values[index], ((Number) values[index + 1]).doubleValue()));
    }
}
