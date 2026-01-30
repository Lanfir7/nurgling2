package nurgling.widgets.bots;

import haven.*;
import nurgling.NUtils;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.conf.NCarrierProp;
import nurgling.i18n.L10n;

import java.util.ArrayList;
import java.util.List;

public class Carrier extends Window implements Checkable {

    TextEntry customObjectInput = null;
    Dropbox<String> objectDropbox = null;
    List<String> objectList = new ArrayList<>();
    Dropbox<NArea> zoneDropbox = null;
    List<NArea> zoneList = new ArrayList<>();

    public Carrier() {
        super(new Coord(UI.scale(300), UI.scale(280)), L10n.get("carrier.wnd_title"));
        NCarrierProp startprop = NCarrierProp.get(NUtils.getUI().sessInfo);
        if (startprop == null) startprop = new NCarrierProp("", "");

        objectList = new ArrayList<>(startprop.objectHistory);
        if (!objectList.contains("")) {
            objectList.add(0, "");
        }

        prev = add(new Label(L10n.get("carrier.object_name")));
        prev = add(customObjectInput = new TextEntry(UI.scale(250), startprop.object == null ? "" : startprop.object), prev.pos("bl").add(UI.scale(0, 5)));
        prev = add(new Label(L10n.get("carrier.hint")), prev.pos("bl").add(UI.scale(0, 2)));
        prev = add(new Label(L10n.get("carrier.select_recent")), prev.pos("bl").add(UI.scale(0, 8)));

        prev = add(objectDropbox = new Dropbox<String>(UI.scale(250), Math.min(10, objectList.size()), UI.scale(16)) {
            @Override
            protected String listitem(int i) {
                return i < objectList.size() ? objectList.get(i) : "";
            }

            @Override
            protected int listitems() {
                return objectList.size();
            }

            @Override
            protected void drawitem(GOut g, String item, int i) {
                if (item != null && !item.isEmpty()) {
                    g.text(item, Coord.z);
                } else {
                    g.text(L10n.get("carrier.empty"), Coord.z);
                }
            }

            @Override
            public void change(String item) {
                super.change(item);
                if (item != null && customObjectInput != null) {
                    customObjectInput.settext(item);
                }
            }
        }, prev.pos("bl").add(UI.scale(0, 5)));

        if (startprop.object != null && !startprop.object.isEmpty() && objectList.contains(startprop.object)) {
            objectDropbox.change(startprop.object);
        } else if (!objectList.isEmpty()) {
            objectDropbox.change(objectList.get(0));
        }

        prev = add(new Label(L10n.get("carrier.carry_to_zone")), prev.pos("bl").add(UI.scale(0, 10)));
        zoneList = NContext.findVisibleCarrierOutZones();
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
                    g.text(L10n.get("carrier.no_zones"), Coord.z);
                }
            }

            @Override
            public void change(NArea item) {
                super.change(item);
            }
        }, prev.pos("bl").add(UI.scale(0, 5)));

        if (startprop.targetZoneId != null && NUtils.getArea(startprop.targetZoneId) != null) {
            NArea savedZone = NUtils.getArea(startprop.targetZoneId);
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
                prop = NCarrierProp.get(NUtils.getUI().sessInfo);
                if (prop != null) {
                    String selectedObject = customObjectInput.text();
                    if (selectedObject == null || selectedObject.trim().isEmpty()) {
                        selectedObject = objectDropbox.sel != null ? objectDropbox.sel : "";
                    }
                    prop.object = selectedObject;
                    prop.addToHistory(selectedObject);
                    NArea selectedZone = zoneDropbox.sel;
                    prop.targetZoneId = (selectedZone != null) ? selectedZone.id : null;
                    NCarrierProp.set(prop);
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

    public NCarrierProp prop = null;
}
