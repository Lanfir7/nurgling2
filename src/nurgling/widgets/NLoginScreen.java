package nurgling.widgets;

import haven.*;
import haven.Label;
import haven.Window;
import nurgling.*;
import nurgling.conf.*;
import nurgling.i18n.L10n;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

public class NLoginScreen extends LoginScreen
{

    private boolean msgMode = false;

    ArrayList<NLoginDataItem> loginItems = new ArrayList<>();

    int marg = UI.scale(10);

    // Retry and backoff state for bot mode
    private boolean autoLoginInProgress = false;
    private long lastAutoLoginTime = 0;
    private int retryAttempt = 0;
    private long nextRetryTime = 0;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long BASE_RETRY_DELAY_MS = 1000; // Start with 1 second
    private static final long MAX_RETRY_DELAY_MS = 30000; // Cap at 30 seconds

    public NLoginScreen(String hostname)
    {
        super(hostname);
        int listW = NLoginAccountLook.CARD_WIDTH + UI.scale(12);
        add(new LoginList(new Coord(listW, UI.scale(bg.sz().y - marg * 2))), new Coord(marg, marg));
        optbtn.move(new Coord(bg.sz().x - UI.scale(130), UI.scale(30)));

        IButton discordBtn = new IButton("nurgling/hud/buttons/discord/", "u", "d", "h") {
            @Override
            public void click() {
                try {
                    ui.wnd.toolkit().browse(java.net.URI.create("https://discord.com/invite/3YF5yaKKPn"));
                } catch (Exception e) {
                    System.err.println("[NLoginScreen] Failed to open Discord link: " + e.getMessage());
                }
            }
        };
        adda(discordBtn, bg.sz().x - UI.scale(50), bg.sz().y - UI.scale(50), 1.0, 1.0);

        adda(new StatusLabel(HttpStatus.mond.get(), 0.5), bg.sz().x/2, bg.sz().y, 0.5, 1);
        ArrayList<NLoginData> logpass = (ArrayList<NLoginData>) NConfig.get(NConfig.Key.credentials);
        if (logpass != null)
        {
            for (NLoginData item : logpass)
            {
                loginItems.add(new NLoginDataItem(item));
            }
        }
        
        // Check for version updates asynchronously to avoid blocking UI
        checkVersionAsync();
    }
    
    /**
     * Асинхронная проверка версии — HTTP-запрос выполняется в отдельном потоке,
     * чтобы не блокировать UI при медленном соединении.
     */
    private void checkVersionAsync() {
        Thread versionCheckThread = new Thread(() -> {
            try {
                if (!new File("ver").exists()) {
                    return;
                }
                
                URL upd_url = new URL((String) Objects.requireNonNull(NConfig.get(NConfig.Key.baseurl)));
                ReadableByteChannel rbc = null;
                FileOutputStream fos = null;
                BufferedReader reader = null;
                BufferedReader reader2 = null;
                
                String remoteLine = null;
                String localLine = null;
                
                try {
                    // Attempt to download version file
                    rbc = Channels.newChannel(upd_url.openStream());
                    
                    // Check if channel was successfully created before proceeding
                    if (rbc == null) {
                        return;
                    }
                    
                    fos = new FileOutputStream("tmp_ver");
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();
                    
                    // Read remote version
                    reader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get("tmp_ver")), StandardCharsets.UTF_8));
                    remoteLine = reader.readLine();
                    reader.close();
                    
                    // Read local version
                    reader2 = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get("ver")), StandardCharsets.UTF_8));
                    localLine = reader2.readLine();
                    reader2.close();
                } finally {
                    // Ensure all resources are properly closed
                    try { if (rbc != null) rbc.close(); } catch (IOException ignored) {}
                    try { if (fos != null) fos.close(); } catch (IOException ignored) {}
                    try { if (reader != null) reader.close(); } catch (IOException ignored) {}
                    try { if (reader2 != null) reader2.close(); } catch (IOException ignored) {}
                }
                
                // Compare versions and show update window on UI thread if needed
                final String remoteVersion = remoteLine;
                final String localVersion = localLine;
                if (NUpdateFeed.needsUpdate(localVersion, remoteVersion)) {
                    java.awt.EventQueue.invokeLater(() -> showVersionUpdateWindow());
                }
            } catch (Exception e) {
                // Silently ignore all version check errors to prevent login screen crashes
                System.err.println("[NLoginScreen] Version check failed: " + e.getMessage());
            }
        }, "VersionCheck");
        versionCheckThread.setDaemon(true);
        versionCheckThread.start();
    }
    
    /**
     * Показывает окно с уведомлением о новой версии (вызывается на UI-потоке).
     */
    private void showVersionUpdateWindow() {
        try {
            Window win = adda(new Window(new Coord(UI.scale(150, 40)), L10n.get("login.attention")) {
                @Override
                public void wdgmsg(String msg, Object... args) {
                    if (msg.equals("close")) {
                        hide();
                    } else {
                        super.wdgmsg(msg, args);
                    }
                }
            }, bgc.x, bg.sz().y / 8, 0.5, 0.5);
            win.add(new Label(L10n.get("login.new_version")));
        } catch (Exception e) {
            System.err.println("[NLoginScreen] Failed to show version update window: " + e.getMessage());
        }
    }

    @Override
    protected void progress(String p)
    {
        super.progress(p);
        if (NConfig.isBotMod())
        {
            attemptAutoLogin();
        }
    }

    /**
     * Attempts auto-login with retry logic and exponential backoff.
     * Prevents infinite retry loops that cause account bans.
     */
    private void attemptAutoLogin()
    {
        long currentTime = System.currentTimeMillis();

        // Check if we've exceeded maximum retry attempts
        if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
            System.err.println("[NLoginScreen] Maximum retry attempts (" + MAX_RETRY_ATTEMPTS + ") reached. Auto-login disabled to prevent ban.");
            System.err.println("[NLoginScreen] Terminating game to prevent further connection attempts.");

            // Terminate the game process - same pattern used by scenario bots
            // No need to logout since we never successfully logged in
            System.exit(1); // Exit code 1 indicates failure
            return;
        }

        // Check if we're already in the process of logging in
        if (autoLoginInProgress) {
            return; // Don't send duplicate login attempts
        }

        // Check if we need to wait for backoff delay
        if (currentTime < nextRetryTime) {
            long remainingWait = nextRetryTime - currentTime;
            if (remainingWait > 1000) { // Only log if more than 1 second remaining
                System.out.println("[NLoginScreen] Auto-login waiting " + (remainingWait / 1000) + " seconds before retry attempt " + (retryAttempt + 1));
            }
            return;
        }

        // Check if this is too soon after last attempt (prevents rapid-fire retries)
        if (currentTime - lastAutoLoginTime < 500) { // Minimum 500ms between attempts
            return;
        }

        try {
            // Mark login as in progress to prevent duplicates
            autoLoginInProgress = true;
            lastAutoLoginTime = currentTime;

            System.out.println("[NLoginScreen] Auto-login attempt " + (retryAttempt + 1) + "/" + MAX_RETRY_ATTEMPTS);

            // Send the login credentials
            wdgmsg("login", new Object[]{new AuthClient.NativeCred(NConfig.botmod.user, NConfig.botmod.pass), false});

            // Prepare for potential retry with exponential backoff
            retryAttempt++;
            long backoffDelay = Math.min(BASE_RETRY_DELAY_MS * (1L << retryAttempt), MAX_RETRY_DELAY_MS);
            nextRetryTime = currentTime + backoffDelay;

            // Reset login progress flag after a short delay to allow for success
            // This gets reset earlier if login succeeds via onLoginSuccess()
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // Give 2 seconds for login to potentially succeed
                    if (autoLoginInProgress) {
                        autoLoginInProgress = false; // Reset if still in progress
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

        } catch (Exception e) {
            System.err.println("[NLoginScreen] Auto-login attempt failed: " + e.getMessage());
            autoLoginInProgress = false;
        }
    }

    /**
     * Call this when login succeeds to reset retry state
     */
    private void onLoginSuccess() {
        System.out.println("[NLoginScreen] Auto-login successful, resetting retry state");
        autoLoginInProgress = false;
        retryAttempt = 0;
        nextRetryTime = 0;
        lastAutoLoginTime = 0;
    }

    /**
     * Call this to reset retry state (e.g., when user manually logs in)
     */
    public void resetAutoLoginState() {
        autoLoginInProgress = false;
        retryAttempt = 0;
        nextRetryTime = 0;
        lastAutoLoginTime = 0;
    }

    public void wdgmsg(
            Widget sender,
            String msg,
            Object... args
    )
    {

        if (sender == this && !msgMode)
        {
            Credbox clogin = (Credbox) login;
            if (!clogin.pass.text().isEmpty())
            {
                saveLoginPass(clogin.user.text(), clogin.pass.text());
            }
            else
            {
                if (args[0] != null && args[0] instanceof AuthClient.TokenCred) {
                    saveLoginToken(clogin.user.text(), ((AuthClient.TokenCred) args[0]).token);
                }

            }
        }

        super.wdgmsg(sender, msg, args);
    }

    void saveLoginPass(String login, String pass)
    {
        ArrayList<NLoginData> logpass;
        if (!pass.isEmpty())
        {

            logpass = (ArrayList<NLoginData>) (NConfig.get(NConfig.Key.credentials));
            if (logpass == null)
            {
                logpass = new ArrayList<>();
            }
            boolean isFound = false;
            for (NLoginData item : logpass)
            {
                if (item.name.equals(login))
                {
                    if (!pass.equals(item.pass))
                    {
                        item.pass = pass;
                        item.isTokenUsed = false;
                        NConfig.set(NConfig.Key.credentials, logpass);
                    }
                    isFound = true;
                }
            }
            if (!isFound)
            {
                logpass.add(new NLoginData(login, pass));
                NConfig.set(NConfig.Key.credentials, logpass);
            }
        }
    }


    void saveLoginToken(String login, byte[] buff)
    {
        ArrayList<NLoginData> logpass;
        if (buff.length > 0)
        {

            logpass = (ArrayList<NLoginData>) NConfig.get(NConfig.Key.credentials);
            if (logpass == null)
            {
                logpass = new ArrayList<>();
            }
            boolean isFound = false;
            for (NLoginData item : logpass)
            {
                if (item.name.equals(login))
                {
                    if (item.token != null && item.token.length == buff.length)
                    {
                        for (int i = 0; i < buff.length; i++)
                            if (buff[i] != item.token[i])
                            {
                                item.token = Arrays.copyOf(buff, buff.length);
                                item.isTokenUsed = true;
                                item.pass = "";
                                NConfig.set(NConfig.Key.credentials, logpass);
                            }

                    }
                    else
                    {
                        item.token = Arrays.copyOf(buff, buff.length);
                        item.isTokenUsed = true;
                        item.pass = "";
                        NConfig.set(NConfig.Key.credentials, logpass);
                    }
                    isFound = true;
                }
            }
            if (!isFound)
            {
                logpass.add(new NLoginData(login, Arrays.copyOf(buff, buff.length)));
                NConfig.set(NConfig.Key.credentials, logpass);
            }
        }
    }

    public void removeToken()
    {
        ArrayList<NLoginData> logpass = ((ArrayList<NLoginData>) NConfig.get(NConfig.Key.credentials));
        if (logpass != null)
            for (NLoginData item : logpass)
            {
                if (item.name.equals(((Credbox)login).user.text()))
                {
                    logpass.remove(item);
                    NConfig.set(NConfig.Key.credentials, logpass);
                    for (NLoginDataItem item1 : loginItems)
                    {
                        if (item1.nd == item)
                            loginItems.remove(item1);
                        break;
                    }
                    break;
                }

            }
    }


    public class LoginList extends SListBox<NLoginDataItem, Widget>
    {
        LoginList(Coord sz)
        {
            super(sz, NLoginAccountLook.CARD_HEIGHT, NLoginAccountLook.CARD_PADDING);
            pack();
        }

        @Override
        protected void drawbg(GOut g, NLoginDataItem item, int idx, Area area) {
        }

        @Override
        protected void drawsel(GOut g, NLoginDataItem item, int idx, Area area) {
        }

        protected List<NLoginDataItem> items()
        {
            return (loginItems);
        }

        protected Widget makeitem(NLoginDataItem item, int idx, Coord sz)
        {
            return (new ItemWidget<NLoginDataItem>(this, sz, item)
            {
                {
                    item.resize(sz);
                    add(item);
                }
            });
        }
    }


    public class NLoginDataItem extends Widget
    {
        NLoginData nd;

        public NLoginDataItem(NLoginData nd)
        {
            super(new Coord(NLoginAccountLook.CARD_WIDTH, NLoginAccountLook.CARD_HEIGHT));
            this.nd = nd;
        }

        private boolean hovered() {
            if (ui == null)
                return false;
            Coord mc = ui.mc.sub(rootpos());
            return mc.isect(Coord.z, sz);
        }

        private boolean closeHovered() {
            if (ui == null)
                return false;
            return NLoginAccountLook.inClose(ui.mc.sub(rootpos()), sz);
        }

        @Override
        public void draw(GOut g) {
            NLoginAccountLook.drawCard(g, sz, nd.name, hovered(), closeHovered());
            super.draw(g);
        }

        private void forget() {
            ArrayList<NLoginData> ld = ((ArrayList<NLoginData>) NConfig.get(NConfig.Key.credentials));
            ld.remove(nd);
            NConfig.set(NConfig.Key.credentials, ld);
            loginItems.remove(this);
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if (ev.b != 1)
                return super.mousedown(ev);
            if (NLoginAccountLook.inClose(ev.c, sz)) {
                forget();
                return true;
            }
            msgMode = true;
            NLoginScreen.this.resetAutoLoginState();
            if (!nd.isTokenUsed)
                NLoginScreen.this.wdgmsg("login", new Object[]{new AuthClient.NativeCred(nd.name, nd.pass), false});
            else
                NLoginScreen.this.wdgmsg("login", new Object[]{new AuthClient.TokenCred(nd.name, Arrays.copyOf(nd.token, nd.token.length)), false});
            msgMode = false;
            return true;
        }
    }
}
