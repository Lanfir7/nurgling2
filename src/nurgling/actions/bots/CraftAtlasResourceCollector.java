package nurgling.actions.bots;

import nurgling.NGameUI;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Allocation;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Plan;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.SlotPlan;
import nurgling.db.dao.StorageItemDao;
import nurgling.i18n.L10n;
import nurgling.widgets.NStorageItemsWidget.GroupedItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static nurgling.craftatlas.CraftAtlasMaterialPlanner.Source;

/** Fetches the warehouse portion of one already-validated Atlas material plan. */
public final class CraftAtlasResourceCollector implements Action {
    public static final class FetchRequest {
        public final String candidateId;
        public final String material;
        public final int count;
        public final GroupedItem group;

        FetchRequest(String candidateId, String material, int count, GroupedItem group) {
            this.candidateId = candidateId;
            this.material = material;
            this.count = count;
            this.group = group;
        }
    }

    private final List<FetchRequest> requests;

    public CraftAtlasResourceCollector(Plan plan, Map<String, GroupedItem> storageRows) {
        this.requests = requests(plan, storageRows);
    }

    public static List<FetchRequest> requests(Plan plan, Map<String, GroupedItem> storageRows) {
        if(plan == null || !plan.complete)
            throw new IllegalArgumentException("material plan must be complete");
        Map<String, GroupedItem> rows = storageRows == null ? Collections.emptyMap() : storageRows;
        Map<String, FetchRequest> result = new LinkedHashMap<>();
        for(SlotPlan slot : plan.slots) for(Allocation allocation : slot.allocations) {
            if(allocation.source != Source.STORAGE) continue;
            GroupedItem group = rows.get(allocation.candidateId);
            if(group == null)
                throw new IllegalArgumentException("missing storage row: " + allocation.candidateId);
            FetchRequest existing = result.get(allocation.candidateId);
            result.put(allocation.candidateId, new FetchRequest(allocation.candidateId, allocation.material,
                    allocation.count + (existing == null ? 0 : existing.count), group));
        }
        return Collections.unmodifiableList(new ArrayList<>(result.values()));
    }

    static List<FetchRequest> combineMaterialRequests(List<FetchRequest> requests) {
        Map<String, CombinedRequest> combined = new LinkedHashMap<>();
        if(requests != null) for(FetchRequest request : requests) {
            if(request == null) continue;
            combined.computeIfAbsent(request.material,
                    key -> new CombinedRequest(request.candidateId, request.material)).add(request);
        }
        List<FetchRequest> result = new ArrayList<>();
        for(CombinedRequest value : combined.values()) result.add(value.build());
        return Collections.unmodifiableList(result);
    }

    private static final class CombinedRequest {
        final String candidateId;
        final String material;
        final List<StorageItemDao.StorageItemData> items = new ArrayList<>();
        final java.util.LinkedHashSet<String> locations = new java.util.LinkedHashSet<>();
        int count;
        int distance = -1;

        CombinedRequest(String candidateId, String material) {
            this.candidateId = candidateId;
            this.material = material;
        }

        void add(FetchRequest request) {
            count += request.count;
            int available = Math.min(request.count, request.group.items.size());
            items.addAll(request.group.items.subList(0, available));
            if(request.group.distanceTiles >= 0 && (distance < 0 || request.group.distanceTiles < distance))
                distance = request.group.distanceTiles;
            if(request.group.storageName != null && !request.group.storageName.isEmpty())
                locations.add(request.group.storageName);
        }

        FetchRequest build() {
            GroupedItem group = new GroupedItem(material, -1, count, items, distance,
                    String.join(", ", locations));
            return new FetchRequest(candidateId, material, count, group);
        }
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        for(FetchRequest request : combineMaterialRequests(requests)) {
            FetchStorageItemBot fetch = new FetchStorageItemBot(request.group, request.count,
                    request.group.items);
            Results result = fetch.run(gui);
            if(!result.IsSuccess() || fetch.actualCollected() < request.count) {
                int missing = Math.max(0, request.count - fetch.actualCollected());
                gui.error(L10n.get("craft_atlas.collect_shortage")
                        .replace("{0}", request.material)
                        .replace("{1}", Integer.toString(missing)));
                return Results.FAIL();
            }
        }
        return Results.SUCCESS();
    }
}
