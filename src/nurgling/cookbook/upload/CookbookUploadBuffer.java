package nurgling.cookbook.upload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CookbookUploadBuffer {
    private final int capacity;
    private final LinkedHashMap<String, CookbookUploadRecord> records = new LinkedHashMap<>();

    CookbookUploadBuffer(int capacity) {
        if (capacity < 1)
            throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    synchronized void offer(CookbookUploadRecord record) {
        records.remove(record.key());
        records.put(record.key(), record);
        trim();
    }

    synchronized List<CookbookUploadRecord> drain(int limit) {
        List<CookbookUploadRecord> batch = new ArrayList<>();
        java.util.Iterator<Map.Entry<String, CookbookUploadRecord>> iterator = records.entrySet().iterator();
        while (iterator.hasNext() && batch.size() < limit) {
            batch.add(iterator.next().getValue());
            iterator.remove();
        }
        return batch;
    }

    synchronized void restore(List<CookbookUploadRecord> failed) {
        LinkedHashMap<String, CookbookUploadRecord> restored = new LinkedHashMap<>();
        for (CookbookUploadRecord record : failed) {
            if (!records.containsKey(record.key()))
                restored.put(record.key(), record);
        }
        restored.putAll(records);
        records.clear();
        records.putAll(restored);
        trim();
    }

    synchronized void clear() {
        records.clear();
    }

    private void trim() {
        while (records.size() > capacity) {
            java.util.Iterator<String> iterator = records.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }
}
