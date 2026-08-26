package nurgling.sessions;

/**
 * Switching back to an existing session from the login screen (after "+"
 * without logging in) must not move the tab bar onto the headless session UI,
 * and must abort Bootstrap's login wait so the pending switch can complete.
 */
public final class SessionSwitchFromLogin {
    public static final String MSG = "sesswitch";

    private SessionSwitchFromLogin() {
    }

    /**
     * @param displayedSession session owning the currently rendered UI, or
     *                         {@code null} when the login screen is shown
     * @return true to leave the tab bar on the displayed (login) UI
     */
    public static boolean keepTabBarOnDisplayedUi(SessionContext displayedSession) {
        return displayedSession == null;
    }

    public static boolean isSwitchMessage(String name) {
        return MSG.equals(name);
    }
}
