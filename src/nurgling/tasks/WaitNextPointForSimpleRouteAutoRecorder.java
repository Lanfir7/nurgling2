package nurgling.tasks;

import haven.Coord2d;
import haven.Following;
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

        Coord2d current = getRouteAnchorPosition(player);
        if (current == null) {
            return true;
        }
        return current.dist(last) >= dist;
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


















