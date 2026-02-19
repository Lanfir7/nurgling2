package nurgling.widgets.bots;

import haven.*;
import nurgling.NUtils;
import nurgling.conf.NChopperProp;
import nurgling.i18n.L10n;

import java.awt.Color;
import java.awt.image.BufferedImage;

public class Chopper extends Window implements Checkable {

    public String tool = null;
    CheckBox autoeat = null;
    CheckBox autorefill = null;
    CheckBox ngrowth = null;
    CheckBox stumps = null;
    CheckBox bushes = null;
    CheckBox checkWounds = null;

    UsingTools usingTools = null;
    UsingTools usingSovels = null;

    private int selectedDirection = 1;
    private Widget selDirN, selDirS, selDirE, selDirW;

    private static final Color COLOR_SEL = new Color(255, 200, 50);
    private static final int SEL_BW = 2;
    private static final int SEL_PAD = 3;
    private static final BufferedImage[] BTN_N_IMG = loadBtnSet("nurgling/hud/buttons/n/cbtn");
    private static final BufferedImage[] BTN_S_IMG = loadBtnSet("nurgling/hud/buttons/s/cbtn");
    private static final BufferedImage[] BTN_E_IMG = loadBtnSet("nurgling/hud/buttons/e/cbtn");
    private static final BufferedImage[] BTN_W_IMG = loadBtnSet("nurgling/hud/buttons/w/cbtn");
    private static final Tex WINDROSE = Resource.loadtex("nurgling/hud/tunneling/windrose");

    private static BufferedImage[] loadBtnSet(String base) {
        return new BufferedImage[]{Resource.loadsimg(base + "u"), Resource.loadsimg(base + "d"), Resource.loadsimg(base + "h")};
    }

    public Chopper() {
        super(new Coord(200,200), L10n.get("chopper.wnd_title"));
        NChopperProp startprop = NChopperProp.get(NUtils.getUI().sessInfo);
        if (startprop == null) startprop = new NChopperProp("", "");
        final NChopperProp finalStartprop = startprop;
        selectedDirection = finalStartprop.approachDirection;
        prev = add(new Label(L10n.get("chopper.settings")));
        prev = add(stumps = new CheckBox(L10n.get("chopper.uproot_stumps")){
            {
                a = finalStartprop.stumps;
            }
            @Override
            public void set(boolean a) {
                super.set(a);
                if(a)
                    usingSovels.show();
                else
                    usingSovels.hide();
            }

        }, prev.pos("bl").add(UI.scale(0,5)));

        prev = add(bushes = new CheckBox(L10n.get("chopper.cut_bushes")){
            {
                a = finalStartprop.bushes;
            }
            @Override
            public void set(boolean a) {
                super.set(a);
            }

        }, prev.pos("bl").add(UI.scale(0,5)));


        prev = add(ngrowth = new CheckBox(L10n.get("chopper.ignore_growth"))
        {
            {
                a = finalStartprop.ngrowth;
            }
            @Override
            public void set(boolean a) {
                super.set(a);
            }

        }, prev.pos("bl").add(UI.scale(0,5)));

        prev = add(autorefill = new CheckBox(L10n.get("botwnd.autorefill"))
        {
            {
                a = finalStartprop.autorefill;
            }
            @Override
            public void set(boolean a) {
                super.set(a);
            }

        }, prev.pos("bl").add(UI.scale(0,5)));

        prev = add(autoeat = new CheckBox(L10n.get("botwnd.autoeat"))
        {
            {
                a = finalStartprop.autoeat;
            }
            @Override
            public void set(boolean a) {
                super.set(a);
            }

        }, prev.pos("bl").add(UI.scale(0,5)));

        prev = add(checkWounds = new CheckBox(L10n.get("botwnd.check_wounds"))
        {
            {
                a = finalStartprop.checkWounds;
            }
            @Override
            public void set(boolean a) {
                super.set(a);
            }

        }, prev.pos("bl").add(UI.scale(0,5)));

        prev = add(usingTools = new UsingTools(UsingTools.Tools.axes), prev.pos("bl").add(UI.scale(0,5)));
        if(finalStartprop.tool!=null)
        {
            for(UsingTools.Tool tl : UsingTools.Tools.axes)
            {
                if (tl.name.equals(finalStartprop.tool)) {
                    usingTools.s = tl;
                    break;
                }
            }

        }

        add(usingSovels = new UsingTools(UsingTools.Tools.shovels, false), usingTools.pos("ur").add(UI.scale(10,usingTools.l.sz.y)));
        if(finalStartprop.shovel!=null)
        {
            for(UsingTools.Tool tl : UsingTools.Tools.shovels)
            {
                if (tl.name.equals(finalStartprop.shovel)) {
                    usingSovels.s = tl;
                    break;
                }
            }
        }
        if(!finalStartprop.stumps)
        {
            usingSovels.hide();
        }

        // Approach direction compass (right side)
        int rightX = UI.scale(215);
        int dirStartY = stumps.c.y;
        add(new Label(L10n.get("chopper.approach_side")), new Coord(rightX, dirStartY));

        IButton btnDirN = new IButton(BTN_N_IMG[0], BTN_N_IMG[1], BTN_N_IMG[2]) { @Override public void click() { setApproach(0); } };
        IButton btnDirS = new IButton(BTN_S_IMG[0], BTN_S_IMG[1], BTN_S_IMG[2]) { @Override public void click() { setApproach(1); } };
        IButton btnDirE = new IButton(BTN_E_IMG[0], BTN_E_IMG[1], BTN_E_IMG[2]) { @Override public void click() { setApproach(2); } };
        IButton btnDirW = new IButton(BTN_W_IMG[0], BTN_W_IMG[1], BTN_W_IMG[2]) { @Override public void click() { setApproach(3); } };

        Coord wrsz = WINDROSE.sz();
        int gap = UI.scale(5);
        int cx = rightX + btnDirW.sz.x + gap + wrsz.x / 2;
        int cy = dirStartY + UI.scale(20) + btnDirN.sz.y + gap + wrsz.y / 2;

        add(new Widget(wrsz) { @Override public void draw(GOut g) { g.image(WINDROSE, Coord.z); } },
            new Coord(cx - wrsz.x / 2, cy - wrsz.y / 2));

        Coord posN = new Coord(cx - btnDirN.sz.x / 2, cy - wrsz.y / 2 - gap - btnDirN.sz.y);
        Coord posS = new Coord(cx - btnDirS.sz.x / 2, cy + wrsz.y / 2 + gap);
        Coord posW = new Coord(cx - wrsz.x / 2 - gap - btnDirW.sz.x, cy - btnDirW.sz.y / 2);
        Coord posE = new Coord(cx + wrsz.x / 2 + gap, cy - btnDirE.sz.y / 2);

        selDirN = addSelFrame(btnDirN, posN); add(btnDirN, posN);
        selDirS = addSelFrame(btnDirS, posS); add(btnDirS, posS);
        selDirW = addSelFrame(btnDirW, posW); add(btnDirW, posW);
        selDirE = addSelFrame(btnDirE, posE); add(btnDirE, posE);
        updateDirSelection();

        prev = add(new Button(UI.scale(150), L10n.get("botwnd.start")){
            @Override
            public void click() {
                super.click();
                prop = NChopperProp.get(NUtils.getUI().sessInfo);
                if (prop != null) {
                    prop.autoeat = autoeat.a;
                    prop.autorefill = autorefill.a;
                    prop.stumps = stumps.a;
                    prop.ngrowth = ngrowth.a;
                    prop.bushes = bushes.a;
                    prop.checkWounds = checkWounds.a;
                    prop.approachDirection = selectedDirection;
                    if(usingTools.s!=null)
                        prop.tool = usingTools.s.name;
                    if(prop.stumps && usingSovels.s!=null)
                        prop.shovel = usingSovels.s.name;
                    NChopperProp.set(prop);
                }
                isReady = true;
            }
        }, prev.pos("bl").add(UI.scale(0,5)));
        pack();
    }

    @Override
    public boolean check() {
        return isReady;
    }

    boolean isReady = false;

    @Override
    public void wdgmsg(String msg, Object... args) {
        if(msg.equals("close")) {
            isReady = true;
            hide();
        }
        super.wdgmsg(msg, args);
    }
    public NChopperProp prop = null;

    private void setApproach(int dir) {
        selectedDirection = dir;
        updateDirSelection();
    }

    private void updateDirSelection() {
        selDirN.hide(); selDirS.hide(); selDirE.hide(); selDirW.hide();
        switch (selectedDirection) {
            case 0: selDirN.show(); break;
            case 1: selDirS.show(); break;
            case 2: selDirE.show(); break;
            case 3: selDirW.show(); break;
        }
    }

    private class SelectionFrame extends Widget {
        SelectionFrame(Coord sz) { super(sz.add(SEL_PAD * 2, SEL_PAD * 2)); }
        @Override
        public void draw(GOut g) {
            g.chcolor(COLOR_SEL);
            g.frect(Coord.z, new Coord(sz.x, SEL_BW));
            g.frect(Coord.z, new Coord(SEL_BW, sz.y));
            g.frect(new Coord(sz.x - SEL_BW, 0), new Coord(SEL_BW, sz.y));
            g.frect(new Coord(0, sz.y - SEL_BW), new Coord(sz.x, SEL_BW));
            g.chcolor();
        }
    }

    private Widget addSelFrame(IButton btn, Coord pos) {
        SelectionFrame f = new SelectionFrame(btn.sz);
        add(f, pos.sub(SEL_PAD, SEL_PAD));
        f.hide();
        return f;
    }
}
