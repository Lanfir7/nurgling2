/*
 * Multi-session manager for Nurgling client.
 * Based on BeyondSRC UIObserver architecture.
 * 
 * Key features:
 * - Manages multiple UI sessions in a single client window
 * - Only one session is ACTIVE at a time (full rendering)
 * - Background sessions run with reduced TPS (5 vs 60)
 * - Single GLPanel shared across all sessions
 */

package nurgling;

import haven.*;
import nurgling.widgets.SessionWidget;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class UIObserver {
    // Collection of all UI sessions
    private final ConcurrentLinkedDeque<UI> uis = new ConcurrentLinkedDeque<>();
    
    // Session switching widget (will be created lazily when first session starts)
    public SessionWidget sessionWidget;
    
    // Reference to the current GLPanel for rendering
    private final AtomicReference<GLPanel> currentPanel = new AtomicReference<>();
    
    // Reference to MainFrame
    private final AtomicReference<MainFrame> mainFrame = new AtomicReference<>();
    
    // Closed flag
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    // Currently active UI (the one being rendered)
    public volatile UI active = null;
    
    // TPS settings (background lower = less CPU when multiple sessions)
    public static final double ACTIVE_TPS = 60.0;
    public static final double BACKGROUND_TPS = 2.0;
    
    // Shared audio root for all sessions
    public final ActAudio.Root audio = new ActAudio.Root();
    
    /**
     * Get a copy of all UI sessions.
     */
    public Set<UI> uis() {
        return new CopyOnWriteArraySet<>(uis);
    }
    
    /**
     * Check if there are no sessions.
     */
    public boolean isEmpty() {
        return uis.isEmpty();
    }
    
    /**
     * Get number of sessions.
     */
    public int size() {
        return uis.size();
    }
    
    /**
     * Set the MainFrame reference.
     */
    public void setMainFrame(MainFrame frame) {
        this.mainFrame.set(frame);
    }
    
    /**
     * Set the GLPanel for rendering.
     */
    public void setPanel(GLPanel panel) {
        this.currentPanel.set(panel);
    }
    
    /**
     * Get the current panel.
     */
    public GLPanel getPanel() {
        return currentPanel.get();
    }
    
    /**
     * Add a UI to the observer.
     */
    private void addUI(UI ui) {
        uis.add(ui);
        if (sessionWidget != null) {
            sessionWidget.addUI(ui);
        }
    }
    
    /**
     * Remove a UI from the observer.
     */
    private void removeUI(UI ui) {
        uis.remove(ui);
        if (sessionWidget != null) {
            sessionWidget.removeUI(ui);
        }
    }
    
    /**
     * Start the first session (called on application startup).
     */
    public void startRunner() {
        createUI(true);
    }
    
    /**
     * Create a new UI session.
     * @return The newly created UI
     */
    public UI createUI() {
        return createUI(null, false);
    }
    
    /**
     * Create a new UI session.
     * @param makeActive If true, make this the active session
     * @return The newly created UI
     */
    public UI createUI(boolean makeActive) {
        return createUI(null, makeActive);
    }
    
    /**
     * Create a new UI session with a specific runner.
     * @param task The runner to use (null = Bootstrap)
     * @param makeActive If true, make this the active session
     * @return The newly created UI
     */
    public UI createUI(UI.Runner task, boolean makeActive) {
        GLPanel panel = currentPanel.get();
        if (panel == null) {
            throw new IllegalStateException("No panel set for UIObserver");
        }
        
        // Create new UI: panel.shape() may be null until the panel is displayed (e.g. new window)
        Area panelShape = panel.shape();
        Coord sz = active != null ? active.root.sz
            : (panelShape != null ? new Coord(panelShape.sz()) : new Coord(800, 600));
        NUI ui = new NUI(panel, sz, null);
        ui.env = panel.env();
        // Note: Audio sharing would require ActAudio.Root changes, skipping for now
        
        addUI(ui);
        
        if (makeActive) {
            setActiveUI(ui);
        }
        
        // Start UI thread
        startUIThread(ui);
        
        // Start runner
        changeRunner(ui, task, true);
        
        return ui;
    }
    
    /**
     * Create or reuse existing session on login screen.
     * If active session is on LoginScreen, return it; otherwise create new.
     */
    public UI createOrChange() {
        UI activeUI = this.active;
        if (activeUI != null) {
            if (activeUI.root.getchild(LoginScreen.class) != null) {
                return activeUI;
            }
        }
        return createUI(null, false);
    }
    
    /**
     * Start the main thread for a UI session.
     * This thread handles tick() and glob.ctick() at appropriate TPS.
     */
    private void startUIThread(UI ui) {
        String threadName = "Haven UI";
        Thread thread = new HackThread(() -> {
            ui.setUiThread(Thread.currentThread());
            ui.state(UI.State.BACKGROUND);
            
            double then = Utils.rtime();
            double[] frames = new double[128];
            int framep = 0;
            
            while (!Thread.currentThread().isInterrupted()) {
                if (ui.isState(UI.State.INTERRUPTED)) {
                    // If we're in game, log out first
                    if (ui.gui != null) {
                        try {
                            ui.gui.act("lo");
                            Thread.sleep(3000); // Wait for logout
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    break;
                }
                
                try {
                    // Update thread name with session info
                    if (ui.sess != null && ui.sess.user != null) {
                        String newName = threadName + ": " + ui.sess.user.name;
                        if (!Thread.currentThread().getName().equals(newName)) {
                            Thread.currentThread().setName(newName);
                        }
                    }
                    
                    // Set UI context under contextLock so GL frame never sees wrong session (prevents flicker + wrong brightness)
                    synchronized (UI.contextLock) {
                        if (ui instanceof NUI) {
                            UI.setInstance((NUI) ui);
                        }
                        try {
                            // Process glob ticks
                            try {
                                if (ui.sess != null && ui.sess.glob != null) {
                                    ui.sess.glob.ctick();
                                }
                            } catch (Exception e) {
                                new Warning(e, "glob.ctick error").issue();
                            }
                            
                            // Process UI ticks
                            try {
                                ui.tick();
                            } catch (Exception e) {
                                new Warning(e, "ui.tick error").issue();
                            }
                        } finally {
                            // Restore active UI as global so getInstance() reflects displayed session when no tick runs
                            UI cur = active;
                            if (cur != null && cur instanceof NUI) {
                                UI.setInstance((NUI) cur);
                            }
                        }
                    }
                    
                    // Ensure session widget is attached to active UI's root
                    // This must be done here because widget tick() only runs if it has a parent
                    if (ui == active && sessionWidget != null && ui.root != null) {
                        // Check if sessionWidget is actually in root's children list
                        boolean inChildren = false;
                        for (Widget w = ui.root.child; w != null; w = w.next) {
                            if (w == sessionWidget) {
                                inChildren = true;
                                break;
                            }
                        }
                        if (!inChildren) {
                            // #region agent log
                            debugLog("G", "tick:reattach", "SessionWidget not in children, reattaching", "parent=" + sessionWidget.parent + ",root=" + ui.root);
                            // #endregion
                            if (sessionWidget.parent != null) {
                                sessionWidget.unlink();
                            }
                            ui.root.add(sessionWidget, Coord.z);
                            sessionWidget.raise();
                        }
                    }
                    
                    // Calculate sleep time based on state
                    double tps = ui.isState(UI.State.ACTIVE) ? ACTIVE_TPS : BACKGROUND_TPS;
                    double targetDt = 1.0 / tps;
                    targetDt = Math.min(targetDt, 1.0);
                    targetDt = Math.max(targetDt, 0.0);
                    
                    double now = Utils.rtime();
                    long nanos = 1;
                    if (then + targetDt > now) {
                        then += targetDt;
                        nanos = (long) ((then - now) * 1e9);
                    } else {
                        then = now;
                    }
                    Thread.sleep(nanos / 1_000_000, (int)(nanos % 1_000_000));
                    
                    // Track updates per second
                    frames[framep] = now;
                    int i = 0, ckf = framep;
                    for (; i < frames.length - 1; i++) {
                        ckf = (ckf - 1 + frames.length) % frames.length;
                        if (now - frames[ckf] > 1)
                            break;
                    }
                    framep = (framep + 1) % frames.length;
                    
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable e) {
                    new Warning(e, "UI thread error").issue();
                    break;
                }
            }
            
            // Cleanup
            ui.destroy();
            closeUI(ui);
        }, threadName);
        
        thread.start();
    }
    
    /**
     * Change the runner for a UI session.
     */
    public void changeRunner(UI ui, UI.Runner task) {
        changeRunner(ui, task, false);
    }
    
    /**
     * Change the runner for a UI session.
     * @param ui The UI session
     * @param task The runner (null = Bootstrap)
     * @param async If true, run in a new thread
     */
    private void changeRunner(UI ui, UI.Runner task, boolean async) {
        if (task == null) {
            task = new Bootstrap();
        }
        
        final UI.Runner initialTask = task;
        
        Runnable runnable = () -> {
            UI.Runner currentTask = initialTask;
            UI.Runner previousTask = null; // Track previous task for transition logic
            
            // Iterative loop instead of recursion to avoid StackOverflow
            while (currentTask != null && !closed.get() && !ui.isState(UI.State.CLOSED)) {
                Thread.currentThread().setName("Haven UI Runner: " + currentTask.title());
                ui.setRunnerThread(Thread.currentThread());
                
                // Recreate root ONLY when returning to Bootstrap (login screen)
                // Do NOT recreate for:
                // - Bootstrap -> RemoteUI (session established by Bootstrap)
                // - RemoteUI -> RemoteUI (new session is already active, interruption causes server timeout!)
                boolean needsRecreate = false;
                if (previousTask != null && currentTask instanceof Bootstrap) {
                    // Only recreate when returning to login screen
                    needsRecreate = true;
                }
                
                if (needsRecreate) {
                    Coord sz = ui.root != null ? ui.root.sz : new Coord(800, 600);
                    ui.recreateRoot(sz);
                }
                
                // Always relink session widget when task changes (ensures it's visible in game)
                relinkWidget(ui);
                
                UI.Runner nextTask = null;
                try {
                    currentTask.init(ui);
                    nextTask = currentTask.run(ui);
                } catch (InterruptedException e) {
                    // Normal termination
                    break;
                } catch (Throwable e) {
                    new Warning(e, "Runner error").issue();
                }
                
                if (closed.get()) return;
                
                // Don't clear session here - it causes race condition with GLPanel
                // The next runner's init() will set the correct session
                
                // Wait if interrupted
                while (ui.isState(UI.State.INTERRUPTED)) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                
                // Move to next runner
                previousTask = currentTask;
                currentTask = nextTask;
                if (currentTask == null) {
                    currentTask = new Bootstrap(); // Return to login screen
                }
            }
        };
        
        if (async) {
            new HackThread(runnable, "Haven UI Runner").start();
        } else {
            runnable.run();
        }
    }
    
    /**
     * Set the active UI session.
     * Previous active becomes BACKGROUND, new one becomes ACTIVE.
     */
    public void setActiveUI(UI ui) {
        // #region agent log
        debugLog("H", "setActiveUI:entry", "setActiveUI called", "ui=" + ui + ",prevActive=" + active + ",panel=" + currentPanel.get());
        // #endregion
        GLPanel panel = currentPanel.get();
        
        // Match BeyondSRC order: change panel first, then update active
        if (panel != null && ui != null) {
            // #region agent log
            debugLog("H", "setActiveUI:changeui", "Calling panel.changeui", "panelType=" + panel.getClass().getSimpleName());
            // #endregion
            if (panel instanceof JOGLPanel) {
                ((JOGLPanel) panel).getLoop().changeui(ui);
            } else if (panel instanceof LWJGLPanel) {
                ((LWJGLPanel) panel).getLoop().changeui(ui);
            }
        } else if (panel == null && ui != null) {
            // #region agent log
            debugLog("H", "setActiveUI:no_panel", "Panel is null!", "");
            // #endregion
        }
        
        if (active != null && active != ui) {
            active.state(UI.State.BACKGROUND);
        }
        active = ui;
        
        if (ui != null) {
            ui.state(UI.State.ACTIVE);
            if (ui instanceof NUI) {
                UI.setInstance((NUI) ui);
            }
            relinkWidget(ui);
        }
        
        if (sessionWidget != null) {
            sessionWidget.setActive(ui);
        }
    }
    
    // #region agent log
    private static final String DEBUG_LOG_PATH = "c:\\Game\\Lanfir-nurgling2\\.cursor\\debug.log";
    private static void debugLog(String hypothesisId, String location, String message, String data) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(DEBUG_LOG_PATH, true))) {
            pw.println("{\"hypothesisId\":\"" + hypothesisId + "\",\"location\":\"" + location + "\",\"message\":\"" + message + "\",\"data\":\"" + data.replace("\"", "'").replace("\n", " ") + "\",\"timestamp\":" + System.currentTimeMillis() + "}");
        } catch (Exception e) { /* ignore */ }
    }
    // #endregion
    
    /**
     * Relink the session widget to the active UI's root.
     */
    private void relinkWidget(UI ui) {
        // #region agent log
        debugLog("A", "relinkWidget:entry", "relinkWidget called", "sessionWidget=" + (sessionWidget != null) + ",ui=" + ui + ",active=" + active + ",uiEqualsActive=" + (ui == active));
        // #endregion
        if (sessionWidget != null && ui == active) {
            try {
                // Only unlink if widget has a parent
                if (sessionWidget.parent != null) {
                    // #region agent log
                    debugLog("B", "relinkWidget:unlink", "Unlinking sessionWidget", "parent=" + sessionWidget.parent);
                    // #endregion
                    sessionWidget.unlink();
                }
                if (ui != null && ui.root != null) {
                    // #region agent log
                    debugLog("C", "relinkWidget:add", "Adding sessionWidget to root", "root=" + ui.root + ",rootSz=" + ui.root.sz);
                    // #endregion
                    ui.root.add(sessionWidget, Coord.z);
                    sessionWidget.raise();
                    // #region agent log
                    debugLog("C", "relinkWidget:added", "SessionWidget added and raised", "parent=" + sessionWidget.parent);
                    // #endregion
                } else {
                    // #region agent log
                    debugLog("D", "relinkWidget:no_root", "ui or ui.root is null", "ui=" + ui + ",root=" + (ui != null ? ui.root : "null"));
                    // #endregion
                }
            } catch (Exception e) {
                // #region agent log
                debugLog("X", "relinkWidget:error", "Exception in relinkWidget", "error=" + e.getClass().getName() + ":" + e.getMessage());
                // #endregion
            }
        } else {
            // #region agent log
            debugLog("E", "relinkWidget:skip", "Skipping relinkWidget", "sessionWidget=" + (sessionWidget != null) + ",uiEqualsActive=" + (ui == active));
            // #endregion
        }
    }
    
    /**
     * Switch to next session.
     */
    public void setNext() {
        if (active == null || uis.isEmpty() || uis.size() == 1) return;
        
        java.util.Iterator<UI> iter = uis.iterator();
        boolean found = false;
        while (iter.hasNext()) {
            UI ui = iter.next();
            if (found) {
                setActiveUI(ui);
                return;
            }
            if (ui == active) found = true;
        }
        // Wrap around to first
        if (found) setActiveUI(uis.getFirst());
    }
    
    /**
     * Switch to previous session.
     */
    public void setPrev() {
        if (active == null || uis.isEmpty() || uis.size() == 1) return;
        
        java.util.Iterator<UI> iter = uis.descendingIterator();
        boolean found = false;
        while (iter.hasNext()) {
            UI ui = iter.next();
            if (found) {
                setActiveUI(ui);
                return;
            }
            if (ui == active) found = true;
        }
        // Wrap around to last
        if (found) setActiveUI(uis.getLast());
    }
    
    /**
     * Interrupt and close a session, choosing next one.
     */
    public void interruptAndChooseNext(UI ui) {
        ArrayList<UI> uiList = new ArrayList<>(this.uis);
        UI currentActive = this.active;
        
        removeUI(ui);
        
        if (ui != currentActive) {
            // Not active, just interrupt it
            ui.state(UI.State.INTERRUPTED);
        } else {
            // Active session - need to choose next
            if (uiList.size() > 1) {
                // Find next session
                java.util.Iterator<UI> iter = uiList.iterator();
                boolean found = false;
                UI prev = null;
                while (iter.hasNext()) {
                    UI iui = iter.next();
                    if (found && iui != ui) {
                        setActiveUI(iui);
                        break;
                    }
                    if (iui == ui) {
                        if (prev != null) {
                            setActiveUI(prev);
                            break;
                        }
                        found = true;
                    }
                    if (iui != ui) prev = iui;
                }
            } else {
                // Last session - create new login
                createUI(true);
            }
            ui.state(UI.State.INTERRUPTED);
        }
    }
    
    /**
     * Close a UI session and cleanup.
     */
    public void closeUI(UI ui) {
        removeUI(ui);
        
        if (!closed.get()) {
            if (uis.isEmpty()) {
                // Last session closed - create new login
                startRunner();
            } else if (active == ui) {
                // Active session closed - switch to first
                setActiveUI(uis.getFirst());
            }
        } else {
            setActiveUI(null);
        }
    }
    
    /**
     * Terminate all sessions (application shutdown).
     */
    public void terminateAllUI() {
        closed.set(true);
        for (UI ui : uis) {
            ui.state(UI.State.INTERRUPTED);
            Thread runner = ui.getRunnerThread();
            if (runner != null) {
                runner.interrupt();
            }
            Thread uiThread = ui.getUiThread();
            if (uiThread != null) {
                uiThread.interrupt();
            }
        }
    }
    
    /**
     * Full termination - close panel thread and all UIs.
     */
    public void terminate() {
        terminateAllUI();
        
        // Wait for UI threads to finish
        for (UI ui : uis) {
            Thread t = ui.getUiThread();
            if (t != null) {
                try {
                    t.join(3000);
                } catch (InterruptedException e) {
                    // Ignore
                }
                if (t.isAlive()) {
                    new Warning("UI thread failed to terminate: " + t.getName()).issue();
                }
            }
        }
    }
    
    /**
     * Initialize the session widget.
     * Should be called after first UI is created.
     */
    public void initSessionWidget() {
        // #region agent log
        debugLog("F", "initSessionWidget:entry", "initSessionWidget called", "sessionWidget=" + (sessionWidget != null));
        // #endregion
        if (sessionWidget == null) {
            sessionWidget = new SessionWidget(this);
            // #region agent log
            debugLog("F", "initSessionWidget:created", "SessionWidget created", "sessionWidget=" + sessionWidget);
            // #endregion
        }
    }
    
    /**
     * Get the currently active session's username (for display).
     */
    public String getActiveUsername() {
        UI ui = active;
        if (ui != null && ui.sess != null && ui.sess.user != null) {
            return ui.sess.user.name;
        }
        return null;
    }
}
