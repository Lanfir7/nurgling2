package nurgling.widgets;

import haven.Button;
import haven.Coord;
import haven.GOut;
import haven.TextEntry;
import haven.Tex;
import haven.UI;
import haven.Widget;
import haven.Window;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.agent.runtime.AgentEventListener;
import nurgling.agent.runtime.AgentRuntime;

public class AgentWindow extends Window implements AgentEventListener {
    private static final Coord MIN_SZ = UI.scale(560, 420);
    private static final Coord PAD = UI.scale(10, 10);
    private static final Coord BTN_SZ = UI.scale(80, 28);
    private static final Tex SIZER = Window.sizer;

    private final NGameUI gui;
    private final AgentRuntime runtime;
    private final AgentTextlog log;
    private final TextEntry input;
    private final Button startBtn;
    private final Button stopBtn;
    private final Button clearBtn;
    private final Button upBtn;
    private final Button downBtn;
    private UI.Grab resizeGrab;
    private Coord resizeAnchor;
    private Coord startContentSize;

    public AgentWindow(NGameUI gui) {
        super(UI.scale(660, 460), "LLM Agent");
        this.gui = gui;
        this.runtime = new AgentRuntime(gui);
        this.runtime.setListener(this);

        log = add(new AgentTextlog(UI.scale(640, 340)), PAD);
        input = add(new TextEntry(UI.scale(440), "") {
            @Override
            public void activate(String text) {
                sendPrompt(text);
            }
        }, UI.scale(10, 300));
        input.canactivate = true;

        startBtn = add(new Button(BTN_SZ.x, "Run"), UI.scale(400, 300));
        startBtn.action(() -> sendPrompt(input.text()));
        stopBtn = add(new Button(BTN_SZ.x, "Stop"), UI.scale(455, 300));
        stopBtn.action(() -> runtime.stop());
        clearBtn = add(new Button(BTN_SZ.x, "Clear"), UI.scale(510, 300));
        clearBtn.action(() -> runtime.clearContext());
        upBtn = add(new Button(BTN_SZ.x, "+"), UI.scale(565, 300));
        upBtn.action(() -> runtime.addManualFeedback(+1));
        downBtn = add(new Button(BTN_SZ.x, "-"), UI.scale(620, 300));
        downBtn.action(() -> runtime.addManualFeedback(-1));

        layoutControls();
    }

    private void sendPrompt(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if ("/clear".equalsIgnoreCase(text.trim())) {
            runtime.clearContext();
            input.settext("");
            input.buf.point(0);
            return;
        }
        if ("/up".equalsIgnoreCase(text.trim())) {
            runtime.addManualFeedback(+1);
            input.settext("");
            input.buf.point(0);
            return;
        }
        if ("/down".equalsIgnoreCase(text.trim())) {
            runtime.addManualFeedback(-1);
            input.settext("");
            input.buf.point(0);
            return;
        }
        log.append("YOU: " + text);
        runtime.submitPrompt(text);
        input.settext("");
        input.buf.point(0);
        NConfig.set(NConfig.Key.agentAutoMode, true);
    }

    @Override
    public boolean keydown(Widget.KeyDownEvent ev) {
        if (ev.code == java.awt.event.KeyEvent.VK_ESCAPE) {
            hide();
            return true;
        }
        return super.keydown(ev);
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if ((sender == this) && "close".equals(msg)) {
            hide();
            return;
        }
        super.wdgmsg(sender, msg, args);
    }

    @Override
    public void hide() {
        super.hide();
    }

    @Override
    public boolean mousedown(Widget.MouseDownEvent ev) {
        Coord hsz = SIZER.sz();
        Coord corner = sz.sub(hsz);
        if (ev.b == 1 && ev.c.isect(corner, hsz)) {
            resizeGrab = ui.grabmouse(this);
            resizeAnchor = ev.c;
            startContentSize = csz();
            return true;
        }
        return super.mousedown(ev);
    }

    @Override
    public void mousemove(Widget.MouseMoveEvent ev) {
        if (resizeGrab != null) {
            Coord delta = ev.c.sub(resizeAnchor);
            Coord target = startContentSize.add(delta);
            if (target.x < MIN_SZ.x) target.x = MIN_SZ.x;
            if (target.y < MIN_SZ.y) target.y = MIN_SZ.y;
            resize(target);
            return;
        }
        super.mousemove(ev);
    }

    @Override
    public boolean mouseup(Widget.MouseUpEvent ev) {
        if (ev.b == 1 && resizeGrab != null) {
            resizeGrab.remove();
            resizeGrab = null;
            return true;
        }
        return super.mouseup(ev);
    }

    @Override
    public void resize(Coord sz) {
        super.resize(sz);
        layoutControls();
    }

    private void layoutControls() {
        Coord csz = csz();
        int innerW = csz.x - PAD.x * 2;
        int topY = PAD.y;
        int inputY = csz.y - PAD.y - input.sz.y;

        Coord logSz = new Coord(innerW, Math.max(UI.scale(200), inputY - topY - PAD.y));
        log.resize(logSz);
        log.c = new Coord(PAD.x, topY);

        int rightButtonsW = BTN_SZ.x * 5 + UI.scale(32);
        int inputW = Math.max(UI.scale(220), innerW - rightButtonsW - UI.scale(8));
        input.resize(inputW);
        input.c = new Coord(PAD.x, inputY);

        startBtn.c = new Coord(input.c.x + inputW + UI.scale(8), input.c.y);
        stopBtn.c = new Coord(startBtn.c.x + BTN_SZ.x + UI.scale(8), input.c.y);
        clearBtn.c = new Coord(stopBtn.c.x + BTN_SZ.x + UI.scale(8), input.c.y);
        upBtn.c = new Coord(clearBtn.c.x + BTN_SZ.x + UI.scale(8), input.c.y);
        downBtn.c = new Coord(upBtn.c.x + BTN_SZ.x + UI.scale(8), input.c.y);
    }

    @Override
    public void draw(GOut g) {
        super.draw(g);
        g.image(SIZER, sz.sub(SIZER.sz()));
    }

    @Override
    public void onLog(String line) {
        if (line == null) return;
        if (AgentRuntime.isUserVisibleLine(line)) {
            log.append(line);
        }
    }
}
