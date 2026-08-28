package nurgling;

import haven.*;
import haven.res.ui.stackinv.ItemStack;
import nurgling.tools.ForageMarkerLogic;

import java.awt.image.BufferedImage;

public final class ForagePickupMarker {
    private final Object lock = new Object();
    private final ForageMarkerLogic.PickupSession session = new ForageMarkerLogic.PickupSession();
    private final NGameUI gui;

    public ForagePickupMarker(NGameUI gui) {
        this.gui = gui;
    }

    public void noteWorldPick(Gob gob) {
        try {
            if (gob == null) return;
            String gobName = (gob.ngob != null) ? gob.ngob.name : null;
            if (gobName == null || gobName.isEmpty()) return;
            if (ForageMarkerLogic.isGardenPot(gobName)) return;
            MiniMap.Location sessloc = sessloc(gui);
            if (gui == null || sessloc == null) return;
            Coord tileCoords = tileCoords(gob, gui, sessloc);
            if (tileCoords == null) return;
            synchronized (lock) {
                session.notePick(sessloc.seg.id, tileCoords.x, tileCoords.y, gobName,
                    System.currentTimeMillis());
            }
        } catch (Exception ignored) {
        }
    }

    public static void onInventoryItem(NInventory inv, GItem item) {
        onNewItem(item);
    }

    public static void onNewItem(GItem item) {
        try {
            if (!(item instanceof NGItem)) return;
            NGItem ng = (NGItem) item;
            ForagePickupMarker marker = markerFor(ng);
            if (marker != null) marker.acceptNewItem(ng);
        } catch (Exception ignored) {
        }
    }

    public static void onItemTick(NGItem item) {
        try {
            ForagePickupMarker marker = markerFor(item);
            if (marker != null) marker.tickItem(item);
        } catch (Exception ignored) {
        }
    }

    public void dispose() {
        synchronized (lock) {
            session.clear();
        }
    }

    private void acceptNewItem(NGItem item) {
        try {
            double minQ = ForageMarkerLogic.minQualityFromConfig(NConfig.get(NConfig.Key.forageMarkerMinQuality));
            synchronized (lock) {
                session.offerItem(item, isStackContainer(item), item.parent instanceof ItemStack,
                    item.quality, resourceName(item), minQ, System.currentTimeMillis());
            }
        } catch (Exception ignored) {
        }
    }

    private void tickItem(NGItem item) {
        try {
            if (gui == null || gui.labeledMarkService == null) return;
            double minQ = ForageMarkerLogic.minQualityFromConfig(NConfig.get(NConfig.Key.forageMarkerMinQuality));
            String name = resolveName(item);
            ForageMarkerLogic.Place place;
            synchronized (lock) {
                if (!session.isWatching(item)) return;
                place = session.placeTick(item, isStackContainer(item), item.quality, name,
                    resourceName(item), minQ, System.currentTimeMillis());
            }
            if (place == null) return;
            String label = ForageMarkerLogic.formatLabel(item.quality);
            gui.labeledMarkService.addForageMark(label, name, place.segmentId,
                new Coord(place.tileX, place.tileY), iconOf(item));
        } catch (Exception ignored) {
        }
    }

    private static ForagePickupMarker markerFor(NGItem item) {
        if (item == null || item.ui == null || !(item.ui.gui instanceof NGameUI)) return null;
        return ((NGameUI) item.ui.gui).foragePickupMarker;
    }

    private static MiniMap.Location sessloc(NGameUI gui) {
        if (gui == null) return null;
        if (gui.mmap != null && gui.mmap.sessloc != null) return gui.mmap.sessloc;
        if (gui.mapfile != null && gui.mapfile.view != null) return gui.mapfile.view.sessloc;
        return null;
    }

    private static Coord tileCoords(Gob gob, NGameUI gui, MiniMap.Location sessloc) {
        try {
            if (gob != null && gob.rc != null) {
                return gob.rc.floor(MCache.tilesz).add(sessloc.tc);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isStackContainer(NGItem item) {
        if (item == null) return false;
        boolean hasAmount = false;
        try {
            hasAmount = item.getInfo(GItem.Amount.class) != null;
        } catch (Exception ignored) {
        }
        return ForageMarkerLogic.isLikelyStackContainer(
            item.parent instanceof ItemStack,
            item.contents instanceof ItemStack,
            hasAmount,
            item.quality);
    }

    private static String resolveName(NGItem item) {
        String tooltip = null;
        String resName = resourceName(item);
        try {
            Resource res = item.getres();
            if (res != null) {
                Resource.Tooltip tt = res.layer(Resource.tooltip);
                if (tt != null) tooltip = tt.t;
            }
        } catch (Exception ignored) {
        }
        return ForageMarkerLogic.resolveItemName(item.name(), tooltip, resName);
    }

    private static String resourceName(NGItem item) {
        try {
            Resource res = item.getres();
            return res != null ? res.name : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BufferedImage iconOf(NGItem item) {
        try {
            GSprite spr = item.spr;
            if (spr instanceof GSprite.ImageSprite) {
                return ((GSprite.ImageSprite) spr).image();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
