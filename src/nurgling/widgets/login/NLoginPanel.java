package nurgling.widgets.login;

import haven.*;
import nurgling.NConfig;
import nurgling.NStyle;
import nurgling.conf.NSavedAccounts;
import nurgling.conf.NSavedAccounts.Account;
import nurgling.i18n.L10n;
import nurgling.sessions.SessionManager;
import nurgling.widgets.NCharTagsWnd;
import nurgling.widgets.cookbook.HintTextEntry;

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.Arrays;

/** Login form preserving Nurgling's legacy password/token store behind the redesigned UI. */
public class NLoginPanel extends Widget {
    @FunctionalInterface
    public interface Submitter {
        boolean submit(AuthClient.Credentials credentials, boolean savePassword);
    }

    public static final int W = UI.scale(300);
    private static final int GAP = UI.scale(12), TIGHT = UI.scale(3);
    private static final int MINROWS = 3;
    private final String confname;
    private final Submitter submit;
    private Banner err, info;
    private Heading heading;
    private Section section;
    private NAccountList accounts;
    private ILabel userlbl, passlbl, caps, remhint, keyhint;
    private Field user, pass;
    private IButton eye;
    private TokenLine tokline;
    private CheckBox remember, obf;
    private Progress prog;
    private Button forget, loginbtn;
    private Account cur = null;
    private boolean busy = false, capson = false, capsok = true;
    private double capscheck = 0;
    private String pendingName = null, pendingPassword = null, pendingTokenAccount = null;
    private byte[] pendingToken = null;
    /** Height offered by the screen and current tallest stable form height. */
    private int budget = -1, stableh = 0;

    public NLoginPanel(String confname, Submitter submit) {
        super(Coord.of(W, 0)); this.confname = confname; this.submit = submit; setfocustab(true);
        err = add(new Banner(true)); info = add(new Banner(false));
        heading = add(new Heading(L10n.get("login.heading"), L10n.get("login.subheading")));
        section = add(new Section());
        accounts = add(new NAccountList(W, new NAccountList.Listener() {
            public void select(Account a) {
                if (busy) return;
                if (a == NAccountList.ANOTHER) { user.settext(""); pass.settext(""); NLoginPanel.this.setfocus(user); }
                else user.settext(a.name);
                userchanged();
            }
            public void activate(Account a) { select(a); enter(); }
            public void remove(Account a) { NSavedAccounts.remove(confname, a.name); if (a.name.equals(user.text())) user.settext(""); reload(); }
            public void reorder(java.util.List<Account> order) {
                java.util.List<String> names = new java.util.ArrayList<>();
                for (Account a : order) names.add(a.name);
                NSavedAccounts.reorder(names);
            }
        }));
        userlbl = add(new ILabel(L10n.get("login.username"), NLoginTheme.label));
        user = add(new Field(L10n.get("login.user_hint"), false));
        passlbl = add(new ILabel(L10n.get("login.password"), NLoginTheme.label));
        pass = add(new Field(L10n.get("login.pass_hint"), true));
        eye = add(new IButton(NStyle.visi[0].back, NStyle.visi[1].back, NStyle.visi[2].back, () -> pass.togglepw()));
        eye.settip(L10n.get("login.show_pw_tip"));
        caps = add(new ILabel(L10n.get("login.caps"), NLoginTheme.warnlabel));
        tokline = add(new TokenLine());
        remember = add(new CheckBox(L10n.get("login.remember_me"))); remember.a = true; remember.settip(L10n.get("login.remember_tip"), true);
        remhint = add(new ILabel(L10n.get("login.remember_hint"), NLoginTheme.hint));
        obf = add(new CheckBox(L10n.get("login.obfuscate")));
        obf.a = Boolean.TRUE.equals(NConfig.get(NConfig.Key.alwaysObfuscate));
        obf.settip(L10n.get("login.obfuscate_tip"), true);
        obf.changed = a -> NConfig.set(NConfig.Key.alwaysObfuscate, a);
        prog = add(new Progress()); keyhint = add(new ILabel(L10n.get("login.keys_hint"), NLoginTheme.hint));
        forget = add(new Button(UI.scale(80), L10n.get("login.forget_me"), this::forgetcur));
        loginbtn = add(new Button(UI.scale(110), L10n.get("login.button"), this::enter));
        int running = SessionManager.getInstance().getAllSessions().size();
        if (running > 0) info.set(L10n.get("login.adding_session", running));
        NSavedAccounts.migrate(confname); accounts.set(NSavedAccounts.list(confname));
        Account first = NSavedAccounts.list(confname).isEmpty() ? null : NSavedAccounts.list(confname).get(0);
        user.settext((first != null) ? first.name : Utils.getpref("loginname@" + confname, ""));
        userchanged();
    }

    public void ready() {
        busy = false; obf.a = Boolean.TRUE.equals(NConfig.get(NConfig.Key.alwaysObfuscate)); reload();
        if (parent != null) parent.setfocus(this);
        if (user.text().isEmpty() || (cur != null)) setfocus(user); else setfocus(pass);
    }
    public void busy(String what) { busy = true; prog.set(what); sync(); }
    public void error(String msg) { busy = false; err.set(msg); sync(); }

    private void enter() {
        if (busy) return;
        String nm = user.text();
        if (nm.trim().isEmpty()) { setfocus(user); return; }
        if ((cur != null) && cur.token) {
            byte[] tok = Bootstrap.gettoken(cur.name, confname);
            if (tok != null) {
                if (go(new AuthClient.TokenCred(cur.name, tok), false)) {
                    clearPending(); pendingTokenAccount = cur.name;
                }
                return;
            }
            NSavedAccounts.invalidateToken(confname, cur.name);
            reload();
        }
        if ((cur != null) && !cur.token && (cur.pass != null)) {
            if (go(new AuthClient.NativeCred(cur.name, cur.pass), true)) clearPending();
            return;
        }
        String pw = pass.text();
        if (pw.isEmpty()) { setfocus(pass); return; }
        AuthClient.Credentials creds = creds(nm, pw);
        boolean persist = shouldPersistNewCredential(remember.state());
        boolean accepted = go(creds, persist);
        if (!accepted) return;
        clearPending();
        if (shouldStageNewCredential(accepted, persist)) {
            pendingName = nm;
            if (creds instanceof AuthClient.TokenCred) {
                pendingToken = Arrays.copyOf(((AuthClient.TokenCred) creds).token, 32);
                pendingTokenAccount = nm;
            } else pendingPassword = pw;
        }
    }
    static boolean shouldPersistNewCredential(boolean remember) { return (remember); }
    static boolean shouldStageNewCredential(boolean accepted, boolean persist) { return (accepted && persist); }
    public void authSucceeded() {
        if ((pendingName != null) && (pendingToken != null))
            NSavedAccounts.saveToken(confname, pendingName, pendingToken);
        else if ((pendingName != null) && (pendingPassword != null))
            NSavedAccounts.savePassword(pendingName, pendingPassword);
        clearPending();
    }
    public void authFailed() {
        if ((pendingTokenAccount != null) && (Bootstrap.gettoken(pendingTokenAccount, confname) == null))
            NSavedAccounts.invalidateToken(confname, pendingTokenAccount);
        clearPending(); reload();
    }
    private void clearPending() {
        if (pendingToken != null) Arrays.fill(pendingToken, (byte) 0);
        pendingName = null; pendingPassword = null; pendingTokenAccount = null; pendingToken = null;
    }
    private boolean go(AuthClient.Credentials creds, boolean savepw) {
        err.clear();
        return submit.submit(creds, savepw);
    }
    private static AuthClient.Credentials creds(String nm, String pw) {
        if (pw.length() == 64) {
            try { return (new AuthClient.TokenCred(nm, Utils.hex.dec(pw))); }
            catch (IllegalArgumentException ignored) {}
        }
        return (new AuthClient.NativeCred(nm, pw));
    }
    private void forgetcur() {
        if (cur != null) {
            NCharTagsWnd.close();
            NSavedAccounts.remove(confname, cur.name);
            reload();
        }
    }
    private void reload() { accounts.set(NSavedAccounts.list(confname)); userchanged(); }
    private void userchanged() { cur = accounts.find(user.text()); accounts.show(cur); sync(); }

    private void sync() {
        boolean saved = (cur != null), haslist = (accounts.saved() > 0);
        vis(section, haslist); vis(accounts, haslist); vis(passlbl, !saved); vis(pass, !saved); vis(eye, !saved); vis(caps, !saved && capson);
        boolean legacy = saved && !cur.token; vis(tokline, legacy); if (legacy) tokline.set(cur);
        vis(remember, !saved); vis(remhint, !saved); vis(prog, busy); vis(loginbtn, !busy); vis(forget, !busy && saved); vis(keyhint, !busy && haslist);
        relayout();
    }
    private static void vis(Widget w, boolean v) { if (w.visible != v) { if (v) w.show(); else w.hide(); } }
    private static int stack(Widget w, int y, int gap) { if (!w.visible) return (y); w.move(Coord.of(0, y)); return (y + w.sz.y + gap); }

    /** Lets the screen constrain the account list without changing the form's visual hierarchy. */
    public void budget(int height) {
        if (height == budget)
            return;
        budget = height;
        relayout();
    }

    /** Tallest current form arrangement; used to keep its vertical anchor stable. */
    public int stableh() {
        return (stableh);
    }

    static int accountRowsForBudget(int budget, int reservedHeight, int rowHeight, int defaultRows) {
        if (budget <= 0)
            return (defaultRows);
        return (Math.max(MINROWS, (budget - reservedHeight) / rowHeight));
    }

    private void relayout() {
        boolean haslist = accounts.visible;
        int below = (userlbl.sz.y + TIGHT) + (user.sz.y + UI.scale(8))
                + (passlbl.sz.y + TIGHT) + (pass.sz.y + UI.scale(6)) + (caps.sz.y + UI.scale(4))
                + (remember.sz.y + TIGHT) + (remhint.sz.y + GAP) + (obf.sz.y + GAP)
                + loginbtn.sz.y + (haslist ? (UI.scale(6) + keyhint.sz.y) : 0);
        int above = (err.visible ? (err.sz.y + GAP) : 0) + (info.visible ? (info.sz.y + GAP) : 0)
                + heading.sz.y + GAP + UI.scale(6);
        int listgap = GAP + UI.scale(4);
        if (haslist) {
            above += section.sz.y + UI.scale(4);
            accounts.maxrows(accountRowsForBudget(budget, above + listgap + below, NAccountList.ROWH, NAccountList.DEFROWS));
        }
        stableh = above + (haslist ? (accounts.sz.y + listgap) : 0) + below;
        int y = 0;
        y = stack(err, y, GAP); y = stack(info, y, GAP); y = stack(heading, y, GAP + UI.scale(6)); y = stack(section, y, UI.scale(4));
        if (haslist) y = stack(accounts, y, listgap);
        y = stack(userlbl, y, TIGHT); y = stack(user, y, UI.scale(8));
        if (pass.visible) { y = stack(passlbl, y, TIGHT); eye.move(Coord.of(W - eye.sz.x - UI.scale(4), y + ((pass.sz.y - eye.sz.y) / 2))); y = stack(pass, y, UI.scale(6)); y = stack(caps, y, UI.scale(4)); }
        y = stack(tokline, y, UI.scale(10)); y = stack(remember, y, TIGHT); y = stack(remhint, y, GAP); y = stack(obf, y, GAP);
        int barh = loginbtn.sz.y;
        if (prog.visible) prog.move(Coord.of(0, y + ((barh - prog.sz.y) / 2)));
        else { loginbtn.move(Coord.of(W - loginbtn.sz.x, y)); if (forget.visible) forget.move(Coord.of(loginbtn.c.x - UI.scale(8) - forget.sz.x, y)); }
        y += barh;
        if (haslist) { y += UI.scale(6); keyhint.move(Coord.of(0, y)); y += keyhint.sz.y; }
        resize(Coord.of(W, y));
    }

    public void tick(double dt) {
        super.tick(dt);
        if (capsok && pass.visible && ((capscheck -= dt) <= 0)) {
            capscheck = 0.3; boolean on;
            try { on = Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK); }
            catch (UnsupportedOperationException e) { capsok = false; on = false; }
            if (on != capson) { capson = on; sync(); }
        }
    }

    private class Field extends HintTextEntry {
        Field(String hint, boolean pw) { super(W, hint, null); this.pw = pw; }
        void togglepw() { pw = !pw; redraw(); }
        protected void changed() { super.changed(); if (this == user) userchanged(); }
        public void activate(String text) { enter(); }
        public boolean keydown(KeyDownEvent ev) {
            if (busy) return (true);
            if (ConsoleHost.kb_histprev.key().match(ev)) { accounts.step(-1); return (true); }
            if (ConsoleHost.kb_histnext.key().match(ev)) { accounts.step(1); return (true); }
            return (super.keydown(ev));
        }
    }
    private static class Banner extends Widget {
        private final boolean error; private Text t;
        Banner(boolean error) { super(Coord.of(W, 0)); this.error = error; visible = false; }
        void set(String s) { t = NLoginTheme.body.renderwrap(s, error ? new Color(242, 201, 195) : new Color(240, 220, 198), W - UI.scale(18)); resize(Coord.of(W, t.sz().y + UI.scale(10))); show(); }
        void clear() { if (visible) hide(); }
        public void draw(GOut g) { Color c = error ? NLoginTheme.err : NLoginTheme.accent; g.chcolor(c.getRed(), c.getGreen(), c.getBlue(), 36); g.frect(Coord.z, sz); g.chcolor(c); g.frect(Coord.z, Coord.of(UI.scale(3), sz.y)); g.chcolor(); if (t != null) g.image(t.tex(), Coord.of(UI.scale(10), UI.scale(5))); }
    }
    static class Heading extends Widget {
        private final Text title, sub;
        Heading(String title, String sub) { super(Coord.z); this.title = NLoginTheme.heading.render(title); this.sub = ((sub == null) || sub.isEmpty()) ? null : NLoginTheme.sub.render(sub); int h = this.title.sz().y + ((this.sub != null) ? this.sub.sz().y - UI.scale(4) : 0) + UI.scale(8); resize(Coord.of(W, h)); }
        public void draw(GOut g) { g.image(title.tex(), Coord.z); if (sub != null) g.image(sub.tex(), Coord.of(0, title.sz().y - UI.scale(4))); g.chcolor(NLoginTheme.accent); g.frect(Coord.of(0, sz.y - UI.scale(2)), Coord.of(UI.scale(40), UI.scale(2))); g.chcolor(); }
    }
    private static class Section extends Widget {
        private final Text t, h;
        Section() { super(Coord.z); t = NLoginTheme.section.render(L10n.get("login.saved_accounts")); h = NLoginTheme.hint.render(L10n.get("login.saved_hint")); resize(Coord.of(W, Math.max(t.sz().y, h.sz().y))); }
        public void draw(GOut g) { g.image(t.tex(), Coord.of(0, sz.y - t.sz().y)); g.image(h.tex(), Coord.of(t.sz().x + UI.scale(8), sz.y - h.sz().y - UI.scale(1))); }
    }
    private static class TokenLine extends Widget {
        private Text hint; private Boolean tok = null;
        TokenLine() { super(Coord.of(W, 0)); }
        void set(Account a) { if ((tok != null) && (tok == a.token)) return; tok = a.token; hint = NLoginTheme.meta.renderwrap(L10n.get(a.token ? "login.token_hint" : "login.legacy_hint"), a.token ? NLoginTheme.muted : NLoginTheme.warn, W); resize(Coord.of(W, hint.sz().y)); }
        public void draw(GOut g) { if (hint != null) g.image(hint.tex(), Coord.z); }
    }
    private static class Progress extends Widget {
        private Text t = null;
        Progress() { super(Coord.of(W, UI.scale(28))); visible = false; }
        void set(String what) { t = NLoginTheme.body.render((what == null) ? "" : what); }
        public void draw(GOut g) { NLoginTheme.drawSpinner(g, Coord.of(UI.scale(10), sz.y / 2), UI.scale(7)); if (t != null) g.image(t.tex(), Coord.of(UI.scale(26), (sz.y - t.sz().y) / 2)); }
    }
}
