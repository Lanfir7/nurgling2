/*
 * Session switching widget for multi-session support.
 * Based on BeyondSRC SessionWidget architecture.
 * 
 * Features:
 * - Visual session switcher with avatar thumbnails
 * - Add/Remove/Next/Prev session buttons
 * - Hotkeys for quick switching
 * - Status indicators (active, fight, notification)
 */

package nurgling.widgets;

import haven.*;
import nurgling.UIObserver;
import nurgling.NUI;
import nurgling.NGameUI;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.concurrent.ConcurrentLinkedDeque;

public class SessionWidget extends Widget {
    // Sizes
    private static final int AVATAR_SIZE = UI.scale(32);
    private static final int BUTTON_SIZE = UI.scale(24);
    private static final Coord AVATAR_COORD = new Coord(AVATAR_SIZE, AVATAR_SIZE);
    
    // Key bindings
    private static final int KEY_ADD = KeyEvent.VK_MULTIPLY;      // *
    private static final int KEY_NEXT = KeyEvent.VK_ADD;          // +
    private static final int KEY_PREV = KeyEvent.VK_SUBTRACT;     // -
    private static final int KEY_CLOSE = KeyEvent.VK_DELETE;      // Delete
    
    // Session avatars
    private final ConcurrentLinkedDeque<SessionAvatar> avatars = new ConcurrentLinkedDeque<>();
    
    // Observer reference
    private final UIObserver observer;
    
    // Control buttons
    private final Button addBtn;
    private final Button prevBtn;
    private final Button nextBtn;
    private final Button closeBtn;
    
    public SessionWidget(UIObserver observer) {
        this.observer = observer;
        
        // Create control buttons
        addBtn = new Button(BUTTON_SIZE, "+", false) {
            @Override
            public void click() {
                final boolean switchNow = (ui.modflags() == UI.MOD_SHIFT);
                // Defer createUI to next tick so we don't block the render/event thread
                ui.queue.submit(new UI.Command(() -> {
                    try {
                        UI newui = observer.createUI();
                        if (switchNow && newui != null) {
                            observer.setActiveUI(newui);
                        }
                    } catch (Throwable t) {
                        new haven.Warning(t, "createUI failed").issue();
                    }
                }));
            }
        };
        addBtn.settip("New Session (*)\nShift+Click to switch immediately");
        
        prevBtn = new Button(BUTTON_SIZE, "<", false) {
            @Override
            public void click() {
                observer.setPrev();
            }
        };
        prevBtn.settip("Previous Session (-)");
        
        nextBtn = new Button(BUTTON_SIZE, ">", false) {
            @Override
            public void click() {
                observer.setNext();
            }
        };
        nextBtn.settip("Next Session (+)");
        
        closeBtn = new Button(BUTTON_SIZE, "X", false) {
            @Override
            public void click() {
                closeActiveSession();
            }
        };
        closeBtn.settip("Close Session (Del)");
        
        // Add buttons (like BeyondSRC: only session switcher, no "new window" key)
        add(addBtn, Coord.z);
        add(prevBtn, new Coord(BUTTON_SIZE, 0));
        add(nextBtn, new Coord(BUTTON_SIZE * 2, 0));
        add(closeBtn, new Coord(BUTTON_SIZE * 3, 0));
        
        pack();
        // #region agent log
        debugLog("I", "constructor:done", "SessionWidget created", "sz=" + sz + ",childCount=" + countChildren());
        // #endregion
    }
    
    // #region agent log
    private int countChildren() {
        int count = 0;
        for (Widget w = child; w != null; w = w.next) count++;
        return count;
    }
    private static int drawCount = 0;
    private static final String DEBUG_LOG_PATH = "c:\\Game\\Lanfir-nurgling2\\.cursor\\debug.log";
    private static void debugLog(String hypothesisId, String location, String message, String data) {
        try (java.io.FileWriter fw = new java.io.FileWriter(DEBUG_LOG_PATH, true);
             java.io.PrintWriter pw = new java.io.PrintWriter(fw)) {
            pw.println("{\"hypothesisId\":\"" + hypothesisId + "\",\"location\":\"" + location + 
                       "\",\"message\":\"" + message + "\",\"data\":\"" + data.replace("\"", "'") + 
                       "\",\"timestamp\":" + System.currentTimeMillis() + "}");
        } catch (Exception e) { }
    }
    // #endregion
    
    private void closeActiveSession() {
        if (observer.active != null) {
            observer.interruptAndChooseNext(observer.active);
        }
    }
    
    @Override
    public void tick(double dt) {
        super.tick(dt);
        
        // Auto-attach to active UI's root if detached or attached to wrong UI
        UI activeUI = observer.active;
        if (activeUI != null && activeUI.root != null) {
            // Check if we're attached to the correct root
            if (parent == null || parent != activeUI.root) {
                // Detach from old parent if any
                if (parent != null) {
                    unlink();
                }
                // Attach to active UI's root
                activeUI.root.add(this, Coord.z);
            }
            // Always raise to stay on top of GameUI and other widgets
            raise();
        }
    }
    
    // #region agent log
    @Override
    public void unlink() {
        debugLog("K", "unlink:called", "SessionWidget.unlink() called", "parent=" + parent + ",caller=" + Thread.currentThread().getStackTrace()[2]);
        super.unlink();
    }
    
    @Override
    public void destroy() {
        debugLog("K", "destroy:called", "SessionWidget.destroy() called", "parent=" + parent);
        super.destroy();
    }
    // #endregion
    
    /**
     * Add a UI session to the widget.
     */
    public void addUI(UI ui) {
        SessionAvatar avatar = new SessionAvatar(ui);
        avatars.add(avatar);
        add(avatar);
        pack();
    }
    
    /**
     * Remove a UI session from the widget.
     */
    public void removeUI(UI ui) {
        avatars.removeIf(avatar -> {
            if (avatar.sessionUI == ui) {
                avatar.reqdestroy();
                return true;
            }
            return false;
        });
        pack();
    }
    
    /**
     * Set the active session (for highlighting).
     */
    public void setActive(UI ui) {
        for (SessionAvatar avatar : avatars) {
            if (avatar.sessionUI == ui) {
                avatar.setActive(true);
            } else {
                avatar.setActive(false);
            }
        }
    }
    
    // #region agent log
    @Override
    public void draw(GOut g) {
        drawCount++;
        if (drawCount == 1 || drawCount == 100 || drawCount == 500 || drawCount == 1000 || drawCount == 2000 || drawCount == 5000) {
            debugLog("I", "draw:called", "SessionWidget draw called", "drawCount=" + drawCount + ",sz=" + sz + ",c=" + c + ",parent=" + parent + ",visible=" + visible);
        }
        super.draw(g);
    }
    // #endregion
    
    @Override
    public void pack() {
        // Layout avatars in a 3-column grid below buttons
        int y0 = BUTTON_SIZE + UI.scale(4);
        int col = 0;
        int row = 0;
        for (SessionAvatar avatar : avatars) {
            avatar.move(new Coord(col * AVATAR_SIZE, y0 + row * AVATAR_SIZE));
            col++;
            if (col >= 3) {
                col = 0;
                row++;
            }
        }
        super.pack();
    }
    
    @Override
    public boolean keydown(KeyDownEvent ev) {
        int code = ev.awt.getKeyCode();
        int mods = ev.awt.getModifiersEx();
        boolean ctrl = (mods & KeyEvent.CTRL_DOWN_MASK) != 0;
        
        // #region agent log
        debugLog("I", "keydown:called", "SessionWidget keydown received", "code=" + code + ",ctrl=" + ctrl + ",mods=" + mods);
        // #endregion
        
        if (ctrl) {
            boolean shift = (mods & KeyEvent.SHIFT_DOWN_MASK) != 0;
            UI active = observer.active;
            switch (code) {
                case KEY_ADD:
                case KeyEvent.VK_8: // Shift+8 = *
                    UI newui = observer.createUI();
                    if (shift && newui != null) {
                        if (active != null)
                            active.queue.submit(new UI.Command(() -> observer.setActiveUI(newui)));
                        else
                            observer.setActiveUI(newui);
                    }
                    return true;
                case KEY_NEXT:
                case KeyEvent.VK_EQUALS: // Shift+= = +
                    if (active != null)
                        active.queue.submit(new UI.Command(observer::setNext));
                    else
                        observer.setNext();
                    return true;
                case KEY_PREV:
                    if (active != null)
                        active.queue.submit(new UI.Command(observer::setPrev));
                    else
                        observer.setPrev();
                    return true;
                case KEY_CLOSE:
                    if (active != null)
                        active.queue.submit(new UI.Command(this::closeActiveSession));
                    else
                        closeActiveSession();
                    return true;
            }
        }
        
        return super.keydown(ev);
    }
    
    @Override
    public boolean globtype(GlobKeyEvent ev) {
        int code = ev.awt.getKeyCode();
        UI active = observer.active;
        
        switch (code) {
            case KEY_ADD:
                UI newui = observer.createUI();
                if (ui.modshift && newui != null) {
                    if (active != null)
                        active.queue.submit(new UI.Command(() -> observer.setActiveUI(newui)));
                    else
                        observer.setActiveUI(newui);
                }
                return true;
            case KEY_NEXT:
                if (active != null)
                    active.queue.submit(new UI.Command(observer::setNext));
                else
                    observer.setNext();
                return true;
            case KEY_PREV:
                if (active != null)
                    active.queue.submit(new UI.Command(observer::setPrev));
                else
                    observer.setPrev();
                return true;
        }
        
        return super.globtype(ev);
    }
    
    /**
     * Session avatar - represents a single session in the widget.
     */
    public class SessionAvatar extends Widget {
        public final UI sessionUI;
        private boolean isActive = false;
        private Color borderColor = Color.WHITE;
        private Color notifyColor = null;
        private long plid = -1;
        
        public SessionAvatar(UI ui) {
            super(AVATAR_COORD);
            this.sessionUI = ui;
        }
        
        public void setActive(boolean active) {
            this.isActive = active;
            this.borderColor = active ? Color.GREEN : Color.WHITE;
            if (active) {
                notifyColor = null; // Clear notification when activated
            }
        }
        
        public void setNotify() {
            if (!isActive) {
                notifyColor = Color.BLUE;
            }
        }
        
        @Override
        public void tick(double dt) {
            super.tick(dt);
            
            // Update avatar based on session state
            if (sessionUI != null && sessionUI.gui != null) {
                NGameUI gui = (NGameUI) sessionUI.gui;
                if (gui.plid != plid) {
                    plid = gui.plid;
                }
            }
        }
        
        @Override
        public void draw(GOut g) {
            // Draw border
            Color drawColor = borderColor;
            if (notifyColor != null) {
                // Pulse notification color
                double t = Utils.rtime() % 2;
                double blend = (Math.cos(t * Math.PI) + 1) / 2;
                drawColor = Utils.blendcol(notifyColor, borderColor, blend);
            }
            
            // Check if in fight
            if (sessionUI != null && sessionUI.gui != null) {
                NGameUI gui = (NGameUI) sessionUI.gui;
                Widget fs = gui.findchild(Fightsess.class);
                if (fs != null) {
                    double t = Utils.rtime() % 2;
                    double blend = (Math.cos(t * Math.PI) + 1) / 2;
                    drawColor = Utils.blendcol(Color.RED, drawColor, blend);
                }
            }
            
            g.chcolor(drawColor);
            g.rect(Coord.z, sz);
            g.chcolor();
            
            // Draw background
            g.chcolor(32, 32, 32, 192);
            g.frect(new Coord(1, 1), sz.sub(2, 2));
            g.chcolor();
            
            // Draw session info
            String name = getSessionName();
            if (name != null && name.length() > 0) {
                // Truncate long names
                if (name.length() > 5) {
                    name = name.substring(0, 4) + "...";
                }
                g.atext(name, sz.div(2), 0.5, 0.5);
            } else {
                g.atext("...", sz.div(2), 0.5, 0.5);
            }
            
            // Draw state indicator
            if (sessionUI != null) {
                String stateChar = "";
                switch (sessionUI.state()) {
                    case ACTIVE:
                        stateChar = "A";
                        break;
                    case BACKGROUND:
                        stateChar = "B";
                        break;
                    case INTERRUPTED:
                        stateChar = "X";
                        break;
                    default:
                        break;
                }
                g.atext(stateChar, new Coord(sz.x - 2, 2), 1, 0);
            }
        }
        
        private String getSessionName() {
            if (sessionUI == null) return null;
            if (sessionUI.sess != null && sessionUI.sess.user != null && sessionUI.sess.user.name != null) {
                return sessionUI.sess.user.name;
            }
            if (sessionUI instanceof NUI) {
                NUI nui = (NUI) sessionUI;
                if (nui.sessInfo != null && nui.sessInfo.username != null) {
                    return nui.sessInfo.username;
                }
            }
            return null;
        }
        
        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if (ev.b == 1) {
                // Left click - switch to this session. Defer to UI thread to avoid deadlock:
                // click is handled on GL thread, setActiveUI->changeui waits for GL to release lockedui.
                UI active = observer.active;
                if (active != null) {
                    final UI target = sessionUI;
                    active.queue.submit(new UI.Command(() -> observer.setActiveUI(target)));
                } else {
                    observer.setActiveUI(sessionUI);
                }
                return true;
            } else if (ev.b == 3) {
                // Right click - close this session (defer to UI thread for same reason)
                UI active = observer.active;
                if (active != null) {
                    final UI target = sessionUI;
                    active.queue.submit(new UI.Command(() -> observer.interruptAndChooseNext(target)));
                } else {
                    observer.interruptAndChooseNext(sessionUI);
                }
                return true;
            }
            return super.mousedown(ev);
        }
        
        @Override
        public Object tooltip(Coord c, Widget prev) {
            String name = getSessionName();
            if (name != null) {
                return name + (isActive ? " (Active)" : "");
            }
            return "New Session";
        }
    }
}
