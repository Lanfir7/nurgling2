package haven.automated;


import haven.*;

import static haven.OCache.posres;
import static java.lang.Thread.sleep;

public class EnterNearestVehicle implements Runnable {
    private GameUI gui;

    public EnterNearestVehicle(GameUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        Gob vehicle = null;
        int vehicleType = 0;
        Gob player = gui.map.player();
        if (player == null)
            return;
        Coord3f raw = player.placed.getc();
        if(raw == null) // ND: This works for checking if the player's on foot.
            return;
        for (Gob gob : Utils.getAllGobs(gui)) {
                Resource res = null;
                try {
                    res = gob.getres();
                } catch (Loading l) {
                }
                if (res != null) { // TODO: ND: Need to fix or do more rigorous testing.
                                   //  Either the gob.occupants don't properly update when one of them leaves the rowboat, or something else is wrong
                    int type = 0;
                    if (res.name.equals("gfx/terobjs/vehicle/knarr") /*&& gob.occupants.size() < 10*/) {
                        type = 1;
                    } else if ( res.name.equals("gfx/terobjs/vehicle/snekkja") /*&& gob.occupants.size() < 4*/){
                        type = 1;
                    } else if ((res.name.equals("gfx/kritter/horse/stallion") || res.name.equals("gfx/kritter/horse/mare")) /*&& gob.occupants.isEmpty()*/) {
                        type = 2;
                    } else if (res.name.equals("gfx/terobjs/vehicle/skis-wilderness")) {
                        int rbuf = getRbuf(gob);
                        if (rbuf == 0 /*&& gob.occupants.isEmpty()*/)
                            type = 3;
                    } else if (res.name.equals("gfx/terobjs/vehicle/coracle")) {
                        int rbuf = getRbuf(gob);
                        if (rbuf == 22 /*&& gob.occupants.isEmpty()*/)
                            type = 4;
                    } else if (res.name.equals("gfx/terobjs/vehicle/wagon")) {
                        int rbuf = getRbuf(gob);
//                        if ((rbuf == 4 || rbuf == 6) && gob.occupants.size() < 6)
                            type = 5;
                    } else if (res.name.equals("gfx/terobjs/vehicle/rowboat")) {
                        int rbuf = getRbuf(gob);
                        // ND: Check if we can actually mount it. Is it in water? Is it occupied?
                        if ((rbuf == 50 /*&& gob.occupants.isEmpty()*/) // ND: The boat is in water, with 2 items in cargo, and 0 occupants.
                        || ((rbuf == 18 || rbuf == 20 || rbuf == 24 || rbuf == 34 || rbuf == 36 || rbuf == 40) /*&& gob.occupants.size() < 2*/) // ND: The boat is in water, with 1 item in cargo and < 2 occupants.
                        || ((rbuf == 2 || rbuf == 4 || rbuf == 8) /*&& gob.occupants.size() < 3*/)) // ND: The boat is in water, with no cargo and < 3 occupants (not full).
                            type = 999;
                    } else if (res.name.equals("gfx/terobjs/vehicle/spark")){
                        int rbuf = getRbuf(gob);
                        if ((rbuf == 0 /*&& gob.occupants.size() < 2*/) || (rbuf == 1/* && gob.occupants.isEmpty()*/)){
                            type = 999;
                        }
                    } else if (res.name.equals("gfx/terobjs/vehicle/dugout")) {
                        int rbuf = getRbuf(gob);
                        if (rbuf == 6 /*&& gob.occupants.isEmpty()*/)
                            type = 999;
                    } else {
                        continue;
                    }

                    double distFromPlayer = gob.rc.dist(player.rc);
                    if (type != 0 && distFromPlayer <= 20 * 20 && (vehicle == null || distFromPlayer < vehicle.rc.dist(player.rc))) {
                        vehicleType = type;
                        vehicle = gob;
                    }
                }
            }
            if (vehicle == null || vehicleType == 0)
                return;
            if (vehicleType == 1) {
                gui.map.wdgmsg("click", Coord.z, vehicle.rc.floor(posres), 3, 0, 0, (int) vehicle.id, vehicle.rc.floor(posres), 0, -1);
                gui.ui.rcvr.rcvmsg(gui.ui.lastWidgetID + 1, "cl", 0, gui.ui.modflags());
            } else if (vehicleType == 2) {
                FlowerMenu.setNextSelection("Giddyup!");
                gui.map.wdgmsg("click", Coord.z, vehicle.rc.floor(posres), 3, 0, 0, (int) vehicle.id, vehicle.rc.floor(posres), 0, -1);
                return;
            } else if (vehicleType == 3) {
                FlowerMenu.setNextSelection("Ski off");
                gui.map.wdgmsg("click", Coord.z, vehicle.rc.floor(posres), 3, 0, 0, (int) vehicle.id, vehicle.rc.floor(posres), 0, -1);
                return;
            } else if (vehicleType == 4) {
                FlowerMenu.setNextSelection("Into the blue yonder!");
                gui.map.wdgmsg("click", Coord.z, vehicle.rc.floor(posres), 3, 0, 0, (int) vehicle.id, vehicle.rc.floor(posres), 0, -1);
                return;
            } else if (vehicleType == 5) {
                FlowerMenu.setNextSelection("Ride");
                gui.map.wdgmsg("click", Coord.z, vehicle.rc.floor(posres), 3, 0, 0, (int) vehicle.id, vehicle.rc.floor(posres), 0, -1);
                return;
            } else {
                gui.map.wdgmsg("click",Coord.z, vehicle.rc.floor(posres), 3, 0, 0, (int) vehicle.id, vehicle.rc.floor(posres), 0, -1);
            }
        if (gui.enterNearestVehicleThread != null) {
            gui.enterNearestVehicleThread.interrupt();
            gui.enterNearestVehicleThread = null;
        }
    }

    private int getRbuf (Gob gob){
        Drawable d = gob.getattr(Drawable.class);
        if (d instanceof ResDrawable) {
            ResDrawable resDrawable = gob.getattr(ResDrawable.class);
            return resDrawable.sdt.checkrbuf(0);
        }
        return 0;
    }
}
