package nurgling.plugins;

import nurgling.NGameUI;
import nurgling.widgets.charsel.NCharselScreen;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NPluginManagerCharselTest {
    @Test
    void charselCallbacksAreOptionalAndOneFailureDoesNotStopTheOthers() throws Exception {
        List<NPlugin> plugins = plugins();
        boolean loaded = loaded();
        List<NPlugin> previous = new ArrayList<>(plugins);
        AtomicInteger called = new AtomicInteger();
        try {
            plugins.clear();
            plugins.add(new NPlugin() {
                public String name() { throw new RuntimeException("expected name failure"); }
                public void onLoad(NGameUI gui) {}
                public void onCharsel(NCharselScreen screen) { throw new AssertionError("expected test failure"); }
            });
            plugins.add(new NPlugin() {
                public String name() { return "working"; }
                public void onLoad(NGameUI gui) {}
                public void onCharsel(NCharselScreen screen) { called.incrementAndGet(); }
            });
            setLoaded(true);

            NPluginManager.onCharsel(null);
            assertEquals(1, called.get());
            assertDoesNotThrow(() -> new NPlugin() {
                public String name() { return "legacy"; }
                public void onLoad(NGameUI gui) {}
            }.onCharsel(null));
        } finally {
            plugins.clear();
            plugins.addAll(previous);
            setLoaded(loaded);
        }
    }

    @Test
    void brokenJarErrorsDoNotPreventTheNextPluginFromLoading() throws Exception {
        List<NPlugin> plugins = plugins();
        List<NPlugin> previous = new ArrayList<>(plugins);
        AtomicInteger attempts = new AtomicInteger();
        try {
            plugins.clear();
            assertFalse(NPluginManager.loadJar(new File("broken.jar"), false, (jar, allowUnsigned) -> {
                attempts.incrementAndGet();
                throw new NoClassDefFoundError("missing/plugin/Dependency");
            }));
            assertFalse(NPluginManager.loadJar(new File("plugin-error.jar"), false, (jar, allowUnsigned) -> {
                attempts.incrementAndGet();
                throw new AssertionError("plugin failure");
            }));
            assertThrows(StackOverflowError.class, () -> NPluginManager.loadJar(
                    new File("fatal.jar"), false, (jar, allowUnsigned) -> {
                        throw new StackOverflowError("fatal test error");
                    }));
            assertThrows(ThreadDeath.class, () -> NPluginManager.loadJar(
                    new File("thread-death.jar"), false, (jar, allowUnsigned) -> {
                        throw new ThreadDeath();
                    }));
            assertTrue(NPluginManager.loadJar(new File("name-error.jar"), false, (jar, allowUnsigned) -> {
                attempts.incrementAndGet();
                return new NPlugin() {
                    public String name() { throw new AssertionError("name failure"); }
                    public void onLoad(NGameUI gui) {}
                };
            }));
            assertTrue(NPluginManager.loadJar(new File("working.jar"), false, (jar, allowUnsigned) -> {
                attempts.incrementAndGet();
                return new NPlugin() {
                    public String name() { return "working"; }
                    public void onLoad(NGameUI gui) {}
                };
            }));
            assertEquals(4, attempts.get());
            assertEquals(2, plugins.size());
        } finally {
            plugins.clear();
            plugins.addAll(previous);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<NPlugin> plugins() throws Exception {
        Field field = NPluginManager.class.getDeclaredField("plugins");
        field.setAccessible(true);
        return (List<NPlugin>) field.get(null);
    }

    private static boolean loaded() throws Exception {
        Field field = NPluginManager.class.getDeclaredField("loaded");
        field.setAccessible(true);
        return field.getBoolean(null);
    }

    private static void setLoaded(boolean value) throws Exception {
        Field field = NPluginManager.class.getDeclaredField("loaded");
        field.setAccessible(true);
        field.setBoolean(null, value);
    }
}
