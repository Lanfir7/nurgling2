package nurgling.widgets.craftatlas;

import haven.Button;
import haven.Coord;
import haven.Label;
import haven.RichText;
import haven.RichTextBox;
import haven.Scrollport;
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
        super(UI.scale(860, 700), L10n.get("craft_atlas.search_help.title"));
        this.closed = closed;
        int margin = UI.scale(14);
        int top = margin;
        Label intro = add(new Label(L10n.get("craft_atlas.search_help.intro")), Coord.of(margin, top));
        intro.setcolor(new Color(208, 214, 211));
        top += UI.scale(34);

        int referenceWidth = UI.scale(400);
        RichTextBox reference = add(new RichTextBox(
                Coord.of(referenceWidth, UI.scale(640)),
                L10n.get("craft_atlas.search_help.reference"), RichText.stdf),
                Coord.of(margin, top));
        reference.bg = new Color(10, 15, 17, 220);

        int x = margin + referenceWidth + UI.scale(14);
        Scrollport examples = add(new Scrollport(Coord.of(UI.scale(414), UI.scale(640))), Coord.of(x, top));
        int y = 0;
        int buttonWidth = UI.scale(380);
        for(String section : orderedSections(activeSection)) {
            Label heading = examples.cont.add(new Label(L10n.get("craft_atlas.search_help." + section)), Coord.of(0, y));
            heading.setcolor(new Color(221, 174, 76));
            y += UI.scale(22);
            String exampleSection = "common".equals(section) ? "all" : section;
            for(String example : CraftAtlasSearch.examplesFor(exampleSection)) {
                examples.cont.add(new Button(buttonWidth, "→  " + example).action(() -> apply.accept(example)),
                        Coord.of(0, y));
                y += UI.scale(31);
            }
            y += UI.scale(9);
        }
        resize(UI.scale(860, 700));
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
