package nurgling.tools;

import nurgling.*;
import nurgling.areas.*;
import nurgling.tasks.*;

public class NAreaSelector implements Runnable
{
    protected NArea.Space result;

    public enum Mode
    {
        CREATE,
        CHANGE,
        SELECT
    }

    Mode mode = Mode.CREATE;

    public NAreaSelector(Mode mode)
    {
        this.mode = mode;
    }

    public static void changeArea(NArea area)
    {
        new Thread(new NAreaSelector(area,Mode.CHANGE)).start();
    }

    NArea area = null;
    private NAreaSelector(NArea area, Mode mode)
    {
        this.area = area;
        this.mode = mode;
    }

    @Override
    public void run()
    {
        if (!((NMapView) NUtils.getGameUI().map).isAreaSelectionMode.get())
        {
            ((NMapView) NUtils.getGameUI().map).isAreaSelectionMode.set(true);
            try
            {
                SelectArea sa;
                if(mode!=Mode.SELECT)
                {
                    NUtils.getGameUI().areas.createMode = true;
                    NUtils.getGameUI().areas.hide();
                    NUtils.getGameUI().areas.createMode = false;
                }
                NUtils.getUI().core.addTask(sa = new SelectArea());
                if (sa.getResult() != null)
                {
                    result = sa.getResult();
                }
                if(mode!=Mode.SELECT)
                {
                    if(result!=null)
                    {
                        if(mode == Mode.CREATE)
                        {
                            ((NMapView) NUtils.getGameUI().map).addArea(result);
                        }
                        else if(mode == Mode.CHANGE)
                        {
                            area.space = result;
                            area.grids_id.clear();
                            area.grids_id.addAll(area.space.space.keySet());
                            for(NArea.VArea space: area.space.space.values())
                                space.isVis = false;
                            
                            // ВАЖНО: Удаляем старые визуальные элементы перед пересозданием,
                            // так как координаты изменились и нужно показать зону на новом месте
                            NMapView mapView = (NMapView) NUtils.getGameUI().map;
                            
                            // Удаляем старый overlay и очищаем данные из ВСЕХ grid'ов
                            synchronized (mapView.nols) {
                                nurgling.overlays.map.NOverlay nol = mapView.nols.get(area.id);
                                if (nol != null) {
                                    // ВАЖНО: Очищаем данные контура из ВСЕХ grid'ов перед пересозданием
                                    // Это гарантирует, что старый контур будет удален везде
                                    synchronized (mapView.glob.map.grids) {
                                        for (haven.MCache.Grid grid : mapView.glob.map.grids.values()) {
                                            if (grid != null) {
                                                // Очищаем данные из всех cuts этого grid'а
                                                try {
                                                    java.lang.reflect.Field cutsField = grid.getClass().getDeclaredField("cuts");
                                                    cutsField.setAccessible(true);
                                                    haven.MCache.Grid.Cut[] cuts = (haven.MCache.Grid.Cut[]) cutsField.get(grid);
                                                    if (cuts != null) {
                                                        for (haven.MCache.Grid.Cut cut : cuts) {
                                                            if (cut != null) {
                                                                cut.nols.remove(area.id);
                                                                cut.nedgs.remove(area.id);
                                                            }
                                                        }
                                                    }
                                                } catch (Exception e) {
                                                    // Если рефлексия не работает, просто логируем
                                                    System.err.println("NAreaSelector: Could not clear grid data for zone " + area.id + ": " + e.getMessage());
                                                }
                                            }
                                        }
                                        System.out.println("NAreaSelector: Cleared zone " + area.id + " contour data from all grids");
                                    }
                                    
                                    nol.remove();
                                    mapView.nols.remove(area.id);
                                }
                            }
                            
                            // Удаляем старый dummy (координаты изменились, нужно пересоздать на новом месте)
                            if (area.gid != Long.MIN_VALUE) {
                                haven.Gob dummy = mapView.dummys.get(area.gid);
                                if (dummy != null) {
                                    mapView.glob.oc.remove(dummy);
                                    mapView.dummys.remove(area.gid);
                                }
                                area.gid = Long.MIN_VALUE; // Сбрасываем gid для пересоздания
                            }
                            
                            // Пересоздаем визуальные элементы на новом месте
                            mapView.createAreaLabel(area.id);
                            
                            // ВАЖНО: Устанавливаем requpdate2 для NOverlay ПОСЛЕ создания,
                            // чтобы контур зоны обновился в реальном времени
                            // Это заставит MCache пересоздать данные контура (nols и nedgs)
                            synchronized (mapView.nols) {
                                nurgling.overlays.map.NOverlay nol = mapView.nols.get(area.id);
                                if (nol != null) {
                                    nol.requpdate2 = true;
                                    System.out.println("NAreaSelector: Set requpdate2=true for zone " + area.id + " overlay to update contour");
                                }
                            }
                            
                            area.inWork = false;
                            
                            // ВАЖНО: Сохраняем изменение координат в БД
                            // Это нужно для синхронизации с сервером и для сохранения изменений
                            try {
                                // Обновляем lastUpdated перед сохранением, чтобы синхронизация увидела изменение
                                area.lastUpdated = System.currentTimeMillis();
                                nurgling.areas.db.AreaDBManager.getInstance().saveArea(area);
                                System.out.println("NAreaSelector: Saved zone " + area.id + " (" + area.name + ") with new coordinates to DB");
                            } catch (Exception e) {
                                System.err.println("NAreaSelector: Failed to save area coordinates to database: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        NConfig.needAreasUpdate();
                        ((NMapView) NUtils.getGameUI().map).routeGraphManager.getGraph().connectAreaToRoutePoints(area);
                        NConfig.needRoutesUpdate();
                    }
                    NUtils.getGameUI().areas.show();
                }
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
