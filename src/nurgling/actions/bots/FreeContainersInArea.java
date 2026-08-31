package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.MCache;
import haven.Pair;
import haven.Resource;
import nurgling.NGameUI;
import nurgling.NISBox;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.FreeContainers;
import nurgling.actions.FreeInventory2;
import nurgling.actions.OpenTargetContainer;
import nurgling.actions.PathFinder;
import nurgling.actions.Results;
import nurgling.actions.TakeItemsFromPile;
import nurgling.areas.NContext;
import nurgling.pf.NHitBoxD;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.StockpileUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FreeContainersInArea implements Action {
    private static final double APPROACH_MARGIN = 0.5;
    private static final double NEARBY_PILE_RADIUS = MCache.tilesz.x * 3;
    private static final double CLEARANCE_TIE_LIMIT = MCache.tilesz.x;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        NContext context = new NContext(gui);
        String workAreaId = context.createArea("Please, select area with piles or containers",
                Resource.loadsimg("baubles/inputArea"));
        Pair<Coord2d, Coord2d> area = context.getRCArea(workAreaId);
        ArrayList<Container> containers = new ArrayList<>();
        if (area != null) {
            for (Gob sm : Finder.findGobs(area, new NAlias(new ArrayList<>(NContext.contcaps.keySet())))) {
                Container cand = new Container(sm, NContext.contcaps.get(sm.ngob.name),
                        context.goToAreaById(workAreaId));
                cand.initattr(Container.Space.class);
                containers.add(cand);
            }
            if (!containers.isEmpty())
                new FreeContainers(containers).run(gui);
        }

        ArrayList<Gob> piles;
        while (!(piles = Finder.findGobs(area, new NAlias("stockpile"))).isEmpty()) {
            Gob player = NUtils.player();
            if (player == null)
                break;
            orderPilesNearestFirst(piles, player.rc);

            boolean openedPile = false;
            for (Gob pile : piles) {
                if (!openPile(gui, pile))
                    continue;
                openedPile = true;
                drainPile(gui, context, workAreaId, pile);
                break;
            }
            if (!openedPile)
                break;
        }
        new FreeInventory2(context).run(gui);
        return Results.SUCCESS();
    }

    private static void drainPile(NGameUI gui, NContext context, String workAreaId, Gob pile)
            throws InterruptedException {
        Coord size = StockpileUtils.itemMaxSize.get(pile.ngob.name);
        Coord itemSize = size != null ? size : new Coord(1, 1);

        while (Finder.findGob(pile.id) != null) {
            int freeSlots = gui.getInventory().getNumberFreeCoord(itemSize);
            if (freeSlots == 0) {
                new FreeInventory2(context).run(gui);
                context.navigateToAreaIfNeeded(workAreaId);
                if (Finder.findGob(pile.id) == null || !openPile(gui, pile))
                    break;
                continue;
            }

            NISBox stockpile = gui.getStockpile();
            if (stockpile == null) {
                if (!openPile(gui, pile))
                    break;
                continue;
            }

            new TakeItemsFromPile(pile, stockpile, freeSlots).run(gui);
            context.navigateToAreaIfNeeded(workAreaId);
        }
    }

    static void orderPilesNearestFirst(ArrayList<Gob> piles, Coord2d player) {
        piles.sort(Comparator
                .comparingDouble((Gob pile) -> pile.rc.dist(player))
                .thenComparingLong(pile -> pile.id));
    }

    @FunctionalInterface
    interface Step {
        boolean run() throws InterruptedException;
    }

    static boolean approachThenOpen(Step approach, Step open) throws InterruptedException {
        return approach.run() && open.run();
    }

    private static boolean openPile(NGameUI gui, Gob pile) throws InterruptedException {
        return approachThenOpen(
                () -> approachPileFromClearSide(gui, pile),
                () -> new OpenTargetContainer("Stockpile", pile, true).run(gui).IsSuccess());
    }

    private static boolean approachPileFromClearSide(NGameUI gui, Gob pile)
            throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null || player.ngob.hitBox == null || pile == null || pile.ngob.hitBox == null)
            return false;

        ArrayList<Gob> nearbyPiles = Finder.findGobs(
                pile.rc, new NAlias("stockpile"), null, NEARBY_PILE_RADIUS);
        ArrayList<NHitBoxD> occupied = new ArrayList<>();
        for (Gob nearby : nearbyPiles) {
            if (nearby.id != pile.id && nearby.ngob.hitBox != null)
                occupied.add(new NHitBoxD(nearby));
        }

        NHitBoxD targetHitBox = new NHitBoxD(pile);
        ArrayList<Coord2d> candidates = safeApproachCandidates(
                player.rc, new NHitBoxD(player), targetHitBox, occupied, APPROACH_MARGIN);
        Coord2d targetCenter = targetHitBox.getCircumscribedUL()
                .add(targetHitBox.getCircumscribedBR()).div(2);
        ArrayList<PathFinder.Mode> modes = approachModes(targetCenter, candidates);
        for (int i = 0; i < candidates.size(); i++) {
            Coord2d candidate = candidates.get(i);
            PathFinder approach = new PathFinder(pile);
            approach.setMode(modes.get(i));
            if (!approach.run(gui).IsSuccess())
                continue;
            Gob currentPlayer = NUtils.player();
            if (currentPlayer != null && currentPlayer.ngob.hitBox != null && playerIsClear(
                    new NHitBoxD(currentPlayer), targetHitBox, occupied))
                return true;
            if (new PathFinder(candidate).run(gui).IsSuccess()) {
                currentPlayer = NUtils.player();
                if (currentPlayer != null && currentPlayer.ngob.hitBox != null && playerIsClear(
                        new NHitBoxD(currentPlayer), targetHitBox, occupied))
                    return true;
            }
        }
        return false;
    }

    static ArrayList<PathFinder.Mode> approachModes(Coord2d targetCenter,
                                                     List<Coord2d> candidates) {
        ArrayList<PathFinder.Mode> result = new ArrayList<>();
        for (Coord2d candidate : candidates) {
            double dx = candidate.x - targetCenter.x;
            double dy = candidate.y - targetCenter.y;
            if (Math.abs(dx) > Math.abs(dy))
                result.add(dx < 0 ? PathFinder.Mode.X_MIN : PathFinder.Mode.X_MAX);
            else
                result.add(dy < 0 ? PathFinder.Mode.Y_MIN : PathFinder.Mode.Y_MAX);
        }
        return result;
    }

    static ArrayList<Coord2d> safeApproachCandidates(Coord2d playerPosition,
                                                     NHitBoxD playerHitBox,
                                                     NHitBoxD targetHitBox,
                                                     List<NHitBoxD> occupied,
                                                     double margin) {
        Coord2d playerUL = playerHitBox.getCircumscribedUL();
        Coord2d playerBR = playerHitBox.getCircumscribedBR();
        Coord2d targetUL = targetHitBox.getCircumscribedUL();
        Coord2d targetBR = targetHitBox.getCircumscribedBR();
        Coord2d targetCenter = targetUL.add(targetBR).div(2);
        double safeMargin = Math.max(0, margin);

        ArrayList<Coord2d> points = new ArrayList<>();
        points.add(Coord2d.of(
                targetUL.x - safeMargin - (playerBR.x - playerPosition.x), targetCenter.y));
        points.add(Coord2d.of(
                targetBR.x + safeMargin + (playerPosition.x - playerUL.x), targetCenter.y));
        points.add(Coord2d.of(
                targetCenter.x, targetUL.y - safeMargin - (playerBR.y - playerPosition.y)));
        points.add(Coord2d.of(
                targetCenter.x, targetBR.y + safeMargin + (playerPosition.y - playerUL.y)));

        Coord2d localUL = playerUL.sub(playerPosition);
        Coord2d localBR = playerBR.sub(playerPosition);
        ArrayList<ScoredApproach> safe = new ArrayList<>();
        for (Coord2d point : points) {
            NHitBoxD candidateHitBox = new NHitBoxD(localUL, localBR, point);
            if (!playerIsClear(candidateHitBox, targetHitBox, occupied))
                continue;
            double clearance = Math.min(nearestClearance(candidateHitBox, occupied),
                    CLEARANCE_TIE_LIMIT);
            safe.add(new ScoredApproach(point, clearance, point.dist(playerPosition)));
        }
        safe.sort(Comparator
                .comparingDouble((ScoredApproach candidate) -> candidate.clearance).reversed()
                .thenComparingDouble(candidate -> candidate.playerDistance));

        ArrayList<Coord2d> result = new ArrayList<>();
        for (ScoredApproach candidate : safe)
            result.add(candidate.position);
        return result;
    }

    private static boolean playerIsClear(NHitBoxD playerHitBox, NHitBoxD targetHitBox,
                                         List<NHitBoxD> occupied) {
        if (playerHitBox.intersects(targetHitBox, true))
            return false;
        for (NHitBoxD obstacle : occupied) {
            if (playerHitBox.intersects(obstacle, true))
                return false;
        }
        return true;
    }

    private static double nearestClearance(NHitBoxD hitBox, List<NHitBoxD> occupied) {
        double result = Double.POSITIVE_INFINITY;
        for (NHitBoxD obstacle : occupied)
            result = Math.min(result, hitBoxDistance(hitBox, obstacle));
        return result;
    }

    private static double hitBoxDistance(NHitBoxD first, NHitBoxD second) {
        Coord2d firstUL = first.getCircumscribedUL();
        Coord2d firstBR = first.getCircumscribedBR();
        Coord2d secondUL = second.getCircumscribedUL();
        Coord2d secondBR = second.getCircumscribedBR();
        double dx = Math.max(0, Math.max(firstUL.x - secondBR.x, secondUL.x - firstBR.x));
        double dy = Math.max(0, Math.max(firstUL.y - secondBR.y, secondUL.y - firstBR.y));
        return Math.hypot(dx, dy);
    }

    private static final class ScoredApproach {
        final Coord2d position;
        final double clearance;
        final double playerDistance;

        ScoredApproach(Coord2d position, double clearance, double playerDistance) {
            this.position = position;
            this.clearance = clearance;
            this.playerDistance = playerDistance;
        }
    }
}
