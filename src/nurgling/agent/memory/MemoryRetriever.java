package nurgling.agent.memory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MemoryRetriever {
    private final MemoryStore store;

    public MemoryRetriever(MemoryStore store) {
        this.store = store;
    }

    public List<MemoryRecord> retrieve(String query, int limit) {
        List<String> keywords = keywords(query);
        if (keywords.isEmpty()) return new ArrayList<>();
        return store.searchByKeywords(keywords, limit);
    }

    public List<String> topRules(int limit) {
        return store.topRules(limit);
    }

    public static List<String> keywords(String query) {
        if (query == null) return new ArrayList<>();
        String[] raw = query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_]+");
        Set<String> uniq = new LinkedHashSet<>();
        for (String token : raw) {
            if (token == null || token.length() < 3) continue;
            uniq.add(token);
            if (uniq.size() >= 8) break;
        }
        return new ArrayList<>(uniq);
    }
}
