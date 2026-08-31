package haven;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LazyRebuildPolicyTest {
    @Test
    void invalidationDoesNotStartUnusedValue() {
        LazyRebuildPolicy policy = new LazyRebuildPolicy();

        assertFalse(policy.onInvalidate());
    }

    @Test
    void firstRequestStartsBuildOnlyOnce() {
        LazyRebuildPolicy policy = new LazyRebuildPolicy();

        assertTrue(policy.onGet());
        assertFalse(policy.onGet());
    }

    @Test
    void invalidationRebuildsRequestedValue() {
        LazyRebuildPolicy policy = new LazyRebuildPolicy();
        policy.onGet();

        assertTrue(policy.onInvalidate());
    }

    @Test
    void disposedValueCannotRestart() {
        LazyRebuildPolicy policy = new LazyRebuildPolicy();
        policy.onGet();
        policy.onDispose();

        assertFalse(policy.onGet());
        assertFalse(policy.onInvalidate());
    }

    @Test
    void deferredInvalidationDoesNotQueueUnusedCut() throws Exception {
        MCache cache = new MCache(null);
        MCache.Grid grid = cache.new Grid(Coord.z);
        MCache.Grid.Deferred<Object> deferred = grid.new Deferred<Object>() {
            protected Object build() {
                return(new Object());
            }

            protected String message() {
                return("test");
            }
        };
        Field queuedBuild = MCache.Grid.Deferred.class.getDeclaredField("def");
        queuedBuild.setAccessible(true);

        deferred.rebuild();

        assertNull(queuedBuild.get(deferred));
        deferred.dispose();
    }
}
