package nurgling.widgets;

import haven.Coord;
import haven.GOut;
import haven.TexI;
import haven.UI;
import haven.Widget;
import haven.Window;
import nurgling.feedback.FeedbackAttachment;
import nurgling.i18n.L10n;

public final class FeedbackPreviewWindow extends Window {
    private final FeedbackAttachment attachment;
    private final TexI texture;
    private final Preview preview;

    public FeedbackPreviewWindow(FeedbackAttachment attachment) {
        super(Coord.z, L10n.get("feedback.preview"));
        this.attachment = attachment;
        this.texture = new TexI(attachment.image());
        this.preview = add(new Preview(fit(UI.scale(900), UI.scale(650))), Coord.z);
        pack();
    }

    @Override
    protected void added() {
        super.added();
        Coord maximum = parent.sz.sub(UI.scale(80, 110)).max(UI.scale(100, 80));
        preview.resize(fit(maximum.x, maximum.y));
        pack();
        c = parent.sz.sub(sz).div(2).max(Coord.z);
    }

    @Override
    public void reqclose() {
        ui.destroy(this);
    }

    @Override
    public void dispose() {
        texture.dispose();
        super.dispose();
    }

    private Coord fit(int maximumWidth, int maximumHeight) {
        double scale = Math.min(1.0, Math.min(
                maximumWidth / (double)attachment.width(),
                maximumHeight / (double)attachment.height()));
        return Coord.of(Math.max(1, (int)Math.round(attachment.width() * scale)),
                Math.max(1, (int)Math.round(attachment.height() * scale)));
    }

    private final class Preview extends Widget {
        private Preview(Coord size) {
            super(size);
        }

        @Override
        public void draw(GOut output) {
            output.image(texture, Coord.z, sz);
        }
    }
}
