package nurgling.tasks;

import haven.Coord2d;
import haven.Gob;
import nurgling.NUtils;
import nurgling.routes.SimpleRoute;

/**
 * Упрощенная версия WaitNextPointForRouteAutoRecorder для простых маршрутов без привязки к зонам.
 */
public class WaitNextPointForSimpleRouteAutoRecorder extends NTask {
    Coord2d last;
    double dist = 77.0;
    SimpleRoute route;
    Gob oldPlayer;

    public WaitNextPointForSimpleRouteAutoRecorder(Coord2d last, SimpleRoute route) {
        this.last = last;
        this.route = route;
        this.oldPlayer = NUtils.player();
    }

    @Override
    public boolean check() {
        Gob player = NUtils.player();

        if (player != this.oldPlayer)
            return true;

        this.oldPlayer = player;

        if(last == null) {
            return true;
        }

        return player.rc.dist(last) >= dist;
    }
}









