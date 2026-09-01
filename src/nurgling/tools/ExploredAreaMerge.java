package nurgling.tools;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * OR-merge of explored-area grid masks (disk ∪ memory snapshot).
 */
public final class ExploredAreaMerge {
    private ExploredAreaMerge() {}

    public static <K> Map<K, boolean[]> merge(Map<K, boolean[]> disk, Map<K, boolean[]> memory, int maskSize) {
        Map<K, boolean[]> merged = new HashMap<>();
        if (disk != null) {
            for (Map.Entry<K, boolean[]> entry : disk.entrySet()) {
                boolean[] mask = entry.getValue();
                merged.put(entry.getKey(), mask == null ? new boolean[maskSize] : Arrays.copyOf(mask, maskSize));
            }
        }
        if (memory != null) {
            for (Map.Entry<K, boolean[]> entry : memory.entrySet()) {
                boolean[] mergedMask = merged.computeIfAbsent(entry.getKey(), ignored -> new boolean[maskSize]);
                boolean[] memoryMask = entry.getValue();
                if (memoryMask == null) {
                    continue;
                }
                int n = Math.min(maskSize, memoryMask.length);
                for (int i = 0; i < n; i++) {
                    mergedMask[i] |= memoryMask[i];
                }
            }
        }
        return merged;
    }
}
