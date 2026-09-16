# What's New Details Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Players can type a phrase from expanded release details in What's new and see only matching versions, already expanded.

**Architecture:** Matching lives on `ReleaseNotes.Entry.matchesDetails`. The window keeps the full release list plus a filtered `visible` list; the left list and Previous/Next read `visible`. Search uses a `Label` + `TextEntry` above the list, not cookbook `HintTextEntry`.

**Tech Stack:** Java 8, Haven widgets, JUnit 5 via `ant test`, L10n properties, player `changes/*.json`.

## Global Constraints

- Search looks only at details text for the current UI language (same fallback as `details(language)`).
- Version id, date, title and summary never match.
- Do not reuse cookbook `HintTextEntry`.
- Window size and the right-hand body stay as they are; only the left list shrinks to fit search.
- Opening the window resets the query; the query is not saved.
- Player copy is bilingual, no class names or implementation details.
- Commit only the files named in that task; leave unrelated dirty work alone.

## File structure

- `src/nurgling/news/ReleaseNotes.java` — `Entry.matchesDetails(String query, String language)`
- `test/nurgling/news/ReleaseNotesTest.java` — matching tests
- `src/nurgling/widgets/ReleaseNotesWindow.java` — search field, filtered list, empty state
- `src/lang/messages.properties` / `src/lang/messages_ru.properties` — `news.search_hint`, `news.search_empty`
- `changes/2026-09-17-news-details-search.json` — player note

---

### Task 1: Details matching

**Files:**
- Modify: `src/nurgling/news/ReleaseNotes.java`
- Test: `test/nurgling/news/ReleaseNotesTest.java`

**Interfaces:**
- Consumes: `Entry.details(String language)` (existing)
- Produces: `public boolean matchesDetails(String query, String language)` on `ReleaseNotes.Entry` — `true` when query is null/blank/whitespace, or any details line contains the trimmed query, case-insensitive via `Locale.ROOT`

- [ ] **Step 1: Write the failing tests**

Add this helper and these tests to `test/nurgling/news/ReleaseNotesTest.java` (keep existing tests):

```java
    @Test
    void matchesDetailsIgnoresCaseAndFindsSubstring() {
        ReleaseNotes.Entry entry = parseOne("{"
                + "\"id\":\"2.1.0\",\"date\":\"2026-09-13\","
                + "\"title\":{\"en\":\"Title\"},"
                + "\"summary\":{\"en\":[\"Summary\"]},"
                + "\"details\":{\"en\":[\"Cart carrier waits at the dock\"]}}");
        assertTrue(entry.matchesDetails("CART", "en"));
        assertTrue(entry.matchesDetails("carrier waits", "en"));
        assertFalse(entry.matchesDetails("missing", "en"));
    }

    @Test
    void blankDetailsQueryMatchesEveryEntry() {
        ReleaseNotes.Entry entry = parseOne("{"
                + "\"id\":\"2.1.0\",\"date\":\"2026-09-13\","
                + "\"title\":{\"en\":\"Title\"},"
                + "\"summary\":{\"en\":[\"Summary\"]},"
                + "\"details\":{\"en\":[\"Cart\"]}}");
        assertTrue(entry.matchesDetails("", "en"));
        assertTrue(entry.matchesDetails("   ", "en"));
        assertTrue(entry.matchesDetails(null, "en"));
    }

    @Test
    void matchesDetailsIgnoresSummaryTitleIdAndDate() {
        ReleaseNotes.Entry entry = parseOne("{"
                + "\"id\":\"needle-id\",\"date\":\"needle-date\","
                + "\"title\":{\"en\":\"needle-title\"},"
                + "\"summary\":{\"en\":[\"needle-summary\"]},"
                + "\"details\":{\"en\":[\"unrelated\"]}}");
        assertFalse(entry.matchesDetails("needle", "en"));
    }

    @Test
    void matchesDetailsUsesLanguageFallback() {
        ReleaseNotes.Entry both = parseOne("{"
                + "\"id\":\"1\",\"date\":\"2026-09-13\","
                + "\"title\":{\"ru\":\"Заголовок\",\"en\":\"Title\"},"
                + "\"summary\":{},"
                + "\"details\":{\"ru\":[\"телега\"],\"en\":[\"cart\"]}}");
        assertTrue(both.matchesDetails("телега", "ru"));
        assertFalse(both.matchesDetails("cart", "ru"));
        assertTrue(both.matchesDetails("cart", "en"));

        ReleaseNotes.Entry enOnly = parseOne("{"
                + "\"id\":\"2\",\"date\":\"2026-09-13\","
                + "\"title\":{\"en\":\"Title\"},"
                + "\"summary\":{},"
                + "\"details\":{\"ru\":[],\"en\":[\"cart\"]}}");
        assertTrue(enOnly.matchesDetails("cart", "ru"));
    }

    private static ReleaseNotes.Entry parseOne(String jsonObject) {
        List<ReleaseNotes.Entry> entries = ReleaseNotes.parse("{\"schema\":1,\"releases\":[" + jsonObject + "]}");
        assertEquals(1, entries.size());
        return entries.get(0);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `ant test`

Expected: FAIL — `matchesDetails` not found (`cannot find symbol`) while compiling `ReleaseNotesTest`.

- [ ] **Step 3: Write minimal implementation**

In `src/nurgling/news/ReleaseNotes.java`:

1. Add `import java.util.Locale;`
2. Add this method on `Entry` after `details(String language)`:

```java
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `ant test`

Expected: PASS, including the four new `ReleaseNotesTest` methods.

- [ ] **Step 5: Commit**

```bash
git add src/nurgling/news/ReleaseNotes.java test/nurgling/news/ReleaseNotesTest.java
git commit -m "feat: match What's new releases by details text"
```

---

### Task 2: Search field and filtered list

**Files:**
- Modify: `src/nurgling/widgets/ReleaseNotesWindow.java`
- Modify: `src/lang/messages.properties`
- Modify: `src/lang/messages_ru.properties`

**Interfaces:**
- Consumes: `ReleaseNotes.Entry.matchesDetails(String query, String language)`
- Produces: live-filtered `visible` list in `ReleaseNotesWindow`; `news.search_hint` and `news.search_empty` L10n keys

- [ ] **Step 1: Add i18n strings**

In `src/lang/messages.properties`, after `news.next=Next`:

```
news.search_hint=Search details
news.search_empty=Nothing found
```

In `src/lang/messages_ru.properties`, after `news.next=Далее`:

```
news.search_hint=Поиск по подробностям
news.search_empty=Ничего не найдено
```

- [ ] **Step 2: Replace `ReleaseNotesWindow` with search + filter**

Overwrite `src/nurgling/widgets/ReleaseNotesWindow.java` with:

```java
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
    private List<ReleaseNotes.Entry> visible = Collections.emptyList();
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
        visible = next;
        releaseList.reset();
        if (visible.isEmpty()) {
            selected = null;
            String emptyKey = releases.isEmpty() ? "news.empty" : "news.search_empty";
            content.settext(RichText.Parser.quote(L10n.get(emptyKey)));
            detailsButton.hide();
            previousButton.hide();
            nextButton.hide();
            return;
        }
        select(visible.get(0));
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
        int current = visible.indexOf(selected);
        int target = current + delta;
        if (target >= 0 && target < visible.size())
            select(visible.get(target));
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
        int current = visible.indexOf(selected);
        previousButton.show(current > 0);
        nextButton.show(current >= 0 && current < visible.size() - 1);
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
            return visible;
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
            if (button != 1 || slot < 0 || slot >= visible.size())
                return false;
            select(visible.get(slot));
            return true;
        }
    }
}
```

No widget test. Behavior to preserve:

- Empty query shows every release, first selected, details collapsed.
- Non-empty query filters by `matchesDetails`, first match selected with details expanded.
- Previous/Next and list clicks use `visible`.
- No matches: `news.search_empty`, nav/details buttons hidden.
- No packaged notes: `news.empty`.
- `show()` clears the search field.

- [ ] **Step 3: Run tests**

Run: `ant test`

Expected: PASS. `ReleaseNotesTest` still green; client compiles.

- [ ] **Step 4: Commit**

```bash
git add src/nurgling/widgets/ReleaseNotesWindow.java src/lang/messages.properties src/lang/messages_ru.properties
git commit -m "feat: filter What's new list by details search"
```

---

### Task 3: Player release note

**Files:**
- Create: `changes/2026-09-17-news-details-search.json`

**Interfaces:**
- Consumes: player-visible search in What's new
- Produces: bilingual `changes/` note, `id` `2026-09-17-news-details-search`

- [ ] **Step 1: Add the note**

Create `changes/2026-09-17-news-details-search.json`:

```json
{
  "id": "2026-09-17-news-details-search",
  "priority": 6,
  "summary": {
    "ru": "В «Что нового» можно найти выпуск по тексту подробностей.",
    "en": "What's new can find a past update by its expanded details text."
  },
  "detail": {
    "ru": "Откройте «Что нового» (кнопка «i» справа в верхнем ряду меню). Над списком версий есть поиск: введите фразу из подробностей — останутся подходящие выпуски, сразу с раскрытым текстом.",
    "en": "Open What's new (the i button at the right of the top menu row). Type a phrase from the expanded details above the version list to keep matching updates, already expanded."
  }
}
```

- [ ] **Step 2: Validate the note**

Run: `python tools/release_notes.py --check`

Expected: exit 0, no errors.

- [ ] **Step 3: Commit**

```bash
git add changes/2026-09-17-news-details-search.json
git commit -m "docs: note What's new details search"
```
