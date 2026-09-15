package nurgling.navigation;

import haven.Coord2d;
import haven.Gob;
import haven.KeyMatch;
import haven.MapFile;
import haven.MapView;
import haven.MiniMap;
import nurgling.overlays.MarkerBeaconSprite;
import nurgling.widgets.LabeledMinimapMark;

import java.util.ArrayDeque;
import java.util.Deque;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/** Starts bounded local visual beacons for map markers. */
public final class MapMarkerBeacon {
    private static final int MAX_PER_WORLD = 4;
    private static final Map<haven.Glob, Deque<WeakReference<Gob>>> active = new WeakHashMap<>();

    private MapMarkerBeacon() {
    }

    public static boolean isTrigger(int button, int modifiers) {
        return button == 1 && (modifiers & KeyMatch.MODS) == (KeyMatch.C | KeyMatch.S);
    }

    /** Whether a visible custom mark owns this press before map navigation handles it. */
    public static boolean claimsGesture(int button, int modifiers, LabeledMinimapMark marker) {
        return claimsGesture(button, modifiers, marker != null);
    }

    public static boolean claimsGesture(int button, int modifiers, boolean markerHit) {
        return markerHit && isTrigger(button, modifiers);
    }

    public static boolean start(MapView map, MapFile.Marker marker, MiniMap.Location session) {
        return start(map, worldTarget(marker, session));
    }

    /** Start a beacon for a custom minimap mark at its persisted tile coordinate. */
    public static boolean start(MapView map, LabeledMinimapMark marker, MiniMap.Location session) {
        return start(map, worldTarget(marker, session));
    }

    /** Start a beacon for another persisted minimap marker type. */
    public static boolean start(MapView map, long segmentId, haven.Coord tile, MiniMap.Location session) {
        return start(map, worldTarget(segmentId, tile, session));
    }

    private static boolean start(MapView map, Coord2d target) {
        if(map == null || map.glob == null || target == null)
            return false;
        try {
            final haven.Glob glob = map.glob;
            Gob beacon = new Gob(glob, target);
            beacon.addol(new Gob.Overlay(beacon,
                    new MarkerBeaconSprite(beacon, () -> forget(glob, beacon))), false);
            synchronized(active) {
                Deque<WeakReference<Gob>> world = active.computeIfAbsent(glob, ignored -> new ArrayDeque<>());
                while(world.size() >= MAX_PER_WORLD) {
                    Gob expired = world.removeFirst().get();
                    if(expired != null)
                        glob.oc.remove(expired);
                }
                world.addLast(new WeakReference<>(beacon));
            }
            glob.oc.add(beacon);
            return true;
        } catch(RuntimeException ignored) {
            return false;
        }
    }

    private static void forget(haven.Glob glob, Gob beacon) {
        synchronized(active) {
            Deque<WeakReference<Gob>> world = active.get(glob);
            if(world == null)
                return;
            for(Iterator<WeakReference<Gob>> i = world.iterator(); i.hasNext();) {
                Gob current = i.next().get();
                if(current == null || current == beacon)
                    i.remove();
            }
            if(world.isEmpty())
                active.remove(glob);
        }
    }

    /** Convert a marker's map tile to the live world coordinate, if it is on this segment. */
    public static Coord2d worldTarget(MapFile.Marker marker, MiniMap.Location session) {
        if(marker == null || session == null || marker.seg != session.seg.id)
            return null;
        return marker.tc.sub(session.tc).mul(haven.MCache.tilesz).add(haven.MCache.tilesz.div(2));
    }

    /** Convert a custom mark's persisted map tile to the live world coordinate. */
    public static Coord2d worldTarget(LabeledMinimapMark marker, MiniMap.Location session) {
        return marker == null ? null : worldTarget(marker.segmentId, marker.tileCoords, session);
    }

    /** Convert a persisted segment tile to a live world coordinate. */
    public static Coord2d worldTarget(long segmentId, haven.Coord tile, MiniMap.Location session) {
        if(tile == null || session == null || segmentId != session.seg.id)
            return null;
        return tile.sub(session.tc).mul(haven.MCache.tilesz).add(haven.MCache.tilesz.div(2));
    }
}
