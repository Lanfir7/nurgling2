package nurgling;

import java.util.ArrayList;
import java.util.List;

/** Converts the server's colored credo bonus blocks into progress-aware lines. */
public final class CredoBonusFormatter {
    private static final String DONE_COLOR = "96,232,96";
    private static final String TODO_COLOR = "255,218,64";

    private CredoBonusFormatter() {
    }

    public static int completedBonuses(int displayedLevel) {
        return Math.max(0, displayedLevel - 1);
    }

    /** True when the selected credo is the one currently being pursued, matched by name. */
    public static boolean isPursuing(String selectedName, String pursuedName) {
        return selectedName != null && selectedName.equals(pursuedName);
    }

    public static String format(String markup, int completedLevels, boolean acquired) {
        if(markup == null || markup.trim().isEmpty())
            return "";

        String body = stripColorMarkup(markup.trim());
        List<String> bonuses = new ArrayList<>();
        for(String raw : body.split("\\r?\\n")) {
            String line = raw.trim();
            if(line.isEmpty())
                continue;
            if(line.startsWith("*") || line.startsWith("•") || line.startsWith("✓"))
                line = line.substring(1).trim();
            if(!line.isEmpty())
                bonuses.add(line);
        }

        StringBuilder out = new StringBuilder();
        int done = Math.max(0, completedLevels);
        for(int i = 0; i < bonuses.size(); i++) {
            if(out.length() > 0)
                out.append('\n');
            boolean completed = acquired || i < done;
            out.append("$col[")
               .append(completed ? DONE_COLOR : TODO_COLOR)
               .append("]{")
               .append(completed ? CredoBonusIcon.MARKUP + " " : "• ")
               .append(bonuses.get(i))
               .append('}');
        }
        return out.toString();
    }

    private static String stripColorMarkup(String markup) {
        StringBuilder out = new StringBuilder(markup.length());
        appendWithoutColors(markup, 0, markup.length(), out);
        return out.toString();
    }

    private static void appendWithoutColors(String markup, int start, int end, StringBuilder out) {
        int i = start;
        while(i < end) {
            if(markup.startsWith("$col[", i)) {
                int open = markup.indexOf('{', i + 5);
                int close = (open < 0 || open >= end) ? -1 : matchingBrace(markup, open, end);
                if(close < 0) {
                    out.append(markup, i, end);
                    return;
                }
                appendWithoutColors(markup, open + 1, close, out);
                if(close + 1 < end && markup.startsWith("$col[", close + 1)
                    && out.length() > 0 && out.charAt(out.length() - 1) != '\n')
                    out.append('\n');
                i = close + 1;
            } else {
                out.append(markup.charAt(i++));
            }
        }
    }

    private static int matchingBrace(String markup, int open, int end) {
        int depth = 0;
        for(int i = open; i < end; i++) {
            char c = markup.charAt(i);
            if(c == '{')
                depth++;
            else if(c == '}' && --depth == 0)
                return i;
        }
        return -1;
    }
}
