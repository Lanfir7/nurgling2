package nurgling.forage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ForageStoreTest {
    @TempDir Path temp;

    private static ForageFind find() {
        return new ForageFind("find-1", 77, 3, 4, "gfx/terobjs/herbs/blueberry",
            "gfx/invobjs/herbs/blueberry", "Blueberries", 42.5, 2, 1000, "Alice", 0);
    }

    @Test void serverTombstoneRejectsAnInFlightUploadAndPersistsIt() {
        Path file = temp.resolve("forage.json");
        ForageStore store = new ForageStore("world", file.toString());
        store.add(find());
        List<ForageStore.Pending<ForageFind>> upload = store.pendingUpserts();
        store.add(find()); // a newer edit arrives while the first upload is in flight

        store.confirmUpserts(upload, Map.of(find().id, -1));

        assertTrue(store.finds().isEmpty());
        assertTrue(store.pendingUpserts().isEmpty());
        store.flush();
        assertTrue(new ForageStore("world", file.toString()).finds().isEmpty());
    }

    @Test void remoteTombstoneWinsOverAnOfflinePendingCopy() {
        ForageStore store = new ForageStore("world", temp.resolve("forage.json").toString());
        store.add(find());

        store.applyRemote(Collections.emptyList(), Collections.singleton(find().id), false);

        assertTrue(store.finds().isEmpty());
        assertTrue(store.pendingUpserts().isEmpty());
    }

    @Test void twoOfflineClientsConvergeOnNewestNearbyFindWithoutDeletingHistory() {
        ForageStore alice = new ForageStore("world", temp.resolve("alice.json").toString());
        ForageStore bob = new ForageStore("world", temp.resolve("bob.json").toString());
        ForageFind older = find();
        ForageFind newer = new ForageFind("find-2", 77, 5, 5, older.gobRes,
            older.itemRes, older.itemName, 49.0, 1, 2000, "Bob", 0);
        alice.add(older);
        bob.add(newer);

        alice.applyRemote(List.of(newer.withVersion(1)), Collections.emptySet(), false);
        bob.applyRemote(List.of(older.withVersion(1)), Collections.emptySet(), false);

        assertEquals(List.of(newer.id), alice.finds().stream().map(f -> f.id).toList());
        assertEquals(List.of(newer.id), bob.finds().stream().map(f -> f.id).toList());
        assertTrue(alice.pendingDeletes().isEmpty());
        assertTrue(bob.pendingDeletes().isEmpty());
        alice.flush();
        assertEquals(List.of(newer.id), new ForageStore("world", temp.resolve("alice.json").toString())
            .finds().stream().map(f -> f.id).toList());
    }

    @Test void distanceAndItemIdentityKeepDistinctObservationsVisible() {
        ForageStore store = new ForageStore("world", temp.resolve("distinct.json").toString());
        ForageFind first = find();
        store.add(first);
        store.add(new ForageFind("far", 77, 8, 8, first.gobRes, first.itemRes,
            first.itemName, 30, 1, 2000, "Bob", 0));
        store.add(new ForageFind("different-item", 77, 4, 4, "gfx/terobjs/herbs/chanterelle",
            "gfx/invobjs/herbs/chanterelle", "Chanterelle", 30, 1, 3000, "Bob", 0));

        assertEquals(3, store.finds().size());
    }
}
