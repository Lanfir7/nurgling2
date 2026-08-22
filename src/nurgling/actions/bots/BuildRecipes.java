package nurgling.actions.bots;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-building construction recipes for the ghost-area resource calculator.
 * Counts match the current Build*.java bots, not the in-game wiki.
 */
public final class BuildRecipes {
    public static final class Line {
        public final String materialId;
        public final int count;

        public Line(String materialId, int count) {
            this.materialId = materialId;
            this.count = count;
        }
    }

    private static final Map<String, List<Line>> RECIPES = new LinkedHashMap<String, List<Line>>();

    static {
        put("Cupboard", line("board", 8));
        put("Barrel", line("board", 5));
        put("Cheese Rack", line("board", 6), line("block", 4));
        put("Crate", line("board", 4));
        put("Wooden Chest", line("board", 4), line("nugget", 4));
        put("Drying Frame", line("branch", 5), line("bough", 2), line("string", 2));
        put("Herbalist Table", line("block", 4), line("board", 4), line("finer_plant_fibre", 8));
        put("Kiln", line("clay", 35));
        put("Large Chest",
                line("board", 5),
                line("metal_bar", 2),
                line("leather", 4),
                line("rope", 2),
                line("bone_glue", 3));
        put("Mound Bed", line("mulch", 12), line("straw", 6));
        put("Smoke Shed",
                line("board", 12),
                line("block", 4),
                line("thatch_or_bough", 6),
                line("brick", 10));
        put("Stone Casket", line("stone", 20), line("nugget", 2));
        put("Tar Kiln", line("stone", 35), line("clay", 50));
        put("Tanning Tub", line("board", 4), line("block", 2));
        put("Dream Catcher", line("bough", 4), line("string", 2));
    }

    private BuildRecipes() {}

    public static List<Line> of(String buildingName) {
        if (buildingName == null)
            return Collections.emptyList();
        List<Line> recipe = RECIPES.get(buildingName);
        if (recipe == null)
            return Collections.emptyList();
        return recipe;
    }

    public static List<Line> totals(String buildingName, int buildings) {
        if (buildings <= 0)
            return Collections.emptyList();
        List<Line> recipe = of(buildingName);
        if (recipe.isEmpty())
            return Collections.emptyList();
        List<Line> out = new ArrayList<Line>(recipe.size());
        for (Line line : recipe)
            out.add(new Line(line.materialId, line.count * buildings));
        return out;
    }

    public static String slug(String buildingName) {
        if (buildingName == null)
            return "";
        return buildingName.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static void put(String name, Line... lines) {
        RECIPES.put(name, Collections.unmodifiableList(Arrays.asList(lines)));
    }

    private static Line line(String materialId, int count) {
        return new Line(materialId, count);
    }
}
