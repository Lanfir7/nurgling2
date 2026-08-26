package nurgling.map;

import haven.Coord;
import haven.MapFile;
import nurgling.NCore;
import nurgling.NGameUI;
import nurgling.db.service.LocalTimerService;
import nurgling.sessions.SessionContext;
import nurgling.sessions.SessionManager;

import java.util.List;

/**
 * Nurgling overlays that key on map-file segment ids have to move when {@link MapFile} merges two
 * segments. Vanilla markers already do; Nurgling overlays are remapped here.
 */
public final class SegmentMerge {
    private SegmentMerge() {}

    public static void notify(MapFile file, long srcId, long dstId, Coord soff) {
        try {
            for (SessionContext sc : SessionManager.getInstance().getAllSessions()) {
                if (sc == null)
                    continue;
                NGameUI gui = sc.getGameUI();
                if (gui == null || gui.mmap == null || gui.mmap.file != file)
                    continue;
                if (gui.localizedResourceTimerService != null) {
                    List<String> oldIds = gui.localizedResourceTimerService.remapSegment(srcId, dstId, soff);
                    dropDbRows(gui, oldIds);
                }
                if (gui.treeLocationService != null)
                    gui.treeLocationService.remapSegment(srcId, dstId, soff);
                if (gui.prospectingLocationService != null)
                    gui.prospectingLocationService.remapSegment(srcId, dstId, soff);
                if (gui.labeledMarkService != null)
                    gui.labeledMarkService.remapSegment(srcId, dstId, soff);
            }
        } catch (RuntimeException ignore) {
        }
    }

    private static void dropDbRows(NGameUI gui, List<String> oldIds) {
        if (oldIds == null || oldIds.isEmpty())
            return;
        if (NCore.databaseManager == null)
            return;
        LocalTimerService svc = NCore.databaseManager.getLocalTimerService();
        if (svc == null)
            return;
        String profile = gui.getGenus();
        if (profile == null || profile.isEmpty())
            profile = "global";
        for (String id : oldIds)
            svc.deleteByResourceId(profile, id);
    }
}
