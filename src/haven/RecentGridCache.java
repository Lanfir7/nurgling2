package haven;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

class RecentGridCache<K, V> {
    private static class Entry<V> {
        final V value;
        final long storedAt;

        Entry(V value, long storedAt) {
            this.value = value;
            this.storedAt = storedAt;
        }
    }

    private final int maxEntries;
    private final long ttlMs;
    private final LongSupplier now;
    private final Consumer<V> disposer;
    private final LinkedHashMap<K, Entry<V>> entries =
            new LinkedHashMap<K, Entry<V>>(16, 0.75f, true);

    RecentGridCache(int maxEntries, long ttlMs, LongSupplier now, Consumer<V> disposer) {
        this.maxEntries = maxEntries;
        this.ttlMs = ttlMs;
        this.now = now;
        this.disposer = disposer;
    }

    synchronized void put(K key, V value) {
        long currentTime = now.getAsLong();
        purgeExpired(currentTime);
        Entry<V> replaced = entries.put(key, new Entry<>(value, currentTime));
        if(replaced != null && replaced.value != value)
            disposer.accept(replaced.value);
        while(entries.size() > maxEntries) {
            Iterator<Map.Entry<K, Entry<V>>> iterator = entries.entrySet().iterator();
            Entry<V> evicted = iterator.next().getValue();
            iterator.remove();
            disposer.accept(evicted.value);
        }
    }

    synchronized V take(K key) {
        long currentTime = now.getAsLong();
        purgeExpired(currentTime);
        Entry<V> entry = entries.remove(key);
        return entry == null ? null : entry.value;
    }

    private void purgeExpired(long currentTime) {
        Iterator<Map.Entry<K, Entry<V>>> iterator = entries.entrySet().iterator();
        while(iterator.hasNext()) {
            Entry<V> entry = iterator.next().getValue();
            if(currentTime - entry.storedAt <= ttlMs)
                continue;
            iterator.remove();
            disposer.accept(entry.value);
        }
    }
}
