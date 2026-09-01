package nurgling.widgets.compass;

import haven.Coord2d;
import haven.Indir;
import haven.Resource;

import java.awt.Color;

public final class NCompassTarget {
    public enum Kind {
        QUEST,
        PARTY,
        DATABASE,
        PLAYER,
        COMBAT
    }

    public final String id;
    public final String mergeKey;
    public final Kind kind;
    public final Coord2d position;
    public final String name;
    public final double distance;
    public final Indir<Resource> icon;
    public final Color color;
    public final long gobId;

    public NCompassTarget(String id, Kind kind, Coord2d position, String name,
                          double distance, Indir<Resource> icon, Color color) {
        this(id, id, kind, position, name, distance, icon, color, -1);
    }

    public NCompassTarget(String id, String mergeKey, Kind kind, Coord2d position, String name,
                          double distance, Indir<Resource> icon, Color color, long gobId) {
        this.id = id;
        this.mergeKey = mergeKey == null ? id : mergeKey;
        this.kind = kind;
        this.position = position;
        this.name = name;
        this.distance = distance;
        this.icon = icon;
        this.color = color;
        this.gobId = gobId;
    }
}
