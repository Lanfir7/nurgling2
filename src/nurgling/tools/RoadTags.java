package nurgling.tools;

import haven.Gob;
import nurgling.NConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/** Private road labels stored separately from routes and milestone discovery. */
public final class RoadTags {
    private static final String POSTS = "posts";
    public static final int MAX_LENGTH = 40;

    private RoadTags() {}

    public static String postKey(Gob gob) {
        if (gob == null || gob.ngob == null || gob.ngob.gcoord == null || gob.ngob.grid_id == 0)
            return null;
        return postKey(gob.ngob.grid_id, gob.ngob.gcoord.x, gob.ngob.gcoord.y);
    }

    public static String postKey(long gridId, int x, int y) {
        return gridId + ":" + x + "," + y;
    }

    /** Length-prefixed name plus ordinal avoids collisions with literal names containing # or digits. */
    public static String roadKey(String name, int occurrence) {
        if (name == null || occurrence < 0)
            throw new IllegalArgumentException("road name and nonnegative occurrence required");
        return name.length() + ":" + name + ":" + occurrence;
    }

    private static Map<String, Object> stored() {
        try {
            return NConfig.getAsMap(NConfig.Key.roadTags);
        } catch (RuntimeException malformed) {
            return new LinkedHashMap<>();
        }
    }

    public static String get(String postKey, String roadKey) {
        return get(stored(), postKey, roadKey);
    }

    public static void set(String postKey, String roadKey, String tag) {
        if (postKey == null || roadKey == null) return;
        NConfig.set(NConfig.Key.roadTags, set(stored(), postKey, roadKey, tag));
    }

    static String get(Map<?, ?> root, String postKey, String roadKey) {
        if (root == null || postKey == null || roadKey == null) return null;
        Object posts = root.get(POSTS);
        if (!(posts instanceof Map)) return null;
        Object post = ((Map<?, ?>) posts).get(postKey);
        if (!(post instanceof Map)) return null;
        Object tag = ((Map<?, ?>) post).get(roadKey);
        return tag instanceof String ? (String) tag : null;
    }

    /** Copy-on-write: callers and old config snapshots retain their original nested maps. */
    static Map<String, Object> set(Map<?, ?> root, String postKey, String roadKey, String tag) {
        Map<String, Object> out = stringKeys(root);
        Map<String, Object> posts = stringKeys(out.get(POSTS));
        Map<String, Object> post = stringKeys(posts.get(postKey));
        String clean = tag == null ? "" : tag.trim();
        if (clean.length() > MAX_LENGTH)
            clean = clean.substring(0, MAX_LENGTH);
        if (clean.isEmpty()) post.remove(roadKey);
        else post.put(roadKey, clean);
        if (post.isEmpty()) posts.remove(postKey);
        else posts.put(postKey, post);
        out.put(POSTS, posts);
        return out;
    }

    private static Map<String, Object> stringKeys(Object value) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (value instanceof Map)
            ((Map<?, ?>) value).forEach((key, entry) -> {
                if (key instanceof String) copy.put((String) key, entry);
            });
        return copy;
    }
}
