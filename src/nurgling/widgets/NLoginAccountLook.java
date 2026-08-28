package nurgling.widgets;

import haven.*;
import nurgling.NConfig;
import nurgling.conf.FontSettings;
import nurgling.sessions.SessionTabBar;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Login-account cards painted like {@link SessionTabBar} session tabs, without portraits. */
public final class NLoginAccountLook {
    /** Session tabs are {@code UI.scale(180)} because of the avatar; login cards skip that slot. */
    public static final int CARD_WIDTH = UI.scale(128);
    public static final int CARD_HEIGHT = UI.scale(24);
    public static final int CARD_PADDING = UI.scale(4);

    static final Color ACTIVE_BORDER = new Color(0x99, 0xFF, 0x84);
    static final Color IDLE_BORDER = new Color(0x91, 0x60, 0x2E);
    static final Color ACTIVE_TEXT = Color.WHITE;
    static final Color IDLE_TEXT = new Color(190, 178, 156);

    private static Text.Forge nameFurnace;
    private static Tex closeNormal, closeHover;
    private static final Map<String, Tex> nameCache = new HashMap<>();
    private static final Map<String, Tex> panelCache = new HashMap<>();
    private static boolean loaded;

    private NLoginAccountLook() {}

    public static Color accent(boolean highlighted) {
        return highlighted ? ACTIVE_BORDER : IDLE_BORDER;
    }

    public static Color nameColor(boolean highlighted) {
        return highlighted ? ACTIVE_TEXT : IDLE_TEXT;
    }

    static Coord closeUl(Coord sz) {
        return new Coord(sz.x - SessionTabBar.CLOSE_BTN_SIZE - SessionTabBar.CLOSE_BTN_MARGIN,
                SessionTabBar.CLOSE_BTN_MARGIN);
    }

    static boolean inClose(Coord c, Coord sz) {
        Coord ul = closeUl(sz);
        return c.isect(ul, new Coord(SessionTabBar.CLOSE_BTN_SIZE, SessionTabBar.CLOSE_BTN_SIZE));
    }

    static void ensure() {
        if (loaded)
            return;
        try {
            Text.Foundry titleFoundry;
            try {
                FontSettings fontSettings = (FontSettings) NConfig.get(NConfig.Key.fonts);
                titleFoundry = QuestHeadingFont.from(fontSettings.getFoundary(
                        nurgling.widgets.nsettings.Fonts.FontType.QUESTS));
            } catch (Exception e) {
                titleFoundry = QuestHeadingFont.from(null);
            }
            nameFurnace = new PUtils.BlurFurn(
                    new PUtils.TexFurn(titleFoundry, Window.ctex),
                    UI.scale(1), UI.scale(1), Color.BLACK);
            closeNormal = Resource.loadtex("nurgling/hud/sessions/close/10x10");
            closeHover = Resource.loadtex("nurgling/hud/sessions/close/10x10_hover");
            loaded = true;
        } catch (Exception e) {
            System.err.println("[NLoginAccountLook] Failed to load chrome: " + e.getMessage());
        }
    }

    static void drawCard(GOut g, Coord sz, String name, boolean hovered, boolean closeHovered) {
        ensure();
        Color accent = accent(hovered);
        Coord ul = Coord.z;
        Tex panel = panelOf(sz);
        if (panel != null) {
            g.chcolor(hovered ? new Color(255, 255, 255, 250) : new Color(214, 214, 214, 234));
            g.image(panel, ul);
            g.chcolor();
        } else {
            g.chcolor(0x11, 0x14, 0x13, 230);
            g.frect(ul, sz);
            g.chcolor();
        }

        if (hovered) {
            for (int i = 1; i <= 3; i++) {
                g.chcolor(accent.getRed(), accent.getGreen(), accent.getBlue(), 54 / i);
                g.rect(ul.sub(i, i), sz.add(i * 2, i * 2));
            }
        }
        g.chcolor(accent.getRed(), accent.getGreen(), accent.getBlue(), hovered ? 255 : 140);
        g.rect(ul, sz);
        g.chcolor();
        g.chcolor(accent);
        g.frect(ul.add(0, UI.scale(3)), new Coord(UI.scale(2), sz.y - UI.scale(6)));
        g.chcolor();
        drawCorners(g, ul, sz, accent, hovered ? 220 : 90);

        int textX = UI.scale(8);
        if (nameFurnace != null) {
            Tex nameTex = cached(nameCache, name, s -> nameFurnace.render(s).tex());
            g.chcolor(nameColor(hovered));
            g.aimage(nameTex, new Coord(textX, sz.y / 2), 0, 0.5);
            g.chcolor();
        }

        Coord cul = closeUl(sz);
        Tex icon = closeHovered ? closeHover : closeNormal;
        if (icon != null) {
            g.chcolor(255, 255, 255, closeHovered ? 255 : 160);
            g.image(icon, cul, new Coord(SessionTabBar.CLOSE_BTN_SIZE, SessionTabBar.CLOSE_BTN_SIZE));
            g.chcolor();
        }
    }

    private static Tex panelOf(Coord sz) {
        String key = sz.x + "x" + sz.y;
        Tex cached = panelCache.get(key);
        if (cached != null)
            return cached;
        try {
            Tex panel = mkpanel(sz.x, sz.y, new Color(0x36, 0x3C, 0x38), new Color(0x11, 0x14, 0x13));
            if (panelCache.size() > 8)
                panelCache.clear();
            panelCache.put(key, panel);
            return panel;
        } catch (Exception e) {
            return null;
        }
    }

    private static Tex mkpanel(int w, int h, Color top, Color bottom) {
        BufferedImage img = TexI.mkbuf(new Coord(w, h));
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 255, 255, 34));
        g.drawLine(1, 1, w - 2, 1);
        g.setColor(new Color(0, 0, 0, 120));
        g.drawLine(1, h - 2, w - 2, h - 2);
        g.dispose();
        int chamfer = UI.scale(2);
        for (int y = 0; y < chamfer; y++) {
            for (int x = 0; x < chamfer - y; x++) {
                img.getRaster().setSample(x, y, 3, 0);
                img.getRaster().setSample(w - 1 - x, y, 3, 0);
                img.getRaster().setSample(x, h - 1 - y, 3, 0);
                img.getRaster().setSample(w - 1 - x, h - 1 - y, 3, 0);
            }
        }
        return (new TexI(img));
    }

    private static void drawCorners(GOut g, Coord ul, Coord bsz, Color c, int alpha) {
        int len = UI.scale(7), thick = UI.scale(2);
        g.chcolor(c.getRed(), c.getGreen(), c.getBlue(), alpha);
        g.frect(ul, new Coord(len, thick));
        g.frect(ul, new Coord(thick, len));
        g.frect(ul.add(bsz.x - len, 0), new Coord(len, thick));
        g.frect(ul.add(bsz.x - thick, 0), new Coord(thick, len));
        g.frect(ul.add(0, bsz.y - thick), new Coord(len, thick));
        g.frect(ul.add(0, bsz.y - len), new Coord(thick, len));
        g.frect(ul.add(bsz.x - len, bsz.y - thick), new Coord(len, thick));
        g.frect(ul.add(bsz.x - thick, bsz.y - len), new Coord(thick, len));
        g.chcolor();
    }

    private static Tex cached(Map<String, Tex> cache, String key, java.util.function.Function<String, Tex> mk) {
        Tex ret = cache.get(key);
        if (ret == null) {
            if (cache.size() > 64)
                cache.clear();
            cache.put(key, ret = mk.apply(key));
        }
        return ret;
    }
}
