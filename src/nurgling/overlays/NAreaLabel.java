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

import java.awt.*;
import java.awt.image.BufferedImage;

public class NAreaLabel extends Sprite implements RenderTree.Node, PView.Render2D{
    private boolean isSelected = false;
    protected Coord3f pos;
    public TexI label = null;
    public TexI sellabel = null;
    public TexI graylabel = null;
    protected TexI img = null;
    NArea area;
    public Coord sc;
    boolean forced = false;
    int sizeSpec;
    public NAreaLabel(Owner owner, NArea area) {
        super(owner, null);
        pos = new Coord3f(0,0,2);
        this.area = area;
        sizeSpec = area.spec.size();
        update();
    }


    public void update()
    {
        BufferedImage img = NStyle.openings.render(area.name).img;
        BufferedImage selimg = NStyle.selopenings.render(area.name).img;
        BufferedImage grayimg = NStyle.disabledopenings.render(area.name).img;
        if (area.showsQuality() && area.maxQuality >= 0) {
            String quality = "Q" + area.maxQuality;
            img = stackQuality(img, qualityImage(quality, Color.WHITE));
            selimg = stackQuality(selimg, qualityImage(quality, Color.GREEN));
            grayimg = stackQuality(grayimg, qualityImage(quality, Color.GRAY));
        }
        if(!area.spec.isEmpty()) {
            int iconSize = UI.scale(32);
            BufferedImage first = Specialisation.findSpecialisation(area.spec.get(0).name) == null ? null : Specialisation.findSpecialisation(area.spec.get(0).name).image;
            BufferedImage ret = TexI.mkbuf(new Coord(iconSize, iconSize));
            Graphics g = ret.getGraphics();
            g.drawImage(first, 0, 0, iconSize, iconSize, null);
            g.dispose();
            first = ret;
            if (area.spec.size() > 1) {

                for (int i = 1; i < area.spec.size(); i++) {
                    first = ItemInfo.catimgsh(UI.scale(5), first, new Coord(iconSize, iconSize), Specialisation.findSpecialisation(area.spec.get(i).name).image);
                }
            }
            img = ItemInfo.catimgsh(UI.scale(5), img, first);
            selimg = ItemInfo.catimgsh(UI.scale(5), selimg, first);
            grayimg = ItemInfo.catimgsh(UI.scale(5), grayimg, first);
        }
        label = new TexI(img);
        sellabel = new TexI(selimg);
        graylabel = new TexI(grayimg);
    }

    private static BufferedImage qualityImage(String quality, Color color) {
        return new PUtils.BlurFurn(
                new Text.Foundry(Text.sans.deriveFont(Font.BOLD, UI.scale(11)), 11, color).aa(true),
                1, 1, new Color(60, 30, 30)).render(quality).img;
    }

    private static BufferedImage stackQuality(BufferedImage title, BufferedImage quality) {
        int width = Math.max(title.getWidth(), quality.getWidth());
        int gap = UI.scale(1);
        BufferedImage result = TexI.mkbuf(new Coord(width, title.getHeight() + gap + quality.getHeight()));
        Graphics2D g = result.createGraphics();
        g.drawImage(title, (width - title.getWidth()) / 2, 0, null);
        g.drawImage(quality, (width - quality.getWidth()) / 2, title.getHeight() + gap, null);
        g.dispose();
        return result;
    }

    @Override
    public boolean tick(double dt) {
        if(NUtils.getGameUI()==null)
            return false;
        isSelected =NUtils.getGameUI().areas!=null && NUtils.getGameUI().areas.al.sel != null && NUtils.getGameUI().areas.al.sel.area == area;
        if (area.spec.size() != sizeSpec) {
            sizeSpec = area.spec.size();
            update();
        }
        return NUtils.findGob(((Gob) owner).id) == null;
    }

    @Override
    public void draw(GOut g, Pipe state) {
        // ВАЖНО: Проверяем видимость зоны
        // Зона должна быть видна если:
        // 1. Окно редактирования зон открыто ИЛИ включен тоггл "показывать все зоны"
        // 2. Зона не скрыта локально (hide = false)
        NGameUI gui = NUtils.getGameUI();
        boolean areasWindowOpen = gui != null && gui.areas != null && gui.areas.visible();
        boolean showAllZones = AreaLabelSync.toggleOn(NConfig.get(NConfig.Key.showAllZonesAlways));
        
        if (area.hide && !showAllZones) {
            // Зона скрыта и не включен режим "показывать все"
            return;
        }
        
        if (!areasWindowOpen && !showAllZones) {
            return;
        }
        if (area.getLoadedRCArea(false) == null) {
            sc = null;
            return;
        }
        
        Coord projected = Homo3D.obj2view(pos, state, Area.sized(g.sz())).round2();
        int markerRadius = NStyle.iCropMap.get(NStyle.CropMarkers.BLUE).sz().y / 2;
        int gap = UI.scale(3);
        sc = projected.sub(0, label.sz().y / 2 + markerRadius + gap);
        if (label != null)
            if(isSelected)
            {
                g.aimage(sellabel, sc, 0.5, 0.5);
            }
            else if(area.hide && graylabel != null)
            {
                g.aimage(graylabel, sc, 0.5, 0.5);
            }
            else {
                g.aimage(label, sc, 0.5, 0.5);
            }
    }

    public boolean isect(Coord pc) {
        if(sc == null || label == null)
            return false;
        NGameUI gui = NUtils.getGameUI();
        if (!AreaLabelSync.labelsClickable(gui != null && gui.areas != null && gui.areas.visible()))
            return false;
        Coord ul = sc.sub(label.sz().div(2));
        return pc.isect(ul, label.sz());
    }
}
