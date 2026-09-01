package nurgling.widgets.compass;

import haven.Coord2d;
import haven.Gob;
import haven.Party;
import haven.Widget;
import haven.res.ui.locptr.Pointer;
import haven.res.ui.obj.buddy.Buddy;
import nurgling.NGameUI;
import nurgling.i18n.L10n;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class NCompassTargetCollector {
    private final NGameUI gui;

    public NCompassTargetCollector(NGameUI gui) {
        this.gui = gui;
    }

    public List<NCompassTarget> collect() {
        List<NCompassTarget> targets = new ArrayList<>();
        Gob player = gui.map == null ? null : gui.map.player();
        if (player == null)
            return targets;

        Widget root = gui.ui == null ? null : gui.ui.root;
        for (Pointer pointer : findPointers(gui, root)) {
            try {
                String name = pointer.tip();
                Gob targetGob = null;
                if (pointer.gobid >= 0 && gui.ui != null && gui.ui.sess != null && gui.ui.sess.glob != null)
                    targetGob = gui.ui.sess.glob.oc.getgob(pointer.gobid);
                Coord2d position = choosePointerPosition(pointer.tc,
                        targetGob == null ? null : targetGob.rc);
                if (name == null || name.trim().isEmpty() || position == null)
                    continue;
                targets.add(new NCompassTarget(
                        "quest:" + System.identityHashCode(pointer),
                        NCompassTarget.Kind.QUEST,
                        position,
                        name,
                        player.rc.dist(position) / 11.0,
                        pointer.icon,
                        Color.WHITE));
            } catch (RuntimeException ignored) {
            }
        }

        if (gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null)
            return targets;
        for (Party.Member member : gui.ui.sess.glob.party.memb.values()) {
            try {
                if (member.gobid == gui.map.plgob)
                    continue;
                Coord2d position = member.getc();
                if (position == null)
                    continue;
                Gob gob = member.getgob();
                Buddy buddy = gob == null ? null : gob.getattr(Buddy.class);
                String buddyName = buddy == null ? null : buddy.rnm;
                String name = choosePartyName(NGameUI.gobIdToKinName.get(member.gobid),
                        buddyName, L10n.get("compass.party_member"));
                if (buddyName != null && !buddyName.isEmpty())
                    NGameUI.gobIdToKinName.put(member.gobid, buddyName);
                targets.add(new NCompassTarget(
                        "party:" + member.gobid,
                        NCompassTarget.Kind.PARTY,
                        position,
                        name,
                        player.rc.dist(position) / 11.0,
                        null,
                        member.col == null ? Color.WHITE : member.col));
            } catch (RuntimeException ignored) {
            }
        }
        return targets;
    }

    static List<Pointer> findPointers(Widget... roots) {
        List<Pointer> pointers = new ArrayList<>();
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        if (roots != null) {
            for (Widget root : roots)
                collectPointers(root, visited, pointers);
        }
        return pointers;
    }

    private static void collectPointers(Widget widget, Set<Widget> visited, List<Pointer> pointers) {
        if (widget == null || !visited.add(widget))
            return;
        if (widget instanceof Pointer)
            pointers.add((Pointer) widget);
        for (Widget child : widget.children())
            collectPointers(child, visited, pointers);
    }

    static String choosePartyName(String cached, String buddy, String fallback) {
        if (cached != null && !cached.isEmpty())
            return cached;
        if (buddy != null && !buddy.isEmpty())
            return buddy;
        return fallback;
    }

    static Coord2d choosePointerPosition(Coord2d pointerPosition, Coord2d gobPosition) {
        return gobPosition == null ? pointerPosition : gobPosition;
    }
}
