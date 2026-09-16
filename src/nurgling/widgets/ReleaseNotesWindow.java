package nurgling.widgets;

import haven.Button;
import haven.Coord;
import haven.GOut;
import haven.Label;
import haven.RichText;
import haven.RichTextBox;
import haven.SListBox;
import haven.TextEntry;
import haven.UI;
import haven.Widget;
import haven.Window;
import nurgling.i18n.L10n;
import nurgling.news.ReleaseNotes;

import java.awt.Color;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compact reader for the release notes embedded in the client jar. */
public class ReleaseNotesWindow extends Window {
    private static final Coord SIZE = UI.scale(640, 430);
    private static final int PAD = UI.scale(10);
    private static final int LIST_WIDTH = UI.scale(180);
    private static final int LIST_HEIGHT = UI.scale(350);
    private static final Coord CONTENT_SIZE = UI.scale(420, 350);
    private static final int BUTTON_WIDTH = UI.scale(145);
    private static final RichText.Foundry CONTENT_FONT = new RichText.Foundry(
            TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, UI.scale(14f),
            TextAttribute.FOREGROUND, Color.WHITE).aa(true);

    private final TextEntry search;
    private final ReleaseList releaseList;
    private final RichTextBox content;
    private final Button detailsButton;
    private final Button previousButton;
    private final Button nextButton;
    private List<ReleaseNotes.Entry> releases = Collections.emptyList();
    private List<ReleaseNotes.Entry> filtered = Collections.emptyList();
    private ReleaseNotes.Entry selected;
    private boolean showingDetails;

    public ReleaseNotesWindow() {
        super(SIZE, L10n.get("news.window_title"));
        Label searchLabel = add(new Label(L10n.get("news.search_hint"), LIST_WIDTH), Coord.of(PAD, PAD));
        search = add(new TextEntry(LIST_WIDTH, "") {
            @Override
            protected void changed() {
                super.changed();
                applyFilter();
            }
        }, searchLabel.pos("bl").add(0, UI.scale(4)));
        int listY = search.pos("bl").y + PAD;
        int listH = Math.max(UI.scale(42), PAD + LIST_HEIGHT - listY);
        releaseList = add(new ReleaseList(Coord.of(LIST_WIDTH, listH)), Coord.of(PAD, listY));
        int contentX = PAD + LIST_WIDTH + PAD;
        content = add(new RichTextBox(CONTENT_SIZE, "", CONTENT_FONT), Coord.of(contentX, PAD));
        detailsButton = add(new Button(BUTTON_WIDTH, L10n.get("news.show_details"), this::toggleDetails),
                content.pos("bl").add(0, PAD));
        previousButton = add(new Button(UI.scale(75), L10n.get("news.previous"), () -> move(-1)),
                detailsButton.pos("ur").add(PAD, 0));
        nextButton = add(new Button(UI.scale(75), L10n.get("news.next"), () -> move(1)),
                previousButton.pos("ur").add(UI.scale(4), 0));
        add(new Button(UI.scale(75), L10n.get("common.close"), this::hide),
                nextButton.pos("ur").add(UI.scale(4), 0));
        content.bg = new Color(0, 0, 0, 150);
        detailsButton.hide();
    }

    @Override
    public void show() {
        refresh();
        if (parent != null)
            c = parent.sz.sub(sz).div(2);
        super.show();
    }

    private void refresh() {
        releases = ReleaseNotes.load();
        search.settext("");
        applyFilter();
        ReleaseNotes.markLatestSeen();
    }

    private void applyFilter() {
        if (search == null)
            return;
        String query = search.text();
        String language = L10n.getLanguage();
        List<ReleaseNotes.Entry> next = new ArrayList<ReleaseNotes.Entry>();
        for (ReleaseNotes.Entry entry : releases) {
            if (entry.matchesDetails(query, language))
                next.add(entry);
        }
        filtered = next;
        releaseList.reset();
        if (filtered.isEmpty()) {
            selected = null;
            String emptyKey = releases.isEmpty() ? "news.empty" : "news.search_empty";
            content.settext(RichText.Parser.quote(L10n.get(emptyKey)));
            detailsButton.hide();
            previousButton.hide();
            nextButton.hide();
            return;
        }
        select(filtered.get(0));
    }

    private boolean hasQuery() {
        return search != null && !search.text().trim().isEmpty();
    }

    private void select(ReleaseNotes.Entry release) {
        selected = release;
        showingDetails = hasQuery();
        releaseList.change(release);
        renderSelected();
    }

    private void toggleDetails() {
        showingDetails = !showingDetails;
        renderSelected();
    }

    private void move(int delta) {
        int current = filtered.indexOf(selected);
        int target = current + delta;
        if (target >= 0 && target < filtered.size())
            select(filtered.get(target));
    }

    private void renderSelected() {
        if (selected == null)
            return;
        String language = L10n.getLanguage();
        StringBuilder text = new StringBuilder();
        text.append("$b{").append(RichText.Parser.quote(selected.displayTitle(language))).append("}\n");
        text.append(RichText.Parser.quote(selected.id + " — " + selected.date));
        appendBullets(text, showingDetails ? selected.details(language) : selected.summary(language));
        content.settext(text.toString());
        boolean hasDetails = !selected.details(language).isEmpty();
        detailsButton.show(hasDetails);
        if (hasDetails)
            detailsButton.change(L10n.get(showingDetails ? "news.hide_details" : "news.show_details"));
        int current = filtered.indexOf(selected);
        previousButton.show(current > 0);
        nextButton.show(current >= 0 && current < filtered.size() - 1);
    }

    private static void appendBullets(StringBuilder text, List<String> lines) {
        for (String line : lines)
            text.append("\n\n• ").append(RichText.Parser.quote(line));
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if ("close".equals(msg))
            hide();
        else
            super.wdgmsg(sender, msg, args);
    }

    private final class ReleaseList extends SListBox<ReleaseNotes.Entry, Widget> {
        private ReleaseList(Coord size) {
            super(size, UI.scale(42));
        }

        @Override
        protected List<ReleaseNotes.Entry> items() {
            return filtered;
        }

        @Override
        protected Widget makeitem(final ReleaseNotes.Entry item, int index, Coord size) {
            return new Widget(size) {
                @Override
                public void draw(GOut g) {
                    g.text(item.id, Coord.of(UI.scale(5), UI.scale(4)));
                    g.chcolor(Color.LIGHT_GRAY);
                    g.text(item.date, Coord.of(UI.scale(5), UI.scale(21)));
                    g.chcolor();
                }
            };
        }

        @Override
        protected boolean slotclick(Coord c, int slot, int button) {
            if (button != 1 || slot < 0 || slot >= filtered.size())
                return false;
            select(filtered.get(slot));
            return true;
        }
    }
}
