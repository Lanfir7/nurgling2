package nurgling.craftatlas;

import haven.MenuGrid;

import java.util.function.LongSupplier;

/** The only Atlas component allowed to invoke the normal MenuGrid craft action. */
public final class CraftExecutionBridge {
    public static final long TIMEOUT_NANOS = 2_000_000_000L;

    @FunctionalInterface public interface Action { void use(); }
    @FunctionalInterface public interface Resolver { Action resolve(String recipeResource); }

    private final Resolver resolver;
    private final LongSupplier clock;
    private boolean pending;
    private long pendingSince;

    public CraftExecutionBridge(MenuGrid menu) {
        this(resource -> {
            MenuGrid.Pagina page = menu == null ? null : menu.recipeByResource(resource);
            return page == null ? null : () -> page.button().use(new MenuGrid.Interaction());
        }, System::nanoTime);
    }

    public CraftExecutionBridge(Resolver resolver, LongSupplier clock) {
        this.resolver = resolver;
        this.clock = clock;
    }

    public synchronized boolean open(String recipeResource, CraftAtlasEntry.Availability availability) {
        expire();
        if(pending || availability != CraftAtlasEntry.Availability.OPEN || recipeResource == null) return false;
        Action action = resolver.resolve(recipeResource);
        if(action == null) return false;
        pending = true;
        pendingSince = clock.getAsLong();
        try {
            action.use();
            return true;
        } catch(RuntimeException e) {
            pending = false;
            throw e;
        }
    }

    public synchronized void completed() { pending = false; }
    public synchronized boolean isPending() { expire(); return pending; }
    private void expire() {
        if(pending && clock.getAsLong() - pendingSince >= TIMEOUT_NANOS) pending = false;
    }
}
