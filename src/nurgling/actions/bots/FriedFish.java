package nurgling.actions.bots;

import haven.*;
import haven.res.gfx.terobjs.roastspit.Roastspit;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitCarveState;
import nurgling.tasks.WaitPose;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

public class FriedFish implements Action {

    NAlias powname = new NAlias(new ArrayList<String>(Arrays.asList("gfx/terobjs/pow")));

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        NContext context = new NContext(gui);
        String insaId = context.createArea("Please select area with raw fish or cleaned carcasses", Resource.loadsimg("baubles/rawFish"));
        NArea insaArea = context.goToAreaById(insaId);

        String outsaId = context.createArea("Please select area for results", Resource.loadsimg("baubles/prepFish"));
        NArea outsaArea = context.goToAreaById(outsaId);

        Gob player = NUtils.player();
        if (player == null) {
            return Results.ERROR("No player");
        }

        ArrayList<Gob> usablePows = new ArrayList<>();
        ArrayList<Coord2d> powSpots = new ArrayList<>();
        for (Gob gob : Finder.findGobs(powname)) {
            if (gob.ngob == null) {
                continue;
            }
            boolean hasSpit = gob.findol(Roastspit.class) != null;
            if (!FriedFishMaterials.isUsableRoastspitPow(gob.ngob.getModelAttribute(), hasSpit)) {
                continue;
            }
            usablePows.add(gob);
            powSpots.add(gob.rc);
        }
        Coord2d nearestSpot = FriedFishMaterials.closestSpot(player.rc, powSpots);
        Gob closestPow = null;
        for (int i = 0; i < powSpots.size(); i++) {
            if (powSpots.get(i) == nearestSpot) {
                closestPow = usablePows.get(i);
                break;
            }
        }
        if (closestPow == null) {
            return Results.ERROR("No fireplace with roast spit");
        }
        ArrayList<Gob> pows = new ArrayList<>();
        pows.add(closestPow);

        Pair<Coord2d, Coord2d> inRc = areaRc(insaArea);
        Pair<Coord2d, Coord2d> outRc = areaRc(outsaArea);

        ArrayList<Container> containers = new ArrayList<>();
        if (outRc != null) {
            for (Gob sm : Finder.findGobs(outRc, new NAlias(new ArrayList<>(NContext.contcaps.keySet())))) {
                Container cand = new Container(sm, NContext.contcaps.get(sm.ngob.name), null);
                cand.initattr(Container.Space.class);
                containers.add(cand);
            }
        }
        boolean toContainers = FriedFishMaterials.toContainers(!containers.isEmpty());
        boolean fromInventory = FriedFishMaterials.fromInventory(inRc != null && Finder.findGob(inRc, new NAlias("stockpile")) != null);

        NAlias rawFish = FriedFishMaterials.roastableRaw();

        while (shouldKeepWorking(gui, fromInventory, inRc, pows, rawFish)) {
            boolean readyToWork = false;
            for (Gob gob : pows) {
                Gob.Overlay ol = gob.findol(Roastspit.class);
                String content = ((Roastspit) ol.spr).getContent();
                if (FriedFishMaterials.isSpitReadyToWork(content, gob.ngob.getModelAttribute())) {
                    readyToWork = true;
                    break;
                }
            }
            if (!readyToWork) {
                ArrayList<Gob> borkas = Finder.findGobs(new NAlias("borka"));
                for (Gob gob : pows) {
                    boolean busy = false;
                    for (Gob borka : borkas) {
                        Following fl;
                        if ((fl = borka.getattr(Following.class)) != null && fl.tgt == gob.id) {
                            busy = true;
                            break;
                        }
                    }
                    if (!busy) {
                        new PathFinder(gob).run(gui);
                        Gob.Overlay ol = gob.findol(Roastspit.class);
                        new SelectFlowerAction("Turn", gob, (Roastspit) ol.spr).run(gui);
                        NUtils.addTask(new WaitPose(NUtils.player(), "gfx/borka/roasting"));
                        NUtils.addTask(new NTask() {
                            @Override
                            public boolean check() {
                                for (Gob waiting : pows) {
                                    Gob.Overlay waitingOl = waiting.findol(Roastspit.class);
                                    String content = ((Roastspit) waitingOl.spr).getContent();
                                    if (FriedFishMaterials.isSpitReadyToWork(content, waiting.ngob.getModelAttribute())) {
                                        return true;
                                    }
                                }
                                return false;
                            }
                        });
                        break;
                    }
                }
            }


            for (Gob gob : pows) {
                Gob.Overlay ol = gob.findol(Roastspit.class);
                String content = ((Roastspit) ol.spr).getContent();
                if (content != null) {
                    while (!content.contains("raw")) {
                        new PathFinder(gob).run(gui);
                        new SelectFlowerAction("Carve", gob, ((Roastspit) ol.spr)).run(gui);
                        NUtils.addTask(new WaitPose(NUtils.player(), "gfx/borka/carving"));
                        WaitCarveState wcs = new WaitCarveState(gob);
                        NUtils.addTask(wcs);
                        if (wcs.getState() == WaitCarveState.State.NOCONTENT) {
                            break;
                        }
                        if (wcs.getState() == WaitCarveState.State.NOFREESPACE) {
                            if (toContainers) {
                                transferCooked(gui, containers);
                            } else {
                                return Results.ERROR("Inventory full");
                            }
                        }
                    }
                }
            }

            if (toContainers && !NUtils.getGameUI().getInventory().getItems("Spitroast").isEmpty()) {
                transferCooked(gui, containers);
            }

            for (Gob gob : pows) {
                Gob.Overlay ol = gob.findol(Roastspit.class);
                String content = ((Roastspit) ol.spr).getContent();
                if (content == null) {
                    if (!putFishOnSpit(gui, gob, ol, fromInventory, inRc, rawFish)) {
                        break;
                    }
                }
            }

            if (!new FillFuelPowOrCauldron(context, pows, 1).run(gui).IsSuccess())
                return Results.FAIL();
            ArrayList<String> flighted = new ArrayList<>();
            for (Gob pow : pows) {
                flighted.add(pow.ngob.hash);
            }
            if (!new LightGob(flighted, 4).run(gui).IsSuccess())
                return Results.ERROR("I can't start a fire");

        }
        return Results.SUCCESS();
    }

    private static Pair<Coord2d, Coord2d> areaRc(NArea area) {
        if (area == null || area.space == null || area.space.space == null || area.space.space.isEmpty()) {
            return null;
        }
        return area.getRCArea();
    }

    private static boolean shouldKeepWorking(NGameUI gui, boolean fromInventory, Pair<Coord2d, Coord2d> inRc, ArrayList<Gob> pows, NAlias rawFish) throws InterruptedException {
        boolean hasPiles = inRc != null && Finder.findGob(inRc, new NAlias("stockpile")) != null;
        boolean hasInvFish = !gui.getInventory().getItems(rawFish).isEmpty();
        boolean spitHasContent = false;
        for (Gob gob : pows) {
            Gob.Overlay ol = gob.findol(Roastspit.class);
            if (ol != null && ((Roastspit) ol.spr).getContent() != null) {
                spitHasContent = true;
                break;
            }
        }
        return FriedFishMaterials.shouldKeepWorking(fromInventory, hasPiles, hasInvFish, spitHasContent);
    }

    private static void transferCooked(NGameUI gui, ArrayList<Container> containers) throws InterruptedException {
        for (Container container : containers) {
            if (container.getattr(Container.Space.class) != null) {
                Container.Space space = (Container.Space) container.getattr(Container.Space.class);
                if (!space.isReady() || (Integer) space.getRes().get(Container.Space.FREESPACE) != 0) {
                    new TransferToContainer(container, new NAlias("Spitroast")).run(gui);
                }
            }
        }
    }

    private static boolean putFishOnSpit(NGameUI gui, Gob gob, Gob.Overlay ol, boolean fromInventory, Pair<Coord2d, Coord2d> inRc, NAlias rawFish) throws InterruptedException {
        if (fromInventory) {
            ArrayList<WItem> items = gui.getInventory().getItems(rawFish);
            if (items.isEmpty()) {
                return false;
            }
            new PathFinder(gob).run(gui);
            NUtils.takeItemToHand(items.get(0));
            NUtils.activateRoastspit(ol);
            NUtils.addTask(new NTask() {
                @Override
                public boolean check() {
                    return NUtils.getGameUI().vhand == null && ((Roastspit) ol.spr).getContent() != null;
                }
            });
            return true;
        }

        if (NUtils.getGameUI().getInventory().getNumberFreeCoord(new Coord(1, 3)) <= 0
                || NUtils.getGameUI().getInventory().getNumberFreeCoord(new Coord(2, 1)) <= 0) {
            return false;
        }
        Gob pile = Finder.findGob(inRc, new NAlias("stockpile"));
        if (pile == null) {
            return false;
        }
        new PathFinder(pile).run(gui);
        new OpenTargetContainer("Stockpile", pile).run(gui);
        TakeItemsFromPile tifp = new TakeItemsFromPile(pile, gui.getStockpile(), 1);
        tifp.run(gui);
        LinkedList<NGItem> targetItems = new LinkedList<>(tifp.newItems());
        if (targetItems.isEmpty()) {
            return false;
        }
        new PathFinder(gob).run(gui);
        NUtils.takeItemToHand(targetItems.pollFirst());
        NUtils.activateRoastspit(ol);
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                return NUtils.getGameUI().vhand == null && ((Roastspit) ol.spr).getContent() != null;
            }
        });
        return true;
    }
}
