package nurgling.areas;

import org.json.JSONObject;

import java.util.Collection;
import java.util.UUID;

public class AreaCreation {
    /**
     * Allocates above both live areas and the database watermark, which includes
     * tombstoned ids that must never be reused.
     */
    public static int nextAvailableAreaId(Collection<NArea> areas, int maxKnownDbId) {
        int id = 1;
        for (NArea area : areas) {
            if (area.id >= id) {
                id = area.id + 1;
            }
        }
        return Math.max(id, maxKnownDbId + 1);
    }

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
