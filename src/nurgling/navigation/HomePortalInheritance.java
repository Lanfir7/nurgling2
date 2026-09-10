package nurgling.navigation;

import nurgling.tools.HomeInteriorRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class HomePortalInheritance {
    public static final class SourceContext {
        public final Set<HomeInteriorRegistry.OriginKey> directOrigins;
        public final HomeInteriorRegistry.Binding inheritedBinding;

        public SourceContext(Collection<HomeInteriorRegistry.OriginKey> directOrigins,
                HomeInteriorRegistry.Binding inheritedBinding) {
            LinkedHashSet<HomeInteriorRegistry.OriginKey> origins =
                    new LinkedHashSet<HomeInteriorRegistry.OriginKey>();
            if (directOrigins != null)
                origins.addAll(directOrigins);
            this.directOrigins = Collections.unmodifiableSet(origins);
            this.inheritedBinding = inheritedBinding;
        }

        public static SourceContext notHome() {
            return new SourceContext(Collections.<HomeInteriorRegistry.OriginKey>emptySet(), null);
        }
    }

    public static final class Traversal {
        public final long fromGridId;
        public final long toGridId;
        public final long fromInstanceId;
        public final long toInstanceId;
        public final String fromLayer;
        public final String toLayer;
        public final ChunkPortal.PortalType portalType;
        public final HomeInteriorRegistry.PortalIdentity rootPortal;
        public final String destinationPortalResource;
        public final boolean confirmed;
        public final boolean teleport;
        public final long occurredAt;

        public Traversal(long fromGridId, long toGridId, long fromInstanceId, long toInstanceId,
                String fromLayer, String toLayer, ChunkPortal.PortalType portalType,
                HomeInteriorRegistry.PortalIdentity rootPortal, String destinationPortalResource,
                boolean confirmed, boolean teleport, long occurredAt) {
            this.fromGridId = fromGridId;
            this.toGridId = toGridId;
            this.fromInstanceId = fromInstanceId;
            this.toInstanceId = toInstanceId;
            this.fromLayer = fromLayer;
            this.toLayer = toLayer;
            this.portalType = portalType;
            this.rootPortal = rootPortal;
            this.destinationPortalResource = destinationPortalResource;
            this.confirmed = confirmed;
            this.teleport = teleport;
            this.occurredAt = occurredAt;
        }
    }

    public static final class Change {
        public final boolean changed;
        public final HomeInteriorRegistry registry;

        private Change(boolean changed, HomeInteriorRegistry registry) {
            this.changed = changed;
            this.registry = registry;
        }
    }

    static boolean isMineOrCaveLayer(String layer) {
        if (layer == null)
            return false;
        String lower = layer.toLowerCase();
        return lower.startsWith("mine") || lower.startsWith("cave");
    }

    private HomePortalInheritance() {
    }

    public static boolean canInherit(ChunkPortal.PortalType type, String fromLayer,
            String toLayer, boolean confirmed, boolean teleport) {
        if (!confirmed || teleport || type == null || toLayer == null)
            return false;
        if (isMineOrCaveLayer(fromLayer))
            return false;
        if (!"inside".equals(toLayer) && !"cellar".equals(toLayer))
            return false;
        switch (type) {
            case DOOR:
            case STAIRS_UP:
            case STAIRS_DOWN:
            case CELLAR:
                return true;
            default:
                return false;
        }
    }

    public static Change apply(HomeInteriorRegistry registry,
            SourceContext source, Traversal traversal) {
        HomeInteriorRegistry current = registry == null ? HomeInteriorRegistry.empty() : registry;
        if (source == null || traversal == null)
            return unchanged(current);
        if (!canInherit(traversal.portalType, traversal.fromLayer, traversal.toLayer,
                traversal.confirmed, traversal.teleport))
            return unchanged(current);
        if (source.inheritedBinding != null)
            return putBinding(current, mergeDestination(source.inheritedBinding, traversal,
                    Collections.<HomeInteriorRegistry.OriginKey>emptySet()));
        if (source.directOrigins.isEmpty() || traversal.rootPortal == null)
            return unchanged(current);
        if (!ChunkNavManager.isInteriorInstanceId(traversal.toInstanceId))
            return unchanged(current);
        String autoId = "auto:" + traversal.rootPortal.stableKey();
        HomeInteriorRegistry.Binding existing = bindingWithId(current, autoId);
        if (existing != null)
            return putBinding(current, mergeDestination(existing, traversal, source.directOrigins));
        return putBinding(current, HomeInteriorRegistry.Binding.automatic(
                autoId,
                traversal.toInstanceId,
                Collections.singleton(traversal.toGridId),
                source.directOrigins,
                traversal.rootPortal,
                "",
                traversal.occurredAt));
    }

    private static HomeInteriorRegistry.Binding bindingWithId(HomeInteriorRegistry registry, String id) {
        for (HomeInteriorRegistry.Binding binding : registry.bindings()) {
            if (id.equals(binding.id))
                return binding;
        }
        return null;
    }

    private static HomeInteriorRegistry.Binding mergeDestination(
            HomeInteriorRegistry.Binding existing, Traversal traversal,
            Set<HomeInteriorRegistry.OriginKey> extraOrigins) {
        HomeInteriorRegistry.PortalIdentity root = existing.rootPortal != null
                ? existing.rootPortal : traversal.rootPortal;
        if (root == null)
            return existing;
        return existing.merge(HomeInteriorRegistry.Binding.automatic(
                existing.id,
                traversal.toInstanceId,
                Collections.singleton(traversal.toGridId),
                extraOrigins,
                root,
                existing.displayName,
                traversal.occurredAt));
    }

    private static Change putBinding(HomeInteriorRegistry current,
            HomeInteriorRegistry.Binding binding) {
        HomeInteriorRegistry next = current.put(binding);
        return new Change(!next.equals(current), next);
    }

    private static Change unchanged(HomeInteriorRegistry registry) {
        return new Change(false, registry);
    }
}
