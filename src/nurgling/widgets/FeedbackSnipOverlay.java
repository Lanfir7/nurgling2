package nurgling.widgets;

import haven.Coord;
import haven.GOut;
import haven.TexI;
import haven.UI;
import haven.Widget;
import nurgling.feedback.SnipSelection;
import nurgling.i18n.L10n;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class FeedbackSnipOverlay extends Widget {
    private static final int MINIMUM_SIZE = 8;

    private final BufferedImage frame;
    private final TexI frameTexture;
    private final Consumer<BufferedImage> selected;
    private final Runnable cancelled;
    private final AtomicBoolean closed = new AtomicBoolean();
    private Coord start;
    private Coord end;
    private UI.Grab mouseGrab;
    private UI.Grab keyGrab;

    public FeedbackSnipOverlay(BufferedImage frame, Consumer<BufferedImage> selected, Runnable cancelled) {
        super(Coord.of(frame.getWidth(), frame.getHeight()));
        this.frame = frame;
        this.frameTexture = new TexI(frame);
        this.selected = selected;
        this.cancelled = cancelled;
        setcanfocus(true);
        z(10000);
    }

    @Override
    protected void added() {
        super.added();
        keyGrab = ui.grabkeys(this);
    }

    @Override
    public void draw(GOut g) {
        g.image(frameTexture, Coord.z);
        Optional<Rectangle> selection = currentSelection(1);
        g.chcolor(0, 0, 0, 155);
        if(selection.isPresent()) {
            Rectangle rectangle = selection.get();
            g.frect(Coord.z, Coord.of(sz.x, rectangle.y));
            g.frect(Coord.of(0, rectangle.y + rectangle.height),
                    Coord.of(sz.x, Math.max(0, sz.y - rectangle.y - rectangle.height)));
            g.frect(Coord.of(0, rectangle.y), Coord.of(rectangle.x, rectangle.height));
            g.frect(Coord.of(rectangle.x + rectangle.width, rectangle.y),
                    Coord.of(Math.max(0, sz.x - rectangle.x - rectangle.width), rectangle.height));
            g.chcolor(new Color(255, 225, 40));
            g.rect(Coord.of(rectangle.x, rectangle.y), Coord.of(rectangle.width, rectangle.height));
        } else {
            g.frect(Coord.z, sz);
        }
        g.chcolor(Color.WHITE);
        g.atext(L10n.get("feedback.snip.instructions"), Coord.of(sz.x / 2, UI.scale(18)), 0.5, 0.0);
        g.chcolor();
    }

    @Override
    public boolean mousedown(MouseDownEvent event) {
        if(event.b == 3) {
            cancel();
            return true;
        }
        if(event.b == 1) {
            start = new Coord(event.c);
            end = new Coord(event.c);
            releaseMouse();
            mouseGrab = ui.grabmouse(this);
        }
        return true;
    }

    @Override
    public void mousemove(MouseMoveEvent event) {
        if(start != null)
            end = new Coord(event.c);
    }

    @Override
    public boolean mouseup(MouseUpEvent event) {
        if(event.b != 1 || start == null)
            return true;
        end = new Coord(event.c);
        releaseMouse();
        Optional<Rectangle> selection = currentSelection(MINIMUM_SIZE);
        if(selection.isPresent())
            select(SnipSelection.crop(frame, selection.get()));
        else
            cancel();
        return true;
    }

    @Override
    public boolean keydown(KeyDownEvent event) {
        if(key_esc.match(event)) {
            cancel();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyup(KeyUpEvent event) {
        return true;
    }

    @Override
    public boolean mousewheel(MouseWheelEvent event) {
        return true;
    }

    @Override
    public void destroy() {
        releaseMouse();
        if(keyGrab != null) {
            keyGrab.remove();
            keyGrab = null;
        }
        frameTexture.dispose();
        super.destroy();
    }

    private Optional<Rectangle> currentSelection(int minimumSize) {
        if(start == null || end == null)
            return Optional.empty();
        return SnipSelection.rectangle(start, end, sz, minimumSize);
    }

    private void select(BufferedImage crop) {
        if(!closed.compareAndSet(false, true))
            return;
        ui.destroy(this);
        selected.accept(crop);
    }

    private void cancel() {
        if(!closed.compareAndSet(false, true))
            return;
        ui.destroy(this);
        cancelled.run();
    }

    private void releaseMouse() {
        if(mouseGrab != null) {
            mouseGrab.remove();
            mouseGrab = null;
        }
    }
}
