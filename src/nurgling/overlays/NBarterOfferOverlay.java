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
        for(int i = 0; i < entries.length; i++)
            if(entries[i] != null && entries[i] != next[i]) entries[i].dispose();
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
        boolean ownedIcon;
        String categoryName;
        Entry(int resourceId, byte[] data) {this.resourceId = resourceId; this.data = data;}
        void resolve() {
            if(icon != null || unavailable) return;
            try {
                if(iconResource == null) {
                    Resource world = gob.context(Resource.Resolver.class).getres(resourceId).get();
                    if("gfx/terobjs/items/seeds".equals(world.name))
                        categoryName = nurgling.i18n.L10n.get("barter.overlay.seeds");
                    else if("gfx/terobjs/items/filet-r".equals(world.name))
                        categoryName = nurgling.i18n.L10n.get("barter.overlay.fillet");
                    else if("gfx/terobjs/items/cheese".equals(world.name))
                        categoryName = nurgling.i18n.L10n.get("barter.overlay.cheese");
                    else if("gfx/terobjs/items/egg".equals(world.name))
                        categoryName = nurgling.i18n.L10n.get("barter.overlay.egg");
                    else if("gfx/terobjs/items/testis".equals(world.name))
                        categoryName = nurgling.i18n.L10n.get("barter.overlay.testis");
                    else if(world.name.startsWith("gfx/terobjs/items/coins-"))
                        categoryName = nurgling.i18n.L10n.get("barter.overlay.coins");
                    if("lib/frozen".equals(world.name)) {
                        resolveFrozenGem();
                        return;
                    }
                    String path = BarterOfferData.inventoryResource(world.name);
                    if(path == null) {unavailable = true; return;}
                    // Generic world models use explicitly labelled category icons.
                    iconResource = (path.startsWith("nurgling/") ? Resource.local() : Resource.remote()).load(path);
                }
                Resource resource = iconResource.get();
                Resource.Image image = resource.layer(Resource.imgc);
                Resource.Tooltip tooltip = resource.layer(Resource.tooltip);
                if(image == null || (tooltip == null && categoryName == null)) {unavailable = true; return;}
                icon = image.tex(); // Resource-owned texture, shared and not disposed here.
                name = categoryName == null ? tooltip.text() : categoryName;
            } catch(Loading ignored) {
            } catch(Resource.LoadFailedException ignored) {unavailable = true;}
        }
        void resolveFrozenGem() {
            if(data.length < 7 || (data[2] & 2) == 0) {unavailable = true; return;}
            Resource.Resolver rr = gob.context(Resource.Resolver.class);
            MessageBuf frozen = new MessageBuf(Arrays.copyOfRange(data, 7, data.length));
            Resource base = rr.getres(frozen.uint16()).get();
            byte[] sub = frozen.bytes(frozen.uint8());
            Resource.Resolver mapped = new Resource.Resolver.ResourceMap(rr, frozen);
            if(!"gfx/terobjs/items/gems/gemstone".equals(base.name) || sub.length != 4) {
                unavailable = true; return;
            }
            Resource inventory = Resource.remote().load("gfx/invobjs/gems/gemstone").get();
            Resource.Resolver gemResources = id -> {
                Resource original = mapped.getres(id).get();
                String path = BarterOfferData.inventoryResource(original.name);
                return path == null ? original.indir() : Resource.remote().load(path);
            };
            GSprite.Owner owner = new GSprite.Owner() {
                public Resource getres() {return inventory;}
                public Random mkrandoom() {return new Random(0);}
                public <C> C context(Class<C> cl) {
                    return cl == Resource.Resolver.class ? cl.cast(gemResources) : gob.context(cl);
                }
            };
            MessageBuf ids = new MessageBuf(sub);
            gemResources.getres(ids.uint16()).get();
            int material = ids.uint16();
            if(material != 65535) gemResources.getres(material).get();
            // Both resource factories use the same two uint16 values: cut and material.
            GSprite sprite = GSprite.create(owner, inventory, new MessageBuf(sub));
            java.awt.image.BufferedImage img = ((GSprite.ImageSprite)sprite).image();
            icon = new TexI(img);
            ownedIcon = true;
            name = sprite instanceof ItemInfo.Name.Dynamic ? ((ItemInfo.Name.Dynamic)sprite).name() : "Gemstone";
        }
        void dispose() {if(ownedIcon && icon != null) icon.dispose();}
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
        for(Entry entry : entries) if(entry != null) entry.dispose();
        Arrays.fill(entries, null);
        synchronized(active) {active.remove(this);}
        super.dispose();
    }
}
