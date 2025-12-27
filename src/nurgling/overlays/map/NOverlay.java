package nurgling.overlays.map;

import haven.*;
import haven.render.*;
import nurgling.*;
import nurgling.areas.*;
import nurgling.widgets.NMiniMap;

import java.awt.*;
import java.util.*;

public class NOverlay extends MapView.MapRaster
{
    final Integer id;
    public boolean requpdate2 = false;

    public boolean requpdate(){
        return false;
    }
    Color bc;
    public final Grid base = new Grid<RenderTree.Node>() {
        public RenderTree.Node getcut(Coord cc) {
            return(map.getnolcut(id, cc));
        }
    };
    public final Grid outl = new Grid<RenderTree.Node>() {
        public RenderTree.Node getcut(Coord cc) {
            return(map.getnedgecut(id, cc));
        }
    };

    public NOverlay(Integer id) {
        super(NUtils.getGameUI().map.glob.map, NUtils.getGameUI().map.view);
        if(id >= 0) {
            NArea a = NUtils.getArea(id);
            bc = (a != null && a.color != null) ? a.color : new Color(194, 194, 65, 56);
        } else {
            // Для кастомных оверлеев цвет обычно задаётся в наследниках,
            // но на всякий случай оставим дефолт.
            bc = new Color(194, 194, 65, 56);
        }
        this.id = id;
    }

    public void tick() {
        super.tick();
        // ВАЖНО: Проверяем, что area инициализирован перед использованием
        // area инициализируется в super.tick(), но может быть null если NUtils.getGameUI() == null
        if (area == null) {
            return;
        }
        
        // ВАЖНО: Оверлеи зон (визуальное выделение) отображаются ВСЕГДА
        // Проверяем только локальное скрытие зоны (hide) с учетом тоггла
        if (id >= 0) {
            // ВАЖНО: Проверяем что GameUI доступен перед вызовом getArea()
            if (NUtils.getGameUI() == null) {
                return;
            }
            NArea zoneArea = NUtils.getArea(id);
            if (zoneArea != null && zoneArea.hide) {
                // Проверяем тоггл "показывать все зоны" для скрытых зон
                NGameUI gui = NUtils.getGameUI();
                boolean showAllZones = false;
                if (gui != null) {
                    // Пробуем через mmapw.miniMap (это более надежный способ, так как miniMap точно является NMiniMap)
                    if (gui.mmapw != null && gui.mmapw.miniMap != null && gui.mmapw.miniMap instanceof NMiniMap) {
                        showAllZones = ((NMiniMap) gui.mmapw.miniMap).showAllZonesAlways;
                    }
                    // Запасной вариант через mmap
                    else if (gui.mmap != null && gui.mmap instanceof NMiniMap) {
                        showAllZones = ((NMiniMap) gui.mmap).showAllZonesAlways;
                    }
                }
                
                // Скрываем оверлей только если зона скрыта локально И тоггл не включен
                if (!showAllZones) {
                    return;
                }
            }
        }
        
        // ВАЖНО: Вызываем base.tick() и outl.tick() только если area инициализирован
        // base и outl используют area через this.this$0.area, поэтому нужна проверка
        base.tick();
        outl.tick();
    }

    public void added(RenderTree.Slot slot) {
//			Material overlay_mat = new Material(new BaseColor(194,194,65,56));
        slot.add(base,new BaseColor(bc));
        slot.add(outl, new BaseColor(200,200,200,200));
        super.added(slot);
    }

    public Loading loading() {
        Loading ret = super.loading();
        if(ret != null)
            return(ret);
        if((ret = base.lastload) != null)
            return(ret);
        return(null);
    }

    public void remove() {

        slot.remove();
        for(MCache.Grid.Cut cut : cuts)
        {
            cut.nols.remove(id);
            cut.nedgs.remove(id);
        }
    }

    public RenderTree.Node makenol(MapMesh mm, Long grid_id, Coord grid_ul) {
        if(mm.olvert == null)
            mm.olvert = mm.makeolvbuf();
        class Buf implements Tiler.MCons {
            short[] fl = new short[16];
            int fn = 0;

            public void faces(MapMesh m, Tiler.MPart d) {
                while(fn + d.f.length > fl.length)
                    fl = Utils.extend(fl, fl.length * 2);
                for(int fi : d.f)
                    fl[fn++] = (short)mm.olvert.vl[d.v[fi].vi];
            }
        }
        Coord t = new Coord();
        Buf buf = new Buf();
        NArea a = NUtils.getArea(id);
        if (a == null || a.space == null || a.space.space == null) {
            return null;
        }
        NArea.VArea space = a.space.space.get(grid_id);
        if (space == null || space.area == null) {
            // Зона не содержит текущий grid_id (или данные обновляются/удалены) — просто ничего не рисуем.
            return null;
        }
        Area curArea = space.area.xl(grid_ul);
        if (curArea == null) {
            return null;
        }
        for(t.y = 0; t.y < mm.sz.y; t.y++) {
            for(t.x = 0; t.x < mm.sz.x; t.x++) {
                Coord gc = t.add(mm.ul);
                if(curArea.contains(gc))
                {
                    mm.map.tiler(mm.map.gettile(gc)).lay(mm, t, gc, buf, false);
                }
            }
        }

        if(buf.fn == 0)
            return(null);
        haven.render.Model mod = new haven.render.Model(haven.render.Model.Mode.TRIANGLES, mm.olvert.dat,
                new haven.render.Model.Indices(buf.fn, NumberFormat.UINT16, DataBuffer.Usage.STATIC,
                        DataBuffer.Filler.of(Arrays.copyOf(buf.fl, buf.fn))));
        return(new MapMesh.ShallowWrap(mod, new MapMesh.NOLOrder(id)));
    }

    public RenderTree.Node makenolol(MapMesh mm, Long grid_id, Coord grid_ul) {
        if(mm.olvert == null)
            mm.olvert = mm.makeolvbuf();
        class Buf implements Tiler.MCons {
            int mask;
            short[] fl = new short[16];
            int fn = 0;

            public void faces(MapMesh m, Tiler.MPart d) {
                byte[] ef = new byte[d.v.length];
                for(int i = 0; i < d.v.length; i++) {
                    if(d.tcy[i] == 0.0f) ef[i] |= 1;
                    if(d.tcx[i] == 1.0f) ef[i] |= 2;
                    if(d.tcy[i] == 1.0f) ef[i] |= 4;
                    if(d.tcx[i] == 0.0f) ef[i] |= 8;
                }
                while(fn + (d.f.length * 2) > fl.length)
                    fl = Utils.extend(fl, fl.length * 2);
                for(int i = 0; i < d.f.length; i += 3) {
                    for(int a = 0; a < 3; a++) {
                        int b = (a + 1) % 3;
                        if((ef[d.f[i + a]] & ef[d.f[i + b]] & mask) != 0) {
                            fl[fn++] = (short)mm.olvert.vl[d.v[d.f[i + a]].vi];
                            fl[fn++] = (short)mm.olvert.vl[d.v[d.f[i + b]].vi];
                        }
                    }
                }
            }
        }
        Area a = Area.sized(mm.ul, mm.sz);

        Buf buf = new Buf();
        NArea area = NUtils.getArea(id);
        if (area == null || area.space == null || area.space.space == null) {
            return null;
        }
        NArea.VArea space = area.space.space.get(grid_id);
        if (space == null || space.area == null) {
            return null;
        }
        Area curArea = space.area.xl(grid_ul);
        if (curArea == null) {
            return null;
        }
        Area fullarea = area.getArea();
        if (fullarea == null) {
            return null;
        }
        for(Coord t : a) {
            if(curArea.contains(t))
            {
                buf.mask = 0;
                for(int d = 0; d < 4; d++) {
                    if(!fullarea.contains(t.add(Coord.uecw[d])))
                        buf.mask |= 1 << d;
                }
                if(buf.mask != 0)
                    mm.map.tiler(mm.map.gettile(t)).lay(mm, t.sub(a.ul), t, buf, false);
            }
        }
        if(buf.fn == 0)
            return(null);
        haven.render.Model mod = new haven.render.Model(haven.render.Model.Mode.LINES, mm.olvert.dat,
                new haven.render.Model.Indices(buf.fn, NumberFormat.UINT16, DataBuffer.Usage.STATIC,
                        DataBuffer.Filler.of(Arrays.copyOf(buf.fl, buf.fn))));
        return(new MapMesh.ShallowWrap(mod, Pipe.Op.compose(new MapMesh.NOLOrder(id), new States.LineWidth(2))));
    }

    public ArrayList<MCache.Grid.Cut> cuts = new ArrayList<>();
}