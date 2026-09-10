package nurgling.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class HomeInteriorRegistry {
    public static final int VERSION = 1;
    private static final HomeInteriorRegistry EMPTY =
            new HomeInteriorRegistry(Collections.<String, Binding>emptyMap(),
                    Collections.<PortalIdentity>emptySet());

    private final Map<String, Binding> byId;
    private final Set<PortalIdentity> suppressedPortals;

    private HomeInteriorRegistry(Map<String, Binding> byId, Set<PortalIdentity> suppressedPortals) {
        this.byId = byId;
        this.suppressedPortals = suppressedPortals;
    }

    public static HomeInteriorRegistry empty() {
        return EMPTY;
    }

    public Collection<Binding> bindings() {
        return byId.values();
    }

    public HomeInteriorRegistry put(Binding binding) {
        Objects.requireNonNull(binding, "binding");
        LinkedHashMap<String, Binding> next = copyBindings();
        next.put(binding.id, binding);
        return new HomeInteriorRegistry(Collections.unmodifiableMap(next), suppressedPortals);
    }

    public HomeInteriorRegistry remove(String bindingId, boolean suppressAutomatic) {
        if (bindingId == null || !byId.containsKey(bindingId))
            return this;
        Binding binding = byId.get(bindingId);
        LinkedHashMap<String, Binding> next = copyBindings();
        next.remove(bindingId);
        Set<PortalIdentity> suppressed = suppressedPortals;
        if (suppressAutomatic && binding != null && binding.rootPortal != null)
            suppressed = withSuppressed(binding.rootPortal);
        return new HomeInteriorRegistry(Collections.unmodifiableMap(next), suppressed);
    }

    public HomeInteriorRegistry applyRemovals(Collection<String> bindingIds) {
        if (bindingIds == null)
            return this;
        HomeInteriorRegistry result = this;
        for (String bindingId : bindingIds)
            result = result.remove(bindingId, true);
        return result;
    }

    public Binding find(String bindingId) {
        return bindingId == null ? null : byId.get(bindingId);
    }

    public HomeInteriorRegistry markManual(long instanceId, Collection<Long> gridIds, String displayName) {
        Binding existing = bindingForInstance(instanceId);
        LinkedHashSet<Long> grids = new LinkedHashSet<Long>();
        if (existing != null)
            grids.addAll(existing.gridIds);
        if (gridIds != null)
            grids.addAll(gridIds);
        Binding marked = new Binding(
                existing != null ? existing.id : ("manual:" + instanceId),
                instanceId,
                grids,
                existing != null ? existing.origins : Collections.<OriginKey>emptySet(),
                existing != null ? existing.rootPortal : null,
                true,
                displayName != null ? displayName : (existing != null ? existing.displayName : ""),
                existing != null ? existing.lastSeen : 0L);
        return put(marked);
    }

    public HomeInteriorRegistry unmarkManual(long instanceId) {
        Binding existing = bindingForInstance(instanceId);
        if (existing == null || !existing.manual)
            return this;
        if (existing.origins.isEmpty())
            return remove(existing.id, false);
        return put(new Binding(existing.id, existing.instanceId, existing.gridIds, existing.origins,
                existing.rootPortal, false, existing.displayName, existing.lastSeen));
    }

    public Binding findActive(long gridId, long instanceId, Collection<HomeTerritories.Entry> saved) {
        Binding byGrid = null;
        Binding byInstance = null;
        for (Binding binding : byId.values()) {
            if (!binding.active(saved))
                continue;
            if (byGrid == null && binding.gridIds.contains(gridId))
                byGrid = binding;
            if (byInstance == null
                    && nurgling.navigation.ChunkNavManager.isInteriorInstanceId(instanceId)
                    && binding.instanceId == instanceId)
                byInstance = binding;
        }
        return byGrid != null ? byGrid : byInstance;
    }

    public boolean isSuppressed(PortalIdentity portal) {
        return portal != null && suppressedPortals.contains(portal);
    }

    public HomeInteriorRegistry clearSuppression(PortalIdentity portal) {
        if (portal == null || !suppressedPortals.contains(portal))
            return this;
        LinkedHashSet<PortalIdentity> next = new LinkedHashSet<PortalIdentity>(suppressedPortals);
        next.remove(portal);
        return new HomeInteriorRegistry(byId, Collections.unmodifiableSet(next));
    }

    public static Map<String, Object> encodeForWorld(Object stored, String genus,
            HomeInteriorRegistry registry) {
        Map<String, Object> worlds = new LinkedHashMap<String, Object>();
        if (stored instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> item : ((Map<?, ?>) stored).entrySet()) {
                if (item.getKey() instanceof String)
                    worlds.put((String) item.getKey(), item.getValue());
            }
        }
        worlds.put(worldKey(genus), encode(registry == null ? EMPTY : registry));
        return worlds;
    }

    public static HomeInteriorRegistry decodeForWorld(Object stored, String genus) {
        if (!(stored instanceof Map<?, ?>))
            return EMPTY;
        return decode(((Map<?, ?>) stored).get(worldKey(genus)));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof HomeInteriorRegistry))
            return false;
        HomeInteriorRegistry registry = (HomeInteriorRegistry) other;
        return byId.equals(registry.byId) && suppressedPortals.equals(registry.suppressedPortals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byId, suppressedPortals);
    }

    private LinkedHashMap<String, Binding> copyBindings() {
        return new LinkedHashMap<String, Binding>(byId);
    }

    private Binding bindingForInstance(long instanceId) {
        for (Binding binding : byId.values()) {
            if (binding.instanceId == instanceId)
                return binding;
        }
        return null;
    }

    private Set<PortalIdentity> withSuppressed(PortalIdentity portal) {
        if (suppressedPortals.contains(portal))
            return suppressedPortals;
        LinkedHashSet<PortalIdentity> next = new LinkedHashSet<PortalIdentity>(suppressedPortals);
        next.add(portal);
        return Collections.unmodifiableSet(next);
    }

    private static String worldKey(String genus) {
        return genus == null ? "" : genus;
    }

    private static Map<String, Object> encode(HomeInteriorRegistry registry) {
        Map<String, Object> stored = new LinkedHashMap<String, Object>();
        stored.put("version", VERSION);
        List<String> ids = new ArrayList<String>(registry.byId.keySet());
        Collections.sort(ids);
        List<Object> bindings = new ArrayList<Object>();
        for (String id : ids)
            bindings.add(encodeBinding(registry.byId.get(id)));
        stored.put("bindings", bindings);
        List<PortalIdentity> suppressed = new ArrayList<PortalIdentity>(registry.suppressedPortals);
        Collections.sort(suppressed, (left, right) -> left.stableKey().compareTo(right.stableKey()));
        List<Object> portals = new ArrayList<Object>();
        for (PortalIdentity portal : suppressed)
            portals.add(encodePortal(portal));
        stored.put("suppressedPortals", portals);
        return stored;
    }

    private static HomeInteriorRegistry decode(Object stored) {
        if (!(stored instanceof Map<?, ?>))
            return EMPTY;
        Map<?, ?> values = (Map<?, ?>) stored;
        Integer version = decodeInt(values.get("version"));
        if (version != null && version > VERSION)
            return EMPTY;
        LinkedHashMap<String, Binding> bindings = new LinkedHashMap<String, Binding>();
        Object bindingsValue = values.get("bindings");
        if (bindingsValue instanceof Collection<?>) {
            for (Object item : (Collection<?>) bindingsValue) {
                Binding binding = decodeBinding(item);
                if (binding != null)
                    bindings.put(binding.id, binding);
            }
        }
        LinkedHashSet<PortalIdentity> suppressed = new LinkedHashSet<PortalIdentity>();
        Object suppressedValue = values.get("suppressedPortals");
        if (suppressedValue instanceof Collection<?>) {
            for (Object item : (Collection<?>) suppressedValue) {
                PortalIdentity portal = decodePortal(item);
                if (portal != null)
                    suppressed.add(portal);
            }
        }
        if (bindings.isEmpty() && suppressed.isEmpty())
            return EMPTY;
        return new HomeInteriorRegistry(Collections.unmodifiableMap(bindings),
                Collections.unmodifiableSet(suppressed));
    }

    private static Map<String, Object> encodeBinding(Binding binding) {
        Map<String, Object> stored = new LinkedHashMap<String, Object>();
        stored.put("id", binding.id);
        stored.put("instanceId", Long.toString(binding.instanceId));
        TreeSet<Long> grids = new TreeSet<Long>(binding.gridIds);
        List<Object> gridIds = new ArrayList<Object>();
        for (Long gridId : grids)
            gridIds.add(Long.toString(gridId));
        stored.put("gridIds", gridIds);
        TreeSet<String> originValues = new TreeSet<String>();
        for (OriginKey origin : binding.origins)
            originValues.add(origin.value());
        stored.put("origins", new ArrayList<String>(originValues));
        if (binding.rootPortal != null)
            stored.put("rootPortal", encodePortal(binding.rootPortal));
        stored.put("manual", binding.manual);
        stored.put("displayName", binding.displayName);
        stored.put("lastSeen", Long.toString(binding.lastSeen));
        return stored;
    }

    private static Binding decodeBinding(Object stored) {
        if (!(stored instanceof Map<?, ?>))
            return null;
        Map<?, ?> values = (Map<?, ?>) stored;
        Object idValue = values.get("id");
        if (!(idValue instanceof String) || ((String) idValue).isEmpty())
            return null;
        LinkedHashSet<Long> gridIds = new LinkedHashSet<Long>();
        Object gridsValue = values.get("gridIds");
        if (gridsValue instanceof Collection<?>) {
            for (Object gridValue : (Collection<?>) gridsValue) {
                Long gridId = decodeLong(gridValue);
                if (gridId != null)
                    gridIds.add(gridId);
            }
        }
        LinkedHashSet<OriginKey> origins = new LinkedHashSet<OriginKey>();
        Object originsValue = values.get("origins");
        if (originsValue instanceof Collection<?>) {
            for (Object originValue : (Collection<?>) originsValue) {
                if (!(originValue instanceof String))
                    continue;
                try {
                    origins.add(OriginKey.parse((String) originValue));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        Object manualValue = values.get("manual");
        boolean manual = manualValue instanceof Boolean && (Boolean) manualValue;
        Object nameValue = values.get("displayName");
        String displayName = nameValue instanceof String ? (String) nameValue : "";
        Long instanceId = decodeLong(values.get("instanceId"));
        Long lastSeen = decodeLong(values.get("lastSeen"));
        return new Binding((String) idValue, instanceId == null ? 0L : instanceId, gridIds, origins,
                decodePortal(values.get("rootPortal")), manual, displayName,
                lastSeen == null ? 0L : lastSeen);
    }

    private static Map<String, Object> encodePortal(PortalIdentity portal) {
        Map<String, Object> stored = new LinkedHashMap<String, Object>();
        stored.put("gridId", Long.toString(portal.gridId));
        stored.put("x", portal.x);
        stored.put("y", portal.y);
        stored.put("resource", portal.resource);
        return stored;
    }

    private static PortalIdentity decodePortal(Object stored) {
        if (!(stored instanceof Map<?, ?>))
            return null;
        Map<?, ?> values = (Map<?, ?>) stored;
        Long gridId = decodeLong(values.get("gridId"));
        Integer x = decodeInt(values.get("x"));
        Integer y = decodeInt(values.get("y"));
        Object resourceValue = values.get("resource");
        if (gridId == null || x == null || y == null || !(resourceValue instanceof String))
            return null;
        return new PortalIdentity(gridId, x, y, (String) resourceValue);
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
        if (value instanceof Number)
            return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public static final class OriginKey {
        private final String value;

        private OriginKey(String value) {
            this.value = value;
        }

        public static OriginKey from(HomeTerritories.Entry entry) {
            Objects.requireNonNull(entry, "entry");
            if (entry.type == HomeTerritories.Type.VILLAGE)
                return parse("village:" + normalize(entry.name));
            if (entry.area != null)
                return parse("claim-anchor:" + entry.area.anchor.gridId + ":"
                        + entry.area.anchor.x + ":" + entry.area.anchor.y);
            return parse("claim-owner:" + normalize(entry.name));
        }

        public static OriginKey parse(String value) {
            if (value == null)
                throw new IllegalArgumentException("origin");
            if (value.startsWith("village:"))
                return new OriginKey("village:" + normalize(value.substring("village:".length())));
            if (value.startsWith("claim-owner:"))
                return new OriginKey("claim-owner:" + normalize(value.substring("claim-owner:".length())));
            if (value.startsWith("claim-anchor:")) {
                String rest = value.substring("claim-anchor:".length());
                int last = rest.lastIndexOf(':');
                int middle = last > 0 ? rest.lastIndexOf(':', last - 1) : -1;
                if (middle <= 0 || last <= middle)
                    throw new IllegalArgumentException(value);
                try {
                    long gridId = Long.parseLong(rest.substring(0, middle));
                    int x = Integer.parseInt(rest.substring(middle + 1, last));
                    int y = Integer.parseInt(rest.substring(last + 1));
                    return new OriginKey("claim-anchor:" + gridId + ":" + x + ":" + y);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(value);
                }
            }
            throw new IllegalArgumentException(value);
        }

        public String value() {
            return value;
        }

        public boolean matches(Collection<HomeTerritories.Entry> saved) {
            if (saved == null)
                return false;
            for (HomeTerritories.Entry entry : saved) {
                if (entry == null)
                    continue;
                if (value.equals(from(entry).value))
                    return true;
                if (entry.type == HomeTerritories.Type.CLAIM && !entry.name.isEmpty()
                        && value.equals("claim-owner:" + normalize(entry.name)))
                    return true;
            }
            return false;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof OriginKey))
                return false;
            return value.equals(((OriginKey) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public static final class PortalIdentity {
        public final long gridId;
        public final int x;
        public final int y;
        public final String resource;

        public PortalIdentity(long gridId, int x, int y, String resource) {
            this.gridId = gridId;
            this.x = x;
            this.y = y;
            this.resource = Objects.requireNonNull(resource, "resource");
        }

        public String stableKey() {
            return gridId + ":" + x + ":" + y + ":" + resource;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof PortalIdentity))
                return false;
            PortalIdentity portal = (PortalIdentity) other;
            return gridId == portal.gridId && x == portal.x && y == portal.y
                    && resource.equals(portal.resource);
        }

        @Override
        public int hashCode() {
            return Objects.hash(gridId, x, y, resource);
        }
    }

    public static final class Binding {
        public final String id;
        public final long instanceId;
        public final Set<Long> gridIds;
        public final Set<OriginKey> origins;
        public final PortalIdentity rootPortal;
        public final boolean manual;
        public final String displayName;
        public final long lastSeen;

        private Binding(String id, long instanceId, Collection<Long> gridIds,
                Collection<OriginKey> origins, PortalIdentity rootPortal, boolean manual,
                String displayName, long lastSeen) {
            this.id = Objects.requireNonNull(id, "id");
            this.instanceId = instanceId;
            LinkedHashSet<Long> grids = new LinkedHashSet<Long>();
            if (gridIds != null)
                grids.addAll(gridIds);
            this.gridIds = Collections.unmodifiableSet(grids);
            LinkedHashSet<OriginKey> originKeys = new LinkedHashSet<OriginKey>();
            if (origins != null)
                originKeys.addAll(origins);
            this.origins = Collections.unmodifiableSet(originKeys);
            this.rootPortal = rootPortal;
            this.manual = manual;
            this.displayName = displayName == null ? "" : displayName;
            this.lastSeen = lastSeen;
        }

        public static Binding automatic(String id, long instanceId, Collection<Long> gridIds,
                Collection<OriginKey> origins, PortalIdentity rootPortal,
                String displayName, long lastSeen) {
            return new Binding(id, instanceId, gridIds, origins,
                    Objects.requireNonNull(rootPortal, "rootPortal"), false, displayName, lastSeen);
        }

        public Binding merge(Binding incoming) {
            if (incoming == null)
                return this;
            LinkedHashSet<Long> grids = new LinkedHashSet<Long>(gridIds);
            grids.addAll(incoming.gridIds);
            LinkedHashSet<OriginKey> mergedOrigins = new LinkedHashSet<OriginKey>(origins);
            mergedOrigins.addAll(incoming.origins);
            String name = incoming.displayName.isEmpty() ? displayName : incoming.displayName;
            long keptInstance = nurgling.navigation.ChunkNavManager.isInteriorInstanceId(instanceId)
                    ? instanceId : incoming.instanceId;
            return new Binding(id, keptInstance, grids,
                    mergedOrigins, rootPortal != null ? rootPortal : incoming.rootPortal,
                    manual || incoming.manual, name, Math.max(lastSeen, incoming.lastSeen));
        }

        public String sourceLabel(Collection<HomeTerritories.Entry> saved) {
            if (!displayName.isEmpty())
                return displayName;
            return activeOriginNames(saved);
        }

        public String activeOriginNames(Collection<HomeTerritories.Entry> saved) {
            Collection<HomeTerritories.Entry> homes = saved == null
                    ? Collections.<HomeTerritories.Entry>emptyList() : saved;
            TreeSet<String> names = new TreeSet<String>();
            for (OriginKey origin : origins) {
                if (origin == null || !origin.matches(homes))
                    continue;
                for (HomeTerritories.Entry entry : homes) {
                    if (entry != null && origin.matches(Collections.singletonList(entry))) {
                        String name = entry.displayName();
                        if (name != null && !name.isEmpty())
                            names.add(name);
                    }
                }
            }
            if (names.isEmpty())
                return "";
            StringBuilder text = new StringBuilder();
            for (String name : names) {
                if (text.length() > 0)
                    text.append(", ");
                text.append(name);
            }
            return text.toString();
        }

        public boolean active(Collection<HomeTerritories.Entry> saved) {
            if (manual)
                return true;
            for (OriginKey origin : origins) {
                if (origin.matches(saved))
                    return true;
            }
            return false;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof Binding))
                return false;
            Binding binding = (Binding) other;
            return instanceId == binding.instanceId && manual == binding.manual
                    && lastSeen == binding.lastSeen && id.equals(binding.id)
                    && gridIds.equals(binding.gridIds) && origins.equals(binding.origins)
                    && Objects.equals(rootPortal, binding.rootPortal)
                    && displayName.equals(binding.displayName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, instanceId, gridIds, origins, rootPortal, manual, displayName, lastSeen);
        }
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
