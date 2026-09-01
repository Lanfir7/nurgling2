package nurgling.widgets.compass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class NCompassLayout {
    public static final class Input {
        public final String id;
        public final double targetBearing;
        public final double distance;

        public Input(String id, double targetBearing, double distance) {
            this.id = id;
            this.targetBearing = targetBearing;
            this.distance = distance;
        }
    }

    public static final class Marker {
        public final String id;
        public final int x;
        public final int lane;
        public final int extra;
        public final NCompassMath.Region region;

        Marker(String id, int x, int lane, int extra, NCompassMath.Region region) {
            this.id = id;
            this.x = x;
            this.lane = lane;
            this.extra = extra;
            this.region = region;
        }
    }

    private static final class Projected {
        final Input input;
        final NCompassMath.Projection projection;

        Projected(Input input, NCompassMath.Projection projection) {
            this.input = input;
            this.projection = projection;
        }
    }

    private static final class MutableMarker {
        final String id;
        final int x;
        final int lane;
        final NCompassMath.Region region;
        int extra;

        MutableMarker(String id, int x, int lane, int extra, NCompassMath.Region region) {
            this.id = id;
            this.x = x;
            this.lane = lane;
            this.extra = extra;
            this.region = region;
        }

        Marker freeze() {
            return new Marker(id, x, lane, extra, region);
        }
    }

    private NCompassLayout() {
    }

    public static List<Marker> arrange(Collection<Input> inputs, double cameraAngle,
                                       int width, int minGap, int laneCount) {
        List<Projected> front = new ArrayList<>();
        List<Projected> rearLeft = new ArrayList<>();
        List<Projected> rearRight = new ArrayList<>();
        if(inputs != null) {
            for(Input input : inputs) {
                if(input == null || input.id == null)
                    continue;
                NCompassMath.Projection projection =
                        NCompassMath.project(input.targetBearing, cameraAngle, width);
                Projected projected = new Projected(input, projection);
                switch(projection.region) {
                    case FRONT:
                        front.add(projected);
                        break;
                    case REAR_LEFT:
                        rearLeft.add(projected);
                        break;
                    case REAR_RIGHT:
                        rearRight.add(projected);
                        break;
                }
            }
        }

        front.sort(Comparator.comparingDouble(p -> distanceKey(p.input.distance)));
        int lanes = Math.max(1, laneCount);
        int gap = Math.max(0, minGap);
        List<MutableMarker> placed = new ArrayList<>();
        for(Projected candidate : front) {
            int lane = firstFreeLane(placed, candidate.projection.x, gap, lanes);
            if(lane >= 0) {
                placed.add(new MutableMarker(candidate.input.id, candidate.projection.x,
                        lane, 0, NCompassMath.Region.FRONT));
            } else {
                nearestAtX(placed, candidate.projection.x).extra++;
            }
        }
        addRear(placed, rearLeft);
        addRear(placed, rearRight);

        List<Marker> out = new ArrayList<>(placed.size());
        for(MutableMarker marker : placed)
            out.add(marker.freeze());
        out.sort(Comparator.comparingInt((Marker marker) -> marker.x)
                .thenComparingInt(marker -> marker.lane)
                .thenComparing(marker -> marker.id));
        return out;
    }

    private static int firstFreeLane(List<MutableMarker> placed, int x, int minGap, int lanes) {
        for(int lane = 0; lane < lanes; lane++) {
            boolean free = true;
            for(MutableMarker marker : placed) {
                if(marker.region == NCompassMath.Region.FRONT && marker.lane == lane &&
                        Math.abs(marker.x - x) < minGap) {
                    free = false;
                    break;
                }
            }
            if(free)
                return lane;
        }
        return -1;
    }

    private static MutableMarker nearestAtX(List<MutableMarker> placed, int x) {
        MutableMarker nearest = null;
        int best = Integer.MAX_VALUE;
        for(MutableMarker marker : placed) {
            if(marker.region != NCompassMath.Region.FRONT)
                continue;
            int distance = Math.abs(marker.x - x);
            if(distance < best) {
                nearest = marker;
                best = distance;
            }
        }
        return nearest;
    }

    private static void addRear(List<MutableMarker> placed, List<Projected> bucket) {
        if(bucket.isEmpty())
            return;
        Projected nearest = bucket.stream()
                .min(Comparator.comparingDouble(p -> distanceKey(p.input.distance)))
                .orElseThrow(AssertionError::new);
        placed.add(new MutableMarker(nearest.input.id, nearest.projection.x,
                0, bucket.size() - 1, nearest.projection.region));
    }

    private static double distanceKey(double distance) {
        return (Double.isNaN(distance) || distance < 0) ? Double.POSITIVE_INFINITY : distance;
    }
}
