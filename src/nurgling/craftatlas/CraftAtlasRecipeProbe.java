package nurgling.craftatlas;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Serializes hidden recipe requests and remembers recipes refreshed in this client session. */
public final class CraftAtlasRecipeProbe {
    public static final long SETTLE_NANOS = 150_000_000L;

    public static final class Claim {
        public final String recipeResource;

        private Claim(String recipeResource) {
            this.recipeResource = recipeResource;
        }
    }

    private final Set<String> completed = new HashSet<>();
    private String pending;
    private String pendingWindowName;
    private boolean claimed;

    public static boolean readyToPublish(long now, long lastServerUpdate) {
        return now - lastServerUpdate >= SETTLE_NANOS;
    }

    public synchronized boolean request(String recipeResource, String windowName, Runnable action) {
        String normalizedWindowName = normalize(windowName);
        if(recipeResource == null || recipeResource.isEmpty() || normalizedWindowName.isEmpty() || action == null ||
                completed.contains(recipeResource) || pending != null) return false;
        pending = recipeResource;
        pendingWindowName = normalizedWindowName;
        claimed = false;
        try {
            action.run();
            return true;
        } catch(RuntimeException error) {
            pending = null;
            pendingWindowName = null;
            throw error;
        }
    }

    public synchronized Claim claim(String windowName) {
        String normalizedWindowName = normalize(windowName);
        if(pending == null || claimed || !pendingWindowName.equals(normalizedWindowName)) return null;
        claimed = true;
        return new Claim(pending);
    }

    public synchronized void complete(String recipeResource) {
        if(recipeResource != null) completed.add(recipeResource);
        if(recipeResource == null || recipeResource.equals(pending)) {
            pending = null;
            pendingWindowName = null;
            claimed = false;
        }
    }

    public synchronized void fail(String recipeResource) {
        if(recipeResource == null || recipeResource.equals(pending)) {
            pending = null;
            pendingWindowName = null;
            claimed = false;
        }
    }

    public synchronized void cancel(String recipeResource) {
        fail(recipeResource);
    }

    private static String normalize(String value) {
        if(value == null) return "";
        StringBuilder normalized = new StringBuilder();
        for(int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if(Character.isLetterOrDigit(codePoint)) normalized.appendCodePoint(Character.toLowerCase(codePoint));
            offset += Character.charCount(codePoint);
        }
        return normalized.toString().toLowerCase(Locale.ROOT);
    }
}
