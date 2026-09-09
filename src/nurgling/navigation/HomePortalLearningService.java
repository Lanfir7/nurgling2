package nurgling.navigation;

import haven.Coord;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.tools.CurrentHomeTerritories;
import nurgling.tools.HomeInteriorRegistry;
import nurgling.tools.HomeInteriorStore;
import nurgling.tools.HomeTerritories;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class HomePortalLearningService {
    public interface RegistryAccess {
        HomeInteriorRegistry load(String genus);

        HomeInteriorRegistry update(String genus, UnaryOperator<HomeInteriorRegistry> updater);
    }

    public interface SourceContextSupplier {
        HomePortalInheritance.SourceContext capture(long sourceGridId, int portalX, int portalY);
    }

    public static final class Pending {
        public final long sourceGridId;
        public final Coord portalCoord;
        public final String portalResource;
        public final long sourceInstanceId;
        public final String sourceLayer;
        public final HomePortalInheritance.SourceContext source;

        Pending(long sourceGridId, Coord portalCoord, String portalResource,
                long sourceInstanceId, String sourceLayer,
                HomePortalInheritance.SourceContext source) {
            this.sourceGridId = sourceGridId;
            this.portalCoord = portalCoord;
            this.portalResource = portalResource;
            this.sourceInstanceId = sourceInstanceId;
            this.sourceLayer = sourceLayer;
            this.source = source == null ? HomePortalInheritance.SourceContext.notHome() : source;
        }
    }

    private static final RegistryAccess NO_STORE = new RegistryAccess() {
        @Override
        public HomeInteriorRegistry load(String genus) {
            return HomeInteriorRegistry.empty();
        }

        @Override
        public HomeInteriorRegistry update(String genus,
                UnaryOperator<HomeInteriorRegistry> updater) {
            return HomeInteriorRegistry.empty();
        }
    };

    private static final RegistryAccess CONFIG_STORE = new RegistryAccess() {
        @Override
        public HomeInteriorRegistry load(String genus) {
            return HomeInteriorStore.load(genus);
        }

        @Override
        public HomeInteriorRegistry update(String genus,
                UnaryOperator<HomeInteriorRegistry> updater) {
            return HomeInteriorStore.update(genus, updater);
        }
    };

    private final boolean disabled;
    private final Supplier<String> genus;
    private final RegistryAccess store;
    private final SourceContextSupplier contexts;
    private final Supplier<Collection<HomeTerritories.Entry>> savedHomes;

    public static HomePortalLearningService disabled() {
        return new HomePortalLearningService(true,
                new Supplier<String>() {
                    @Override
                    public String get() {
                        return null;
                    }
                },
                NO_STORE,
                new SourceContextSupplier() {
                    @Override
                    public HomePortalInheritance.SourceContext capture(long sourceGridId,
                            int portalX, int portalY) {
                        return HomePortalInheritance.SourceContext.notHome();
                    }
                },
                new Supplier<Collection<HomeTerritories.Entry>>() {
                    @Override
                    public Collection<HomeTerritories.Entry> get() {
                        return Collections.emptyList();
                    }
                });
    }

    public HomePortalLearningService(final ChunkNavManager manager) {
        this(false, genusOf(manager), CONFIG_STORE, liveContext(manager), homesOf(manager));
    }

    public HomePortalLearningService(String genus, RegistryAccess store,
            SourceContextSupplier contexts, Collection<HomeTerritories.Entry> savedHomes) {
        this(false, constantGenus(genus), store, contexts, constantHomes(savedHomes));
    }

    private HomePortalLearningService(boolean disabled, Supplier<String> genus,
            RegistryAccess store, SourceContextSupplier contexts,
            Supplier<Collection<HomeTerritories.Entry>> savedHomes) {
        this.disabled = disabled;
        this.genus = genus;
        this.store = store;
        this.contexts = contexts;
        this.savedHomes = savedHomes;
    }

    public boolean shouldTrack(boolean chunkOverlayEnabled) {
        if (chunkOverlayEnabled)
            return true;
        if (disabled)
            return false;
        Collection<HomeTerritories.Entry> saved = savedHomes.get();
        if (saved != null && !saved.isEmpty())
            return true;
        HomeInteriorRegistry registry = store.load(genus.get());
        if (registry == null)
            return false;
        for (HomeInteriorRegistry.Binding binding : registry.bindings()) {
            if (binding.manual || binding.active(saved))
                return true;
        }
        return false;
    }

    public Pending capture(long sourceGridId, int portalX, int portalY, String portalResource,
            long sourceInstanceId, String sourceLayer) {
        return capture(sourceGridId, new Coord(portalX, portalY), portalResource,
                sourceInstanceId, sourceLayer);
    }

    public Pending capture(long sourceGridId, Coord portalCoord, String portalResource,
            long sourceInstanceId, String sourceLayer) {
        if (sourceGridId == -1 || portalCoord == null)
            return null;
        HomePortalInheritance.SourceContext source = HomePortalInheritance.SourceContext.notHome();
        if (!disabled && !HomePortalInheritance.isMineOrCaveLayer(sourceLayer) && contexts != null) {
            HomePortalInheritance.SourceContext captured = contexts.capture(
                    sourceGridId, portalCoord.x, portalCoord.y);
            if (captured != null)
                source = captured;
        }
        return new Pending(sourceGridId, portalCoord, portalResource, sourceInstanceId, sourceLayer, source);
    }

    public static UnaryOperator<HomeInteriorRegistry> claimBackfillUpdater(
            final ChunkNavGraph graph,
            final Collection<HomeTerritories.Entry> savedHomes,
            final HomePortalLearningService learning) {
        return new UnaryOperator<HomeInteriorRegistry>() {
            @Override
            public HomeInteriorRegistry apply(HomeInteriorRegistry current) {
                HomeInteriorRegistry base = current == null
                        ? HomeInteriorRegistry.empty() : current;
                HomeInteriorRegistry next = learning.backfillClaims(graph, savedHomes, base);
                return next.equals(base) ? base : next;
            }
        };
    }

    public HomeInteriorRegistry backfillClaims(ChunkNavGraph graph,
            Collection<HomeTerritories.Entry> savedHomes,
            HomeInteriorRegistry registry) {
        HomeInteriorRegistry current = registry == null ? HomeInteriorRegistry.empty() : registry;
        if (disabled || graph == null)
            return current;
        Collection<HomeTerritories.Entry> homes = savedHomes == null
                ? Collections.<HomeTerritories.Entry>emptyList() : savedHomes;
        for (ChunkNavData source : graph.getAllChunks()) {
            if (source == null
                    || source.instanceId != ChunkNavManager.SURFACE_INSTANCE
                    || !"outside".equals(source.layer)
                    || source.portals == null)
                continue;
            for (ChunkPortal portal : source.portals) {
                current = backfillDoor(graph, homes, current, source, portal);
            }
        }
        return current;
    }

    private static HomeInteriorRegistry backfillDoor(ChunkNavGraph graph,
            Collection<HomeTerritories.Entry> homes, HomeInteriorRegistry current,
            ChunkNavData source, ChunkPortal portal) {
        if (portal == null || portal.type != ChunkPortal.PortalType.DOOR
                || portal.connectsToGridId == -1 || portal.localCoord == null
                || portal.gobName == null)
            return current;
        ChunkNavData dest = graph.getChunk(portal.connectsToGridId);
        if (dest == null || dest.instanceId <= ChunkNavManager.SURFACE_INSTANCE)
            return current;
        if (!"inside".equals(dest.layer) && !"cellar".equals(dest.layer))
            return current;
        LinkedHashSet<HomeInteriorRegistry.OriginKey> origins =
                new LinkedHashSet<HomeInteriorRegistry.OriginKey>();
        for (HomeTerritories.Entry entry : homes) {
            if (entry == null || entry.type != HomeTerritories.Type.CLAIM || entry.area == null)
                continue;
            if (entry.area.contains(source.gridId, portal.localCoord.x, portal.localCoord.y))
                origins.add(HomeInteriorRegistry.OriginKey.from(entry));
        }
        if (origins.isEmpty())
            return current;
        HomeInteriorRegistry.PortalIdentity root = new HomeInteriorRegistry.PortalIdentity(
                source.gridId, portal.localCoord.x, portal.localCoord.y, portal.gobName);
        if (current.isSuppressed(root))
            return current;
        LinkedHashSet<Long> grids = new LinkedHashSet<Long>();
        for (ChunkNavData chunk : graph.getAllChunks()) {
            if (chunk != null && chunk.instanceId == dest.instanceId)
                grids.add(chunk.gridId);
        }
        String autoId = "auto:" + root.stableKey();
        HomeInteriorRegistry.Binding created = HomeInteriorRegistry.Binding.automatic(
                autoId, dest.instanceId, grids, origins, root, "", 0L);
        HomeInteriorRegistry.Binding existing = bindingWithId(current, autoId);
        HomeInteriorRegistry.Binding nextBinding = existing == null ? created : existing.merge(created);
        HomeInteriorRegistry updated = current.put(nextBinding);
        return updated.equals(current) ? current : updated;
    }

    private static HomeInteriorRegistry.Binding bindingWithId(HomeInteriorRegistry registry, String id) {
        for (HomeInteriorRegistry.Binding binding : registry.bindings()) {
            if (id.equals(binding.id))
                return binding;
        }
        return null;
    }

    public void confirm(Pending pending, HomePortalInheritance.Traversal traversal) {
        if (disabled || pending == null || traversal == null)
            return;
        if (HomePortalInheritance.isMineOrCaveLayer(pending.sourceLayer)
                || HomePortalInheritance.isMineOrCaveLayer(traversal.fromLayer))
            return;
        if (!HomePortalInheritance.canInherit(traversal.portalType, traversal.fromLayer,
                traversal.toLayer, traversal.confirmed, traversal.teleport))
            return;
        HomeInteriorRegistry loaded = store.load(genus.get());
        if (loaded == null)
            loaded = HomeInteriorRegistry.empty();
        if (!HomePortalInheritance.apply(loaded, pending.source, traversal).changed)
            return;
        store.update(genus.get(), new UnaryOperator<HomeInteriorRegistry>() {
            @Override
            public HomeInteriorRegistry apply(HomeInteriorRegistry existing) {
                HomeInteriorRegistry next = existing == null ? HomeInteriorRegistry.empty() : existing;
                HomePortalInheritance.Change change = HomePortalInheritance.apply(
                        next, pending.source, traversal);
                if (!change.changed)
                    return next;
                HomeInteriorRegistry result = change.registry;
                if (traversal.confirmed && traversal.rootPortal != null
                        && result.isSuppressed(traversal.rootPortal))
                    result = result.clearSuppression(traversal.rootPortal);
                return result;
            }
        });
    }

    private static SourceContextSupplier liveContext(final ChunkNavManager manager) {
        return new SourceContextSupplier() {
            @Override
            public HomePortalInheritance.SourceContext capture(long sourceGridId,
                    int portalX, int portalY) {
                try {
                    Collection<HomeTerritories.Entry> saved = homesOf(manager).get();
                    NGameUI gui = NUtils.getGameUI();
                    CurrentHomeTerritories.Detection detection = CurrentHomeTerritories.detect(gui);
                    List<HomeTerritories.Entry> matching = HomeTerritories.matchingHomes(
                            saved, detection.entries, detection.claimArea);
                    LinkedHashSet<HomeInteriorRegistry.OriginKey> origins =
                            new LinkedHashSet<HomeInteriorRegistry.OriginKey>();
                    for (HomeTerritories.Entry entry : matching) {
                        if (entry != null)
                            origins.add(HomeInteriorRegistry.OriginKey.from(entry));
                    }
                    HomeInteriorRegistry registry = CONFIG_STORE.load(
                            manager == null ? null : manager.getCurrentGenus());
                    long instanceId = manager == null ? 0L : manager.getCurrentInstanceId();
                    HomeInteriorRegistry.Binding inherited = registry.findActive(
                            sourceGridId, instanceId, saved);
                    return new HomePortalInheritance.SourceContext(origins, inherited);
                } catch (RuntimeException ignored) {
                    return HomePortalInheritance.SourceContext.notHome();
                }
            }
        };
    }

    private static Supplier<String> genusOf(final ChunkNavManager manager) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return manager == null ? null : manager.getCurrentGenus();
            }
        };
    }

    private static Supplier<Collection<HomeTerritories.Entry>> homesOf(final ChunkNavManager manager) {
        return new Supplier<Collection<HomeTerritories.Entry>>() {
            @Override
            public Collection<HomeTerritories.Entry> get() {
                return HomeTerritories.decodeForWorld(
                        NConfig.get(NConfig.Key.homeTerritories),
                        manager == null ? null : manager.getCurrentGenus());
            }
        };
    }

    private static Supplier<String> constantGenus(final String genus) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return genus;
            }
        };
    }

    private static Supplier<Collection<HomeTerritories.Entry>> constantHomes(
            final Collection<HomeTerritories.Entry> savedHomes) {
        final Collection<HomeTerritories.Entry> homes = savedHomes == null
                ? Collections.<HomeTerritories.Entry>emptyList() : savedHomes;
        return new Supplier<Collection<HomeTerritories.Entry>>() {
            @Override
            public Collection<HomeTerritories.Entry> get() {
                return homes;
            }
        };
    }
}
