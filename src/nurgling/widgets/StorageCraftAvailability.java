package nurgling.widgets;

import nurgling.NCore;
import nurgling.actions.TakeFromStorageIndex;
import nurgling.db.dao.StorageItemDao;
import nurgling.widgets.storage.VillageStorage;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Per-window asynchronous index hints. A hint never authorizes moving an item without live verification. */
final class StorageCraftAvailability {
    private static final long REFRESH_NS = 10_000_000_000L;
    enum Status { CHECKING, UNAVAILABLE, EMPTY, PRESENT }
    private Map<String, Integer> counts = Map.of();
    private Set<String> names = Set.of();
    private Object session;
    private Object origin;
    private Object database;
    private boolean knowable;
    private boolean answered;
    private boolean failed;
    private long generation;
    private long lastQuery;
    private CompletableFuture<Answer> pending;
    private long pendingGeneration;

    private static final class Answer {
        final long generation;
        final Map<String, Integer> counts;
        final boolean failed;
        Answer(long generation, Map<String, Integer> counts, boolean failed) {
            this.generation = generation;
            this.counts = counts;
            this.failed = failed;
        }
    }

    void refresh(Collection<String> requested, VillageStorage village, Object currentSession,
                 Object currentOrigin, Object currentDatabase) {
        Set<String> wanted = new LinkedHashSet<>();
        for(String name : requested)
            if(TakeFromStorageIndex.eligibleName(name)) wanted.add(name);
        boolean canQuery = village != null && village.knowable()
                && currentOrigin != null && currentDatabase != null
                && NCore.databaseManager != null;
        updateContext(wanted, currentSession, currentOrigin, currentDatabase, canQuery);
        if(pending != null && pending.isDone()) {
            try {
                Answer result = pending.getNow(null);
                if(result != null) {
                    if(result.failed) applyFailure(result.generation);
                    else applyAnswer(result.generation, result.counts);
                }
            } catch (RuntimeException ignored) { applyFailure(pendingGeneration); }
            pending = null;
        }
        long now = System.nanoTime();
        if(pending != null || wanted.isEmpty() || !canQuery || now - lastQuery < REFRESH_NS) return;
        lastQuery = now;
        long requestedGeneration = generation;
        pendingGeneration = requestedGeneration;
        Set<String> namesCopy = Set.copyOf(wanted);
        nurgling.db.DatabaseManager manager = NCore.databaseManager;
        pending = CompletableFuture.supplyAsync(() -> {
            try {
                Set<Long> grids = village.village();
                if(grids == null || grids.isEmpty()) return new Answer(requestedGeneration, Map.of(), false);
                Map<String, Integer> count = new HashMap<>();
                for(StorageItemDao.LocatedItem row : manager.getStorageItemService()
                        .loadLocatedCountableByExactNames(namesCopy)) {
                    if(row != null && grids.contains(row.gridId) && namesCopy.contains(row.name))
                        count.merge(row.name, 1, Integer::sum);
                }
                return new Answer(requestedGeneration, Map.copyOf(count), false);
            } catch (Exception ignored) { return new Answer(requestedGeneration, Map.of(), true); }
        });
    }

    void updateContext(Set<String> wanted, Object currentSession, Object currentOrigin,
                       Object currentDatabase, boolean knowable) {
        if(session != currentSession || database != currentDatabase
                || !Objects.equals(origin, currentOrigin) || !names.equals(wanted)
                || this.knowable != knowable) {
            session = currentSession;
            origin = currentOrigin;
            database = currentDatabase;
            this.knowable = knowable;
            names = Set.copyOf(wanted);
            generation++;
            counts = Map.of();
            answered = false;
            failed = false;
            lastQuery = 0;
        }
    }

    long generation() { return generation; }

    void applyAnswer(long answerGeneration, Map<String, Integer> answerCounts) {
        if(answerGeneration == generation && knowable) {
            counts = Map.copyOf(answerCounts);
            answered = true;
            failed = false;
        }
    }

    void applyFailure(long answerGeneration) {
        if(answerGeneration == generation && knowable) {
            counts = Map.of();
            answered = false;
            failed = true;
        }
    }

    int count(String name) { return counts.getOrDefault(name, 0); }

    Status status(String name) {
        if(!knowable || failed) return Status.UNAVAILABLE;
        if(!answered || !names.contains(name)) return Status.CHECKING;
        return count(name) > 0 ? Status.PRESENT : Status.EMPTY;
    }
}
