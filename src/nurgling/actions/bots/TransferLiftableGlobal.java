package nurgling.actions.bots;

import haven.*;
import nurgling.NGameUI;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.conf.NCarrierProp;
import nurgling.navigation.AreaNavigationHelper;
import nurgling.navigation.ChunkNavExecutor;
import nurgling.navigation.ChunkNavManager;
import nurgling.navigation.ChunkPath;
import nurgling.tasks.WaitCheckable;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;

/**
 * Transfers liftable objects using global zones with chunk navigation.
 * Automatically finds and uses global CarrierIn and CarrierOut zones.
 */
public class TransferLiftableGlobal implements Action
{
    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        nurgling.widgets.bots.Carrier w = null;
        NCarrierProp prop = null;
        try
        {
            NUtils.getUI().core.addTask(new WaitCheckable(NUtils.getGameUI().add((w = new nurgling.widgets.bots.Carrier()), UI.scale(200, 200))));
            prop = w.prop;
        } catch (InterruptedException e)
        {
            throw e;
        } finally
        {
            if (w != null)
                w.destroy();
        }
        if (prop == null)
        {
            return Results.ERROR("No config");
        }

        // Create context for transfer
        NContext context = new NContext(gui);

        // Prompt for input area selection (no global carrierin specialization exists)
        String insaId = context.createArea("Please, select input area", Resource.loadsimg("baubles/inputArea"));
        NArea inarea = context.getAreaById(insaId);

        if (inarea == null)
        {
            return Results.ERROR("No input area selected.");
        }

        // Find global CarrierOut area for output
        NArea.Specialisation carrierOutSpec = new NArea.Specialisation(Specialisation.SpecName.carrierout.toString());
        NArea carrierOutArea = NContext.findSpecGlobal(carrierOutSpec);

        if (carrierOutArea == null)
        {
            return Results.ERROR("No global CarrierOut zone found. Please create a CarrierOut zone first.");
        }

        // Navigate to input area using chunk navigation
        NUtils.navigateToArea(inarea);

        ArrayList<Gob> items;
        while (!(items = Finder.findGobs(inarea, new NAlias(prop.object))).isEmpty())
        {
            ArrayList<Gob> availableItems = new ArrayList<>();
            for (Gob currGob : items)
            {
                if (PathFinder.isAvailable(currGob))
                    availableItems.add(currGob);
            }
            if (availableItems.isEmpty())
            {
                NUtils.getGameUI().msg("Can't reach any " + prop.object + " in current area, skipping...");
                break;
            }

            availableItems.sort(NUtils.d_comp);
            Gob item = availableItems.get(0);

            // Lift the item
            new LiftObject(item).run(gui);

            // Navigate to output area using chunk navigation with lifted item
            // ВАЖНО: С поднятым предметом PathFinder не работает вне экрана, поэтому используем специальную навигацию
            navigateToAreaWithLiftedItem(carrierOutArea, gui);
            
            // Move to output area and place the item
            // ВАЖНО: Передаем NArea, чтобы FindPlaceAndAction мог навигировать к зоне, если она не видна
            new FindPlaceAndAction(null, carrierOutArea).run(gui);

            // Move away from the placed item
            Coord2d shift = item.rc.sub(NUtils.player().rc).norm().mul(2);
            new GoTo(NUtils.player().rc.sub(shift)).run(gui);
            
            // Navigate back to input area using chunk navigation
            NUtils.navigateToArea(inarea);
        }

        return Results.SUCCESS();
    }

    /**
     * Навигация к зоне с поднятым предметом.
     * Использует ChunkNav для навигации между чанками с правильным обходом препятствий.
     * Для финального подхода к зоне использует прямой клик, так как PathFinder не работает с поднятыми предметами.
     */
    private void navigateToAreaWithLiftedItem(NArea area, NGameUI gui) throws InterruptedException {
        if (area == null) return;
        
        // Проверяем, находимся ли мы уже в зоне
        Gob player = gui.map.player();
        if (player != null && area.checkHit(player.rc)) {
            return;
        }
        
        // Используем ChunkNavManager для навигации с правильным обходом препятствий
        ChunkNavManager chunkNav = null;
        if (gui.map instanceof NMapView) {
            chunkNav = ((NMapView) gui.map).getChunkNavManager();
        }
        
        if (chunkNav != null && chunkNav.isInitialized()) {
            // Планируем путь к зоне через ChunkNav (правильно обходит препятствия)
            ChunkPath path = AreaNavigationHelper.findShortestPathToAreaCorners(area, chunkNav);
            
            if (path != null && !path.isEmpty()) {
                // Навигируем по waypoints через ChunkNavExecutor - он правильно обходит препятствия
                // Но используем его только для навигации между чанками, не для финального подхода
                ChunkNavExecutor executor = new ChunkNavExecutor(path, area, chunkNav);
                
                // Запускаем навигацию - она обойдет препятствия через ChunkNav
                // Если PathFinder не сможет подойти близко из-за поднятого предмета, это нормально
                executor.run(gui);
            }
            
            // Всегда используем прямой клик для финального подхода к зоне,
            // так как PathFinder не работает с поднятыми предметами вне экрана
            player = gui.map.player();
            if (player != null && !area.checkHit(player.rc)) {
                Coord2d areaCenter = area.getCenter2d();
                if (areaCenter != null) {
                    // Используем прямой клик на карту для финального подхода
                    gui.map.wdgmsg("click", Coord.z, areaCenter.floor(haven.OCache.posres), 1, 0);
                    // Ждем, пока достигнем зоны
                    int attempts = 0;
                    while (attempts < 30 && (player == null || !area.checkHit(player.rc))) {
                        Thread.sleep(200);
                        player = gui.map.player();
                        if (player != null && player.rc.dist(areaCenter) < MCache.tilesz.x * 5) {
                            break; // Достаточно близко
                        }
                        attempts++;
                    }
                }
            }
        } else {
            // ChunkNav не доступен - используем прямой клик как fallback
            Coord2d areaCenter = area.getCenter2d();
            if (areaCenter != null) {
                gui.map.wdgmsg("click", Coord.z, areaCenter.floor(haven.OCache.posres), 1, 0);
                int attempts = 0;
                while (attempts < 30 && (player == null || !area.checkHit(player.rc))) {
                    Thread.sleep(200);
                    player = gui.map.player();
                    if (player != null && player.rc.dist(areaCenter) < MCache.tilesz.x * 5) {
                        break;
                    }
                    attempts++;
                }
            }
        }
    }
}
