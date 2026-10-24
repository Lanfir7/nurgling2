package nurgling.planning;

import nurgling.NUtils;
import nurgling.areas.AreaPresetStore;
import nurgling.tools.NFileUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Where Base planner presets live: one JSON file each in {@code <data dir>/planner_presets/}, shared
 * by every profile and world, so a preset can be shared by sending its file. There are no built-in
 * presets; players make their own.
 */
public final class PlannerPresetStore
{
    private static final String DIR = "planner_presets";

    private PlannerPresetStore() {}

    public static Path dir()
    {
        return NUtils.getDataFilePath(DIR);
    }

    /** The player's presets, A-Z. A file that doesn't parse is skipped, not fatal. */
    public static List<PlannerPreset> all()
    {
        List<PlannerPreset> res = new ArrayList<>();
        Path dir = dir();
        if (!Files.isDirectory(dir))
            return res;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
            for (Path f : files) {
                try {
                    PlannerPreset p = load(f);
                    p.file = f.getFileName().toString();
                    res.add(p);
                } catch (IOException | JSONException | IllegalArgumentException e) {
                    System.out.println("[PlannerPresets] Skipping " + f + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("[PlannerPresets] Can't list " + dir + ": " + e.getMessage());
        }
        res.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name));
        return res;
    }

    public static PlannerPreset load(Path file) throws IOException
    {
        if (Files.size(file) > 2_000_000)
            throw new IOException("Planner preset is too large");
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        return PlannerPreset.fromJson(new JSONObject(text));
    }

    /** Writes a new preset under a file name made from its name. */
    public static synchronized void saveNew(PlannerPreset p) throws IOException
    {
        saveNew(p, dir(), (target, json) -> NFileUtils.writeAtomically(target.toString(), json));
    }

    @FunctionalInterface
    interface Writer { void write(Path target, String json) throws IOException; }

    /** Package-private root/writer keeps tests away from the player's real preset folder. */
    static synchronized void saveNew(PlannerPreset p, Path root, Writer writer) throws IOException {
        p.validate();
        Files.createDirectories(root);
        String base = AreaPresetStore.slug(p.name);
        for (int i = 1; i < 10000; i++) {
            String file = base + (i == 1 ? "" : "-" + i) + ".json";
            Path target = ownedFile(root, file);
            try {
                Files.createFile(target);
            } catch (FileAlreadyExistsException collision) {
                continue;
            }
            String previous = p.file;
            p.file = file;
            try {
                writer.write(target, p.toJson().toString(2));
                return;
            } catch (IOException | RuntimeException failure) {
                p.file = previous;
                // Only this invocation reserved target. Never remove a colliding player's preset.
                try {
                    Files.deleteIfExists(target);
                    Files.deleteIfExists(target.resolveSibling(file + ".bak"));
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }
        throw new IOException("Too many planner presets with this name");
    }

    /** Rewrites a preset in its own file, e.g. after a rename. */
    public static synchronized void write(PlannerPreset p) throws IOException
    {
        p.validate();
        NFileUtils.writeAtomically(ownedFile(p.file).toString(), p.toJson().toString(2));
    }

    public static void delete(PlannerPreset p) throws IOException
    {
        if (p.file == null)
            return;
        Path file = ownedFile(p.file);
        Files.deleteIfExists(file);
        Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".bak"));
    }

    public static void export(PlannerPreset p, Path target) throws IOException
    {
        NFileUtils.writeAtomically(target.toString(), p.toJson().toString(2));
    }

    /** Copies a preset file from elsewhere into the presets folder; throws if it isn't one. */
    public static PlannerPreset importFile(Path source) throws IOException
    {
        PlannerPreset p = load(source);
        saveNew(p);
        return p;
    }

    private static Path ownedFile(String file) throws IOException
    {
        return ownedFile(dir(), file);
    }

    private static Path ownedFile(Path rootDir, String file) throws IOException
    {
        if (file == null || !file.matches("[\\p{L}\\p{N}_-]+\\.json"))
            throw new IOException("Invalid planner preset file name");
        Path root = rootDir.toAbsolutePath().normalize();
        Path target = root.resolve(file).normalize();
        if (!target.getParent().equals(root) || Files.isSymbolicLink(target))
            throw new IOException("Planner preset path escapes its directory");
        return target;
    }
}
