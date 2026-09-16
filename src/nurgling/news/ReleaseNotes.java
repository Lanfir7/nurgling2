package nurgling.news;

import haven.Utils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Local, packaged release notes. The resource is read once, only when the UI asks for it. */
public final class ReleaseNotes {
    public static final String RESOURCE = "/nurgling/news/releases.json";
    public static final String SEEN_PREF = "nurgling/news/seen-release-id";
    public static final int SUMMARY_LIMIT = 3;

    private static volatile List<Entry> cached;
    private static volatile String seenId;
    private static final PreferenceStore UTILS_PREFERENCES = new PreferenceStore() {
        @Override
        public String get(String key, String fallback) {
            return Utils.getpref(key, fallback);
        }

        @Override
        public void put(String key, String value) {
            Utils.setpref(key, value);
        }
    };
    private static volatile PreferenceStore preferences = UTILS_PREFERENCES;

    private ReleaseNotes() {
    }

    public static List<Entry> load() {
        List<Entry> result = cached;
        if (result != null)
            return result;
        synchronized (ReleaseNotes.class) {
            if (cached == null)
                cached = loadResource();
            return cached;
        }
    }

    private static List<Entry> loadResource() {
        try (InputStream input = ReleaseNotes.class.getResourceAsStream(RESOURCE)) {
            if (input == null)
                return Collections.emptyList();
            return parse(read(input));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int read; (read = input.read(buffer)) >= 0; )
            output.write(buffer, 0, read);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Parses only the stable packaged-data contract; invalid entries are ignored. */
    public static List<Entry> parse(String json) {
        if (json == null || json.trim().isEmpty())
            return Collections.emptyList();
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("schema", -1) != 1)
                return Collections.emptyList();
            JSONArray releases = root.optJSONArray("releases");
            if (releases == null)
                return Collections.emptyList();
            List<Entry> result = new ArrayList<>();
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                Entry entry = Entry.from(release);
                if (entry != null)
                    result.add(entry);
            }
            return Collections.unmodifiableList(result);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    public static boolean hasUnread() {
        Entry latest = latest();
        return latest != null && isUnread(latest.id);
    }

    public static void markLatestSeen() {
        Entry latest = latest();
        if (latest != null)
            markSeen(latest.id);
    }

    public static Entry latest() {
        List<Entry> releases = load();
        return releases.isEmpty() ? null : releases.get(0);
    }

    private static String seenId() {
        String result = seenId;
        if (result != null)
            return result;
        synchronized (ReleaseNotes.class) {
            if (seenId == null)
                seenId = preferences.get(SEEN_PREF, "");
            return seenId;
        }
    }

    static boolean isUnread(String latestId) {
        return latestId != null && !latestId.isEmpty() && !latestId.equals(seenId());
    }

    static void markSeen(String id) {
        if (id == null || id.isEmpty())
            return;
        preferences.put(SEEN_PREF, id);
        seenId = id;
    }

    interface PreferenceStore {
        String get(String key, String fallback);
        void put(String key, String value);
    }

    static void setPreferenceStoreForTests(PreferenceStore store) {
        preferences = store == null ? UTILS_PREFERENCES : store;
        seenId = null;
    }

    public static final class Entry {
        public final String id;
        public final String date;
        public final Localized title;
        public final LocalizedLines summary;
        public final LocalizedLines details;

        private Entry(String id, String date, Localized title, LocalizedLines summary, LocalizedLines details) {
            this.id = id;
            this.date = date;
            this.title = title;
            this.summary = summary;
            this.details = details;
        }

        private static Entry from(JSONObject json) {
            if (json == null)
                return null;
            String id = text(json.optString("id", ""));
            String date = text(json.optString("date", ""));
            Localized title = Localized.from(json.optJSONObject("title"));
            LocalizedLines summary = LocalizedLines.from(json.optJSONObject("summary"));
            LocalizedLines details = LocalizedLines.from(json.optJSONObject("details"));
            if (id.isEmpty() || date.isEmpty() || title.isEmpty())
                return null;
            return new Entry(id, date, title, summary, details);
        }

        public String displayTitle(String language) {
            return title.forLanguage(language);
        }

        public List<String> summary(String language) {
            return summary.forLanguage(language, SUMMARY_LIMIT);
        }

        public List<String> details(String language) {
            return details.forLanguage(language, Integer.MAX_VALUE);
        }

        public boolean matchesDetails(String query, String language) {
            if (query == null || query.trim().isEmpty())
                return true;
            String needle = query.trim().toLowerCase(Locale.ROOT);
            for (String line : details(language)) {
                if (line.toLowerCase(Locale.ROOT).contains(needle))
                    return true;
            }
            return false;
        }
    }

    public static final class Localized {
        public final String ru;
        public final String en;

        private Localized(String ru, String en) {
            this.ru = ru;
            this.en = en;
        }

        private static Localized from(JSONObject json) {
            if (json == null)
                return new Localized("", "");
            return new Localized(text(json.optString("ru", "")), text(json.optString("en", "")));
        }

        private boolean isEmpty() {
            return ru.isEmpty() && en.isEmpty();
        }

        public String forLanguage(String language) {
            if ("ru".equalsIgnoreCase(language) && !ru.isEmpty())
                return ru;
            return !en.isEmpty() ? en : ru;
        }
    }

    public static final class LocalizedLines {
        public final List<String> ru;
        public final List<String> en;

        private LocalizedLines(List<String> ru, List<String> en) {
            this.ru = ru;
            this.en = en;
        }

        private static LocalizedLines from(JSONObject json) {
            if (json == null)
                return new LocalizedLines(Collections.emptyList(), Collections.emptyList());
            return new LocalizedLines(lines(json.optJSONArray("ru")), lines(json.optJSONArray("en")));
        }

        public List<String> forLanguage(String language, int limit) {
            List<String> preferred = "ru".equalsIgnoreCase(language) && !ru.isEmpty() ? ru : (!en.isEmpty() ? en : ru);
            return preferred.size() <= limit ? preferred : preferred.subList(0, limit);
        }
    }

    private static List<String> lines(JSONArray lines) {
        if (lines == null || lines.length() == 0)
            return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < lines.length(); i++) {
            String line = text(lines.optString(i, ""));
            if (!line.isEmpty())
                result.add(line);
        }
        return Collections.unmodifiableList(result);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
