package nurgling.sessions;

/**
 * After Sleep on a bed the server drops the character to the charlist
 * without closing the client. Last session should exit; extra sessions
 * only close this one.
 */
public final class SleepLogout {
    public enum Action {
        NONE,
        CLOSE_SESSION,
        EXIT_CLIENT
    }

    static volatile Runnable exitHook = () -> System.exit(0);

    private SleepLogout() {}

    public static boolean isSleepAction(String name) {
        return name != null && name.equalsIgnoreCase("Sleep");
    }

    public static Action decide(boolean sleepRequested, int sessionCount) {
        if (!sleepRequested) {
            return Action.NONE;
        }
        if (sessionCount <= 1) {
            return Action.EXIT_CLIENT;
        }
        return Action.CLOSE_SESSION;
    }

    public static void markIfSleep(haven.UI ui, String action) {
        if (ui == null || !isSleepAction(action)) {
            return;
        }
        SessionContext ctx = SessionManager.getInstance().findByUI(ui);
        if (ctx != null) {
            ctx.markExitAfterSleep();
        }
    }

    public static void onCharlist(haven.UI ui) {
        apply(ui, true);
    }

    public static void onSessionEnded(haven.UI ui) {
        SessionManager sm = SessionManager.getInstance();
        SessionContext ctx = ui != null ? sm.findByUI(ui) : null;
        boolean sleepRequested = ctx != null && ctx.consumeExitAfterSleep();
        if (decide(sleepRequested, sm.getSessionCount()) == Action.EXIT_CLIENT) {
            exitHook.run();
        }
    }

    private static void apply(haven.UI ui, boolean closeNetwork) {
        SessionManager sm = SessionManager.getInstance();
        SessionContext ctx = ui != null ? sm.findByUI(ui) : null;
        boolean sleepRequested = ctx != null && ctx.consumeExitAfterSleep();
        Action action = decide(sleepRequested, sm.getSessionCount());
        if (action == Action.NONE) {
            return;
        }
        if (action == Action.EXIT_CLIENT) {
            if (closeNetwork && ui != null && ui.sess != null) {
                ui.sess.close();
            }
            exitHook.run();
            return;
        }
        if (ctx != null) {
            sm.requestCloseSession(ctx.sessionId);
        }
    }
}
