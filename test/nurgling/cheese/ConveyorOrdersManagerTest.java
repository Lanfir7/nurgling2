package nurgling.cheese;

import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.Arrays;
import java.util.Map;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConveyorOrdersManagerTest {
    @TempDir Path temp;

    @Test
    void legacyOrdersAndExplicitPrioritiesRoundTripWithoutChangingProgress() throws Exception {
        Path file = temp.resolve(ConveyorOrdersManager.FILE_NAME);
        ConveyorOrder once = ConveyorOrder.create(3, "Caciotta");
        once.addTrays(4);
        JSONObject old = once.toJson();
        old.remove("priority");
        old.put("credit", 2.75).put("lastRelease", 12_345L).put("extra", 2).put("paused", true);
        old.put("batches", new JSONObject().put("Feta@cellar",
                new JSONArray().put(new JSONObject().put("count", 2).put("time", 5_000L))));
        ConveyorOrder continuous = ConveyorOrder.create(1, "Cabrales");
        continuous.makeContinuous(3, 12);
        JSONObject second = continuous.toJson().put("priority", -2);
        ConveyorOrder tied = ConveyorOrder.create(2, "Halloumi");
        JSONObject third = tied.toJson().put("priority", -2);
        Files.write(file, new JSONObject().put("orders", new JSONArray().put(old).put(second).put(third))
                .toString().getBytes(StandardCharsets.UTF_8));

        ConveyorOrdersManager manager = new ConveyorOrdersManager(file.toString());
        assertEquals(Arrays.asList(1, 2, 3), manager.sorted().stream().map(ConveyorOrder::getId)
                .collect(java.util.stream.Collectors.toList()));
        assertEquals(0, manager.getOrders().get(3).getPriority());
        manager.save();
        ConveyorOrdersManager loaded = new ConveyorOrdersManager(file.toString());
        assertEquals(-2, loaded.getOrders().get(1).getPriority());
        assertEquals(-2, loaded.getOrders().get(2).getPriority());
        JSONObject roundTrip = loaded.getOrders().get(3).toJson();
        assertEquals(4, roundTrip.getInt("count"));
        assertEquals(2.75, roundTrip.getDouble("credit"));
        assertEquals(12_345L, roundTrip.getLong("lastRelease"));
        assertEquals(2, roundTrip.getInt("extra"));
        assertTrue(roundTrip.getBoolean("paused"));
        assertEquals(old.getJSONArray("status").toString(), roundTrip.getJSONArray("status").toString());
        assertEquals(old.getJSONObject("batches").toString(), roundTrip.getJSONObject("batches").toString());
    }

    @Test
    void invalidMovesDoNothingAndValidMovesPersistOnlyPriority() {
        Path file = temp.resolve(ConveyorOrdersManager.FILE_NAME);
        ConveyorOrdersManager manager = new ConveyorOrdersManager(file.toString());
        ConveyorOrder first = manager.getOrCreate("Caciotta");
        ConveyorOrder second = manager.getOrCreate("Cabrales");
        first.addTrays(7);
        manager.save();
        assertFalse(manager.move(999, -1));
        assertFalse(manager.move(first.getId(), -1));
        assertFalse(manager.move(second.getId(), 1));
        assertFalse(manager.move(first.getId(), 0));
        assertEquals(0, first.getPriority());
        assertEquals(0, second.getPriority());
        assertTrue(manager.move(second.getId(), -1));
        manager.save();
        ConveyorOrdersManager loaded = new ConveyorOrdersManager(file.toString());
        assertEquals(second.getId(), loaded.sorted().get(0).getId());
        assertEquals(7, loaded.getOrders().get(first.getId()).getCount());
    }

    @Test
    void explicitModelPriorityEditBeatsLoadedValueOnSave() {
        Path file = temp.resolve(ConveyorOrdersManager.FILE_NAME);
        ConveyorOrdersManager manager = new ConveyorOrdersManager(file.toString());
        ConveyorOrder order = manager.getOrCreate("Caciotta");
        manager.save();
        order.setPriority(-7);
        manager.save();
        assertEquals(-7, new ConveyorOrdersManager(file.toString()).getOrders().get(order.getId()).getPriority());
    }

    @Test
    void staleBotSaveKeepsReorderAndUiReorderKeepsFreshProgress() {
        Path file = temp.resolve(ConveyorOrdersManager.FILE_NAME);
        ConveyorOrdersManager initial = new ConveyorOrdersManager(file.toString());
        ConveyorOrder first = initial.getOrCreate("Caciotta");
        ConveyorOrder second = initial.getOrCreate("Cabrales");
        initial.save();

        ConveyorOrdersManager bot = new ConveyorOrdersManager(file.toString());
        ConveyorOrdersManager ui = new ConveyorOrdersManager(file.toString());
        assertTrue(ui.moveAndSave(second.getId(), -1));
        bot.getOrders().get(first.getId()).addTrays(3);
        bot.save();
        ConveyorOrdersManager afterBot = new ConveyorOrdersManager(file.toString());
        assertEquals(second.getId(), afterBot.sorted().get(0).getId());
        assertEquals(3, afterBot.getOrders().get(first.getId()).getCount());

        ConveyorOrdersManager staleUi = new ConveyorOrdersManager(file.toString());
        bot.getOrders().get(first.getId()).advance("Sheep's Curd", CheeseBranch.Place.start, 1, 42_000L);
        bot.save();
        assertTrue(staleUi.moveAndSave(first.getId(), -1));
        ConveyorOrdersManager afterUi = new ConveyorOrdersManager(file.toString());
        assertEquals(2, afterUi.getOrders().get(first.getId()).startStep().left);
        assertEquals(42_000L, afterUi.getOrders().get(first.getId()).oldestArrival("Feta", CheeseBranch.Place.cellar));
    }

    @Test
    void failedSaveDoesNotMakeAdoptedPriorityLookLikeALocalEdit() {
        Path file = temp.resolve(ConveyorOrdersManager.FILE_NAME);
        ConveyorOrdersManager initial = new ConveyorOrdersManager(file.toString());
        int first = initial.getOrCreate("Caciotta").getId();
        initial.getOrCreate("Cabrales");
        int third = initial.getOrCreate("Halloumi").getId();
        initial.save();

        ConveyorOrdersManager stale = new ConveyorOrdersManager(file.toString()) {
            private boolean failNext = true;

            @Override
            void writeOrders(String content) throws IOException {
                if (failNext) {
                    failNext = false;
                    throw new IOException("simulated write failure");
                }
                super.writeOrders(content);
            }
        };
        ConveyorOrdersManager ui = new ConveyorOrdersManager(file.toString());
        assertTrue(ui.moveAndSave(third, -1));
        assertThrows(RuntimeException.class, stale::save);
        assertEquals(0, stale.getOrders().get(third).getPriority());
        assertTrue(ui.moveAndSave(third, -1));

        stale.save();
        ConveyorOrdersManager reloaded = new ConveyorOrdersManager(file.toString());
        assertEquals(third, reloaded.sorted().get(0).getId());
        assertEquals(first, reloaded.sorted().get(1).getId());
    }

    @Test
    void stageBatchesKeepOldestArrivalAcrossMovementAndSave() {
        ConveyorOrder order = ConveyorOrder.create(1, "Caciotta");
        CheeseBranch.Place start = CheeseBranch.Place.start;
        CheeseBranch.Place cellar = CheeseBranch.Place.cellar;
        CheeseBranch.Place inside = CheeseBranch.Place.inside;

        order.addTrays(2);
        order.advance("Sheep's Curd", start, 2, 1_000L);
        order.addTrays(2);
        order.advance("Sheep's Curd", start, 2, 2_000L);
        assertEquals(1_000L, order.oldestArrival("Feta", cellar));

        order.advance("Feta", cellar, 3, 3_000L);
        assertEquals(1, order.findStep("Feta", cellar).left);
        assertEquals(2_000L, order.oldestArrival("Feta", cellar));
        assertEquals(3_000L, order.oldestArrival("Caciotta", inside));

        ConveyorOrder loaded = new ConveyorOrder(order.toJson());
        assertEquals(2_000L, loaded.oldestArrival("Feta", cellar));
        assertEquals(1, loaded.stageBatches("Feta", cellar).get(0).count);
        assertEquals(3_000L, loaded.oldestArrival("Caciotta", inside));
    }

    @Test
    void olderOrderWithoutBatchesShowsUnknownArrival() {
        ConveyorOrder order = ConveyorOrder.create(1, "Caciotta");
        CheeseBranch.Place cellar = CheeseBranch.Place.cellar;
        order.getStatus().add(new CheeseOrder.StepStatus("Feta", cellar.name(), 3));
        JSONObject oldJson = order.toJson();
        oldJson.remove("batches");

        ConveyorOrder loaded = new ConveyorOrder(oldJson);
        assertEquals(0L, loaded.oldestArrival("Feta", cellar));
        assertEquals(3, loaded.stageBatches("Feta", cellar).get(0).count);
        assertEquals(0L, new ConveyorOrder(loaded.toJson()).oldestArrival("Feta", cellar));
    }

    @Test
    void olderUnstampedTraysLeaveBeforeNewlyStampedTrays() {
        ConveyorOrder order = ConveyorOrder.create(1, "Caciotta");
        CheeseBranch.Place cellar = CheeseBranch.Place.cellar;
        order.getStatus().add(new CheeseOrder.StepStatus("Feta", cellar.name(), 3));

        order.addTrays(2);
        order.advance("Sheep's Curd", CheeseBranch.Place.start, 2, 2_000L);
        order.advance("Feta", cellar, 3, 3_000L);

        assertEquals(2, order.findStep("Feta", cellar).left);
        assertEquals(2_000L, order.oldestArrival("Feta", cellar));
    }

    @Test
    void intermediateCheeseIsNotSlicedWhenBookedForLaterStage() {
        ConveyorOrder jorbonzola = ConveyorOrder.create(1, "Jorbonzola");
        ConveyorOrder midnightBlue = ConveyorOrder.create(2, "Midnight Blue Cheese");
        jorbonzola.makeContinuous(1, 1);
        midnightBlue.makeContinuous(1, 1);

        CheeseBranch.Place mine = CheeseBranch.Place.mine;
        midnightBlue.getStatus().add(new CheeseOrder.StepStatus("Jorbonzola", "mine", 1));
        assertNull(ConveyorOrdersManager.sliceOrder(Arrays.asList(jorbonzola, midnightBlue), "Jorbonzola", mine));
        assertSame(midnightBlue, ConveyorOrdersManager.orderFor(Arrays.asList(jorbonzola, midnightBlue), "Jorbonzola", mine));

        jorbonzola.getStatus().add(new CheeseOrder.StepStatus("Jorbonzola", "mine", 1));
        assertSame(jorbonzola, ConveyorOrdersManager.sliceOrder(Arrays.asList(jorbonzola, midnightBlue), "Jorbonzola", mine));
        jorbonzola.sliced();
        assertEquals(0, jorbonzola.findStep("Jorbonzola", mine).left);
        assertNull(ConveyorOrdersManager.sliceOrder(Arrays.asList(jorbonzola, midnightBlue), "Jorbonzola", mine));
    }

    @Test
    void sharedIntermediateMovementSplitsBookedWorkBeforeContinuousOverflow() {
        ConveyorOrder caciotta = ConveyorOrder.create(1, "Caciotta");
        ConveyorOrder cabrales = ConveyorOrder.create(2, "Cabrales");
        caciotta.makeContinuous(1, 1);
        cabrales.makeContinuous(1, 1);

        CheeseBranch.Place cellar = CheeseBranch.Place.cellar;
        caciotta.getStatus().add(new CheeseOrder.StepStatus("Feta", cellar.name(), 2));
        cabrales.getStatus().add(new CheeseOrder.StepStatus("Feta", cellar.name(), 3));

        Map<CheeseBranch.Place, Integer> destinations = ConveyorOrdersManager.destinationsFor(
                Arrays.asList(caciotta, cabrales), "Feta", cellar, 9);
        assertEquals(6, destinations.get(CheeseBranch.Place.inside));
        assertEquals(3, destinations.get(CheeseBranch.Place.outside));

        assertTrue(ConveyorOrdersManager.advanceMoved(Arrays.asList(caciotta, cabrales), "Feta", cellar,
                CheeseBranch.Place.inside, 6));
        assertEquals(0, caciotta.findStep("Feta", cellar).left);
        assertEquals(6, caciotta.findStep("Caciotta", CheeseBranch.Place.inside).left);
        assertEquals(3, cabrales.findStep("Feta", cellar).left);

        assertTrue(ConveyorOrdersManager.advanceMoved(Arrays.asList(caciotta, cabrales), "Feta", cellar,
                CheeseBranch.Place.outside, 3));
        assertEquals(0, cabrales.findStep("Feta", cellar).left);
        assertEquals(3, cabrales.findStep("Cabrales", CheeseBranch.Place.outside).left);
    }
}
