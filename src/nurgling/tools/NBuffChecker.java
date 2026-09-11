package nurgling.tools;

import haven.Buff;
import haven.GItem;
import haven.ItemInfo;
import haven.Loading;
import haven.Widget;
import nurgling.NUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for checking active buffs in the buff bar.
 */
public class NBuffChecker {

    // Matched against Buff's underlying resource path (case-insensitive substring) rather than a
    // full exact path, since the precise resource path for this buff hasn't been confirmed against
    // the live resource server yet - "tansy" is distinctive enough that a substring match is safe.
    private static final String SCENT_OF_TANSY_RES_HINT = "tansy";

    static final class BuffSnapshot {
        final String resName;
        final List<ItemInfo> info;
        final boolean infoLoading;

        BuffSnapshot(String resName, List<ItemInfo> info) {
            this(resName, info, false);
        }

        BuffSnapshot(String resName, List<ItemInfo> info, boolean infoLoading) {
            this.resName = resName;
            this.info = info;
            this.infoLoading = infoLoading;
        }

        static BuffSnapshot loadingInfo(String resName) {
            return new BuffSnapshot(resName, Collections.<ItemInfo>emptyList(), true);
        }
    }

    static final class BuffProbe {
        final boolean present;
        final int count;

        BuffProbe(boolean present, int count) {
            this.present = present;
            this.count = count;
        }
    }

    /** True if a matching "Scent of Tansy" buff widget is present, including while its numeric overlay is still loading. */
    public static boolean hasScentOfTansy() {
        return hasScentOfTansy(probeLive());
    }

    /** Current Scent of Tansy stack count (the number overlaid on its buff icon - it rises on each
     *  application and falls on each midge bite), or -1 if the count is unknown or the buff is absent.
     *  A matching but still-loading buff is unknown (-1) rather than absent; use {@link #hasScentOfTansy()}
     *  to distinguish presence. */
    public static int getScentOfTansyCount() {
        return getScentOfTansyCount(probeLive());
    }

    static boolean hasScentOfTansy(BuffProbe probe) {
        return probe != null && probe.present;
    }

    static int getScentOfTansyCount(BuffProbe probe) {
        return probe == null ? -1 : probe.count;
    }

    static BuffProbe probe(BuffSnapshot... snapshots) {
        List<BuffSnapshot> list = new ArrayList<BuffSnapshot>();
        if (snapshots != null) {
            for (BuffSnapshot snapshot : snapshots) {
                if (snapshot != null)
                    list.add(snapshot);
            }
        }
        return probe(list);
    }

    static BuffProbe probe(Iterable<BuffSnapshot> snapshots) {
        if (snapshots == null)
            return new BuffProbe(false, -1);
        for (BuffSnapshot snapshot : snapshots) {
            if (snapshot == null)
                continue;
            if (snapshot.resName == null || !snapshot.resName.toLowerCase().contains(SCENT_OF_TANSY_RES_HINT))
                continue;
            if (snapshot.infoLoading)
                return new BuffProbe(true, -1);
            int count = -1;
            if (snapshot.info != null) {
                for (ItemInfo ii : snapshot.info) {
                    if (ii instanceof GItem.NumberInfo) {
                        count = ((GItem.NumberInfo) ii).itemnum();
                        break;
                    }
                }
            }
            return new BuffProbe(true, count);
        }
        return new BuffProbe(false, -1);
    }

    private static BuffProbe probeLive() {
        try {
            haven.GameUI gui = NUtils.getGameUI();
            if (gui == null || gui.buffs == null)
                return new BuffProbe(false, -1);
            List<BuffSnapshot> snapshots = new ArrayList<BuffSnapshot>();
            for (Widget w = gui.buffs.child; w != null; w = w.next) {
                if (!(w instanceof Buff))
                    continue;
                Buff buff = (Buff) w;
                try {
                    String resName = buff.res.get().name;
                    try {
                        snapshots.add(new BuffSnapshot(resName, buff.info()));
                    } catch (Loading l) {
                        snapshots.add(BuffSnapshot.loadingInfo(resName));
                    }
                } catch (Loading l) {
                    // Resource not loaded yet, skip this buff widget
                } catch (Exception e) {
                    // Malformed info for this widget - skip it rather than fail the whole scan
                }
            }
            return probe(snapshots);
        } catch (Exception e) {
            return new BuffProbe(false, -1);
        }
    }
}
