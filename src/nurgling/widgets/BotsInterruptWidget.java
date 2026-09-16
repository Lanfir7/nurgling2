package nurgling.widgets;

import haven.Coord;
import haven.MenuGrid;
import haven.Widget;
import haven.res.ui.croster.Entry;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUI;
import nurgling.sessions.SessionContext;
import nurgling.sessions.SessionManager;

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
 * Per-session registry of running bot threads.
 *
 * This widget deliberately has no visual children. SessionTabBar renders the
 * controls from immutable snapshots, while this registry keeps interruption
 * and stack restoration bound to the UI that owns it.
 */
public class BotsInterruptWidget extends Widget {
    /** A stable entry exposed to session UI and diagnostics. */
    public static final class RunningBot {
        private final Thread thread;
        private final String name;

        private RunningBot(Thread thread, String name) {
            this.thread = thread;
            this.name = name;
        }

        public Thread getThread() {
            return thread;
        }

        public String getName() {
            return name;
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

    public BotsInterruptWidget() {
        super(Coord.z);
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
            runningBots.put(thread, new RunningBot(thread, thread.getName()));
            waitBot.set(true);
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
        super.tick(dt);
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
            String currentAction = first == null ? null : currentAction(first.getThread());

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

    private static String currentAction(Thread thread) {
        for (StackTraceElement element : thread.getStackTrace()) {
            if (element.toString().contains("actions.")) {
                return element.toString();
            }
        }
        return null;
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
