package nurgling;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Where this fork publishes client updates. The popup on the login screen
 * and {@code nurgling_launcher} both read from these URLs.
 */
public final class NUpdateFeed {
    public static final String RELEASE_REPO = "Lanfir7/nurgling-release";
    public static final String SOURCE_REPO_URL = "https://github.com/Lanfir7/nurgling2";
    public static final String STABLE_DIR = "https://raw.githubusercontent.com/Lanfir7/nurgling-release/stable/";
    public static final String LATEST_DIR = "https://raw.githubusercontent.com/Lanfir7/nurgling-release/latest/";
    public static final String SOURCE_RELEASE_DIR = "https://raw.githubusercontent.com/Lanfir7/nurgling2/master/release/";
    public static final String DEFAULT_BASEURL = SOURCE_RELEASE_DIR + "ver";

    private NUpdateFeed() {}

    /** Rewrite saved config URLs that still point at upstream or stale feeds. */
    public static String migrateBaseUrl(String current) {
        if (current == null || current.trim().isEmpty()) {
            return DEFAULT_BASEURL;
        }
        try {
            URI uri = new URI(current.trim());
            if (!"raw.githubusercontent.com".equalsIgnoreCase(uri.getHost()) ||
                    !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) ||
                    uri.getUserInfo() != null || uri.getPort() != -1 ||
                    uri.getRawQuery() != null || uri.getRawFragment() != null)
                return current;
            String path = uri.getRawPath().toLowerCase(Locale.ROOT);
            if (path.matches("/(lanfir7|katodiy|aleksandrsvoboda)/nurgling2/(master|next)/(release/)?ver") ||
                    path.matches("/(lanfir7|katodiy|aleksandrsvoboda)/nurgling-release/(latest|stable)/ver"))
                return DEFAULT_BASEURL;
        } catch (URISyntaxException ignored) {
            // Preserve custom feeds; changing a branch must not rewrite unrelated settings.
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
