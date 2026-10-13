package nurgling.widgets;

import haven.*;
import haven.res.ui.croster.Entry;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NStyle;
import nurgling.NUI;
import nurgling.NUtils;
import nurgling.actions.bots.registry.BotDescriptor;
import nurgling.actions.bots.registry.BotRegistry;
import nurgling.i18n.L10n;
import nurgling.sessions.SessionContext;
import nurgling.sessions.SessionManager;
import nurgling.tasks.NTask;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-session bot registry and compact status bars. SessionTabBar reads immutable
 * snapshots; bars and hotkeys stop bots through the same registry.
 */
public class BotsInterruptWidget extends Widget {
    /** A stable entry exposed to session UI and diagnostics. */
    public static final class RunningBot {
        private final Thread thread;
        private final String name;
        private final String status;

        private RunningBot(Thread thread, String name, String status) {
            this.thread = thread;
            this.name = name;
            this.status = status;
        }

        public Thread getThread() {
            return thread;
        }

        public String getName() {
            return name;
        }

        /** Null means no action could be sampled yet. */
        public String getStatus() {
            return status;
        }
    }

    /** Per-session flag used by auto-petal selection and AutoDrink. */
    public final AtomicBoolean waitBot = new AtomicBoolean(false);

    private final Object botsLock = new Object();
    private final Map<Thread, RunningBot> runningBots = new LinkedHashMap<>();
    private final Set<Thread> stackBots = new LinkedHashSet<>();
    private boolean restoreStackState;

    // Stack trace writing for autorunner debugging
    private static String autorunnerStackTraceFile;
    private static long lastStackTraceWrite;
    private static final long STACK_TRACE_WRITE_INTERVAL = 2000;
    private static final long STATUS_UPDATE_INTERVAL = 250;
    private long lastStatusUpdate;

    // Thread.getStackTrace() stops the thread at a safepoint, so sample a few times a second, not per frame.
    private static final long SAMPLE_MS = 250;
    private static final String CRUMB_SEP = " › ";

    // Bar geometry, matching the IMeter bars exactly (155x30 frame, 30px badge at scale 1).
    private static final int COLLAPSED_W = UI.scale(155);
    private static final int EXPANDED_W = UI.scale(300);
    private static final int PAD = UI.scale(7);
    private static final int LINE = UI.scale(14);
    private static final int COLLAPSED_H = UI.scale(30);
    private static final int EXPANDED_H = 2 * PAD + 3 * LINE;
    private static final int BADGE = UI.scale(30);
    private static final int FRAME_X = UI.scale(15);
    private static final int ICON = UI.scale(16);
    private static final int GAP = UI.scale(4);
    private static final int ROW_GAP = UI.scale(2);
    private static final int ICON_X = FRAME_X + UI.scale(18);
    private static final int TEXT_X = ICON_X + ICON + GAP;

    static Text.Foundry titlef, linef, waitf;
    private static final Color FRAME_DARK = new Color(94, 75, 26);
    private static final Color FRAME_GOLD = new Color(255, 198, 94);

    private static Tex[] gears;
    private static Tex badge;
    private static Frame frame;
    private static BufferedImage[] stopi;

    /** Views only; runningBots remains the sole owner of bot lifetimes. */
    final ArrayList<Gear> obs = new ArrayList<>();

    /** Registry users can construct this widget before UI fonts and resources exist. */
    private static synchronized void initVisuals() {
        if (gears != null)
            return;
        titlef = new Text.Foundry(Text.sans.deriveFont(Font.BOLD), 12, Color.WHITE).aa(true);
        linef = new Text.Foundry(Text.sans, 11, NStyle.questDim).aa(true);
        waitf = new Text.Foundry(Text.sans, 11, NStyle.border).aa(true);
        badge = badge();
        frame = Frame.load("nurgling/hud/meter/stam");
        stopi = scaled(NStyle.crossSquare, ICON);
        gears = scaledGears(UI.scale(22));
    }

    private static Tex[] scaledGears(int size) {
        Tex[] res = new Tex[NStyle.gear.length];
        for (int i = 0; i < res.length; i++)
            res[i] = new TexI(PUtils.convolvedown(Resource.loadsimg("nurgling/hud/gear/" + i), new Coord(size, size), CharWnd.iconfilter));
        return res;
    }

    private static BufferedImage[] scaled(TexI[] src, int size) {
        BufferedImage[] res = new BufferedImage[src.length];
        for (int i = 0; i < src.length; i++)
            res[i] = PUtils.convolvedown(src[i].back, new Coord(size, size), CharWnd.iconfilter);
        return res;
    }

    /** The round badge the meters carry their icon in, empty: dark / gold / dark rings around a teal disc. */
    private static Tex badge() {
        BufferedImage img = new BufferedImage(BADGE, BADGE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int ring = Math.max(1, UI.scale(1));
        g.setColor(FRAME_DARK);
        g.fillOval(0, 0, BADGE, BADGE);
        g.setColor(FRAME_GOLD);
        g.fillOval(ring, ring, BADGE - 2 * ring, BADGE - 2 * ring);
        g.setColor(FRAME_DARK);
        g.fillOval(4 * ring, 4 * ring, BADGE - 8 * ring, BADGE - 8 * ring);
        g.setColor(NStyle.rowEven);
        g.fillOval(5 * ring, 5 * ring, BADGE - 10 * ring, BADGE - 10 * ring);
        g.dispose();
        return new TexI(img);
    }

    /** The meter frame cut into nine slices so it stretches to any bar size; the left cap is the right cap mirrored. */
    private static class Frame {
        final Tex tl, t, tr, l, r, bl, b, br;
        final int capw, top, bottom;

        private Frame(BufferedImage img) {
            int w = img.getWidth(), h = img.getHeight();
            double f = w / 155.0;
            capw = (int) Math.round(10 * f);
            top = (int) Math.round(8 * f);
            bottom = (int) Math.round(8 * f);
            int midh = h - top - bottom, cx = w - capw, mx = w / 2;
            tr = slice(img, cx, 0, capw, top, false);
            r = slice(img, cx, top, capw, midh, false);
            br = slice(img, cx, h - bottom, capw, bottom, false);
            tl = slice(img, cx, 0, capw, top, true);
            l = slice(img, cx, top, capw, midh, true);
            bl = slice(img, cx, h - bottom, capw, bottom, true);
            t = slice(img, mx, 0, 1, top, false);
            b = slice(img, mx, h - bottom, 1, bottom, false);
        }

        static Frame load(String res) {
            BufferedImage img = Resource.loadsimg(res);
            // Headless sessions get a dummy image too small to slice; they never draw anyway.
            return img.getWidth() < 100 ? null : new Frame(img);
        }

        /** A standalone copy of the region: TexI of a getSubimage view would upload the whole parent raster. */
        private static Tex slice(BufferedImage src, int x, int y, int w, int h, boolean mirror) {
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            if (mirror)
                g.drawImage(src, w, 0, 0, h, x, y, x + w, y + h, null);
            else
                g.drawImage(src, 0, 0, w, h, x, y, x + w, y + h, null);
            g.dispose();
            return new TexI(out);
        }

        void draw(GOut g, Coord ul, Coord sz) {
            int midw = sz.x - 2 * capw, midh = sz.y - top - bottom;
            int rx = ul.x + sz.x - capw, by = ul.y + sz.y - bottom;
            g.image(tl, ul);
            g.image(t, new Coord(ul.x + capw, ul.y), new Coord(midw, top));
            g.image(tr, new Coord(rx, ul.y));
            g.image(l, new Coord(ul.x, ul.y + top), new Coord(capw, midh));
            g.image(r, new Coord(rx, ul.y + top), new Coord(capw, midh));
            g.image(bl, new Coord(ul.x, by));
            g.image(b, new Coord(ul.x + capw, by), new Coord(midw, bottom));
            g.image(br, new Coord(rx, by));
        }
    }

    /** The first nurgling.actions frame, i.e. the innermost action the thread is in. */
    public static String currentAction(Thread t) {
        List<StackTraceElement> frames = actionFrames(t.getStackTrace());
        return frames.isEmpty() ? null : frames.get(0).toString();
    }

    /** nurgling.actions frames, innermost first. */
    static List<StackTraceElement> actionFrames(StackTraceElement[] stack) {
        List<StackTraceElement> res = new ArrayList<>();
        for (StackTraceElement e : stack) {
            if (e.getClassName().startsWith("nurgling.actions."))
                res.add(e);
        }
        return res;
    }

    /** "nurgling.actions.bots.Forager$2" -> "Forager". */
    static String simpleName(String className) {
        String s = className.substring(className.lastIndexOf('.') + 1);
        int d = s.indexOf('$');
        return d > 0 ? s.substring(0, d) : s;
    }

    /** Action class names, outermost first, with consecutive repeats collapsed. */
    static List<String> breadcrumb(List<StackTraceElement> frames) {
        List<String> res = new ArrayList<>();
        for (int i = frames.size() - 1; i >= 0; i--) {
            String n = simpleName(frames.get(i).getClassName());
            if (res.isEmpty() || !res.get(res.size() - 1).equals(n))
                res.add(n);
        }
        return res;
    }

    /** Joins the breadcrumb, dropping middle items and then characters until it fits w pixels. */
    static String fit(List<String> parts, Text.Foundry f, int w) {
        List<String> p = new ArrayList<>(parts);
        String s = String.join(CRUMB_SEP, p);
        while (f.strsize(s).x > w && p.size() > 3) {
            p.remove(p.get(1).equals("…") ? 2 : 1);
            if (!p.get(1).equals("…"))
                p.add(1, "…");
            s = String.join(CRUMB_SEP, p);
        }
        while (f.strsize(s).x > w && s.length() > 1)
            s = s.substring(0, s.length() - 2) + "…";
        return s;
    }

    static String taskName(NTask task) {
        Class<?> c = task.getClass();
        if (c.isAnonymousClass())
            return simpleName(c.getName()) + " task";
        return c.getSimpleName();
    }

    static String elapsed(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    /** Re-renders only when the string changed, disposing the old texture. */
    static Text rerender(Text old, String s, Text.Foundry f) {
        if (old != null && old.text.equals(s))
            return old;
        if (old != null)
            old.dispose();
        return s == null ? null : f.render(s);
    }

    private static BotDescriptor descriptorFor(String threadName) {
        String n = threadName;
        for (String suffix : new String[]{"-ToggleThread", "-RecentAction"}) {
            if (n.endsWith(suffix))
                n = n.substring(0, n.length() - suffix.length());
        }
        for (BotDescriptor d : BotRegistry.all()) {
            if (n.equals(d.iconPath) || n.equals(d.id) || n.equals(d.getDisplayName()))
                return d;
        }
        return null;
    }

    public class Gear extends Widget {
        final Thread t;
        final String title;
        final long started = System.currentTimeMillis();
        private final Tex icon;
        private final IButton stopb;
        private boolean expanded = false;
        private long lastSample = 0;

        List<StackTraceElement> frames = new ArrayList<>();
        List<String> crumb = new ArrayList<>();
        String waiting = null;
        private Text titlet, timet, crumbt, waitt;
        BotDetailWnd detail = null;

        public Gear(Thread t) {
            super();
            assert t != null;
            initVisuals();
            this.t = t;
            BotDescriptor d = descriptorFor(t.getName());
            title = d != null ? d.getDisplayName() : t.getName();
            icon = d != null ? botIcon(d) : null;
            stopb = add(new IButton(stopi[0], stopi[1], stopi[2]) {
                @Override
                public void click() {
                    removeObserve(Gear.this.t);
                }
            }.squarehit(true));
            stopb.settip(L10n.get("botstatus.stop", title));
            layout();
        }

        private Tex botIcon(BotDescriptor d) {
            try {
                return new TexI(PUtils.convolvedown(Resource.loadsimg("nurgling/bots/icons/" + d.iconPath + "/u"), new Coord(ICON, ICON), CharWnd.iconfilter));
            } catch (Resource.NoSuchResourceException e) {
                return null;
            }
        }

        void layout() {
            resize(expanded ? new Coord(EXPANDED_W, EXPANDED_H) : new Coord(COLLAPSED_W, COLLAPSED_H));
            stopb.move(new Coord(sz.x - UI.scale(6) - ICON, PAD));
        }

        /** Room for text between the icon and the stop button. */
        private int textW() {
            return stopb.c.x - GAP - TEXT_X;
        }

        void sample() {
            frames = actionFrames(t.getStackTrace());
            crumb = breadcrumb(frames);
            NTask task = (ui != null && ui.core != null) ? ui.core.waitingTask(t) : null;
            long waited = task == null ? 0 : (System.currentTimeMillis() - task.waitSince) / 1000;
            // Most waits last a few ticks; only show the ones long enough to explain a stall.
            waiting = waited < 1 ? null : L10n.get("botstatus.waiting", taskName(task), waited);
        }

        String crumbText(Text.Foundry f, int w) {
            return crumb.isEmpty() ? L10n.get("botstatus.no_action") : fit(crumb, f, w);
        }

        @Override
        public void tick(double dt) {
            super.tick(dt);
            long now = System.currentTimeMillis();
            if ((expanded || detail != null) && now - lastSample >= SAMPLE_MS) {
                lastSample = now;
                sample();
            }
            timet = rerender(timet, elapsed(now - started), linef);
            titlet = rerender(titlet, fit(List.of(title), titlef, textW() - timet.sz().x - GAP), titlef);
            if (expanded) {
                crumbt = rerender(crumbt, crumbText(linef, textW()), linef);
                waitt = rerender(waitt, waiting, waitf);
            }
        }

        @Override
        public void draw(GOut g) {
            Coord fsz = new Coord(sz.x - FRAME_X, sz.y);
            int inset = UI.scale(4);
            g.chcolor(ui.mc.isect(rootpos(), sz) ? NStyle.rowOdd : NStyle.rowEven);
            g.frect(new Coord(FRAME_X + inset, inset), fsz.sub(2 * inset, 2 * inset));
            g.chcolor();
            if (frame != null)
                frame.draw(g, new Coord(FRAME_X, 0), fsz);

            int by = (sz.y - BADGE) / 2;
            g.image(badge, new Coord(0, by));
            Tex gear = gears[(int) (NUtils.getTickId() / 5) % gears.length];
            g.image(gear, new Coord((BADGE - gear.sz().x) / 2, by + (BADGE - gear.sz().y) / 2));

            if (icon != null)
                g.image(icon, new Coord(ICON_X, PAD));
            int ty = expanded ? PAD : (sz.y - LINE) / 2;
            if (titlet != null)
                g.image(titlet.tex(), new Coord(TEXT_X, ty));
            if (timet != null)
                g.image(timet.tex(), new Coord(stopb.c.x - GAP - timet.sz().x, ty + UI.scale(1)));
            if (expanded) {
                if (crumbt != null)
                    g.image(crumbt.tex(), new Coord(TEXT_X, PAD + LINE));
                if (waitt != null)
                    g.image(waitt.tex(), new Coord(TEXT_X, PAD + 2 * LINE));
            }
            super.draw(g);
        }

        @Override
        public Object tooltip(Coord c, Widget prev) {
            Object tt = super.tooltip(c, prev);
            return tt != null ? tt : L10n.get("botstatus.hint");
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if (ev.propagate(this))
                return true;
            if (ev.b == 1) {
                expanded = !expanded;
                lastSample = 0;
                layout();
                repack();
            } else if (ev.b == 3) {
                openDetail();
            }
            // Bars sit over the map; never let a click fall through and walk the character.
            return true;
        }

        void openDetail() {
            if (detail == null) {
                lastSample = 0;
                GameUI gui = getparent(GameUI.class);
                if (gui != null)
                    detail = gui.add(new BotDetailWnd(this), rootpos().add(sz.x + UI.scale(10), 0));
            } else {
                detail.raise();
            }
        }

        void closeDetail() {
            if (detail != null) {
                detail.reqdestroy();
                detail = null;
            }
        }

        void stop() {
            removeObserve(t);
        }

        @Override
        public void dispose() {
            for (Text txt : new Text[]{titlet, timet, crumbt, waitt}) {
                if (txt != null)
                    txt.dispose();
            }
            if (icon != null)
                icon.dispose();
            super.dispose();
        }
    }

    public BotsInterruptWidget() {
        super(new Coord(COLLAPSED_W, COLLAPSED_H));
        initializeStackTraceFile();
    }

    private void initializeStackTraceFile() {
        if (NConfig.isBotMod() && NConfig.botmod != null && NConfig.botmod.stackTraceFile != null) {
            autorunnerStackTraceFile = NConfig.botmod.stackTraceFile;
            System.out.println("Autorunner mode detected: Stack trace file = " + autorunnerStackTraceFile);
        }
    }

    /** Register a bot before it is started. Duplicate registration is harmless. */
    public void addObserve(Thread thread) {
        addObserve(thread, false);
    }

    /**
     * Register a bot before it is started and optionally hide the owning
     * session's equipment stacks for its lifetime.
     */
    public void addObserve(Thread thread, boolean disableStacks) {
        if (thread == null) {
            return;
        }
        synchronized (botsLock) {
            if (runningBots.containsKey(thread)) {
                return;
            }
            if (disableStacks && stacksAreEnabled()) {
                if (stackBots.isEmpty()) {
                    restoreStackState = true;
                    setStacksEnabled(false);
                }
                if (restoreStackState) {
                    stackBots.add(thread);
                }
            } else if (disableStacks && restoreStackState) {
                stackBots.add(thread);
            }
            runningBots.put(thread, new RunningBot(thread, thread.getName(), null));
            waitBot.set(true);
        }
    }

    /** Reconcile bars on the UI tick; bot threads only mutate the registry. */
    private void syncViews() {
        if (ui == null)
            return;
        List<Thread> threads;
        synchronized (botsLock) {
            threads = new ArrayList<>(runningBots.keySet());
        }
        boolean changed = false;
        for (java.util.Iterator<Gear> it = obs.iterator(); it.hasNext();) {
            Gear gear = it.next();
            if (!threads.contains(gear.t)) {
                gear.closeDetail();
                gear.destroy();
                it.remove();
                changed = true;
            }
        }
        for (Thread thread : threads) {
            boolean present = false;
            for (Gear gear : obs) {
                if (gear.t == thread) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                obs.add(add(new Gear(thread)));
                changed = true;
            }
        }
        if (changed)
            repack();
    }

    /** Stack the bars and size the draggable host to the widest one. */
    void repack() {
        int y = 0, w = COLLAPSED_W;
        for (Gear gear : obs) {
            gear.move(new Coord(0, y));
            y += gear.sz.y + ROW_GAP;
            w = Math.max(w, gear.sz.x);
        }
        Coord size = new Coord(w, obs.isEmpty() ? COLLAPSED_H : y - ROW_GAP);
        if (!size.equals(sz)) {
            if (parent instanceof NDraggableWidget)
                parent.resize(size.add(NDraggableWidget.delta));
            else
                resize(size);
        }
    }

    /** Interrupt one bot and immediately remove it from this session's registry. */
    public void removeObserve(Thread thread) {
        if (thread == null) {
            return;
        }
        synchronized (botsLock) {
            if (!runningBots.containsKey(thread)) {
                return;
            }
            thread.interrupt();
        }
        unregister(thread);
    }

    /** Immutable snapshot safe to render while bot threads change state. */
    public List<RunningBot> getRunningBots() {
        synchronized (botsLock) {
            return java.util.Collections.unmodifiableList(new ArrayList<>(runningBots.values()));
        }
    }

    /** Check whether this session has tracked bots, including a NEW bot about to start. */
    public boolean hasRunningBots() {
        synchronized (botsLock) {
            return !runningBots.isEmpty();
        }
    }

    /** Interrupt every tracked bot without selecting or switching sessions. */
    public void interruptAll() {
        List<Thread> threads;
        synchronized (botsLock) {
            threads = new ArrayList<>(runningBots.keySet());
        }
        for (Thread thread : threads) {
            thread.interrupt();
            unregister(thread);
        }
    }

    public void interruptAllBots() {
        interruptAll();
    }

    @Override
    public void tick(double dt) {
        syncViews();
        super.tick(dt);
        long now = System.currentTimeMillis();
        if (now - lastStatusUpdate >= STATUS_UPDATE_INTERVAL) {
            refreshStatuses();
            lastStatusUpdate = now;
        }
        if (autorunnerStackTraceFile != null &&
                System.currentTimeMillis() - lastStackTraceWrite > STACK_TRACE_WRITE_INTERVAL) {
            writeCurrentStackTrace();
            lastStackTraceWrite = System.currentTimeMillis();
        }

        List<Thread> terminated = new ArrayList<>();
        synchronized (botsLock) {
            for (Thread thread : runningBots.keySet()) {
                // A bot is registered before start; never mistake NEW for a finished thread.
                if (thread.getState() == Thread.State.TERMINATED) {
                    terminated.add(thread);
                }
            }
        }
        for (Thread thread : terminated) {
            unregister(thread);
        }
        if (!terminated.isEmpty())
            syncViews();
    }

    @Override
    public void draw(GOut g) {
        if (!obs.isEmpty())
            super.draw(g);
    }

    /** Sample action names off the render path so tab drawing stays cheap. */
    private void refreshStatuses() {
        synchronized (botsLock) {
            for (Map.Entry<Thread, RunningBot> entry : runningBots.entrySet()) {
                RunningBot bot = entry.getValue();
                String status = formatActionStatus(entry.getKey().getStackTrace());
                if (!java.util.Objects.equals(status, bot.getStatus())) {
                    entry.setValue(new RunningBot(entry.getKey(), bot.getName(), status));
                }
            }
        }
    }

    private void unregister(Thread thread) {
        if (thread == null) {
            return;
        }
        synchronized (botsLock) {
            if (runningBots.remove(thread) == null) {
                return;
            }
            stackBots.remove(thread);
            if (stackBots.isEmpty() && restoreStackState) {
                restoreStackState = false;
                // Registration and restoration share this lock, so another
                // stack-disabling bot cannot be registered in between.
                setStacksEnabled(true);
            }
            waitBot.set(!runningBots.isEmpty());
        }
        SessionContext active = SessionManager.getInstance().getActiveSession();
        if (active != null && active.getGameUI() == owningGameUI()) {
            Entry.killList.clear();
        }
    }

    /** Return the UI that contains this registry, never the currently active session's UI. */
    private NGameUI owningGameUI() {
        return (ui instanceof NUI) ? ((NUI) ui).gui : null;
    }

    private boolean stacksAreEnabled() {
        NGameUI gui = owningGameUI();
        if (gui == null || !(gui.maininv instanceof NInventory)) {
            return false;
        }
        return ((NInventory) gui.maininv).bundle.a;
    }

    private void setStacksEnabled(boolean enabled) {
        NGameUI gui = owningGameUI();
        if (gui == null || !(gui.maininv instanceof NInventory)) {
            return;
        }
        NInventory inventory = (NInventory) gui.maininv;
        if (inventory.bundle.a != enabled) {
            MenuGrid.PagButton button = inventory.pagBundle;
            if (button != null) {
                button.use(new MenuGrid.Interaction(1, 0));
            }
        }
    }

    private void writeCurrentStackTrace() {
        try {
            List<RunningBot> bots = getRunningBots();
            RunningBot first = bots.isEmpty() ? null : bots.get(0);
            String botName = first == null ? "Unknown" : first.getName();
            String currentAction = first == null ? null : formatActionStatus(first.getThread().getStackTrace());

            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"timestamp\": \"").append(Instant.now()).append("\",\n");
            json.append("  \"botName\": \"").append(jsonEscape(botName)).append("\",\n");
            json.append("  \"currentAction\": \"")
                    .append(jsonEscape(currentAction == null ? "No action found" : currentAction)).append("\",\n");
            json.append("  \"activeBotsCount\": ").append(bots.size()).append("\n");
            json.append("}");

            Path tempFile = Paths.get(autorunnerStackTraceFile + ".tmp");
            Path finalFile = Paths.get(autorunnerStackTraceFile);
            Files.write(tempFile, json.toString().getBytes(), StandardOpenOption.CREATE);
            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | SecurityException e) {
            System.err.println("Failed to write stack trace: " + e.getMessage());
        }
    }

    static String formatActionStatus(StackTraceElement[] stack) {
        if (stack == null) {
            return null;
        }
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.startsWith("nurgling.actions.")) {
                int dot = className.lastIndexOf('.');
                String simpleName = className.substring(dot + 1);
                int anonymous = simpleName.indexOf('$');
                return anonymous >= 0 ? simpleName.substring(0, anonymous) : simpleName;
            }
        }
        return null;
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
