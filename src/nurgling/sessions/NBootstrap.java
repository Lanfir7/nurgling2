package nurgling.sessions;

import haven.*;

/**
 * Extended Bootstrap with multi-session support.
 *
 * This class overrides:
 * - preRun() to check for pending session switches
 * - createRemoteUI() to return NRemoteUI for multi-session support
 *
 * When a session switch is pending, it skips normal bootstrap (login screen)
 * and directly returns a NRemoteUI connected to the target session.
 */
public class NBootstrap extends Bootstrap {

    /**
     * Create a new NBootstrap with the default server.
     * This should be used instead of Bootstrap.create() to ensure
     * multi-session support works correctly.
     */
    public static NBootstrap create() {
        return new NBootstrap();
    }

    /**
     * Override to create NRemoteUI instead of RemoteUI.
     * This enables multi-session support by using NRemoteUI which:
     * - Registers sessions with SessionManager
     * - Attaches SessionTabBar to the UI
     * - Handles session switching via DetachMessage
     */
    @Override
    protected RemoteUI createRemoteUI(Session sess) {
        return new NRemoteUI(sess);
    }

    /**
     * Hook called before normal bootstrap runs.
     * Checks if we're switching to an existing session instead of starting a new one.
     *
     * @param ui The UI instance
     * @return non-null Runner to skip bootstrap, null to continue normally
     */
    @Override
    protected UI.Runner preRun(UI ui) throws InterruptedException {
        return consumePendingSwitch(ui);
    }

    @Override
    protected UI.Runner onLoginWaitMessage(UI ui, String msgName) throws InterruptedException {
        if (SessionSwitchFromLogin.isSwitchMessage(msgName)) {
            return consumePendingSwitch(ui);
        }
        return null;
    }

    private UI.Runner consumePendingSwitch(UI ui) throws InterruptedException {
        SessionManager sm = SessionManager.getInstance();
        SessionContext switchTo = sm.consumePendingSwitchTo();

        if (switchTo != null && switchTo.session != null) {
            switchTo.promoteToVisual(ui.getenv());
            sm.applyPendingCameraState(switchTo);
            Thread.sleep(100);
            return new NRemoteUI(switchTo.session);
        }
        return null;
    }
}
