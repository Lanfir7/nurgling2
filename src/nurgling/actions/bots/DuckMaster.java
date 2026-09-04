package nurgling.actions.bots;

import haven.Coord;
import haven.Gob;
import haven.Inventory;
import haven.WItem;
import nurgling.NConfig;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitItems;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Manages duck coops and duckling incubators: replaces low-quality drakes and hens,
 * moves ducklings to incubators, and processes low-quality ducks and eggs.
 */
public class DuckMaster implements Action {
    private static final String COOP_CAP = "Chicken Coop";
    private static final NAlias COOP_GOB = new NAlias("gfx/terobjs/chickencoop");

    static final NAlias DRAKE = new NAlias(List.of("Duck Drake"), List.of("Dead", "Plucked"));
    static final NAlias HEN = new NAlias(List.of("Duck Hen"), List.of("Dead", "Plucked"));
    private static final NAlias DUCKLING = new NAlias("Duckling");
    private static final NAlias DUCK_EGG = new NAlias("Duck Egg");

    private static final String DEAD_DRAKE = "Dead Duck Drake";
    private static final String DEAD_HEN = "Dead Duck Hen";
    private static final String PLUCKED_DRAKE = "Plucked Duck Drake";
    private static final String PLUCKED_HEN = "Plucked Duck Hen";
    private static final String CLEANED_DUCK = "Cleaned Duck";

    private static final int MAX_DUCKLINGS_PER_INCUBATOR = 24;

    private static class CoopInfo {
        String gobHash;
        double drakeQuality;
        ArrayList<Float> henQualities = new ArrayList<>();

        CoopInfo(String gobHash, double drakeQuality) {
            this.gobHash = gobHash;
            this.drakeQuality = drakeQuality;
        }
    }

    private static class IncubatorInfo {
        String gobHash;
        double duckQuality;

        IncubatorInfo(String gobHash, double duckQuality) {
            this.gobHash = gobHash;
            this.duckQuality = duckQuality;
        }
    }

    private final Comparator<IncubatorInfo> incubatorComparator =
            Comparator.comparingDouble(info -> info.duckQuality);

    private final Comparator<CoopInfo> coopComparator = (first, second) -> {
        int result = Double.compare(first.drakeQuality, second.drakeQuality);
        if (result == 0 && !first.henQualities.isEmpty() && !second.henQualities.isEmpty()) {
            double firstAverage = first.henQualities.stream()
                    .mapToDouble(Float::doubleValue)
                    .average()
                    .orElse(0);
            double secondAverage = second.henQualities.stream()
                    .mapToDouble(Float::doubleValue)
                    .average()
                    .orElse(0);
            result = Double.compare(firstAverage, secondAverage);
        }
        return result;
    };

    private NContext context;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        context = new NContext(gui);

        NArea.Specialisation duckSpec = new NArea.Specialisation(Specialisation.SpecName.duck.toString());
        NArea.Specialisation incubatorSpec = new NArea.Specialisation(Specialisation.SpecName.duckIncubator.toString());
        NArea.Specialisation swillSpec = new NArea.Specialisation(Specialisation.SpecName.swill.toString());
        NArea.Specialisation waterSpec = new NArea.Specialisation(Specialisation.SpecName.water.toString());

        ArrayList<NArea.Specialisation> required = new ArrayList<>();
        required.add(duckSpec);
        required.add(incubatorSpec);

        ArrayList<NArea.Specialisation> optional = new ArrayList<>();
        optional.add(swillSpec);
        optional.add(waterSpec);

        if (!new Validator(required, optional).run(gui).IsSuccess()) {
            return Results.FAIL();
        }

        NArea duckArea = context.findArea(Specialisation.SpecName.duck);
        NArea incubatorArea = context.findArea(Specialisation.SpecName.duckIncubator);
        NArea swillArea = context.findArea(Specialisation.SpecName.swill);
        NArea waterArea = context.findArea(Specialisation.SpecName.water);

        if (duckArea == null) {
            return Results.ERROR("Duck area not found!");
        }
        if (incubatorArea == null) {
            return Results.ERROR("Duckling incubator area not found!");
        }

        NUtils.navigateToArea(duckArea);
        ArrayList<String> coopHashes = findCoopHashes(duckArea);

        NUtils.navigateToArea(incubatorArea);
        ArrayList<String> incubatorHashes = findCoopHashes(incubatorArea);

        fillCoopFluids(gui, coopHashes, incubatorHashes, duckArea, incubatorArea, swillArea, waterArea);

        ArrayList<CoopInfo> coopInfos = readDuckCoops(gui, coopHashes, duckArea);
        if (coopInfos == null) {
            return Results.FAIL();
        }
        coopInfos.sort(coopComparator.reversed());

        ArrayList<IncubatorInfo> drakes = new ArrayList<>();
        ArrayList<IncubatorInfo> hens = new ArrayList<>();
        if (!readIncubators(gui, incubatorHashes, incubatorArea, drakes, hens)) {
            return Results.FAIL();
        }

        Results drakeResult = processDrakes(gui, coopInfos, drakes);
        if (!drakeResult.IsSuccess()) {
            return drakeResult;
        }

        Results henResult = processHens(gui, coopInfos, hens);
        if (!henResult.IsSuccess()) {
            return henResult;
        }

        transferDucklings(gui, coopHashes, incubatorHashes);

        if (coopInfos.isEmpty()) {
            return Results.ERROR("No duck coops found!");
        }

        context.goToArea(Specialisation.SpecName.duck);
        Gob bestCoopGob = Finder.findGob(coopInfos.get(0).gobHash);
        if (bestCoopGob == null) {
            return Results.ERROR("Best coop not found!");
        }

        new PathFinder(bestCoopGob).run(gui);
        if (!new OpenTargetContainer(COOP_CAP, bestCoopGob).run(gui).IsSuccess()) {
            return Results.FAIL();
        }

        ArrayList<Float> topHenQualities = new ArrayList<>();
        for (WItem hen : gui.getInventory(COOP_CAP).getItems(HEN)) {
            topHenQualities.add(((NGItem) hen.item).quality);
        }
        new CloseTargetContainer(COOP_CAP).run(gui);

        if (topHenQualities.isEmpty()) {
            return Results.ERROR("No duck hens in best coop");
        }

        topHenQualities.sort(Float::compareTo);
        double threshold = topHenQualities.get(0);

        collectAndDisposeLowQualityEggs(gui, coopHashes, threshold);
        new FreeInventory2(context).run(gui);
        return Results.SUCCESS();
    }

    private ArrayList<String> findCoopHashes(NArea area) throws InterruptedException {
        ArrayList<String> hashes = new ArrayList<>();
        for (Gob coop : Finder.findGobs(area, COOP_GOB)) {
            if (coop.ngob != null && coop.ngob.hash != null) {
                hashes.add(coop.ngob.hash);
            }
        }
        return hashes;
    }

    private void fillCoopFluids(NGameUI gui, ArrayList<String> coopHashes,
                                ArrayList<String> incubatorHashes, NArea duckArea,
                                NArea incubatorArea, NArea swillArea, NArea waterArea)
            throws InterruptedException {
        if (swillArea == null && waterArea == null) {
            return;
        }

        ArrayList<Container> coops = getContainersFromHashes(coopHashes, duckArea);
        ArrayList<Container> incubators = getContainersFromHashes(incubatorHashes, incubatorArea);

        if (swillArea != null) {
            new FillFluid(coops, swillArea.getRCArea(), new NAlias("swill"), 2).run(gui);
            new FillFluid(incubators, swillArea.getRCArea(), new NAlias("swill"), 2).run(gui);
        }
        if (waterArea != null) {
            new FillFluid(coops, waterArea.getRCArea(), new NAlias("water"), 1).run(gui);
            new FillFluid(incubators, waterArea.getRCArea(), new NAlias("water"), 1).run(gui);
        }
    }

    private ArrayList<CoopInfo> readDuckCoops(NGameUI gui, ArrayList<String> coopHashes, NArea duckArea)
            throws InterruptedException {
        ArrayList<CoopInfo> result = new ArrayList<>();
        NUtils.navigateToArea(duckArea);
        for (String hash : coopHashes) {
            Gob gob = Finder.findGob(hash);
            if (gob == null) {
                continue;
            }

            new PathFinder(gob).run(gui);
            if (!new OpenTargetContainer(COOP_CAP, gob).run(gui).IsSuccess()) {
                return null;
            }

            WItem drake = gui.getInventory(COOP_CAP).getItem(DRAKE);
            CoopInfo info = new CoopInfo(hash, drake == null ? -1 : ((NGItem) drake.item).quality);
            for (WItem hen : gui.getInventory(COOP_CAP).getItems(HEN)) {
                info.henQualities.add(((NGItem) hen.item).quality);
            }
            info.henQualities.sort(Float::compareTo);
            result.add(info);
            new CloseTargetContainer(COOP_CAP).run(gui);
        }
        return result;
    }

    private boolean readIncubators(NGameUI gui, ArrayList<String> incubatorHashes, NArea incubatorArea,
                                    ArrayList<IncubatorInfo> drakes, ArrayList<IncubatorInfo> hens)
            throws InterruptedException {
        NUtils.navigateToArea(incubatorArea);
        for (String hash : incubatorHashes) {
            Gob gob = Finder.findGob(hash);
            if (gob == null) {
                continue;
            }

            new PathFinder(gob).run(gui);
            if (!new OpenTargetContainer(COOP_CAP, gob).run(gui).IsSuccess()) {
                return false;
            }

            for (WItem drake : gui.getInventory(COOP_CAP).getItems(DRAKE)) {
                drakes.add(new IncubatorInfo(hash, ((NGItem) drake.item).quality));
            }
            for (WItem hen : gui.getInventory(COOP_CAP).getItems(HEN)) {
                hens.add(new IncubatorInfo(hash, ((NGItem) hen.item).quality));
            }
            new CloseTargetContainer(COOP_CAP).run(gui);
        }
        return true;
    }

    private ArrayList<Container> getContainersFromHashes(ArrayList<String> hashes, NArea area) {
        ArrayList<Container> containers = new ArrayList<>();
        for (String hash : hashes) {
            Gob gob = Finder.findGob(hash);
            if (gob != null) {
                Container container = new Container(gob, COOP_CAP, area);
                container.initattr(Container.Space.class);
                containers.add(container);
            }
        }
        return containers;
    }

    private void transferDucklings(NGameUI gui, ArrayList<String> coopHashes,
                                   ArrayList<String> incubatorHashes) throws InterruptedException {
        context.goToArea(Specialisation.SpecName.duck);
        for (String hash : coopHashes) {
            Gob gob = Finder.findGob(hash);
            if (gob == null) {
                continue;
            }

            new PathFinder(gob).run(gui);
            if (!new OpenTargetContainer(COOP_CAP, gob).run(gui).IsSuccess()) {
                continue;
            }

            for (WItem duckling : gui.getInventory(COOP_CAP).getItems(DUCKLING)) {
                duckling.item.wdgmsg("transfer", Coord.z);
            }
            new CloseTargetContainer(COOP_CAP).run(gui);

            if (shouldDropOffItems(gui)) {
                transferDucklingsToIncubators(gui, incubatorHashes);
                context.goToArea(Specialisation.SpecName.duck);
            }
        }

        transferDucklingsToIncubators(gui, incubatorHashes);
        killExcessDucklings(gui);
    }

    private void transferDucklingsToIncubators(NGameUI gui, ArrayList<String> incubatorHashes)
            throws InterruptedException {
        ArrayList<WItem> ducklings = gui.getInventory().getItems(DUCKLING);
        if (ducklings.isEmpty()) {
            return;
        }

        context.goToArea(Specialisation.SpecName.duckIncubator);
        for (String hash : incubatorHashes) {
            ducklings = gui.getInventory().getItems(DUCKLING);
            if (ducklings.isEmpty()) {
                break;
            }

            Gob gob = Finder.findGob(hash);
            if (gob == null) {
                continue;
            }

            Container incubator = new Container(gob, COOP_CAP, null);
            Container.ItemCount itemCount = incubator.initItemCount(DUCKLING, MAX_DUCKLINGS_PER_INCUBATOR);

            new PathFinder(gob).run(gui);
            if (!new OpenTargetContainer(incubator).run(gui).IsSuccess()) {
                continue;
            }

            itemCount.update();
            int canAdd = itemCount.getNeeded();
            if (canAdd <= 0) {
                new CloseTargetContainer(incubator).run(gui);
                continue;
            }

            int transferred = 0;
            for (WItem duckling : ducklings) {
                if (transferred >= canAdd
                        || gui.getInventory(COOP_CAP).getNumberFreeCoord(new Coord(2, 2)) <= 0) {
                    break;
                }
                duckling.item.wdgmsg("transfer", Coord.z);
                transferred++;
            }
            new CloseTargetContainer(incubator).run(gui);
        }
    }

    private void killExcessDucklings(NGameUI gui) throws InterruptedException {
        ArrayList<WItem> ducklings = gui.getInventory().getItems(DUCKLING);
        while (!ducklings.isEmpty()) {
            new SelectFlowerAction("Wring neck", ducklings.get(0)).run(gui);
            NUtils.addTask(new WaitItems((NInventory) gui.maininv, new NAlias("A Bloody Mess"), 1));

            WItem bloodyMess = gui.getInventory().getItem(new NAlias("A Bloody Mess"));
            if (bloodyMess != null) {
                NUtils.drop(bloodyMess);
                NUtils.addTask(new NTask() {
                    @Override
                    public boolean check() {
                        try {
                            return gui.getInventory().getItems(new NAlias("A Bloody Mess")).isEmpty();
                        } catch (InterruptedException e) {
                            return false;
                        }
                    }
                });
            }
            ducklings = gui.getInventory().getItems(DUCKLING);
        }
    }

    private void collectAndDisposeLowQualityEggs(NGameUI gui, ArrayList<String> coopHashes,
                                                  double qualityThreshold) throws InterruptedException {
        context.goToArea(Specialisation.SpecName.duck);
        for (String hash : coopHashes) {
            Gob gob = Finder.findGob(hash);
            if (gob == null) {
                continue;
            }

            new PathFinder(gob).run(gui);
            if (!new OpenTargetContainer(COOP_CAP, gob).run(gui).IsSuccess()) {
                continue;
            }

            for (WItem egg : gui.getInventory(COOP_CAP).getItems(DUCK_EGG)) {
                if (shouldDiscardEgg(((NGItem) egg.item).quality, qualityThreshold)) {
                    egg.item.wdgmsg("transfer", Coord.z);
                }
            }
            new CloseTargetContainer(COOP_CAP).run(gui);

            if (shouldDropOffItems(gui)) {
                new FreeInventory2(context).run(gui);
                context.goToArea(Specialisation.SpecName.duck);
            }
        }
    }

    private Results processDrakes(NGameUI gui, ArrayList<CoopInfo> coopInfos,
                                  ArrayList<IncubatorInfo> drakes) throws InterruptedException {
        drakes.sort(incubatorComparator.reversed());
        for (IncubatorInfo drakeInfo : drakes) {
            context.goToArea(Specialisation.SpecName.duckIncubator);
            Gob sourceGob = Finder.findGob(drakeInfo.gobHash);
            if (sourceGob == null) {
                continue;
            }

            new PathFinder(sourceGob).run(gui);
            if (!new OpenTargetContainer(COOP_CAP, sourceGob).run(gui).IsSuccess()) {
                return Results.FAIL();
            }

            WItem drake = gui.getInventory(COOP_CAP).getItem(DRAKE);
            if (drake == null) {
                new CloseTargetContainer(COOP_CAP).run(gui);
                continue;
            }
            double drakeQuality = ((NGItem) drake.item).quality;
            transferFromOpenCoop(gui, drake);

            for (CoopInfo coopInfo : coopInfos) {
                if (!shouldReplaceDrake(coopInfo.drakeQuality, drakeQuality)) {
                    continue;
                }

                drake = gui.getInventory().getItem(DRAKE);
                if (drake == null) {
                    break;
                }

                context.goToArea(Specialisation.SpecName.duck);
                Gob targetGob = Finder.findGob(coopInfo.gobHash);
                if (targetGob == null) {
                    continue;
                }

                new PathFinder(targetGob).run(gui);
                if (!new OpenTargetContainer(COOP_CAP, targetGob).run(gui).IsSuccess()) {
                    return Results.FAIL();
                }

                WItem oldDrake = gui.getInventory(COOP_CAP).getItem(DRAKE);
                if (oldDrake == null) {
                    new CloseTargetContainer(COOP_CAP).run(gui);
                    continue;
                }

                Coord position = oldDrake.c.div(Inventory.sqsz);
                oldDrake.item.wdgmsg("transfer", Coord.z);
                waitForFreeSlot(gui, position);
                NUtils.takeItemToHand(drake);
                gui.getInventory(COOP_CAP).dropOn(position, "Duck Drake");

                coopInfo.drakeQuality = drakeQuality;
                drakeQuality = ((NGItem) oldDrake.item).quality;
                new CloseTargetContainer(COOP_CAP).run(gui);
            }

            drake = gui.getInventory().getItem(DRAKE);
            if (drake != null) {
                butcherDuck(gui, drake, DEAD_DRAKE, PLUCKED_DRAKE);
            }
        }
        new FreeInventory2(context).run(gui);
        return Results.SUCCESS();
    }

    private Results processHens(NGameUI gui, ArrayList<CoopInfo> coopInfos,
                                ArrayList<IncubatorInfo> hens) throws InterruptedException {
        hens.sort(incubatorComparator.reversed());
        for (IncubatorInfo henInfo : hens) {
            context.goToArea(Specialisation.SpecName.duckIncubator);
            Gob sourceGob = Finder.findGob(henInfo.gobHash);
            if (sourceGob == null) {
                continue;
            }

            new PathFinder(sourceGob).run(gui);
            if (!new OpenTargetContainer(COOP_CAP, sourceGob).run(gui).IsSuccess()) {
                return Results.FAIL();
            }

            WItem hen = gui.getInventory(COOP_CAP).getItem(HEN);
            if (hen == null) {
                new CloseTargetContainer(COOP_CAP).run(gui);
                continue;
            }
            float henQuality = ((NGItem) hen.item).quality;
            transferFromOpenCoop(gui, hen);

            for (CoopInfo coopInfo : coopInfos) {
                for (int index = 0; index < coopInfo.henQualities.size(); index++) {
                    if (coopInfo.henQualities.get(index) >= henQuality) {
                        continue;
                    }

                    hen = gui.getInventory().getItem(HEN);
                    if (hen == null) {
                        break;
                    }

                    context.goToArea(Specialisation.SpecName.duck);
                    Gob targetGob = Finder.findGob(coopInfo.gobHash);
                    if (targetGob == null) {
                        continue;
                    }

                    new PathFinder(targetGob).run(gui);
                    if (!new OpenTargetContainer(COOP_CAP, targetGob).run(gui).IsSuccess()) {
                        return Results.FAIL();
                    }

                    WItem oldHen = gui.getInventory(COOP_CAP).getItem(HEN, coopInfo.henQualities.get(index));
                    if (oldHen == null) {
                        new CloseTargetContainer(COOP_CAP).run(gui);
                        continue;
                    }

                    Coord position = oldHen.c.div(Inventory.sqsz);
                    oldHen.item.wdgmsg("transfer", Coord.z);
                    waitForFreeSlot(gui, position);
                    NUtils.takeItemToHand(hen);
                    gui.getInventory(COOP_CAP).dropOn(position, "Duck Hen");

                    coopInfo.henQualities.set(index, henQuality);
                    henQuality = ((NGItem) oldHen.item).quality;
                    new CloseTargetContainer(COOP_CAP).run(gui);
                    break;
                }
            }

            hen = gui.getInventory().getItem(HEN);
            if (hen != null) {
                butcherDuck(gui, hen, DEAD_HEN, PLUCKED_HEN);
            }
        }
        new FreeInventory2(context).run(gui);
        return Results.SUCCESS();
    }

    private void transferFromOpenCoop(NGameUI gui, WItem bird) throws InterruptedException {
        Coord position = bird.c.div(Inventory.sqsz);
        bird.item.wdgmsg("transfer", Coord.z);
        waitForFreeSlot(gui, position);
        new CloseTargetContainer(COOP_CAP).run(gui);
    }

    private void waitForFreeSlot(NGameUI gui, Coord position) throws InterruptedException {
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                return gui.getInventory(COOP_CAP).isSlotFree(position);
            }
        });
    }

    private void butcherDuck(NGameUI gui, WItem duck, String deadType, String pluckedType)
            throws InterruptedException {
        if (gui.getInventory().getNumberFreeCoord(new Coord(1, 1)) < 2) {
            new FreeInventory2(context).run(gui);
        }

        new SelectFlowerAction("Wring neck", duck).run(gui);
        NUtils.addTask(new WaitItems((NInventory) gui.maininv, new NAlias(deadType), 1));

        WItem deadDuck = gui.getInventory().getItem(new NAlias(deadType));
        if (deadDuck == null) {
            return;
        }

        boolean isDrake = DEAD_DRAKE.equals(deadType);
        Boolean skipPluckDrakes = (Boolean) NConfig.get(NConfig.Key.skipPluckingDrakesInDuck);
        if (!(Boolean.TRUE.equals(skipPluckDrakes) && isDrake)) {
            new SelectFlowerAction("Pluck", deadDuck).run(gui);
            NUtils.addTask(new WaitItems((NInventory) gui.maininv, new NAlias(pluckedType), 1));

            WItem pluckedDuck = gui.getInventory().getItem(new NAlias(pluckedType));
            if (pluckedDuck == null) {
                return;
            }

            new SelectFlowerAction("Clean", pluckedDuck).run(gui);
            NUtils.addTask(new WaitItems((NInventory) gui.maininv, new NAlias(CLEANED_DUCK), 1));

            WItem cleanedDuck = gui.getInventory().getItem(new NAlias(CLEANED_DUCK));
            if (cleanedDuck == null) {
                return;
            }

            Boolean skipButcher = (Boolean) NConfig.get(NConfig.Key.skipButcherInDuck);
            if (!Boolean.TRUE.equals(skipButcher)) {
                new SelectFlowerAction("Butcher", cleanedDuck).run(gui);
                NUtils.addTask(new NTask() {
                    @Override
                    public boolean check() {
                        try {
                            return gui.getInventory().getItems(new NAlias(CLEANED_DUCK)).isEmpty();
                        } catch (InterruptedException e) {
                            return false;
                        }
                    }
                });
            }
        }

        if (shouldDropOffItems(gui)) {
            new FreeInventory2(context).run(gui);
        }
    }

    private boolean shouldDropOffItems(NGameUI gui) throws InterruptedException {
        return needsDropOff(gui.getInventory().getNumberFreeCoord(new Coord(2, 2)));
    }

    static boolean shouldReplaceDrake(double residentQuality, double candidateQuality) {
        return residentQuality != -1 && residentQuality < candidateQuality;
    }

    static boolean shouldDiscardEgg(double eggQuality, double thresholdQuality) {
        return eggQuality < thresholdQuality;
    }

    static boolean needsDropOff(int availableDuckSlots) {
        return availableDuckSlots <= 2;
    }
}
