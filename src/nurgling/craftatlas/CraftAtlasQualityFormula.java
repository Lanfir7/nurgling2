package nurgling.craftatlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Known workstation quality formulas used by the Atlas quality preview. */
public final class CraftAtlasQualityFormula {
    public static final String CAULDRON_WATER = "context:cauldron-water";
    public static final String GENERIC_CAULDRON_TYPE = "context:cauldron-clay";

    public static final class Factor {
        public final String key, resource, name;
        public final CraftAtlasEntry.Requirement requirement;
        public final boolean affectsResult;

        private Factor(String key, String resource, String name,
                       CraftAtlasEntry.Requirement requirement, boolean affectsResult) {
            this.key = key;
            this.resource = resource;
            this.name = name;
            this.requirement = requirement;
            this.affectsResult = affectsResult;
        }
    }

    private CraftAtlasQualityFormula() { }

    public static List<Factor> factors(CraftAtlasEntry entry) {
        if(entry == null) return Collections.emptyList();
        Map<String, Factor> factors = new LinkedHashMap<>();
        boolean cauldron = false;
        boolean anvil = false;
        for(CraftAtlasEntry.Requirement requirement : entry.requirements) {
            if(requirement.kind != CraftAtlasEntry.RequirementKind.STATION &&
                    requirement.kind != CraftAtlasEntry.RequirementKind.TOOL) continue;
            String key = factorKey(entry, requirement);
            cauldron |= "station:cauldron".equals(key) || "station:clay-cauldron".equals(key);
            anvil |= "station:anvil".equals(key);
            factors.putIfAbsent(key, new Factor(key, requirement.resource, requirement.name, requirement,
                    affectsResult(key)));
        }
        if(anvil) {
            CraftAtlasEntry.Requirement hammer = new CraftAtlasEntry.Requirement(
                    CraftAtlasEntry.RequirementKind.TOOL, "gfx/invobjs/smithshammer", "Smithy's Hammer", null);
            factors.putIfAbsent("tool:smithy-hammer", new Factor("tool:smithy-hammer",
                    hammer.resource, hammer.name, hammer, true));
        }
        if(hasGenericCauldron(entry)) {
            CraftAtlasEntry.Requirement clay = new CraftAtlasEntry.Requirement(
                    CraftAtlasEntry.RequirementKind.STATION, "gfx/terobjs/claycauldron", "Clay Cauldron", null);
            factors.putIfAbsent("station:clay-cauldron", new Factor("station:clay-cauldron",
                    clay.resource, clay.name, clay, true));
        }
        if(cauldron) factors.putIfAbsent(CAULDRON_WATER, new Factor(CAULDRON_WATER, "gfx/invobjs/water",
                "Water in cauldron", null, true));
        return Collections.unmodifiableList(new ArrayList<>(factors.values()));
    }

    public static double result(CraftAtlasEntry entry, double ingredientQuality,
                                Map<String, Double> qualities) {
        double ingredients = finiteQuality(ingredientQuality, 10);
        if(hasCauldron(entry)) {
            boolean clay = hasExplicitClayCauldron(entry) || genericCauldronUsesClay(entry, qualities);
            double cauldron = quality(qualities, clay ? "station:clay-cauldron" : "station:cauldron");
            double water = quality(qualities, CAULDRON_WATER);
            if(clay)
                water = Math.max(1.0, water / 2.0);
            return (ingredients * 6.0 + cauldron + water) / 8.0;
        }
        if(has(entry, "station:anvil")) {
            double anvil = quality(qualities, "station:anvil");
            double hammer = quality(qualities, "tool:smithy-hammer");
            return (ingredients * 9.0 + anvil * 4.0 + hammer * 3.0) / 16.0;
        }
        for(String station : new String[] { "station:loom", "station:spinning-wheel", "station:churn",
                "station:meatgrinder", "station:potters-wheel", "station:extraction-press" }) {
            if(has(entry, station)) return (ingredients * 3.0 + quality(qualities, station)) / 4.0;
        }
        return ingredients;
    }

    public static double softcap(double resultQuality, double characterQuality) {
        double result = finiteQuality(resultQuality, 10);
        double character = finiteQuality(characterQuality, result);
        return character < result ? (result + character) / 2.0 : result;
    }

    public static String key(CraftAtlasEntry.Requirement requirement) {
        if(requirement == null) return "";
        String resource = normalized(requirement.resource);
        String name = normalized(requirement.name);
        String prefix = requirement.kind == CraftAtlasEntry.RequirementKind.STATION ? "station:" : "tool:";
        if(matches(resource, name, "clay-cauldron", "claycauldron")) return "station:clay-cauldron";
        if(matches(resource, name, "cauldron", "metal-cauldron", "metalcauldron")) return "station:cauldron";
        if(matches(resource, name, "crucible")) return "station:crucible";
        if(matches(resource, name, "anvil")) return "station:anvil";
        if(matches(resource, name, "smelter", "ore-smelter")) return "station:smelter";
        if(matches(resource, name, "stack-furnace", "stackfurnace", "primsmelter")) return "station:stack-furnace";
        if(matches(resource, name, "spinning-wheel", "swheel", "sswheel", "spinningwheel"))
            return "station:spinning-wheel";
        if(matches(resource, name, "potters-wheel", "potter-s-wheel", "potterswheel"))
            return "station:potters-wheel";
        if(matches(resource, name, "extraction-press", "extractionpress", "winepress"))
            return "station:extraction-press";
        if(matches(resource, name, "meatgrinder")) return "station:meatgrinder";
        if(matches(resource, name, "loom")) return "station:loom";
        if(matches(resource, name, "churn")) return "station:churn";
        if(matches(resource, name, "smithy-s-hammer", "smithy-hammer", "smithshammer"))
            return "tool:smithy-hammer";
        String base = requirement.resource;
        if(base == null || base.isEmpty()) base = requirement.name == null ? "unknown" : requirement.name;
        int slash = base.lastIndexOf('/');
        if(slash >= 0) base = base.substring(slash + 1);
        base = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return prefix + base;
    }

    private static boolean has(CraftAtlasEntry entry, String key) {
        for(Factor factor : factors(entry)) if(key.equals(factor.key)) return true;
        return false;
    }

    /** Use a cauldron's listed water requirement for the formula instead of adding a duplicate row. */
    public static String factorKey(CraftAtlasEntry entry, CraftAtlasEntry.Requirement requirement) {
        if(hasCauldron(entry) && isWater(requirement)) return CAULDRON_WATER;
        return key(requirement);
    }

    public static boolean hasGenericCauldron(CraftAtlasEntry entry) {
        if(entry == null) return false;
        for(CraftAtlasEntry.Requirement requirement : entry.requirements) {
            if(requirement == null) continue;
            String resource = normalized(requirement.resource);
            String name = normalized(requirement.name);
            if(matches(resource, name, "metal-cauldron", "metalcauldron", "clay-cauldron", "claycauldron")) continue;
            if(matches(resource, name, "cauldron"))
                return true;
        }
        return false;
    }

    private static boolean hasCauldron(CraftAtlasEntry entry) {
        if(entry == null) return false;
        for(CraftAtlasEntry.Requirement requirement : entry.requirements)
            if("station:cauldron".equals(key(requirement)) || "station:clay-cauldron".equals(key(requirement)))
                return true;
        return false;
    }

    private static boolean hasExplicitClayCauldron(CraftAtlasEntry entry) {
        if(entry == null) return false;
        for(CraftAtlasEntry.Requirement requirement : entry.requirements)
            if("station:clay-cauldron".equals(key(requirement))) return true;
        return false;
    }

    private static boolean isWater(CraftAtlasEntry.Requirement requirement) {
        if(requirement == null) return false;
        return matches(normalized(requirement.resource), normalized(requirement.name), "water");
    }

    private static boolean genericCauldronUsesClay(CraftAtlasEntry entry, Map<String, Double> qualities) {
        Double value = qualities == null ? null : qualities.get(GENERIC_CAULDRON_TYPE);
        return hasGenericCauldron(entry) && value != null && Double.isFinite(value) && value >= 1.0;
    }

    public static String canonicalStoredKey(String key) {
        if(key == null) return null;
        String value = key.toLowerCase(Locale.ROOT);
        if(!value.startsWith("tool:") && !value.startsWith("station:")) return key;
        String canonical = key(new CraftAtlasEntry.Requirement(CraftAtlasEntry.RequirementKind.STATION,
                value.substring(value.indexOf(':') + 1), null, null));
        for(String name : new String[] { "loom", "spinning-wheel", "churn", "meatgrinder", "potters-wheel", "extraction-press", "smelter", "stack-furnace" })
            if(canonical.equals("station:" + name)) return canonical;
        return key;
    }

    private static boolean matches(String resource, String name, String... aliases) {
        for(String alias : aliases)
            if(alias.equals(resource) || alias.equals(name) || ("wiki-item-" + alias).equals(resource)) return true;
        return false;
    }

    private static String normalized(String value) {
        if(value == null) return "";
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf(':'));
        if(slash >= 0) value = value.substring(slash + 1);
        String result = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "")
                .replaceFirst("-(lit|assembled)$", "");
        // Wiki requirements append the required water volume to the station's name.
        return result.replaceFirst("^((?:wiki-item-)?(?:(?:clay|metal)-)?cauldron)-(?:with-)?[0-9]+(?:-[0-9]+)?-?(?:l|liters|litres)$", "$1");
    }

    private static double quality(Map<String, Double> values, String key) {
        return finiteQuality(values == null ? null : values.get(key), 10);
    }

    private static boolean affectsResult(String key) {
        return CAULDRON_WATER.equals(key) || "station:cauldron".equals(key) || "station:clay-cauldron".equals(key) ||
                "station:anvil".equals(key) || "tool:smithy-hammer".equals(key) ||
                "station:loom".equals(key) || "station:spinning-wheel".equals(key) ||
                "station:churn".equals(key) || "station:meatgrinder".equals(key) ||
                "station:potters-wheel".equals(key) || "station:extraction-press".equals(key);
    }

    private static double finiteQuality(Double value, double fallback) {
        return value != null && Double.isFinite(value) && value >= 1 ? value : fallback;
    }
}
