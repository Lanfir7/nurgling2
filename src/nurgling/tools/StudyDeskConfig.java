package nurgling.tools;

import haven.Drawable;
import haven.Gob;
import nurgling.NConfig;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared read/write/migration logic for the study desk layout config.
 * <p>
 * Storage is a single flat, global map keyed by study desk gob hash (not by character),
 * since any character can now place items into any study desk:
 * <pre>
 * studyDeskLayout: {
 *   "desks": {
 *     "&lt;gobHash&gt;": { "label": "Desk 1", "layout": { "x,y": {...} } },
 *     ...
 *   },
 *   "legacyBackup": { ...original legacy config... }
 * }
 * </pre>
 * Older configs saved one desk per character name (no "desks" key) and are migrated into this
 * shape the first time they're loaded. The complete legacy value is kept as recovery metadata,
 * including malformed entries that cannot be migrated automatically.
 */
public class StudyDeskConfig {

    private StudyDeskConfig() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final String DESKS_KEY = "desks";
    private static final String LABEL_KEY = "label";
    private static final String LAYOUT_KEY = "layout";
    private static final String GOB_HASH_KEY = "gobHash";
    private static final String OWNER_KEY = "owner";
    private static final String LEGACY_LAYOUTS_KEY = "legacyLayouts";
    private static final String LEGACY_BACKUP_KEY = "legacyBackup";

    /**
     * Load every configured desk, migrating legacy per-character config on the fly.
     * @return map of gobHash -> desk entry ({"label": ..., "layout": {...}})
     */
    @SuppressWarnings("unchecked")
    public static synchronized Map<String, Object> allDesks() {
        // This is deliberately global: NConfig.set writes and propagates the shared desk map
        // globally, so reading a session/profile snapshot here could overwrite newer desks.
        Object existingData = NConfig.getGlobal(NConfig.Key.studyDeskLayout);

        Map<String, Object> raw = rawConfig(existingData);
        if (raw == null) {
            return new HashMap<>();
        }

        Object desksObj = raw.get(DESKS_KEY);
        if (desksObj instanceof Map) {
            return copyMap((Map<String, Object>) desksObj);
        }

        // Legacy shape: Map<charName, {gobHash, layout}> with no top-level "desks" key.
        // Flatten every character's single desk into the new per-hash map.
        Map<String, Object> migrated = new HashMap<>();
        Map<String, String> firstOwnerByHash = new HashMap<>();
        for (Map.Entry<String, Object> entry : new java.util.TreeMap<>(raw).entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            Map<String, Object> charData = (Map<String, Object>) entry.getValue();
            Object hashObj = charData.get(GOB_HASH_KEY);
            Object layoutObj = charData.get(LAYOUT_KEY);
            if (!(hashObj instanceof String) || !(layoutObj instanceof Map)) {
                continue;
            }
            String hash = (String) hashObj;
            if (migrated.containsKey(hash)) {
                // The new schema intentionally has one plan per physical desk. Preserve every
                // colliding legacy plan as recovery metadata while choosing the first character
                // alphabetically as the active plan, making migration deterministic.
                Map<String, Object> existing = (Map<String, Object>) migrated.get(hash);
                Map<String, Object> legacyLayouts = (Map<String, Object>) existing.get(LEGACY_LAYOUTS_KEY);
                if (legacyLayouts == null) {
                    legacyLayouts = new HashMap<>();
                    legacyLayouts.put(firstOwnerByHash.get(hash), existing.get(LAYOUT_KEY));
                    existing.put(LEGACY_LAYOUTS_KEY, legacyLayouts);
                }
                legacyLayouts.put(entry.getKey(), layoutObj);
                continue;
            }
            Map<String, Object> deskEntry = new HashMap<>();
            deskEntry.put(LABEL_KEY, "Desk (" + entry.getKey() + ")");
            deskEntry.put(LAYOUT_KEY, layoutObj);
            migrated.put(hash, deskEntry);
            firstOwnerByHash.put(hash, entry.getKey());
        }

        saveDesks(migrated, raw);
        return migrated;
    }

    /**
     * @return the desk entry for the given hash, or null if none is saved.
     */
    @SuppressWarnings("unchecked")
    public static synchronized Map<String, Object> getDesk(String hash) {
        if (hash == null) {
            return null;
        }
        Object entry = allDesks().get(hash);
        return (entry instanceof Map) ? (Map<String, Object>) entry : null;
    }

    /**
     * Save (or update) a single desk's label/layout, keeping every other desk untouched.
     * @param label new label, or null to keep whatever label is already saved for this desk
     * @param layout the planned layout map (position key -> item data), never null
     * @param owner the saving character's chrid, claiming this desk as theirs (see
     *              {@link #findOwnedDeskHash}), or null to leave ownership as it already was.
     */
    @SuppressWarnings("unchecked")
    public static synchronized void putDesk(String hash, String label, Map<String, Object> layout, String owner) {
        Map<String, Object> desks = allDesks();
        Object existing = desks.get(hash);

        Map<String, Object> deskEntry = existing instanceof Map
                ? copyMap((Map<String, Object>) existing)
                : new HashMap<>();
        String resolvedLabel = orExisting(existing, LABEL_KEY, label);
        deskEntry.put(LABEL_KEY, resolvedLabel != null ? resolvedLabel : defaultLabel(hash));
        deskEntry.put(LAYOUT_KEY, layout);
        String resolvedOwner = orExisting(existing, OWNER_KEY, owner);
        if (resolvedOwner != null) {
            deskEntry.put(OWNER_KEY, resolvedOwner);
        }
        desks.put(hash, deskEntry);

        // A character owns at most one desk at a time - claiming this one clears their tag from
        // any other desk they'd previously claimed, so findOwnedDeskHash() always has exactly one
        // unambiguous answer instead of an arbitrary pick among several (HashMap iteration order).
        if (owner != null) {
            for (Map.Entry<String, Object> entry : desks.entrySet()) {
                if (entry.getKey().equals(hash) || !(entry.getValue() instanceof Map)) {
                    continue;
                }
                Map<String, Object> other = (Map<String, Object>) entry.getValue();
                if (owner.equals(other.get(OWNER_KEY))) {
                    other.remove(OWNER_KEY);
                }
            }
        }

        saveDesks(desks);
    }

    /** {@code key}'s value from {@code existing} if {@code provided} is null, else {@code provided}. */
    @SuppressWarnings("unchecked")
    private static String orExisting(Object existing, String key, String provided) {
        if (provided != null) {
            return provided;
        }
        if (existing instanceof Map) {
            Object v = ((Map<String, Object>) existing).get(key);
            if (v instanceof String) {
                return (String) v;
            }
        }
        return null;
    }

    private static String defaultLabel(String hash) {
        return "Study Desk (" + hash.substring(0, Math.min(8, hash.length())) + ")";
    }

    /** CharacterInfo can lag behind GameUI during startup; both identify the same character. */
    public static String resolveOwnerId(String characterInfoId, String gameUiId) {
        return characterInfoId != null && !characterInfoId.isEmpty() ? characterInfoId : gameUiId;
    }

    /**
     * The hash of the desk the given character last saved a plan for (see {@link #putDesk}), or
     * null if they don't own one. Used by "Fill Study Desk" to target their own desk specifically.
     * At most one desk can carry a given owner at a time (putDesk clears it from any other), so
     * this always has exactly one unambiguous answer.
     */
    @SuppressWarnings("unchecked")
    public static synchronized String findOwnedDeskHash(String chrid) {
        if (chrid == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : allDesks().entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            Object owner = ((Map<String, Object>) entry.getValue()).get(OWNER_KEY);
            if (chrid.equals(owner)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Update just a desk's label, leaving its saved layout untouched (an empty layout is
     * created if this desk has never been saved before). Unlike {@link #putDesk}, this never
     * writes whatever is currently on-screen in the planner, so renaming can't silently
     * persist an in-progress, not-yet-saved layout edit.
     */
    @SuppressWarnings("unchecked")
    public static synchronized void renameDesk(String hash, String label) {
        Map<String, Object> desks = allDesks();

        Map<String, Object> deskEntry;
        Object existing = desks.get(hash);
        if (existing instanceof Map) {
            deskEntry = new HashMap<>((Map<String, Object>) existing);
        } else {
            deskEntry = new HashMap<>();
            deskEntry.put(LAYOUT_KEY, new HashMap<>());
        }
        deskEntry.put(LABEL_KEY, label);

        desks.put(hash, deskEntry);
        saveDesks(desks);
    }

    /**
     * Persist the full flat desk map back to config.
     */
    @SuppressWarnings("unchecked")
    public static synchronized void saveDesks(Map<String, Object> desks) {
        Map<String, Object> current = rawConfig(
                NConfig.getGlobal(NConfig.Key.studyDeskLayout));
        Object legacyBackup = current == null ? null : current.get(LEGACY_BACKUP_KEY);
        saveDesks(desks, legacyBackup instanceof Map
                ? (Map<String, Object>) legacyBackup : null);
    }

    private static void saveDesks(Map<String, Object> desks,
                                  Map<String, Object> legacyBackup) {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put(DESKS_KEY, copyMap(desks));
        if (legacyBackup != null) {
            wrapper.put(LEGACY_BACKUP_KEY, copyMap(legacyBackup));
        }
        NConfig.set(NConfig.Key.studyDeskLayout, wrapper);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rawConfig(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        if (value instanceof String && !((String) value).isEmpty()) {
            return new JSONObject((String) value).toMap();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyMap(Map<String, Object> source) {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            copy.put(entry.getKey(), value instanceof Map
                    ? copyMap((Map<String, Object>) value)
                    : value);
        }
        return copy;
    }

    /**
     * The container cap name ("Study Desk" / "Fine Study Desk" / "Grand Study Desk") for a
     * study desk gob, or null if the gob isn't a recognized study desk variant.
     */
    public static String capFor(Gob gob) {
        Drawable drawable = gob.getattr(Drawable.class);
        if (drawable == null || drawable.getres() == null) {
            return null;
        }
        String resName = drawable.getres().name;
        if ("gfx/terobjs/studydesk-big".equals(resName)) {
            return "Fine Study Desk";
        } else if ("gfx/terobjs/grandstudydesk".equals(resName)) {
            return "Grand Study Desk";
        } else if ("gfx/terobjs/studydesk".equals(resName)) {
            return "Study Desk";
        }
        return null;
    }
}
