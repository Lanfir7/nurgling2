package nurgling.craftatlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Non-recursive producer index used by clickable ingredient links. */
public final class CraftRecipeGraph {
    public enum LinkState { NONE, SINGLE, MULTIPLE, CYCLE }
    private final Map<String, List<CraftAtlasEntry>> byOutput = new HashMap<>();

    public CraftRecipeGraph(CraftAtlasSnapshot snapshot) {
        if(snapshot == null) return;
        for(CraftAtlasEntry entry : snapshot.entries) {
            if(entry.outputResource == null || entry.outputResource.isEmpty()) continue;
            List<CraftAtlasEntry> entries = byOutput.get(entry.outputResource);
            if(entries == null) byOutput.put(entry.outputResource, entries = new ArrayList<>());
            entries.add(entry);
        }
        for(Map.Entry<String, List<CraftAtlasEntry>> entry : new ArrayList<>(byOutput.entrySet()))
            byOutput.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
    }

    public List<CraftAtlasEntry> producers(String outputResource) {
        List<CraftAtlasEntry> found = byOutput.get(outputResource);
        return found == null ? Collections.<CraftAtlasEntry>emptyList() : found;
    }

    public LinkState linkState(String outputResource, List<String> activeRecipePath) {
        List<CraftAtlasEntry> found = producers(outputResource);
        if(found.isEmpty()) return LinkState.NONE;
        if(activeRecipePath != null) {
            for(CraftAtlasEntry producer : found)
                if(activeRecipePath.contains(producer.recipeResource)) return LinkState.CYCLE;
        }
        return found.size() == 1 ? LinkState.SINGLE : LinkState.MULTIPLE;
    }
}
