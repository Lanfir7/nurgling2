package nurgling.tools;

import nurgling.NConfig;

import java.util.Objects;
import java.util.function.UnaryOperator;

public final class HomeInteriorStore {
    public static HomeInteriorRegistry load(String genus) {
        return HomeInteriorRegistry.decodeForWorld(
                NConfig.getGlobal(NConfig.Key.homeInteriors), genus);
    }

    public static HomeInteriorRegistry update(String genus,
            UnaryOperator<HomeInteriorRegistry> updater) {
        Object stored = NConfig.update(NConfig.Key.homeInteriors, raw -> {
            HomeInteriorRegistry current = HomeInteriorRegistry.decodeForWorld(raw, genus);
            HomeInteriorRegistry changed = Objects.requireNonNull(updater.apply(current));
            return HomeInteriorRegistry.encodeForWorld(raw, genus, changed);
        });
        return HomeInteriorRegistry.decodeForWorld(stored, genus);
    }
}
