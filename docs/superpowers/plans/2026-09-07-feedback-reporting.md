# In-game Feedback Reporting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an in-game bug/suggestion form with rectangular screenshot capture and direct, asynchronous delivery to a dedicated Telegram bot.

**Architecture:** Keep validation, image encoding, selection geometry, multipart HTTP, and retry state in renderer-independent classes under `nurgling.feedback`. Keep Haven widget/event adaptation under `nurgling.widgets`. `OptWnd` supplies a bright entry button; secrets come from an ignored neighboring properties file or Java system properties and never enter Git.

**Tech Stack:** Java 8, Haven widget/rendering APIs, `HttpURLConnection`, `BufferedImage`/`ImageIO`, JUnit 5, Ant.

**Spec:** `docs/superpowers/specs/2026-09-07-feedback-reporting-design.md`

## Global Constraints

- Compile with Java source/target 1.8; do not use records, text blocks, `java.net.http`, or APIs newer than Java 8.
- Do not add a third-party dependency.
- Report types are `BUG` and `SUGGESTION`; `BUG` is the default.
- Subject is required and limited to 120 characters after trimming.
- Description is required and limited to 4,000 characters after trimming.
- Accept at most three PNG screenshot attachments; do not add arbitrary file attachments.
- A valid snip is at least 8 by 8 pixels after clamping to the captured frame.
- Do not collect character name, account data, coordinates, logs, IP address, or machine identifiers.
- Never commit a real bot token or chat ID, and never include the token in log, exception, or UI text.
- Delivery must run away from the UI/render thread and retries must continue from the first unsent operation.
- All player-facing text must exist in both English and Russian resource bundles.

---

### Task 1: Feedback configuration and draft domain

**Files:**

- Create: `src/nurgling/feedback/FeedbackType.java`
- Create: `src/nurgling/feedback/FeedbackAttachment.java`
- Create: `src/nurgling/feedback/FeedbackSubmission.java`
- Create: `src/nurgling/feedback/FeedbackDraft.java`
- Create: `src/nurgling/feedback/FeedbackConfig.java`
- Test: `test/nurgling/feedback/FeedbackDraftTest.java`
- Test: `test/nurgling/feedback/FeedbackConfigTest.java`

**Interfaces:**

- Produces: `FeedbackType.BUG`, `FeedbackType.SUGGESTION`.
- Produces: `FeedbackAttachment.from(BufferedImage)`, `png()`, `image()`, `width()`, `height()`.
- Produces: immutable `FeedbackSubmission(String reportId, FeedbackType type, String subject, String description, List<FeedbackAttachment> attachments)`.
- Produces: `FeedbackDraft.Validation`, `validate()`, `addAttachment(...)`, `removeAttachment(int)`, `dirty()`, `clear()`, and `submission(String)`.
- Produces: `FeedbackConfig.resolve(Properties system, Properties file)`, `load()`, `configured()`, `botToken()`, and `chatId()`.

- [ ] **Step 1: Write failing draft tests**

```java
package nurgling.feedback;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackDraftTest {
    @Test void requiresTrimmedSubjectAndDescription() {
        FeedbackDraft draft = new FeedbackDraft();
        assertEquals(FeedbackDraft.Validation.SUBJECT_REQUIRED, draft.validate());
        draft.setSubject("  crash  ");
        assertEquals(FeedbackDraft.Validation.DESCRIPTION_REQUIRED, draft.validate());
        draft.setDescription("  opening inventory crashes  ");
        assertEquals(FeedbackDraft.Validation.OK, draft.validate());
        FeedbackSubmission submission = draft.submission("R-1234");
        assertEquals("crash", submission.subject());
        assertEquals("opening inventory crashes", submission.description());
    }

    @Test void enforcesTextAndAttachmentLimits() throws Exception {
        FeedbackDraft draft = new FeedbackDraft();
        draft.setSubject(repeat('s', 121));
        draft.setDescription("body");
        assertEquals(FeedbackDraft.Validation.SUBJECT_TOO_LONG, draft.validate());
        draft.setSubject("subject");
        draft.setDescription(repeat('d', 4001));
        assertEquals(FeedbackDraft.Validation.DESCRIPTION_TOO_LONG, draft.validate());
        FeedbackAttachment image = FeedbackAttachment.from(new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB));
        assertTrue(draft.addAttachment(image));
        assertTrue(draft.addAttachment(image));
        assertTrue(draft.addAttachment(image));
        assertFalse(draft.addAttachment(image));
        assertEquals(3, draft.attachments().size());
    }

    @Test void clearingRestoresPristineBugDraft() {
        FeedbackDraft draft = new FeedbackDraft();
        draft.setType(FeedbackType.SUGGESTION);
        draft.setSubject("idea");
        assertTrue(draft.dirty());
        draft.clear();
        assertEquals(FeedbackType.BUG, draft.type());
        assertFalse(draft.dirty());
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }
}
```

- [ ] **Step 2: Run the suite and verify RED**

Run: `rtk ant test`

Expected: compilation fails because the `nurgling.feedback` domain classes do not exist.

- [ ] **Step 3: Implement the minimal draft domain**

```java
public enum FeedbackType { BUG, SUGGESTION }

public final class FeedbackDraft {
    public static final int MAX_SUBJECT = 120, MAX_DESCRIPTION = 4000, MAX_ATTACHMENTS = 3;
    public enum Validation { OK, SUBJECT_REQUIRED, SUBJECT_TOO_LONG, DESCRIPTION_REQUIRED, DESCRIPTION_TOO_LONG }

    private FeedbackType type = FeedbackType.BUG;
    private String subject = "", description = "";
    private final List<FeedbackAttachment> attachments = new ArrayList<>();

    public Validation validate() {
        String cleanSubject = subject.trim(), cleanDescription = description.trim();
        if(cleanSubject.isEmpty()) return Validation.SUBJECT_REQUIRED;
        if(cleanSubject.length() > MAX_SUBJECT) return Validation.SUBJECT_TOO_LONG;
        if(cleanDescription.isEmpty()) return Validation.DESCRIPTION_REQUIRED;
        if(cleanDescription.length() > MAX_DESCRIPTION) return Validation.DESCRIPTION_TOO_LONG;
        return Validation.OK;
    }

    public boolean addAttachment(FeedbackAttachment attachment) {
        if(attachment == null || attachments.size() >= MAX_ATTACHMENTS) return false;
        attachments.add(attachment);
        return true;
    }
}
```

Implement ordinary getters/setters, immutable list views, indexed removal, `dirty()`, `clear()`, and `submission(...)` exactly as exercised by the tests. `FeedbackAttachment.from(...)` writes one PNG with `ImageIO` and rejects a missing PNG writer or zero-length result with `IOException`. `FeedbackSubmission` defensively copies and exposes an unmodifiable attachment list.

- [ ] **Step 4: Write failing configuration precedence tests**

```java
package nurgling.feedback;

import org.junit.jupiter.api.Test;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackConfigTest {
    @Test void systemPropertiesOverrideNeighboringFile() {
        Properties file = values("file-token", "100");
        Properties system = values("system-token", "200");
        FeedbackConfig config = FeedbackConfig.resolve(system, file);
        assertEquals("system-token", config.botToken());
        assertEquals("200", config.chatId());
        assertTrue(config.configured());
    }

    @Test void blankValuesAreNotConfigured() {
        FeedbackConfig config = FeedbackConfig.resolve(new Properties(), values(" ", ""));
        assertFalse(config.configured());
        assertFalse(config.toString().contains("token"));
    }

    private static Properties values(String token, String chat) {
        Properties values = new Properties();
        values.setProperty(FeedbackConfig.TOKEN_KEY, token);
        values.setProperty(FeedbackConfig.CHAT_ID_KEY, chat);
        return values;
    }
}
```

- [ ] **Step 5: Run the suite and verify the new configuration test fails**

Run: `rtk ant test`

Expected: `FeedbackConfigTest` fails to compile because `FeedbackConfig` is missing.

- [ ] **Step 6: Implement configuration loading**

```java
public final class FeedbackConfig {
    public static final String TOKEN_KEY = "nurgling.feedback.bot-token";
    public static final String CHAT_ID_KEY = "nurgling.feedback.chat-id";

    static FeedbackConfig resolve(Properties system, Properties file) {
        return new FeedbackConfig(first(system, file, TOKEN_KEY), first(system, file, CHAT_ID_KEY));
    }

    public static FeedbackConfig load() {
        return resolve(System.getProperties(), loadNeighboringFile());
    }

    public boolean configured() {
        return !botToken.isEmpty() && chatId.matches("-?[0-9]+");
    }

    @Override public String toString() {
        return "FeedbackConfig{configured=" + configured() + "}";
    }
}
```

`loadNeighboringFile()` locates `feedback.properties` beside `Utils.srcpath(FeedbackConfig.class)` when running from a JAR and falls back to the process working directory during class-directory development. Missing files return empty `Properties`; read failures issue a token-free warning.

- [ ] **Step 7: Run all tests and commit**

Run: `rtk ant test`

Expected: all tests pass.

```bash
rtk git add src/nurgling/feedback test/nurgling/feedback
rtk git commit -m "feat(feedback): add report draft and configuration"
```

---

### Task 2: Rectangle selection and PNG cropping

**Files:**

- Create: `src/nurgling/feedback/SnipSelection.java`
- Test: `test/nurgling/feedback/SnipSelectionTest.java`

**Interfaces:**

- Consumes: Haven `Coord` and Java `BufferedImage`.
- Produces: `SnipSelection.rectangle(Coord start, Coord end, Coord bounds, int minimumSize)` returning `Optional<Rectangle>`.
- Produces: `SnipSelection.crop(BufferedImage source, Rectangle selection)` returning a detached `BufferedImage`.

- [ ] **Step 1: Write failing geometry tests**

```java
package nurgling.feedback;

import haven.Coord;
import org.junit.jupiter.api.Test;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SnipSelectionTest {
    @Test void normalizesReverseDragAndClampsToFrame() {
        Optional<Rectangle> result = SnipSelection.rectangle(
                Coord.of(90, 80), Coord.of(-5, 10), Coord.of(80, 60), 8);
        assertEquals(new Rectangle(0, 10, 80, 50), result.get());
    }

    @Test void rejectsSelectionsSmallerThanEightPixels() {
        assertFalse(SnipSelection.rectangle(
                Coord.of(10, 10), Coord.of(17, 30), Coord.of(100, 100), 8).isPresent());
    }

    @Test void cropReturnsDetachedPixels() {
        BufferedImage source = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(5, 6, 0xff12ab34);
        BufferedImage crop = SnipSelection.crop(source, new Rectangle(5, 6, 8, 9));
        assertEquals(8, crop.getWidth());
        assertEquals(9, crop.getHeight());
        assertEquals(0xff12ab34, crop.getRGB(0, 0));
        crop.setRGB(0, 0, 0);
        assertEquals(0xff12ab34, source.getRGB(5, 6));
    }
}
```

- [ ] **Step 2: Run the suite and verify RED**

Run: `rtk ant test`

Expected: `SnipSelectionTest` fails to compile because `SnipSelection` is missing.

- [ ] **Step 3: Implement normalization, clamping, and detached cropping**

```java
public static Optional<Rectangle> rectangle(Coord start, Coord end, Coord bounds, int minimumSize) {
    int left = Math.max(0, Math.min(start.x, end.x));
    int top = Math.max(0, Math.min(start.y, end.y));
    int right = Math.min(bounds.x, Math.max(start.x, end.x));
    int bottom = Math.min(bounds.y, Math.max(start.y, end.y));
    int width = Math.max(0, right - left), height = Math.max(0, bottom - top);
    return (width < minimumSize || height < minimumSize)
            ? Optional.empty() : Optional.of(new Rectangle(left, top, width, height));
}

public static BufferedImage crop(BufferedImage source, Rectangle selection) {
    BufferedImage result = new BufferedImage(selection.width, selection.height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = result.createGraphics();
    try {
        graphics.drawImage(source, 0, 0, selection.width, selection.height,
                selection.x, selection.y, selection.x + selection.width, selection.y + selection.height, null);
    } finally {
        graphics.dispose();
    }
    return result;
}
```

- [ ] **Step 4: Run all tests and commit**

Run: `rtk ant test`

Expected: all tests pass.

```bash
rtk git add src/nurgling/feedback/SnipSelection.java test/nurgling/feedback/SnipSelectionTest.java
rtk git commit -m "feat(feedback): add screenshot snip geometry"
```

---

### Task 3: Telegram transport and resumable submission

**Files:**

- Create: `src/nurgling/feedback/FeedbackSender.java`
- Create: `src/nurgling/feedback/TelegramFeedbackClient.java`
- Create: `src/nurgling/feedback/FeedbackFormatter.java`
- Create: `src/nurgling/feedback/FeedbackSubmissionService.java`
- Test: `test/nurgling/feedback/TelegramFeedbackClientTest.java`
- Test: `test/nurgling/feedback/FeedbackSubmissionServiceTest.java`

**Interfaces:**

- Consumes: `FeedbackConfig`, `FeedbackSubmission`, and `FeedbackAttachment.png()` from Task 1.
- Produces: `FeedbackSender.sendMessage(String)` and `sendPhoto(byte[], String, String)`.
- Produces: `TelegramFeedbackClient(FeedbackConfig, int connectTimeoutMs, int readTimeoutMs)` and package-private constructor accepting a test API root.
- Produces: `FeedbackFormatter.message(FeedbackSubmission)` and `photoCaption(FeedbackSubmission, int)`.
- Produces: `FeedbackSubmissionService.Attempt`, `begin(FeedbackSubmission)`, and `submit(Attempt, Listener)`.

- [ ] **Step 1: Write failing HTTP transport tests**

```java
package nurgling.feedback;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class TelegramFeedbackClientTest {
    private HttpServer server;
    @AfterEach void stop() { if(server != null) server.stop(0); }

    @Test void postsMessageAndPngAsMultipartWithoutLoggingToken() throws Exception {
        AtomicReference<String> messageBody = new AtomicReference<>(), photoBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/botsecret/sendMessage", exchange -> respond(exchange, messageBody, 200));
        server.createContext("/botsecret/sendPhoto", exchange -> respond(exchange, photoBody, 200));
        server.start();
        FeedbackConfig config = new FeedbackConfig("secret", "20871713");
        String root = "http://127.0.0.1:" + server.getAddress().getPort() + "/bot";
        TelegramFeedbackClient client = new TelegramFeedbackClient(config, root, 2000, 2000);

        client.sendMessage("BUG R-1\nSubject");
        client.sendPhoto(new byte[] {(byte)0x89, 0x50, 0x4e, 0x47}, "R-1-1.png", "R-1 1/1");

        assertTrue(messageBody.get().contains("20871713"));
        assertTrue(messageBody.get().contains("Subject"));
        assertTrue(photoBody.get().contains("filename=\"R-1-1.png\""));
        assertTrue(photoBody.get().contains("R-1 1/1"));
    }

    @Test void httpFailureDoesNotExposeToken() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/botsecret/sendMessage", exchange -> respond(exchange, new AtomicReference<>(), 500));
        server.start();
        TelegramFeedbackClient client = new TelegramFeedbackClient(
                new FeedbackConfig("secret", "20871713"),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/bot", 2000, 2000);
        java.io.IOException failure = assertThrows(java.io.IOException.class,
                () -> client.sendMessage("hello"));
        assertFalse(failure.getMessage().contains("secret"));
    }
}
```

Add test helpers `respond(...)` and `readAll(...)` following `CookbookHttpClientTest`, returning the exact status supplied.

- [ ] **Step 2: Run the suite and verify RED**

Run: `rtk ant test`

Expected: transport tests fail to compile because the sender/client classes do not exist.

- [ ] **Step 3: Implement token-safe Telegram requests**

```java
public interface FeedbackSender {
    void sendMessage(String text) throws IOException;
    void sendPhoto(byte[] png, String filename, String caption) throws IOException;
}

public void sendMessage(String text) throws IOException {
    postForm("sendMessage", fields("chat_id", config.chatId(), "text", text));
}

public void sendPhoto(byte[] png, String filename, String caption) throws IOException {
    postMultipart("sendPhoto", fields("chat_id", config.chatId(), "caption", caption),
            "photo", filename, "image/png", png);
}
```

Use a random ASCII multipart boundary, UTF-8 for text fields, CRLF separators, fixed timeouts, and `HttpURLConnection`. Accept only 2xx responses, drain and close response streams, disconnect in `finally`, and throw only `Telegram returned HTTP <status>` or `Telegram request failed` messages. Do not enable a Telegram parse mode; report text is plain text.

- [ ] **Step 4: Write failing formatting and retry-state tests**

```java
@Test void retryContinuesAfterTheLastSuccessfulOperation() throws Exception {
    List<String> calls = new ArrayList<>();
    AtomicBoolean failSecondPhotoOnce = new AtomicBoolean(true);
    FeedbackSender sender = new FeedbackSender() {
        public void sendMessage(String text) { calls.add("message"); }
        public void sendPhoto(byte[] png, String name, String caption) throws IOException {
            calls.add(name);
            if(name.endsWith("-2.png") && failSecondPhotoOnce.getAndSet(false))
                throw new IOException("offline");
        }
    };
    FeedbackSubmission report = submissionWithThreeImages("R-7");
    FeedbackSubmissionService service = new FeedbackSubmissionService(sender, Runnable::run);
    FeedbackSubmissionService.Attempt attempt = service.begin(report);
    RecordingListener listener = new RecordingListener();

    service.submit(attempt, listener);
    assertEquals(Arrays.asList("message", "R-7-1.png", "R-7-2.png"), calls);
    assertEquals(1, attempt.photosSent());
    service.submit(attempt, listener);
    assertEquals(Arrays.asList("message", "R-7-1.png", "R-7-2.png", "R-7-2.png", "R-7-3.png"), calls);
    assertTrue(listener.succeeded);
}

@Test void formatterContainsOnlyExplicitReportData() {
    FeedbackSubmission report = new FeedbackSubmission("R-8", FeedbackType.SUGGESTION,
            "Craft window", "Please add favorites", Collections.emptyList());
    String text = FeedbackFormatter.message(report);
    assertTrue(text.contains("R-8"));
    assertTrue(text.contains("SUGGESTION"));
    assertTrue(text.contains("Craft window"));
    assertFalse(text.contains("character"));
    assertFalse(text.contains("machine"));
}
```

- [ ] **Step 5: Run the suite and verify retry tests fail**

Run: `rtk ant test`

Expected: `FeedbackSubmissionServiceTest` fails to compile because formatting, attempt, and service types are missing.

- [ ] **Step 6: Implement formatting and resumable asynchronous delivery**

```java
public void submit(final Attempt attempt, final Listener listener) {
    executor.execute(() -> {
        try {
            if(!attempt.messageSent()) {
                sender.sendMessage(FeedbackFormatter.message(attempt.submission()));
                attempt.markMessageSent();
            }
            while(attempt.photosSent() < attempt.submission().attachments().size()) {
                int index = attempt.photosSent();
                sender.sendPhoto(attempt.submission().attachments().get(index).png(),
                        attempt.submission().reportId() + "-" + (index + 1) + ".png",
                        FeedbackFormatter.photoCaption(attempt.submission(), index));
                attempt.markPhotoSent();
            }
            listener.succeeded();
        } catch(IOException failure) {
            listener.failed(failure);
        }
    });
}
```

Guard one `Attempt` from concurrent submits with an atomic `running` flag. `begin(...)` starts at `messageSent=false`, `photosSent=0`. Listener failures retain the same attempt state. The production factory uses one named daemon executor; tests inject `Runnable::run`.

- [ ] **Step 7: Run all tests and commit**

Run: `rtk ant test`

Expected: all tests pass.

```bash
rtk git add src/nurgling/feedback test/nurgling/feedback
rtk git commit -m "feat(feedback): send resumable Telegram reports"
```

---

### Task 4: Frozen-frame snipping overlay

**Files:**

- Create: `src/nurgling/widgets/FeedbackSnipOverlay.java`
- Create: `src/nurgling/widgets/FeedbackCaptureController.java`
- Test: `test/nurgling/widgets/FeedbackCaptureStateTest.java`

**Interfaces:**

- Consumes: `SnipSelection` and `FeedbackAttachment` from Tasks 1-2.
- Produces: `FeedbackCaptureController.capture(GameUI game, Widget form, Widget options, Consumer<FeedbackAttachment> selected, Runnable cancelled, Consumer<Throwable> failed)`.
- Produces: package-private `FeedbackCaptureController.VisibilityState` used to guarantee exactly-once restoration.
- Produces: `FeedbackSnipOverlay(BufferedImage frame, Consumer<BufferedImage> selected, Runnable cancelled)`.

- [ ] **Step 1: Write a failing exactly-once restoration test**

```java
package nurgling.widgets;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedbackCaptureStateTest {
    @Test void selectionCancellationAndFailureRestoreWindowsOnlyOnce() {
        AtomicInteger restores = new AtomicInteger();
        FeedbackCaptureController.VisibilityState state =
                new FeedbackCaptureController.VisibilityState(restores::incrementAndGet);
        state.restore();
        state.restore();
        assertEquals(1, restores.get());
    }
}
```

- [ ] **Step 2: Run the suite and verify RED**

Run: `rtk ant test`

Expected: the test fails to compile because `FeedbackCaptureController` is missing.

- [ ] **Step 3: Implement capture lifecycle and selection overlay**

`FeedbackCaptureController.capture(...)` must:

```java
form.hide();
options.hide();
VisibilityState visibility = new VisibilityState(() -> {
    options.show();
    form.show();
    form.raise();
});
game.ui.drawafter(g -> g.getimage(Coord.z, g.sz(), frame -> {
    synchronized(game.ui) {
        FeedbackSnipOverlay overlay = new FeedbackSnipOverlay(frame, crop -> {
            try {
                selected.accept(FeedbackAttachment.from(crop));
            } catch(IOException error) {
                failed.accept(error);
            } finally {
                visibility.restore();
            }
        }, () -> {
            visibility.restore();
            cancelled.run();
        });
        game.ui.root.add(overlay, Coord.z);
        game.ui.root.setfocus(overlay);
    }
}));
```

The overlay fills `game.ui.root.sz`, owns a `TexI` for the frozen frame, dims the unselected region, draws a high-contrast rectangle and localized instructions, and consumes all pointer/key input. Left press starts selection and obtains `ui.grabmouse(this)`; move updates the endpoint; left release calls `SnipSelection.rectangle(...)` and either returns a detached crop or cancels. Right click and `Esc` cancel. Removal disposes both the mouse grab and screenshot texture.

- [ ] **Step 4: Run tests and compile the client**

Run: `rtk ant test`

Expected: all automated tests pass and the UI adapter compiles.

- [ ] **Step 5: Commit**

```bash
rtk git add src/nurgling/widgets/FeedbackSnipOverlay.java src/nurgling/widgets/FeedbackCaptureController.java test/nurgling/widgets/FeedbackCaptureStateTest.java
rtk git commit -m "feat(feedback): add in-game snipping overlay"
```

---

### Task 5: Feedback form, previews, and send-state UI

**Files:**

- Create: `src/nurgling/widgets/FeedbackWindow.java`
- Create: `src/nurgling/widgets/FeedbackPreviewWindow.java`
- Create: `src/nurgling/widgets/FeedbackWindowLayout.java`
- Test: `test/nurgling/widgets/FeedbackWindowLayoutTest.java`

**Interfaces:**

- Consumes: all domain, capture, and submission interfaces from Tasks 1-4.
- Produces: `FeedbackWindow.open(GameUI game, Widget options)` that raises an existing form or creates one centered in `GameUI`.
- Produces: `FeedbackWindowLayout.calculate(int width, int thumbnailCount)` for stable scaled positions.
- Produces: `FeedbackPreviewWindow(FeedbackAttachment)`.

- [ ] **Step 1: Write a failing layout test for zero and three thumbnails**

```java
package nurgling.widgets;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackWindowLayoutTest {
    @Test void threeThumbnailsRemainInsideTheAttachmentRow() {
        FeedbackWindowLayout layout = FeedbackWindowLayout.calculate(560, 3);
        assertEquals(3, layout.thumbnails().size());
        assertTrue(layout.thumbnails().get(2).x + layout.thumbnailSize() <= 560);
        assertTrue(layout.sendY() > layout.attachmentsY());
    }

    @Test void sendRowDoesNotMoveWhenThereAreNoAttachments() {
        assertEquals(FeedbackWindowLayout.calculate(560, 0).sendY(),
                FeedbackWindowLayout.calculate(560, 3).sendY());
    }
}
```

- [ ] **Step 2: Run the suite and verify RED**

Run: `rtk ant test`

Expected: layout tests fail to compile because `FeedbackWindowLayout` is missing.

- [ ] **Step 3: Implement the pure scaled layout**

Return fixed rows for the type selector, subject, multiline description, attachment strip, status, and actions. Use scaled values at the call site, keep the content width at `UI.scale(560)`, description height at `UI.scale(180)`, thumbnails at `UI.scale(96)`, and consistent `UI.scale(8)` gaps. The layout must reserve all three thumbnail slots even when empty so the window does not jump.

- [ ] **Step 4: Implement the form against the tested domain APIs**

Construct `FeedbackWindow` with:

```java
typeBug.action(() -> selectType(FeedbackType.BUG));
typeSuggestion.action(() -> selectType(FeedbackType.SUGGESTION));
subject = add(new TextEntry(UI.scale(560), ""), subjectPosition);
description = add(new NTextArea(UI.scale(560, 180), ""), descriptionPosition);
snip = add(new Button(UI.scale(180), L10n.get("feedback.snip"), this::startSnip), snipPosition);
send = add(new Button(UI.scale(110), L10n.get("feedback.send"), this::send), sendPosition);
```

`refresh()` copies widget text into `FeedbackDraft`, updates the selected type style, rebuilds thumbnail widgets, displays `n/3`, disables scissors at three attachments, and disables send when configuration is absent or an attempt is running. Each thumbnail draws a downscaled `TexI`, opens `FeedbackPreviewWindow` on left click, and has an adjacent remove button.

`send()` validates the draft and maps every `FeedbackDraft.Validation` to a localized key. On valid data it creates the report ID as `R-` plus eight uppercase hexadecimal characters, snapshots the draft, creates or reuses the current `Attempt`, updates the button/status to sending, and calls the service. Listener callbacks enter `synchronized(ui)` before mutating widgets. Success clears and destroys the form and calls `game.msg(...)`; failure keeps the attempt and attachments and changes the send label to **Retry**.

Override close handling. If `draft.dirty()` is false, destroy immediately. Otherwise show a small child confirmation with localized **Discard** and **Continue editing** actions; do not create a second confirmation while one is already visible.

- [ ] **Step 5: Implement preview lifecycle and single-instance open behavior**

`FeedbackPreviewWindow` displays a fitted `TexI` without upscaling beyond the available root area and disposes it on removal. `FeedbackWindow.open(...)` walks direct `GameUI` children, raises an existing `FeedbackWindow`, and otherwise adds one centered. Capture temporarily hides both the form and the passed options widget, then restores them through Task 4's controller.

- [ ] **Step 6: Run all tests and commit**

Run: `rtk ant test`

Expected: all tests pass.

```bash
rtk git add src/nurgling/widgets/FeedbackWindow.java src/nurgling/widgets/FeedbackPreviewWindow.java src/nurgling/widgets/FeedbackWindowLayout.java test/nurgling/widgets/FeedbackWindowLayoutTest.java
rtk git commit -m "feat(feedback): add report form and screenshot previews"
```

---

### Task 6: Options entry point, localization, and private release configuration

**Files:**

- Create: `src/nurgling/widgets/FeedbackButton.java`
- Create: `src/nurgling/widgets/FeedbackEntryLayout.java`
- Create: `test/nurgling/widgets/FeedbackEntryLayoutTest.java`
- Create: `test/nurgling/widgets/FeedbackLocalizationTest.java`
- Create: `local-feedback.properties.example`
- Modify: `.gitignore`
- Modify: `build.xml:212-238`
- Modify: `src/haven/OptWnd.java:892-905`
- Modify: `src/lang/messages.properties`
- Modify: `src/lang/messages_ru.properties`

**Interfaces:**

- Consumes: `FeedbackWindow.open(GameUI, Widget)` from Task 5.
- Produces: `FeedbackEntryLayout.below(Coord position, Coord size, int gap)`.
- Produces: `FeedbackButton(int width, String text, Runnable action)` with amber base/hover styling.
- Produces: an ignored runtime file `bin/feedback.properties` copied from ignored `local-feedback.properties`.

- [ ] **Step 1: Write failing placement and localization tests**

```java
package nurgling.widgets;

import haven.Coord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedbackEntryLayoutTest {
    @Test void feedbackButtonSitsDirectlyBelowNurglingSettings() {
        assertEquals(Coord.of(210, 35), FeedbackEntryLayout.below(Coord.of(210, 0), Coord.of(200, 30), 5));
    }
}
```

```java
class FeedbackLocalizationTest {
    private static final List<String> REQUIRED = Arrays.asList(
        "feedback.entry", "feedback.title", "feedback.type.bug", "feedback.type.suggestion",
        "feedback.subject", "feedback.description", "feedback.attachments", "feedback.snip",
        "feedback.snip.instruction", "feedback.send", "feedback.cancel", "feedback.retry",
        "feedback.remove", "feedback.preview", "feedback.discard.title", "feedback.discard",
        "feedback.keep_editing", "feedback.error.subject_required", "feedback.error.subject_too_long",
        "feedback.error.description_required", "feedback.error.description_too_long",
        "feedback.error.configuration", "feedback.error.capture", "feedback.error.delivery",
        "feedback.status.sending", "feedback.status.sent");

    @Test void englishAndRussianBundlesContainEveryFeedbackKey() throws Exception {
        assertComplete(load("src/lang/messages.properties"));
        assertComplete(load("src/lang/messages_ru.properties"));
    }
}
```

Implement `load(...)` and `assertComplete(...)` exactly like `CraftAtlasLocalizationTest`: use an UTF-8 `InputStreamReader`, collect missing/blank values, and assert that the missing list is empty.

- [ ] **Step 2: Run the suite and verify RED**

Run: `rtk ant test`

Expected: placement test fails to compile and localization test reports every missing `feedback.*` key.

- [ ] **Step 3: Add localized strings and the bright button**

Use concise copy matching the design. `FeedbackButton` extends `Button`, applies an amber tint, uses a pale label, and redraws with a stronger tint while hovered. Do not add a bitmap resource. It must preserve the base button's pressed, disabled, font-theme, click-sound, and scaling behavior.

- [ ] **Step 4: Place the button below Nurgling Settings**

Change the main-panel construction to retain the Nurgling button and use the tested helper:

```java
Widget nurglingSettings = main.add(new PButton(UI.scale(200),
        L10n.get("opt.main.nurgling"), 'k', nqolwnd), x, prev.pos("ur").y);
main.add(new FeedbackButton(UI.scale(200), L10n.get("feedback.entry"), () ->
        FeedbackWindow.open(getparent(GameUI.class), OptWnd.this)),
        FeedbackEntryLayout.below(nurglingSettings.c, nurglingSettings.sz, UI.scale(5)));
```

Keep the existing left-column video, audio, and hotkey positions unchanged. Import only the new widget classes required by `OptWnd`.

- [ ] **Step 5: Add private configuration packaging without committing secrets**

Add these exact ignored paths:

```gitignore
/local-feedback.properties
/bin/feedback.properties
```

Track only this example:

```properties
nurgling.feedback.bot-token=
nurgling.feedback.chat-id=
```

In the Ant `bin` target, add a quiet optional copy after the existing JAR copies:

```xml
<copy file="local-feedback.properties" tofile="bin/feedback.properties"
      failonerror="false" quiet="true" />
```

Create the real `local-feedback.properties` only in the local workspace, using the regenerated release token and destination chat ID. Confirm with `rtk git status --short` that neither the source file nor copied `bin/feedback.properties` is tracked.

- [ ] **Step 6: Run all tests and verify the build artifact**

Run: `rtk ant test`

Expected: all tests pass.

Run: `rtk ant bin`

Expected: `bin/hafen.jar` builds and `bin/feedback.properties` exists locally when `local-feedback.properties` exists.

Run: `rtk git grep -n "nurgling.feedback.bot-token" -- ':!local-feedback.properties.example' ':!docs/superpowers/**'`

Expected: source references the property name, but no line contains a real token value or chat ID.

- [ ] **Step 7: Commit tracked integration files only**

```bash
rtk git add .gitignore build.xml local-feedback.properties.example src/haven/OptWnd.java src/lang/messages.properties src/lang/messages_ru.properties src/nurgling/widgets/FeedbackButton.java src/nurgling/widgets/FeedbackEntryLayout.java test/nurgling/widgets/FeedbackEntryLayoutTest.java test/nurgling/widgets/FeedbackLocalizationTest.java
rtk git commit -m "feat(feedback): expose localized report workflow"
```

---

### Task 7: End-to-end verification

**Files:**

- Modify only files found defective by verification, together with a failing regression test for each defect.

**Interfaces:**

- Consumes: the completed feedback workflow from Tasks 1-6.
- Produces: a verified distributable and a rotated-token release checklist.

- [ ] **Step 1: Run the complete automated suite from a clean compilation output**

Run: `rtk ant clean test`

Expected: all tests pass with no compilation error.

- [ ] **Step 2: Rebuild the distributable**

Run: `rtk ant bin`

Expected: `bin/hafen.jar` and the optional local `bin/feedback.properties` are produced successfully.

- [ ] **Step 3: Perform the UI smoke test**

Verify at 100% and one non-default UI scale:

- the amber entry button is directly below Nurgling Settings and remains readable;
- only one form opens;
- Bug/Suggestion switching, required fields, length errors, dirty-close confirmation, and zero-image sending work;
- scissors hide the form/options, freeze the game frame, support every drag direction, clamp at edges, and cancel on `Esc`/right click;
- 7-pixel selections are rejected and an 8-pixel selection is accepted;
- one, two, and three screenshots render as removable/openable thumbnails; a fourth cannot be started;
- a forced network failure leaves the draft intact and Retry does not resend completed operations;
- a successful Telegram delivery contains the report ID, type, subject, description, and ordered PNGs.

- [ ] **Step 4: Audit secrets and unrelated changes**

Run: `rtk git status --short`

Expected: the real `local-feedback.properties` and copied `bin/feedback.properties` are absent from status; pre-existing unrelated user changes remain untouched.

Run: `rtk git diff --check`

Expected: no whitespace errors.

- [ ] **Step 5: Rotate the exposed design-time token before release**

Use BotFather to revoke the token shared in conversation, place only the new value in ignored `local-feedback.properties`, rebuild with `rtk ant bin`, and send one final live report. This step requires the owner and must be completed before distributing the client.

- [ ] **Step 6: Commit only regression fixes, if any**

If verification required a code fix, first add a failing test reproducing it, then implement the minimum correction, rerun `rtk ant test`, and commit only those tracked files with a narrowly scoped `fix(feedback): ...` message. If no defect is found, create no empty verification commit.
