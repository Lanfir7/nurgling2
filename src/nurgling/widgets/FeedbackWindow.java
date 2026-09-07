package nurgling.widgets;

import haven.Button;
import haven.Coord;
import haven.GOut;
import haven.GameUI;
import haven.Label;
import haven.TexI;
import haven.TextEntry;
import haven.UI;
import haven.Widget;
import haven.Window;
import nurgling.feedback.FeedbackAttachment;
import nurgling.feedback.FeedbackConfig;
import nurgling.feedback.FeedbackDraft;
import nurgling.feedback.FeedbackSubmissionService;
import nurgling.feedback.FeedbackType;
import nurgling.feedback.TelegramFeedbackClient;
import nurgling.i18n.L10n;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class FeedbackWindow extends Window {
    private static final int CONTENT_WIDTH = UI.scale(560);
    private static final int GAP = UI.scale(8);

    private final GameUI game;
    private final Widget options;
    private final FeedbackDraft draft = new FeedbackDraft();
    private final FeedbackConfig config;
    private final FeedbackSubmissionService service;
    private final FeedbackWindowLayout layout;
    private final List<Widget> attachmentWidgets = new ArrayList<>();
    private final Button typeBug;
    private final Button typeSuggestion;
    private final TextEntry subject;
    private final TextEntry discordContact;
    private final NTextArea description;
    private final Button snip;
    private final Button send;
    private final Label attachmentCount;
    private final Label status;
    private FeedbackSubmissionService.Attempt attempt;
    private Window discardConfirmation;
    private boolean closed;

    private FeedbackWindow(GameUI game, Widget options) {
        super(Coord.z, L10n.get("feedback.title"));
        this.game = game;
        this.options = options;
        this.layout = FeedbackWindowLayout.calculate(CONTENT_WIDTH, 0);
        this.config = FeedbackConfig.load();
        this.service = config.configured()
                ? new FeedbackSubmissionService(new TelegramFeedbackClient(config, 8000, 20000))
                : null;

        add(new Label(L10n.get("feedback.type")), Coord.of(0, layout.typeY() - UI.scale(18)));
        typeBug = add(new Button(UI.scale(170), L10n.get("feedback.type.bug"),
                () -> selectType(FeedbackType.BUG)), Coord.of(0, layout.typeY()));
        typeSuggestion = add(new Button(UI.scale(170), L10n.get("feedback.type.suggestion"),
                () -> selectType(FeedbackType.SUGGESTION)), Coord.of(UI.scale(178), layout.typeY()));

        add(new Label(L10n.get("feedback.subject")), Coord.of(0, layout.subjectY() - UI.scale(18)));
        subject = add(new TextEntry(CONTENT_WIDTH, "") {
            @Override
            protected void changed() {
                super.changed();
                syncDraft();
            }
        }, Coord.of(0, layout.subjectY()));

        add(new Label(L10n.get("feedback.discord_contact")),
                Coord.of(0, layout.discordY() - UI.scale(18)));
        discordContact = add(new TextEntry(CONTENT_WIDTH, "") {
            @Override
            protected void changed() {
                super.changed();
                syncDraft();
            }
        }, Coord.of(0, layout.discordY()));

        add(new Label(L10n.get("feedback.description")),
                Coord.of(0, layout.descriptionY() - UI.scale(18)));
        description = add(new NTextArea(Coord.of(CONTENT_WIDTH, UI.scale(180)), ""),
                Coord.of(0, layout.descriptionY()));
        description.onchange = this::syncDraft;

        add(new Label(L10n.get("feedback.attachments")),
                Coord.of(0, layout.attachmentsY() - UI.scale(20)));
        int attachmentActionsY = layout.attachmentsY() + layout.thumbnailSize() + GAP;
        snip = add(new Button(UI.scale(180), L10n.get("feedback.snip"), this::startSnip),
                Coord.of(0, attachmentActionsY));
        attachmentCount = add(new Label("0/3"), Coord.of(UI.scale(192), attachmentActionsY + UI.scale(7)));
        status = add(new Label(""), Coord.of(0, layout.statusY()));
        send = add(new Button(UI.scale(110), L10n.get("feedback.send"), this::send),
                Coord.of(0, layout.sendY()));
        add(new Button(UI.scale(140), L10n.get("feedback.cancel"), this::reqclose),
                Coord.of(UI.scale(120), layout.sendY()));

        refreshAttachments();
        refreshState();
        pack();
    }

    public static void open(GameUI game, Widget options) {
        if(game == null)
            return;
        for(Widget child : game.children()) {
            if(child instanceof FeedbackWindow) {
                child.show();
                child.raise();
                return;
            }
        }
        FeedbackWindow window = new FeedbackWindow(game, options);
        game.add(window, game.sz.sub(window.sz).div(2).max(Coord.z));
    }

    @Override
    public void reqclose() {
        syncDraft();
        if(!draft.dirty()) {
            ui.destroy(this);
            return;
        }
        showDiscardConfirmation();
    }

    @Override
    public void dispose() {
        closed = true;
        Window confirmation = discardConfirmation;
        discardConfirmation = null;
        if(confirmation != null && confirmation.parent != null)
            confirmation.destroy();
        super.dispose();
    }

    private void selectType(FeedbackType type) {
        if(attempt != null)
            return;
        draft.setType(type);
        refreshState();
    }

    private void syncDraft() {
        if(subject == null || discordContact == null || description == null)
            return;
        draft.setSubject(subject.text());
        draft.setDiscordContact(discordContact.text());
        draft.setDescription(description.text());
    }

    private void refreshState() {
        boolean locked = attempt != null;
        typeBug.tint = draft.type() == FeedbackType.BUG ? new Color(255, 190, 90) : null;
        typeSuggestion.tint = draft.type() == FeedbackType.SUGGESTION ? new Color(150, 220, 130) : null;
        typeBug.disable(locked);
        typeSuggestion.disable(locked);
        subject.setcanfocus(!locked);
        discordContact.setcanfocus(!locked);
        description.setcanfocus(!locked);
        for(Widget widget : attachmentWidgets)
            ((AttachmentWidget)widget).setLocked(locked);
        attachmentCount.settext(draft.attachments().size() + "/" + FeedbackDraft.MAX_ATTACHMENTS);
        snip.disable(locked || draft.attachments().size() >= FeedbackDraft.MAX_ATTACHMENTS);
        boolean running = attempt != null && attempt.running();
        send.disable(!config.configured() || running);
        if(!config.configured())
            status.settext(L10n.get("feedback.error.configuration"));
    }

    private void refreshAttachments() {
        for(Widget widget : attachmentWidgets)
            widget.destroy();
        attachmentWidgets.clear();
        FeedbackWindowLayout current = FeedbackWindowLayout.calculate(CONTENT_WIDTH, draft.attachments().size());
        for(int index = 0; index < draft.attachments().size(); index++) {
            AttachmentWidget widget = add(new AttachmentWidget(draft.attachments().get(index), index),
                    current.thumbnails().get(index));
            attachmentWidgets.add(widget);
        }
        refreshState();
    }

    private void startSnip() {
        if(attempt != null || draft.attachments().size() >= FeedbackDraft.MAX_ATTACHMENTS)
            return;
        FeedbackCaptureController.capture(game, this, options, attachment -> {
            if(draft.addAttachment(attachment))
                refreshAttachments();
        }, () -> { }, failure -> status.settext(L10n.get("feedback.error.capture")));
    }

    private void send() {
        syncDraft();
        if(attempt == null) {
            FeedbackDraft.Validation validation = draft.validate();
            if(validation != FeedbackDraft.Validation.OK) {
                status.settext(L10n.get(validationKey(validation)));
                return;
            }
            if(service == null) {
                status.settext(L10n.get("feedback.error.configuration"));
                return;
            }
            String reportId = "R-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            attempt = service.begin(draft.submission(reportId));
        }
        status.settext(L10n.get("feedback.status.sending"));
        send.change(L10n.get("feedback.send"));
        refreshState();
        send.disable(true);
        service.submit(attempt, new FeedbackSubmissionService.Listener() {
            public void succeeded() {
                synchronized(ui) {
                    draft.clear();
                    if(!closed)
                        ui.destroy(FeedbackWindow.this);
                    game.msg(L10n.get("feedback.status.sent"), Color.GREEN);
                }
            }

            public void failed(IOException failure) {
                synchronized(ui) {
                    if(closed)
                        return;
                    status.settext(L10n.get("feedback.error.delivery"));
                    send.change(L10n.get("feedback.retry"));
                    refreshState();
                }
            }
        });
    }

    private static String validationKey(FeedbackDraft.Validation validation) {
        switch(validation) {
        case SUBJECT_REQUIRED: return "feedback.error.subject_required";
        case SUBJECT_TOO_LONG: return "feedback.error.subject_too_long";
        case DESCRIPTION_REQUIRED: return "feedback.error.description_required";
        case DESCRIPTION_TOO_LONG: return "feedback.error.description_too_long";
        case DISCORD_CONTACT_TOO_LONG: return "feedback.error.discord_contact_too_long";
        default: return "feedback.error.delivery";
        }
    }

    private void showDiscardConfirmation() {
        if(discardConfirmation != null)
            return;
        Window confirmation = new Window(Coord.z, L10n.get("feedback.discard.title")) {
            @Override
            public void reqclose() {
                closeConfirmation();
            }
        };
        confirmation.add(new Label(L10n.get("feedback.discard.question")), Coord.z);
        confirmation.add(new Button(UI.scale(120), L10n.get("feedback.discard"), () -> {
            closeConfirmation();
            if(!closed)
                ui.destroy(FeedbackWindow.this);
        }), Coord.of(0, UI.scale(32)));
        confirmation.add(new Button(UI.scale(160), L10n.get("feedback.keep_editing"), this::closeConfirmation),
                Coord.of(UI.scale(128), UI.scale(32)));
        confirmation.pack();
        discardConfirmation = game.add(confirmation, game.sz.sub(confirmation.sz).div(2).max(Coord.z));
        discardConfirmation.raise();
    }

    private void closeConfirmation() {
        Window confirmation = discardConfirmation;
        discardConfirmation = null;
        if(confirmation != null)
            confirmation.destroy();
        if(!closed)
            raise();
    }

    private final class AttachmentWidget extends Widget {
        private final TexI texture;
        private final int index;
        private final Button remove;

        private AttachmentWidget(FeedbackAttachment attachment, int index) {
            super(Coord.of(UI.scale(181), layout.thumbnailSize()));
            this.texture = new TexI(attachment.image());
            this.index = index;
            remove = add(new Button(UI.scale(77), L10n.get("feedback.remove"), this::removeAttachment),
                    Coord.of(layout.thumbnailSize() + GAP, UI.scale(33)));
            remove.disable(attempt != null);
        }

        @Override
        public void draw(GOut output) {
            Coord fitted = fit(texture.sz(), Coord.of(layout.thumbnailSize(), layout.thumbnailSize()));
            Coord offset = Coord.of((layout.thumbnailSize() - fitted.x) / 2,
                    (layout.thumbnailSize() - fitted.y) / 2);
            output.image(texture, offset, fitted);
            output.chcolor(Color.WHITE);
            output.rect(Coord.z, Coord.of(layout.thumbnailSize(), layout.thumbnailSize()));
            output.chcolor();
            super.draw(output);
        }

        @Override
        public boolean mousedown(MouseDownEvent event) {
            if(event.b == 1 && event.c.isect(Coord.z, Coord.of(layout.thumbnailSize(), layout.thumbnailSize()))) {
                FeedbackPreviewWindow preview = new FeedbackPreviewWindow(draft.attachments().get(index));
                game.add(preview, game.sz.sub(preview.sz).div(2).max(Coord.z));
                return true;
            }
            return super.mousedown(event);
        }

        @Override
        public void dispose() {
            texture.dispose();
            super.dispose();
        }

        private void removeAttachment() {
            if(attempt != null)
                return;
            draft.removeAttachment(index);
            refreshAttachments();
        }

        private void setLocked(boolean locked) {
            remove.disable(locked);
        }
    }

    private static Coord fit(Coord source, Coord bounds) {
        double scale = Math.min(1.0, Math.min(bounds.x / (double)source.x, bounds.y / (double)source.y));
        return Coord.of(Math.max(1, (int)Math.round(source.x * scale)),
                Math.max(1, (int)Math.round(source.y * scale)));
    }
}
