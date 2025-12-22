package nurgling.actions.bots;

import haven.Coord2d;
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
        Coord2d playerRC = player().rc;

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
                playerRC = playerGob.rc;
            } else {
                try {
                    NUtils.getUI().core.addTask(new WaitPlayerNotNull());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                playerRC = player().rc;
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
}

