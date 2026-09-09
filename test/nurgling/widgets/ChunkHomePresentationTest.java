package nurgling.widgets;

import nurgling.navigation.ChunkNavData;
import nurgling.navigation.ChunkNavManager;
import nurgling.tools.ClaimArea;
import nurgling.tools.HomeInteriorRegistry;
import nurgling.tools.HomeTerritories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkHomePresentationTest {
    @ParameterizedTest
    @ValueSource(strings = {"outside", "mine1", "mine2", "cave"})
    void manualMarkIsDisabledOutsideAndInMinesOrCaves(String layer) {
        ChunkNavData chunk = chunk(701L, 700L, layer);
        ChunkHomePresentation presentation = ChunkHomePresentation.forChunk(
                chunk, HomeInteriorRegistry.empty(), Collections.emptyList());

        assertFalse(presentation.canMarkManual);
        assertEquals(ChunkHomePresentation.Kind.RESTRICTED, presentation.kind);
    }

    @Test
    void unknownLayerAndUnknownInstanceStayRestricted() {
        ChunkHomePresentation unknownLayer = ChunkHomePresentation.forChunk(
                chunk(701L, 700L, "unknown"), HomeInteriorRegistry.empty(), Collections.emptyList());
        ChunkHomePresentation unknownInstance = ChunkHomePresentation.forChunk(
                chunk(701L, 0L, "inside"), HomeInteriorRegistry.empty(), Collections.emptyList());
        ChunkHomePresentation surfaceSentinel = ChunkHomePresentation.forChunk(
                chunk(701L, ChunkNavManager.SURFACE_INSTANCE, "inside"),
                HomeInteriorRegistry.empty(), Collections.emptyList());
        ChunkHomePresentation noSelection = ChunkHomePresentation.forChunk(
                null, HomeInteriorRegistry.empty(), Collections.emptyList());

        assertRestricted(unknownLayer);
        assertRestricted(unknownInstance);
        assertRestricted(surfaceSentinel);
        assertRestricted(noSelection);
    }

    @Test
    void insideAndCellarCanBeMarkedWhenNotHome() {
        ChunkHomePresentation inside = ChunkHomePresentation.forChunk(
                chunk(701L, 700L, "inside"), HomeInteriorRegistry.empty(), Collections.emptyList());
        ChunkHomePresentation cellar = ChunkHomePresentation.forChunk(
                chunk(802L, 800L, "cellar"), HomeInteriorRegistry.empty(), Collections.emptyList());

        assertEquals(ChunkHomePresentation.Kind.NONE, inside.kind);
        assertTrue(inside.canMarkManual);
        assertFalse(inside.canUnmarkManual);
        assertFalse(inside.active);
        assertEquals("", inside.sourceLabel);

        assertEquals(ChunkHomePresentation.Kind.NONE, cellar.kind);
        assertTrue(cellar.canMarkManual);
        assertFalse(cellar.canUnmarkManual);
        assertFalse(cellar.active);
    }

    @Test
    void automaticBindingReportsAutoKindActiveOriginAndMarkAvailability() {
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty().put(automaticBinding(
                700L, setOf(701L, 702L), "Lanfir's Claim -> Stone Mansion"));
        ChunkHomePresentation presentation = ChunkHomePresentation.forChunk(
                chunk(701L, 700L, "inside"), registry, savedClaim());

        assertEquals(ChunkHomePresentation.Kind.AUTO, presentation.kind);
        assertTrue(presentation.active);
        assertTrue(presentation.canMarkManual);
        assertFalse(presentation.canUnmarkManual);
        assertEquals("Lanfir's Claim -> Stone Mansion", presentation.sourceLabel);
    }

    @Test
    void automaticOriginTextIsDeterministicWhenDisplayNameIsEmpty() {
        HomeInteriorRegistry.Binding binding = HomeInteriorRegistry.Binding.automatic(
                "auto:700", 700L, setOf(701L),
                setOf(HomeInteriorRegistry.OriginKey.parse("village:oak vale"),
                        HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                new HomeInteriorRegistry.PortalIdentity(42L, 7, 9, "gfx/terobjs/arch/stonemansion"),
                "", 1L);
        List<HomeTerritories.Entry> saved = Arrays.asList(
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Oak Vale"),
                savedClaim().get(0));
        ChunkHomePresentation presentation = ChunkHomePresentation.forChunk(
                chunk(701L, 700L, "inside"),
                HomeInteriorRegistry.empty().put(binding), saved);

        assertEquals(ChunkHomePresentation.Kind.AUTO, presentation.kind);
        assertEquals("Lanfir's Claim, Oak Vale", presentation.sourceLabel);
    }

    @Test
    void manualBindingReportsManualKindAndUnmarkAvailability() {
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty()
                .markManual(700L, setOf(701L), "Manual house");
        ChunkHomePresentation presentation = ChunkHomePresentation.forChunk(
                chunk(701L, 700L, "cellar"), registry, Collections.emptyList());

        assertEquals(ChunkHomePresentation.Kind.MANUAL, presentation.kind);
        assertTrue(presentation.active);
        assertFalse(presentation.canMarkManual);
        assertTrue(presentation.canUnmarkManual);
        assertEquals("Manual house", presentation.sourceLabel);
    }

    @Test
    void everyLoadedGridOfTheSameInstanceSharesAutomaticHomeState() {
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty().put(automaticBinding(
                700L, setOf(701L), "Lanfir's Claim -> Stone Mansion"));
        ChunkHomePresentation first = ChunkHomePresentation.forChunk(
                chunk(701L, 700L, "inside"), registry, savedClaim());
        ChunkHomePresentation second = ChunkHomePresentation.forChunk(
                chunk(702L, 700L, "inside"), registry, savedClaim());

        assertEquals(ChunkHomePresentation.Kind.AUTO, first.kind);
        assertEquals(ChunkHomePresentation.Kind.AUTO, second.kind);
        assertTrue(first.active);
        assertTrue(second.active);
        assertEquals(first.sourceLabel, second.sourceLabel);
    }

    @Test
    void unmarkingManualKeepsStillActiveAutomaticOrigins() {
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty()
                .put(automaticBinding(700L, setOf(701L, 702L), "Lanfir's Claim -> Stone Mansion"))
                .markManual(700L, setOf(701L, 702L), "Lanfir's Claim -> Stone Mansion");
        ChunkHomePresentation marked = ChunkHomePresentation.forChunk(
                chunk(702L, 700L, "inside"), registry, savedClaim());
        HomeInteriorRegistry unmarked = registry.unmarkManual(700L);
        ChunkHomePresentation afterUnmark = ChunkHomePresentation.forChunk(
                chunk(702L, 700L, "inside"), unmarked, savedClaim());

        assertEquals(ChunkHomePresentation.Kind.MANUAL, marked.kind);
        assertTrue(marked.canUnmarkManual);
        assertEquals(ChunkHomePresentation.Kind.AUTO, afterUnmark.kind);
        assertTrue(afterUnmark.active);
        assertTrue(afterUnmark.canMarkManual);
        assertFalse(afterUnmark.canUnmarkManual);
        assertEquals("Lanfir's Claim -> Stone Mansion", afterUnmark.sourceLabel);
        assertEquals(1, unmarked.bindings().size());
    }

    private static void assertRestricted(ChunkHomePresentation presentation) {
        assertEquals(ChunkHomePresentation.Kind.RESTRICTED, presentation.kind);
        assertFalse(presentation.active);
        assertFalse(presentation.canMarkManual);
        assertFalse(presentation.canUnmarkManual);
        assertEquals("", presentation.sourceLabel);
    }

    private static ChunkNavData chunk(long gridId, long instanceId, String layer) {
        ChunkNavData data = new ChunkNavData(gridId);
        data.instanceId = instanceId;
        data.layer = layer;
        return data;
    }

    private static List<HomeTerritories.Entry> savedClaim() {
        ClaimArea area = new ClaimArea(new ClaimArea.Tile(42L, 7, 9),
                Collections.singleton(new ClaimArea.Tile(42L, 7, 9)));
        return Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir", area));
    }

    private static HomeInteriorRegistry.Binding automaticBinding(long instanceId,
            Set<Long> gridIds, String displayName) {
        return HomeInteriorRegistry.Binding.automatic(
                "auto:" + instanceId, instanceId, gridIds,
                setOf(HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                new HomeInteriorRegistry.PortalIdentity(42L, 7, 9,
                        "gfx/terobjs/arch/stonemansion"),
                displayName, 1L);
    }

    @SafeVarargs
    private static <T> Set<T> setOf(T... values) {
        return new LinkedHashSet<T>(Arrays.asList(values));
    }
}
