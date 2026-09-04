package nurgling.tools;

/**
 * Game-day forage windows converted to real-time remaining.
 *
 * Mantle window/math follows community/EnderWiggin TimeWdg constants
 * (04:45–07:15, ratio via worldSpeed); reimplemented, not copied.
 */
public final class DayCycleEvents {
    /** Inclusive start of Dewy Lady's Mantle, 04:45 game time. */
    public static final int MANTLE_START_MIN = 285;
    /** Inclusive end of Dewy Lady's Mantle, 07:15 game time. */
    public static final int MANTLE_END_MIN = 435;
    private static final int MINUTES_PER_DAY = 24 * 60;

    private DayCycleEvents() {}

    /** {@code hh*60+mm}, same clock Cal.tick uses. */
    public static int minutesOfDay(int hh, int mm) {
        return hh * 60 + mm;
    }

    /**
     * Astronomy day-fraction clock: {@code hh=(int)(24*dt)},
     * {@code mm=(int)(60*(24*dt-hh))}.
     */
    public static int minutesOfDay(double dt) {
        int hh = (int) (24 * dt);
        int mm = (int) (60 * (24 * dt - hh));
        return minutesOfDay(hh, mm);
    }

    public static boolean inMantleWindow(int curtimeM) {
        return curtimeM >= MANTLE_START_MIN && curtimeM <= MANTLE_END_MIN;
    }

    public static MantleEta mantleEta(int hh, int mm, double worldSpeed) {
        return mantleEta(minutesOfDay(hh, mm), worldSpeed);
    }

    public static MantleEta mantleEta(int curtimeM, double worldSpeed) {
        boolean inWindow = inMantleWindow(curtimeM);
        int gameDelta;
        if (inWindow) {
            gameDelta = MANTLE_END_MIN - curtimeM;
        } else {
            int cur = curtimeM;
            if (cur > MANTLE_END_MIN) {
                cur -= MINUTES_PER_DAY;
            }
            gameDelta = MANTLE_START_MIN - cur;
        }
        int rlHours = (int) Math.floor(gameDelta / (60.0 * worldSpeed));
        int rlMinutes = (int) ((gameDelta / worldSpeed) % 60);
        return new MantleEta(inWindow, rlHours, rlMinutes);
    }

    public static final class MantleEta {
        public final boolean inWindow;
        public final int rlHours;
        public final int rlMinutes;

        public MantleEta(boolean inWindow, int rlHours, int rlMinutes) {
            this.inWindow = inWindow;
            this.rlHours = rlHours;
            this.rlMinutes = rlMinutes;
        }
    }
}
