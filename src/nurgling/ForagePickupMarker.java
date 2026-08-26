package nurgling;

import haven.*;
import haven.res.ui.stackinv.ItemStack;
import nurgling.tools.ForageMarkerLogic;

import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;

public final class ForagePickupMarker {
    private static final Object LOCK = new Object();
    private static Pending pending;
    private static WeakReference<NGItem> watching;
    private static long watchStartMs;

    private static final class Pending {
        final long segmentId;
        final Coord tileCoords;
        final long createdMs;
        Pending(long segmentId, Coord tileCoords) {
            this.segmentId = segmentId;
            this.tileCoords = tileCoords;
            this.createdMs = System.currentTimeMillis();
        }
    }

    private ForagePickupMarker() {}

    public static void noteWorldPick(Gob gob) {
        try {
            if (gob == null) return;
            String gobName = (gob.ngob != null) ? gob.ngob.name : null;
            if (ForageMarkerLogic.isGardenPot(gobName)) return;
            NGameUI gui = NUtils.getGameUI();
            MiniMap.Location sessloc = sessloc(gui);
            if (gui == null || sessloc == null) return;
            Coord tileCoords = tileCoords(gob, gui, sessloc);
            if (tileCoords == null) return;
            synchronized (LOCK) {
                pending = new Pending(sessloc.seg.id, tileCoords);
                watching = null;
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
            NGameUI gui = NUtils.getGameUI();
            if (gui == null) return;
            NInventory inv = findInventory(item);
            if (inv == null) return;
            boolean isMain = gui.maininv == inv;
            boolean hasParentGob = inv.parentGob != null;
            if (!ForageMarkerLogic.acceptsPickedItemInventory(isMain, hasParentGob)) return;
            synchronized (LOCK) {
                if (pending == null) return;
                if (System.currentTimeMillis() - pending.createdMs > ForageMarkerLogic.QUALITY_WAIT_MS) {
                    pending = null;
                    return;
                }
                watching = new WeakReference<NGItem>((NGItem) item);
                watchStartMs = System.currentTimeMillis();
            }
        } catch (Exception ignored) {
        }
    }

    public static void onItemTick(NGItem item) {
        try {
            Pending p;
            synchronized (LOCK) {
                if (watching == null || watching.get() != item) return;
                if (System.currentTimeMillis() - watchStartMs > ForageMarkerLogic.QUALITY_WAIT_MS) {
                    watching = null;
                    pending = null;
                    return;
                }
                p = pending;
            }
            if (p == null) return;
            if (!ForageMarkerLogic.shouldPlace(item.quality)) {
                if (item.quality != null) {
                    synchronized (LOCK) {
                        watching = null;
                        pending = null;
                    }
                }
                return;
            }
            String name = item.name();
            BufferedImage icon = iconOf(item);
            if (name == null || icon == null) return;
            NGameUI gui = NUtils.getGameUI();
            if (gui == null || gui.labeledMarkService == null) return;
            String label = ForageMarkerLogic.formatLabel(item.quality);
            gui.labeledMarkService.addForageMark(label, name, p.segmentId, p.tileCoords, icon);
            synchronized (LOCK) {
                watching = null;
                pending = null;
            }
        } catch (Exception ignored) {
        }
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
        try {
            Gob player = NUtils.player();
            if (player != null && player.rc != null) {
                return player.rc.floor(MCache.tilesz).add(sessloc.tc);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static NInventory findInventory(GItem item) {
        if (item == null) return null;
        Widget p = item.parent;
        if (p instanceof NInventory) return (NInventory) p;
        if (p instanceof ItemStack) {
            Widget stackParent = p.parent;
            if (stackParent instanceof GItem.ContentsWindow) {
                GItem stackContainer = ((GItem.ContentsWindow) stackParent).cont;
                if (stackContainer != null && stackContainer.parent instanceof NInventory) {
                    return (NInventory) stackContainer.parent;
                }
            }
            NInventory walked = walkToInventory(p);
            if (walked != null) return walked;
        }
        return walkToInventory(p);
    }

    private static NInventory walkToInventory(Widget start) {
        for (Widget w = start; w != null; w = w.parent) {
            if (w instanceof NInventory) return (NInventory) w;
        }
        return null;
    }

    private static BufferedImage iconOf(NGItem item) {
        try {
            GSprite spr = item.spr;
            if (spr instanceof StaticGSprite) {
                return ((StaticGSprite) spr).img.img;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
