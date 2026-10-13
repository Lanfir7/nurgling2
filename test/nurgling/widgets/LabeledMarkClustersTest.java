package nurgling.widgets;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabeledMarkClustersTest {
    private static LabeledMinimapMark mark(String type, int quality, int x, int y) {
        return new LabeledMinimapMark("q" + quality, type, quality, 1L, new Coord(x, y), null);
    }

    @Test
    void groupsOnlyNearbyMinedMarksOfTheSameTypeAtCurrentZoom() {
        LabeledMinimapMark low = mark("Granite", 20, 2, 0);
        LabeledMinimapMark best = mark("Granite", 50, 0, 0);
        LabeledMinimapMark other = mark("Cassiterite", 40, 1, 0);
        LabeledMinimapMark distant = mark("Granite", 30, 50, 0);
        List<LabeledMinimapMark> marks = Arrays.asList(low, other, distant, best);

        LabeledMarkClusters clusters = new LabeledMarkClusters(marks, 1, 28, true, mark -> true);

        assertEquals(3, clusters.clusters.size());
        LabeledMarkClusters.Cluster granite = clusters.clusters.stream()
            .filter(c -> c.best() == best).findFirst().orElseThrow();
        assertEquals(2, granite.marks.size());
        assertSame(low, granite.worst());
        assertEquals(new Coord(0, 0), granite.anchor);
        assertEquals(new Coord(100, 100), granite.screenPos(new Coord(0, 0), 1, new Coord(100, 100)));
    }

    @Test
    void toggleAndVisibilitySnapshotKeepHiddenOrNonMinedMarksSeparate() {
        LabeledMinimapMark high = mark("Granite", 50, 0, 0);
        LabeledMinimapMark low = mark("Granite", 20, 2, 0);
        LabeledMinimapMark sample = mark("Water", 30, 1, 0);
        List<LabeledMinimapMark> visible = Arrays.asList(high, low, sample);

        LabeledMarkClusters enabled = new LabeledMarkClusters(visible, 1, 28, true,
            mark -> !"Water".equals(mark.resourceType));
        assertEquals(2, enabled.clusters.size());
        assertTrue(enabled.matches(visible, 1, true));
        assertFalse(enabled.matches(Arrays.asList(high, sample), 1, true));
        assertFalse(enabled.matches(visible, 2, true));
        assertFalse(enabled.matches(visible, 1, false));

        LabeledMarkClusters disabled = new LabeledMarkClusters(visible, 1, 28, false,
            mark -> !"Water".equals(mark.resourceType));
        assertEquals(3, disabled.clusters.size());
        assertTrue(disabled.clusters.stream().allMatch(c -> c.marks.size() == 1));
    }
}
