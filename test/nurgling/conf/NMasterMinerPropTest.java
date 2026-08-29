package nurgling.conf;

import nurgling.NConfig;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NMasterMinerPropTest {

    @Test
    void listFromRawTreatsBooleanFalseAsEmptyList() {
        ArrayList<NMasterMinerProp> fromFalse = NMasterMinerProp.listFromRaw(false);
        assertNotNull(fromFalse);
        assertTrue(fromFalse.isEmpty());
        assertTrue(NMasterMinerProp.listFromRaw(null).isEmpty());
        assertTrue(NMasterMinerProp.listFromRaw(true).isEmpty());
    }

    @Test
    void listFromRawRebuildsHashMapEntries() {
        HashMap<String, Object> raw = new HashMap<>();
        raw.put("username", "alice");
        raw.put("chrid", "chr1");
        raw.put("dropThreshold", 40);
        raw.put("keepStonesForSupport", 12);
        raw.put("wndX", 80);
        raw.put("wndY", 120);
        ArrayList<HashMap<String, Object>> leftover = new ArrayList<>();
        leftover.add(raw);

        ArrayList<NMasterMinerProp> props = NMasterMinerProp.listFromRaw(leftover);
        assertEquals(1, props.size());
        assertEquals(40f, props.get(0).dropThreshold);
        assertEquals(12, props.get(0).keepStonesForSupport);
        assertEquals(80, props.get(0).wndX);
        assertEquals(120, props.get(0).wndY);
    }

    @Test
    void getSetSurvivesBooleanFalseLeftover() {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = new NConfig();
            NConfig.set(NConfig.Key.masterminerprop, false);

            NMasterMinerProp prop = new NMasterMinerProp("alice", "chr1");
            prop.dropThreshold = 40f;
            prop.keepStonesForSupport = 12;
            assertDoesNotThrow(() -> NMasterMinerProp.set(prop));

            Object stored = NConfig.getGlobal(NConfig.Key.masterminerprop);
            assertInstanceOf(ArrayList.class, stored);
            @SuppressWarnings("unchecked")
            ArrayList<?> list = (ArrayList<?>) stored;
            assertEquals(1, list.size());
            assertInstanceOf(NMasterMinerProp.class, list.get(0));
            NMasterMinerProp saved = (NMasterMinerProp) list.get(0);
            assertEquals(40f, saved.dropThreshold);
            assertEquals(12, saved.keepStonesForSupport);

            NMasterMinerProp loaded = NMasterMinerProp.get("alice", "chr1");
            assertNotNull(loaded);
            assertEquals(40f, loaded.dropThreshold);
            assertEquals(12, loaded.keepStonesForSupport);
        } finally {
            NConfig.current = previous;
        }
    }

    @Test
    void jsonRoundTripsThresholdsKeepStonesAndWindowPos() {
        NMasterMinerProp original = new NMasterMinerProp("bob", "chr2");
        original.dropThreshold = 33.5f;
        original.keepStonesForSupport = 7;
        original.wndX = 140;
        original.wndY = 220;

        JSONObject json = original.toJson();
        @SuppressWarnings("unchecked")
        HashMap<String, Object> values = new HashMap<String, Object>(json.toMap());
        NMasterMinerProp restored = new NMasterMinerProp(values);

        assertEquals(33.5f, restored.dropThreshold);
        assertEquals(7, restored.keepStonesForSupport);
        assertEquals(140, restored.wndX);
        assertEquals(220, restored.wndY);
        assertTrue(restored.hasWindowPos());
        assertFalse(new NMasterMinerProp("x", "y").hasWindowPos());
    }

    @Test
    void constructorDefaultIsEmptyListNotBoolean() {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = new NConfig();
            Object def = NConfig.getGlobal(NConfig.Key.masterminerprop);
            assertInstanceOf(ArrayList.class, def);
            assertTrue(((ArrayList<?>) def).isEmpty());
        } finally {
            NConfig.current = previous;
        }
    }
}
