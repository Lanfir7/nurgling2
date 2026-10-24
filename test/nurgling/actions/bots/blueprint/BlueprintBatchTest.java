package nurgling.actions.bots.blueprint;

import nurgling.planning.PlanningGhost;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintBatchTest {
    private static GhostJob job(String id) {
        return new GhostJob(new PlanningGhost(id, "gfx/terobjs/smelter", null,
                -4, 22, -11, Math.PI / 2), "Workshop");
    }

    private static final class DepositPort implements GhostConstruction.Deposit {
        boolean owned = true;
        int slot = 2, units = 3, sends;
        boolean acknowledge = true;
        @Override public boolean owned() { return owned; }
        @Override public int slotCount() { return slot; }
        @Override public int heldUnits() { return units; }
        @Override public void sendOne() { sends++; }
        @Override public void await(int before) {
            if (acknowledge) { slot++; units--; }
        }
    }

    private static class Port implements BlueprintBatch.Port {
        int builds, removals;
        boolean changed;
        GhostConstruction.Deposit deposit;
        @Override public boolean unchanged(PlanningGhost ghost) { return !changed; }
        @Override public void build(GhostJob job) throws GhostConstruction.FatalException, InterruptedException {
            builds++;
            if (deposit != null) GhostConstruction.depositOne(deposit);
            job.status = GhostJob.Status.BUILT;
        }
        @Override public boolean removeIfUnchanged(PlanningGhost ghost) { removals++; return !changed; }
        @Override public void progress(GhostJob job, int index, int total) {}
        @Override public void finished(GhostJob job, int index, int total) {}
    }

    @Test void foreignBuildWindowStopsBeforeSendingOrStartingNextGhost() throws Exception {
        DepositPort receipt = new DepositPort();
        receipt.owned = false;
        Port port = new Port();
        port.deposit = receipt;
        BlueprintBatch.Outcome result = BlueprintBatch.run(Arrays.asList(job("one"), job("two")), true, false, port);
        assertNotNull(result.failure);
        assertEquals(1, port.builds);
        assertEquals(0, receipt.sends);
        assertEquals(0, port.removals);
    }

    @Test void partialOrLateMaterialReceiptStopsBeforeNextGhostWithoutAnotherTransfer() throws Exception {
        DepositPort receipt = new DepositPort();
        receipt.acknowledge = false;
        Port port = new Port();
        port.deposit = receipt;
        BlueprintBatch.Outcome result = BlueprintBatch.run(Arrays.asList(job("one"), job("two")), true, false, port);
        assertNotNull(result.failure);
        assertEquals(1, receipt.sends);
        assertEquals(1, port.builds);
        assertEquals(0, port.removals);
    }

    @Test void editedGhostCannotBeRemovedAndUnconfirmedBuildCannotStartNext() throws Exception {
        Port changed = new Port() {
            @Override public void build(GhostJob job) throws GhostConstruction.FatalException, InterruptedException {
                super.build(job);
                this.changed = true;
            }
        };
        BlueprintBatch.Outcome result = BlueprintBatch.run(Arrays.asList(job("one"), job("two")), true, false, changed);
        assertNotNull(result.failure);
        assertEquals(1, changed.builds);
        assertEquals(0, changed.removals);

        Port unresolved = new Port() {
            @Override public void build(GhostJob job) { builds++; }
        };
        result = BlueprintBatch.run(Arrays.asList(job("one"), job("two")), true, false, unresolved);
        assertNotNull(result.failure);
        assertEquals(1, unresolved.builds);
        assertEquals(0, unresolved.removals);
    }

    @Test void confirmedReceiptAndFinishedBuildingRemoveOnlyMatchingGhost() throws Exception {
        DepositPort receipt = new DepositPort();
        Port port = new Port();
        port.deposit = receipt;
        List<GhostJob> jobs = Arrays.asList(job("one"), job("two"));
        BlueprintBatch.Outcome result = BlueprintBatch.run(jobs, true, true, port);
        assertNull(result.failure);
        assertEquals(2, port.builds);
        assertEquals(2, port.removals);
        assertEquals(2, receipt.sends);
    }

    @Test void confirmedFetchAndDepositForThirtyFiveUnitsStillReachTheBuildPass() throws Exception {
        GhostConstruction.FinishPassBudget budget = new GhostConstruction.FinishPassBudget();
        int[] clay = {0, 35};
        Map<String, int[]> slots = Collections.singletonMap("Clay", clay);
        int fetched = 0, deposited = 0, held = 0, started = 0;
        while (budget.next()) {
            budget.observeInitialSlots(slots);
            if (clay[1] == 0) {
                started++;
                break;
            }
            if (held == 0) { fetched++; held++; }
            else { deposited++; held--; clay[0]++; clay[1]--; }
        }
        assertEquals(35, fetched);
        assertEquals(35, deposited);
        assertEquals(1, started, "the final Build command needs a pass after the last deposit");

        GhostConstruction.FinishPassBudget stalled = new GhostConstruction.FinishPassBudget();
        stalled.observeInitialSlots(Collections.singletonMap("Clay", new int[]{0, 1}));
        int attempts = 0;
        while (stalled.next()) attempts++;
        assertEquals(60, attempts, "an unchanging construction remains bounded");
    }
}
