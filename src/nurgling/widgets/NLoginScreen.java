package nurgling.widgets;

import haven.*;
import nurgling.NConfig;
import nurgling.conf.NCharTags;
import nurgling.widgets.login.NBackdrop;
import nurgling.widgets.login.NLoginPanel;
import nurgling.widgets.login.NLoginStatusBar;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class NLoginScreen extends LoginScreen {
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long BASE_RETRY_DELAY_MS = 1000, MAX_RETRY_DELAY_MS = 30000;
    private static final int VERSION_CHECK_TIMEOUT_MS = 2000, MARGIN = UI.scale(64);
    private NLoginStatusBar statusbar;
    private boolean autoPending = false;
    private int retryAttempt = 0;
    private long nextRetryTime = 0;
    private String pending = null;
    private AuthClient.Credentials pendingCredentials = null;
    private boolean authenticating = false;
    private int formtop = -1;
    private int formmin = 0, formmax = 0;

    static boolean isRemoteVersionNewer(String remoteVersion, String localVersion) {
        if ((remoteVersion == null) || (localVersion == null)) return (false);
        String[] remoteParts = remoteVersion.trim().split("\\."), localParts = localVersion.trim().split("\\.");
        int n = Math.max(remoteParts.length, localParts.length);
        for (int i = 0; i < n; i++) {
            int r = i < remoteParts.length ? parseVersionPart(remoteParts[i]) : 0;
            int l = i < localParts.length ? parseVersionPart(localParts[i]) : 0;
            if (r > l) return (true);
            if (r < l) return (false);
        }
        return (false);
    }
    private static int parseVersionPart(String part) { try { return Integer.parseInt(part); } catch (NumberFormatException e) { return 0; } }

    public NLoginScreen(String hostname) {
        super(hostname);
        statusbar = add(new NLoginStatusBar(HttpStatus.mond.get(), sz.x - (2 * MARGIN)));
        layout(); startVersionCheck();
    }

    @Override protected Widget mkbg() { return (new NBackdrop(() -> bg, NBackdrop.SCRIMW)); }
    @Override protected Widget mkcredbox() { return (new NLoginPanel(confname, this::submit)); }
    @Override protected void submitCredentials(AuthClient.Credentials creds, boolean savepw) { submit(creds, savepw); }
    private NLoginPanel panel() { return ((NLoginPanel) login); }

    @Override public void presize() {
        super.presize();
        if (statusbar != null)
            layout();
    }

    private void layout() {
        int vtop = 0, vbot = sz.y;
        if (parent != null) {
            vtop = Math.max(0, -c.y);
            vbot = Math.min(sz.y, parent.sz.y - c.y);
        }
        optbtn.move(Coord.of(sz.x - optbtn.sz.x - UI.scale(20), vtop + UI.scale(20)));
        statusbar.move(Coord.of(MARGIN, vbot - statusbar.sz.y - UI.scale(10)));
        formmin = vtop + UI.scale(40);
        formmax = statusbar.c.y - UI.scale(12);
        panel().budget(formmax - formmin);
        formtop = -1; placeform();
    }
    private void placeform() {
        if (formtop < 0)
            formtop = formmin + Math.max(0, ((formmax - formmin) - panel().stableh()) / 2);
        login.move(Coord.of(MARGIN, formtop));
    }
    @Override public void cresize(Widget ch) { if ((ch == login) && (statusbar != null)) placeform(); }

    @Override public void uimsg(String msg, Object... args) {
        if (msg == "login") { authenticating = false; setSteamBusy(false); login.show(); panel().ready(); if (NConfig.isBotMod()) autoPending = true; }
        else if (msg == "prg") { setSteamBusy(true); login.show(); panel().busy((String) args[0]); }
        else if (msg == "error") {
            authenticating = false; setSteamBusy(false); panel().authFailed(); pending = null; pendingCredentials = null;
            panel().error((String) args[0]); if (NConfig.isBotMod()) autoPending = true;
        }
        else super.uimsg(msg, args);
    }
    @Override public void tick(double dt) { super.tick(dt); if (autoPending && NConfig.isBotMod()) attemptAutoLogin(); }

    private void attemptAutoLogin() {
        if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
            System.err.println("[NLoginScreen] Maximum retry attempts (" + MAX_RETRY_ATTEMPTS + ") reached. Auto-login disabled to prevent ban.");
            System.exit(1); return;
        }
        long now = System.currentTimeMillis();
        if (now < nextRetryTime) return;
        autoPending = false; retryAttempt++;
        nextRetryTime = now + Math.min(BASE_RETRY_DELAY_MS * (1L << retryAttempt), MAX_RETRY_DELAY_MS);
        authenticating = true; setSteamBusy(true);
        send(new AuthClient.NativeCred(NConfig.botmod.user, NConfig.botmod.pass), false);
    }
    private boolean submit(AuthClient.Credentials creds, boolean savepw) {
        if (authenticating) return (false);
        authenticating = true; setSteamBusy(true);
        panel().busy(null);
        autoPending = false; retryAttempt = 0; nextRetryTime = 0; send(creds, savepw);
        return (true);
    }
    private void send(AuthClient.Credentials creds, boolean savepw) {
        pendingCredentials = creds; pending = creds.authname(); wdgmsg("login", creds, savepw);
    }
    private void setSteamBusy(boolean busy) { if (steambtn != null) steambtn.disable(busy); }

    @Override public void destroy() {
        panel().authSucceeded();
        String account = (pendingCredentials == null) ? pending : pendingCredentials.authname();
        if (account != null) NCharTags.setUsed(account, System.currentTimeMillis());
        super.destroy();
    }

    private void startVersionCheck() {
        /* New launchers pass the selected channel; legacy launchers still use the profile setting. */
        String channelUrl = System.getProperty("nurgling.updateurl");
        Object baseurl = channelUrl != null ? channelUrl : NConfig.get(NConfig.Key.baseurl);
        Thread checker = new HackThread(() -> {
            String local = readLocalVersion(), remote = null;
            if ((local != null) && (baseurl instanceof String)) {
                try {
                    URLConnection conn = new URL((String) baseurl).openConnection();
                    conn.setConnectTimeout(VERSION_CHECK_TIMEOUT_MS); conn.setReadTimeout(VERSION_CHECK_TIMEOUT_MS);
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) { remote = in.readLine(); }
                } catch (IOException ignored) {}
            }
            statusbar.versions(local, isRemoteVersionNewer(remote, local) ? remote.trim() : null);
        }, "Version check");
        checker.setDaemon(true); checker.start();
    }
    private static String readLocalVersion() {
        if (!new File("ver").isFile()) return (null);
        try (BufferedReader in = Files.newBufferedReader(Paths.get("ver"), StandardCharsets.UTF_8)) {
            String line = in.readLine(); return ((line == null) || line.trim().isEmpty()) ? null : line.trim();
        } catch (IOException e) { return (null); }
    }
}
