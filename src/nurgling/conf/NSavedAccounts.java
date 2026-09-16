package nurgling.conf;

import haven.Bootstrap;
import haven.Utils;
import nurgling.NConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** Compatibility view over Haven tokens and Nurgling's existing password/token list. */
public final class NSavedAccounts {
    private NSavedAccounts() {
    }

    public static final class Account {
        public final String name;
        public final boolean token;
        public final String pass;
        public final long used;

        public Account(String name, boolean token, String pass, long used) {
            this.name = name;
            this.token = token;
            this.pass = pass;
            this.used = used;
        }
    }

    /** Copies old Nurgling tokens into Haven's store without deleting the legacy credentials. */
    public static void migrate(String confname) {
        for (NLoginData d : legacy()) {
            if ((d.name != null) && !d.name.isEmpty() && d.isTokenUsed &&
                    (d.token != null) && (d.token.length == 32) &&
                    (Bootstrap.gettoken(d.name, confname) == null))
                Bootstrap.settoken(d.name, confname, Arrays.copyOf(d.token, d.token.length));
        }
    }

    public static List<Account> list(String confname) {
        List<String> tokenNames = Utils.getprefsl("saved-tokens@" + confname, new String[]{});
        List<Account> ret = collect(legacy(), tokenNames,
                name -> Bootstrap.gettoken(name, confname), NCharTags::used);
        ret.sort((a, b) -> Integer.compare(NCharTags.accindex(a.name), NCharTags.accindex(b.name)));
        return (ret);
    }

    static List<Account> collect(List<NLoginData> legacy, List<String> tokenNames,
                                 Function<String, byte[]> tokenLookup,
                                 ToLongFunction<String> usedLookup) {
        List<Account> ret = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String name : tokenNames) {
            if ((name != null) && !name.isEmpty() && (tokenLookup.apply(name) != null) && seen.add(name))
                ret.add(new Account(name, true, null, usedLookup.applyAsLong(name)));
        }
        for (NLoginData d : legacy) {
            if ((d.name == null) || d.name.isEmpty() || !seen.add(d.name))
                continue;
            if (d.isTokenUsed && (d.token != null) && (d.token.length == 32))
                ret.add(new Account(d.name, true, null, usedLookup.applyAsLong(d.name)));
            else if ((d.pass != null) && !d.pass.isEmpty())
                ret.add(new Account(d.name, false, d.pass, usedLookup.applyAsLong(d.name)));
        }
        return (ret);
    }

    public static void savePassword(String name, String pass) {
        ArrayList<NLoginData> legacy = legacy();
        putPassword(legacy, name, pass);
        NConfig.set(NConfig.Key.credentials, legacy);
    }

    public static void saveToken(String confname, String name, byte[] token) {
        if ((token == null) || (token.length != 32))
            return;
        byte[] copy = Arrays.copyOf(token, token.length);
        Bootstrap.settoken(name, confname, copy);
        ArrayList<NLoginData> legacy = legacy();
        putToken(legacy, name, copy);
        NConfig.set(NConfig.Key.credentials, legacy);
    }

    static void putPassword(List<NLoginData> legacy, String name, String pass) {
        if ((name == null) || name.isEmpty() || (pass == null) || pass.isEmpty())
            return;
        NLoginData found = find(legacy, name);
        if (found == null) {
            legacy.add(new NLoginData(name, pass));
        } else {
            found.pass = pass;
            found.token = null;
            found.isTokenUsed = false;
        }
    }

    static void putToken(List<NLoginData> legacy, String name, byte[] token) {
        if ((name == null) || name.isEmpty() || (token == null) || (token.length != 32))
            return;
        NLoginData found = find(legacy, name);
        if (found == null) {
            legacy.add(new NLoginData(name, Arrays.copyOf(token, token.length)));
        } else {
            found.pass = "";
            found.token = Arrays.copyOf(token, token.length);
            found.isTokenUsed = true;
        }
    }

    private static NLoginData find(List<NLoginData> legacy, String name) {
        for (NLoginData d : legacy) {
            if (name.equals(d.name))
                return (d);
        }
        return (null);
    }

    public static void reorder(List<String> names) {
        NCharTags.setAccOrder(names);
    }

    public static void remove(String confname, String name) {
        Bootstrap.settoken(name, confname, null);
        ArrayList<NLoginData> legacy = legacy();
        if (legacy.removeIf(d -> name.equals(d.name)))
            NConfig.set(NConfig.Key.credentials, legacy);
        NCharTags.forgetUsed(name);
    }

    /** Drops a rejected token while retaining an older password fallback, if one exists. */
    public static void invalidateToken(String confname, String name) {
        Bootstrap.settoken(name, confname, null);
        ArrayList<NLoginData> legacy = legacy();
        if (invalidateToken(legacy, name))
            NConfig.set(NConfig.Key.credentials, legacy);
    }

    static boolean invalidateToken(List<NLoginData> legacy, String name) {
        return legacy.removeIf(d -> name.equals(d.name) && d.isTokenUsed);
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<NLoginData> legacy() {
        Object o = NConfig.get(NConfig.Key.credentials);
        return ((o instanceof ArrayList) ? (ArrayList<NLoginData>) o : new ArrayList<>());
    }
}
