package haven;

import java.util.function.Supplier;

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

    static boolean insideViewport(Coord point, Coord viewport, int margin) {
        return (point.x >= -margin) && (point.y >= -margin) &&
                (point.x <= viewport.x + margin) && (point.y <= viewport.y + margin);
    }

    private MiniMapIconPolicy() {
    }
}
