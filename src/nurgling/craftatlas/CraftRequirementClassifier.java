package nurgling.craftatlas;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Separates consumed ingredients from tools and stations. */
public final class CraftRequirementClassifier {
    private static final String[] KNOWN_STATIONS = {
            "anvil", "cauldron", "crucible", "kiln", "oven", "smelter", "fineryforge",
            "loom", "quern", "workbench", "churn", "press", "fireplace", "stackfurnace"
    };
    private final Map<String, CraftAtlasEntry.RequirementKind> overrides;

    public CraftRequirementClassifier(Map<String, CraftAtlasEntry.RequirementKind> trustedOverrides) {
        overrides = Collections.unmodifiableMap(new LinkedHashMap<>(trustedOverrides == null
                ? Collections.<String, CraftAtlasEntry.RequirementKind>emptyMap() : trustedOverrides));
    }

    public CraftAtlasEntry.Requirement classify(String resource, String name) {
        CraftAtlasEntry.RequirementKind kind = overrides.get(resource);
        String description = null;
        if(kind == null && knownStation(resource, name))
            kind = CraftAtlasEntry.RequirementKind.STATION;
        else if(kind == null && resource != null && resource.startsWith("gfx/invobjs/"))
            kind = CraftAtlasEntry.RequirementKind.TOOL;
        else if(kind == null) {
            kind = CraftAtlasEntry.RequirementKind.TOOL;
            description = "Unknown requirement type";
        }
        return new CraftAtlasEntry.Requirement(kind, resource, name, description);
    }

    private static boolean knownStation(String resource, String name) {
        if(resource != null && resource.startsWith("gfx/terobjs/")) return true;
        String value = ((resource == null ? "" : resource) + " " + (name == null ? "" : name)).toLowerCase();
        for(String station : KNOWN_STATIONS) if(value.contains(station)) return true;
        return false;
    }
}
