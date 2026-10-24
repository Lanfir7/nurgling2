package nurgling.cheese;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConveyorOrderCreditWindowTest {
    private static final long HOUR = 3_600_000L;
    private static final long START = 10 * HOUR;
    @TempDir Path temp;

    private static ConveyorOrder continuous(double trays, double hours) {
        ConveyorOrder order = ConveyorOrder.create(1, "Caciotta");
        order.makeContinuous(trays, hours);
        return order;
    }

    @Test
    void twelveHourPeriodStartsWithOnePeriodAndNeverBanksMoreAfterGap() {
        ConveyorOrder order = continuous(30, 12);
        assertEquals(30, order.release(START));
        assertEquals(30, order.startStep().left);
        order.advance("Sheep's Curd", CheeseBranch.Place.start, 30, START);
        assertEquals(30, order.release(START + 72 * HOUR));
        assertEquals(30, order.startStep().left);
        assertEquals(0, order.release(START + 72 * HOUR));
    }

    @Test
    void longPeriodStillCapsAtOneDayAndFractionalCreditCarriesForward() {
        ConveyorOrder longPeriod = continuous(60, 48);
        assertEquals(30, longPeriod.release(START));
        longPeriod.advance("Sheep's Curd", CheeseBranch.Place.start, 30, START);
        assertEquals(30, longPeriod.release(START + 96 * HOUR));

        ConveyorOrder fractional = continuous(3, 2);
        assertEquals(3, fractional.release(START));
        fractional.advance("Sheep's Curd", CheeseBranch.Place.start, 3, START);
        assertEquals(0, fractional.release(START + HOUR / 2));
        assertEquals(0.75, fractional.toJson().getDouble("credit"), 1e-9);
        assertEquals(1, fractional.release(START + HOUR));
        assertEquals(0.5, fractional.toJson().getDouble("credit"), 1e-9);
    }

    @Test
    void legacyCreditAndPendingTraysSurviveLoadAndClampOnlyOnActiveRelease() throws Exception {
        Path file = temp.resolve(ConveyorOrdersManager.FILE_NAME);
        ConveyorOrder old = continuous(30, 12);
        old.addTrays(40);
        old.advance("Sheep's Curd", CheeseBranch.Place.start, 5, START - 1);
        JSONObject saved = old.toJson().put("extra", 0).put("credit", 90.5)
                .put("lastRelease", START).put("priority", -4);
        Files.writeString(file, new JSONObject().put("orders", new JSONArray().put(saved)).toString());
        ConveyorOrdersManager manager = new ConveyorOrdersManager(file.toString());
        manager.save();
        ConveyorOrder loaded = new ConveyorOrdersManager(file.toString()).getOrders().get(old.getId());
        assertEquals(90.5, loaded.toJson().getDouble("credit"), 1e-9);
        assertEquals(35, loaded.startStep().left);
        assertEquals(-4, loaded.getPriority());
        assertEquals(START, loaded.toJson().getLong("lastRelease"));
        assertEquals(5, loaded.inProgress());
        assertEquals(START - 1, loaded.oldestArrival("Feta", CheeseBranch.Place.cellar));

        assertEquals(0, loaded.release(START));
        assertEquals(35, loaded.startStep().left);
        assertEquals(30, loaded.toJson().getDouble("credit"), 1e-9);
        assertEquals(-4, loaded.getPriority());
        assertEquals(START - 1, loaded.oldestArrival("Feta", CheeseBranch.Place.cellar));
    }

    @Test
    void manualExtrasDoNotConsumeRateBacklogAndPauseKeepsStoredCredit() {
        ConveyorOrder manual = continuous(30, 12);
        manual.addTrays(8);
        assertEquals(30, manual.release(START));
        assertEquals(38, manual.startStep().left);
        assertEquals(8, manual.extraLeft());

        ConveyorOrder paused = new ConveyorOrder(continuous(30, 12).toJson()
                .put("credit", 7.5).put("lastRelease", START).put("paused", true));
        assertEquals(0, paused.release(START + 30 * HOUR));
        assertEquals(7.5, paused.toJson().getDouble("credit"), 1e-9);
        assertEquals(START + 30 * HOUR, paused.toJson().getLong("lastRelease"));
        paused.setPaused(false);
        assertEquals(7, paused.release(START + 30 * HOUR));
        assertEquals(0.5, paused.toJson().getDouble("credit"), 1e-9);

        ConveyorOrder once = ConveyorOrder.create(2, "Caciotta");
        once.addTrays(5);
        assertEquals(0, once.release(START));
        assertEquals(5, once.startStep().left);
    }
}
