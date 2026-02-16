package nurgling.actions;

import haven.*;
import haven.res.ui.invsq.InvSquare;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitLifted;
import nurgling.tasks.WaitWindow;
import nurgling.tools.Finder;

import static haven.OCache.posres;
import static nurgling.tools.Finder.findLiftedbyPlayer;

public class TakeFromVehicle implements Action {
    private final Gob vehicle;
    /** -1 = take first available; 0..N = take from specific slot index */
    private final int slotIndex;
    /** when true, skip PathFinder and Open if Wagon window already exists */
    private final boolean reuseOpen;

    public TakeFromVehicle(Gob vehicle) {
        this(vehicle, -1, false);
    }

    public TakeFromVehicle(Gob vehicle, int slotIndex) {
        this(vehicle, slotIndex, false);
    }

    public TakeFromVehicle(Gob vehicle, int slotIndex, boolean reuseOpen) {
        this.vehicle = vehicle;
        this.slotIndex = slotIndex;
        this.reuseOpen = reuseOpen;
    }

    @Override
    public Results run ( NGameUI gui )
            throws InterruptedException {
        new PathFinder(vehicle).run(gui);
        if (vehicle.ngob.name.contains("cart")) {
            int start = (slotIndex >= 0 && slotIndex < 6) ? slotIndex : 0;
            int stop = (slotIndex >= 0 && slotIndex < 6) ? slotIndex + 1 : 6;
            int mul = 4 << start;
            for (int i = start; i < stop; i++) {
                if ((vehicle.ngob.getModelAttribute() & mul) == mul) {
                    gui.map.wdgmsg("click", Coord.z, vehicle.rc.floor(posres), 3, 0, 0, (int) vehicle.id, vehicle.rc.floor(posres),
                            0, i+2);
                    NUtils.addTask(new NTask() {
                        @Override
                        public boolean check() {
                            return Finder.findLiftedbyPlayer() != null;
                        }
                    });
                    return Results.SUCCESS();
                }
                mul *= 2;
            }
            return Results.FAIL();
        }
        else if(vehicle.ngob.name.contains("snekkja")) {
            new PathFinder(vehicle).run(gui);
            new SelectFlowerAction("Cargo", vehicle).run(gui);
            NUtils.addTask(new WaitWindow("Snekkja"));
            java.util.List<Widget> slots = null;
            for (Widget widget : NUtils.getGameUI().getWindow("Snekkja").children()) {
                if (widget.children().size() >= 16) {
                    slots = new java.util.ArrayList<>(widget.children());
                    break;
                }
            }
            if (slots == null) return Results.FAIL();
            int start = (slotIndex >= 0 && slotIndex < slots.size()) ? slotIndex : 0;
            int end = (slotIndex >= 0 && slotIndex < slots.size()) ? slotIndex + 1 : slots.size();
            for (int i = start; i < end; i++) {
                Widget child = slots.get(i);
                if (!(child instanceof InvSquare)) {
                    child.wdgmsg("click", UI.scale(15,15),1,0);
                    NUtils.addTask(new NTask() {
                        @Override
                        public boolean check() {
                            return Finder.findLiftedbyPlayer() != null;
                        }
                    });
                    return Results.SUCCESS();
                }
            }
            return Results.FAIL();
        }
        else if(vehicle.ngob.name.contains("wagon")) {
            if (!reuseOpen || NUtils.getGameUI().getWindow("Wagon") == null) {
                new PathFinder(vehicle).run(gui);
                new SelectFlowerAction("Open", vehicle).run(gui);
                NUtils.addTask(new WaitWindow("Wagon"));
            }
            Widget wnd = NUtils.getGameUI().getWindow("Wagon");
            if (wnd == null) return Results.FAIL();
            java.util.List<Widget> allChildren = null;
            for (Widget widget : wnd.children()) {
                if (widget.children().size() >= 20) {
                    allChildren = new java.util.ArrayList<>(widget.children());
                    break;
                }
            }
            if (allChildren == null) return Results.FAIL();
            java.util.List<Widget> items = new java.util.ArrayList<>();
            for (Widget child : allChildren) {
                if (!(child instanceof InvSquare))
                    items.add(child);
            }
            if (items.isEmpty()) return Results.FAIL();
            int idx = (slotIndex >= 0 && slotIndex < items.size()) ? slotIndex : 0;
            Widget target = items.get(idx);
            target.wdgmsg("click", UI.scale(15,15),1,0);
            NUtils.addTask(new NTask() {
                @Override
                public boolean check() {
                    return Finder.findLiftedbyPlayer() != null;
                }
            });
            return Results.SUCCESS();
        }
        return Results.FAIL();
    }
}