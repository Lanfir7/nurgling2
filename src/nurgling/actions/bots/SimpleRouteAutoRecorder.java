package nurgling.actions.bots;

import haven.Coord2d;
import haven.Following;
import haven.Gob;
import nurgling.NUtils;
import nurgling.routes.SimpleRoute;
import nurgling.tasks.WaitNextPointForSimpleRouteAutoRecorder;
import nurgling.tasks.WaitPlayerNotNull;

import static nurgling.NUtils.player;

/**
 * Упрощенная версия RouteAutoRecorder для записи простых маршрутов без привязки к зонам.
 */
public class SimpleRouteAutoRecorder implements Runnable {
    private final SimpleRoute route;
    private boolean running = true;

    public SimpleRouteAutoRecorder(SimpleRoute route) {
        this.route = route;
    }

    public void stop() {
        running = false;
    }

    /**
     * Основной цикл записи для автоматического создания простого маршрута.
     * Записывает только позиции без привязки к зонам.
     */
    @Override
    public void run() {
        Coord2d playerRC = getRouteAnchorPosition(player());

        // Добавляем waypoint в начале записи
        route.addWaypoint();

        while (running) {
            // Ждем пока игрок переместится на следующую точку (или до прерывания)
            try {
                NUtils.getUI().core.addTask(new WaitNextPointForSimpleRouteAutoRecorder(playerRC, this.route));
            } catch (InterruptedException e) {
                NUtils.getGameUI().msg("Stopped simple route recording for: " + route.name);
                running = false;
            }

            if (!running) break;

            // Обновляем позицию игрока
            Gob playerGob = player();
            if(playerGob != null) {
                playerRC = getRouteAnchorPosition(playerGob);
            } else {
                try {
                    NUtils.getUI().core.addTask(new WaitPlayerNotNull());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                playerRC = getRouteAnchorPosition(player());
            }

            // Добавляем обычный waypoint
            if(NUtils.player() != null && NUtils.player().rc != null) {
                route.addWaypoint();
            }

            if (NUtils.getGameUI() != null && NUtils.getGameUI().simpleRoutesWidget != null) {
                NUtils.getGameUI().simpleRoutesWidget.updateWaypoints();
            }
        }
    }

    private Coord2d getRouteAnchorPosition(Gob player) {
        if (player == null) {
            return null;
        }
        Following following = player.getattr(Following.class);
        if (following != null) {
            Gob vehicle = following.tgt();
            if (vehicle != null) {
                if (vehicle.ngob != null && vehicle.ngob.name != null && vehicle.ngob.name.contains("/vehicle/wagon")) {
                    Gob horse = findHorseForWagon(vehicle);
                    if (horse != null) {
                        return horse.rc;
                    }
                }
                return vehicle.rc;
            }
        }
        return player.rc;
    }

    private Gob findHorseForWagon(Gob wagon) {
        if (wagon == null || NUtils.getGameUI() == null || NUtils.getGameUI().ui == null ||
                NUtils.getGameUI().ui.sess == null || NUtils.getGameUI().ui.sess.glob == null) {
            return null;
        }
        synchronized (NUtils.getGameUI().ui.sess.glob.oc) {
            for (Gob gob : NUtils.getGameUI().ui.sess.glob.oc) {
                if (gob == null || gob.ngob == null || gob.ngob.name == null) continue;
                Following fl = gob.getattr(Following.class);
                if (fl != null && fl.tgt == wagon.id && gob.ngob.name.contains("/kritter/horse/")) {
                    return gob;
                }
            }
        }
        return null;
    }
}

