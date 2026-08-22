package nurgling.actions;

import haven.WItem;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.areas.NContext;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.VSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.*;

public class TransferItems2 implements Action
{
    final NContext cnt;
    HashSet<String> items;

    static HashSet<String> orderList = new HashSet<>();
    static {
        orderList.add("Moose Antlers");
        orderList.add("Flipper Bones");
        orderList.add("Red Deer Antlers");
        orderList.add("Wolf's Claws");
        orderList.add("Bear Tooth");
        orderList.add("Lynx Claws");
        orderList.add("Boar Tusk");
        orderList.add("Billygoat Horn");
        orderList.add("Bog Turtle Shell");
        orderList.add("Boreworm Beak");
        orderList.add("Cachalot Tooth");
        orderList.add("Roe Deer Antlers");
        orderList.add("Wildgoat Horn");
        orderList.add("Mole's Pawbone");
        orderList.add("Orca Tooth");
        orderList.add("Adder Skeleton");
        orderList.add("Ant Chitin");
        orderList.add("Bee Chitin");
        orderList.add("Mammoth Tusk");
        orderList.add("Cave Louse Chitin");
        orderList.add("Crabshell");
        orderList.add("Trollbone");
        orderList.add("Walrus Tusk");
        orderList.add("Troll Tusks");
        orderList.add("Whale Bone Material");
        orderList.add("Wishbone");
    }

    public TransferItems2(NContext context, HashSet<String> items)
    {
        this.cnt = context;
        this.items = items;
    }

    /**
     * Helper class to store item transfer information
     */
    private static class ItemTransfer {
        String itemName;
        double quality;
        String areaId;

        ItemTransfer(String itemName, double quality, String areaId) {
            this.itemName = itemName;
            this.quality = quality;
            this.areaId = areaId;
        }
    }

    /**
     * Helper class to group transfers by quality threshold for proper ordering
     */
    private static class ThresholdGroup {
        double threshold;
        Map<String, List<ItemTransfer>> itemsByArea = new LinkedHashMap<>();

        ThresholdGroup(double threshold) {
            this.threshold = threshold;
        }
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        // Step 1: Sort items into priority/non-priority (preserve existing orderList behavior)
        ArrayList<String> before = new ArrayList<>();
        ArrayList<String> after = new ArrayList<>();

        for (String item : items)
        {
            if(orderList.contains(item))
            {
                before.add(item);
            }
            else
            {
                after.add(item);
            }
        }
        ArrayList<String> resitems = new ArrayList<>();
        resitems.addAll(before);
        resitems.addAll(after);

        // Step 2: Group items by quality threshold first, then by area within each threshold
        // This ensures higher quality thresholds are processed first (preventing lower threshold
        // areas from grabbing high quality items)
        TreeMap<Double, ThresholdGroup> thresholdGroups = new TreeMap<>(Collections.reverseOrder());

        for(String item : resitems) {
            TreeMap<Double,String> areas = cnt.getOutAreas(item);
            if(areas != null) {
                for (Double quality : areas.descendingKeySet()) {
                    if (!getItemsExactMatch(item, quality).isEmpty()) {
                        String areaId = areas.get(quality);
                        ThresholdGroup group = thresholdGroups.computeIfAbsent(quality, ThresholdGroup::new);
                        group.itemsByArea.computeIfAbsent(areaId, k -> new ArrayList<>())
                            .add(new ItemTransfer(item, quality, areaId));
                    }
                }
            }
        }

        // Step 3: Process each threshold group in order (highest first)
        for (ThresholdGroup group : thresholdGroups.values()) {

            if (group.threshold > 1) {
                // Items with thresholds: process in arbitrary order (no optimization needed)
                for (String areaId : group.itemsByArea.keySet()) {
                    processAreaTransfers(areaId, group.itemsByArea.get(areaId), gui);
                }
            } else {
                // Items without thresholds (threshold <= 1): dump fewer same-group
                // siblings first so the majority can batch, then greedy nearest.
                Map<String, List<ItemTransfer>> remaining = new HashMap<>(group.itemsByArea);
                Map<String, Integer> counts = inventoryCounts(remaining);
                while (!remaining.isEmpty()) {
                    Map<String, List<String>> namesByArea = namesByArea(remaining);
                    Map<String, Double> distances = new HashMap<>();
                    for (String areaId : remaining.keySet()) {
                        distances.put(areaId, cnt.getDistanceToAreaById(areaId, gui));
                    }
                    String nearestAreaId = pickNextArea(namesByArea, counts, distances);
                    if (nearestAreaId == null) break;
                    List<ItemTransfer> itemsForArea = remaining.get(nearestAreaId);
                    ArrayList<String> allNames = new ArrayList<>();
                    for (List<String> names : namesByArea.values()) {
                        allNames.addAll(names);
                    }
                    itemsForArea.sort(Comparator.comparingInt(
                            (ItemTransfer t) -> transferPriority(t.itemName, allNames, counts)));
                    processAreaTransfers(nearestAreaId, itemsForArea, gui);
                    remaining.remove(nearestAreaId);
                }
            }
        }

        return Results.SUCCESS();
    }


    /**
     * Process all item transfers for a specific area.
     */
    private void processAreaTransfers(String areaId, List<ItemTransfer> itemsForArea, NGameUI gui) throws InterruptedException {
        for (ItemTransfer itemTransfer : itemsForArea) {
            ArrayList<NContext.ObjectStorage> storages = cnt.getOutStorages(itemTransfer.itemName, itemTransfer.quality);
            for (NContext.ObjectStorage output : storages) {
                if (output instanceof NContext.FloorDump) {
                    new DropItemsOnFloor(cnt.getRCArea(areaId), itemTransfer.itemName).run(gui);
                }
                if (output instanceof NContext.Pile) {
                    new TransferToPiles(cnt.getRCArea(areaId), itemTransfer.itemName,
                        (int)itemTransfer.quality).run(gui);
                }
                if (output instanceof Container) {
                    TreeMap<Double,String> areas = cnt.getOutAreas(itemTransfer.itemName);
                    TransferToContainer ttc = new TransferToContainer((Container) output, itemTransfer.itemName,
                        (int)itemTransfer.quality);
                    ttc.needsSorting = areas != null && areas.size() > 1;
                    ttc.run(gui);
                }
                if (output instanceof NContext.Barrel) {
                    new TransferToBarrel(Finder.findGob(((NContext.Barrel) output).barrel),
                        itemTransfer.itemName).run(gui);
                }
                if (output instanceof NContext.Barter) {
                    new TransferToBarter((NContext.Barter) output,
                        new NAlias(itemTransfer.itemName), (int) itemTransfer.quality).run(gui);
                }
            }
        }
    }

    static List<String> orderByGroupCount(Collection<String> names, Map<String, Integer> counts) {
        ArrayList<String> ordered = new ArrayList<String>(names);
        ordered.sort(Comparator
                .comparingInt((String n) -> transferPriority(n, names, counts))
                .thenComparingInt(n -> indexOf(names, n)));
        return ordered;
    }

    static String pickNextArea(
            Map<String, List<String>> itemsByArea,
            Map<String, Integer> counts,
            Map<String, Double> distances) {
        ArrayList<String> allNames = new ArrayList<String>();
        for (List<String> names : itemsByArea.values()) {
            allNames.addAll(names);
        }
        int minPri = Integer.MAX_VALUE;
        for (String name : allNames) {
            minPri = Math.min(minPri, transferPriority(name, allNames, counts));
        }
        String nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Map.Entry<String, List<String>> e : itemsByArea.entrySet()) {
            boolean candidate = false;
            for (String name : e.getValue()) {
                if (transferPriority(name, allNames, counts) == minPri) {
                    candidate = true;
                    break;
                }
            }
            if (!candidate) {
                continue;
            }
            Double dist = distances.get(e.getKey());
            double d = dist != null ? dist : Double.MAX_VALUE;
            if (d < minDist) {
                minDist = d;
                nearest = e.getKey();
            }
        }
        if (nearest == null && !itemsByArea.isEmpty()) {
            return itemsByArea.keySet().iterator().next();
        }
        return nearest;
    }

    static int transferPriority(String name, Collection<String> remaining, Map<String, Integer> counts) {
        if (!hasGroupSibling(name, remaining)) {
            return Integer.MAX_VALUE;
        }
        Integer c = counts.get(name);
        return c != null ? c : 0;
    }

    static boolean hasGroupSibling(String name, Collection<String> remaining) {
        List<String> groups = transferGroups(name);
        if (groups.isEmpty()) {
            return false;
        }
        for (String other : remaining) {
            if (other.equals(name)) {
                continue;
            }
            for (String g : transferGroups(other)) {
                if (groups.contains(g)) {
                    return true;
                }
            }
        }
        return false;
    }

    static List<String> transferGroups(String name) {
        ArrayList<String> categories = new ArrayList<String>(VSpec.getCategory(name));
        if (categories.contains("Hide Fresh")) {
            categories.add("Prepared Animal Hide");
        } else if (categories.contains("Prepared Animal Hide")) {
            categories.add("Hide Fresh");
        }
        return categories;
    }

    private static int indexOf(Collection<String> names, String n) {
        int i = 0;
        for (String x : names) {
            if (x.equals(n)) {
                return i;
            }
            i++;
        }
        return i;
    }

    private Map<String, Integer> inventoryCounts(Map<String, List<ItemTransfer>> remaining)
            throws InterruptedException {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (List<ItemTransfer> list : remaining.values()) {
            for (ItemTransfer t : list) {
                if (!counts.containsKey(t.itemName)) {
                    counts.put(t.itemName, getItemsExactMatch(t.itemName, t.quality).size());
                }
            }
        }
        return counts;
    }

    private static Map<String, List<String>> namesByArea(Map<String, List<ItemTransfer>> remaining) {
        Map<String, List<String>> namesByArea = new HashMap<String, List<String>>();
        for (Map.Entry<String, List<ItemTransfer>> e : remaining.entrySet()) {
            ArrayList<String> names = new ArrayList<String>();
            for (ItemTransfer t : e.getValue()) {
                names.add(t.itemName);
            }
            namesByArea.put(e.getKey(), names);
        }
        return namesByArea;
    }

    /**
     * Gets items from inventory with exact name match only.
     * This prevents substring matching issues where "Straw Hat" would match "Straw" area.
     */
    private static ArrayList<WItem> getItemsExactMatch(String exactName, double quality) throws InterruptedException {
        ArrayList<WItem> allItems = NUtils.getGameUI().getInventory().getItems(new NAlias(exactName), quality);
        ArrayList<WItem> exactMatches = new ArrayList<>();
        for (WItem witem : allItems) {
            if (((NGItem) witem.item).name().equals(exactName)) {
                exactMatches.add(witem);
            }
        }
        return exactMatches;
    }

}
