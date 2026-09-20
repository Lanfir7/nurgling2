package nurgling.conf;

import nurgling.NConfig;
import nurgling.NUI;
import nurgling.guarding.GuardEntry;
import nurgling.guarding.GuardingProfile;
import nurgling.routes.ForagerAction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NForagerPropMigrationTest {

    @Test
    void legacyPresetActionsBecomeNamedActionsProfile() {
        HashMap<String, Object> values = baseValues();
        HashMap<String, Object> preset = new HashMap<>();
        preset.put("pathFile", "routes/old.json");
        ArrayList<HashMap<String, Object>> actions = new ArrayList<>();
        HashMap<String, Object> action = new HashMap<>();
        action.put("targetObjectPattern", "gfx/terobjs/herbs/chantrelle");
        action.put("actionType", "PICK");
        action.put("maintainQuantity", 20);
        actions.add(action);
        preset.put("actions", actions);
        preset.put("onPlayerAction", "logout");
        preset.put("onAnimalAction", "travel hearth");
        preset.put("ignoreBats", false);
        preset.put("waterMode", true);
        HashMap<String, HashMap<String, Object>> presets = new HashMap<>();
        presets.put("Forest", preset);
        values.put("currentPreset", "Forest");
        values.put("presets", presets);

        NForagerProp prop = new NForagerProp(values);

        assertTrue(prop.actionsProfiles.containsKey("Forest"));
        assertEquals(1, prop.actionsProfiles.get("Forest").size());
        ForagerAction migrated = prop.actionsProfiles.get("Forest").get(0);
        assertEquals("gfx/terobjs/herbs/chantrelle", migrated.targetObjectPattern);
        assertEquals(20, migrated.maintainQuantity);
        assertEquals("Forest", prop.presets.get("Forest").actionsProfileName);

        GuardingProfile gp = prop.guardingProfiles.get("Forest");
        assertTrue(gp.waterMode);
        assertFalse(gp.ignoreBats);
        assertEquals("Forest", prop.presets.get("Forest").guardingProfileName);
        assertOutcome(gp, "unknown_player", "logout", true);
        assertOutcome(gp, "dangerous_animal", "travel hearth", true);
    }

    @Test
    void emptyActionsProfilesKeyDoesNotResurrectLegacyActions() {
        HashMap<String, Object> values = baseValues();
        HashMap<String, Object> preset = new HashMap<>();
        ArrayList<HashMap<String, Object>> actions = new ArrayList<>();
        HashMap<String, Object> action = new HashMap<>();
        action.put("targetObjectPattern", "gfx/terobjs/herbs/chantrelle");
        action.put("actionType", "PICK");
        actions.add(action);
        preset.put("actions", actions);
        HashMap<String, HashMap<String, Object>> presets = new HashMap<>();
        presets.put("Forest", preset);
        values.put("presets", presets);
        values.put("actionsProfiles", new HashMap<String, ArrayList<HashMap<String, Object>>>());

        NForagerProp prop = new NForagerProp(values);

        assertTrue(prop.actionsProfiles.containsKey("Default"));
        assertTrue(prop.actionsProfiles.get(prop.currentActionsProfile).isEmpty());
    }

    @Test
    void emptyGuardingProfilesKeyDoesNotResurrectLegacyGuards() {
        HashMap<String, Object> values = baseValues();
        HashMap<String, Object> preset = new HashMap<>();
        preset.put("onPlayerAction", "logout");
        preset.put("onAnimalAction", "travel hearth");
        preset.put("ignoreBats", false);
        preset.put("waterMode", true);
        HashMap<String, HashMap<String, Object>> presets = new HashMap<>();
        presets.put("Forest", preset);
        values.put("presets", presets);
        values.put("guardingProfiles", new HashMap<String, HashMap<String, Object>>());

        NForagerProp prop = new NForagerProp(values);

        assertFalse(prop.guardingProfiles.containsKey("Forest"));
        assertTrue(prop.guardingProfiles.containsKey("Default"));
        GuardingProfile seeded = prop.guardingProfiles.get(prop.currentGuardingProfile);
        assertTrue(seeded.ignoreBats);
        assertFalse(seeded.waterMode);
    }

    @Test
    void savingCaseVariantsCollapsesEveryOlderEntry() {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = new NConfig();
            ArrayList<NForagerProp> saved = new ArrayList<>();
            saved.add(new NForagerProp("Alice", "Character-1"));
            saved.add(new NForagerProp("ALICE", "CHARACTER-1"));
            NForagerProp unrelated = new NForagerProp("Bob", "Character-1");
            saved.add(unrelated);
            NConfig.set(NConfig.Key.foragerprop, saved);

            NForagerProp latest = new NForagerProp("alice", "character-1");
            NForagerProp.set(latest);

            @SuppressWarnings("unchecked")
            ArrayList<NForagerProp> stored = (ArrayList<NForagerProp>) NConfig.get(NConfig.Key.foragerprop);
            assertEquals(2, stored.size());
            assertSame(unrelated, stored.get(0));
            assertSame(latest, stored.get(1));
        } finally {
            NConfig.current = previous;
        }
    }

    @Test
    void savingNullIdentityDoesNotThrow() {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = new NConfig();
            NForagerProp.set(new NForagerProp(null, null));
            NForagerProp.set(new NForagerProp(null, null));

            @SuppressWarnings("unchecked")
            ArrayList<NForagerProp> stored = (ArrayList<NForagerProp>) NConfig.get(NConfig.Key.foragerprop);
            assertEquals(2, stored.size());
        } finally {
            NConfig.current = previous;
        }
    }

    @Test
    void loadingIsSerializedWithSaving() throws NoSuchMethodException {
        int modifiers = NForagerProp.class
                .getDeclaredMethod("get", NUI.NSessInfo.class)
                .getModifiers();

        assertTrue(Modifier.isStatic(modifiers));
        assertTrue(Modifier.isSynchronized(modifiers));
    }

    private static void assertOutcome(GuardingProfile gp, String guardId, String outcomeId, boolean enabled) {
        for (GuardEntry entry : gp.inflightGuards) {
            if (guardId.equals(entry.guardId)) {
                assertEquals(enabled, entry.enabled);
                assertEquals(outcomeId, entry.outcomeId);
                return;
            }
        }
        throw new AssertionError("missing guard " + guardId);
    }

    private static HashMap<String, Object> baseValues() {
        HashMap<String, Object> values = new HashMap<>();
        values.put("username", "tester");
        values.put("chrid", "chr1");
        return values;
    }
}
