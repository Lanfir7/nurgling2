package nurgling.actions.bots.cheeseconveyor;

import nurgling.cheese.CheeseBranch;
import nurgling.cheese.ConveyorOrder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheeseConveyorPriorityTest {
    @Test
    void higherPriorityGetsFirstStageSlotsBeforeLowerPriority() throws Exception {
        ConveyorOrder high = order(2, "Caciotta", -1, 3);
        ConveyorOrder low = order(1, "Cabrales", 1, 4);
        Map<CheeseBranch.Place, Integer> capacity = cellarSlots(5);
        List<String> calls = new ArrayList<>();
        CheeseConveyorBot.startByPriority(Arrays.asList(low, high), capacity, (order, allot) -> {
            calls.add(order.getId() + ":" + allot);
            return allot;
        });
        assertEquals(Arrays.asList("2:3", "1:2"), calls);
        assertEquals(0, capacity.get(CheeseBranch.Place.cellar));
    }

    @Test
    void equalAndLegacyDefaultPrioritiesShareSlotsProportionally() throws Exception {
        ConveyorOrder first = order(1, "Caciotta", 0, 2);
        ConveyorOrder second = order(2, "Cabrales", 0, 6);
        Map<CheeseBranch.Place, Integer> capacity = cellarSlots(4);
        List<String> calls = new ArrayList<>();
        CheeseConveyorBot.startByPriority(Arrays.asList(second, first), capacity, (order, allot) -> {
            calls.add(order.getId() + ":" + allot);
            return allot;
        });
        assertEquals(Arrays.asList("1:1", "2:3"), calls);
    }

    @Test
    void failedOrPartialHighOrderLeavesActualSpaceForNextTier() throws Exception {
        ConveyorOrder high = order(1, "Caciotta", -2, 4);
        ConveyorOrder low = order(2, "Cabrales", 2, 4);
        Map<CheeseBranch.Place, Integer> capacity = cellarSlots(5);
        List<String> calls = new ArrayList<>();
        CheeseConveyorBot.startByPriority(Arrays.asList(low, high), capacity, (order, allot) -> {
            calls.add(order.getId() + ":" + allot);
            return order == high ? 1 : allot;
        });
        assertEquals(Arrays.asList("1:4", "2:4"), calls);
        assertEquals(0, capacity.get(CheeseBranch.Place.cellar));
    }

    private static ConveyorOrder order(int id, String cheese, int priority, int trays) {
        ConveyorOrder order = ConveyorOrder.create(id, cheese);
        order.setPriority(priority);
        order.addTrays(trays);
        return order;
    }

    private static Map<CheeseBranch.Place, Integer> cellarSlots(int count) {
        Map<CheeseBranch.Place, Integer> capacity = new EnumMap<>(CheeseBranch.Place.class);
        capacity.put(CheeseBranch.Place.cellar, count);
        return capacity;
    }
}
