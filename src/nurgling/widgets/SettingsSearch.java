package nurgling.widgets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SettingsSearch {
    public static final int LIMIT = 12;

    private SettingsSearch() {}

    public static final class Entry {
        public final String category;
        public final String tab;
        public final String label;
        public final boolean tabOnly;

        Entry(String category, String tab, String label, boolean tabOnly) {
            this.category = category;
            this.tab = tab;
            this.label = label;
            this.tabOnly = tabOnly;
        }

        public String display() {
            return tabOnly ? tab : tab + " › " + label;
        }
    }

    public static final class Match {
        public final Entry entry;
        public final int score;

        Match(Entry entry, int score) {
            this.entry = entry;
            this.score = score;
        }

        public String display() {
            return entry.display();
        }
    }

    public static Entry tab(String category, String tab) {
        return new Entry(category, tab, tab, true);
    }

    public static Entry setting(String category, String tab, String label) {
        return new Entry(category, tab, label, false);
    }

    public static boolean isSearchableLabel(String text) {
        if (text == null)
            return false;
        String t = text.trim();
        if (t.length() < 2)
            return false;
        return !t.matches("[0-9.,:%\\s×x/+-]+");
    }

    public static List<Match> query(List<Entry> catalog, String raw) {
        if (catalog == null || raw == null)
            return Collections.emptyList();
        String q = raw.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty())
            return Collections.emptyList();
        String[] tokens = q.split("\\s+");
        List<Match> out = new ArrayList<>();
        for (Entry e : catalog) {
            int score = score(e, q, tokens);
            if (score > 0)
                out.add(new Match(e, score));
        }
        out.sort(Comparator
                .comparingInt((Match m) -> -m.score)
                .thenComparing(m -> m.entry.tab, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(m -> m.entry.label, String.CASE_INSENSITIVE_ORDER));
        if (out.size() > LIMIT)
            return new ArrayList<>(out.subList(0, LIMIT));
        return out;
    }

    private static int score(Entry e, String q, String[] tokens) {
        for (String token : tokens) {
            if (!matchesToken(e, token))
                return 0;
        }
        String lab = e.label.toLowerCase(Locale.ROOT);
        String tab = e.tab.toLowerCase(Locale.ROOT);
        int s = 0;
        if (lab.equals(q))
            s += 1000;
        else if (lab.startsWith(q))
            s += 500;
        else if (lab.contains(q))
            s += 200;
        else if (acronym(e.label).equals(q))
            s += 450;
        if (tab.equals(q))
            s += 400;
        else if (tab.startsWith(q))
            s += 200;
        else if (tab.contains(q))
            s += 80;
        else if (acronym(e.tab).equals(q))
            s += 350;
        if (e.tabOnly)
            s += 40;
        return Math.max(s, 1);
    }

    private static boolean matchesToken(Entry e, String token) {
        return contains(e.label, token)
                || contains(e.tab, token)
                || contains(e.category, token)
                || acronym(e.label).equals(token)
                || acronym(e.tab).equals(token)
                || compact(e.label).contains(token)
                || compact(e.tab).contains(token);
    }

    private static boolean contains(String hay, String needle) {
        return hay.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String acronym(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.toLowerCase(Locale.ROOT).split("[\\s/_-]+")) {
            if (!w.isEmpty())
                b.append(w.charAt(0));
        }
        return b.toString();
    }

    private static String compact(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
