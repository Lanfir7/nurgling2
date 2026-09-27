package nurgling;

/** Separate JVM fixture so NConfig's real shutdown hook runs during the test. */
public final class UnloadedNConfigFixtureMain {
    private UnloadedNConfigFixtureMain() {}

    public static void main(String[] args) {
        NConfig config = new NConfig();
        config.path = args[0];
        NConfig.current = config;
        NConfig.set(NConfig.Key.showGrid, true);
    }
}
