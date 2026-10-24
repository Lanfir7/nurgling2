package nurgling.tools;

import java.util.Locale;

/** Positional matching for short setting names and localization keys. */
public final class NFuzzy {
    public static final double NO_MATCH = Double.NEGATIVE_INFINITY;

    private NFuzzy() {}

    public static String fold(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    /** Scores already folded strings, rewarding contiguous and early matches. */
    public static double score(String haystack, String needle) {
        if(needle.isEmpty())
            return 0;
        if(haystack.isEmpty())
            return NO_MATCH;
        if(haystack.equals(needle))
            return 1000;

        int at = haystack.indexOf(needle);
        if(at >= 0) {
            double score = 400;
            if(at == 0 || boundary(haystack.charAt(at - 1)))
                score += 150;
            return score - at * 2.0 - (haystack.length() - needle.length()) * 0.5;
        }

        double score = 100;
        int from = 0, previous = -2, first = -1;
        for(int i = 0; i < needle.length(); i++) {
            at = haystack.indexOf(needle.charAt(i), from);
            if(at < 0)
                return NO_MATCH;
            if(first < 0)
                first = at;
            if(at == previous + 1)
                score += 8;
            else if(at == 0 || boundary(haystack.charAt(at - 1)))
                score += 5;
            previous = at;
            from = at + 1;
        }
        return score - first * 1.5 - (haystack.length() - needle.length()) * 0.3;
    }

    private static boolean boundary(char ch) {
        return !Character.isLetterOrDigit(ch);
    }
}
