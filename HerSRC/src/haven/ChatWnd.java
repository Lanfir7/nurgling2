package haven;

public class ChatWnd extends Window {
    GameUI gui;

    public ChatWnd(Coord sz, String cap, GameUI gui) {
        super(sz, cap);
        this.gui = gui;
    }

    protected Deco makedeco() {
        return(new DefaultDeco(true){

            @Override
            public void mousemove(MouseMoveEvent ev) {
                if (szdrag != null) {
                    gui.chat.resize(sz.x - UI.scale(54), sz.y - UI.scale(51));
                }
                super.mousemove(ev);
            }

            @Override
            public boolean mouseup(MouseUpEvent ev) {
                fixWindowPosition();
                if (szdrag != null) {
                    gui.chat.resize(sz.x - UI.scale(54), sz.y - UI.scale(51));
                }
                return super.mouseup(ev);
            }

            @Override
            public boolean checkhit(Coord c) {
                Coord cpc = c.sub(cptl);
                Coord cpsz2 = new Coord(cpsz.x + (UI.scale(14)), cpsz.y); // ND: Fix top-right corner drag not working. It's just some stupid bug involving ALL OF THIS SPAGHETTI CODE.
                return(ca.contains(c) || (c.isect(cptl, cpsz2) && (cm.back.getRaster().getSample(cpc.x % cm.back.getWidth(), cpc.y, 3) >= 128)));
            }

        }.dragsize(true));
    }

    @Override
    protected void added() { // ND: Resize the chat widget to match the chat window, after the window is added to the GUI
        super.added();
        if (deco instanceof DefaultDeco)
            ((DefaultDeco)deco).cbtn.hide();
        gui.chat.resize(sz.x - UI.scale(54), sz.y - UI.scale(51));
    }

    @Override
    public void resize(Coord sz) {
        sz.x = Math.max(sz.x, UI.scale(280));
        sz.y = Math.max(sz.y, UI.scale(58));
        super.resize(sz);
        Utils.setprefc("wndsz-chat", sz);
    }

    public void fixWindowPosition(){
        // ND: This prevents us from resizing it larger than the game window size
        if(this.csz().x > gui.sz.x - dlmrgn.x * 2 - dsmrgn.x) this.resize(gui.sz.x - dlmrgn.x * 2 - dsmrgn.x, this.csz().y);
        if(this.csz().y > gui.sz.y - (int)(dlmrgn.y * 3.2)) this.resize(this.csz().x, gui.sz.y - (int)(dlmrgn.y * 3.2));
        // ND: This prevents us from dragging it outside at all
        if (this.c.x < -dsmrgn.x) this.c.x = -dsmrgn.x;
        if (this.c.y < -dsmrgn.y) this.c.y = -dsmrgn.y;
        if (this.c.x > (gui.sz.x - this.csz().x - (dlmrgn.x + dsmrgn.x) * 2)) this.c.x = gui.sz.x - this.csz().x - (dlmrgn.x + dsmrgn.x) * 2;
        if (this.c.y > (gui.sz.y - this.csz().y - (int)(dlmrgn.y * 3.9))) this.c.y = gui.sz.y - this.csz().y - (int)(dlmrgn.y * 3.9);
    }

    @Override
    public void preventDraggingOutside() {
        fixWindowPosition();
    }
}
