package nurgling.actions.bots.road;

import nurgling.NUtils;
import nurgling.routes.ForagerPath;
import nurgling.routes.ForagerRouteStore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Named road routes, stored as {@link ForagerPath} files under {@code road_paths}. */
public final class RoadRouteStore {
    private static final String ROUTES_DIR = "road_paths";

    private RoadRouteStore() {}

    public static List<String> listRouteNames() {
        List<String> names = new ArrayList<String>();
        File dir = NUtils.getDataFilePath(ROUTES_DIR).toFile();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    names.add(f.getName().replace(".json", ""));
                }
            }
        }
        Collections.sort(names);
        return names;
    }

    public static ForagerRouteStore.LoadResult load(String name) {
        return ForagerRouteStore.loadFromFile(name, nameToFile(name));
    }

    public static void save(ForagerPath route) throws IOException {
        route.save(NUtils.getDataFile(ROUTES_DIR));
    }

    public static void delete(String name) throws IOException {
        Files.deleteIfExists(namePath(name));
    }

    public static String nameToFile(String name) {
        return NUtils.getDataFile(ROUTES_DIR, name + ".json");
    }

    private static Path namePath(String name) {
        return NUtils.getDataFilePath(ROUTES_DIR, name + ".json");
    }
}
