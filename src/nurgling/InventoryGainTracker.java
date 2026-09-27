package nurgling;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compares the player's carried items by widget identity and resource, cancelling moves within the carry inventory. */
public final class InventoryGainTracker<T> {
    public static final class Item<T> {
        public final T identity;
        public final String resource;
        public final int amount;

        public Item(T identity, String resource, int amount) {
            this.identity = identity;
            this.resource = resource;
            this.amount = Math.max(0, amount);
        }
    }

    public static final class Gain<T> {
        public final T example;
        public final String resource;
        public final int amount;

        private Gain(T example, String resource, int amount) {
            this.example = example;
            this.resource = resource;
            this.amount = amount;
        }
    }

    private static final class Seen {
        final String resource;
        final int amount;
        final boolean pending;

        Seen(String resource, int amount, boolean pending) {
            this.resource = resource;
            this.amount = amount;
            this.pending = pending;
        }
    }

    private IdentityHashMap<T, Seen> previous = new IdentityHashMap<>();
    private final Map<String, Delayed<T>> delayed = new LinkedHashMap<>();
    private boolean primed;

    private static final class Delayed<T> {
        int amount;
        double idle;
        double total;
        T example;
    }

    public void reset() {
        previous.clear();
        delayed.clear();
        primed = false;
    }

    public List<Gain<T>> observe(List<Item<T>> items, double dt) {
        IdentityHashMap<T, Seen> current = new IdentityHashMap<>();
        Map<String, Integer> delta = new LinkedHashMap<>();
        Map<String, T> examples = new LinkedHashMap<>();
        for (Item<T> item : items) {
            if (item.identity == null || item.amount == 0)
                continue;
            Seen old = previous.get(item.identity);
            boolean pending = item.resource == null && primed && (old == null || old.pending);
            current.put(item.identity, new Seen(item.resource, item.amount, pending));
            if (!primed || item.resource == null)
                continue;
            int change;
            if (old == null)
                change = item.amount;
            else if (old.resource == null)
                change = old.pending ? item.amount : Math.max(0, item.amount - old.amount);
            else if (old.resource.equals(item.resource))
                change = item.amount - old.amount;
            else
                change = item.amount;
            if (change != 0) {
                delta.merge(item.resource, change, Integer::sum);
                examples.put(item.resource, item.identity);
            }
        }
        if (primed) {
            for (Map.Entry<T, Seen> entry : previous.entrySet()) {
                Seen old = entry.getValue();
                Seen now = current.get(entry.getKey());
                if (old.resource != null && (now == null || !old.resource.equals(now.resource)))
                    delta.merge(old.resource, -old.amount, Integer::sum);
            }
        }
        previous = current;
        primed = true;
        for (Delayed<T> change : delayed.values()) {
            change.idle += Math.max(0, dt);
            change.total += Math.max(0, dt);
        }
        for (Map.Entry<String, Integer> entry : delta.entrySet()) {
            Delayed<T> change = delayed.computeIfAbsent(entry.getKey(), key -> new Delayed<>());
            change.amount += entry.getValue();
            change.idle = 0;
            if (examples.containsKey(entry.getKey()))
                change.example = examples.get(entry.getKey());
        }
        List<Gain<T>> gains = new ArrayList<>();
        delayed.entrySet().removeIf(entry -> {
            Delayed<T> change = entry.getValue();
            if (change.idle < 0.35 && change.total < 0.8)
                return false;
            if (change.amount > 0 && change.example != null)
                gains.add(new Gain<>(change.example, entry.getKey(), change.amount));
            return true;
        });
        return gains;
    }
}
