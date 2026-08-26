package nurgling;

/**
 * Where this fork publishes client updates. The popup on the login screen
 * and {@code nurgling_launcher} both read from these URLs.
 */
public final class NUpdateFeed {
    public static final String RELEASE_REPO = "Lanfir7/nurgling-release";
    public static final String SOURCE_REPO_URL = "https://github.com/Lanfir7/nurgling2";
    public static final String STABLE_DIR = "https://raw.githubusercontent.com/Lanfir7/nurgling-release/stable/";
    public static final String LATEST_DIR = "https://raw.githubusercontent.com/Lanfir7/nurgling-release/latest/";
    public static final String SOURCE_RELEASE_DIR = "https://raw.githubusercontent.com/Lanfir7/nurgling2/next/release/";
    public static final String DEFAULT_BASEURL = SOURCE_RELEASE_DIR + "ver";

    private NUpdateFeed() {}

    /** Rewrite saved config URLs that still point at upstream or stale feeds. */
    public static String migrateBaseUrl(String current) {
        if (current == null || current.trim().isEmpty()) {
            return DEFAULT_BASEURL;
        }
        String lower = current.toLowerCase();
        if (lower.contains("katodiy") || lower.contains("aleksandrsvoboda")) {
            return DEFAULT_BASEURL;
        }
        if (lower.contains("lanfir7/nurgling-release")) {
            return DEFAULT_BASEURL;
        }
        if (lower.contains("lanfir7/nurgling2") && !lower.contains("/next/release")) {
            return DEFAULT_BASEURL;
        }
        return current;
    }

    public static boolean needsUpdate(String localVersion, String remoteVersion) {
        if (localVersion == null || remoteVersion == null) {
            return false;
        }
        return !localVersion.trim().equals(remoteVersion.trim());
    }
}
