package nurgling.widgets;

import nurgling.tools.NFuzzy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class SettingsSearch {
    public static final int LIMIT = 12;

    private SettingsSearch() {}

    public static final class Entry {
        public final String category;
        public final String tab;
        public final String label;
        public final boolean tabOnly;
        public final List<String> keywords;

        Entry(String category, String tab, String label, boolean tabOnly, String... keywords) {
            this.category = category;
            this.tab = tab;
            this.label = label;
            this.tabOnly = tabOnly;
            this.keywords = Collections.unmodifiableList(Arrays.asList(keywords.clone()));
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

    public static Entry tab(String category, String tab, String... keywords) {
        return new Entry(category, tab, tab, true, keywords);
    }

    public static Entry setting(String category, String tab, String label, String... keywords) {
        return new Entry(category, tab, label, false, keywords);
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
        String q = NFuzzy.fold(raw).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
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
        String lab = NFuzzy.fold(e.label);
        String tab = NFuzzy.fold(e.tab);
        int s = 0;
        if (lab.equals(q))
            s += 3000;
        else if (lab.startsWith(q))
            s += 2200;
        else if (lab.contains(q))
            s += 1500;
        else if (acronym(e.label).equals(q))
            s += 1800;
        if (tab.equals(q))
            s += 1000;
        else if (tab.startsWith(q))
            s += 700;
        else if (tab.contains(q))
            s += 400;
        else if (acronym(e.tab).equals(q))
            s += 650;
        for(String token : tokens)
            s += tokenScore(e, token);
        if (e.tabOnly)
            s += 40;
        return s;
    }

    private static boolean matchesToken(Entry e, String token) {
        return tokenScore(e, token) > 0;
    }

    private static int tokenScore(Entry e, String token) {
        int best = Math.max(fieldScore(e.label, token),
                Math.max((int)(fieldScore(e.tab, token) * 0.7),
                         (int)(fieldScore(e.category, token) * 0.4)));
        for(String keyword : e.keywords)
            best = Math.max(best, (int)(fieldScore(keyword, token) * 0.75));
        return best;
    }

    private static int fieldScore(String text, String token) {
        String hay = NFuzzy.fold(text);
        if(hay.isEmpty())
            return 0;
        if(acronym(text).equals(token))
            return 720;
        if(compact(text).contains(token) && !hay.contains(token))
            return 520;
        // One or two scattered letters create far too many unrelated results.
        if(token.length() < 3 && !hay.contains(token))
            return 0;
        double score = NFuzzy.score(hay, token);
        return score == NFuzzy.NO_MATCH ? 0 : Math.max(1, (int)score);
    }

    private static String acronym(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : NFuzzy.fold(s).split("[\\s/_-]+")) {
            if (!w.isEmpty())
                b.append(w.charAt(0));
        }
        return b.toString();
    }

    private static String compact(String s) {
        return NFuzzy.fold(s).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
