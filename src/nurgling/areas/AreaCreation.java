package nurgling.areas;

import org.json.JSONObject;

import java.util.UUID;

public class AreaCreation {
    public static void initializeNew(NArea area) {
        area.dirtyGroups.clear();
        area.uuid = UUID.randomUUID().toString();
        area.zoneSync = null;
        area.version = 0;
        area.baselineVersion = 0;
        area.baselineSnapshot = null;
        area.synced = false;
        area.lastUpdated = 0L;
        area.lastTouchedBy = null;
        area.lastTouchedAt = 0L;
        area.syncGridIdsFromSpace();
        area.markDirty(AreaFieldGroup.GEOMETRY);
        area.markDirty(AreaFieldGroup.IDENTITY);
        area.markDirty(AreaFieldGroup.COSMETIC);
        area.markDirty(AreaFieldGroup.ROUTING);
    }

    public static NArea duplicate(NArea source, int id, String name) {
        NArea copy = new NArea(new JSONObject(source.toJson().toString()));
        copy.id = id;
        copy.name = name;
        copy.gid = Long.MIN_VALUE;
        initializeNew(copy);
        return copy;
    }
}
