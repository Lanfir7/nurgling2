package nurgling.sessions;

import haven.*;
import haven.Widget.*;
import nurgling.NConfig;
import nurgling.NCore;
import nurgling.NGameUI;
import nurgling.NStyle;
import nurgling.NUI;
import nurgling.NUtils;
import nurgling.conf.FontSettings;
import nurgling.widgets.QuestHeadingFont;
import nurgling.conf.NDragProp;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * A draggable widget that displays buttons for all active sessions.
 * Allows switching between sessions, closing sessions, and adding new accounts.
 */
public class SessionTabBar extends Widget {
    /** Button dimensions */
    public static final int BUTTON_HEIGHT = UI.scale(40);
    public static final int BUTTON_WIDTH = UI.scale(180);
    /** Close button size (inside session button, top right) */
    public static final int CLOSE_BTN_SIZE = UI.scale(10);
    public static final int CLOSE_BTN_MARGIN = UI.scale(4);
    /** Plus button dimensions */
    public static final int PLUS_BTN_SIZE = UI.scale(18);
    public static final int PLUS_BAR_HEIGHT = UI.scale(20);
    /** Padding between buttons */
    public static final int BUTTON_PADDING = UI.scale(4);
    /** Status icon size (inside session button, bottom right) */
    public static final int STATUS_ICON_SIZE = UI.scale(16);
    /** Character portrait */
    public static final int AVA_SIZE = UI.scale(32);
    public static final int AVA_MARGIN = UI.scale(4);
    /** Horizontal inset of the tabs when the drag frame is shown */
    private static final Coord DRAG_INSET = UI.scale(15, 34);
    public static final int STATUS_ICON_MARGIN = UI.scale(3);
    /** Where the bar sits before the user ever moves it - a small inset from the top-left corner. */
    public static final Coord DEFAULT_POS = UI.scale(new Coord(10, 10));

    /** Colors for different states */
    private static final Color ACTIVE_BORDER = new Color(0x99, 0xFF, 0x84);    // #99FF84
    private static final Color ACTIVE_TEXT = new Color(255, 255, 255);         // White
    private static final Color BOT_BORDER = new Color(0xE9, 0x9C, 0x54);       // #E99C54
    private static final Color BOT_TEXT = new Color(0xE9, 0x9C, 0x54);         // #E99C54
    private static final Color IDLE_BORDER = new Color(0x91, 0x60, 0x2E);      // #91602E
    private static final Color IDLE_TEXT = new Color(190, 178, 156);
    private static final Color COMBAT_BORDER = new Color(0xFF, 0x64, 0x64);    // #FF6464
    private static final Color COMBAT_TEXT = new Color(0xFF, 0x64, 0x64);      // #FF6464
    private static final Color SUB_TEXT = new Color(0x9A, 0x92, 0x82);
    /** Alarm outranks every other state - it is the one thing the user can otherwise miss. */
    private static final Color ALARM_BORDER = new Color(0xFF, 0x3B, 0x3B);     // #FF3B3B
    private static final Color ALARM_BORDER_ALT = new Color(0xFF, 0xF0, 0xA0); // #FFF0A0
    private static final Color ALARM_TEXT = new Color(0xFF, 0x3B, 0x3B);       // #FF3B3B
    /** Ticks per half-cycle of the alarm border pulse (~1.5Hz at 60fps). */
    private static final int ALARM_PULSE_TICKS = 20;
    private static final Color CLOSE_BTN_COLOR = new Color(180, 80, 80);
    private static final Color CLOSE_BTN_HOVER = new Color(220, 100, 100);
    private static final Color PLUS_BTN_BG = new Color(0x25, 0x2B, 0x29, 0xE5);
    private static final Color PLUS_BTN_HOVER = new Color(0x35, 0x3B, 0x39, 0xE5);
    private static final Color PLUS_BTN_BORDER = new Color(0x91, 0x60, 0x2E);  // #91602E

    /** Icon resources */
    private static Tex gearIcon;
    private static Tex warningIcon;
    private static Tex closeNormal, closeHover, closePush;
    private static Tex addNormal, addHover, addPush;
    private static Tex tabPanel, plusPanel;
    private static boolean resourcesLoaded = false;

    /** Fonts (static so shared across instances) */
    private static Text.Forge nameFurnace;
    private static Text.Foundry subFoundry;
    private static Tex plusLabel;

    /** Rendered text cache, keyed by string */
    private static final Map<String, Tex> nameCache = new HashMap<>();
    private static final Map<String, Tex> subCache = new HashMap<>();
    private static final Map<String, String> fitCache = new HashMap<>();

    /** Portraits, keyed by session id */
    private final Map<String, SessionAvatar> avatars = new HashMap<>();

    /** Currently hovered button index (-1 = none, -2 = plus button) */
    private int hoveredButton = -1;
    /** Currently hovered close button index (-1 = none) */
    private int hoveredCloseButton = -1;

    /** Drag state */
    private UI.Grab dm = null;
    private Coord doff;
    private Coord dragStartPos;
    private int dragStartButton = -1;
    private static final int DRAG_THRESHOLD = 3; // pixels to move before starting drag

    /** Keybindings - static so they can be accessed from NGameUI.globtype() */
    public static final KeyBinding kb_session1 = KeyBinding.get("session-1", KeyMatch.forcode(KeyEvent.VK_1, KeyMatch.M));
    public static final KeyBinding kb_session2 = KeyBinding.get("session-2", KeyMatch.forcode(KeyEvent.VK_2, KeyMatch.M));
    public static final KeyBinding kb_session3 = KeyBinding.get("session-3", KeyMatch.forcode(KeyEvent.VK_3, KeyMatch.M));
    public static final KeyBinding kb_session4 = KeyBinding.get("session-4", KeyMatch.forcode(KeyEvent.VK_4, KeyMatch.M));
    public static final KeyBinding kb_session5 = KeyBinding.get("session-5", KeyMatch.forcode(KeyEvent.VK_5, KeyMatch.M));
    public static final KeyBinding kb_session6 = KeyBinding.get("session-6", KeyMatch.forcode(KeyEvent.VK_6, KeyMatch.M));
    public static final KeyBinding kb_session7 = KeyBinding.get("session-7", KeyMatch.forcode(KeyEvent.VK_7, KeyMatch.M));
    public static final KeyBinding kb_session8 = KeyBinding.get("session-8", KeyMatch.forcode(KeyEvent.VK_8, KeyMatch.M));
    public static final KeyBinding kb_session9 = KeyBinding.get("session-9", KeyMatch.forcode(KeyEvent.VK_9, KeyMatch.M));
    public static final KeyBinding kb_session10 = KeyBinding.get("session-10", KeyMatch.forcode(KeyEvent.VK_0, KeyMatch.M));
    public static final KeyBinding kb_session_next = KeyBinding.get("session-next", KeyMatch.forcode(KeyEvent.VK_CLOSE_BRACKET, KeyMatch.M));
    public static final KeyBinding kb_session_prev = KeyBinding.get("session-prev", KeyMatch.forcode(KeyEvent.VK_OPEN_BRACKET, KeyMatch.M));

    /** Array of session keybindings for easy iteration */
    public static final KeyBinding[] SESSION_BINDINGS = {
        kb_session1, kb_session2, kb_session3, kb_session4, kb_session5,
        kb_session6, kb_session7, kb_session8, kb_session9, kb_session10
    };

    /** Callback for when add account is clicked */
    private Runnable onAddAccount;

    /** Drag mode controls */
    private ICheckBox btnLock;
    private ICheckBox btnVis;
    private static TexI label;

    /** Drag mode resources */
    public static final IBox box = Window.wbox;
    private static Tex ctl;
    private static final Coord controlOffset = UI.scale(10, 10);
    public static Text.Furnace labelFont = new PUtils.BlurFurn(
        new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD), 14, Color.YELLOW).aa(true),
        UI.scale(1), UI.scale(2), Color.BLACK
    );

    public SessionTabBar() {
        super(Coord.z);

        // Note: Resource loading is deferred to ensureResourcesLoaded()
        // which is called on first draw() to avoid blocking during initialization

        // Create lock button
        add(btnLock = new ICheckBox(NStyle.locki[0], NStyle.locki[1], NStyle.locki[2], NStyle.locki[3]) {
            @Override
            public void changed(boolean val) {
                super.changed(val);
                saveDragState();
            }
        }, new Coord(0, 0)); // Position will be updated in updateSize()

        // Create visibility button
        add(btnVis = new ICheckBox(NStyle.visi[0], NStyle.visi[1], NStyle.visi[2], NStyle.visi[3]) {
            @Override
            public void changed(boolean val) {
                super.changed(val);
                // Don't set this.visible - just save the state
                // The draw method will check btnVis.a to decide what to show
                saveDragState();
            }
        }, new Coord(0, 0)); // Position will be updated in updateSize()

        // Hide buttons initially (shown in drag mode)
        btnLock.hide();
        btnVis.hide();

        // Load saved position and state
        loadPosition();
        loadDragState();

        // Calculate initial size
        updateSize();
    }

    /**
     * Lazy-load resources on first draw to avoid blocking during initialization.
     * This prevents RenderTree$SlotRemoved errors during character selection.
     */
    private void ensureResourcesLoaded() {
        if (resourcesLoaded) return;

        try {
            // Load icon textures
            gearIcon = Resource.loadtex("nurgling/hud/sessions/icons/gear");
            warningIcon = Resource.loadtex("nurgling/hud/sessions/icons/warning");
            closeNormal = Resource.loadtex("nurgling/hud/sessions/close/10x10");
            closeHover = Resource.loadtex("nurgling/hud/sessions/close/10x10_hover");
            closePush = Resource.loadtex("nurgling/hud/sessions/close/10x10_push");
            addNormal = Resource.loadtex("nurgling/hud/buttons/add_session/18x18");
            addHover = Resource.loadtex("nurgling/hud/buttons/add_session/18x18_hover");
            addPush = Resource.loadtex("nurgling/hud/buttons/add_session/18x18_push");
            ctl = Resource.loadtex("nurgling/hud/box/tl");

            // Load fonts
            Text.Foundry titleFoundry;
            Font bodyFont;
            try {
                FontSettings fontSettings = (FontSettings) NConfig.get(NConfig.Key.fonts);
                titleFoundry = QuestHeadingFont.from(fontSettings.getFoundary(
                    nurgling.widgets.nsettings.Fonts.FontType.QUESTS));
                bodyFont = FontSettings.getOpenSans();
            } catch (Exception e) {
                titleFoundry = QuestHeadingFont.from(null);
                bodyFont = Text.sans;
            }
            // Gilded, engraved caption in the same spirit as the game's window titles
            nameFurnace = new PUtils.BlurFurn(
                new PUtils.TexFurn(titleFoundry, Window.ctex),
                UI.scale(1), UI.scale(1), Color.BLACK);
            subFoundry = new Text.Foundry(bodyFont, 9, Color.WHITE).aa(true);
            plusLabel = subFoundry.render("New session").tex();

            tabPanel = mkpanel(BUTTON_WIDTH, BUTTON_HEIGHT,
                               new Color(0x36, 0x3C, 0x38), new Color(0x11, 0x14, 0x13));
            plusPanel = mkpanel(BUTTON_WIDTH, PLUS_BAR_HEIGHT,
                                new Color(0x2A, 0x2F, 0x2C), new Color(0x14, 0x17, 0x16));

            // Create label
            label = new TexI(labelFont.render("Sessions").img);

            resourcesLoaded = true;
        } catch (Exception e) {
            System.err.println("Failed to load session tab resources: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Build a beveled panel: vertical gradient, a lit top edge, a shaded bottom edge
     * and clipped corners so the colored border drawn over it reads as a chamfer.
     */
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

    private static Tex cached(Map<String, Tex> cache, String key, java.util.function.Function<String, Tex> mk) {
        Tex ret = cache.get(key);
        if (ret == null) {
            if (cache.size() > 64)
                cache.clear();
            cache.put(key, ret = mk.apply(key));
        }
        return (ret);
    }

    /**
     * Load widget position from preferences.
     */
    private void loadPosition() {
        String posStr = Utils.getpref("sessionbar-pos", DEFAULT_POS.x + "," + DEFAULT_POS.y);
        try {
            String[] parts = posStr.split(",");
            if (parts.length == 2) {
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                this.c = new Coord(x, y);
            }
        } catch (Exception e) {
            this.c = DEFAULT_POS;
        }
    }

    /**
     * Save widget position to preferences.
     */
    private void savePosition() {
        Utils.setpref("sessionbar-pos", c.x + "," + c.y);
    }

    /**
     * Clamp the widget into the current window.
     * The saved position is absolute and outlives the resolution it was saved at, so a bar parked
     * near the right edge of a wide monitor lands completely off screen on a narrower one - and
     * there is no way to drag back something you cannot see.
     *
     * Clamps against the full widget size rather than a token sliver, so a bar pushed in from the
     * edge ends up wholly visible instead of hugging the border. If the bar is larger than the
     * window it pins to the top-left, which is the only position that keeps it reachable.
     */
    private void clampToScreen() {
        if (parent == null || dm != null) // don't fight a drag in progress
            return;
        int maxX = Math.max(0, parent.sz.x - sz.x);
        int maxY = Math.max(0, parent.sz.y - sz.y);
        int x = Math.min(Math.max(c.x, 0), maxX);
        int y = Math.min(Math.max(c.y, 0), maxY);
        if (x != c.x || y != c.y) {
            this.c = new Coord(x, y);
            savePosition();
        }
    }

    @Override
    protected void added() {
        super.added();
        clampToScreen();
    }

    @Override
    public void presize() {
        super.presize();
        clampToScreen();
    }

    /**
     * Update widget size based on number of sessions.
     * In drag mode the frame adds an inset around the tabs.
     */
    private void updateSize() {
        boolean dragMode = ui != null && ui.core != null && ui.core.mode == NCore.Mode.DRAG;

        SessionManager sm = SessionManager.getInstance();
        int sessionCount = sm.getSessionCount();

        int width = BUTTON_WIDTH;
        int height = sessionCount * (BUTTON_HEIGHT + BUTTON_PADDING) + PLUS_BAR_HEIGHT;

        if (dragMode) {
            width += DRAG_INSET.x * 2 + UI.scale(24);
            height += DRAG_INSET.y + UI.scale(12);
        }
        this.sz = new Coord(width, height);

        // Update button positions (top-right corner)
        if (btnLock != null && btnVis != null) {
            int iconSize = NStyle.locki[0].sz().x;
            btnLock.move(new Coord(sz.x - iconSize - iconSize / 2, iconSize / 2));
            btnVis.move(new Coord(sz.x - iconSize - iconSize / 2, iconSize + controlOffset.y));
        }
    }

    /**
     * Set the callback for when "Add Account" is clicked.
     */
    public void setOnAddAccount(Runnable callback) {
        this.onAddAccount = callback;
    }

    /** Offset of the first tab inside the widget. */
    private Coord tabOffset() {
        boolean dragMode = ui != null && ui.core != null && ui.core.mode == NCore.Mode.DRAG;
        return (dragMode ? DRAG_INSET : Coord.z);
    }

    /**
     * Create, position and retire the portrait widgets so there is exactly one per session.
     */
    private void syncAvatars() {
        boolean dragMode = ui != null && ui.core != null && ui.core.mode == NCore.Mode.DRAG;
        boolean show = btnVis.a || dragMode;
        Coord off = tabOffset();

        SessionManager sm = SessionManager.getInstance();
        List<SessionContext> sessions = new ArrayList<>(sm.getAllSessions());
        Set<String> alive = new HashSet<>();

        for (int i = 0; i < sessions.size(); i++) {
            SessionContext ctx = sessions.get(i);
            alive.add(ctx.sessionId);
            SessionAvatar ava = avatars.get(ctx.sessionId);
            if (ava == null) {
                ava = add(new SessionAvatar(new Coord(AVA_SIZE, AVA_SIZE), ctx));
                avatars.put(ctx.sessionId, ava);
            }
            ava.move(new Coord(off.x + AVA_MARGIN,
                               off.y + i * (BUTTON_HEIGHT + BUTTON_PADDING) + (BUTTON_HEIGHT - AVA_SIZE) / 2));
            if (show)
                ava.show();
            else
                ava.hide();
        }

        for (Iterator<Map.Entry<String, SessionAvatar>> it = avatars.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, SessionAvatar> e = it.next();
            if (!alive.contains(e.getKey())) {
                e.getValue().destroy();
                it.remove();
            }
        }
    }

    @Override
    public void draw(GOut g) {
        // Lazy-load resources on first draw
        ensureResourcesLoaded();
        if (!resourcesLoaded) return; // Skip drawing if resources failed to load

        SessionManager sm = SessionManager.getInstance();
        List<SessionContext> sessions = new ArrayList<>(sm.getAllSessions());

        boolean dragMode = ui != null && ui.core != null && ui.core.mode == NCore.Mode.DRAG;

        updateSize();
        // Only authoritative once updateSize() has run - sz changes with session count and with
        // drag mode, so a clamp done at added()/presize() time can be based on a stale size.
        clampToScreen();

        // Draw overall background and border in drag mode
        if (dragMode) {
            drawDragBackground(g, sz);
            box.draw(g, Coord.z, sz);
            g.aimage(label, new Coord(sz.x / 2, UI.scale(17)), 0.5, 0.5);
        }

        if (!btnVis.a && !dragMode) {
            // Only the drag handles remain visible
            super.draw(g);
            return;
        }

        Coord off = tabOffset();
        SessionContext active = sm.getActiveSession();
        boolean canClose = sessions.size() > 1;

        // Pass 1: plates, so the portrait widgets can be drawn on top of them
        int y = off.y;
        for (int i = 0; i < sessions.size(); i++) {
            drawTabPlate(g, off.x, y, sessions.get(i), i == hoveredButton, sessions.get(i) == active);
            y += BUTTON_HEIGHT + BUTTON_PADDING;
        }

        // Pass 2: child widgets (portraits, drag handles)
        super.draw(g);

        // Pass 3: everything that sits above the portraits
        y = off.y;
        for (int i = 0; i < sessions.size(); i++) {
            SessionContext ctx = sessions.get(i);
            drawTabContent(g, off.x, y, ctx, i == hoveredButton, ctx == active,
                           canClose && (i == hoveredCloseButton), canClose);
            y += BUTTON_HEIGHT + BUTTON_PADDING;
        }

        drawPlusButton(g, off.x, y, hoveredButton == -2);
        g.chcolor();
    }

    private void drawDragBackground(GOut g, Coord sz) {
        Coord bgUl = new Coord(ctl.sz().x / 2, ctl.sz().y / 2);
        Coord bgSz = new Coord(sz.x - ctl.sz().x, sz.y - ctl.sz().y);

        if (ui instanceof NUI) {
            NUI nui = (NUI)ui;
            float opacity = nui.getUIOpacity();
            int alpha = (int)(255 * opacity);

            if (nui.getUseSolidBackground()) {
                Color bgColor = nui.getWindowBackgroundColor();
                g.chcolor(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), alpha);
                g.frect(bgUl, bgSz);
                g.chcolor();
            } else {
                g.chcolor(255, 255, 255, alpha);
                Coord bgc = new Coord();
                Coord ca_ul = bgUl;
                Coord ca_br = bgUl.add(bgSz);
                for(bgc.y = ca_ul.y; bgc.y < ca_br.y; bgc.y += Window.bg.sz().y) {
                    for(bgc.x = ca_ul.x; bgc.x < ca_br.x; bgc.x += Window.bg.sz().x)
                        g.image(Window.bg, bgc, ca_ul, ca_br);
                }
                g.chcolor();
            }
        }
    }

    private static Color accentOf(SessionContext ctx, boolean isActive) {
        if (ctx.hasAlarm()) {
            boolean pulseHigh = ((NUtils.getTickId() / ALARM_PULSE_TICKS) % 2) == 0;
            return pulseHigh ? ALARM_BORDER : ALARM_BORDER_ALT;
        }
        if (ctx.isInCombat()) return (COMBAT_BORDER);
        if (ctx.isRunningBot()) return (BOT_BORDER);
        if (isActive) return (ACTIVE_BORDER);
        return (IDLE_BORDER);
    }

    private static Color textOf(SessionContext ctx, boolean isActive) {
        if (ctx.hasAlarm()) return (ALARM_TEXT);
        if (ctx.isInCombat()) return (COMBAT_TEXT);
        if (ctx.isRunningBot()) return (BOT_TEXT);
        if (isActive) return (ACTIVE_TEXT);
        return (IDLE_TEXT);
    }

    private static String subtitleOf(SessionContext ctx, boolean isActive) {
        if (ctx.hasAlarm())
            return ("Alarm");
        if (ctx.isInCombat())
            return ("In combat");
        if (ctx.isRunningBot()) {
            String bot = ctx.getCurrentBotName();
            return ((bot == null || bot.isEmpty()) ? "Bot running" : bot);
        }
        if (isActive)
            return ("Active");
        return (ctx.isHeadless() ? "Background" : "Idle");
    }

    /** Background plate, glow and border of one tab. */
    private void drawTabPlate(GOut g, int x, int y, SessionContext ctx, boolean hovered, boolean isActive) {
        Coord ul = new Coord(x, y);
        Coord bsz = new Coord(BUTTON_WIDTH, BUTTON_HEIGHT);
        Color accent = accentOf(ctx, isActive);

        if (isActive) {
            for (int i = 1; i <= 3; i++) {
                g.chcolor(accent.getRed(), accent.getGreen(), accent.getBlue(), 54 / i);
                g.rect(ul.sub(i, i), bsz.add(i * 2, i * 2));
            }
        }

        g.chcolor(hovered ? new Color(255, 255, 255, 250) : new Color(214, 214, 214, 234));
        g.image(tabPanel, ul);

        g.chcolor(accent.getRed(), accent.getGreen(), accent.getBlue(), isActive ? 255 : 140);
        g.rect(ul, bsz);
        g.chcolor();

        // Accent rail along the left edge marks the state at a glance
        g.chcolor(accent);
        g.frect(ul.add(0, UI.scale(3)), new Coord(UI.scale(2), BUTTON_HEIGHT - UI.scale(6)));
        g.chcolor();

        drawCorners(g, ul, bsz, accent, isActive ? 220 : 90);
    }

    private void drawCorners(GOut g, Coord ul, Coord bsz, Color c, int alpha) {
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

    /** Portrait frame, name, subtitle, status icon and close button of one tab. */
    private void drawTabContent(GOut g, int x, int y, SessionContext ctx, boolean hovered,
                                boolean isActive, boolean closeHovered, boolean canClose) {
        Color accent = accentOf(ctx, isActive);
        Color textColor = textOf(ctx, isActive);

        // Portrait frame
        Coord avaUl = new Coord(x + AVA_MARGIN, y + (BUTTON_HEIGHT - AVA_SIZE) / 2);
        g.chcolor(accent.getRed(), accent.getGreen(), accent.getBlue(), isActive ? 210 : 120);
        g.rect(avaUl.sub(1, 1), new Coord(AVA_SIZE + 2, AVA_SIZE + 2));
        g.chcolor();

        int textX = x + AVA_MARGIN + AVA_SIZE + UI.scale(8);
        int maxTextWidth = BUTTON_WIDTH - (textX - x) - UI.scale(22);

        Tex nameTex = cached(nameCache, fit(ctx.getDisplayName(), maxTextWidth),
                             s -> nameFurnace.render(s).tex());
        g.chcolor(textColor);
        g.aimage(nameTex, new Coord(textX, y + UI.scale(13)), 0, 0.5);
        g.chcolor();

        Tex subTex = cached(subCache, subtitleOf(ctx, isActive), s -> subFoundry.render(s).tex());
        g.chcolor(SUB_TEXT);
        g.aimage(subTex, new Coord(textX, y + UI.scale(29)), 0, 0.5);
        g.chcolor();

        drawStatusIcon(g, x + BUTTON_WIDTH - STATUS_ICON_SIZE - UI.scale(4),
                       y + BUTTON_HEIGHT - STATUS_ICON_SIZE - UI.scale(4), ctx);

        if (hovered || closeHovered || canClose)
            drawCloseButton(g, x + BUTTON_WIDTH - CLOSE_BTN_SIZE - CLOSE_BTN_MARGIN,
                            y + CLOSE_BTN_MARGIN, closeHovered, !canClose);
    }

    /** Truncate to fit the given pixel width, appending an ellipsis. */
    private String fit(String name, int maxWidth) {
        String ret = fitCache.get(name);
        if (ret != null)
            return (ret);
        ret = name;
        if (nameFurnace.strsize(name).x > maxWidth) {
            for (int len = name.length() - 1; len > 0; len--) {
                String cut = name.substring(0, len) + "\u2026";
                if (nameFurnace.strsize(cut).x <= maxWidth) {
                    ret = cut;
                    break;
                }
            }
        }
        if (fitCache.size() > 64)
            fitCache.clear();
        fitCache.put(name, ret);
        return (ret);
    }

    private void drawCloseButton(GOut g, int x, int y, boolean hovered, boolean disabled) {
        Tex icon = (!disabled && hovered) ? closeHover : closeNormal;
        if (icon == null)
            return;
        g.chcolor(255, 255, 255, disabled ? 70 : (hovered ? 255 : 160));
        g.image(icon, new Coord(x, y), new Coord(CLOSE_BTN_SIZE, CLOSE_BTN_SIZE));
        g.chcolor();
    }

    private void drawStatusIcon(GOut g, int x, int y, SessionContext ctx) {
        Tex icon = null;
        if (ctx.isInCombat()) {
            icon = warningIcon;
        } else if (ctx.isRunningBot()) {
            icon = gearIcon;
        }
        if (icon != null)
            g.image(icon, new Coord(x, y), new Coord(STATUS_ICON_SIZE, STATUS_ICON_SIZE));
    }

    private void drawPlusButton(GOut g, int x, int y, boolean hovered) {
        Coord ul = new Coord(x, y);
        Coord bsz = new Coord(BUTTON_WIDTH, PLUS_BAR_HEIGHT);

        g.chcolor(hovered ? new Color(255, 255, 255, 250) : new Color(200, 200, 200, 220));
        g.image(plusPanel, ul);
        g.chcolor(PLUS_BTN_BORDER.getRed(), PLUS_BTN_BORDER.getGreen(), PLUS_BTN_BORDER.getBlue(),
                  hovered ? 255 : 150);
        g.rect(ul, bsz);
        g.chcolor();

        int iconSize = PLUS_BAR_HEIGHT - UI.scale(6);
        Tex icon = hovered ? addHover : addNormal;
        if (icon != null)
            g.image(icon, ul.add(UI.scale(3), UI.scale(3)), new Coord(iconSize, iconSize));

        if (plusLabel != null) {
            g.chcolor(hovered ? new Color(0xF0, 0xE2, 0xC0) : SUB_TEXT);
            g.aimage(plusLabel, ul.add(UI.scale(6) + iconSize, PLUS_BAR_HEIGHT / 2), 0, 0.5);
            g.chcolor();
        }
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        boolean dragMode = ui != null && ui.core != null && ui.core.mode == NCore.Mode.DRAG;

        if (dragMode) {
            // In drag mode, check if buttons handled the event
            if (!btnLock.mousedown(ev) && !btnVis.mousedown(ev)) {
                // Buttons didn't handle it, allow dragging if not locked
                if (ev.c.isect(Coord.z, sz)) {
                    if (ui.grabs.isEmpty()) {
                        if (!btnLock.a) {
                            if (ev.b == 1) {
                                dm = ui.grabmouse(this);
                                doff = ev.c;
                            }
                        }
                    } else {
                        if (ev.b == 1) {
                            dm = ui.grabmouse(this);
                            doff = ev.c;
                        }
                        parent.setfocus(this);
                    }
                }
            }
            return super.mousedown(ev);
        }

        // Normal mode - handle session button clicks
        if (ev.b != 1) return super.mousedown(ev);

        SessionManager sm = SessionManager.getInstance();
        List<SessionContext> sessions = new ArrayList<>(sm.getAllSessions());

        // Check if clicking on plus button
        if (isPlusButtonHit(ev.c)) {
            if (onAddAccount != null) {
                onAddAccount.run();
            }
            return true;
        }

        // Check if clicking any close button first (they're separate from session buttons)
        // Only allow closing if there's more than one session
        if (sessions.size() > 1) {
            for (int i = 0; i < sessions.size(); i++) {
                if (isCloseButtonHit(ev.c, i)) {
                    sm.requestCloseSession(sessions.get(i).sessionId);
                    return true;
                }
            }
        }

        // Check which session button was clicked
        int buttonIndex = getButtonAt(ev.c);
        if (buttonIndex >= 0 && buttonIndex < sessions.size()) {

            // Otherwise, prepare for potential drag or click
            dragStartPos = ev.c;
            dragStartButton = buttonIndex;
            return true;
        }

        return super.mousedown(ev);
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
        boolean dragMode = ui != null && ui.core != null && ui.core.mode == NCore.Mode.DRAG;

        if (dm != null && dragMode) {
            // Save drag mode position
            saveDragState();
            dm.remove();
            dm = null;
            return true;
        } else if (dm != null) {
            // Normal mode drag ended
            dm.remove();
            dm = null;
            savePosition();
            dragStartPos = null;
            dragStartButton = -1;
            return true;
        }

        // If we had a mousedown on a button but didn't drag, treat as click
        if (ev.b == 1 && dragStartButton >= 0 && dragStartPos != null) {
            SessionManager sm = SessionManager.getInstance();
            List<SessionContext> sessions = new ArrayList<>(sm.getAllSessions());
            if (dragStartButton < sessions.size()) {
                SessionContext ctx = sessions.get(dragStartButton);
                SessionContext active = sm.getActiveSession();
                if (ctx != active) {
                    sm.switchToSession(ctx.sessionId);
                }
            }
            dragStartPos = null;
            dragStartButton = -1;
            return true;
        }

        return super.mouseup(ev);
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
        boolean dragMode = ui != null && ui.core != null && ui.core.mode == NCore.Mode.DRAG;

        if (dragMode) {
            // Handle active dragging in drag mode
            if (dm != null) {
                this.c = this.c.add(ev.c.sub(doff));
            } else {
                // Not dragging, handle button hover
                if (ev.c.isect(Coord.z, sz)) {
                    btnLock.mousemove(ev);
                    btnVis.mousemove(ev);
                }
            }
        } else {
            // Normal mode
            if (dm != null) {
                // Handle dragging
                this.c = this.c.add(ev.c.sub(doff));
                return;
            }

            // Check if we should start dragging (mouse moved enough from start position)
            if (dragStartPos != null && dragStartButton >= 0) {
                int dx = Math.abs(ev.c.x - dragStartPos.x);
                int dy = Math.abs(ev.c.y - dragStartPos.y);
                if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) {
                    // Start dragging
                    dm = ui.grabmouse(this);
                    doff = dragStartPos;
                    dragStartButton = -1;
                    return;
                }
            }

            // Update hover state
            if (isPlusButtonHit(ev.c)) {
                hoveredButton = -2;
                hoveredCloseButton = -1;
            } else {
                int buttonIndex = getButtonAt(ev.c);
                hoveredButton = buttonIndex;

                if (buttonIndex >= 0 && isCloseButtonHit(ev.c, buttonIndex)) {
                    hoveredCloseButton = buttonIndex;
                } else {
                    hoveredCloseButton = -1;
                }
            }

            super.mousemove(ev);
        }
    }

    @Override
    public void tick(double dt) {
        syncAvatars();
        super.tick(dt);
        boolean dragMode = ui != null && ui.core != null && ui.core.mode == NCore.Mode.DRAG;

        // Show/hide drag mode controls
        if (dragMode) {
            if (!btnLock.visible()) {
                btnLock.show();
                btnVis.show();
            }
        } else {
            if (btnLock.visible()) {
                btnLock.hide();
                btnVis.hide();
            }
        }
    }

    @Override
    public boolean mousehover(MouseHoverEvent ev, boolean hovering) {
        if (!hovering) {
            hoveredButton = -1;
            hoveredCloseButton = -1;
        }
        return false;
    }

    /**
     * Get the button index at the given coordinate.
     */
    private int getButtonAt(Coord c) {
        Coord off = tabOffset();

        if (c.x < off.x || c.x > off.x + BUTTON_WIDTH) {
            return -1;
        }

        SessionManager sm = SessionManager.getInstance();
        int sessionCount = sm.getSessionCount();

        for (int i = 0; i < sessionCount; i++) {
            int y = off.y + i * (BUTTON_HEIGHT + BUTTON_PADDING);
            if (c.y >= y && c.y < y + BUTTON_HEIGHT) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Check if coordinate is over close button of given button.
     * Close button sits in the top right corner of the tab.
     */
    private boolean isCloseButtonHit(Coord c, int buttonIndex) {
        Coord off = tabOffset();

        int y = off.y + buttonIndex * (BUTTON_HEIGHT + BUTTON_PADDING) + CLOSE_BTN_MARGIN;
        int closeX = off.x + BUTTON_WIDTH - CLOSE_BTN_SIZE - CLOSE_BTN_MARGIN;

        return c.x >= closeX && c.x < closeX + CLOSE_BTN_SIZE &&
               c.y >= y && c.y < y + CLOSE_BTN_SIZE;
    }

    /**
     * Check if coordinate is over the "new session" bar below the tabs.
     */
    private boolean isPlusButtonHit(Coord c) {
        Coord off = tabOffset();

        SessionManager sm = SessionManager.getInstance();
        int sessionCount = sm.getSessionCount();

        int y = off.y + sessionCount * (BUTTON_HEIGHT + BUTTON_PADDING);

        return c.x >= off.x && c.x < off.x + BUTTON_WIDTH &&
               c.y >= y && c.y < y + PLUS_BAR_HEIGHT;
    }

    /**
     * Load drag state from preferences.
     */
    private void loadDragState() {
        String lockedStr = Utils.getpref("sessionbar-locked", "false");
        String visibleStr = Utils.getpref("sessionbar-visible", "true");
        btnLock.a = Boolean.parseBoolean(lockedStr);
        btnVis.a = Boolean.parseBoolean(visibleStr);
        // Don't set this.visible - the draw method checks btnVis.a instead
    }

    /**
     * Save drag state to preferences.
     */
    private void saveDragState() {
        Utils.setpref("sessionbar-locked", String.valueOf(btnLock.a));
        Utils.setpref("sessionbar-visible", String.valueOf(btnVis.a));
        savePosition();
    }

}
