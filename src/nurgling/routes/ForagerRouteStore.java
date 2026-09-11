package nurgling.routes;

import nurgling.NUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Filesystem persistence for named Forager routes (ForagerPath) - kept separate from any UI so
 *  listing/loading/saving/deleting a route doesn't require going through the Settings panel. */
public class ForagerRouteStore {
    private static final String ROUTES_DIR = "forager_paths";

    private ForagerRouteStore() {}

    /** Every saved route's bare name (no directory, no .json), sorted. */
    public static List<String> listRouteNames() {
        List<String> names = new ArrayList<>();
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

    /**
     * Loads a named route. Parse/read failures stay distinguishable: {@link LoadResult#failed()}
     * is true and {@link LoadResult#canSave()} is false, so callers must not persist an empty
     * replacement over the existing file.
     */
    public static LoadResult load(String name) {
        return loadFromFile(name, nameToFile(name));
    }

    /** Same as {@link #load(String)} against an explicit file path (tests and callers that already have one). */
    public static LoadResult loadFromFile(String name, String filePath) {
        try {
            return LoadResult.loaded(ForagerPath.load(filePath));
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.isEmpty()) {
                message = e.getClass().getSimpleName();
            }
            return LoadResult.failed(name, message);
        }
    }

    /** Outcome of {@link #load(String)} / {@link #loadFromFile(String, String)}. */
    public static final class LoadResult {
        private final ForagerPath path;
        private final String name;
        private final String error;

        private LoadResult(ForagerPath path, String name, String error) {
            this.path = path;
            this.name = name;
            this.error = error;
        }

        public static LoadResult loaded(ForagerPath path) {
            if (path == null) throw new NullPointerException("path");
            return new LoadResult(path, path.name, null);
        }

        public static LoadResult failed(String name, String error) {
            return new LoadResult(null, name, error != null ? error : "unknown error");
        }

        public ForagerPath path() { return path; }
        public String name() { return name; }
        public String error() { return error; }
        public boolean failed() { return error != null; }
        public boolean canSave() { return path != null && error == null; }
    }

    public static void save(ForagerPath route) throws IOException {
        route.save(NUtils.getDataFile(ROUTES_DIR));
    }

    public static void delete(String name) throws IOException {
        Files.deleteIfExists(namePath(name));
    }

    /** PresetData.pathFile is a full file path; callers working in bare names (routeNames, the dropdowns) use this to convert. */
    public static String fileToName(String pathFile) {
        if (pathFile == null || pathFile.isEmpty()) return null;
        String name = new File(pathFile).getName();
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - 5);
        }
        return name;
    }

    public static String nameToFile(String name) {
        return NUtils.getDataFile(ROUTES_DIR, name + ".json");
    }

    private static Path namePath(String name) {
        return NUtils.getDataFilePath(ROUTES_DIR, name + ".json");
    }
}
