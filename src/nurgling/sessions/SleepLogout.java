package nurgling.sessions;

/**
 * After Sleep on a bed the server drops the character to the charlist
 * without closing the client. Close only the sleeping session; when it is
 * the last one, the normal client loop returns to the login screen.
 */
public final class SleepLogout {
    public enum Action {
        NONE,
        CLOSE_SESSION
    }

    private SleepLogout() {}

    public static boolean isSleepAction(String name) {
        return name != null && name.equalsIgnoreCase("Sleep");
    }

    public static Action decide(boolean sleepRequested, int sessionCount) {
        if (!sleepRequested) {
            return Action.NONE;
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
        apply(ui);
    }

    public static void onSessionEnded(haven.UI ui) {
        SessionManager sm = SessionManager.getInstance();
        SessionContext ctx = ui != null ? sm.findByUI(ui) : null;
        if (ctx != null) {
            ctx.consumeExitAfterSleep();
        }
    }

    private static void apply(haven.UI ui) {
        SessionManager sm = SessionManager.getInstance();
        SessionContext ctx = ui != null ? sm.findByUI(ui) : null;
        boolean sleepRequested = ctx != null && ctx.consumeExitAfterSleep();
        Action action = decide(sleepRequested, sm.getSessionCount());
        if (action == Action.NONE) {
            return;
        }
        if (ctx != null) {
            sm.requestCloseSession(ctx.sessionId);
        }
    }
}
