package nurgling.conf;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NMasterMinerPropTest {

    @Test
    void getSetSurvivesBooleanFalseLeftover() {
        ArrayList<NMasterMinerProp> fromLeftover = NMasterMinerProp.listFromRaw(false);
        assertTrue(fromLeftover.isEmpty());
        assertTrue(NMasterMinerProp.listFromRaw(null).isEmpty());
        assertTrue(NMasterMinerProp.listFromRaw(true).isEmpty());

        NMasterMinerProp prop = new NMasterMinerProp("alice", "chr1");
        prop.dropThreshold = 40f;
        prop.keepStonesForSupport = 12;
        ArrayList<NMasterMinerProp> stored = NMasterMinerProp.replace(fromLeftover, prop);

        assertInstanceOf(ArrayList.class, stored);
        assertEquals(1, stored.size());
        assertInstanceOf(NMasterMinerProp.class, stored.get(0));
        assertEquals(40f, stored.get(0).dropThreshold);
        assertEquals(12, stored.get(0).keepStonesForSupport);

        NMasterMinerProp loaded = NMasterMinerProp.find(stored, "alice", "chr1");
        assertEquals(40f, loaded.dropThreshold);
        assertEquals(12, loaded.keepStonesForSupport);
        assertEquals(Float.NaN, NMasterMinerProp.find(fromLeftover, "alice", "chr1").dropThreshold);
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
    void emptyListDefaultWritesAListNotBoolean() {
        ArrayList<NMasterMinerProp> def = new ArrayList<>();
        assertTrue(NMasterMinerProp.listFromRaw(def).isEmpty());
        NMasterMinerProp prop = new NMasterMinerProp("alice", "chr1");
        ArrayList<NMasterMinerProp> stored = NMasterMinerProp.replace(def, prop);
        assertInstanceOf(ArrayList.class, stored);
        assertNotSame(Boolean.FALSE, stored);
        assertEquals(1, stored.size());
    }
}

