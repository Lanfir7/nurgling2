package nurgling.actions.bots;

import nurgling.NGameUI;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Allocation;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Plan;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.SlotPlan;
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

        private FetchRequest(String candidateId, String material, int count, GroupedItem group) {
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

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        for(FetchRequest request : requests) {
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
