package nurgling.conf;

import haven.Coord;
import haven.MCache;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NMiningOverlayMemoryTest {

    @Test
    void staleCachedGridCannotRestoreSafeMiningMarks() {
        MCache map = new MCache(null);
        MCache.Grid stale = map.new Grid(Coord.of(5, 7));
        stale.id = 42L;
        map.grids.put(stale.gc, stale);
        Coord tile = stale.ul.add(3, 4);

        assertNull(NMiningOverlayMemory.ofWorld(map, tile));
        assertNull(NMiningOverlayMemory.toWorld(map,
                new NMiningOverlayMemory.TileRef(stale.id, 3, 4)));
    }

    @Test
    void jsonRoundTripsNumbersAndGreens() {
        NMiningOverlayMemory original = new NMiningOverlayMemory("alice", "chr1");
        NMiningOverlayMemory.TileRef n1 = new NMiningOverlayMemory.TileRef(0x1a2b3c4d5e6f7081L, 10, 20);
        NMiningOverlayMemory.TileRef n2 = new NMiningOverlayMemory.TileRef(99L, 0, 1);
        NMiningOverlayMemory.TileRef g1 = new NMiningOverlayMemory.TileRef(0x1a2b3c4d5e6f7081L, 11, 20);
        assertTrue(original.putNumber(n1, 1));
        assertTrue(original.putNumber(n2, 2));
        assertTrue(original.putGreen(g1));

        JSONObject json = original.toJson();
        @SuppressWarnings("unchecked")
        HashMap<String, Object> values = new HashMap<String, Object>(json.toMap());
        NMiningOverlayMemory restored = new NMiningOverlayMemory(values);

        assertEquals(1, restored.getNumber(n1));
        assertEquals(2, restored.getNumber(n2));
        assertTrue(restored.isGreen(g1));
        assertEquals(2, restored.numberCount());
        assertEquals(1, restored.greenCount());
        assertEquals("alice", restored.username());
        assertEquals("chr1", restored.chrid());
    }

    @Test
    void liveNumberOverridesStaleMemory() {
        NMiningOverlayMemory mem = new NMiningOverlayMemory("alice", "chr1");
        NMiningOverlayMemory.TileRef tile = new NMiningOverlayMemory.TileRef(7L, 3, 4);
        assertTrue(mem.putNumber(tile, 1));
        assertFalse(mem.putNumber(tile, 1));
        assertTrue(mem.putNumber(tile, 3));
        assertEquals(3, mem.getNumber(tile));
        assertEquals(1, mem.numberCount());
    }

    @Test
    void capEvictsOldestNumberAndGreen() {
        NMiningOverlayMemory mem = new NMiningOverlayMemory("alice", "chr1");
        NMiningOverlayMemory.TileRef firstNumber = new NMiningOverlayMemory.TileRef(1L, 0, 0);
        mem.putNumber(firstNumber, 1);
        for (int i = 1; i < NMiningOverlayMemory.MAX_NUMBERS; i++) {
            mem.putNumber(new NMiningOverlayMemory.TileRef(1L, i, 0), 1);
        }
        assertEquals(NMiningOverlayMemory.MAX_NUMBERS, mem.numberCount());
        NMiningOverlayMemory.TileRef overflowNumber = new NMiningOverlayMemory.TileRef(1L, NMiningOverlayMemory.MAX_NUMBERS, 0);
        mem.putNumber(overflowNumber, 4);
        assertNull(mem.getNumber(firstNumber));
        assertEquals(4, mem.getNumber(overflowNumber));
        assertEquals(NMiningOverlayMemory.MAX_NUMBERS, mem.numberCount());

        NMiningOverlayMemory.TileRef firstGreen = new NMiningOverlayMemory.TileRef(2L, 0, 0);
        mem.putGreen(firstGreen);
        for (int i = 1; i < NMiningOverlayMemory.MAX_GREENS; i++) {
            mem.putGreen(new NMiningOverlayMemory.TileRef(2L, i, 0));
        }
        assertEquals(NMiningOverlayMemory.MAX_GREENS, mem.greenCount());
        NMiningOverlayMemory.TileRef overflowGreen = new NMiningOverlayMemory.TileRef(2L, NMiningOverlayMemory.MAX_GREENS, 0);
        mem.putGreen(overflowGreen);
        assertFalse(mem.isGreen(firstGreen));
        assertTrue(mem.isGreen(overflowGreen));
        assertEquals(NMiningOverlayMemory.MAX_GREENS, mem.greenCount());
    }

    @Test
    void differentCharactersDoNotShareMaps() {
        NMiningOverlayMemory alice = new NMiningOverlayMemory("alice", "chr1");
        NMiningOverlayMemory.TileRef tile = new NMiningOverlayMemory.TileRef(5L, 1, 2);
        alice.putNumber(tile, 2);
        alice.putGreen(new NMiningOverlayMemory.TileRef(5L, 2, 2));

        ArrayList<NMiningOverlayMemory> stored = NMiningOverlayMemory.replace(new ArrayList<>(), alice);
        NMiningOverlayMemory bob = NMiningOverlayMemory.find(stored, "bob", "chr2");
        assertNotSame(alice, bob);
        assertNull(bob.getNumber(tile));
        assertEquals(0, bob.numberCount());
        assertEquals(0, bob.greenCount());
        assertEquals(2, NMiningOverlayMemory.find(stored, "alice", "chr1").getNumber(tile));
    }

    @Test
    void listFromRawRebuildsHashMapEntries() {
        HashMap<String, Object> num = new HashMap<>();
        num.put("g", "1234567890123456789");
        num.put("x", 4);
        num.put("y", 8);
        num.put("v", 2);
        HashMap<String, Object> green = new HashMap<>();
        green.put("g", 42);
        green.put("x", 5);
        green.put("y", 9);

        HashMap<String, Object> raw = new HashMap<>();
        raw.put("username", "alice");
        raw.put("chrid", "chr1");
        ArrayList<HashMap<String, Object>> numbers = new ArrayList<>();
        numbers.add(num);
        ArrayList<HashMap<String, Object>> greens = new ArrayList<>();
        greens.add(green);
        raw.put("numbers", numbers);
        raw.put("greens", greens);

        ArrayList<HashMap<String, Object>> leftover = new ArrayList<>();
        leftover.add(raw);
        ArrayList<NMiningOverlayMemory> loaded = NMiningOverlayMemory.listFromRaw(leftover);
        assertEquals(1, loaded.size());
        NMiningOverlayMemory.TileRef n = new NMiningOverlayMemory.TileRef(1234567890123456789L, 4, 8);
        NMiningOverlayMemory.TileRef g = new NMiningOverlayMemory.TileRef(42L, 5, 9);
        assertEquals(2, loaded.get(0).getNumber(n));
        assertTrue(loaded.get(0).isGreen(g));
    }

    @Test
    void numbersMapAndGreenSetSurviveRoundTrip() {
        NMiningOverlayMemory original = new NMiningOverlayMemory("bob", "chr2");
        Map<NMiningOverlayMemory.TileRef, Integer> expectedNumbers = new HashMap<>();
        expectedNumbers.put(new NMiningOverlayMemory.TileRef(11L, 1, 1), 1);
        expectedNumbers.put(new NMiningOverlayMemory.TileRef(11L, 2, 3), 5);
        for (Map.Entry<NMiningOverlayMemory.TileRef, Integer> e : expectedNumbers.entrySet()) {
            original.putNumber(e.getKey(), e.getValue());
        }
        original.putGreen(new NMiningOverlayMemory.TileRef(11L, 3, 3));
        original.putGreen(new NMiningOverlayMemory.TileRef(12L, 0, 0));

        JSONObject json = original.toJson();
        @SuppressWarnings("unchecked")
        NMiningOverlayMemory restored = new NMiningOverlayMemory(new HashMap<String, Object>(json.toMap()));
        assertEquals(expectedNumbers, restored.numbers());
        assertEquals(original.greens(), restored.greens());
    }

    @Test
    void nconfigKeyExistsForDiskStore() {
        assertEquals(nurgling.NConfig.Key.miningoverlaymemory, nurgling.NConfig.Key.valueOf("miningoverlaymemory"));
    }
}
