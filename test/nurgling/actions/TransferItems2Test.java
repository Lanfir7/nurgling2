package nurgling.actions;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferItems2Test {

    @Test
    void qualityAtNextThresholdBelongsOnlyToHigherBand() {
        assertTrue(TransferItems2.matchesQuality(19.99, 1.0, 20.0));
        assertFalse(TransferItems2.matchesQuality(20.0, 1.0, 20.0));
        assertTrue(TransferItems2.matchesQuality(20.0, 20.0, null));
    }

    @Test
    void differentItemsAssignedToSameAreaBecomeOneStop() {
        Map<String, NavigableMap<Double, String>> destinations = new LinkedHashMap<>();
        destinations.put("Bone", thresholds(1.0, "bone-low", 20.0, "shared"));
        destinations.put("Entrails", thresholds(1.0, "shared", 30.0, "entrails-high"));

        Map<String, List<Double>> qualities = new LinkedHashMap<>();
        qualities.put("Bone", List.of(25.0));
        qualities.put("Entrails", List.of(10.0));

        Map<String, List<TransferItems2.ItemTransfer>> plan =
                TransferItems2.buildAreaPlan(destinations, qualities);

        assertEquals(List.of("shared"), List.copyOf(plan.keySet()));
        assertEquals(List.of("Bone", "Entrails"), plan.get("shared").stream()
                .map(job -> job.itemName)
                .collect(Collectors.toList()));
    }

    private static NavigableMap<Double, String> thresholds(
            double firstQuality, String firstArea,
            double secondQuality, String secondArea) {
        NavigableMap<Double, String> result = new TreeMap<>();
        result.put(firstQuality, firstArea);
        result.put(secondQuality, secondArea);
        return result;
    }

    @Test
    void routeChoosesNearestAreaInsteadOfItemGroupPriority() {
        assertEquals("near-bones", TransferItems2.pickNearestArea(
                List.of("far-meat", "near-bones", "middle-entrails"),
                Map.of("far-meat", 100.0, "near-bones", 5.0, "middle-entrails", 20.0)));
    }

    @Test
    void unsafeLowerBandWaitsOnlyForHigherBandOfSameItem() {
        Map<String, NavigableMap<Double, String>> destinations = new LinkedHashMap<>();
        destinations.put("Bone", thresholds(1.0, "bone-low", 20.0, "bone-high"));
        destinations.put("Entrails", thresholds(1.0, "entrails-low", 30.0, "entrails-high"));

        Map<String, List<Double>> qualities = new LinkedHashMap<>();
        qualities.put("Bone", List.of(5.0, 25.0));
        qualities.put("Entrails", List.of(10.0));

        Map<String, List<TransferItems2.ItemTransfer>> remaining =
                TransferItems2.buildAreaPlan(destinations, qualities);
        Map<String, List<TransferItems2.ItemTransfer>> eligible =
                TransferItems2.eligibleAreaPlan(remaining,
                        (areaId, itemName) -> areaId.equals("bone-low"));

        assertEquals(List.of("bone-high", "entrails-low"), List.copyOf(eligible.keySet()));
    }

    @Test
    void safeQualityBandsCanBeVisitedInNearestAreaOrder() {
        Map<String, NavigableMap<Double, String>> destinations = new LinkedHashMap<>();
        destinations.put("Bone", thresholds(1.0, "bone-low", 20.0, "bone-high"));

        Map<String, List<Double>> qualities = new LinkedHashMap<>();
        qualities.put("Bone", List.of(5.0, 25.0));

        Map<String, List<TransferItems2.ItemTransfer>> remaining =
                TransferItems2.buildAreaPlan(destinations, qualities);
        Map<String, List<TransferItems2.ItemTransfer>> eligible =
                TransferItems2.eligibleAreaPlan(remaining, (areaId, itemName) -> false);

        assertEquals(List.of("bone-low", "bone-high"), List.copyOf(eligible.keySet()));
    }

    @Test
    void barterBandWaitsUntilSafeBandsOfSameItemAreGone() {
        Map<String, NavigableMap<Double, String>> destinations = new LinkedHashMap<>();
        destinations.put("Bone", thresholds(1.0, "bone-low", 20.0, "bone-barter"));

        Map<String, List<Double>> qualities = new LinkedHashMap<>();
        qualities.put("Bone", List.of(5.0, 25.0));

        Map<String, List<TransferItems2.ItemTransfer>> remaining =
                TransferItems2.buildAreaPlan(destinations, qualities);
        Map<String, List<TransferItems2.ItemTransfer>> eligible =
                TransferItems2.eligibleAreaPlan(remaining,
                        (areaId, itemName) -> areaId.equals("bone-barter"));

        assertEquals(List.of("bone-low"), List.copyOf(eligible.keySet()));
    }

    @Test
    void transferBandIsCompleteOnlyWhenNoMatchingQualityRemains() {
        TransferItems2.ItemTransfer lowBand =
                new TransferItems2.ItemTransfer("Bone", 1.0, 20.0, "bone-low");

        assertFalse(TransferItems2.isBandEmpty(lowBand, List.of(5.0, 25.0)));
        assertTrue(TransferItems2.isBandEmpty(lowBand, List.of(25.0)));
    }

    @Test
    void fewerGroupSiblingGoesFirstSoMajorityCanBatch() {
        List<String> ordered = TransferItems2.orderByGroupCount(
                List.of("Soil", "Earthworm"),
                Map.of("Soil", 200, "Earthworm", 1));
        assertEquals(List.of("Earthworm", "Soil"), ordered);
    }

    @Test
    void farMinorityAreaIsPickedBeforeNearMajorityArea() {
        Map<String, List<String>> itemsByArea = new LinkedHashMap<>();
        itemsByArea.put("near-soil", List.of("Soil"));
        itemsByArea.put("far-worms", List.of("Earthworm"));

        Map<String, Double> distances = new HashMap<>();
        distances.put("near-soil", 1.0);
        distances.put("far-worms", 100.0);

        assertEquals("far-worms", TransferItems2.pickNextArea(
                itemsByArea,
                Map.of("Soil", 200, "Earthworm", 1),
                distances));
    }

    @Test
    void withoutGroupConflictNearestAreaWins() {
        Map<String, List<String>> itemsByArea = new LinkedHashMap<>();
        itemsByArea.put("near-flax", List.of("Flax"));
        itemsByArea.put("far-soil", List.of("Soil"));

        Map<String, Double> distances = new HashMap<>();
        distances.put("near-flax", 1.0);
        distances.put("far-soil", 100.0);

        assertEquals("near-flax", TransferItems2.pickNextArea(
                itemsByArea,
                Map.of("Flax", 10, "Soil", 200),
                distances));
    }

    @Test
    void threeSoilTypesGoFromFewestToMost() {
        List<String> ordered = TransferItems2.orderByGroupCount(
                List.of("Soil", "Mulch", "Earthworm"),
                Map.of("Soil", 200, "Mulch", 5, "Earthworm", 1));
        assertEquals(List.of("Earthworm", "Mulch", "Soil"), ordered);
    }
}
