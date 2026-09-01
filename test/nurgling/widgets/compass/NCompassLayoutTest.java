package nurgling.widgets.compass;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NCompassLayoutTest {
    @Test
    void rearBucketShowsNearestAndCountsTheRest() {
        List<NCompassLayout.Input> in = Arrays.asList(
                new NCompassLayout.Input("far", -Math.PI + 0.1, 80),
                new NCompassLayout.Input("near", -Math.PI + 0.2, 20),
                new NCompassLayout.Input("other", -Math.PI + 0.3, 40));

        List<NCompassLayout.Marker> out = NCompassLayout.arrange(in, 0.0, 500, 70, 2);

        NCompassLayout.Marker left = marker(out, NCompassMath.Region.REAR_LEFT);
        assertEquals("near", left.id);
        assertEquals(0, left.x);
        assertEquals(2, left.extra);
    }

    @Test
    void rearSidesAreAggregatedIndependently() {
        List<NCompassLayout.Input> in = Arrays.asList(
                new NCompassLayout.Input("left", -Math.PI + 0.1, 10),
                new NCompassLayout.Input("right", Math.PI - 0.1, 15));

        List<NCompassLayout.Marker> out = NCompassLayout.arrange(in, 0.0, 500, 70, 2);

        assertEquals("left", marker(out, NCompassMath.Region.REAR_LEFT).id);
        assertEquals("right", marker(out, NCompassMath.Region.REAR_RIGHT).id);
    }

    @Test
    void collisionsUseTwoLanesThenAggregate() {
        List<NCompassLayout.Input> in = Arrays.asList(
                new NCompassLayout.Input("a", 0.0, 10),
                new NCompassLayout.Input("b", 0.0, 20),
                new NCompassLayout.Input("c", 0.0, 30));

        List<NCompassLayout.Marker> out = NCompassLayout.arrange(in, 0.0, 500, 70, 2);

        assertEquals(2, out.size());
        assertEquals(1, out.stream().mapToInt(m -> m.extra).sum());
        assertEquals(0, out.get(0).lane);
        assertEquals(1, out.get(1).lane);
    }

    @Test
    void separatedFrontTargetsStayInFirstLane() {
        List<NCompassLayout.Input> in = Arrays.asList(
                new NCompassLayout.Input("left", -Math.PI / 2, 10),
                new NCompassLayout.Input("right", Math.PI / 2, 20));

        List<NCompassLayout.Marker> out = NCompassLayout.arrange(in, 0.0, 500, 70, 2);

        assertEquals(2, out.size());
        assertEquals(0, out.get(0).lane);
        assertEquals(0, out.get(1).lane);
    }

    private static NCompassLayout.Marker marker(List<NCompassLayout.Marker> markers,
                                                 NCompassMath.Region region) {
        return markers.stream()
                .filter(m -> m.region == region)
                .findFirst()
                .orElseThrow(AssertionError::new);
    }
}
