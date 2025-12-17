package haven.automated;

import haven.*;
import haven.Button;
import haven.Window;
import haven.automated.helpers.AreaSelectCallback;
import haven.render.RenderTree;

import java.awt.*;
import java.util.*;
import java.util.List;

import static haven.OCache.posres;

public class RoastingSpitBot extends Window implements Runnable {
    Button startbutton;
    private boolean active = false;
    private final GameUI gui;
    private boolean stop;
    private Gob fireplace = null;
    private final String[] spitroastableItems = { // ND: Incomplete list
            "gfx/invobjs/rabbit-clean",
            "gfx/invobjs/fish-",
            "gfx/invobjs/chicken-cleaned",
            "gfx/invobjs/bat-clean",
            "gfx/invobjs/mole-clean",
            "gfx/invobjs/rockdove-cleaned",
            "gfx/invobjs/mallard-cleaned",
            "gfx/invobjs/squirrel-clean",
    };
    double maxDistance = 2 * 11;
    public String roastingSpitOverlayName = "gfx/terobjs/roastspit";
    private boolean passiveMode = false;
    CheckBox passiveModeBox = null;

    public RoastingSpitBot(GameUI gui) {
        super(UI.scale(160, 50), "Roasting Spit Bot", true);
        this.gui = gui;

        startbutton = add(new Button(UI.scale(150), "Start") {
            @Override
            public void click() {
                active = !active;
                if (active) {
                    Gob theObject = null;
                    Gob player = gui.map.player();
                    Coord2d plc = player.rc;
                    for (Gob gob : Utils.getAllGobs(gui)) {
                        double distFromPlayer = gob.rc.dist(plc);
                        if (gob.id == gui.map.plgob || distFromPlayer >= maxDistance)
                            continue;
                        Resource res = null;
                        try {
                            res = gob.getres();
                        } catch (Loading l) {
                        }
                        if (res != null) {
                            if (res.name.equals("gfx/terobjs/pow")) {
                                if (AUtils.gobHasOverlay(gob, roastingSpitOverlayName)){
                                    if (distFromPlayer < maxDistance && (theObject == null || distFromPlayer < theObject.rc.dist(plc))) {
                                        theObject = gob;
                                    }
                                }
                            }
                        }
                    }
                    if (theObject == null) {
                        gui.error("Roasting Spit Bot: You're not standing next to a Fireplace that has a Roasting Spit!");
                        active = false;
                    } else {
                        fireplace = theObject;
                        gui.msg("Roasting Spit Bot: Started", Color.WHITE);
                        this.change("Stop");
                    }
                } else {
                    fireplace = null;
                    gui.msg("Roasting Spit Bot: Stopped", Color.WHITE);
                    this.change("Start");
                }
            }
        }, 0, 0);

        passiveModeBox = add(new CheckBox("Passive mode") {
            {a = passiveMode;}

            public void changed(boolean val) {
                passiveMode = val;
            }
        }, startbutton.pos("bl").adds(0, 6));

        pack();
        passiveModeBox.tooltip = RichText.render("If enabled, the bot will only roast once there is something on the spit and once done roasting it will wait for another item to be put on the roast", UI.scale(300));
    }

    private void sleep(int duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if ((sender == this) && (Objects.equals(msg, "close"))) {
            this.stop();
            reqdestroy();
            gui.roastingSpitBot = null;
            gui.roastingSpitThread = null;
        } else
            super.wdgmsg(sender, msg, args);
    }

    public void stop() {
        if (ui.gui.map.pfthread != null) {
            ui.gui.map.pfthread.interrupt();
        }
        if (gui.roastingSpitThread != null) {
            gui.roastingSpitThread.interrupt();
            gui.roastingSpitThread = null;
        }
        stop = true;
    }

    public boolean isSpitroastableItem(String input) {
        for (String item : spitroastableItems) {
            if (input.toLowerCase().contains(item.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public List <GItem> findSpitroastableItems(List <WItem> invItems) {
        List <GItem> foundSpitroastableItems = new ArrayList<>();
        for (Object item : invItems.toArray()) {
            if (item instanceof WItem) {
                WItem wi = (WItem) item;
                if (isSpitroastableItem(wi.item.resource().name)) {
                    foundSpitroastableItems.add(wi.item);
                }
            }
        }
        return foundSpitroastableItems;
    }

    public boolean readyToRoast() {
        if (fireplace != null && !fireplace.ols.isEmpty()) {
            Optional<Gob.Overlay> foundOverlay = fireplace.ols.stream()
                    .filter(ol -> ol != null && ol.spr != null && ol.spr.res != null && roastingSpitOverlayName.equals(ol.spr.res.name))
                    .findFirst();

            if (foundOverlay.isPresent()) {
                Gob.Overlay gobOverlay = foundOverlay.get();
                if(((RenderTree.TreeSlot)((ArrayList<?>) gobOverlay.slots).get(0)).children[0].children.length > 2 && ((RenderTree.TreeSlot)((ArrayList<?>) gobOverlay.slots).get(0)).children[0].children[2] != null)
                    return ((RenderTree.TreeSlot)((ArrayList<?>) gobOverlay.slots).get(0)).children[0].children[2].toString().contains("raw");
            }
        }
        return false;
    }

    public boolean isCooked() {
        if (fireplace != null && !fireplace.ols.isEmpty()) {
            Optional<Gob.Overlay> foundOverlay = fireplace.ols.stream()
                    .filter(ol -> ol != null && ol.spr != null && ol.spr.res != null && roastingSpitOverlayName.equals(ol.spr.res.name))
                    .findFirst();

            if (foundOverlay.isPresent()) {
                Gob.Overlay gobOverlay = foundOverlay.get();
                if(((RenderTree.TreeSlot)((ArrayList<?>) gobOverlay.slots).get(0)).children[0].children.length > 2 && ((RenderTree.TreeSlot)((ArrayList<?>) gobOverlay.slots).get(0)).children[0].children[2] != null)
                    return ((RenderTree.TreeSlot)((ArrayList<?>) gobOverlay.slots).get(0)).children[0].children[2].toString().contains("roast");
            }
        }
        return false;
    }

    @Override
    public void run() {
        while (!stop) {
            sleep(10);
            if (active && fireplace != null) {
                try {
                    if (!passiveMode) {
                        List<WItem> invItems = AUtils.getAllItemsFromAllInventoriesAndStacksExcludeBeltAndKeyring(this.gui);
                        List<GItem> foundSpitroastableItems = findSpitroastableItems(invItems);
                        if (!foundSpitroastableItems.isEmpty() && !readyToRoast() && !isCooked()) {
                            GItem spitroastableItem = foundSpitroastableItems.get(0);
                            sleep(1000);
                            putItemOnRoast(spitroastableItem);
                            startRoasting();
                            carve();
                        } else if (readyToRoast()) {
                            sleep(1000);
                            startRoasting();
                            carve();
                        } else if (isCooked()) {
                            sleep(1000);
                            carve();
                        } else {
                            gui.msg("Roasting Spit Bot: Done cooking!", Color.WHITE);
                            active = false;
                            startbutton.change("Start");
                        }
                    } else {
                        if (readyToRoast() && !isCooked()) {
                            startRoasting();
                        } else if (isCooked()) {
                            sleep(1000);
                            ui.gui.map.wdgmsg("click", Coord.z, ui.gui.map.player().rc.floor(posres), 1, 0);
                        }
                    }
                } catch (Exception e) {
                    gui.error("Roasting Spit Bot: Something went wrong, resetting...");
                    active = false;
                    startbutton.change("Start");
                    fireplace = null;
                }
            }
        }
    }

    public void putItemOnRoast (GItem item) {
        item.wdgmsg("take", Coord.z);
        sleep(500);
        if (AUtils.rightClickGobOverlayWithItem(this.gui, fireplace, roastingSpitOverlayName)) {
            sleep(2000);
        } else {
            gui.error("Roasting Spit Bot: The Roasting Spit is gone!");
            active = false;
            fireplace = null;
            startbutton.change("Start");
        }
    }

    public void startRoasting() {
        AUtils.rightClickGobOverlayAndSelectOption(this.gui, fireplace,0, roastingSpitOverlayName);
        sleep(2000);
        try {
            waitProgBarRoastingSpit(gui);
        } catch (InterruptedException ignored) {
        }
    }

    public void carve() {
        if (isCooked()){
            AUtils.rightClickGobOverlayAndSelectOption(this.gui, fireplace,1, roastingSpitOverlayName);
            while (gui.prog != null)
                sleep(1000);
        }
    }

    public static void waitProgBarRoastingSpit(GameUI gui) throws InterruptedException {
        double maxProg = 0;
        while (gui.prog != null && gui.prog.prog >= 0) {
            if (maxProg > gui.prog.prog)
                break;
            maxProg = gui.prog.prog;
            Thread.sleep(40);
        }
    }

    @Override
    public void reqdestroy() {
        Utils.setprefc("wndc-roastingSpitBotWindow", this.c);
        super.reqdestroy();
    }
}
