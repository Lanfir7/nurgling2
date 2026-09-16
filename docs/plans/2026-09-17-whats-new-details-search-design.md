# What's New details search

Date: 2026-09-17

## Goal

Players can find a past release by a phrase from its expanded details, without browsing the version list by hand.

## Scope

In the existing «What's new» window, add a live filter over packaged release notes. Search looks only at details text for the current UI language. Version id, date, title and summary are out of scope.

Out of scope: highlight in the body, regex, both-language search, persisting the query.

## Behavior

- A search field sits above the left version list, same width as the list.
- Each change of the query immediately filters the list. Matching is a case-insensitive substring in any details line of that release.
- Empty or whitespace-only query shows every release, as today.
- Language follows the same fallback as the window: current language if that details list is non-empty, otherwise the other language.
- The first remaining release is selected and shown with details already expanded.
- Previous / Next move only through the filtered list.
- No matches: empty list, body text «Nothing found» / «Ничего не найдено», details and navigation buttons hidden.
- Opening the window resets the query. Closing does not save it.

## Architecture

Search stays in the window. Matching is a method on `ReleaseNotes.Entry` so tests do not need UI.

- `ReleaseNotes.Entry.matchesDetails(query, language)` — `true` when the query is blank/whitespace, or any details line contains the trimmed query, ignoring case.
- `ReleaseNotesWindow` keeps the full `releases` list and a filtered view. Filtering is `releases` where `matchesDetails` is true. The left list and Previous / Next read the filtered view.
- Layout: a `Label` (`news.search_hint`) and a `TextEntry` stacked above `ReleaseList`, same width as the list. Do not reuse cookbook `HintTextEntry`. List height shrinks by the label, field and padding. Window size and the right-hand body stay as they are.
- i18n: `news.search_hint`, `news.search_empty` in `messages.properties` and `messages_ru.properties`.

## Empty and error states

- No packaged notes: current empty message; search field may stay visible but does nothing useful.
- Invalid or missing details on an entry: that entry never matches a non-empty query.
- Null query is treated as empty.

## Testing

Extend `test/nurgling/news/ReleaseNotesTest.java`:

- match is case-insensitive and finds a substring inside a details line;
- blank or whitespace query matches every entry;
- summary, title, id and date do not match;
- Russian query hits Russian details; English details are used only when the Russian list is empty (same fallback as `details(language)`).

No UI widget test.

## Player note

After implementation, add a bilingual `changes/` note: search in What's new finds text from expanded details.
