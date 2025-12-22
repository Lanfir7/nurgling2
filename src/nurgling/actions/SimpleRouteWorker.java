package nurgling.actions;

import haven.Coord;
import haven.Coord2d;
import haven.Following;
import haven.Gob;
import haven.MCache;
import static haven.OCache.posres;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.routes.SimpleRoute;
import nurgling.routes.SimpleRoutePoint;
import nurgling.tasks.*;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

/**
 * Worker для воспроизведения простых маршрутов без привязки к зонам.
 */
public class SimpleRouteWorker implements Action {

    private final SimpleRoute route;
    private final Action action;
    private final boolean backtrack;

    // When to come back to the start (Optional);
    private NTask predicate = null;
    // What to do when you come back to first waypoint (Optional);
    private Action returnAction = null;
    private Action finalAction = null;

    public SimpleRouteWorker(Action action, SimpleRoute route, boolean backtrack) {
        this.route = route;
        this.action = action;
        this.backtrack = backtrack;
    }

    public SimpleRouteWorker(Action action, SimpleRoute route, boolean backtrack, NTask predicate, Action returnAction, Action finalAction) {
        this.route = route;
        this.action = action;
        this.backtrack = backtrack;
        this.predicate = predicate;
        this.returnAction = returnAction;
        this.finalAction = finalAction;
    }

    /**
     * Проверяет, является ли игрок рулевым корабля (не пассажиром)
     */
    private boolean isShipDriver() {
        Gob player = NUtils.player();
        if (player == null) return false;
        
        Following following = player.getattr(Following.class);
        if (following == null) return false;
        
        Gob vehicle = following.tgt();
        if (vehicle == null) return false;
        
        String pos = following.xfname;
        String vehicleName = vehicle.ngob.name;
        
        // Проверяем различные типы кораблей
        if (NParser.checkName(vehicleName, "/vehicle/snekkja")) {
            return pos.equals("m0"); // m0 = рулевой
        } else if (NParser.checkName(vehicleName, "/vehicle/knarr")) {
            return pos.equals("m0"); // m0 = рулевой
        } else if (NParser.checkName(vehicleName, "/vehicle/rowboat")) {
            return pos.equals("d"); // d = рулевой
        } else if (NParser.checkName(vehicleName, "/vehicle/spark")) {
            return pos.equals("d"); // d = рулевой
        }
        
        return false;
    }

    /**
     * Получает текущую позицию (игрока или корабля, если игрок на корабле)
     */
    private Coord2d getCurrentPosition() {
        Gob player = NUtils.player();
        if (player == null) return null;
        
        // Если игрок на корабле, используем координаты корабля
        Following following = player.getattr(Following.class);
        if (following != null) {
            Gob vehicle = following.tgt();
            if (vehicle != null) {
                return vehicle.rc;
            }
        }
        
        return player.rc;
    }

    /**
     * Получает координаты waypoint, используя координаты корабля если нужно
     */
    private Coord2d getWaypointCoord(SimpleRoutePoint point, MCache map) {
        // Сначала пробуем стандартный способ
        Coord2d coord = point.toCoord2d(map);
        if (coord != null) {
            return coord;
        }
        
        // Если не получилось, пробуем использовать координаты корабля для определения gridId
        if (isShipDriver()) {
            Gob player = NUtils.player();
            if (player != null) {
                Following following = player.getattr(Following.class);
                if (following != null) {
                    Gob vehicle = following.tgt();
                    if (vehicle != null) {
                        // Используем координаты корабля для поиска grid
                        Coord tilec = vehicle.rc.div(MCache.tilesz).floor();
                        MCache.Grid vehicleGrid = map.getgridt(tilec);
                        if (vehicleGrid != null && vehicleGrid.id == point.gridId) {
                            // Если grid совпадает, используем координаты из waypoint
                            Coord tilec2 = vehicleGrid.ul.add(point.localCoord);
                            return tilec2.mul(MCache.tilesz).add(MCache.tilehsz);
                        }
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Навигация к точке - для корабля использует прямой клик, для пешего - PathFinder
     */
    private Results navigateToPoint(NGameUI gui, Coord2d target) throws InterruptedException {
        if (isShipDriver()) {
            // Для корабля используем прямой клик на карту
            Gob player = NUtils.player();
            Following following = player.getattr(Following.class);
            Gob vehicle = following.tgt();
            
            // Кликаем на карту для движения корабля
            gui.map.wdgmsg("click", Coord.z, target.floor(posres), 1, 0);
            
            // Ждем, пока корабль начнет движение и достигнет точки
            if (NParser.isIt(vehicle, new NAlias("rowboat"))) {
                NUtils.getUI().core.addTask(new IsPoseMov(target, player, new NAlias("gfx/borka/rowing")));
                NUtils.getUI().core.addTask(new IsNotPose(player, new NAlias("gfx/borka/rowing")));
            } else if (NParser.isIt(vehicle, new NAlias("dugout"))) {
                NUtils.getUI().core.addTask(new IsPoseMov(target, player, new NAlias("gfx/borka/dugoutrowan")));
                NUtils.getUI().core.addTask(new IsNotPose(player, new NAlias("gfx/borka/dugoutrowan")));
            } else {
                // Для других кораблей просто ждем достижения точки
                NUtils.getUI().core.addTask(new IsMoving(target));
                NUtils.getUI().core.addTask(new MovingCompleted(target));
            }
            
            // Проверяем, достигли ли мы точки (используем координаты корабля)
            Coord2d currentPos = getCurrentPosition();
            if (currentPos == null || currentPos.dist(target) > 11.0) {
                return Results.FAIL();
            }
            return Results.SUCCESS();
        } else {
            // Для пешего движения используем PathFinder
            return new PathFinder(target).run(gui);
        }
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (route == null || route.waypoints.isEmpty())
            return Results.ERROR("No route or waypoints defined.");

        int lastVisited = 0;

        for (int i = 0; i < route.waypoints.size(); i++) {
            SimpleRoutePoint rp = route.waypoints.get(i);
            Coord2d target = getWaypointCoord(rp, gui.map.glob.map);
            if (target == null) continue;

            navigateToPoint(gui, target);
            lastVisited = i;

            action.run(gui);

            if (predicate != null && predicate.check()) {
                gui.msg("Predicate triggered. Backtracking to start.");

                goToStart(gui, lastVisited);

                if (returnAction != null) {
                    returnAction.run(gui);
                }

                if (lastVisited > 0) {
                    returnToLastVisited(gui, lastVisited);
                }
                i=i-1;
            }
        }

        if (backtrack) {
            goToStart(gui, lastVisited);
        }

        if (finalAction != null) {
            finalAction.run(gui);
        }

        return Results.SUCCESS();
    }

    private void goToStart(NGameUI gui, int lastVisited) throws InterruptedException {
        for (int j = lastVisited; j >= 0; j--) {
            SimpleRoutePoint backtrackPoint = route.waypoints.get(j);
            Coord2d backtrackTarget = getWaypointCoord(backtrackPoint, gui.map.glob.map);
            if (backtrackTarget != null)
                navigateToPoint(gui, backtrackTarget);
        }
    }

    private void returnToLastVisited(NGameUI gui, int lastVisited) throws InterruptedException {
        for (int j = 1; j <= lastVisited; j++) {
            Coord2d resume = getWaypointCoord(route.waypoints.get(j), gui.map.glob.map);
            if (resume != null)
                navigateToPoint(gui, resume);
        }
    }
}

