package nurgling.tools;

import haven.Coord;

import java.util.function.BooleanSupplier;

/** Coordinates one automatic home capture after travelling to the player's hearth fire. */
public final class HearthHomeAutoSaver {
    static final double SETTLE_SECONDS = 0.75;
    static final double RETRY_SECONDS = 0.25;
    static final double TIMEOUT_SECONDS = 15.0;

    private boolean armed;
    private boolean teleportStarted;
    private Coord originTile;
    private double elapsed;
    private double settledFor;
    private double retryIn;

    public static boolean isHearthTravel(String... args) {
        return args != null && args.length >= 2
                && "travel".equals(args[0])
                && "hearth".equals(args[1]);
    }

    public void onAction(String... args) {
        onAction(null, args);
    }

    public void onAction(Coord originTile, String... args) {
        if (!isHearthTravel(args))
            return;
        armed = true;
        teleportStarted = false;
        this.originTile = originTile;
        elapsed = 0;
        settledFor = 0;
        retryIn = 0;
    }

    public void onTeleportStarted() {
        if (armed)
            teleportStarted = true;
    }

    public boolean hasReachedDestination(Coord currentTile) {
        return armed && teleportStarted && originTile != null && currentTile != null
                && !originTile.equals(currentTile);
    }

    public void onTerritoryUpdated(boolean ownerPresent) {
        if (!armed || !ownerPresent)
            return;
        settledFor = 0;
        retryIn = 0;
    }

    public void tick(double dt, BooleanSupplier destinationReady,
                     BooleanSupplier saveCurrentTerritories) {
        if (!armed)
            return;
        double step = Math.max(0, dt);
        elapsed += step;
        if (elapsed >= TIMEOUT_SECONDS) {
            armed = false;
            return;
        }
        if (!teleportStarted)
            return;

        settledFor += step;
        if (settledFor < SETTLE_SECONDS)
            return;
        retryIn -= step;
        if (retryIn > 0)
            return;

        try {
            if (!destinationReady.getAsBoolean()) {
                retryIn = RETRY_SECONDS;
                return;
            }
            if (saveCurrentTerritories.getAsBoolean()) {
                armed = false;
                return;
            }
        } catch (RuntimeException ignored) {
            // A transient resource/config failure must never escape from the UI tick.
        }
        if (armed)
            retryIn = RETRY_SECONDS;
    }

    boolean isArmed() {
        return armed;
    }
}
