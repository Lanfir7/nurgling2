package haven.automated;


import haven.*;

import java.util.Iterator;

import static haven.OCache.posres;

public class LootNearestKnockedPlayer implements Runnable {
    private GameUI gui;

    public LootNearestKnockedPlayer(GameUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        Gob player = null;
        synchronized (gui.map.glob.oc) {
            for (Gob gob : gui.map.glob.oc) {
                try {
                    Resource res = gob.getres();
                    if (res != null && res.name.startsWith("gfx/borka/body")) {
                        if (gob.knocked) {
                            Coord2d plc = gui.map.player().rc;
                            if ((player == null || gob.rc.dist(plc) < player.rc.dist(plc)))
                                player = gob;
                        }
                    }
                } catch (Loading l) {
                }
            }
        }
        if (player == null)
            return;

        FlowerMenu.setNextSelection("Steal");
        gui.map.wdgmsg("click", Coord.z, player.rc.floor(posres), 3, 0, 0, (int) player.id, player.rc.floor(posres), 0, -1);

        if (gui.lootNearestKnockedPlayerThread != null) {
            gui.lootNearestKnockedPlayerThread.interrupt();
            gui.lootNearestKnockedPlayerThread = null;
        }
    }
}

