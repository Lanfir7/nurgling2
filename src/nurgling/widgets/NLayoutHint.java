package nurgling.widgets;

import haven.*;
import nurgling.*;
import nurgling.hotkeys.Hotkeys;
import nurgling.i18n.L10n;

import java.awt.Color;
import java.util.*;

/**
 * The welcome card that tells a player the HUD is theirs to rearrange. Shown once per
 * login at the top of the screen, it lists the gestures as they are actually bound, so
 * it keeps telling the truth after the player rebinds them. It leaves on its own after
 * half a minute, on a click in the world, or for good via its own button.
 */
public class NLayoutHint extends Widget
{
    private static final String PREF = "layouthintdone";
    private static final double LIFE = 30.0, FADE = 0.6;
    private static final int pad = UI.scale(14);
    private static final int lineh = UI.scale(22);
    private static final int noteh = UI.scale(18);
    private static final int noteind = UI.scale(13);
    private static final int colgap = UI.scale(14);
    private static final int groupgap = UI.scale(10);

    private static final Text.Foundry titlefnd =
        new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD), 15, NStyle.border).aa(true);
    private static final Text.Foundry bodyfnd = new Text.Foundry(Text.sans, 12, new Color(226, 220, 206)).aa(true);
    private static final Text.Foundry dimfnd = new Text.Foundry(Text.sans, 11, NStyle.questDim).aa(true);
    private static final Text.Foundry badgefnd =
        new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD), 11, new Color(255, 226, 138)).aa(true);

    /**
     * One gesture and what it does: a headline beside the key badge, then the notes that
     * belong to that same gesture. Grouping them is what keeps the card from reading as a
     * heap of sentences with two labels dropped next to it.
     */
    private static class Group
    {
        final Text badge, head;
        final List<Text> notes = new ArrayList<>();

        Group(String badge, String head, String... notes)
        {
            this.badge = badgefnd.render(badge);
            this.head = bodyfnd.render(head);
            for(String note : notes)
                this.notes.add(dimfnd.render(note));
        }

        int height()
        {
            return(lineh + (notes.size() * noteh));
        }
    }

    private static boolean shownthissession = false;

    private final Text title, footer;
    private final List<Group> groups = new ArrayList<>();
    private final Widget hidebtn;
    private final int badgew, bodyy;
    private double life = LIFE;

    private NLayoutHint()
    {
        this.title = titlefnd.render(L10n.get("hud.layouthint.title"));
        this.footer = dimfnd.render(L10n.get("hud.layouthint.footer", (int)Math.round(LIFE)));
        groups.add(new Group(Hotkeys.action(Hotkeys.LAYOUT_ADJUST).current().displayName(),
                             L10n.get("hud.layouthint.move"),
                             L10n.get("hud.layouthint.stretch"),
                             L10n.get("hud.layouthint.slider")));
        groups.add(new Group(Hotkeys.action(Hotkeys.LAYOUT_SCALE_UP).current().displayName() + " / " +
                             Hotkeys.action(Hotkeys.LAYOUT_SCALE_DOWN).current().displayName(),
                             L10n.get("hud.layouthint.wheel"),
                             L10n.get("hud.layouthint.fixed")));

        int badgew = 0, textw = 0;
        for(Group group : groups)
        {
            badgew = Math.max(badgew, group.badge.sz().x + UI.scale(14));
            textw = Math.max(textw, group.head.sz().x);
            for(Text note : group.notes)
                textw = Math.max(textw, note.sz().x + noteind);
        }
        this.badgew = badgew;
        this.bodyy = UI.scale(28) + pad;
        int w = Math.max(UI.scale(420), (2 * pad) + badgew + colgap + textw + pad);
        w = Math.max(w, (2 * pad) + title.sz().x);
        w = Math.max(w, (2 * pad) + footer.sz().x);

        int btnw = UI.scale(190);
        int h = bodyy + bodyheight() + UI.scale(4) + footer.sz().y + UI.scale(14);
        hidebtn = add(new Button(btnw, L10n.get("hud.layouthint.hide"))
        {
            @Override
            public void click()
            {
                Utils.setprefb(PREF, true);
                NLayoutHint.this.destroy();
            }
        }, Coord.z);
        /* Bottom margin large enough that the button does not sit on the frame, and the
         * countdown line below it has room of its own. */
        h += hidebtn.sz.y + UI.scale(16);
        resize(new Coord(w, h));
        hidebtn.move(new Coord((w - btnw) / 2, h - UI.scale(14) - hidebtn.sz.y));
    }

    private int bodyheight()
    {
        int h = 0;
        for(Group group : groups)
            h += group.height() + groupgap;
        return(h);
    }

    /**
     * Put the card up unless the player has asked not to see it again. Called every
     * tick; the guards make every call after the first one a no-op.
     */
    public static void maybeshow(GameUI gui)
    {
        if(shownthissession || (gui == null) || (gui.sz == Coord.z) || Utils.getprefb(PREF, false))
            return;
        shownthissession = true;
        NLayoutHint hint = new NLayoutHint();
        gui.add(hint, new Coord(Math.max(0, (gui.sz.x - hint.sz.x) / 2), UI.scale(64))).z(900);
    }

    /** Dismiss the card, if it is up. Any click in the world means "yes, I read it". */
    public static void dismiss(UI ui)
    {
        if((ui == null) || (ui.gui == null))
            return;
        for(Widget wdg : new ArrayList<>(ui.gui.children()))
        {
            if(wdg instanceof NLayoutHint)
                wdg.destroy();
        }
    }

    @Override
    public void tick(double dt)
    {
        super.tick(dt);
        life -= dt;
        if(life <= 0)
        {
            destroy();
            return;
        }
        /* Re-centred rather than placed once, so a resized window does not leave the
         * card hanging off the edge. */
        if((parent != null) && (parent.sz != Coord.z))
            move(new Coord(Math.max(0, (parent.sz.x - sz.x) / 2), c.y));
    }

    @Override
    public void draw(GOut g)
    {
        int alpha = (int)Math.round(255 * Utils.clip(life / FADE, 0.0, 1.0));
        GOut cg = g.reclipl(Coord.z, sz);
        cg.chcolor(new Color(NStyle.windowBg.getRed(), NStyle.windowBg.getGreen(), NStyle.windowBg.getBlue(),
                             (int)Math.round(238 * (alpha / 255.0))));
        cg.frect(Coord.z, sz);
        cg.chcolor(new Color(NStyle.titleBg.getRed(), NStyle.titleBg.getGreen(), NStyle.titleBg.getBlue(),
                             (int)Math.round(245 * (alpha / 255.0))));
        cg.frect(Coord.z, new Coord(sz.x, UI.scale(28)));
        cg.chcolor(new Color(NStyle.separator.getRed(), NStyle.separator.getGreen(), NStyle.separator.getBlue(), alpha));
        cg.frect(new Coord(0, UI.scale(28)), new Coord(sz.x, Math.max(1, UI.scale(1))));
        cg.chcolor(new Color(NStyle.border.getRed(), NStyle.border.getGreen(), NStyle.border.getBlue(), alpha));
        cg.rect(Coord.z, sz);
        cg.chcolor();

        /* A stripe in the title bar's own colour, so the heading has a left edge to sit
         * against instead of floating in the corner. */
        cg.chcolor(new Color(NStyle.border.getRed(), NStyle.border.getGreen(), NStyle.border.getBlue(), alpha));
        cg.frect(new Coord(0, 0), new Coord(UI.scale(3), UI.scale(28)));
        cg.chcolor(255, 255, 255, alpha);
        cg.aimage(title.tex(), new Coord(pad, UI.scale(14)), 0, 0.5);

        int textx = pad + badgew + colgap;
        /* The rule between the key column and the text column: it is what makes the two
         * columns read as a table rather than as ragged lines. */
        cg.chcolor(new Color(NStyle.separator.getRed(), NStyle.separator.getGreen(), NStyle.separator.getBlue(), alpha));
        cg.frect(new Coord(textx - (colgap / 2), bodyy - UI.scale(4)),
                 new Coord(Math.max(1, UI.scale(1)), bodyheight() + UI.scale(4)));

        int y = bodyy;
        for(Group group : groups)
        {
            Coord bsz = new Coord(badgew, UI.scale(17));
            Coord bc = new Coord(pad, y + ((lineh - bsz.y) / 2));
            cg.chcolor(0, 0, 0, (int)Math.round(160 * (alpha / 255.0)));
            cg.frect(bc, bsz);
            cg.chcolor(new Color(NStyle.border.getRed(), NStyle.border.getGreen(), NStyle.border.getBlue(),
                                 (int)Math.round(130 * (alpha / 255.0))));
            cg.rect(bc, bsz);
            cg.chcolor(255, 255, 255, alpha);
            cg.aimage(group.badge.tex(), bc.add(bsz.x / 2, bsz.y / 2), 0.5, 0.5);
            cg.aimage(group.head.tex(), new Coord(textx, y + (lineh / 2)), 0, 0.5);
            y += lineh;
            for(Text note : group.notes)
            {
                /* A dash ties the note to the gesture above it. */
                cg.chcolor(new Color(NStyle.questDim.getRed(), NStyle.questDim.getGreen(), NStyle.questDim.getBlue(), alpha));
                cg.frect(new Coord(textx + UI.scale(2), y + (noteh / 2)), new Coord(UI.scale(5), Math.max(1, UI.scale(1))));
                cg.chcolor(255, 255, 255, alpha);
                cg.aimage(note.tex(), new Coord(textx + noteind, y + (noteh / 2)), 0, 0.5);
                y += noteh;
            }
            y += groupgap;
        }

        cg.chcolor(new Color(NStyle.separator.getRed(), NStyle.separator.getGreen(), NStyle.separator.getBlue(), alpha));
        cg.frect(new Coord(pad, y - UI.scale(3)), new Coord(sz.x - (2 * pad), Math.max(1, UI.scale(1))));
        cg.chcolor(255, 255, 255, alpha);
        cg.aimage(footer.tex(), new Coord(sz.x / 2, y + UI.scale(9)), 0.5, 0.5);
        cg.chcolor();

        /* The time left, as a line that runs out along the bottom edge. */
        cg.chcolor(new Color(0, 138, 146, (int)Math.round(200 * (alpha / 255.0))));
        cg.frect(new Coord(0, sz.y - UI.scale(2)),
                 new Coord((int)Math.round(sz.x * Utils.clip(life / LIFE, 0.0, 1.0)), UI.scale(2)));
        cg.chcolor();

        super.draw(g);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev)
    {
        /* The button still gets its click, but nothing falls through to the world behind
         * the card - which would both dismiss it and send the character walking. */
        ev.propagate(this);
        return(true);
    }
}
