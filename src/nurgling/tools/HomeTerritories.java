package nurgling.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

public final class HomeTerritories {
    public enum Type {
        CLAIM,
        VILLAGE
    }

    public static final class Entry {
        public final Type type;
        public final String name;
        public final ClaimArea area;

        public Entry(Type type, String name) {
            this(type, name, null);
        }

        public Entry(Type type, String name, ClaimArea area) {
            this.type = Objects.requireNonNull(type);
            this.name = Objects.requireNonNull(name).trim();
            this.area = type == Type.CLAIM ? area : null;
        }

        public String displayName() {
            if (type != Type.CLAIM)
                return name;
            if (!name.isEmpty())
                return name + "'s Claim";
            if (area == null)
                return "Claim";
            return String.format("Claim [%s:%d,%d]", Long.toHexString(area.anchor.gridId),
                    area.anchor.x, area.anchor.y);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof Entry))
                return false;
            Entry entry = (Entry) other;
            return type == entry.type && name.equals(entry.name) && Objects.equals(area, entry.area);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name, area);
        }
    }

    public static final class HomeStatus {
        public final boolean village;
        public final boolean claim;

        private HomeStatus(boolean village, boolean claim) {
            this.village = village;
            this.claim = claim;
        }
    }

    private HomeTerritories() {
    }

    public static List<Entry> merge(Collection<Entry> saved, Collection<Entry> current) {
        List<Entry> result = new ArrayList<>();
        if (saved != null)
            result.addAll(saved);
        if (current != null) {
            for (Entry incoming : current) {
                int match = matchingIndex(result, incoming);
                if (match < 0)
                    result.add(incoming);
                else
                    result.set(match, mergeEntries(result.get(match), incoming));
            }
        }
        return result;
    }

    public static List<Entry> matchingHomes(Collection<Entry> saved,
            Collection<Entry> current, ClaimArea currentClaimArea) {
        LinkedHashSet<Entry> matched = new LinkedHashSet<Entry>();
        if (saved == null)
            return new ArrayList<Entry>();
        if (current != null) {
            for (Entry here : current) {
                if (here == null)
                    continue;
                for (Entry home : saved) {
                    if (home == null || home.type != here.type)
                        continue;
                    if (here.type == Type.VILLAGE && namesMatch(home.name, here.name))
                        matched.add(home);
                    if (here.type == Type.CLAIM && !home.name.isEmpty()
                            && namesMatch(home.name, here.name))
                        matched.add(home);
                }
            }
        }
        if (currentClaimArea != null) {
            for (Entry home : saved) {
                if (home != null && home.type == Type.CLAIM && home.area != null
                        && home.area.overlaps(currentClaimArea))
                    matched.add(home);
            }
        }
        return new ArrayList<Entry>(matched);
    }

    public static HomeStatus status(Collection<Entry> saved, Collection<Entry> current,
                                    ClaimArea currentClaimArea) {
        boolean village = false;
        boolean claim = false;
        for (Entry home : matchingHomes(saved, current, currentClaimArea)) {
            if (home.type == Type.VILLAGE)
                village = true;
            else if (home.type == Type.CLAIM)
                claim = true;
        }
        return new HomeStatus(village, claim);
    }

    private static boolean namesMatch(String saved, String current) {
        return saved.toLowerCase(Locale.ROOT).equals(current.toLowerCase(Locale.ROOT));
    }

    /** Adds the separately captured personal claim to other detected territories. */
    public static List<Entry> withCurrentClaim(Collection<Entry> detected,
                                               Collection<Entry> saved,
                                               ClaimArea currentArea,
                                               String knownOwner) {
        List<Entry> result = new ArrayList<>();
        String name = knownOwner == null ? "" : knownOwner.trim();
        if (detected != null) {
            for (Entry entry : detected) {
                if (entry == null)
                    continue;
                if (entry.type == Type.CLAIM) {
                    if (name.isEmpty())
                        name = entry.name;
                } else {
                    result.add(entry);
                }
            }
        }
        Entry matched = null;
        if (saved != null && currentArea != null) {
            for (Entry entry : saved) {
                if (entry != null && entry.type == Type.CLAIM && entry.area != null
                        && entry.area.overlaps(currentArea)) {
                    matched = entry;
                    break;
                }
            }
        }
        if (matched != null) {
            if (name.isEmpty())
                name = matched.name;
            currentArea = matched.area.merge(currentArea);
        }
        if (currentArea != null)
            result.add(new Entry(Type.CLAIM, name, currentArea));
        return result;
    }

    private static int matchingIndex(List<Entry> entries, Entry candidate) {
        for (int i = 0; i < entries.size(); i++) {
            if (sameTerritory(entries.get(i), candidate))
                return i;
        }
        return -1;
    }

    private static boolean sameTerritory(Entry first, Entry second) {
        if (first == null || second == null || first.type != second.type)
            return false;
        if (first.type == Type.VILLAGE)
            return first.name.equals(second.name);
        if (first.area != null && second.area != null)
            return first.area.overlaps(second.area);
        return !first.name.isEmpty() && first.name.equals(second.name);
    }

    private static Entry mergeEntries(Entry saved, Entry current) {
        String name = !saved.name.isEmpty() ? saved.name : current.name;
        ClaimArea area = saved.area == null ? current.area
                : (current.area == null ? saved.area : saved.area.merge(current.area));
        return new Entry(saved.type, name, area);
    }

    /** Applies edits made from a snapshot while preserving homes added concurrently. */
    public static List<Entry> applyEdits(Collection<Entry> current,
                                         Collection<Entry> baseline,
                                         Collection<Entry> edited) {
        List<Entry> result = merge(null, current);
        Collection<Entry> baselineEntries = baseline == null
                ? java.util.Collections.emptyList() : baseline;
        Collection<Entry> editedEntries = edited == null
                ? java.util.Collections.emptyList() : edited;
        for (Entry original : baselineEntries) {
            if (!containsTerritory(editedEntries, original))
                result.removeIf(entry -> sameTerritory(entry, original));
        }
        for (Entry entry : editedEntries) {
            if (!containsTerritory(baselineEntries, entry))
                result = merge(result, java.util.Collections.singletonList(entry));
        }
        return result;
    }

    private static boolean containsTerritory(Collection<Entry> entries, Entry territory) {
        for (Entry entry : entries) {
            if (sameTerritory(entry, territory))
                return true;
        }
        return false;
    }

    public static ArrayList<Object> encode(Collection<Entry> entries) {
        ArrayList<Object> result = new ArrayList<>();
        if (entries == null)
            return result;
        for (Entry entry : entries) {
            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("type", entry.type.name());
            stored.put("name", entry.name);
            if (entry.area != null)
                stored.put("area", encodeArea(entry.area));
            result.add(stored);
        }
        return result;
    }

    public static List<Entry> decode(Object stored) {
        Set<Entry> result = new LinkedHashSet<>();
        if (!(stored instanceof Collection<?>))
            return new ArrayList<>();
        for (Object item : (Collection<?>) stored) {
            if (!(item instanceof Map<?, ?>))
                continue;
            Map<?, ?> values = (Map<?, ?>) item;
            Object typeValue = values.get("type");
            Object nameValue = values.get("name");
            if (!(typeValue instanceof String) || !(nameValue instanceof String))
                continue;
            String name = ((String) nameValue).trim();
            try {
                Type type = Type.valueOf((String) typeValue);
                ClaimArea area = decodeArea(values.get("area"));
                if (name.isEmpty() && (type != Type.CLAIM || area == null))
                    continue;
                result.add(new Entry(type, name, area));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new ArrayList<>(result);
    }

    private static Map<String, Object> encodeArea(ClaimArea area) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("anchor", encodeTile(area.anchor));
        Map<Long, List<Integer>> byGrid = new TreeMap<>();
        for (ClaimArea.Tile tile : area.tiles())
            byGrid.computeIfAbsent(tile.gridId, ignored -> new ArrayList<>())
                    .add(tile.x + (tile.y * 100));
        List<Object> grids = new ArrayList<>();
        for (Map.Entry<Long, List<Integer>> group : byGrid.entrySet()) {
            java.util.Collections.sort(group.getValue());
            List<Object> runs = new ArrayList<>();
            int start = group.getValue().get(0);
            int end = start;
            for (int i = 1; i < group.getValue().size(); i++) {
                int value = group.getValue().get(i);
                if (value == end + 1) {
                    end = value;
                } else {
                    runs.add(start);
                    runs.add(end);
                    start = end = value;
                }
            }
            runs.add(start);
            runs.add(end);
            Map<String, Object> encodedGrid = new LinkedHashMap<>();
            encodedGrid.put("id", Long.toString(group.getKey()));
            encodedGrid.put("runs", runs);
            grids.add(encodedGrid);
        }
        result.put("grids", grids);
        return result;
    }

    private static Map<String, Object> encodeTile(ClaimArea.Tile tile) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("grid", Long.toString(tile.gridId));
        result.put("x", tile.x);
        result.put("y", tile.y);
        return result;
    }

    private static ClaimArea decodeArea(Object stored) {
        if (!(stored instanceof Map<?, ?>))
            return null;
        Map<?, ?> values = (Map<?, ?>) stored;
        ClaimArea.Tile anchor = decodeTile(values.get("anchor"));
        Object gridsValue = values.get("grids");
        if (anchor == null || !(gridsValue instanceof Collection<?>))
            return null;
        List<ClaimArea.Tile> tiles = new ArrayList<>();
        for (Object gridValue : (Collection<?>) gridsValue) {
            if (!(gridValue instanceof Map<?, ?>))
                continue;
            Map<?, ?> grid = (Map<?, ?>) gridValue;
            Long id = decodeLong(grid.get("id"));
            Object runsValue = grid.get("runs");
            if (id == null || !(runsValue instanceof List<?>))
                continue;
            List<?> runs = (List<?>) runsValue;
            for (int i = 0; i + 1 < runs.size(); i += 2) {
                Integer start = decodeInt(runs.get(i));
                Integer end = decodeInt(runs.get(i + 1));
                if (start == null || end == null || start < 0 || end < start || end >= 10000)
                    continue;
                for (int tile = start; tile <= end; tile++)
                    tiles.add(new ClaimArea.Tile(id, tile % 100, tile / 100));
            }
        }
        return new ClaimArea(anchor, tiles);
    }

    private static ClaimArea.Tile decodeTile(Object stored) {
        if (!(stored instanceof Map<?, ?>))
            return null;
        Map<?, ?> values = (Map<?, ?>) stored;
        Long grid = decodeLong(values.get("grid"));
        Integer x = decodeInt(values.get("x"));
        Integer y = decodeInt(values.get("y"));
        if (grid == null || x == null || y == null || x < 0 || x >= 100 || y < 0 || y >= 100)
            return null;
        return new ClaimArea.Tile(grid, x, y);
    }

    private static Long decodeLong(Object value) {
        try {
            if (value instanceof Number)
                return ((Number) value).longValue();
            if (value instanceof String)
                return Long.parseLong((String) value);
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static Integer decodeInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    public static Map<String, Object> encodeForWorld(Object stored, String genus, Collection<Entry> entries) {
        Map<String, Object> worlds = new LinkedHashMap<>();
        if (stored instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> item : ((Map<?, ?>) stored).entrySet()) {
                if (item.getKey() instanceof String)
                    worlds.put((String) item.getKey(), item.getValue());
            }
        }
        worlds.put(worldKey(genus), encode(entries));
        return worlds;
    }

    public static List<Entry> decodeForWorld(Object stored, String genus) {
        if (!(stored instanceof Map<?, ?>))
            return new ArrayList<>();
        return decode(((Map<?, ?>) stored).get(worldKey(genus)));
    }

    private static String worldKey(String genus) {
        return genus == null ? "" : genus;
    }

    /**
     * Matches the server's owner names to the territory overlays active under the player.
     * The numeric keys of {@code GameUI.polowners} are message identifiers, not resource ids.
     */
    public static List<Entry> classifyOwners(Collection<String> owners,
                                             Collection<Type> activeTypes,
                                             Collection<String> knownVillages,
                                             Collection<String> knownRealms,
                                             String characterName,
                                             Collection<Entry> saved) {
        return classifyOwners(owners, activeTypes, knownVillages, knownRealms,
                characterName, saved, true);
    }

    public static List<Entry> classifyOwners(Collection<String> owners,
                                             Collection<Type> activeTypes,
                                             Collection<String> knownVillages,
                                             Collection<String> knownRealms,
                                             String characterName,
                                             Collection<Entry> saved,
                                             boolean allowFallback) {
        Set<Type> types = new LinkedHashSet<>();
        if (activeTypes != null)
            types.addAll(activeTypes);
        if (types.isEmpty())
            return new ArrayList<>();

        Set<String> villages = cleanNames(knownVillages);
        Set<String> realms = cleanNames(knownRealms);
        Set<String> candidates = cleanNames(owners);
        candidates.removeAll(realms);
        if (candidates.isEmpty())
            return new ArrayList<>();

        Map<Type, String> assigned = new LinkedHashMap<>();
        if (types.contains(Type.VILLAGE))
            assignFirst(assigned, Type.VILLAGE, candidates, villages);
        if (types.contains(Type.CLAIM)) {
            String character = cleanName(characterName);
            if (character != null && candidates.contains(character))
                assigned.put(Type.CLAIM, character);
        }
        if (saved != null) {
            for (Entry entry : saved) {
                if (entry != null && types.contains(entry.type) && !assigned.containsKey(entry.type)
                        && candidates.contains(entry.name))
                    assigned.put(entry.type, entry.name);
            }
        }

        Set<String> used = new LinkedHashSet<>(assigned.values());
        List<Type> remainingTypes = new ArrayList<>();
        List<String> remainingNames = new ArrayList<>();
        for (Type type : types) {
            if (!assigned.containsKey(type))
                remainingTypes.add(type);
        }
        for (String candidate : candidates) {
            if (!used.contains(candidate))
                remainingNames.add(candidate);
        }
        if (allowFallback && remainingTypes.size() == 1 && remainingNames.size() == 1) {
            for (Type type : remainingTypes) {
                String fallback = null;
                for (String candidate : remainingNames) {
                    if (used.contains(candidate))
                        continue;
                    if (type == Type.CLAIM && villages.contains(candidate))
                        continue;
                    fallback = candidate;
                    break;
                }
                if (fallback != null) {
                    assigned.put(type, fallback);
                    used.add(fallback);
                }
            }
        }

        List<Entry> result = new ArrayList<>();
        for (Type type : types) {
            String name = assigned.get(type);
            if (name != null)
                result.add(new Entry(type, name));
        }
        return result;
    }

    private static void assignFirst(Map<Type, String> assigned, Type type,
                                    Collection<String> candidates, Collection<String> preferred) {
        for (String candidate : candidates) {
            if (preferred.contains(candidate)) {
                assigned.put(type, candidate);
                return;
            }
        }
    }

    private static Set<String> cleanNames(Collection<String> names) {
        Set<String> result = new LinkedHashSet<>();
        if (names != null) {
            for (String name : names) {
                String cleaned = cleanName(name);
                if (cleaned != null)
                    result.add(cleaned);
            }
        }
        return result;
    }

    private static String cleanName(String name) {
        if (name == null)
            return null;
        String cleaned = name.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    public static List<Entry> detect(Map<Integer, String> owners,
                                     Function<Integer, ? extends Collection<String>> tagsById) {
        Set<Entry> result = new LinkedHashSet<>();
        if (owners == null || tagsById == null)
            return new ArrayList<>();
        for (Map.Entry<Integer, String> owner : owners.entrySet()) {
            if (owner.getValue() == null || owner.getValue().trim().isEmpty())
                continue;
            Collection<String> tags = tagsById.apply(owner.getKey());
            if (tags == null)
                continue;
            if (tags.contains("cplot"))
                result.add(new Entry(Type.CLAIM, owner.getValue()));
            if (tags.contains("vlg"))
                result.add(new Entry(Type.VILLAGE, owner.getValue()));
        }
        return new ArrayList<>(result);
    }
}
