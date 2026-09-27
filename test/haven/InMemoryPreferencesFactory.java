package haven;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/** Prevents test JVMs from reading or changing the player's OS preferences. */
public final class InMemoryPreferencesFactory implements PreferencesFactory {
    private static final MemoryNode USER = new MemoryNode(null, "");
    private static final MemoryNode SYSTEM = new MemoryNode(null, "");

    @Override
    public Preferences userRoot() {
        return USER;
    }

    @Override
    public Preferences systemRoot() {
        return SYSTEM;
    }

    private static final class MemoryNode extends AbstractPreferences {
        private final MemoryNode parentNode;
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, MemoryNode> children = new HashMap<>();

        private MemoryNode(MemoryNode parent, String name) {
            super(parent, name);
            this.parentNode = parent;
        }

        @Override protected void putSpi(String key, String value) { values.put(key, value); }
        @Override protected String getSpi(String key) { return values.get(key); }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected void removeNodeSpi() throws BackingStoreException {
            values.clear();
            children.clear();
            if (parentNode != null)
                parentNode.children.remove(name());
        }
        @Override protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
        @Override protected String[] childrenNamesSpi() { return children.keySet().toArray(new String[0]); }
        @Override protected AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name, key -> new MemoryNode(this, key));
        }
        @Override protected void syncSpi() throws BackingStoreException {}
        @Override protected void flushSpi() throws BackingStoreException {}
    }
}
