package nurgling.craftatlas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic, immutable view of every recipe currently known by the Atlas. */
public final class CraftAtlasSnapshot {
    public final long revision;
    public final List<CraftAtlasEntry> entries;
    private final Map<String, CraftAtlasEntry> byRecipe;

    private CraftAtlasSnapshot(long revision, Collection<CraftAtlasEntry> source) {
        this.revision = revision;
        LinkedHashMap<String, CraftAtlasEntry> index = new LinkedHashMap<>();
        if(source != null) {
            for(CraftAtlasEntry entry : source) {
                if(entry == null) throw new IllegalArgumentException("entry must not be null");
                if(index.put(entry.recipeResource, entry) != null)
                    throw new IllegalArgumentException("duplicate recipe resource: " + entry.recipeResource);
            }
        }
        byRecipe = Collections.unmodifiableMap(index);
        entries = Collections.unmodifiableList(new ArrayList<>(index.values()));
    }

    public static CraftAtlasSnapshot of(long revision, Collection<CraftAtlasEntry> entries) {
        return new CraftAtlasSnapshot(revision, entries);
    }

    public CraftAtlasEntry byRecipe(String resource) {
        return byRecipe.get(resource);
    }
}
