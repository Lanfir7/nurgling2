package nurgling.overlays;

import haven.*;
import haven.render.Homo3D;
import haven.render.Pipe;
import haven.render.RenderTree;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NStyle;
import nurgling.NUtils;
import nurgling.areas.AreaLabelSync;
import nurgling.areas.NArea;
import nurgling.widgets.Specialisation;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class NAreaLabel extends Sprite implements RenderTree.Node, PView.Render2D {
    private boolean isSelected = false;
    volatile boolean disposed;
    boolean requestNeeded = true;
    private int renderSlots;
    private final AreaLabelRenderQueue.Mailbox<Images> raster = new AreaLabelRenderQueue.Mailbox<>();
    protected Coord3f pos;
    public TexI label = null;
    public TexI sellabel = null;
    public TexI graylabel = null;
    NArea area;
    public Coord sc;
    boolean forced = false;
    int sizeSpec;

    public NAreaLabel(Owner owner, NArea area) {
        super(owner, null);
        pos = new Coord3f(0, 0, 2);
        this.area = area;
        sizeSpec = area.spec.size();
        update();
    }

    /** Invalidates a snapshot after name, quality, or specialisation changes. */
    public synchronized void update() {
        if (!disposed) {
            requestNeeded = true;
            raster.invalidate();
        }
    }

    static final class Style {
        final Font font;
        final Color color;
        final boolean antialias;
        final int glowRadius;
        final int blurRadius;
        final Color outline;

        Style(Font font, Color color, boolean antialias, int glowRadius, int blurRadius, Color outline) {
            this.font = font;
            this.color = color;
            this.antialias = antialias;
            this.glowRadius = glowRadius;
            this.blurRadius = blurRadius;
            this.outline = outline;
        }

        BufferedImage render(String text) {
            // Foundry caches FontMetrics internally, so each worker render gets its own instance.
            Text.Foundry foundry = new Text.Foundry(font, color).aa(antialias);
            return new PUtils.BlurFurn(foundry, glowRadius, blurRadius, outline).render(text).img;
        }
    }

    static Style snapshotStyle(Text.Furnace source) {
        PUtils.BlurFurn blur = (PUtils.BlurFurn) source;
        Text.Foundry foundry = (Text.Foundry) blur.back;
        return new Style(foundry.font, foundry.defcol, foundry.aa,
                blur.grad, blur.brad, blur.col);
    }

    static final class Request {
        final String name;
        final String quality;
        final List<BufferedImage> icons;
        final Style[] titleStyles;
        final Font qualityFont;
        final int iconSize;
        final int specGap;
        final int qualityGap;

        Request(String name, String quality, List<BufferedImage> icons, Style[] titleStyles,
                Font qualityFont, int iconSize, int specGap, int qualityGap) {
            this.name = name;
            this.quality = quality;
            this.icons = List.copyOf(icons);
            this.titleStyles = titleStyles.clone();
            this.qualityFont = qualityFont;
            this.iconSize = iconSize;
            this.specGap = specGap;
            this.qualityGap = qualityGap;
        }
    }

    private Request snapshot() {
        List<BufferedImage> icons = new ArrayList<>();
        for (NArea.Specialisation spec : area.spec) {
            Specialisation.SpecialisationItem item = Specialisation.findSpecialisation(spec.name);
            if (item != null && item.image != null)
                icons.add(item.image); // Resource-loaded icons are never mutated after publication.
        }
        String quality = area.showsQuality() && area.maxQuality >= 0
                ? "Q" + area.maxQuality : null;
        int fontSize = UI.scale(11);
        Font qualityFont = Text.sans.deriveFont(Font.BOLD, fontSize)
                .deriveFont(UI.scale(11f));
        return new Request(area.name, quality, icons,
                new Style[] {snapshotStyle(NStyle.openings), snapshotStyle(NStyle.selopenings),
                        snapshotStyle(NStyle.disabledopenings)}, qualityFont,
                UI.scale(32), UI.scale(5), UI.scale(1));
    }

    static final class Images {
        final BufferedImage normal;
        final BufferedImage selected;
        final BufferedImage disabled;

        Images(BufferedImage normal, BufferedImage selected, BufferedImage disabled) {
            this.normal = normal;
            this.selected = selected;
            this.disabled = disabled;
        }
    }

    static Images rasterize(Request request) {
        BufferedImage[] labels = new BufferedImage[3];
        Color[] qualityColors = {Color.WHITE, Color.GREEN, Color.GRAY};
        for (int i = 0; i < labels.length; i++) {
            labels[i] = request.titleStyles[i].render(request.name);
            if (request.quality != null) {
                Style qualityStyle = new Style(request.qualityFont, qualityColors[i], true,
                        1, 1, new Color(60, 30, 30));
                labels[i] = stackQuality(labels[i], qualityStyle.render(request.quality),
                        request.qualityGap);
            }
        }
        if (!request.icons.isEmpty()) {
            BufferedImage icons = TexI.mkbuf(new Coord(request.iconSize, request.iconSize));
            Graphics g = icons.getGraphics();
            g.drawImage(request.icons.get(0), 0, 0, request.iconSize, request.iconSize, null);
            g.dispose();
            for (int i = 1; i < request.icons.size(); i++)
                icons = ItemInfo.catimgsh(request.specGap, icons,
                        new Coord(request.iconSize, request.iconSize), request.icons.get(i));
            for (int i = 0; i < labels.length; i++)
                labels[i] = ItemInfo.catimgsh(request.specGap, labels[i], icons);
        }
        return new Images(labels[0], labels[1], labels[2]);
    }

    private static BufferedImage stackQuality(BufferedImage title, BufferedImage quality, int gap) {
        int width = Math.max(title.getWidth(), quality.getWidth());
        BufferedImage result = TexI.mkbuf(new Coord(width, title.getHeight() + gap + quality.getHeight()));
        Graphics2D g = result.createGraphics();
        g.drawImage(title, (width - title.getWidth()) / 2, 0, null);
        g.drawImage(quality, (width - quality.getWidth()) / 2, title.getHeight() + gap, null);
        g.dispose();
        return result;
    }

    private void installReady() {
        Images images = raster.take();
        if (images == null || disposed)
            return;
        TexI newNormal = new TexI(images.normal);
        TexI newSelected = new TexI(images.selected);
        TexI newDisabled = new TexI(images.disabled);
        TexI oldNormal = label, oldSelected = sellabel, oldDisabled = graylabel;
        label = newNormal;
        sellabel = newSelected;
        graylabel = newDisabled;
        requestNeeded = false;
        if (oldNormal != null) oldNormal.dispose();
        if (oldSelected != null) oldSelected.dispose();
        if (oldDisabled != null) oldDisabled.dispose();
    }

    private void releaseTextures() {
        TexI oldNormal = label, oldSelected = sellabel, oldDisabled = graylabel;
        label = sellabel = graylabel = null;
        if (oldNormal != null) oldNormal.dispose();
        if (oldSelected != null) oldSelected.dispose();
        if (oldDisabled != null) oldDisabled.dispose();
    }

    @Override
    public synchronized void added(RenderTree.Slot slot) {
        if (!disposed)
            renderSlots++;
    }

    @Override
    public synchronized void removed(RenderTree.Slot slot) {
        if (disposed)
            return;
        if (renderSlots > 0 && --renderSlots == 0) {
            raster.invalidate();
            requestNeeded = true;
            sc = null;
            releaseTextures();
        }
    }

    @Override
    public boolean tick(double dt) {
        if (disposed)
            return true;
        if (NUtils.getGameUI() == null)
            return false;
        isSelected = NUtils.getGameUI().areas != null && NUtils.getGameUI().areas.al.sel != null
                && NUtils.getGameUI().areas.al.sel.area == area;
        if (area.spec.size() != sizeSpec) {
            sizeSpec = area.spec.size();
            update();
        }
        boolean gone = NUtils.findGob(((Gob) owner).id) == null;
        if (gone)
            dispose();
        return gone;
    }

    @Override
    public synchronized void draw(GOut g, Pipe state) {
        if (disposed) {
            sc = null;
            return;
        }
        NGameUI gui = NUtils.getGameUI();
        boolean areasWindowOpen = gui != null && gui.areas != null && gui.areas.visible();
        boolean showAllZones = AreaLabelSync.toggleOn(NConfig.get(NConfig.Key.showAllZonesAlways));
        if ((area.hide && !showAllZones) || (!areasWindowOpen && !showAllZones)
                || area.getLoadedRCArea(false) == null) {
            sc = null;
            return;
        }

        installReady();
        if (requestNeeded && raster.canSubmit(System.nanoTime())) {
            try {
                Request request = snapshot();
                raster.submit(() -> rasterize(request), System.nanoTime());
            } catch (Loading | java.util.ConcurrentModificationException ignored) {
                // Resource or area data is still arriving; the next visible draw retries.
            }
        }
        TexI visible = isSelected ? sellabel : area.hide ? graylabel : label;
        if (visible == null) {
            sc = null;
            return;
        }
        Coord projected = Homo3D.obj2view(pos, state, Area.sized(g.sz())).round2();
        int markerRadius = NStyle.iCropMap.get(NStyle.CropMarkers.BLUE).sz().y / 2;
        sc = projected.sub(0, visible.sz().y / 2 + markerRadius + UI.scale(3));
        g.aimage(visible, sc, 0.5, 0.5);
    }

    public synchronized boolean isect(Coord pc) {
        if (disposed || sc == null || label == null)
            return false;
        NGameUI gui = NUtils.getGameUI();
        if (!AreaLabelSync.labelsClickable(gui != null && gui.areas != null && gui.areas.visible()))
            return false;
        Coord ul = sc.sub(label.sz().div(2));
        return pc.isect(ul, label.sz());
    }

    @Override
    public synchronized void dispose() {
        if (disposed)
            return;
        disposed = true;
        raster.close();
        sc = null;
        releaseTextures();
        super.dispose();
    }
}
