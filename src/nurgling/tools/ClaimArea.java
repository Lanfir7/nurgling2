package nurgling.tools;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Stable personal-claim geometry expressed in server map-grid coordinates. */
public final class ClaimArea {
    public static final class Tile {
        public final long gridId;
        public final int x;
        public final int y;

        public Tile(long gridId, int x, int y) {
            this.gridId = gridId;
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof Tile))
                return false;
            Tile tile = (Tile) other;
            return gridId == tile.gridId && x == tile.x && y == tile.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(gridId, x, y);
        }
    }

    public final Tile anchor;
    private final Set<Tile> tiles;

    public ClaimArea(Tile anchor, Collection<Tile> tiles) {
        this.anchor = Objects.requireNonNull(anchor);
        LinkedHashSet<Tile> copy = new LinkedHashSet<>();
        if (tiles != null)
            copy.addAll(tiles);
        copy.add(anchor);
        this.tiles = Collections.unmodifiableSet(copy);
    }

    public int size() {
        return tiles.size();
    }

    public boolean contains(long gridId, int x, int y) {
        return tiles.contains(new Tile(gridId, x, y));
    }

    public Set<Tile> tiles() {
        return tiles;
    }

    public boolean overlaps(ClaimArea other) {
        if (other == null)
            return false;
        Set<Tile> smaller = tiles.size() <= other.tiles.size() ? tiles : other.tiles;
        Set<Tile> larger = smaller == tiles ? other.tiles : tiles;
        for (Tile tile : smaller) {
            if (larger.contains(tile))
                return true;
        }
        return false;
    }

    public ClaimArea merge(ClaimArea other) {
        if (other == null)
            return this;
        LinkedHashSet<Tile> merged = new LinkedHashSet<>(tiles);
        merged.addAll(other.tiles);
        return new ClaimArea(anchor, merged);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof ClaimArea))
            return false;
        ClaimArea area = (ClaimArea) other;
        return anchor.equals(area.anchor) && tiles.equals(area.tiles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(anchor, tiles);
    }
}
