package nurgling.craftatlas;

import nurgling.NConfig;
import nurgling.tools.NFileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small profile-local state store for the Atlas. */
public final class CraftAtlasPreferences {
    public static final int RECENT_LIMIT = 50;
    public static final int SEARCH_HISTORY_LIMIT = 12;
    public final Set<String> favorites = new LinkedHashSet<>();
    public final List<String> recent = new ArrayList<>();
    public final List<String> searchHistory = new ArrayList<>();
    public final Map<String, Integer> columnWidths = new LinkedHashMap<>();
    public String lastSection = "all";
    public boolean favoriteFilter, recentFilter;
    public int windowX = -1, windowY = -1, windowW = -1, windowH = -1;

    public void recordRecent(String recipeResource) {
        if(recipeResource == null || recipeResource.isEmpty()) return;
        recent.remove(recipeResource);
        recent.add(0, recipeResource);
        while(recent.size() > RECENT_LIMIT) recent.remove(recent.size() - 1);
    }

    public void recordSearch(String query) {
        String value = query == null ? "" : query.trim();
        if(value.isEmpty()) return;
        searchHistory.remove(value);
        searchHistory.add(0, value);
        while(searchHistory.size() > SEARCH_HISTORY_LIMIT) searchHistory.remove(searchHistory.size() - 1);
    }

    public static Path profilePath() {
        String path = NConfig.current == null
                ? "craft_atlas.nurgling.json"
                : NConfig.current.getProfileAwarePath("craft_atlas.nurgling.json");
        return Paths.get(path);
    }

    public static CraftAtlasPreferences loadProfile() { return load(profilePath()); }
    public void saveProfile() throws IOException { save(profilePath()); }

    public static CraftAtlasPreferences load(Path file) {
        CraftAtlasPreferences prefs = new CraftAtlasPreferences();
        if(file == null || !Files.isRegularFile(file)) return prefs;
        try {
            JSONObject root = new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            readStrings(root.optJSONArray("favorites"), prefs.favorites);
            readStrings(root.optJSONArray("recent"), prefs.recent);
            readStrings(root.optJSONArray("searchHistory"), prefs.searchHistory);
            while(prefs.recent.size() > RECENT_LIMIT) prefs.recent.remove(prefs.recent.size() - 1);
            while(prefs.searchHistory.size() > SEARCH_HISTORY_LIMIT)
                prefs.searchHistory.remove(prefs.searchHistory.size() - 1);
            prefs.lastSection = root.optString("lastSection", prefs.lastSection);
            prefs.favoriteFilter = root.optBoolean("favoriteFilter", false);
            prefs.recentFilter = root.optBoolean("recentFilter", false);
            JSONObject window = root.optJSONObject("window");
            if(window != null) {
                prefs.windowX = window.optInt("x", prefs.windowX);
                prefs.windowY = window.optInt("y", prefs.windowY);
                prefs.windowW = window.optInt("w", prefs.windowW);
                prefs.windowH = window.optInt("h", prefs.windowH);
            }
            JSONObject columns = root.optJSONObject("columns");
            if(columns != null) for(String key : columns.keySet()) prefs.columnWidths.put(key, columns.optInt(key));
        } catch(Exception ignored) {
            return new CraftAtlasPreferences();
        }
        return prefs;
    }

    private static void readStrings(JSONArray array, java.util.Collection<String> target) {
        if(array == null) return;
        for(int i = 0; i < array.length(); i++) {
            String value = array.optString(i, null);
            if(value != null && !value.isEmpty()) target.add(value);
        }
    }

    public void save(Path file) throws IOException {
        JSONObject root = new JSONObject();
        root.put("favorites", new JSONArray(favorites));
        root.put("recent", new JSONArray(recent));
        root.put("searchHistory", new JSONArray(searchHistory));
        root.put("lastSection", lastSection);
        root.put("favoriteFilter", favoriteFilter);
        root.put("recentFilter", recentFilter);
        root.put("window", new JSONObject().put("x", windowX).put("y", windowY).put("w", windowW).put("h", windowH));
        root.put("columns", new JSONObject(columnWidths));
        NFileUtils.writeAtomically(file.toString(), root.toString(2));
    }
}
