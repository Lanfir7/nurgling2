package nurgling.routes;

import haven.*;

import java.util.*;

/**
 * Упрощенная версия RoutePoint без привязки к зонам.
 * Используется только для записи и воспроизведения маршрута.
 */
public class SimpleRoutePoint {
    public String name;
    public int id;
    public long gridId;
    public Coord localCoord;
    public String hearthFirePlayerName;
    
    // Original position for drag limiting
    public long originalGridId;
    public Coord originalLocalCoord;

    public ArrayList<Integer> neighbors = new ArrayList<>();
    public Map<Integer, Connection> connections = new HashMap<>();

    public class Connection {
        public String connectionTo;
        public String gobHash;
        public String gobName;
        public boolean isDoor;
        
        public Connection(String connectionTo, String gobHash, String gobName, boolean isDoor) {
            this.connectionTo = connectionTo;
            this.gobHash = gobHash;
            this.gobName = gobName;
            this.isDoor = isDoor;
        }
    }

    public SimpleRoutePoint(Coord2d rc, MCache mcache) {
        this(rc, mcache, null);
    }

    public SimpleRoutePoint(Coord2d rc, MCache mcache, String hearthFirePlayerName) {
        Coord tilec = rc.div(MCache.tilesz).floor();
        MCache.Grid grid = mcache.getgridt(tilec);

        this.gridId = grid.id;
        this.localCoord = tilec.sub(grid.ul);
        this.hearthFirePlayerName = hearthFirePlayerName;
        
        // Store original position for drag limiting
        this.originalGridId = this.gridId;
        this.originalLocalCoord = new Coord(this.localCoord);
        
        this.id = hashCode();
    }

    public SimpleRoutePoint(long gridId, Coord localCoord, String hearthFirePlayerName) {
        this.name = "";
        this.gridId = gridId;
        this.localCoord = localCoord;
        this.hearthFirePlayerName = hearthFirePlayerName;
        
        // Store original position for drag limiting
        this.originalGridId = this.gridId;
        this.originalLocalCoord = new Coord(this.localCoord);
        
        this.id = hashCode();
    }

    public void addNeighbor(int id) {
        if(!neighbors.contains(Integer.valueOf(id))) {
            neighbors.add(id);
        }
    }

    public List<Integer> getNeighbors() {
        return new ArrayList<>(neighbors);
    }

    public void addConnection(int neighborHash, String connectionTo, String gobHash, String gobName, boolean isDoor) {
        connections.put(neighborHash, new Connection(connectionTo, gobHash, gobName, isDoor));
    }

    public void addConnection(int neighborHash, Connection connection) {
        connections.put(neighborHash, connection);
    }

    public Connection getConnection(int neighborHash) {
        return connections.get(neighborHash);
    }

    public Set<Integer> getConnectedNeighbors() {
        return new HashSet<>(connections.keySet());
    }

    public void removeConnection(int neighborHash) {
        connections.remove(neighborHash);
    }

    public void setLocalCoord(Coord localCoord) {
        this.localCoord = localCoord;
        this.id = hashCode();
    }

    public void setGridId(long gridId) {
        this.gridId = gridId;
    }

    public Coord2d toCoord2d(MCache mcache) {
        synchronized(mcache.grids) {
            for (MCache.Grid grid : mcache.grids.values()) {
                if (grid.id == gridId) {
                    Coord tilec = grid.ul.add(localCoord);
                    return tilec.mul(MCache.tilesz).add(MCache.tilehsz);
                }
            }
            return null;
        }
    }

    public Coord3f toCoord3f(MCache mcache) {
        synchronized (mcache.grids) {
            for (MCache.Grid grid : mcache.grids.values()) {
                if (grid.id == gridId) {
                    boolean canContinue = false;
                    for(MCache.Grid.Cut cut : grid.cuts) {
                        canContinue = cut.mesh.isReady() && cut.fo.isReady();
                    }
                    if (canContinue) {
                        return mcache.getzp(grid.ul.add(localCoord).mul(MCache.tilesz).add(MCache.tilehsz));
                    }
                }
            }
            return null;
        }
    }

    public Collection<Connection> getConnections() {
        return this.connections.values();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.gridId, this.localCoord);
    }

    // Check if a proposed position is within the 5-cell limit from original position
    public boolean isWithinDragLimit(long proposedGridId, Coord proposedLocalCoord, MCache mcache) {
        try {
            // Get original world position
            Coord2d originalWorldPos = getOriginalWorldPosition(mcache);
            if (originalWorldPos == null) return false;
            
            // Get proposed world position
            Coord2d proposedWorldPos = getWorldPositionForCoords(proposedGridId, proposedLocalCoord, mcache);
            if (proposedWorldPos == null) return false;
            
            // Calculate distance in tiles (each tile is MCache.tilesz units)
            double distanceInTiles = originalWorldPos.dist(proposedWorldPos) / MCache.tilesz.x;
            
            return distanceInTiles <= 5.0;
        } catch (Exception e) {
            return false; // If any error occurs, don't allow the move
        }
    }
    
    // Get the original world position of this route point
    public Coord2d getOriginalWorldPosition(MCache mcache) {
        synchronized(mcache.grids) {
            for (MCache.Grid grid : mcache.grids.values()) {
                if (grid.id == originalGridId) {
                    Coord tilec = grid.ul.add(originalLocalCoord);
                    return tilec.mul(MCache.tilesz).add(MCache.tilehsz);
                }
            }
            return null;
        }
    }
    
    // Get world position for specific grid/local coordinates
    private Coord2d getWorldPositionForCoords(long gridId, Coord localCoord, MCache mcache) {
        synchronized(mcache.grids) {
            for (MCache.Grid grid : mcache.grids.values()) {
                if (grid.id == gridId) {
                    Coord tilec = grid.ul.add(localCoord);
                    return tilec.mul(MCache.tilesz).add(MCache.tilehsz);
                }
            }
            return null;
        }
    }
}

