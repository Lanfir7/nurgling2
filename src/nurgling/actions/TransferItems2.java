package nurgling.actions;

import haven.WItem;
import haven.error.FileLogger;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.areas.NContext;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.VSpec;

import java.util.*;
import java.util.function.BiPredicate;

public class TransferItems2 implements Action
{
    final NContext cnt;
    HashSet<String> items;

    static boolean matchesQuality(double quality, double minInclusive, Double maxExclusive) {
        return quality >= minInclusive && (maxExclusive == null || quality < maxExclusive);
    }

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
    static class ItemTransfer {
        final String itemName;
        final double quality;
        final Double maxQualityExclusive;
        final String areaId;

        ItemTransfer(String itemName, double quality, String areaId) {
            this(itemName, quality, null, areaId);
        }

        ItemTransfer(String itemName, double quality, Double maxQualityExclusive, String areaId) {
            this.itemName = itemName;
            this.quality = quality;
            this.maxQualityExclusive = maxQualityExclusive;
            this.areaId = areaId;
        }
    }

    static Map<String, List<ItemTransfer>> buildAreaPlan(
            Map<String, ? extends NavigableMap<Double, String>> destinations,
            Map<String, List<Double>> inventoryQualities) {
        Map<String, List<ItemTransfer>> result = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends NavigableMap<Double, String>> itemEntry : destinations.entrySet()) {
            String itemName = itemEntry.getKey();
            NavigableMap<Double, String> areas = itemEntry.getValue();
            List<Double> qualities = inventoryQualities.getOrDefault(itemName, Collections.emptyList());
            for (Map.Entry<Double, String> threshold : areas.entrySet()) {
                double minQuality = threshold.getKey();
                Double maxQuality = areas.higherKey(minQuality);
                boolean hasItems = false;
                for (Double quality : qualities) {
                    double normalized = quality != null ? quality : 1.0;
                    if (matchesQuality(normalized, minQuality, maxQuality)) {
                        hasItems = true;
                        break;
                    }
                }
                if (hasItems) {
                    String areaId = threshold.getValue();
                    result.computeIfAbsent(areaId, key -> new ArrayList<>())
                            .add(new ItemTransfer(itemName, minQuality, maxQuality, areaId));
                }
            }
        }
        return result;
    }

    static boolean allGroupItemsRouteToArea(
            String currentItem, String currentArea,
            Map<String, ? extends NavigableMap<Double, String>> destinations,
            Map<String, List<Double>> inventoryQualities) {
        List<String> currentGroups = transferGroups(currentItem);
        if (currentGroups.isEmpty()) {
            return false;
        }
        boolean found = false;
        for (Map.Entry<String, List<Double>> item : inventoryQualities.entrySet()) {
            if (Collections.disjoint(currentGroups, transferGroups(item.getKey()))) {
                continue;
            }
            NavigableMap<Double, String> areas = destinations.get(item.getKey());
            for (Double quality : item.getValue()) {
                found = true;
                double normalized = quality != null ? quality : 1.0;
                Map.Entry<Double, String> route = areas != null
                        ? areas.floorEntry(normalized) : null;
                if (route == null || !Objects.equals(currentArea, route.getValue())) {
                    return false;
                }
            }
        }
        return found;
    }

    static String pickNearestArea(Collection<String> areaIds, Map<String, Double> distances) {
        String nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (String areaId : areaIds) {
            Double value = distances.get(areaId);
            double distance = value != null ? value : Double.MAX_VALUE;
            if (nearest == null || distance < nearestDistance) {
                nearest = areaId;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    static Map<String, List<ItemTransfer>> eligibleAreaPlan(
            Map<String, List<ItemTransfer>> remaining,
            BiPredicate<String, String> requiresDescendingQuality) {
        Map<String, Double> highestUnsafeByItem = new HashMap<>();
        Set<String> itemsWithSafeTransfer = new HashSet<>();
        for (Map.Entry<String, List<ItemTransfer>> area : remaining.entrySet()) {
            for (ItemTransfer transfer : area.getValue()) {
                if (requiresDescendingQuality.test(area.getKey(), transfer.itemName)) {
                    highestUnsafeByItem.merge(
                            transfer.itemName, transfer.quality, Math::max);
                } else {
                    itemsWithSafeTransfer.add(transfer.itemName);
                }
            }
        }
        Map<String, List<ItemTransfer>> eligible = new LinkedHashMap<>();
        for (Map.Entry<String, List<ItemTransfer>> area : remaining.entrySet()) {
            for (ItemTransfer transfer : area.getValue()) {
                boolean unsafeServerSelection = requiresDescendingQuality.test(
                        area.getKey(), transfer.itemName);
                if (!unsafeServerSelection
                        || (!itemsWithSafeTransfer.contains(transfer.itemName)
                        && Double.compare(transfer.quality,
                                highestUnsafeByItem.get(transfer.itemName)) == 0)) {
                    eligible.computeIfAbsent(area.getKey(), key -> new ArrayList<>()).add(transfer);
                }
            }
        }
        return eligible;
    }

    @FunctionalInterface
    interface RoutingScores {
        Map<String, Double> get(Collection<String> areaIds) throws InterruptedException;
    }

    @FunctionalInterface
    interface AreaProcessor {
        Results process(String areaId, List<ItemTransfer> transfers) throws InterruptedException;
    }

    static Results processPlan(
            Map<String, List<ItemTransfer>> plan,
            BiPredicate<String, String> requiresDescendingQuality,
            RoutingScores routingScores,
            AreaProcessor processor) throws InterruptedException {
        Map<String, List<ItemTransfer>> remaining = new LinkedHashMap<>();
        for (Map.Entry<String, List<ItemTransfer>> entry : plan.entrySet())
            remaining.put(entry.getKey(), new ArrayList<>(entry.getValue()));

        while (!remaining.isEmpty()) {
            Map<String, List<ItemTransfer>> eligible = eligibleAreaPlan(
                    remaining, requiresDescendingQuality);
            String nearestAreaId = pickNearestArea(
                    eligible.keySet(), routingScores.get(eligible.keySet()));
            if (nearestAreaId == null)
                break;
            List<ItemTransfer> areaTransfers = eligible.get(nearestAreaId);
            areaTransfers.sort(Comparator
                    .comparing((ItemTransfer transfer) -> !orderList.contains(transfer.itemName))
                    .thenComparing(transfer -> transfer.itemName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Comparator.comparingDouble(
                            (ItemTransfer transfer) -> transfer.quality).reversed()));
            Results processed = processor.process(nearestAreaId, areaTransfers);
            if (processed == null || !processed.IsSuccess()) {
                FileLogger.log("[TransferItems2] area-failed thread="
                        + Thread.currentThread().getName()
                        + " area=" + nearestAreaId
                        + " transfers=" + areaTransfers.size());
                return processed != null ? processed : Results.FAIL();
            }

            List<ItemTransfer> pendingAtArea = remaining.get(nearestAreaId);
            pendingAtArea.removeAll(areaTransfers);
            if (pendingAtArea.isEmpty())
                remaining.remove(nearestAreaId);
        }
        return remaining.isEmpty() ? Results.SUCCESS() : Results.FAIL();
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
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
        before.sort(String.CASE_INSENSITIVE_ORDER);
        after.sort(String.CASE_INSENSITIVE_ORDER);
        ArrayList<String> resitems = new ArrayList<>();
        resitems.addAll(before);
        resitems.addAll(after);

        Map<String, NavigableMap<Double, String>> destinations = new LinkedHashMap<>();
        Map<String, List<Double>> inventoryQualities = new LinkedHashMap<>();
        for (String itemName : resitems) {
            TreeMap<Double, String> areas = cnt.getOutAreas(itemName);
            if (areas != null && !areas.isEmpty()) {
                destinations.put(itemName, areas);
                inventoryQualities.put(itemName, new ArrayList<>());
            }
        }
        for (WItem item : gui.getInventory().getItems()) {
            String itemName = ((NGItem)item.item).name();
            List<Double> qualities = inventoryQualities.get(itemName);
            if (qualities != null) {
                Float quality = ((NGItem)item.item).quality;
                qualities.add(quality != null ? (double)quality : 1.0);
            }
        }

        return processPlan(
                buildAreaPlan(destinations, inventoryQualities),
                cnt::isBarterOutput,
                areaIds -> cnt.getRoutingScores(areaIds, gui),
                (areaId, areaTransfers) -> processAreaTransfers(areaId, areaTransfers, gui));
    }


    /**
     * Process all item transfers for a specific area.
     */
    private Results processAreaTransfers(String areaId, List<ItemTransfer> itemsForArea, NGameUI gui) throws InterruptedException {
        for (ItemTransfer itemTransfer : itemsForArea) {
            ArrayList<NContext.ObjectStorage> storages = cnt.getOutStorages(itemTransfer.itemName, itemTransfer.quality);
            for (NContext.ObjectStorage output : storages) {
                if (output instanceof NContext.FloorDump) {
                    Results result = new DropItemsOnFloor(cnt.getRCArea(areaId), itemTransfer.itemName,
                            itemTransfer.quality, itemTransfer.maxQualityExclusive).run(gui);
                    if (!result.IsSuccess())
                        return result;
                }
                if (output instanceof NContext.Pile) {
                    boolean categoryBulkSafe = allLiveGroupItemsRouteToArea(
                            itemTransfer.itemName, areaId, gui);
                    Results result = new TransferToPiles(cnt.getRCArea(areaId), itemTransfer.itemName,
                            (int)itemTransfer.quality, itemTransfer.maxQualityExclusive,
                            categoryBulkSafe).run(gui);
                    if (!result.IsSuccess())
                        return result;
                }
                if (output instanceof Container) {
                    TreeMap<Double,String> areas = cnt.getOutAreas(itemTransfer.itemName);
                    TransferToContainer ttc = new TransferToContainer((Container) output, itemTransfer.itemName,
                            itemTransfer.quality, itemTransfer.maxQualityExclusive);
                    ttc.needsSorting = areas != null && areas.size() > 1;
                    Results result = ttc.run(gui);
                    if (!result.IsSuccess())
                        return result;
                }
                if (output instanceof NContext.Barrel) {
                    if (getItemsExactMatch(itemTransfer.itemName, itemTransfer.quality,
                            itemTransfer.maxQualityExclusive).isEmpty())
                        break;
                    Results result = new TransferToBarrel(Finder.findGob(((NContext.Barrel) output).barrel),
                            itemTransfer.itemName, itemTransfer.quality,
                            itemTransfer.maxQualityExclusive).run(gui);
                    if (!result.IsSuccess())
                        return result;
                }
                if (output instanceof NContext.Barter) {
                    Results result = new TransferToBarter((NContext.Barter) output,
                            itemTransfer.itemName, itemTransfer.quality,
                            itemTransfer.maxQualityExclusive).run(gui);
                    if (!result.IsSuccess())
                        return result;
                }
            }
        }
        return Results.SUCCESS();
    }

    private boolean allLiveGroupItemsRouteToArea(
            String currentItem, String currentArea, NGameUI gui) throws InterruptedException {
        Map<String, List<Double>> inventoryQualities = new LinkedHashMap<>();
        for (WItem witem : gui.getInventory().getItems()) {
            NGItem item = (NGItem) witem.item;
            String name = item.name();
            if (!Collections.disjoint(transferGroups(currentItem), transferGroups(name))) {
                inventoryQualities.computeIfAbsent(name, key -> new ArrayList<>())
                        .add(item.quality != null ? (double)item.quality : 1.0);
            }
        }

        Map<String, NavigableMap<Double, String>> destinations = new LinkedHashMap<>();
        for (String name : inventoryQualities.keySet()) {
            TreeMap<Double, String> areas = cnt.getOutAreas(name);
            if (areas != null) {
                destinations.put(name, areas);
            }
        }
        return allGroupItemsRouteToArea(
                currentItem, currentArea, destinations, inventoryQualities);
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
    private static ArrayList<WItem> getItemsExactMatch(
            String exactName, double minQuality, Double maxQualityExclusive) throws InterruptedException {
        ArrayList<WItem> allItems = NUtils.getGameUI().getInventory().getItems(new NAlias(exactName));
        ArrayList<WItem> exactMatches = new ArrayList<>();
        for (WItem witem : allItems) {
            NGItem item = (NGItem)witem.item;
            double quality = item.quality != null ? item.quality : 1.0;
            if (item.name().equals(exactName)
                    && matchesQuality(quality, minQuality, maxQualityExclusive)) {
                exactMatches.add(witem);
            }
        }
        return exactMatches;
    }

    private static ArrayList<WItem> getItemsExactMatch(String exactName, double quality)
            throws InterruptedException {
        return getItemsExactMatch(exactName, quality, null);
    }

}
