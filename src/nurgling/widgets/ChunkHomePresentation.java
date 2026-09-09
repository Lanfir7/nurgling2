package nurgling.widgets;

import nurgling.navigation.ChunkNavData;
import nurgling.navigation.ChunkNavManager;
import nurgling.tools.HomeInteriorRegistry;
import nurgling.tools.HomeTerritories;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

public final class ChunkHomePresentation {
    public enum Kind { AUTO, MANUAL, NONE, RESTRICTED }

    public final Kind kind;
    public final boolean active;
    public final boolean canMarkManual;
    public final boolean canUnmarkManual;
    public final String sourceLabel;

    private ChunkHomePresentation(Kind kind, boolean active, boolean canMarkManual,
            boolean canUnmarkManual, String sourceLabel) {
        this.kind = kind;
        this.active = active;
        this.canMarkManual = canMarkManual;
        this.canUnmarkManual = canUnmarkManual;
        this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
    }

    public static ChunkHomePresentation forChunk(ChunkNavData chunk,
            HomeInteriorRegistry registry,
            Collection<HomeTerritories.Entry> savedHomes) {
        Collection<HomeTerritories.Entry> saved = savedHomes == null
                ? Collections.<HomeTerritories.Entry>emptyList() : savedHomes;
        HomeInteriorRegistry homes = registry == null ? HomeInteriorRegistry.empty() : registry;
        if (chunk == null || !manualEligible(chunk))
            return new ChunkHomePresentation(Kind.RESTRICTED, false, false, false, "");
        HomeInteriorRegistry.Binding binding = homes.findActive(chunk.gridId, chunk.instanceId, saved);
        if (binding == null)
            return new ChunkHomePresentation(Kind.NONE, false, true, false, "");
        String label = binding.sourceLabel(saved);
        if (binding.manual)
            return new ChunkHomePresentation(Kind.MANUAL, true, false, true, label);
        return new ChunkHomePresentation(Kind.AUTO, true, true, false, label);
    }

    private static boolean manualEligible(ChunkNavData chunk) {
        if (chunk.instanceId <= ChunkNavManager.SURFACE_INSTANCE)
            return false;
        String layer = chunk.layer == null ? "outside" : chunk.layer.toLowerCase(Locale.ROOT).trim();
        return "inside".equals(layer) || "cellar".equals(layer);
    }
}
