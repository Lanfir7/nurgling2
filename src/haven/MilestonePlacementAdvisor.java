package haven;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Deterministic, UI-free search for the nearest Milestone placement tile. */
final class MilestonePlacementAdvisor {
    static final double SEARCH_RADIUS_TILES = 7.5;

    interface CandidateValidator {
        boolean isSuitable(Coord2d candidate, double angle);
    }

    private MilestonePlacementAdvisor() {
    }

    static boolean isMilestoneResource(String resourceName) {
        return resourceName != null &&
                (resourceName.startsWith("gfx/terobjs/road/milestone-wood-") ||
                 resourceName.startsWith("gfx/terobjs/road/milestone-stone-"));
    }

    static Optional<Coord2d> findNearest(Coord2d origin, double tileSize, double angle,
                                         CandidateValidator validator) {
        if (origin == null || tileSize <= 0 || validator == null) {
            return Optional.empty();
        }
        int radius = (int)Math.ceil(SEARCH_RADIUS_TILES);
        List<Coord> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if ((dx * dx) + (dy * dy) <= SEARCH_RADIUS_TILES * SEARCH_RADIUS_TILES) {
                    offsets.add(Coord.of(dx, dy));
                }
            }
        }
        offsets.sort(Comparator
                .comparingInt((Coord offset) -> (offset.x * offset.x) + (offset.y * offset.y))
                .thenComparingInt(offset -> offset.x)
                .thenComparingInt(offset -> offset.y));
        for (Coord offset : offsets) {
            Coord2d candidate = origin.add(offset.x * tileSize, offset.y * tileSize);
            if (validator.isSuitable(candidate, angle)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    static Coord2d snapIfNear(Coord2d requested, Coord2d suggestion, double tileSize) {
        if (requested == null || suggestion == null || tileSize <= 0) {
            return requested;
        }
        return requested.dist(suggestion) <= tileSize ? suggestion : requested;
    }
}
