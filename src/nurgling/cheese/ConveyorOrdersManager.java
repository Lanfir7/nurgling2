package nurgling.cheese;

import nurgling.NConfig;
import nurgling.tools.NFileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orders of the Cheese Conveyor bot, stored in {@code cheese_conveyor.nurgling.json}. The Cheese
 * Production Bot's {@code cheese_orders.nurgling.json} is never read or written here.
 */
public class ConveyorOrdersManager {
    public static final String FILE_NAME = "cheese_conveyor.nurgling.json";

    private final Map<Integer, ConveyorOrder> orders = new HashMap<>();
    private final String path;

    public ConveyorOrdersManager() {
        this.path = NConfig.getGlobalInstance().getProfileAwarePath(FILE_NAME);
        load();
    }

    public void load() {
        orders.clear();
        String content = NFileUtils.readWithBackupFallback(path);
        if (content == null || content.isEmpty())
            return;
        try {
            JSONArray array = new JSONObject(content).getJSONArray("orders");
            for (int i = 0; i < array.length(); i++) {
                ConveyorOrder order = new ConveyorOrder(array.getJSONObject(i));
                if (CheeseBranch.getChainToProduct(order.getCheeseType()) != null)
                    orders.put(order.getId(), order);
            }
        } catch (org.json.JSONException e) {
            System.err.println("[ConveyorOrdersManager] Failed to parse orders file (corrupt JSON): " + e.getMessage());
        }
    }

    public void save() {
        JSONArray arr = new JSONArray();
        for (ConveyorOrder order : orders.values())
            arr.put(order.toJson());
        JSONObject main = new JSONObject();
        main.put("orders", arr);
        try {
            NFileUtils.writeAtomically(path, main.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<Integer, ConveyorOrder> getOrders() {
        return orders;
    }

    public String getPath() {
        return path;
    }

    public ConveyorOrder byCheese(String cheeseType) {
        for (ConveyorOrder order : orders.values())
            if (order.getCheeseType().equals(cheeseType))
                return order;
        return null;
    }

    /** The order for {@code cheeseType}, created (one-time, empty) when there is none yet. */
    public ConveyorOrder getOrCreate(String cheeseType) {
        ConveyorOrder existing = byCheese(cheeseType);
        if (existing != null)
            return existing;
        int id = orders.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
        ConveyorOrder order = ConveyorOrder.create(id, cheeseType);
        if (order != null)
            orders.put(id, order);
        return order;
    }

    public void delete(int id) {
        orders.remove(id);
    }

    /** See {@link #orderFor(Collection, String, CheeseBranch.Place)}. */
    public ConveyorOrder orderFor(String cheese, CheeseBranch.Place place) {
        return orderFor(orders.values(), cheese, place);
    }

    /**
     * Splits ready trays by their actual next destination. Counted work is claimed first; any
     * excess is routed through the first continuous order so manually moved or surplus trays do
     * not remain in buffers forever.
     */
    public Map<CheeseBranch.Place, Integer> destinationsFor(String cheese, CheeseBranch.Place place, int trays) {
        return destinationsFor(orders.values(), cheese, place, trays);
    }

    static Map<CheeseBranch.Place, Integer> destinationsFor(Collection<ConveyorOrder> orders, String cheese,
                                                              CheeseBranch.Place place, int trays) {
        Map<CheeseBranch.Place, Integer> destinations = new LinkedHashMap<>();
        int remaining = Math.max(0, trays);
        for (ConveyorOrder order : movementOrders(orders, cheese, place)) {
            CheeseOrder.StepStatus step = order.findStep(cheese, place);
            int claimed = Math.min(remaining, Math.max(0, step.left));
            if (claimed > 0) {
                addDestination(destinations, order.nextStep(cheese, place).place, claimed);
                remaining -= claimed;
            }
            if (remaining == 0)
                return destinations;
        }
        if (remaining > 0) {
            ConveyorOrder fallback = continuousFallback(orders, cheese, place, null);
            if (fallback != null)
                addDestination(destinations, fallback.nextStep(cheese, place).place, remaining);
        }
        return destinations;
    }

    /** Advance only orders whose trays were physically moved to {@code destination}. */
    public boolean advanceMoved(String cheese, CheeseBranch.Place place, CheeseBranch.Place destination, int trays) {
        return advanceMoved(orders.values(), cheese, place, destination, trays);
    }

    static boolean advanceMoved(Collection<ConveyorOrder> orders, String cheese, CheeseBranch.Place place,
                                CheeseBranch.Place destination, int trays) {
        int remaining = Math.max(0, trays);
        boolean advanced = false;
        for (ConveyorOrder order : movementOrders(orders, cheese, place)) {
            CheeseOrder.StepStatus step = order.findStep(cheese, place);
            if (order.nextStep(cheese, place).place != destination)
                continue;
            int claimed = Math.min(remaining, Math.max(0, step.left));
            if (claimed > 0) {
                order.advance(cheese, place, claimed);
                remaining -= claimed;
                advanced = true;
            }
            if (remaining == 0)
                return true;
        }
        if (remaining > 0) {
            ConveyorOrder fallback = continuousFallback(orders, cheese, place, destination);
            if (fallback != null) {
                fallback.advance(cheese, place, remaining);
                advanced = true;
            }
        }
        return advanced;
    }

    private static List<ConveyorOrder> movementOrders(Collection<ConveyorOrder> orders, String cheese,
                                                        CheeseBranch.Place place) {
        List<ConveyorOrder> candidates = new ArrayList<>();
        for (ConveyorOrder order : orders) {
            CheeseOrder.StepStatus step = order.findStep(cheese, place);
            if (step != null && step.left > 0 && order.nextStep(cheese, place) != null)
                candidates.add(order);
        }
        candidates.sort(Comparator.comparingInt(ConveyorOrder::getId));
        return candidates;
    }

    private static ConveyorOrder continuousFallback(Collection<ConveyorOrder> orders, String cheese,
                                                     CheeseBranch.Place place, CheeseBranch.Place destination) {
        List<ConveyorOrder> candidates = new ArrayList<>();
        for (ConveyorOrder order : orders) {
            CheeseBranch.Cheese next = order.nextStep(cheese, place);
            if (order.isContinuous() && next != null && (destination == null || next.place == destination))
                candidates.add(order);
        }
        candidates.sort(Comparator.comparingInt(ConveyorOrder::getId));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static void addDestination(Map<CheeseBranch.Place, Integer> destinations,
                                       CheeseBranch.Place destination, int trays) {
        destinations.put(destination, destinations.getOrDefault(destination, 0) + trays);
    }

    /**
     * Which order a tray of {@code cheese} that finished aging in {@code place} belongs to. Only
     * orders whose recipe carries it further can claim it: first one whose counters still expect
     * trays there, then any continuous order that would move it on (its counters may have drifted
     * when trays were moved by hand).
     */
    static ConveyorOrder orderFor(Collection<ConveyorOrder> orders, String cheese, CheeseBranch.Place place) {
        for (ConveyorOrder order : orders) {
            CheeseOrder.StepStatus step = order.findStep(cheese, place);
            if (step != null && step.left > 0 && order.nextStep(cheese, place) != null)
                return order;
        }
        for (ConveyorOrder order : orders)
            if (order.isContinuous() && order.nextStep(cheese, place) != null)
                return order;
        return null;
    }

    /** The order that wants a finished tray of {@code cheese} in {@code place} sliced, or null. */
    public ConveyorOrder sliceOrder(String cheese, CheeseBranch.Place place) {
        return sliceOrder(orders.values(), cheese, place);
    }

    /**
     * Which order slices a finished tray of {@code cheese} found in {@code place}.
     * <p>
     * Normally the order whose counters still expect trays there. When no other order's recipe
     * would carry this cheese on to a later stage, a continuous order slices it even if its
     * counters have drifted to zero, so finished cheese never sits on a rack forever. While
     * another order does carry it on (Jorbonzola is a stage of Midnight Blue), only the counted
     * trays are sliced and the rest travel on.
     */
    static ConveyorOrder sliceOrder(Collection<ConveyorOrder> orders, String cheese, CheeseBranch.Place place) {
        ConveyorOrder order = null;
        for (ConveyorOrder candidate : orders)
            if (candidate.getCheeseType().equals(cheese))
                order = candidate;
        if (order == null)
            return null;
        if (order.wantsSlice(place))
            return order;
        if (!order.isContinuous() || !order.endsIn(place))
            return null;
        for (ConveyorOrder other : orders)
            if (other != order && other.nextStep(cheese, place) != null)
                return null;
        return order;
    }
}
