package nurgling.widgets.craftatlas;

import nurgling.craftatlas.CraftAtlasAttributes;
import nurgling.craftatlas.CraftAtlasEntry;
import nurgling.craftatlas.CraftAtlasSearch;
import nurgling.i18n.L10n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Metric columns and deterministic sorting for the expanded Craft Atlas list. */
final class CraftAtlasListTable {
    static final class Column {
        final String id;
        final String label;
        final String tooltip;
        final String iconResource;
        private final ToDoubleFunction<CraftAtlasEntry> extractor;

        Column(String id, String label, String tooltip, ToDoubleFunction<CraftAtlasEntry> extractor) {
            this(id, label, tooltip, null, extractor);
        }

        Column(String id, String label, String tooltip, String iconResource,
               ToDoubleFunction<CraftAtlasEntry> extractor) {
            this.id = id;
            this.label = label;
            this.tooltip = tooltip;
            this.iconResource = iconResource;
            this.extractor = extractor;
        }

        double value(CraftAtlasEntry entry) { return extractor.applyAsDouble(entry); }
    }

    private static final String[][] FOOD = {
            {"Strength", "STR"}, {"Agility", "AGI"}, {"Intelligence", "INT"},
            {"Constitution", "CON"}, {"Perception", "PER"}, {"Charisma", "CHA"},
            {"Dexterity", "DEX"}, {"Will", "WIL"}, {"Psyche", "PSY"}
    };
    private static final String[] CHARACTER_ORDER = {
            "Strength", "Agility", "Intelligence", "Constitution", "Perception", "Charisma",
            "Dexterity", "Will", "Psyche", "Unarmed Combat", "Melee Combat", "Marksmanship",
            "Exploration", "Stealth", "Sewing", "Smithing", "Masonry", "Carpentry", "Cooking",
            "Farming", "Survival", "Lore", "Inventory"
    };
    private static final Map<String, Integer> CHARACTER_ORDER_INDEX = characterOrderIndex();

    private CraftAtlasListTable() { }

    static List<Column> columnsFor(String section, List<CraftAtlasEntry> entries) {
        if("foods".equals(section)) {
            List<Column> result = new ArrayList<>();
            for(String[] stat : FOOD) {
                String normalized = CraftAtlasSearch.normalize(stat[0]);
                if(!hasBonus(entries, normalized)) continue;
                result.add(new Column("food:" + normalized, stat[1], stat[0],
                        CraftAtlasAttributes.resource(stat[0], null),
                        entry -> bonusValue(entry, normalized)));
            }
            return Collections.unmodifiableList(result);
        }
        if("curiosities".equals(section)) {
            return List.of(
                    new Column("curiosity:mental-weight", L10n.get("craft_atlas.table.mental_weight"),
                            L10n.get("craft_atlas.table.mental_weight_tip"),
                            entry -> entry.curiosity == null ? Double.NaN : entry.curiosity.mentalWeight),
                    new Column("curiosity:lp-hour", L10n.get("craft_atlas.table.lp_hour"),
                            L10n.get("craft_atlas.table.lp_hour_tip"),
                            entry -> entry.curiosity == null ? Double.NaN : entry.curiosity.lpPerHour()),
                    new Column("curiosity:lp-hour-weight", L10n.get("craft_atlas.table.lp_hour_weight"),
                            L10n.get("craft_atlas.table.lp_hour_weight_tip"),
                            entry -> entry.curiosity == null ? Double.NaN : entry.curiosity.lpPerHourPerWeight()),
                    new Column("curiosity:study-time", L10n.get("craft_atlas.table.study_time"),
                            L10n.get("craft_atlas.table.study_time_tip"),
                            entry -> entry.curiosity == null ? Double.NaN : entry.curiosity.studyHours()));
        }
        if("gildings".equals(section)) {
            Map<String, String> names = new LinkedHashMap<>();
            if(entries != null) for(CraftAtlasEntry entry : entries) for(CraftAtlasEntry.Bonus bonus : entry.bonuses) {
                if(bonus.value == null || "gild:chance".equals(bonus.attributeResource)) continue;
                String name = canonicalName(bonus.name);
                names.putIfAbsent(CraftAtlasSearch.normalize(name), name);
            }
            List<Map.Entry<String, String>> ordered = new ArrayList<>(names.entrySet());
            ordered.sort(Comparator
                    .comparingInt((Map.Entry<String, String> value) -> characterOrder(value.getKey()))
                    .thenComparing(Map.Entry::getValue, String.CASE_INSENSITIVE_ORDER));
            List<Column> result = new ArrayList<>();
            for(Map.Entry<String, String> value : ordered) {
                String normalized = value.getKey();
                String name = value.getValue();
                result.add(new Column("gilding:" + normalized, abbreviation(name), name,
                        bonusIcon(entries, normalized, name),
                        entry -> bonusValue(entry, normalized)));
            }
            return Collections.unmodifiableList(result);
        }
        return Collections.emptyList();
    }

    static List<CraftAtlasEntry> sort(List<CraftAtlasEntry> entries, Column column, boolean descending) {
        List<CraftAtlasEntry> result = new ArrayList<>(entries == null ? Collections.emptyList() : entries);
        if(column == null) return result;
        result.sort((left, right) -> {
            double a = column.value(left), b = column.value(right);
            boolean missingA = !Double.isFinite(a), missingB = !Double.isFinite(b);
            if(missingA != missingB) return missingA ? 1 : -1;
            int compared = missingA ? 0 : Double.compare(a, b);
            if(descending) compared = -compared;
            return compared != 0 ? compared : left.displayName.compareToIgnoreCase(right.displayName);
        });
        return Collections.unmodifiableList(result);
    }

    private static double bonusValue(CraftAtlasEntry entry, String normalizedName) {
        if(entry == null) return Double.NaN;
        for(CraftAtlasEntry.Bonus bonus : entry.bonuses) {
            if(bonus.value == null) continue;
            String name = CraftAtlasSearch.normalize(canonicalName(bonus.name));
            if(normalizedName.equals(name)) return bonus.value;
        }
        return Double.NaN;
    }

    private static String bonusIcon(List<CraftAtlasEntry> entries, String normalizedName, String name) {
        if(entries != null) for(CraftAtlasEntry entry : entries) for(CraftAtlasEntry.Bonus bonus : entry.bonuses) {
            if(!normalizedName.equals(CraftAtlasSearch.normalize(canonicalName(bonus.name)))) continue;
            return CraftAtlasAttributes.resource(name, bonus.attributeResource);
        }
        return CraftAtlasAttributes.resource(name, null);
    }

    private static boolean hasBonus(List<CraftAtlasEntry> entries, String normalizedName) {
        if(entries == null) return false;
        for(CraftAtlasEntry entry : entries)
            if(Double.isFinite(bonusValue(entry, normalizedName))) return true;
        return false;
    }

    private static String canonicalName(String value) {
        String name = CraftAtlasAttributes.baseName(value);
        String normalized = CraftAtlasSearch.normalize(name);
        if("unarmed".equals(normalized) || "unarmed combat".equals(normalized)) return "Unarmed Combat";
        if("inventory".equals(normalized) || "inventory space".equals(normalized) ||
                "inventory spaces".equals(normalized) || "inventory slots".equals(normalized)) return "Inventory";
        return name;
    }

    private static Map<String, Integer> characterOrderIndex() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for(int index = 0; index < CHARACTER_ORDER.length; index++)
            result.put(CraftAtlasSearch.normalize(CHARACTER_ORDER[index]), index);
        return Collections.unmodifiableMap(result);
    }

    private static int characterOrder(String normalizedName) {
        if("inventory".equals(normalizedName)) return Integer.MAX_VALUE;
        return CHARACTER_ORDER_INDEX.getOrDefault(normalizedName, CHARACTER_ORDER.length - 1);
    }

    private static String abbreviation(String name) {
        for(String[] stat : FOOD) if(stat[0].equalsIgnoreCase(name)) return stat[1];
        String compact = name == null ? "" : name.replaceAll("[^\\p{L}\\p{Nd}]", "");
        if(compact.length() <= 5) return compact.toUpperCase(Locale.ROOT);
        return compact.substring(0, 3).toUpperCase(Locale.ROOT);
    }
}
