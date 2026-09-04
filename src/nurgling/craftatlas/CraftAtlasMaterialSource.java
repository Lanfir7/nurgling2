package nurgling.craftatlas;

import haven.Loading;
import haven.WItem;
import haven.Widget;
import nurgling.NGameUI;
import nurgling.NGItem;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.i18n.L10n;
import nurgling.tools.CraftIngredientStock;
import nurgling.tools.CraftSlotQuality;
import nurgling.tools.VSpec;
import nurgling.widgets.NStorageItemsWidget.GroupedItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static nurgling.craftatlas.CraftAtlasMaterialPlanner.Candidate;
import static nurgling.craftatlas.CraftAtlasMaterialPlanner.SlotRequest;
import static nurgling.craftatlas.CraftAtlasMaterialPlanner.Source;

/** Adapts live inventory and the warehouse database to the pure Atlas planner. */
public final class CraftAtlasMaterialSource {
    public static final class InventorySample {
        public final String name;
        public final double quality;
        public final int count;

        public InventorySample(String name, double quality, int count) {
            this.name = name;
            this.quality = quality;
            this.count = count;
        }
    }

    public static final class MergedRows {
        public final List<Candidate> candidates;
        public final Map<String, GroupedItem> storageByCandidateId;

        private MergedRows(List<Candidate> candidates, Map<String, GroupedItem> storageByCandidateId) {
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
            this.storageByCandidateId = Collections.unmodifiableMap(new LinkedHashMap<>(storageByCandidateId));
        }
    }

    public static final class Snapshot {
        public final List<SlotRequest> slots;
        public final Map<Integer, List<Candidate>> candidatesBySlot;
        public final Map<String, GroupedItem> storageByCandidateId;
        public final boolean collectible;

        private Snapshot(List<SlotRequest> slots, Map<Integer, List<Candidate>> candidatesBySlot,
                         Map<String, GroupedItem> storageByCandidateId, boolean collectible) {
            this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
            Map<Integer, List<Candidate>> candidateCopy = new LinkedHashMap<>();
            for(Map.Entry<Integer, List<Candidate>> entry : candidatesBySlot.entrySet())
                candidateCopy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            this.candidatesBySlot = Collections.unmodifiableMap(candidateCopy);
            this.storageByCandidateId = Collections.unmodifiableMap(new LinkedHashMap<>(storageByCandidateId));
            this.collectible = collectible;
        }
    }

    public Snapshot load(CraftAtlasEntry entry) {
        return load(entry, inventorySamples());
    }

    public Snapshot load(CraftAtlasEntry entry, List<InventorySample> inventory) {
        if(entry == null) return new Snapshot(Collections.emptyList(), Collections.emptyMap(),
                Collections.emptyMap(), false);
        if(inventory == null) inventory = Collections.emptyList();
        List<SlotRequest> slots = new ArrayList<>();
        Map<Integer, List<Candidate>> candidates = new LinkedHashMap<>();
        Map<String, GroupedItem> storage = new LinkedHashMap<>();
        for(int i = 0; i < entry.inputs.size(); i++) {
            CraftAtlasEntry.InputSlot input = entry.inputs.get(i);
            List<String> names = allowedNames(input);
            slots.add(new SlotRequest(i, input.quantity, input.optional, names));
            Set<String> wanted = new LinkedHashSet<>(names);
            List<InventorySample> matchingInventory = new ArrayList<>();
            for(InventorySample sample : inventory)
                if(wanted.contains(sample.name)) matchingInventory.add(sample);
            MergedRows rows = merge(i, matchingInventory, CraftIngredientStock.search(names));
            candidates.put(i, rows.candidates);
            storage.putAll(rows.storageByCandidateId);
        }
        return new Snapshot(slots, candidates, storage, entry.inputsObserved && !slots.isEmpty());
    }

    public static Snapshot emptySnapshot(CraftAtlasEntry entry) {
        if(entry == null) return new Snapshot(Collections.emptyList(), Collections.emptyMap(),
                Collections.emptyMap(), false);
        List<SlotRequest> slots = new ArrayList<>();
        Map<Integer, List<Candidate>> candidates = new LinkedHashMap<>();
        for(int i = 0; i < entry.inputs.size(); i++) {
            CraftAtlasEntry.InputSlot input = entry.inputs.get(i);
            slots.add(new SlotRequest(i, input.quantity, input.optional, allowedNames(input)));
            candidates.put(i, Collections.emptyList());
        }
        return new Snapshot(slots, candidates, Collections.emptyMap(), entry.inputsObserved && !slots.isEmpty());
    }

    public static List<String> allowedNames(CraftAtlasEntry.InputSlot slot) {
        if(slot == null) return Collections.emptyList();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for(CraftAtlasEntry.IngredientOption option : slot.options) {
            if(option == null || option.name == null) continue;
            boolean category = VSpec.categories.containsKey(option.name);
            names.addAll(CraftIngredientStock.namesFor(option.name, category, null));
        }
        return Collections.unmodifiableList(new ArrayList<>(names));
    }

    public static MergedRows merge(int slotIndex, List<InventorySample> inventory,
                                   List<GroupedItem> warehouse) {
        List<Candidate> candidates = new ArrayList<>();
        Map<String, GroupedItem> storage = new LinkedHashMap<>();
        if(inventory != null) for(InventorySample sample : groupInventory(inventory)) {
            if(sample.name == null || sample.name.isEmpty() || sample.count < 1
                    || !Double.isFinite(sample.quality) || sample.quality <= 0) continue;
            candidates.add(new Candidate(candidateId(Source.INVENTORY, sample.name, sample.quality),
                    sample.name, sample.quality, sample.count, Source.INVENTORY,
                    L10n.get("craft_atlas.inventory")));
        }
        if(warehouse != null) for(GroupedItem row : warehouse) {
            if(row == null || row.name == null || row.name.isEmpty() || row.count < 1
                    || !Double.isFinite(row.quality) || row.quality <= 0) continue;
            String id = candidateId(Source.STORAGE, row.name, row.quality);
            candidates.add(new Candidate(id, row.name, row.quality, row.count, Source.STORAGE,
                    row.storageName));
            storage.put(id, row);
        }
        return new MergedRows(CraftAtlasMaterialPlanner.sortedCandidates(candidates), storage);
    }

    private static List<InventorySample> groupInventory(List<InventorySample> samples) {
        Map<String, InventorySample> grouped = new LinkedHashMap<>();
        for(InventorySample sample : samples) {
            if(sample == null || sample.name == null) continue;
            String key = sample.name + "|" + CraftIngredientStock.qualityKey(sample.quality);
            InventorySample current = grouped.get(key);
            grouped.put(key, new InventorySample(sample.name, sample.quality,
                    Math.max(0, sample.count) + (current == null ? 0 : current.count)));
        }
        return new ArrayList<>(grouped.values());
    }

    private static String candidateId(Source source, String name, double quality) {
        return source.name().toLowerCase(Locale.ROOT) + ":"
                + CraftAtlasSearch.normalize(name) + ":" + CraftIngredientStock.qualityKey(quality);
    }

    public static List<InventorySample> inventorySamples() {
        NGameUI gui = NUtils.getGameUI();
        if(gui == null) return Collections.emptyList();
        NInventory inventory = gui.getInventory();
        if(inventory == null) return Collections.emptyList();
        List<InventorySample> samples = new ArrayList<>();
        try {
            for(WItem item : inventory.getTopLevelItems()) collect(item, samples);
        } catch(Loading ignored) {
        } catch(Exception ignored) {
        }
        return samples;
    }

    private static void collect(WItem widget, List<InventorySample> samples) {
        if(widget == null || widget.item == null) return;
        if(widget.item.contents instanceof haven.res.ui.stackinv.ItemStack) {
            haven.res.ui.stackinv.ItemStack stack = (haven.res.ui.stackinv.ItemStack)widget.item.contents;
            if(stack.wmap != null) for(WItem child : stack.wmap.values()) collect(child, samples);
            return;
        }
        if(!(widget.item instanceof NGItem)) return;
        NGItem item = (NGItem)widget.item;
        Double quality = quality(item);
        String name = item.name();
        if(name != null && quality != null) samples.add(new InventorySample(name, quality, 1));
    }

    private static Double quality(NGItem item) {
        Double direct = CraftSlotQuality.qualityOf(item.quality);
        if(direct != null) return direct;
        try {
            haven.res.ui.tt.stackn.Stack stack = item.getInfo(haven.res.ui.tt.stackn.Stack.class);
            if(stack != null && stack.quality > 0) return Double.valueOf(stack.quality);
        } catch(Exception ignored) {
        }
        if(item.contents != null) {
            List<Double> nested = new ArrayList<>();
            for(Widget child : item.contents.children())
                if(child instanceof NGItem) {
                    Double value = CraftSlotQuality.qualityOf(((NGItem)child).quality);
                    if(value != null) nested.add(value);
                }
            return CraftSlotQuality.average(nested);
        }
        return null;
    }
}
