package nurgling.widgets.craftatlas;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Coordinates asynchronous base-storage snapshots and rejects stale completions. */
final class CraftAtlasStoredItemsState {
    static final class Result {
        final long revision;
        final Set<String> names;
        final Exception error;

        private Result(long revision, Set<String> names, Exception error) {
            this.revision = revision;
            this.names = names;
            this.error = error;
        }
    }

    private long request;
    private long requestedRevision;
    private boolean loading;
    private Result pending;

    synchronized long begin(long revision) {
        requestedRevision = revision;
        loading = true;
        pending = null;
        return ++request;
    }

    synchronized void complete(long completedRequest, Set<String> names, Exception error) {
        if(completedRequest != request || !loading) return;
        Set<String> snapshot = names == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(names));
        pending = new Result(requestedRevision, snapshot, error);
    }

    synchronized Result take() {
        Result result = pending;
        if(result != null) {
            pending = null;
            loading = false;
        }
        return result;
    }

    synchronized void cancel() {
        request++;
        loading = false;
        pending = null;
    }

    synchronized boolean loading() {
        return loading;
    }
}
