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
            String key = key(requirement);
            cauldron |= "station:cauldron".equals(key);
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
        if(cauldron) factors.put(CAULDRON_WATER, new Factor(CAULDRON_WATER, "gfx/invobjs/water",
                "Water in cauldron", null, true));
        return Collections.unmodifiableList(new ArrayList<>(factors.values()));
    }

    public static double result(CraftAtlasEntry entry, double ingredientQuality,
                                Map<String, Double> qualities) {
        double ingredients = finiteQuality(ingredientQuality, 10);
        if(has(entry, "station:cauldron")) {
            double cauldron = quality(qualities, "station:cauldron");
            double water = quality(qualities, CAULDRON_WATER);
            return (ingredients * 6.0 + cauldron + water) / 8.0;
        }
        if(has(entry, "station:anvil")) {
            double anvil = quality(qualities, "station:anvil");
            double hammer = quality(qualities, "tool:smithy-hammer");
            return (ingredients * 9.0 + anvil * 4.0 + hammer * 3.0) / 16.0;
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
        String value = ((requirement.resource == null ? "" : requirement.resource) + " " +
                (requirement.name == null ? "" : requirement.name)).toLowerCase(Locale.ROOT);
        String prefix = requirement.kind == CraftAtlasEntry.RequirementKind.STATION ? "station:" : "tool:";
        if(value.contains("cauldron")) return "station:cauldron";
        if(value.contains("crucible")) return "station:crucible";
        if(value.contains("anvil")) return "station:anvil";
        if(value.contains("smith") && value.contains("hammer")) return "tool:smithy-hammer";
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

    private static double quality(Map<String, Double> values, String key) {
        return finiteQuality(values == null ? null : values.get(key), 10);
    }

    private static boolean affectsResult(String key) {
        return "station:cauldron".equals(key) || "station:anvil".equals(key) ||
                "tool:smithy-hammer".equals(key);
    }

    private static double finiteQuality(Double value, double fallback) {
        return value != null && Double.isFinite(value) && value >= 1 ? value : fallback;
    }
}
