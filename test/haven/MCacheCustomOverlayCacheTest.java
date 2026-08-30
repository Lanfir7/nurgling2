package haven;

import haven.render.RenderTree;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MCacheCustomOverlayCacheTest {
    private static final Class<?> cacheType = cacheType();

    private static Class<?> cacheType() {
        try {
            return Class.forName("haven.MCache$CustomOverlayCache");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("MCache needs a revision-aware custom-overlay cache", e);
        }
    }

    private static Object cache() {
        try {
            Constructor<?> constructor = cacheType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot create custom-overlay cache", e);
        }
    }

    private static void put(Object cache, int id, long revision) {
        try {
            Method put = cacheType.getDeclaredMethod("put", Integer.class, long.class,
                    RenderTree.Node.class, RenderTree.Node.class);
            put.setAccessible(true);
            put.invoke(cache, id, revision, null, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot store a custom-overlay revision", e);
        }
    }

    private static boolean isCurrent(Object cache, int id, long revision) {
        try {
            Method current = cacheType.getDeclaredMethod("isCurrent", Integer.class, long.class);
            current.setAccessible(true);
            return (boolean) current.invoke(cache, id, revision);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot check a custom-overlay revision", e);
        }
    }

    private static void clear(Object cache) {
        try {
            Method clear = cacheType.getDeclaredMethod("clear");
            clear.setAccessible(true);
            clear.invoke(cache);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot invalidate a custom-overlay cache", e);
        }
    }

    @Test
    void emptyOverlayGeometryRemainsCachedUntilItsRevisionChanges() {
        Object cache = cache();

        put(cache, 17, 4);

        assertTrue(isCurrent(cache, 17, 4));
        assertFalse(isCurrent(cache, 17, 5));
    }

    @Test
    void invalidatingOneMapCutDoesNotEvictAnotherCut() {
        Object changedCut = cache();
        Object untouchedCut = cache();
        put(changedCut, 17, 4);
        put(untouchedCut, 17, 4);

        clear(changedCut);

        assertFalse(isCurrent(changedCut, 17, 4));
        assertTrue(isCurrent(untouchedCut, 17, 4));
    }
}
