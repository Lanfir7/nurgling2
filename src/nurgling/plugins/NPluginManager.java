package nurgling.plugins;

import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.widgets.charsel.NCharselScreen;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads external plugins from a drop-folder ({@code plugins/} by default).
 *
 * By default only plugins signed by the client's embedded trusted certificate
 * are loaded, so users are not tricked into running untrusted or malicious jars.
 * A dev escape hatch ({@link NConfig.Key#pluginsAllowUnsigned}) disables
 * verification for local testing.
 *
 * The trusted certificate is read from the classpath resource
 * {@code /nurgling/plugins/trusted.cer} (a non-.java file under src/, copied
 * into the build by the client's build script).
 */
public class NPluginManager {

    private static final List<NPlugin> plugins = new ArrayList<>();
    private static boolean loaded = false;
    private static X509Certificate trusted = null;

    /** Discover and load all plugin jars. Idempotent; later calls are fallbacks. */
    public static synchronized void loadAll() {
        if (loaded) return;
        loaded = true;

        trusted = loadTrustedCert();
        boolean allowUnsigned = isAllowUnsigned();

        File dir = pluginsDir();
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] jars = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
        if (jars == null) return;

        for (File jar : jars) {
            loadJar(jar, allowUnsigned, NPluginManager::loadPlugin);
        }
    }

    @FunctionalInterface
    interface PluginLoader {
        NPlugin load(File jar, boolean allowUnsigned) throws Exception;
    }

    /** Loads one candidate so a broken plugin jar cannot prevent the rest from loading. */
    static boolean loadJar(File jar, boolean allowUnsigned, PluginLoader loader) {
        try {
            NPlugin p = loader.load(jar, allowUnsigned);
            if (p != null) {
                String name = pluginName(p);
                plugins.add(p);
                System.out.println("[Plugins] Loaded: " + name + " (" + jar.getName() + ")");
            }
            return true;
        } catch (Exception e) {
            System.out.println("[Plugins] Failed to load " + jar.getName() + ": " + e);
            return false;
        } catch (Error e) {
            rethrowFatal(e);
            System.out.println("[Plugins] Failed to load " + jar.getName() + ": " + e);
            return false;
        }
    }

    /** Called when a session shows character selection; notifies every loaded plugin. */
    public static void onCharsel(NCharselScreen screen) {
        for (NPlugin p : snapshot()) {
            try {
                p.onCharsel(screen);
            } catch (RuntimeException e) {
                System.out.println("[Plugins] onCharsel error in " + pluginName(p) + ": " + e);
            } catch (Error error) {
                rethrowFatal(error);
                System.out.println("[Plugins] onCharsel error in " + pluginName(p) + ": " + error);
            }
        }
    }

    /** Called when a session's NGameUI is ready; notifies every loaded plugin. */
    public static void onGameUIReady(NGameUI gui) {
        for (NPlugin p : snapshot()) {
            try {
                p.onLoad(gui);
            } catch (RuntimeException e) {
                System.out.println("[Plugins] onLoad error in " + pluginName(p) + ": " + e);
            } catch (Error error) {
                rethrowFatal(error);
                System.out.println("[Plugins] onLoad error in " + pluginName(p) + ": " + error);
            }
        }
    }

    private static synchronized List<NPlugin> snapshot() {
        loadAll();
        return (new ArrayList<>(plugins));
    }

    private static String pluginName(NPlugin plugin) {
        try {
            return (plugin.name());
        } catch (RuntimeException ignored) {
            return (plugin.getClass().getName());
        } catch (Error error) {
            rethrowFatal(error);
            return (plugin.getClass().getName());
        }
    }

    private static void rethrowFatal(Error error) {
        if (error instanceof VirtualMachineError)
            throw (VirtualMachineError) error;
        if (error instanceof ThreadDeath)
            throw (ThreadDeath) error;
    }

    private static NPlugin loadPlugin(File jar, boolean allowUnsigned) throws Exception {
        if (!allowUnsigned) {
            if (trusted == null) {
                System.out.println("[Plugins] Refusing " + jar.getName()
                        + ": no trusted certificate embedded. Sign the plugin, or set pluginsAllowUnsigned for dev.");
                return null;
            }
            if (!isSignedByTrusted(jar)) {
                System.out.println("[Plugins] Refusing " + jar.getName() + ": not signed by the trusted key.");
                return null;
            }
        } else {
            System.out.println("[Plugins] WARNING: signature verification disabled (dev mode) for " + jar.getName());
        }

        String entry = readEntryClass(jar);
        if (entry == null) {
            System.out.println("[Plugins] No entry class (plugin.properties 'main=') in " + jar.getName());
            return null;
        }

        URLClassLoader cl = new URLClassLoader(
                new URL[]{jar.toURI().toURL()},
                NPluginManager.class.getClassLoader());
        boolean keepClassLoader = false;
        try {
            Class<?> cls = Class.forName(entry, true, cl);
            Object o = cls.getDeclaredConstructor().newInstance();
            if (!(o instanceof NPlugin)) {
                System.out.println("[Plugins] Entry class is not an NPlugin: " + entry);
                return null;
            }
            keepClassLoader = true;
            return (NPlugin) o;
        } finally {
            if (!keepClassLoader) {
                try {
                    cl.close();
                } catch (IOException e) {
                    System.out.println("[Plugins] Failed to close rejected plugin " + jar.getName() + ": " + e);
                }
            }
        }
    }

    private static String readEntryClass(File jar) throws Exception {
        try (JarFile jf = new JarFile(jar)) {
            JarEntry pe = jf.getJarEntry("plugin.properties");
            if (pe == null) return null;
            Properties props = new Properties();
            try (InputStream in = jf.getInputStream(pe)) {
                props.load(in);
            }
            String main = props.getProperty("main");
            return (main != null && !main.trim().isEmpty()) ? main.trim() : null;
        }
    }

    /** True only if every content entry is signed by the trusted certificate. */
    private static boolean isSignedByTrusted(File jar) {
        try (JarFile jf = new JarFile(jar, true)) {
            byte[] buf = new byte[8192];
            boolean anyContent = false;
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                // Reading the entry fully is required before certificates populate.
                try (InputStream in = jf.getInputStream(je)) {
                    while (in.read(buf) != -1) { /* discard */ }
                }
                if (je.isDirectory()) continue;
                String nm = je.getName();
                if (nm.startsWith("META-INF/")) continue;
                Certificate[] certs = je.getCertificates();
                if (certs == null || certs.length == 0) {
                    return false; // an unsigned content entry
                }
                boolean match = false;
                for (Certificate c : certs) {
                    if (c.equals(trusted)) { match = true; break; }
                }
                if (!match) return false;
                anyContent = true;
            }
            return anyContent;
        } catch (Exception e) {
            System.out.println("[Plugins] Signature check failed for " + jar.getName() + ": " + e);
            return false;
        }
    }

    private static X509Certificate loadTrustedCert() {
        try (InputStream in = NPluginManager.class.getResourceAsStream("/nurgling/plugins/trusted.cer")) {
            if (in == null) return null;
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static File pluginsDir() {
        try {
            Object v = NConfig.get(NConfig.Key.pluginsDir);
            if (v instanceof String && !((String) v).isEmpty()) {
                return new File((String) v);
            }
        } catch (Exception ignored) {
        }
        return new File("plugins");
    }

    private static boolean isAllowUnsigned() {
        try {
            return Boolean.TRUE.equals(NConfig.get(NConfig.Key.pluginsAllowUnsigned));
        } catch (Exception e) {
            return false;
        }
    }
}
