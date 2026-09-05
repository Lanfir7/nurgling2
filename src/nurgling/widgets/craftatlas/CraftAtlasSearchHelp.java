package nurgling.widgets.craftatlas;

import haven.Button;
import haven.Coord;
import haven.Label;
import haven.UI;
import haven.Widget;
import haven.Window;
import nurgling.craftatlas.CraftAtlasSearch;
import nurgling.i18n.L10n;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Structured, clickable reference for Atlas extended queries. */
final class CraftAtlasSearchHelp extends Window {
    private final Runnable closed;
    private boolean closing;

    CraftAtlasSearchHelp(String activeSection, Consumer<String> apply, Runnable closed) {
        super(UI.scale(620, 620), L10n.get("craft_atlas.search_help.title"));
        this.closed = closed;
        int margin = UI.scale(14);
        int top = margin;
        Label intro = add(new Label(L10n.get("craft_atlas.search_help.intro")), Coord.of(margin, top));
        intro.setcolor(new Color(208, 214, 211));
        top += UI.scale(34);

        int[] y = {top, top};
        int columnWidth = UI.scale(292);
        int buttonWidth = UI.scale(280);
        for(String section : orderedSections(activeSection)) {
            int column = y[0] <= y[1] ? 0 : 1;
            int x = margin + column * columnWidth;
            Label heading = add(new Label(L10n.get("craft_atlas.search_help." + section)), Coord.of(x, y[column]));
            heading.setcolor(new Color(221, 174, 76));
            y[column] += UI.scale(22);
            String exampleSection = "common".equals(section) ? "all" : section;
            for(String example : CraftAtlasSearch.examplesFor(exampleSection)) {
                add(new Button(buttonWidth, "→  " + example).action(() -> apply.accept(example)),
                        Coord.of(x, y[column]));
                y[column] += UI.scale(31);
            }
            y[column] += UI.scale(9);
        }
        resize(Coord.of(UI.scale(620), Math.max(UI.scale(300), Math.max(y[0], y[1]) + margin)));
    }

    private static List<String> orderedSections(String active) {
        List<String> result = new ArrayList<>();
        result.add("common");
        String normalized = active != null && active.startsWith("equipment") ? "equipment" : active;
        if(List.of("foods", "gildings", "curiosities", "equipment").contains(normalized)) result.add(normalized);
        for(String section : List.of("foods", "gildings", "curiosities", "equipment"))
            if(!result.contains(section)) result.add(section);
        return result;
    }

    private void close() {
        if(closing) return;
        closing = true;
        if(closed != null) closed.run();
        reqdestroy();
    }

    @Override public void wdgmsg(Widget sender, String msg, Object... args) {
        if(sender == this && "close".equals(msg)) { close(); return; }
        super.wdgmsg(sender, msg, args);
    }
}
