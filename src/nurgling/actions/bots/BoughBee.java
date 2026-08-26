package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.*;
import nurgling.conf.NAreaRad;
import nurgling.conf.NBoughBeeProp;
import nurgling.tasks.*;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static haven.MCache.tilesz;
import static haven.OCache.posres;

public class BoughBee implements Action {
    private static final NAlias BOUGH_ITEMS = new NAlias("Bough", "bough");
    private static final NAlias BRANCH_ITEMS = new NAlias("Branch", "branch");
    private static final NAlias TREES = new NAlias("gfx/terobjs/trees");
    private static final NAlias BPYRE = new NAlias("bpyre");
    private static final NAlias WILD_HIVE = new NAlias("wildbees/wildbeehive");

    private final Gob targetHive;

    public BoughBee() {
        this(null);
    }

    public BoughBee(Gob targetHive) {
        this.targetHive = targetHive;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        nurgling.widgets.bots.BoughBee w = null;
        NBoughBeeProp prop;
        if (!NBoughBeeProp.useSettingsGui()) {
            prop = NBoughBeeProp.runDefaults();
        } else {
            try {
                NUtils.getUI().core.addTask(new WaitCheckable(NUtils.addCentered((w = new nurgling.widgets.bots.BoughBee()))));
                prop = w.prop;
            } catch (InterruptedException e) {
                throw e;
            } finally {
                if (w != null)
                    w.destroy();
            }
            if (prop == null) {
                return Results.ERROR("No config");
            }
        }

        @SuppressWarnings("unchecked")
        ArrayList<NAreaRad> animalRads = (ArrayList<NAreaRad>) NConfig.get(NConfig.Key.animalrad);
        ArrayList<String> dangerousAnimals = new ArrayList<>();
        if (animalRads != null) {
            for (NAreaRad rad : animalRads) {
                dangerousAnimals.add(rad.name);
            }
        }

        Gob beehive = null;
        Gob pyre;
        if (targetHive != null) {
            beehive = resolveTargetHive();
            if (beehive == null)
                return Results.ERROR("Beehive disappeared");
            pyre = findNearbyPyre(beehive);
        } else {
            pyre = findNearbyPyre();
        }
        if (pyre == null) {
            if (beehive == null)
                beehive = findNearbyHive();
            if (beehive == null)
                return Results.ERROR("No wild beehive within 5 tiles");

            if (!BoughBeeMaterials.hasBoughsForPyre(countItems(gui, BOUGH_ITEMS))) {
                if (!prop.harvestTrees)
                    return Results.ERROR("Need 4 boughs for Bough Pyre");
                Results boughs = collectBoughs(gui);
                if (!boughs.IsSuccess())
                    return boughs;
                if (!BoughBeeMaterials.hasBoughsForPyre(countItems(gui, BOUGH_ITEMS)))
                    return Results.ERROR("Need 4 boughs for Bough Pyre");
            }

            Coord2d spot = findFreeSpotNear(beehive);
            if (spot == null)
                return Results.ERROR("No free tile near the beehive for Bough Pyre");

            pyre = placeBoughPyre(gui, spot);
            if (pyre == null)
                return Results.ERROR("Bough Pyre was not placed");
        }

        if (BoughBeeMaterials.shouldCollectBranchesForLight(prop.harvestTrees, countItems(gui, BRANCH_ITEMS))) {
            Results branches = collectBranches(gui);
            if (!branches.IsSuccess())
                return branches;
        }

        ArrayList<Gob> toLight = new ArrayList<>();
        toLight.add(pyre);
        Results lit = new LightObject(toLight).run(gui);
        if (!lit.IsSuccess())
            return Results.ERROR("Failed to light Bough Pyre");

        pyre = Finder.findGob(pyre.id);
        if (pyre == null)
            return Results.ERROR("Bough Pyre disappeared after lighting");
        if (!LightObject.isBpyreLit(pyre.ngob.getModelAttribute(),
                NUtils.isOverlay(pyre, new NAlias("smoke", "flame", "fire", "ember"))))
            return Results.ERROR("Bough Pyre is not lit");

        placePyreTimer(gui, pyre);

        final NGameUI finalGui = gui;
        final NBoughBeeProp finalProp = prop;
        final Gob finalBpyre = pyre;

        if (beehive == null)
            beehive = Finder.findGob(pyre.rc, WILD_HIVE, null, 50.0);
        if (beehive == null) {
            return Results.ERROR("No beehive found");
        }
        final Gob finalBeehive = beehive;

        while (true) {
            NUtils.getUI().core.addTask(new NTask() {
                @Override
                public boolean check() {
                    try {
                        // Check for dangerous players (this session's own detections)
                        if (finalGui.alarmWdg != null && finalGui.alarmWdg.hasBorkas()) {
                            if (!finalProp.onPlayerAction.equals("nothing")) {
                                performSafetyAction(finalGui, finalProp.onPlayerAction);
                                return true;
                            }
                        }

                        for (String animalPattern : dangerousAnimals) {
                            Gob animal = Finder.findGob(NUtils.player().rc, new NAlias(animalPattern), null, 200.0);
                            if (animal != null) {
                                performSafetyAction(finalGui, finalProp.onAnimalAction);
                                return true;
                            }
                        }

                        Gob currentBpyre = Finder.findGob(finalBpyre.id);
                        if (currentBpyre == null) {
                            Gob currentBeehive = Finder.findGob(finalBeehive.id);
                            if (currentBeehive != null) {
                                ResDrawable rd = currentBeehive.getattr(ResDrawable.class);
                                if (rd != null && rd.calcMarker() == 0) {
                                    return true;
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        return true;
                    }
                    return false;
                }
            });

            // Check if we exited due to safety action
            if (finalGui.alarmWdg != null && finalGui.alarmWdg.hasBorkas() && !finalProp.onPlayerAction.equals("nothing")) {
                return Results.SUCCESS();
            }
            for (String animalPattern : dangerousAnimals) {
                Gob animal = Finder.findGob(NUtils.player().rc, new NAlias(animalPattern), null, 200.0);
                if (animal != null) {
                    return Results.SUCCESS();
                }
            }

            Gob currentBeehive = Finder.findGob(finalBeehive.id);
            if (currentBeehive == null) {
                performSafetyAction(finalGui, finalProp.afterHarvestAction);
                return Results.SUCCESS();
            }

            new PathFinder(currentBeehive).run(finalGui);
            new SelectFlowerAction("Raid!", currentBeehive).run(finalGui);
            NUtils.getUI().core.addTask(new WaitGobRemoval(finalBeehive.id));
            performSafetyAction(finalGui, finalProp.afterHarvestAction);
            return Results.SUCCESS();
        }
    }

    private int countItems(NGameUI gui, NAlias alias) throws InterruptedException {
        int n = 0;
        for (WItem it : gui.getInventory().getItems(alias)) {
            GItem.Amount amount = ((NGItem) it.item).getInfo(GItem.Amount.class);
            n += BoughBeeMaterials.stackPieces(amount != null ? amount.itemnum() : null);
        }
        return n;
    }

    private Results collectBoughs(NGameUI gui) throws InterruptedException {
        ArrayList<Gob> trees = Finder.findGobs(TREES);
        trees.removeIf(tree -> tree.ngob == null || !BoughBeeMaterials.isBoughTree(tree.ngob.name));
        trees.sort(NUtils.d_comp);
        for (Gob tree : trees) {
            if (BoughBeeMaterials.boughsNeeded(countItems(gui, BOUGH_ITEMS)) == 0)
                return Results.SUCCESS();
            new CollectFromGob(tree, BoughBeeMaterials.TAKE_BOUGH, BoughBeeMaterials.TREE_PICK_POSE,
                    new Coord(2, 1), BOUGH_ITEMS, true, BoughBeeMaterials.BOUGHS_FOR_PYRE).run(gui);
        }
        if (BoughBeeMaterials.boughsNeeded(countItems(gui, BOUGH_ITEMS)) > 0)
            return Results.ERROR("Could not collect 4 boughs from nearby trees");
        return Results.SUCCESS();
    }

    private Results collectBranches(NGameUI gui) throws InterruptedException {
        ArrayList<Gob> trees = Finder.findGobs(TREES);
        trees.removeIf(tree -> tree.ngob == null || !BoughBeeMaterials.isLivingTree(tree.ngob.name));
        trees.sort(NUtils.d_comp);
        for (Gob tree : trees) {
            if (!BoughBeeMaterials.needsBranches(countItems(gui, BRANCH_ITEMS)))
                return Results.SUCCESS();
            new PathFinder(tree).run(gui);
            new CollectFromGob(tree, BoughBeeMaterials.TAKE_BRANCH, BoughBeeMaterials.TREE_PICK_POSE,
                    new Coord(1, 2), BRANCH_ITEMS, true, BoughBeeMaterials.BRANCHES_FOR_LIGHT).run(gui);
        }
        if (BoughBeeMaterials.needsBranches(countItems(gui, BRANCH_ITEMS)))
            return Results.ERROR("Could not collect branches from nearby trees");
        return Results.SUCCESS();
    }

    private Gob findNearbyPyre() throws InterruptedException {
        return findNearbyPyre(NUtils.player());
    }

    private Gob findNearbyPyre(Gob origin) throws InterruptedException {
        if (origin == null)
            return null;
        Gob gob = Finder.findGob(origin.rc, BPYRE, null, BoughBeeMaterials.NEAR_PYRE_TILES * tilesz.x + 0.01);
        if (gob == null)
            return null;
        return BoughBeeMaterials.isNearbyPyre(origin.rc.dist(gob.rc), tilesz.x) ? gob : null;
    }

    private Gob resolveTargetHive() {
        if (targetHive == null)
            return null;
        Gob gob = Finder.findGob(targetHive.id);
        if (gob == null || gob.ngob == null || !BoughBeeMaterials.isWildHive(gob.ngob.name))
            return null;
        return gob;
    }

    private Gob findNearbyHive() throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null)
            return null;
        Gob gob = Finder.findGob(player.rc, WILD_HIVE, null, BoughBeeMaterials.HIVE_SEARCH_TILES * tilesz.x + 0.01);
        if (gob == null)
            return null;
        return BoughBeeMaterials.isHiveInRange(player.rc.dist(gob.rc), tilesz.x) ? gob : null;
    }

    private Coord2d findFreeSpotNear(Gob hive) {
        NHitBox hitBox = NHitBox.findCustom("gfx/terobjs/bpyre");
        if (hitBox == null)
            hitBox = new NHitBox(new Coord(-5, -5), new Coord(5, 5));
        double r = BoughBeeMaterials.PLACE_NEAR_HIVE_TILES * tilesz.x;
        Pair<Coord2d, Coord2d> area = new Pair<>(hive.rc.sub(r, r), hive.rc.add(r, r));
        return Finder.getFreePlace(area, hitBox, 0, hive.rc);
    }

    private Gob placeBoughPyre(NGameUI gui, Coord2d pos) throws InterruptedException {
        Set<Long> beforePyre = new HashSet<>();
        for (Gob g : Finder.findGobs(BPYRE))
            beforePyre.add(g.id);
        Set<Long> beforeCons = new HashSet<>();
        for (Gob g : Finder.findGobs(new NAlias("consobj")))
            beforeCons.add(g.id);

        MenuGrid.Pagina pag = findPagina(gui, BoughBeeMaterials.PYRE_BUILD);
        if (pag == null || pag.button() == null) {
            gui.error("Bough Pyre build not found");
            return null;
        }

        pag.button().use(new MenuGrid.Interaction(1, 0));
        NUtils.addTask(new WaitPlob(false));
        if (gui.map.placing == null)
            pag.button().use(new MenuGrid.Interaction(1, 0));
        NUtils.addTask(new WaitPlob(false));
        if (gui.map.placing == null)
            return null;

        NHitBox hitBox = NHitBox.findCustom("gfx/terobjs/bpyre");
        if (hitBox == null)
            hitBox = new NHitBox(new Coord(-5, -5), new Coord(5, 5));
        PathFinder pf = new PathFinder(NGob.getDummy(pos, 0, hitBox), true);
        pf.run(gui);
        gui.map.wdgmsg("place", pos.floor(posres), 0, 1, 0);
        NUtils.addTask(new WaitNamedConstruction(pos));

        Gob pyre = findNewPyre(beforePyre);
        if (pyre != null)
            return pyre;
        Gob cons = Finder.findGob(pos);
        if (cons != null && cons.ngob != null && NParser.checkName(cons.ngob.name, "gfx/terobjs/consobj"))
            return finishPyreConstruction(gui, cons);
        cons = findNewConsobj(beforeCons);
        if (cons != null)
            return finishPyreConstruction(gui, cons);
        return Finder.findGob(pos, BPYRE, null, 15);
    }

    private Gob finishPyreConstruction(NGameUI gui, Gob consobj) throws InterruptedException {
        Coord2d pos = consobj.rc;
        long id = consobj.id;

        Gob pyre = Finder.findGob(pos, BPYRE, null, 15);
        if (pyre != null)
            return pyre;

        Window window = findPyreBuildWindow(gui);
        if (window == null) {
            Gob player = NUtils.player();
            Gob site = Finder.findGob(id);
            if (site == null)
                site = Finder.findGob(pos);
            if (site != null && player != null && player.rc.dist(site.rc) > 22) {
                new PathFinder(site).run(gui);
                site = Finder.findGob(id);
                if (site == null)
                    site = Finder.findGob(pos);
            }
            if (site != null)
                NUtils.rclickGob(site);
            NUtils.addTask(new WaitPyreBuildWindow(gui, pos));
            window = findPyreBuildWindow(gui);
        }

        pyre = Finder.findGob(pos, BPYRE, null, 15);
        if (pyre != null)
            return pyre;

        Gob gob = Finder.findGob(id);
        if (gob == null)
            gob = Finder.findGob(pos);
        int attempts = 0;
        while (gob != null && gob.ngob != null
                && NParser.checkName(gob.ngob.name, "gfx/terobjs/consobj") && attempts++ < 10) {
            window = findPyreBuildWindow(gui);
            if (window == null) {
                NUtils.rclickGob(gob);
                NUtils.addTask(new WaitPyreBuildWindow(gui, pos));
                window = findPyreBuildWindow(gui);
            }
            if (window != null)
                NUtils.startBuild(window);

            NUtils.addTask(new WaitBuildProgress(gui));
            WaitBuildState wbs = new WaitBuildState();
            NUtils.addTask(wbs);
            if (wbs.getState() == WaitBuildState.State.TIMEFORDRINK) {
                if (!(new Drink(0.9, false).run(gui)).IsSuccess())
                    return null;
            } else if (wbs.getState() == WaitBuildState.State.DANGER) {
                return null;
            }
            pyre = Finder.findGob(pos, BPYRE, null, 15);
            if (pyre != null)
                return pyre;
            gob = Finder.findGob(id);
            if (gob == null)
                gob = Finder.findGob(pos);
        }
        return Finder.findGob(pos, BPYRE, null, 15);
    }

    private static Window findPyreBuildWindow(NGameUI gui) {
        if (gui == null)
            return null;
        Window exact = gui.getWindow(BoughBeeMaterials.PYRE_BUILD);
        if (exact != null)
            return exact;
        for (Widget w = gui.lchild; w != null; w = w.prev) {
            if (!(w instanceof Window))
                continue;
            Window wnd = (Window) w;
            if (BoughBeeMaterials.isPyreWindowCap(wnd.cap) || hasBuildButton(wnd))
                return wnd;
        }
        return null;
    }

    private static boolean hasBuildButton(Window window) {
        for (Widget sp = window.lchild; sp != null; sp = sp.prev) {
            if (sp instanceof Button && ((Button) sp).text != null
                    && "Build".equals(((Button) sp).text.text))
                return true;
        }
        return false;
    }

    private static class WaitNamedConstruction extends NTask {
        private final Coord2d pos;

        WaitNamedConstruction(Coord2d pos) {
            this.pos = pos;
        }

        @Override
        public boolean check() {
            try {
                Gob pyre = Finder.findGob(pos, BPYRE, null, 15);
                if (pyre != null)
                    return true;
                Gob gob = Finder.findGob(pos);
                if (gob == null || gob.ngob == null)
                    return false;
                return BoughBeeMaterials.constructionSiteReady(gob.ngob.name);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
        }
    }

    private static class WaitPyreBuildWindow extends NTask {
        private final NGameUI gui;
        private final Coord2d pos;

        WaitPyreBuildWindow(NGameUI gui, Coord2d pos) {
            this.gui = gui;
            this.pos = pos;
        }

        @Override
        public boolean check() {
            boolean hasWindow = findPyreBuildWindow(gui) != null;
            boolean hasPyre = false;
            boolean consobjExists = false;
            try {
                hasPyre = Finder.findGob(pos, BPYRE, null, 15) != null;
                Gob gob = Finder.findGob(pos);
                consobjExists = gob != null && gob.ngob != null
                        && gob.ngob.name != null && gob.ngob.name.contains("consobj");
                if (!consobjExists) {
                    for (Gob g : Finder.findGobs(new NAlias("consobj"))) {
                        if (g.rc != null && g.rc.dist(pos) < 15) {
                            consobjExists = true;
                            break;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
            return BoughBeeMaterials.pyreBuildWindowWaitDone(hasWindow, hasPyre, consobjExists);
        }
    }

    private static class WaitBuildProgress extends NTask {
        private final NGameUI gui;
        private int count = 0;

        WaitBuildProgress(NGameUI gui) {
            this.gui = gui;
        }

        @Override
        public boolean check() {
            return gui.prog != null || count++ > 100;
        }
    }

    private Gob findNewPyre(Set<Long> before) {
        return findNewPyreStatic(NUtils.getGameUI(), before);
    }

    private Gob findNewConsobj(Set<Long> before) {
        return findNewConsobjStatic(NUtils.getGameUI(), before);
    }

    private static Gob findNewPyreStatic(NGameUI gui, Set<Long> before) {
        if (gui == null)
            return null;
        for (Gob g : Finder.findGobs(BPYRE)) {
            if (!before.contains(g.id))
                return g;
        }
        return null;
    }

    private static Gob findNewConsobjStatic(NGameUI gui, Set<Long> before) {
        if (gui == null)
            return null;
        for (Gob g : Finder.findGobs(new NAlias("consobj"))) {
            if (before.contains(g.id))
                continue;
            String built = consobjBuiltName(g);
            if (built == null || BoughBeeMaterials.isPyreBuild(g.ngob.name, built))
                return g;
        }
        return null;
    }

    private static String consobjBuiltName(Gob gob) {
        if (gob == null)
            return null;
        ResDrawable rd = gob.getattr(ResDrawable.class);
        if (rd == null || !(rd.spr instanceof haven.res.gfx.terobjs.consobj.Consobj))
            return null;
        haven.res.gfx.terobjs.consobj.Consobj cons = (haven.res.gfx.terobjs.consobj.Consobj) rd.spr;
        if (cons.built == null || cons.built.res == null)
            return null;
        try {
            Resource res = cons.built.res.get();
            return res != null ? res.name : null;
        } catch (Loading e) {
            return null;
        }
    }

    private MenuGrid.Pagina findPagina(NGameUI gui, String name) {
        if (gui.menu == null)
            return null;
        for (MenuGrid.Pagina pb : gui.menu.paginae) {
            try {
                if (pb.button() != null && name.equals(pb.button().name()))
                    return pb;
            } catch (Loading ignored) {
            }
        }
        return null;
    }

    public static void placePyreTimer(NGameUI gui, Gob pyre) {
        if (gui == null || pyre == null || pyre.rc == null)
            return;
        if (gui.mmap == null || gui.mmap.sessloc == null || gui.localizedResourceTimerService == null)
            return;
        Coord tileCoords = pyre.rc.floor(tilesz).add(gui.mmap.sessloc.tc);
        gui.localizedResourceTimerService.createTimer(
                gui.mmap.sessloc.seg.id,
                tileCoords,
                "Bough Pyre",
                LocalizedResourceTimer.BOUGH_PYRE_TYPE,
                LocalizedResourceTimer.BOUGH_PYRE_READY_MS,
                "Bough Pyre",
                LocalizedResourceTimer.BOUGH_PYRE_AUTO_REMOVE_MS,
                LocalizedResourceTimer.BOUGH_PYRE_ICON);
    }

    private void performSafetyAction(NGameUI gui, String action) throws InterruptedException {
        switch (action) {
            case "logout":
                gui.act("lo");
                break;
            case "travel hearth":
                gui.act("travel", "hearth");
                break;
            case "nothing":
            default:
                break;
        }
    }
}
