package nurgling.actions.bots;

import haven.Coord;
import haven.Loading;
import haven.MCache;
import haven.Resource;
import nurgling.NGameUI;
import nurgling.i18n.L10n;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Watches previously known cave tiles for newly revealed selected ore. */
public final class OreWatch {
    private static final String ROCK_TILE = "gfx/tiles/rocks/";
    private static final int RADIUS = 2;
    private static final Map<String, String> ORES;
    static {
        Map<String, String> ores = new LinkedHashMap<>();
        ores.put("argentite", "Silvershine");
        ores.put("cassiterite", "Cassiterite");
        ores.put("chalcopyrite", "Chalcopyrite");
        ores.put("cinnabar", "Cinnabar");
        ores.put("cuprite", "Wine Glance");
        ores.put("galena", "Galena");
        ores.put("hematite", "Bloodstone");
        ores.put("hornsilver", "Horn Silver");
        ores.put("ilmenite", "Heavy Earth");
        ores.put("leadglance", "Lead Glance");
        ores.put("limonite", "Iron Ochre");
        ores.put("magnetite", "Black Ore");
        ores.put("malachite", "Malachite");
        ores.put("nagyagite", "Leaf Ore");
        ores.put("peacockore", "Peacock Ore");
        ores.put("petzite", "Direvein");
        ores.put("sylvanite", "Schrifterz");
        ORES = Collections.unmodifiableMap(ores);
    }

    private final Set<String> selected;
    private final Map<Coord, Boolean> seen = new HashMap<>();
    private String found;

    public OreWatch(Set<String> selected) {
        this.selected = Collections.unmodifiableSet(validKeys(selected));
    }

    public static Map<String, String> ores() {
        return ORES;
    }

    public static Set<String> validKeys(Set<String> keys) {
        Set<String> valid = new HashSet<>();
        if (keys != null)
            for (String key : keys)
                if (ORES.containsKey(key))
                    valid.add(key);
        return valid;
    }

    static String oreKey(String tileRes) {
        if (tileRes == null || !tileRes.startsWith(ROCK_TILE))
            return null;
        String key = tileRes.substring(ROCK_TILE.length());
        return ORES.containsKey(key) ? key : null;
    }

    /** Missing or loading tiles are not evidence that a tile is non-ore. */
    String observe(Coord center, Function<Coord, String> tileResource) {
        if (found != null)
            return found;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                Coord tile = center.add(dx, dy);
                String resource = tileResource.apply(tile);
                if (resource == null)
                    continue;
                String ore = oreKey(resource);
                Boolean previouslyOre = seen.put(tile, ore != null);
                if (ore != null && Boolean.FALSE.equals(previouslyOre) && selected.contains(ore)) {
                    found = L10n.get("tunneler.ore_found", ORES.get(ore), tile);
                    return found;
                }
            }
        }
        return null;
    }

    public String look(NGameUI gui, Coord center) {
        if (found != null)
            return found;
        MCache map = gui.ui.sess.glob.map;
        return observe(center, tile -> {
            try {
                Resource resource = map.tilesetr(map.gettile(tile));
                return resource == null ? null : resource.name;
            } catch (Loading loading) {
                return null;
            }
        });
    }

    public String found() {
        return found;
    }
}
