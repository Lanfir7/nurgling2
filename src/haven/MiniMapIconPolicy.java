package haven;

import java.util.function.Supplier;
import nurgling.tools.ClaimLand;
import nurgling.tools.DefaultAnimalAlarms;

final class MiniMapIconPolicy {
    private static final double REFRESH_INTERVAL = 0.2;

    static final class RefreshResult<T> {
        final T value;
        final boolean refreshed;

        RefreshResult(T value, boolean refreshed) {
            this.value = value;
            this.refreshed = refreshed;
        }
    }

    static final class TimedRefresh<T> {
        private final double interval;
        private double age;
        private T value;

        TimedRefresh(double interval) {
            this.interval = interval;
            this.age = interval;
        }

        RefreshResult<T> update(double dt, Supplier<T> refresh) {
            age += dt;
            if ((value == null) || (age >= interval)) {
                value = refresh.get();
                age = 0;
                return new RefreshResult<>(value, true);
            }
            return new RefreshResult<>(value, false);
        }
    }

    static <T> TimedRefresh<T> newRefresh() {
        return new TimedRefresh<>(REFRESH_INTERVAL);
    }

    static GobIcon.Icon takeLoaded(Loader.Future<GobIcon.Icon> load) {
        try {
            return load.get();
        } catch (RuntimeException e) {
            new Warning(e, "could not load map marker icon").ctrace(false).issue();
            return null;
        }
    }

    static boolean insideViewport(Coord point, Coord viewport, int margin) {
        return (point.x >= -margin) && (point.y >= -margin) &&
                (point.x <= viewport.x + margin) && (point.y <= viewport.y + margin);
    }

    static final String PLAYER_ICON_RES = "gfx/hud/mmap/plo";

    static boolean isPlayerMapIcon(String resName) {
        return PLAYER_ICON_RES.equals(resName);
    }

    /** Own character and party members are drawn as party marks, not as gob icons. */
    static boolean skipPartyGob(long gobId, long playerGobId, boolean inParty) {
        if ((playerGobId >= 0) && (gobId == playerGobId))
            return true;
        return inParty;
    }

    /**
     * Until the party list arrives, the local character has no kin group and
     * matches the white Player icon. Keep those icons out so the alert cannot
     * re-arm every scan.
     */
    static boolean skipUnsyncedPlayerIcon(boolean partyKnown, String iconResName) {
        return !partyKnown && isPlayerMapIcon(iconResName);
    }

    static boolean shouldPlayIconNotify(boolean onClaim) {
        return ClaimLand.shouldPlayIconNotify(onClaim);
    }

    enum PlayerAlert { WAIT, MUTE, PLAY }

    static PlayerAlert playerAlert(String iconResName, boolean buddyPending,
                                   Integer group, double ageSeconds) {
        if (!isPlayerMapIcon(iconResName))
            return PlayerAlert.PLAY;
        if (buddyPending)
            return PlayerAlert.WAIT;
        if (group != null) {
            if (group < 0 || group > 7)
                return PlayerAlert.WAIT;
            // The kin list uses group 0 for white and 2 for red.
            return (group == 0 || group == 2)
                    ? PlayerAlert.PLAY : PlayerAlert.MUTE;
        }
        return ageSeconds >= 4.0 ? PlayerAlert.PLAY : PlayerAlert.WAIT;
    }

    static void fireIconNotify(DefaultAnimalAlarms.State alarmState, boolean onClaim,
                               String pose, String iconResName) {
        fireIconNotify(alarmState, () -> onClaim, pose, iconResName);
    }

    static void fireIconNotify(DefaultAnimalAlarms.State alarmState, Supplier<Boolean> onClaim,
                               String pose, String iconResName) {
        if (alarmState == null || !alarmState.isPending()) {
            return;
        }
        Boolean claim = (onClaim == null) ? null : onClaim.get();
        if (claim == null)
            return;
        boolean mute = claim;
        if (!shouldPlayIconNotify(mute)) {
            alarmState.dropSound();
            return;
        }
        alarmState.poll(pose, iconResName);
    }

    private MiniMapIconPolicy() {
    }
}
