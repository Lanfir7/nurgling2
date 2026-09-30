package haven.render;

import haven.Loading;
import java.util.*;
import java.util.function.*;

/**
 * Scene-thread slot registry. Pending work is retried fairly without losing
 * removed slots or discarding a drawable before its replacement is ready.
 * The owning draw list provides serialization; this never calls from a worker.
 */
public final class DeferredRenderSlots<K, V> {
    private final Map<K, V> slots = new IdentityHashMap<>();
    private final Set<K> pending = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Deque<K> pendingOrder = new ArrayDeque<>();
    private final Function<K, V> build;
    private final BiConsumer<V, V> install;

    public DeferredRenderSlots(Function<K, V> build, BiConsumer<V, V> install) {
        this.build = build;
        this.install = install;
    }

    public boolean contains(K key) {return slots.containsKey(key);}
    public V get(K key) {return slots.get(key);}

    private void enqueue(K key) {
        if(pending.add(key))
            pendingOrder.addLast(key);
    }

    private void dequeue(K key) {
        if(pending.remove(key))
            pendingOrder.removeIf(entry -> entry == key);
    }

    public void add(K key, boolean defer) {
        if(slots.containsKey(key))
            throw new IllegalStateException("duplicate render slot");
        slots.put(key, null);
        if(defer) {
            enqueue(key);
        } else {
            try {
                replace(key);
            } catch(Loading loading) {
                enqueue(key);
            } catch(RuntimeException | Error failure) {
                slots.remove(key);
                throw failure;
            }
        }
    }

    public void update(K key, boolean defer) {
        if(!slots.containsKey(key))
            throw new IllegalStateException("updating absent render slot");
        if(defer) {
            enqueue(key);
        } else {
            try {
                replace(key);
                dequeue(key);
            } catch(Loading loading) {
                enqueue(key);
            }
        }
    }

    private void replace(K key) {
        V next = Objects.requireNonNull(build.apply(key));
        V old = slots.put(key, next);
        install.accept(old, next);
    }

    public void remove(K key) {
        if(!slots.containsKey(key))
            throw new IllegalStateException("removing absent render slot");
        V old = slots.remove(key);
        dequeue(key);
        if(old != null)
            install.accept(old, null);
    }

    public void refresh() {slots.keySet().forEach(this::enqueue);}

    /** At least one attempt is allowed; the budget cannot preempt one build. */
    public void retry(int max, long budgetNanos) {
        long start = System.nanoTime();
        int attempts = Math.min(max, pending.size());
        for(int n = 0; n < attempts; n++) {
            if(n > 0 && System.nanoTime() - start >= budgetNanos)
                break;
            K key = pendingOrder.removeFirst();
            pending.remove(key);
            try {
                replace(key);
            } catch(Loading loading) {
                enqueue(key);
            }
        }
    }

    public void clear() {
        for(V old : slots.values()) {
            if(old != null)
                install.accept(old, null);
        }
        slots.clear();
        pending.clear();
        pendingOrder.clear();
    }
}
