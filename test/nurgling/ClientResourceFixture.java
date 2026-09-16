package nurgling;

import haven.Resource;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

/** Supplies local UI assets independently of test execution order. */
public final class ClientResourceFixture implements AutoCloseable {
    private final Field local = field("_local");
    private final Field remote = field("_remote");
    private final Object previousLocal = local.get(null);
    private final Object previousRemote = remote.get(null);
    private final URLClassLoader jars;

    public ClientResourceFixture() throws Exception {
        jars = new URLClassLoader(new URL[]{
                Paths.get("bin", "builtin-res.jar").toUri().toURL(),
                Paths.get("bin", "hafen-res.jar").toUri().toURL()});
        local.set(null, new Resource.Pool(
                new Resource.FileSource(Paths.get("resources", "compiled", "res")),
                name -> {
                    InputStream stream = jars.getResourceAsStream("res/" + name + ".res");
                    if (stream == null) throw new FileNotFoundException(name);
                    return stream;
                }));
        remote.set(null, null);
    }

    @Override public void close() throws Exception {
        local.set(null, previousLocal);
        remote.set(null, previousRemote);
        jars.close();
    }

    private static Field field(String name) throws Exception {
        Field field = Resource.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
