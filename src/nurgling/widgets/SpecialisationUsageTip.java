package nurgling.widgets;

import haven.RichText;

import java.util.ArrayList;
import java.util.List;

final class SpecialisationUsageTip {
    static final int BOT_LIMIT = 15;

    private SpecialisationUsageTip() {
    }

    static String build(String title, List<String> bots, String unusedText, String usedByText, String moreText) {
        List<String> lines = new ArrayList<>();
        lines.add("$b{" + RichText.Parser.quote(title) + "}");
        if(bots.isEmpty()) {
            lines.add("$i{" + RichText.Parser.quote(unusedText) + "}");
        } else {
            lines.add(RichText.Parser.quote(usedByText));
            int shown = Math.min(bots.size(), BOT_LIMIT);
            for(int index = 0; index < shown; index++)
                lines.add("• " + RichText.Parser.quote(bots.get(index)));
            if(bots.size() > shown)
                lines.add("$i{" + RichText.Parser.quote(moreText) + "}");
        }
        return String.join("\n", lines);
    }
}
