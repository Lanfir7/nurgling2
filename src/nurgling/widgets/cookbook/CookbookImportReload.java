package nurgling.widgets.cookbook;

import nurgling.actions.Results;

/**
 * Headless seam for whether a JSON import {@link Results} should reload the cookbook catalog,
 * and when a pending import reload may actually start.
 */
public final class CookbookImportReload {
    private CookbookImportReload() {
    }

    public static boolean shouldReload(Results results) {
        return (results != null) && results.IsSuccess();
    }

    /** Start only when a successful import is pending, the DB is ready, and no catalog load is running. */
    public static boolean shouldStartReload(boolean pending, boolean dbReady, boolean loaderBusy) {
        return pending && dbReady && !loaderBusy;
    }

    /** Keep the pending flag while the DB is not ready or a catalog load is already running. */
    public static boolean shouldKeepPending(boolean pending, boolean dbReady, boolean loaderBusy) {
        return pending && (!dbReady || loaderBusy);
    }
}
