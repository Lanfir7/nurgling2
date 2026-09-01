package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExploredAreaMergeTest {
    @Test
    void mergeOrsOverlappingMasksAndKeepsUniques() {
        Map<String, boolean[]> disk = new HashMap<>();
        Map<String, boolean[]> memory = new HashMap<>();
        disk.put("shared", bits(true, false, false, true));
        memory.put("shared", bits(false, true, false, false));
        memory.put("only-mem", bits(true, false, false, false));
        disk.put("only-disk", bits(false, true, false, false));

        Map<String, boolean[]> merged = ExploredAreaMerge.merge(disk, memory, 4);

        assertArrayEquals(bits(true, true, false, true), merged.get("shared"));
        assertArrayEquals(bits(true, false, false, false), merged.get("only-mem"));
        assertArrayEquals(bits(false, true, false, false), merged.get("only-disk"));
    }

    @Test
    void mergeCopiesInputsSoCallersCanKeepMutatingMemory() {
        boolean[] diskMask = bits(true, false, false, false);
        boolean[] memoryMask = bits(false, true, false, false);
        Map<String, boolean[]> disk = new HashMap<>();
        Map<String, boolean[]> memory = new HashMap<>();
        disk.put("g", diskMask);
        memory.put("g", memoryMask);

        Map<String, boolean[]> merged = ExploredAreaMerge.merge(disk, memory, 4);
        memoryMask[2] = true;
        diskMask[3] = true;

        assertTrue(merged.get("g")[0]);
        assertTrue(merged.get("g")[1]);
        assertFalse(merged.get("g")[2]);
        assertFalse(merged.get("g")[3]);
    }

    private static boolean[] bits(boolean... values) {
        return values;
    }
}
