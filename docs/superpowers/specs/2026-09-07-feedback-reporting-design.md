# In-game feedback reporting design

## Goal

Add a prominent entry point below **Nurgling Settings** that lets a player send a bug report or suggestion, with a subject, description, and up to three cropped in-game screenshots, directly to the owner's dedicated Telegram bot.

## Scope

The feature includes:

- a bright feedback button in the main options panel;
- a localized feedback form;
- an in-game rectangular snipping overlay;
- up to three in-memory PNG attachments;
- asynchronous delivery through the Telegram Bot API;
- local, untracked release configuration for the bot token and destination chat ID;
- validation, progress, success, and retryable error states.

It does not include a hosted relay, arbitrary files from disk, report history, player authentication, or automatic collection of logs and account data.

## Security model

This design intentionally accepts that anyone who receives the distributed client can extract the bot token. The token belongs to a dedicated disposable bot that is not used in groups, has no administrative permissions, and serves no other purpose. Possession of the bot token does not grant access to the owner's personal Telegram account, but it can be used to impersonate or disable the bot, intercept reports sent to it, and create spam.

The token and chat ID must never be committed to Git. Development and release builds read them from an ignored `local-feedback.properties`; the build copies that file to `bin/feedback.properties`. A tracked example file documents the required keys without values. The runtime also accepts equivalent Java system properties so CI or a packager can inject configuration without creating the local file.

Because the token was shared during design, it must be regenerated in BotFather before the public release is built.

## User experience

### Entry point

The main options panel gains a high-contrast amber button in the right column, immediately below **Nurgling Settings**. Its localized label is **Bug report / Suggestion** in English and **Баг-репорт / Предложение** in Russian. The button uses a custom drawn background and hover state rather than a new image asset, so it remains readable with UI scaling and font changes.

Opening the form leaves the options window behind it. Only one feedback form may exist at a time; pressing the entry button again raises the existing form.

### Form

The form contains:

- a two-option selector: **Bug** or **Suggestion**, defaulting to **Bug**;
- a single-line subject field;
- a multiline description field;
- an attachment strip with a counter from `0/3` to `3/3`;
- a scissors button labeled **Select screen area**;
- a **Send** button and a **Cancel** button;
- an inline status/error label.

Each attachment is shown as a small thumbnail with an individual remove control. Clicking a thumbnail opens an enlarged in-game preview. Attachments exist only in memory and are discarded when the form is closed or a report is sent successfully.

Subject and description are both required after trimming whitespace. The subject is limited to 120 characters and the description to 4,000 characters. Sending is disabled while a request is running. Closing a non-empty form asks for confirmation so an accidental close does not discard the draft.

### Snipping flow

Pressing the scissors button is disabled after three attachments. Otherwise it:

1. temporarily hides the form and options window;
2. captures the next fully drawn game frame into a `BufferedImage` using the existing renderer readback mechanism;
3. displays that frozen frame full-screen under a translucent selection overlay;
4. lets the player drag a rectangle with the left mouse button;
5. crops the selected rectangle when the mouse button is released;
6. restores the form and options window and adds the crop as a PNG thumbnail.

The overlay shows a crosshair, dims the area outside the rectangle, and displays a short localized instruction. `Esc` or right click cancels and restores the previous windows without adding an attachment. Selections smaller than 8 by 8 pixels are treated as cancellation. Coordinates are normalized, clamped to the captured image, and work regardless of drag direction.

## Components and boundaries

### Options integration

`OptWnd` creates the bright feedback button in the empty right-column position below the Nurgling settings button. A small layout helper computes the position so it can be covered by a unit test without constructing the full renderer-backed UI.

The button asks the current `GameUI` to show one `FeedbackWindow`. Window ownership and deduplication remain in the feedback feature rather than adding more state to the options panel.

### Form and draft model

`FeedbackWindow` owns visual widgets and delegates all nonvisual rules to `FeedbackDraft`:

- report type;
- trimmed subject and description;
- ordered attachment list;
- length and attachment-count validation;
- whether the draft has unsaved content.

This separation keeps validation and attachment behavior testable without an OpenGL context.

### Capture overlay

`FeedbackCaptureController` schedules renderer readback, hides and restores source windows, and creates `FeedbackSnipOverlay`. The overlay handles mouse and keyboard events and delegates rectangle normalization/clamping to a pure `SnipSelection` helper. It returns either one cropped `BufferedImage` or cancellation through a callback.

Only the captured game frame is selectable. Desktop content outside the game is never captured.

### Telegram delivery

`TelegramFeedbackClient` performs HTTPS multipart requests to the Telegram Bot API. `FeedbackSubmissionService` runs it on a single daemon executor so UI rendering never blocks.

A submission receives a short random report ID. The service sends one formatted text message containing the ID, type, subject, and description, then sends each PNG in order with the same ID in its caption. It tracks progress within the current submission, so retry continues from the first unsent operation instead of duplicating successful messages or images.

No player credentials, IP address, character name, coordinates, logs, or machine identifiers are added automatically.

### Configuration

The runtime keys are:

- `nurgling.feedback.bot-token`
- `nurgling.feedback.chat-id`

Values are resolved first from Java system properties and then from `feedback.properties` in the working directory. Missing or malformed configuration leaves the form usable but disables **Send** and shows a localized configuration error. Tokens must never be printed in exceptions, logs, URLs, or UI messages.

## Error handling

- Empty or oversized text is rejected locally with a field-specific message.
- A failed screenshot readback restores both windows and reports an inline capture error.
- PNG encoding failure rejects only that attachment and leaves the draft intact.
- Network requests use finite connection and read timeouts.
- Telegram non-success responses are reduced to a safe status message without echoing the request URL or token.
- Failed delivery leaves the form and all attachments intact. **Retry** resumes the same report ID from the first unsent operation.
- Success clears the draft, closes the form, and displays a localized confirmation through the game UI.

## Localization

All new player-facing strings are added to `src/lang/messages.properties` and `src/lang/messages_ru.properties`. The implementation does not introduce hard-coded Russian or English status text.

## Testing

Implementation follows test-driven development. Automated coverage includes:

- feedback-button placement beneath Nurgling Settings;
- subject, description, and three-attachment limits;
- dirty-draft detection;
- rectangle normalization, clamping, reverse dragging, and minimum size;
- Telegram multipart encoding without exposing the token;
- ordered send and retry-from-failure behavior against an in-process HTTP server or injected transport;
- missing configuration behavior;
- required English and Russian localization keys.

The final verification runs the focused feedback tests, the full project test suite, and the Ant client build. A manual smoke test verifies UI scaling, the frozen-frame selection interaction, thumbnail removal/preview, and delivery of one report with zero, one, and three screenshots.

