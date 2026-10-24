package nurgling.conf;

import nurgling.NConfig;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NDrinkerPropTest {
    @Test void roundTripAndCharacterReplacementKeepOtherCharacters() {
        NDrinkerProp alice = new NDrinkerProp("account", "alice");
        alice.sips.put("Wine", 3);
        alice.cups.put("Wine", "Mug");
        NDrinkerProp bob = new NDrinkerProp("account", "bob");
        bob.sips.put("Beer", 2);
        NDrinkerProp newerAlice = new NDrinkerProp("account", "alice");
        newerAlice.sips.put("Wine", 5);
        ArrayList<NDrinkerProp> updated = NDrinkerProp.replace(List.of(alice, bob), newerAlice);
        assertEquals(2, updated.size());
        assertTrue(updated.contains(bob));
        assertFalse(updated.contains(alice));
        assertEquals(2, bob.sips.get("Beer"));
        assertEquals(3, alice.sips.get("Wine"));
        JSONObject json = alice.toJson();
        NDrinkerProp restored = new NDrinkerProp(new HashMap<>(json.toMap()));
        assertEquals(3, restored.sips.get("Wine"));
        assertEquals("Mug", restored.cups.get("Wine"));
        assertEquals("NDrinkerProp", restored.toJson().getString("type"));
    }

    @Test void malformedSavedValuesAreIgnoredWithoutAffectingOtherProps() {
        HashMap<String, Object> raw = new HashMap<>();
        raw.put("username", "account");
        raw.put("chrid", "alice");
        raw.put("sips", java.util.Map.of("Wine", "five", "Beer", 2));
        raw.put("cups", java.util.Map.of("Beer", 4, "Wine", "Mug"));
        NDrinkerProp restored = new NDrinkerProp(raw);
        assertEquals(java.util.Map.of("Beer", 2), restored.sips);
        assertEquals(java.util.Map.of("Wine", "Mug"), restored.cups);
    }

    @Test void settingAnotherCharacterDoesNotReplaceTheFirst() {
        NConfig prior = NConfig.current;
        try {
            NConfig.current = new NConfig();
            NDrinkerProp alice = new NDrinkerProp("account", "alice");
            alice.sips.put("Wine", 2);
            NDrinkerProp bob = new NDrinkerProp("account", "bob");
            bob.sips.put("Beer", 4);
            NDrinkerProp.set(alice);
            NDrinkerProp.set(bob);
            Object stored = NConfig.get(NConfig.Key.drinkerprop);
            assertTrue(stored instanceof List<?>);
            assertEquals(2, ((List<?>) stored).size());
            assertEquals(2, alice.sips.get("Wine"));
            assertEquals(4, bob.sips.get("Beer"));
        } finally {
            NConfig.current = prior;
        }
    }
}
