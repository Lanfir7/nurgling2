package haven;

import haven.MapFile.Marker;
import haven.MapFile.PMarker;
import haven.MapFile.SMarker;

import java.util.LinkedHashSet;
import java.util.Set;

/** Persistent, resource-based visibility choices for the world-map marker list. */
public final class MapMarkerVisibility {
    private MapMarkerVisibility() {
    }

    public static String identity(Marker marker) {
        if(marker instanceof SMarker) {
            SMarker sm = (SMarker)marker;
            return("resource:" + ((sm.res == null || sm.res.name == null) ? "unknown" : sm.res.name));
        }
        if(marker instanceof PMarker)
            return("placed:" + Integer.toUnsignedString(((PMarker)marker).color.getRGB(), 16));
        return("marker:" + marker.getClass().getName());
    }

    /** Only saved system markers belong to the custom world-map visibility list. */
    public static boolean isSystemMarker(Marker marker) {
        return(marker instanceof SMarker);
    }

    public static boolean visible(boolean hideUnchecked, Set<String> hidden, Marker marker) {
	return(visible(hideUnchecked, hidden, identity(marker)));
    }

    public static boolean visible(boolean hideUnchecked, Set<String> hidden, String identity) {
	return(!hideUnchecked || !customFilterIdentity(identity) || !hidden.contains(identity));
    }

    /**
     * The custom map-icon list is deliberately limited to sources without another
     * visibility control. Legacy hidden values for the other sources are ignored.
     */
    public static boolean customFilterIdentity(String identity) {
	return((identity != null) && (identity.startsWith("resource:") || identity.startsWith("live:") || identity.startsWith("discovery:") || identity.startsWith("timer:")));
    }

    public static String liveIconIdentity(MiniMap.DisplayIcon icon) {
        String resource = (icon == null || icon.icon == null || icon.icon.res == null) ? "unknown" : icon.icon.res.name;
        return("live:" + resource);
    }

    public static String resourceIdentity(String category, String resource) {
        return(category + ":" + ((resource == null || resource.isEmpty()) ? "unknown" : resource));
    }

    public static boolean isThingwall(Marker marker) {
        if(!(marker instanceof SMarker))
            return(false);
        SMarker sm = (SMarker)marker;
        return((sm.res != null) && "gfx/terobjs/mm/thingwall".equals(sm.res.name));
    }

    public static String thingwallKey(Marker marker) {
        if(!isThingwall(marker))
            return(null);
        String map = (marker.file.filename == null) ? "" : marker.file.filename;
        return(map + "|" + identity(marker) + "@" + marker.seg + ":" + marker.tc.x + ":" + marker.tc.y);
    }

    public static boolean hasVisitedThingwallHalo(Set<String> visited, Marker marker) {
        String key = thingwallKey(marker);
        return((key != null) && visited.contains(key));
    }

    public static Set<String> decode(String saved) {
        if((saved == null) || saved.isEmpty())
            return(new LinkedHashSet<>());
        Set<String> ret = new LinkedHashSet<>();
        for(String entry : saved.split("\\n")) {
            if(!entry.isEmpty())
                ret.add(entry);
        }
        return(ret);
    }

    public static String encode(Set<String> identities) {
        if((identities == null) || identities.isEmpty())
            return("");
        return(String.join("\n", new java.util.TreeSet<>(identities)));
    }

    public static Set<String> merge(Set<String> saved, Set<String> additions) {
        Set<String> merged = new LinkedHashSet<>();
        if(saved != null)
            merged.addAll(saved);
        if(additions != null)
            merged.addAll(additions);
        return(merged);
    }
}
