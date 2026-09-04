package nurgling.actions.bots;

import nurgling.craftatlas.CraftAtlasMaterialPlanner;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Candidate;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Plan;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Selection;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.SlotRequest;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Source;
import nurgling.db.dao.StorageItemDao;
import nurgling.widgets.NStorageItemsWidget.GroupedItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasResourceCollectorTest {
    @Test
    void emitsOnlyStorageAllocationsInPlannerOrder() {
        Candidate inventory = candidate("inv-100", 100, 2, Source.INVENTORY);
        Candidate storage100 = candidate("db-100", 100, 3, Source.STORAGE);
        Candidate storage92 = candidate("db-92", 92, 1, Source.STORAGE);
        Plan plan = CraftAtlasMaterialPlanner.plan(
                List.of(new SlotRequest(0, 6, false, List.of("Linen Cloth"))),
                Map.of(0, List.of(inventory, storage100, storage92)),
                Map.of(0, Selection.all()), 1);
        Map<String, GroupedItem> storage = new LinkedHashMap<>();
        storage.put("db-100", group(100, 3));
        storage.put("db-92", group(92, 1));

        List<CraftAtlasResourceCollector.FetchRequest> requests =
                CraftAtlasResourceCollector.requests(plan, storage);

        assertEquals(List.of("db-100", "db-92"), requests.stream()
                .map(value -> value.candidateId).collect(Collectors.toList()));
        assertEquals(List.of(3, 1), requests.stream()
                .map(value -> value.count).collect(Collectors.toList()));
    }

    @Test
    void rejectsIncompletePlanBeforeBuildingRequests() {
        Plan plan = CraftAtlasMaterialPlanner.plan(
                List.of(new SlotRequest(0, 2, false, List.of("Glue"))),
                Map.of(), Map.of(), 1);

        assertThrows(IllegalArgumentException.class,
                () -> CraftAtlasResourceCollector.requests(plan, Map.of()));
    }

    @Test
    void rejectsMissingWarehouseRow() {
        Candidate storage = new Candidate("db-glue", "Glue", 50, 1, Source.STORAGE, "Chest");
        Plan plan = CraftAtlasMaterialPlanner.plan(
                List.of(new SlotRequest(0, 1, false, List.of("Glue"))),
                Map.of(0, List.of(storage)), Map.of(0, Selection.all()), 1);

        assertThrows(IllegalArgumentException.class,
                () -> CraftAtlasResourceCollector.requests(plan, Map.of()));
    }

    @Test
    void combinesOneWarehouseBatchSharedBySeveralRecipeSlots() {
        Candidate storage = candidate("db-shared", 80, 2, Source.STORAGE);
        Plan plan = CraftAtlasMaterialPlanner.plan(
                List.of(new SlotRequest(0, 1, false, List.of("Linen Cloth")),
                        new SlotRequest(1, 1, false, List.of("Linen Cloth"))),
                Map.of(0, List.of(storage), 1, List.of(storage)),
                Map.of(0, Selection.all(), 1, Selection.all()), 1);

        List<CraftAtlasResourceCollector.FetchRequest> requests =
                CraftAtlasResourceCollector.requests(plan, Map.of("db-shared", group(80, 2)));

        assertEquals(1, requests.size());
        assertEquals(2, requests.get(0).count);
    }

    private static Candidate candidate(String id, double quality, int count, Source source) {
        return new Candidate(id, "Linen Cloth", quality, count, source,
                source == Source.INVENTORY ? "Inventory" : "Chest");
    }

    private static GroupedItem group(double quality, int count) {
        List<StorageItemDao.StorageItemData> items = new ArrayList<>();
        for(int i = 0; i < count; i++)
            items.add(new StorageItemDao.StorageItemData("h-" + quality + "-" + i,
                    "Linen Cloth", quality, "(0,0)", "box"));
        return new GroupedItem("Linen Cloth", quality, count, items, 3, "Chest");
    }
}
