package haven;

import java.awt.*;

public class QuestObjectivesWindow extends Window {
    public QuestObjectivesWindow() {
        super(Coord.z,"Quest Objectives");
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if((sender == this) && (msg == "close")) {
            ui.gui.chrwdg.quest.cqst.unselect(1);
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }

    public void cresize(Widget ch) {
        pack();
        if (parent != null)
            presize();
    }

    @Override
    public void cdestroy(Widget w) {
        hide();
        if (ui != null && ui.gui != null)
            ui.gui.qqview = null;
        super.cdestroy(w);
    }


    @Override
    public <T extends Widget> T add(T child) {
        show();
        return super.add(child);
    }


    public void resetDeco() {
        if (OptWnd.transparentQuestsObjectivesWindowCheckBox.a) {
            chdeco(new DefaultDeco() {
                @Override
                protected void drawbg(GOut g) {
                }

                @Override
                protected void drawframe(GOut g) {
                }

                @Override
                public boolean mousedown(MouseDownEvent ev) {
                    if(ev.propagate(this))
                        return(true);
                    if(checkhit(ev.c)) {
                        Window wnd = (Window)parent;
                        wnd.parent.setfocus(wnd);
                        wnd.raise();
                        if(ev.b == 1)
                            wnd.drag(ev.c);
                        return(true);
                    }
                    return(super.mousedown(ev));
                }
            });
        } else {
            chdeco(new DefaultDeco() {
                @Override
                public boolean mousedown(MouseDownEvent ev) {
                    if(ev.propagate(this))
                        return(true);
                    if(checkhit(ev.c)) {
                        Window wnd = (Window)parent;
                        wnd.parent.setfocus(wnd);
                        wnd.raise();
                        if(ev.b == 1)
                            wnd.drag(ev.c);
                        return(true);
                    }
                    return(super.mousedown(ev));
                }
            });
        }
    }

    @Override
    protected void added() {
        super.added();
        if (deco instanceof DefaultDeco)
            ((DefaultDeco)deco).cbtn.hide();
    }

    @Override
    public void chdeco(Deco deco) {
        super.chdeco(deco);
        if (deco instanceof DefaultDeco)
            ((DefaultDeco)deco).cbtn.hide();
    }
}
