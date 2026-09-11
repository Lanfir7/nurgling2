package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProvinceNameResolverTest {
    @Test
    void choosesNearestCandidateInSameSegment() {
        assertEquals("Near", ProvinceNameResolver.nearest(10L, 100, 100, Arrays.asList(
                new ProvinceNameResolver.Candidate("Far", 10L, 500, 500),
                new ProvinceNameResolver.Candidate("Near", 10L, 102, 99),
                new ProvinceNameResolver.Candidate("Other segment", 11L, 100, 100))));
    }

    @Test
    void ignoresNullBlankAndOtherSegmentCandidates() {
        assertNull(ProvinceNameResolver.nearest(10L, 0, 0, Arrays.asList(
                null,
                new ProvinceNameResolver.Candidate(null, 10L, 0, 0),
                new ProvinceNameResolver.Candidate("  ", 10L, 0, 0),
                new ProvinceNameResolver.Candidate("Elsewhere", 11L, 0, 0))));
    }

    @Test
    void breaksDistanceTiesDeterministically() {
        assertEquals("Alpha", ProvinceNameResolver.nearest(10L, 0, 0, Arrays.asList(
                new ProvinceNameResolver.Candidate("Zulu", 10L, -1, 0),
                new ProvinceNameResolver.Candidate("Alpha", 10L, 1, 0))));
    }

    @Test
    void handlesLargeAndNegativeCoordinatesWithoutOverflow() {
        assertEquals("Near maximum", ProvinceNameResolver.nearest(10L, Integer.MAX_VALUE, Integer.MAX_VALUE, Arrays.asList(
                new ProvinceNameResolver.Candidate("Near maximum", 10L, Integer.MAX_VALUE - 1L, Integer.MAX_VALUE - 1L),
                new ProvinceNameResolver.Candidate("Far minimum", 10L, Integer.MIN_VALUE, Integer.MIN_VALUE))));
        assertEquals("Near minimum", ProvinceNameResolver.nearest(10L, Integer.MIN_VALUE, Integer.MIN_VALUE, Arrays.asList(
                new ProvinceNameResolver.Candidate("Far maximum", 10L, Integer.MAX_VALUE, Integer.MAX_VALUE),
                new ProvinceNameResolver.Candidate("Near minimum", 10L, Integer.MIN_VALUE + 1L, Integer.MIN_VALUE + 1L))));
    }
}
