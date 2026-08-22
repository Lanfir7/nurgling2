package nurgling.actions;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferItems2Test {

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
