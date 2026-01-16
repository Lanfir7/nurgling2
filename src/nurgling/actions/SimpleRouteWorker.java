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
import nurgling.tools.NParser;
import nurgling.NConfig;
import java.util.ArrayList;
import java.util.HashMap;

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
     * Проверяет, находится ли игрок на корабле/лодке
     */
    private boolean isOnShip() {
        Gob player = NUtils.player();
        if (player == null) return false;
        
        Following following = player.getattr(Following.class);
        if (following == null) return false;
        
        Gob vehicle = following.tgt();
        if (vehicle == null) return false;
        
        String vehicleName = vehicle.ngob.name;
        
        // Проверяем, является ли транспорт водным
        return NParser.checkName(vehicleName, "/vehicle/snekkja") ||
               NParser.checkName(vehicleName, "/vehicle/knarr") ||
               NParser.checkName(vehicleName, "/vehicle/rowboat") ||
               NParser.checkName(vehicleName, "/vehicle/spark") ||
               NParser.checkName(vehicleName, "/vehicle/dugout");
    }
    
    /**
     * Получает корабль, на котором находится игрок
     */
    private Gob getShip() {
        Gob player = NUtils.player();
        if (player == null) return null;
        
        Following following = player.getattr(Following.class);
        if (following == null) return null;
        
        return following.tgt();
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
        Gob ship = getShip();
        if (ship != null) {
            // Используем координаты корабля для поиска grid
            Coord tilec = ship.rc.div(MCache.tilesz).floor();
            MCache.Grid vehicleGrid = map.getgridt(tilec);
            if (vehicleGrid != null && vehicleGrid.id == point.gridId) {
                // Если grid совпадает, используем координаты из waypoint
                Coord tilec2 = vehicleGrid.ul.add(point.localCoord);
                return tilec2.mul(MCache.tilesz).add(MCache.tilehsz);
            }
        }
        
        return null;
    }

    /**
     * Навигация к точке - для корабля использует прямой клик, для пешего - PathFinder
     */
    private Results navigateToPoint(NGameUI gui, Coord2d target, ObjectTracker objectTracker) throws InterruptedException {
        Gob ship = getShip();
        
        if (isOnShip() && ship != null) {
            // Для корабля кликаем на карту для движения
            gui.map.wdgmsg("click", Coord.z, target.floor(posres), 1, 0);
            
            // Ждем движение корабля по его координатам (универсальный способ для всех кораблей)
            waitForShipMovement(ship, target, objectTracker);
            
            // Проверяем, достигли ли мы точки (используем координаты корабля)
            Coord2d currentPos = getCurrentPosition();
            if (currentPos == null || currentPos.dist(target) > 11.0) {
                return Results.FAIL();
            }
            return Results.SUCCESS();
        } else {
            // Для пешего движения используем PathFinder
            // Проверяем объекты во время движения
            if (objectTracker != null) {
                objectTracker.checkObjects();
            }
            return new PathFinder(target).run(gui);
        }
    }
    
    /**
     * Ожидание движения большого корабля (snekkja, knarr) по его координатам
     */
    private void waitForShipMovement(Gob vehicle, Coord2d target, ObjectTracker objectTracker) throws InterruptedException {
        // Порог достижения цели
        final double ARRIVAL_THRESHOLD = 11.0;
        // Максимальное время ожидания начала движения (мс)
        final long START_TIMEOUT = 3000;
        // Максимальное время полёта до цели (мс)
        final long MOVEMENT_TIMEOUT = 60000;
        // Интервал проверки (мс)
        final long CHECK_INTERVAL = 100;
        
        Coord2d startPos = vehicle.rc;
        long startTime = System.currentTimeMillis();
        
        // Фаза 1: Ждём начала движения (позиция изменилась или уже близко к цели)
        while (System.currentTimeMillis() - startTime < START_TIMEOUT) {
            if (vehicle.rc.dist(target) <= ARRIVAL_THRESHOLD) {
                return; // Уже на месте
            }
            if (vehicle.rc.dist(startPos) > 1.0) {
                break; // Корабль начал движение
            }
            Thread.sleep(CHECK_INTERVAL);
        }
        
        // Фаза 2: Ждём достижения цели
        startTime = System.currentTimeMillis();
        Coord2d lastPos = vehicle.rc;
        int stoppedCount = 0;
        
        while (System.currentTimeMillis() - startTime < MOVEMENT_TIMEOUT) {
            Coord2d currentPos = vehicle.rc;
            
            // Проверяем достижение цели
            if (currentPos.dist(target) <= ARRIVAL_THRESHOLD) {
                return;
            }
            
            // Проверяем остановку корабля (координаты не меняются)
            if (currentPos.dist(lastPos) < 0.1) {
                stoppedCount++;
                if (stoppedCount > 10) { // Корабль остановился
                    return;
                }
            } else {
                stoppedCount = 0;
            }
            
            lastPos = currentPos;
            
            // Проверяем объекты во время движения корабля (каждую секунду)
            if (objectTracker != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed % 1000 < CHECK_INTERVAL * 2) {
                    objectTracker.checkObjects();
                }
            }
            
            Thread.sleep(CHECK_INTERVAL);
        }
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (route == null || route.waypoints.isEmpty())
            return Results.ERROR("No route or waypoints defined.");

        // Инициализируем ObjectTracker для отслеживания объектов
        boolean discordNotifyEnabled = getBool(NConfig.Key.simpleRoutesDiscordNotify);
        ArrayList<String> trackedObjects = getTrackedObjects();
        ObjectTracker objectTracker = new ObjectTracker(gui, trackedObjects, discordNotifyEnabled);

        int lastVisited = 0;

        for (int i = 0; i < route.waypoints.size(); i++) {
            SimpleRoutePoint rp = route.waypoints.get(i);
            Coord2d target = getWaypointCoord(rp, gui.map.glob.map);
            if (target == null) continue;

            navigateToPoint(gui, target, objectTracker);
            lastVisited = i;

            // Проверяем объекты во время движения
            objectTracker.checkObjects();

            action.run(gui);

            // Проверяем объекты после выполнения действия
            objectTracker.checkObjects();

            if (predicate != null && predicate.check()) {
                gui.msg("Predicate triggered. Backtracking to start.");

                goToStart(gui, lastVisited, objectTracker);

                if (returnAction != null) {
                    returnAction.run(gui);
                }

                if (lastVisited > 0) {
                    returnToLastVisited(gui, lastVisited, objectTracker);
                }
                i=i-1;
            }
        }

        if (backtrack) {
            goToStart(gui, lastVisited, objectTracker);
            // Проверяем объекты при возврате
            objectTracker.checkObjects();
        }

        if (finalAction != null) {
            finalAction.run(gui);
        }

        return Results.SUCCESS();
    }

    /**
     * Получает значение boolean из конфига
     */
    private boolean getBool(NConfig.Key key) {
        Object val = NConfig.get(key);
        return val instanceof Boolean ? (Boolean) val : false;
    }

    /**
     * Получает список отслеживаемых объектов из конфига
     */
    @SuppressWarnings("unchecked")
    private ArrayList<String> getTrackedObjects() {
        ArrayList<String> result = new ArrayList<>();
        if (NConfig.get(NConfig.Key.simpleRoutesTrackedObjects) != null) {
            for (HashMap<String, Object> item : (ArrayList<HashMap<String, Object>>) NConfig.get(NConfig.Key.simpleRoutesTrackedObjects)) {
                Boolean enabled = (Boolean) item.get("enabled");
                if (enabled != null && enabled) {
                    String name = (String) item.get("name");
                    if (name != null && !name.isEmpty()) {
                        result.add(name);
                    }
                }
            }
        }
        return result;
    }

    private void goToStart(NGameUI gui, int lastVisited, ObjectTracker objectTracker) throws InterruptedException {
        for (int j = lastVisited; j >= 0; j--) {
            SimpleRoutePoint backtrackPoint = route.waypoints.get(j);
            Coord2d backtrackTarget = getWaypointCoord(backtrackPoint, gui.map.glob.map);
            if (backtrackTarget != null)
                navigateToPoint(gui, backtrackTarget, objectTracker);
        }
    }

    private void returnToLastVisited(NGameUI gui, int lastVisited, ObjectTracker objectTracker) throws InterruptedException {
        for (int j = 1; j <= lastVisited; j++) {
            Coord2d resume = getWaypointCoord(route.waypoints.get(j), gui.map.glob.map);
            if (resume != null)
                navigateToPoint(gui, resume, objectTracker);
        }
    }
}

