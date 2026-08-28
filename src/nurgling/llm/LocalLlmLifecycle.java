package nurgling.llm;

import java.net.URI;
import java.util.Optional;

/**
 * Process-global desktop lifecycle bridge for one LocalLlmManager instance.
 */
public class LocalLlmLifecycle {
    interface ManagerFactory {
        LocalLlmManager create();
    }

    private static final LocalLlmLifecycle GLOBAL = new LocalLlmLifecycle(new ManagerFactory() {
        @Override
        public LocalLlmManager create() {
            return new LocalLlmManager();
        }
    });

    private final Object lock = new Object();
    private final ManagerFactory managerFactory;
    private LocalLlmManager manager;

    public static LocalLlmLifecycle global() {
        return GLOBAL;
    }

    LocalLlmLifecycle(ManagerFactory managerFactory) {
        this.managerFactory = managerFactory;
    }

    public void startDesktop() {
        LocalLlmManager current;
        synchronized (lock) {
            if (manager == null) {
                manager = managerFactory.create();
            }
            current = manager;
        }
        if (current != null) {
            current.start();
        }
    }

    public void stopDesktop() {
        LocalLlmManager current;
        synchronized (lock) {
            current = manager;
        }
        if (current != null) {
            current.stop();
        }
    }

    public boolean isAvailable() {
        LocalLlmManager current = currentManagerForTests();
        return current != null && current.isAvailable();
    }

    public boolean isReady() {
        LocalLlmManager current = currentManagerForTests();
        return current != null && current.isReady();
    }

    public Optional<URI> getEndpoint() {
        LocalLlmManager current = currentManagerForTests();
        return current == null ? Optional.<URI>empty() : current.getEndpoint();
    }

    public LocalLlmState getState() {
        LocalLlmManager current = currentManagerForTests();
        return current == null ? LocalLlmState.STOPPED : current.getState();
    }

    public LocalLlmStatus getStatus() {
        LocalLlmManager current = currentManagerForTests();
        return current == null
                ? new LocalLlmStatus(LocalLlmState.STOPPED, Optional.<URI>empty())
                : current.getStatus();
    }

    private LocalLlmManager currentManagerForStatus() {
        synchronized (lock) {
            return manager;
        }
    }

    LocalLlmManager currentManagerForTests() {
        return currentManagerForStatus();
    }
}
