package nurgling.widgets.bots;

import haven.*;
import nurgling.NUtils;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.conf.NFillTreePotsProp;
import nurgling.i18n.L10n;

import java.util.ArrayList;
import java.util.List;

public class FillTreePotsWidget extends Window implements Checkable {

    Dropbox<NArea> zoneDropbox = null;
    List<NArea> zoneList = new ArrayList<>();

    public FillTreePotsWidget() {
        super(new Coord(UI.scale(300), UI.scale(180)), L10n.get("fill_tree_pots.wnd_title"));
        NFillTreePotsProp startprop = NFillTreePotsProp.get(NUtils.getUI().sessInfo);
        if (startprop == null) startprop = new NFillTreePotsProp("", "");

        prev = add(new Label(L10n.get("fill_tree_pots.select_mulch_zone")));
        zoneList = NContext.findVisibleSoilForTreesZones();
        final int zoneListSize = zoneList.size();
        prev = add(zoneDropbox = new Dropbox<NArea>(UI.scale(250), Math.min(8, Math.max(1, zoneListSize + 1)), UI.scale(16)) {
            @Override
            protected NArea listitem(int i) {
                if (i == 0 && zoneListSize == 0) return null;
                return i < zoneList.size() ? zoneList.get(i) : null;
            }

            @Override
            protected int listitems() {
                return zoneList.isEmpty() ? 1 : zoneList.size();
            }

            @Override
            protected void drawitem(GOut g, NArea item, int i) {
                if (item != null) {
                    g.text(item.name != null ? item.name : ("#" + item.id), Coord.z);
                } else {
                    g.text(L10n.get("fill_tree_pots.no_zones"), Coord.z);
                }
            }

            @Override
            public void change(NArea item) {
                super.change(item);
            }
        }, prev.pos("bl").add(UI.scale(0, 5)));

        if (startprop.mulchZoneId != null && NUtils.getArea(startprop.mulchZoneId) != null) {
            NArea savedZone = NUtils.getArea(startprop.mulchZoneId);
            if (zoneList.contains(savedZone)) {
                zoneDropbox.change(savedZone);
            } else if (!zoneList.isEmpty()) {
                zoneDropbox.change(zoneList.get(0));
            }
        } else if (!zoneList.isEmpty()) {
            zoneDropbox.change(zoneList.get(0));
        }

        prev = add(new Button(UI.scale(150), L10n.get("botwnd.start")) {
            @Override
            public void click() {
                super.click();
                prop = NFillTreePotsProp.get(NUtils.getUI().sessInfo);
                if (prop != null) {
                    NArea selectedZone = zoneDropbox.sel;
                    prop.mulchZoneId = (selectedZone != null) ? selectedZone.id : null;
                    NFillTreePotsProp.set(prop);
                }
                isReady = true;
            }
        }, prev.pos("bl").add(UI.scale(0, 10)));
        pack();
    }

    @Override
    public boolean check() {
        return isReady;
    }

    boolean isReady = false;

    @Override
    public void wdgmsg(String msg, Object... args) {
        if (msg.equals("close")) {
            isReady = true;
            hide();
        }
        super.wdgmsg(msg, args);
    }

    public NFillTreePotsProp prop = null;
}
