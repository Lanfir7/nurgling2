package nurgling.craftatlas;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Search, extended filters and deterministic sorting independent of widgets. */
public final class CraftAtlasSearch {
    private static final Pattern NUMBER_FILTER = Pattern.compile(
            "^([\\p{L}\\p{N}_-]+)(>=|<=|=|>|<)([-+]?\\d+(?:[.,]\\d+)?)([\\p{L}]*)$");
    private static final Map<String, String> METRIC_ALIASES = metricAliases();

    private CraftAtlasSearch() { }

    public static final class Query {
        public final String text, bonusResource, category;
        public final boolean descending, restricted, knownOnly, storedOnly;
        public final Set<String> favorites, restrictedResources, storedItems;
        public final List<String> preferredOrder;

        private Query(Builder b) {
            text = normalize(b.text);
            bonusResource = b.bonusResource;
            descending = b.descending;
            category = normalize(b.category);
            favorites = Collections.unmodifiableSet(new LinkedHashSet<>(b.favorites));
            restrictedResources = Collections.unmodifiableSet(new LinkedHashSet<>(b.restrictedResources));
            restricted = b.restricted;
            knownOnly = b.knownOnly;
            storedOnly = b.storedOnly;
            Set<String> normalizedStoredItems = new LinkedHashSet<>();
            for(String value : b.storedItems) {
                String normalized = normalize(value);
                if(!normalized.isEmpty()) normalizedStoredItems.add(normalized);
            }
            storedItems = Collections.unmodifiableSet(normalizedStoredItems);
            preferredOrder = Collections.unmodifiableList(new ArrayList<>(b.preferredOrder));
        }

        public static Query text(String value) { return builder().text(value).build(); }
        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String text = "", bonusResource, category = "";
            private boolean descending, restricted, knownOnly, storedOnly;
            private Set<String> favorites = Collections.emptySet(), restrictedResources = Collections.emptySet();
            private Set<String> storedItems = Collections.emptySet();
            private List<String> preferredOrder = Collections.emptyList();
            public Builder text(String value) { text = value; return this; }
            public Builder bonus(String value) { bonusResource = value; return this; }
            public Builder descending(boolean value) { descending = value; return this; }
            public Builder category(String value) { category = value; return this; }
            public Builder knownOnly(boolean value) { knownOnly = value; return this; }
            public Builder storedItems(Set<String> value) {
                storedItems = value == null ? Collections.emptySet() : value;
                storedOnly = true;
                return this;
            }
            public Builder favorites(Set<String> value) { favorites = value == null ? Collections.emptySet() : value; return this; }
            public Builder restrictTo(Set<String> value) {
                restrictedResources = value == null ? Collections.emptySet() : value;
                restricted = true;
                return this;
            }
            public Builder preferredOrder(List<String> value) {
                preferredOrder = value == null ? Collections.emptyList() : value;
                return this;
            }
            public Query build() { return new Query(this); }
        }
    }

    private interface EntryFilter { boolean matches(CraftAtlasEntry entry); }

    private static final class Parsed {
        final List<EntryFilter> filters = new ArrayList<>();
        final List<String> plain = new ArrayList<>();
        String sort;
        boolean descending;
    }

    public static List<CraftAtlasEntry> query(CraftAtlasSnapshot snapshot, Query query) {
        if(snapshot == null) return Collections.emptyList();
        Query q = query == null ? Query.text("") : query;
        Parsed parsed = parse(q.text);
        List<CraftAtlasEntry> result = new ArrayList<>();
        for(CraftAtlasEntry entry : snapshot.entries) {
            if(!q.favorites.isEmpty() && !q.favorites.contains(entry.recipeResource)) continue;
            if(q.restricted && !q.restrictedResources.contains(entry.recipeResource)) continue;
            if(q.knownOnly && entry.availability == CraftAtlasEntry.Availability.REFERENCE_ONLY) continue;
            if(q.storedOnly && !q.storedItems.contains(normalize(entry.displayName))) continue;
            if(!q.category.isEmpty() && !normalizedCategories(entry).contains(q.category)) continue;
            String haystack = searchableText(entry);
            boolean matches = true;
            for(String token : parsed.plain) if(!haystack.contains(token)) { matches = false; break; }
            if(!matches) continue;
            for(EntryFilter filter : parsed.filters) if(!filter.matches(entry)) { matches = false; break; }
            if(matches) result.add(entry);
        }
        if(!q.preferredOrder.isEmpty()) {
            Map<String, Integer> order = new LinkedHashMap<>();
            for(int i = 0; i < q.preferredOrder.size(); i++) order.putIfAbsent(q.preferredOrder.get(i), i);
            result.sort(Comparator.comparingInt(entry -> order.getOrDefault(entry.recipeResource, Integer.MAX_VALUE)));
        } else if(q.bonusResource != null) {
            result.sort((a, b) -> compareBonus(a, b, q.bonusResource, q.descending));
        } else if(parsed.sort != null) {
            result.sort(metricComparator(parsed.sort, parsed.descending));
        } else {
            result.sort(Comparator.comparing(entry -> normalize(entry.displayName)));
        }
        return Collections.unmodifiableList(result);
    }

    public static List<String> examplesFor(String section) {
        String category = normalize(section);
        if("foods".equals(category)) return List.of(
                "str>=5 sort:str order:desc", "effect:dexterity", "recipe:true known:true");
        if("gildings".equals(category)) return List.of(
                "chance-min>=40 sewing>=3", "effect:inventory", "sort:chance-max order:desc");
        if("curiosities".equals(category)) return List.of(
                "time>=12h weight<=10", "lph>=500 sort:lph order:desc", "recipe:true known:true");
        if(category.startsWith("equipment")) return List.of(
                "slot:7l", "station:anvil recipe:true", "known:true available:true");
        return List.of("known:true", "recipe:true known:true", "type:food", "sort:name order:asc");
    }

    private static Parsed parse(String text) {
        Parsed parsed = new Parsed();
        for(String raw : tokenize(text)) {
            boolean negate = raw.startsWith("-") && raw.length() > 1;
            String token = negate ? raw.substring(1) : raw;
            EntryFilter filter = fieldFilter(token);
            if(filter == null) filter = numericFilter(token);
            if(filter != null) {
                EntryFilter resolved = filter;
                parsed.filters.add(negate ? entry -> !resolved.matches(entry) : resolved);
                continue;
            }
            int separator = token.indexOf(':');
            if(separator > 0) {
                String key = normalize(token.substring(0, separator));
                String value = normalize(token.substring(separator + 1));
                if("sort".equals(key) && isMetric(value)) { parsed.sort = canonicalMetric(value); continue; }
                if("order".equals(key) && ("desc".equals(value) || "descending".equals(value))) {
                    parsed.descending = true;
                    continue;
                }
                if("order".equals(key) && ("asc".equals(value) || "ascending".equals(value))) {
                    parsed.descending = false;
                    continue;
                }
            }
            parsed.plain.add(normalize(token));
        }
        return parsed;
    }

    private static EntryFilter fieldFilter(String token) {
        int separator = token.indexOf(':');
        if(separator <= 0) return null;
        String key = normalize(token.substring(0, separator));
        String value = normalize(token.substring(separator + 1));
        if(value.isEmpty()) return null;
        switch(key) {
            case "name": return entry -> normalize(entry.displayName).contains(value);
            case "type": case "category": return entry -> matchesCategory(entry, value);
            case "effect": case "bonus": {
                EntryFilter numeric = numericFilter(value);
                return numeric != null ? numeric : entry -> bonusText(entry).contains(value);
            }
            case "slot": return entry -> containsNormalized(entry.equipmentSlots, value);
            case "ingredient": case "material": return entry -> ingredientText(entry).contains(value);
            case "station": return entry -> requirementText(entry, CraftAtlasEntry.RequirementKind.STATION).contains(value);
            case "tool": return entry -> requirementText(entry, CraftAtlasEntry.RequirementKind.TOOL).contains(value);
            case "recipe": case "craft": {
                Boolean expected = booleanValue(value);
                return expected == null ? null : entry -> (!entry.inputs.isEmpty()) == expected;
            }
            case "known": case "learned": {
                Boolean expected = booleanValue(value);
                return expected == null ? null : entry -> (entry.availability != CraftAtlasEntry.Availability.REFERENCE_ONLY) == expected;
            }
            case "available": {
                Boolean expected = booleanValue(value);
                return expected == null ? null : entry -> (entry.availability == CraftAtlasEntry.Availability.OPEN) == expected;
            }
            default: return null;
        }
    }

    private static EntryFilter numericFilter(String token) {
        Matcher match = NUMBER_FILTER.matcher(normalize(token));
        if(!match.matches()) return null;
        String metric = canonicalMetric(match.group(1));
        if(metric == null || "name".equals(metric)) return null;
        double expected;
        try { expected = Double.parseDouble(match.group(3).replace(',', '.')); }
        catch(NumberFormatException ignored) { return null; }
        if("time".equals(metric)) expected = minutes(expected, match.group(4));
        String operator = match.group(2);
        double target = expected;
        return entry -> {
            Double actual = metricValue(entry, metric);
            return actual != null && Double.isFinite(actual) && compare(actual, operator, target);
        };
    }

    private static Comparator<CraftAtlasEntry> metricComparator(String metric, boolean descending) {
        return (left, right) -> {
            if("name".equals(metric)) {
                int compared = normalize(left.displayName).compareTo(normalize(right.displayName));
                return descending ? -compared : compared;
            }
            Double a = metricValue(left, metric), b = metricValue(right, metric);
            boolean missingA = a == null || !Double.isFinite(a), missingB = b == null || !Double.isFinite(b);
            if(missingA != missingB) return missingA ? 1 : -1;
            int compared = missingA ? 0 : Double.compare(a, b);
            if(descending) compared = -compared;
            return compared != 0 ? compared : normalize(left.displayName).compareTo(normalize(right.displayName));
        };
    }

    private static Double metricValue(CraftAtlasEntry entry, String metric) {
        if(entry == null) return null;
        if(entry.gilding != null) switch(metric) {
            case "chance-min": return entry.gilding.pmin * 100.0;
            case "chance-max": return entry.gilding.pmax * 100.0;
        }
        if(entry.curiosity != null) switch(metric) {
            case "lp": return (double)entry.curiosity.learningPoints;
            case "lph": return entry.curiosity.lpPerHour();
            case "lphw": return entry.curiosity.lpPerHourPerWeight();
            case "weight": return (double)entry.curiosity.mentalWeight;
            case "time": return (double)entry.curiosity.studyMinutes;
        }
        return bonusValue(entry, metric);
    }

    private static boolean compare(double actual, String operator, double expected) {
        switch(operator) {
            case ">=": return actual >= expected;
            case "<=": return actual <= expected;
            case ">": return actual > expected;
            case "<": return actual < expected;
            default: return Math.abs(actual - expected) < 0.000001;
        }
    }

    private static double minutes(double value, String unit) {
        String normalized = normalize(unit);
        if(Set.of("d", "day", "days", "д").contains(normalized)) return value * 1440;
        if(Set.of("m", "min", "mins", "м").contains(normalized)) return value;
        return value * 60;
    }

    private static String canonicalMetric(String value) { return METRIC_ALIASES.get(normalize(value)); }
    private static boolean isMetric(String value) { return canonicalMetric(value) != null; }

    private static Map<String, String> metricAliases() {
        Map<String, String> result = new LinkedHashMap<>();
        alias(result, "name", "name", "название");
        alias(result, "lp", "lp", "learningpoints", "learning-points");
        alias(result, "lph", "lph", "lp-hour", "lp/h");
        alias(result, "lphw", "lphw", "lp-hour-weight", "lp/h/w");
        alias(result, "weight", "weight", "mw", "mentalweight", "mental-weight", "вес");
        alias(result, "time", "time", "studytime", "study-time", "время");
        alias(result, "chance-min", "chance", "chance-min", "min-chance");
        alias(result, "chance-max", "chance-max", "max-chance");
        alias(result, "strength", "str", "strength");
        alias(result, "agility", "agi", "agility");
        alias(result, "intelligence", "int", "intelligence");
        alias(result, "constitution", "con", "constitution");
        alias(result, "perception", "per", "prc", "perception");
        alias(result, "charisma", "cha", "csm", "charisma");
        alias(result, "dexterity", "dex", "dexterity");
        alias(result, "will", "wil", "will");
        alias(result, "psyche", "psy", "psyche");
        alias(result, "unarmed combat", "uac", "unarmed", "unarmedcombat", "unarmed-combat");
        alias(result, "melee combat", "mc", "melee", "meleecombat", "melee-combat");
        alias(result, "marksmanship", "mm", "marksmanship");
        alias(result, "exploration", "expl", "exploration");
        alias(result, "stealth", "stealth");
        alias(result, "sewing", "sew", "sewing");
        alias(result, "smithing", "smith", "smithing");
        alias(result, "masonry", "mas", "masonry");
        alias(result, "carpentry", "car", "carpentry");
        alias(result, "cooking", "cook", "cooking");
        alias(result, "farming", "farm", "farming");
        alias(result, "survival", "surv", "survival");
        alias(result, "lore", "lore");
        alias(result, "inventory", "inventory", "inventory-space");
        return Collections.unmodifiableMap(result);
    }

    private static void alias(Map<String, String> target, String canonical, String... aliases) {
        for(String alias : aliases) target.put(normalize(alias), canonical);
    }

    private static List<String> tokenize(String text) {
        if(text == null || text.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for(int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if(ch == '"') { quoted = !quoted; continue; }
            if(Character.isWhitespace(ch) && !quoted) {
                if(current.length() > 0) { result.add(current.toString()); current.setLength(0); }
            } else current.append(ch);
        }
        if(current.length() > 0) result.add(current.toString());
        return result;
    }

    private static Boolean booleanValue(String value) {
        if(Set.of("true", "yes", "1", "да").contains(value)) return true;
        if(Set.of("false", "no", "0", "нет").contains(value)) return false;
        return null;
    }

    private static boolean matchesCategory(CraftAtlasEntry entry, String value) {
        String wanted = value;
        if(Set.of("food", "еда").contains(wanted)) wanted = "foods";
        else if(Set.of("gilding", "gild", "гилдинг", "гилдинги").contains(wanted)) wanted = "gildings";
        else if(Set.of("curiosity", "curio", "курик", "курики").contains(wanted)) wanted = "curiosities";
        else if(Set.of("clothes", "clothing", "одежда").contains(wanted)) wanted = "equipment";
        for(String category : entry.categories) {
            String normalized = normalize(category);
            if(normalized.equals(wanted) || "equipment".equals(wanted) && normalized.startsWith("equipment")) return true;
        }
        return false;
    }

    private static boolean containsNormalized(List<String> values, String needle) {
        for(String value : values) if(normalize(value).contains(needle)) return true;
        return false;
    }

    private static String ingredientText(CraftAtlasEntry entry) {
        StringBuilder out = new StringBuilder();
        for(CraftAtlasEntry.InputSlot slot : entry.inputs) for(CraftAtlasEntry.IngredientOption option : slot.options)
            out.append(' ').append(normalize(option.name)).append(' ').append(normalize(option.resource));
        return out.toString();
    }

    private static String requirementText(CraftAtlasEntry entry, CraftAtlasEntry.RequirementKind kind) {
        StringBuilder out = new StringBuilder();
        for(CraftAtlasEntry.Requirement requirement : entry.requirements) if(requirement.kind == kind)
            out.append(' ').append(normalize(requirement.name)).append(' ').append(normalize(requirement.resource));
        return out.toString();
    }

    private static String bonusText(CraftAtlasEntry entry) {
        StringBuilder out = new StringBuilder();
        for(CraftAtlasEntry.Bonus bonus : entry.bonuses)
            out.append(' ').append(normalize(bonus.name)).append(' ').append(normalize(bonus.attributeResource));
        return out.toString();
    }

    private static int compareBonus(CraftAtlasEntry a, CraftAtlasEntry b, String resource, boolean descending) {
        Double av = bonusValueByResource(a, resource), bv = bonusValueByResource(b, resource);
        if(av == null && bv == null) return normalize(a.displayName).compareTo(normalize(b.displayName));
        if(av == null) return 1;
        if(bv == null) return -1;
        int cmp = Double.compare(av, bv);
        if(descending) cmp = -cmp;
        return cmp != 0 ? cmp : normalize(a.displayName).compareTo(normalize(b.displayName));
    }

    private static Double bonusValueByResource(CraftAtlasEntry entry, String resource) {
        for(CraftAtlasEntry.Bonus bonus : entry.bonuses)
            if(resource.equals(bonus.attributeResource)) return bonus.value;
        return null;
    }

    private static Double bonusValue(CraftAtlasEntry entry, String metric) {
        for(CraftAtlasEntry.Bonus bonus : entry.bonuses) {
            if(bonus.value == null) continue;
            String name = canonicalMetric(CraftAtlasAttributes.baseName(bonus.name));
            if(metric.equals(name)) return bonus.value;
        }
        return null;
    }

    private static String normalizedCategories(CraftAtlasEntry entry) {
        StringBuilder out = new StringBuilder();
        for(String category : entry.categories) out.append(' ').append(normalize(category));
        return out.toString();
    }

    private static String searchableText(CraftAtlasEntry entry) {
        return normalize(entry.displayName) + bonusText(entry);
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replace('ё', 'е').trim().replaceAll("\\s+", " ");
    }
}
