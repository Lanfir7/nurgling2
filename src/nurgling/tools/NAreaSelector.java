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
                int createdAreaId = -1;
                if(mode!=Mode.SELECT)
                {
                    if(result!=null)
                    {
                        if(mode == Mode.CREATE)
                        {
                            createdAreaId = ((NMapView) NUtils.getGameUI().map).addArea(result);
                        }
                        else if(mode == Mode.CHANGE)
                        {
                            final NMapView mapView = (NMapView) NUtils.getGameUI().map;
                            
                            try {
                                // Обновляем данные зоны
                                area.space = result;
                                area.lastLocalChange = System.currentTimeMillis();
                                
                                // Синхронизируем grids_id - используем синхронизацию для потокобезопасности
                                synchronized(area.grids_id) {
                                    area.grids_id.clear();
                                    area.grids_id.addAll(area.space.space.keySet());
                                }
                                
                                for(NArea.VArea space: area.space.space.values())
                                    space.isVis = false;
                                
                                // Удаляем старый overlay (ConcurrentHashMap - потокобезопасен)
                                nurgling.overlays.map.NOverlay oldNol = mapView.nols.get(area.id);
                                if (oldNol != null) {
                                    try {
                                        oldNol.remove();
                                    } catch (Exception e) {
                                        // Игнорируем ошибки при удалении overlay
                                    }
                                    mapView.nols.remove(area.id);
                                }
                                
                                // Удаляем старый dummy - синхронизируем с dummys
                                if (area.gid != Long.MIN_VALUE) {
                                    synchronized(mapView.dummys) {
                                        haven.Gob dummy = mapView.dummys.get(area.gid);
                                        if (dummy != null) {
                                            try {
                                                mapView.glob.oc.remove(dummy);
                                            } catch (Exception e) {
                                                // Игнорируем ошибки
                                            }
                                            mapView.dummys.remove(area.gid);
                                        }
                                    }
                                    area.gid = Long.MIN_VALUE;
                                }
                                
                                // Пересоздаем визуальные элементы
                                mapView.createAreaLabel(area.id);
                                
                                // Устанавливаем requpdate2 для обновления контура
                                nurgling.overlays.map.NOverlay newNol = mapView.nols.get(area.id);
                                if (newNol != null) {
                                    newNol.requpdate2 = true;
                                }
                            } catch (Exception e) {
                                System.err.println("NAreaSelector: Error updating area: " + e.getMessage());
                                e.printStackTrace();
                            } finally {
                                area.inWork = false;
                            }
                        }
                        NConfig.needAreasUpdate();
                    }
                    NUtils.getGameUI().areas.show();
                    // Set focus on the created/changed area
                    if(mode == Mode.CREATE && createdAreaId != -1) {
                        NUtils.getGameUI().areas.selectAreaById(createdAreaId);
                    } else if(mode == Mode.CHANGE && area != null) {
                        NUtils.getGameUI().areas.selectAreaById(area.id);
                    }
                }
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
            finally
            {
                // ВАЖНО: Всегда сбрасываем флаг режима выбора зоны
                ((NMapView) NUtils.getGameUI().map).isAreaSelectionMode.set(false);
            }
        }
    }
}
