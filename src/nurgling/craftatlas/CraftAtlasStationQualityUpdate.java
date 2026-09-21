package nurgling.craftatlas;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Maps inspected workstation gobs onto stored Atlas station qualities. */
public final class CraftAtlasStationQualityUpdate {
    public enum Decision { UPDATED, UNCHANGED, IGNORED }

    public static final class Result {
        public final Decision decision;
        public final String key;
        private final Map<String, Double> stored;
        private final Map<String, Double> previous;

        private Result(Decision decision, String key) {
            this(decision, key, null, null);
        }

        private Result(Decision decision, String key, Map<String, Double> stored, Map<String, Double> previous) {
            this.decision = decision;
            this.key = key;
            this.stored = stored;
            this.previous = previous;
        }

        public boolean updated() { return decision == Decision.UPDATED; }

        public void rollback() {
            if(decision != Decision.UPDATED || stored == null || previous == null) return;
            for(Map.Entry<String, Double> entry : previous.entrySet()) {
                if(entry.getValue() == null) stored.remove(entry.getKey());
                else stored.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static final Result IGNORED = new Result(Decision.IGNORED, null);

    private CraftAtlasStationQualityUpdate() { }

    /** One-time fallback loader for bundled station keys when Atlas is absent. */
    public static final class CachedStationKeys {
        private Set<String> keys;

        public Set<String> get(java.util.function.Supplier<? extends Iterable<CraftAtlasEntry>> bundled) {
            if(keys == null) {
                Iterable<CraftAtlasEntry> entries = bundled == null ? null : bundled.get();
                keys = Collections.unmodifiableSet(stationKeys(entries));
            }
            return keys;
        }
    }

    public static Set<String> stationKeys(Iterable<CraftAtlasEntry> entries) {
        Set<String> keys = new LinkedHashSet<String>();
        // Passive processing stations have no crafting-menu recipe requirements.
        keys.add("station:smelter");
        keys.add("station:stack-furnace");
        keys.add("station:kiln");
        if(entries == null) return keys;
        for(CraftAtlasEntry entry : entries) {
            if(entry == null) continue;
            for(CraftAtlasEntry.Requirement requirement : entry.requirements) {
                if(requirement == null || (requirement.kind != CraftAtlasEntry.RequirementKind.STATION &&
                        requirement.kind != CraftAtlasEntry.RequirementKind.TOOL)) continue;
                addStationKey(keys, CraftAtlasQualityFormula.key(requirement));
                if(CraftAtlasQualityFormula.hasGenericCauldron(
                        CraftAtlasEntry.builder("station", "station").requirement(requirement).build()))
                    keys.add("station:clay-cauldron");
            }
        }
        return keys;
    }

    public static Result apply(String gobResource, double quality, boolean atHome,
                               Set<String> knownStationKeys, Map<String, Double> stored) {
        if(!atHome || gobResource == null || gobResource.trim().isEmpty()
                || !Double.isFinite(quality) || quality < 1
                || knownStationKeys == null || stored == null)
            return IGNORED;
        CraftAtlasEntry.Requirement probe = new CraftAtlasEntry.Requirement(
                CraftAtlasEntry.RequirementKind.STATION, gobResource, null, null);
        if(CraftAtlasQualityFormula.key(probe).startsWith("tool:"))
            return IGNORED;
        String identity = gobIdentity(gobResource);
        String firstMatch = null;
        String firstChanged = null;
        Map<String, Double> previous = new LinkedHashMap<String, Double>();
        for(String key : knownStationKeys) {
            if(!gobSatisfies(identity, key)) continue;
            if(firstMatch == null) firstMatch = key;
            Double current = stored.get(key);
            if(current != null && quality <= current) continue;
            previous.put(key, current);
            stored.put(key, quality);
            if(firstChanged == null) firstChanged = key;
        }
        if(firstMatch == null) return IGNORED;
        if(firstChanged == null) return new Result(Decision.UNCHANGED, firstMatch);
        return new Result(Decision.UPDATED, firstChanged, stored, previous);
    }

    private static void addStationKey(Set<String> keys, String key) {
        if(key != null && key.startsWith("station:")) keys.add(key);
    }

    private static String gobIdentity(String gobResource) {
        String base = lastSegment(gobResource);
        if(base == null) return "";
        base = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if("pow".equals(base)) return "fire";
        if("htable".equals(base)) return "herbalist-table";
        if("tarkiln".equals(base)) return "tar-kiln";
        if("gridiron".equals(base)) return "grid-iron";
        if("swheel".equals(base) || "sswheel".equals(base) || "spinningwheel".equals(base)) return "spinning-wheel";
        if("potterswheel".equals(base)) return "potters-wheel";
        if("winepress".equals(base) || "extractionpress".equals(base)) return "extraction-press";
        if("claycauldron".equals(base)) return "clay-cauldron";
        if("primsmelter".equals(base) || "stackfurnace".equals(base)) return "stack-furnace";
        if("ore-smelter".equals(base)) return "smelter";
        return base;
    }

    private static boolean gobSatisfies(String identity, String key) {
        if(identity == null || identity.isEmpty() || key == null || !key.startsWith("station:"))
            return false;
        String body = key.substring("station:".length());
        if(body.startsWith("wiki-item-")) body = body.substring("wiki-item-".length());
        for(String part : body.split("-or-")) {
            if(identity.equals(part.replaceAll("-(lit|assembled)$", ""))) return true;
        }
        return false;
    }

    private static String lastSegment(String value) {
        if(value == null || value.isEmpty()) return value;
        int cut = Math.max(value.lastIndexOf('/'), value.lastIndexOf(':'));
        return cut >= 0 ? value.substring(cut + 1) : value;
    }
}
