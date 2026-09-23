package nurgling.overlays;

import haven.*;
import haven.render.*;
import nurgling.NConfig;

import java.awt.Color;
import java.util.*;

/** Live stand offers, read only from world overlays; never opens or buys from a stand. */
public final class NBarterOfferOverlay extends NObjectTexLabel {
    private static final Set<NBarterOfferOverlay> active = Collections.newSetFromMap(new WeakHashMap<>());
    private final Gob gob;
    private final Entry[] entries = new Entry[5];
    private double elapsed = 1;
    private Coord screen;
    private long drawnAt;
    private int renderSlots;

    public NBarterOfferOverlay(Gob gob) {
        super(gob);
        this.gob = gob;
        pos = new Coord3f(0, 0, 12);
    }

    public static boolean supports(String name) {return "gfx/terobjs/barterstand".equals(name);}
    public static boolean enabled() {return !Boolean.FALSE.equals(NConfig.get(NConfig.Key.barterOfferIcons));}

    public static void ensureAttached(Gob gob) {
        gob.defer(() -> {
            if(gob.ngob != null && supports(gob.ngob.name) && gob.findol(NBarterOfferOverlay.class) == null)
                gob.addol(new Gob.Overlay(gob, new NBarterOfferOverlay(gob)), false);
        });
    }

    @Override public synchronized boolean tick(double dt) {
        elapsed += dt;
        if(elapsed < 0.25) return false;
        elapsed = 0;
        if(!enabled()) {screen = null; return false;}
        Entry[] next = new Entry[5];
        for(Gob.Overlay overlay : gob.ols) {
            try {
                byte[] bytes;
                Indir<Resource> source;
                if(overlay.sm instanceof OCache.OlSprite) {
                    OCache.OlSprite mill = (OCache.OlSprite)overlay.sm;
                    bytes = mill.sdt; source = mill.res;
                } else if(overlay.sm instanceof Sprite.Mill.FromRes) {
                    Sprite.Mill.FromRes mill = (Sprite.Mill.FromRes)overlay.sm;
                    bytes = mill.sdt; source = mill.res;
                } else continue;
                if(!"gfx/fx/eq".equals(source.get().name)) continue;
                BarterOfferData data = BarterOfferData.decode(bytes);
                if(data == null) continue;
                Entry old = entries[data.slot];
                next[data.slot] = old != null && old.resourceId == data.resourceId && Arrays.equals(old.data, bytes)
                        ? old : new Entry(data.resourceId, bytes.clone());
            } catch(Loading ignored) {
                // An unrecognised/loading source does not retain obsolete offer icons.
            } catch(Resource.LoadFailedException ignored) {}
        }
        System.arraycopy(next, 0, entries, 0, entries.length);
        for(Entry entry : entries) if(entry != null) entry.resolve();
        return false;
    }

    private final class Entry {
        final int resourceId;
        final byte[] data;
        Indir<Resource> iconResource;
        Tex icon;
        String name;
        boolean unavailable;
        Entry(int resourceId, byte[] data) {this.resourceId = resourceId; this.data = data;}
        void resolve() {
            if(icon != null || unavailable) return;
            try {
                if(iconResource == null) {
                    Resource world = gob.context(Resource.Resolver.class).getres(resourceId).get();
                    String path = BarterOfferData.inventoryResource(world.name);
                    if(path == null) {unavailable = true; return;}
                    // Verified inventory counterparts for live stand resources. Missing counterparts
                    // are omitted, never replaced with a guessed name or an unrelated generic icon.
                    iconResource = Resource.remote().load(path);
                }
                Resource resource = iconResource.get();
                Resource.Image image = resource.layer(Resource.imgc);
                Resource.Tooltip tooltip = resource.layer(Resource.tooltip);
                if(image == null || tooltip == null) {unavailable = true; return;}
                icon = image.tex(); // Resource-owned texture, shared and not disposed here.
                name = tooltip.text();
            } catch(Loading ignored) {
            } catch(Resource.LoadFailedException ignored) {unavailable = true;}
        }
    }

    @Override public synchronized void draw(GOut g, Pipe state) {
        screen = null;
        if(!enabled()) return;
        int count = 0;
        for(Entry entry : entries) if(entry != null && entry.icon != null) count++;
        if(count == 0) return;
        if(Homo3D.obj2clip(drawPos(state), state).w <= 0) return;
        Coord anchor = projectBillboard(g, state, owner, pos);
        int cell = UI.scale(34), gap = UI.scale(3), height = count * (cell + gap) - gap;
        Coord top = anchor.sub(cell / 2, height);
        if(top.x + cell <= 0 || top.x >= g.sz().x || top.y + height <= 0 || top.y >= g.sz().y) return;
        int index = 0;
        for(Entry entry : entries) {
            if(entry == null || entry.icon == null) continue;
            Coord c = top.add(0, index++ * (cell + gap));
            g.chcolor(new Color(28, 25, 22, 215)); g.frect(c, Coord.of(cell));
            g.chcolor(new Color(170, 133, 75, 230)); g.rect(c, Coord.of(cell)); g.chcolor();
            Coord size = entry.icon.sz();
            double scale = (double)(cell - UI.scale(4)) / Math.max(size.x, size.y);
            Coord fitted = new Coord(Math.max(1, (int)(size.x * scale)), Math.max(1, (int)(size.y * scale)));
            g.image(entry.icon, c.add((cell - fitted.x) / 2, (cell - fitted.y) / 2), fitted);
        }
        screen = top; drawnAt = System.nanoTime();
    }

    private synchronized String hit(Coord c) {
        if(screen == null || System.nanoTime() - drawnAt > 250_000_000L) return null;
        int index = 0, cell = UI.scale(34), gap = UI.scale(3);
        for(Entry entry : entries) {
            if(entry == null || entry.icon == null) continue;
            if(c.isect(screen.add(0, index++ * (cell + gap)), Coord.of(cell))) return entry.name;
        }
        return null;
    }

    public static String tooltip(Glob glob, Coord c) {
        if(!enabled()) return null;
        List<NBarterOfferOverlay> overlays;
        synchronized(active) {overlays = new ArrayList<>(active);}
        for(NBarterOfferOverlay overlay : overlays) {
            if(overlay.gob.glob != glob) continue;
            String tip = overlay.hit(c);
            if(tip != null) return tip;
        }
        return null;
    }

    @Override public synchronized void added(RenderTree.Slot slot) {
        renderSlots++;
        synchronized(active) {active.add(this);}
    }
    @Override public synchronized void removed(RenderTree.Slot slot) {
        if(renderSlots > 0 && --renderSlots == 0) {
            screen = null;
            synchronized(active) {active.remove(this);}
        }
    }
    @Override public synchronized void dispose() {
        screen = null;
        Arrays.fill(entries, null);
        synchronized(active) {active.remove(this);}
        super.dispose();
    }
}
