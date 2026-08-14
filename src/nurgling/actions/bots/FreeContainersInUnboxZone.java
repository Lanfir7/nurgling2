package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tools.*;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;
import java.util.HashSet;

public class FreeContainersInUnboxZone implements Action {
//    RoutePoint closestRoutePoint = null;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {

        // Find the area with "unbox" specialization
        NContext context = new NContext(gui);
        NArea unboxArea = context.goToArea(Specialisation.SpecName.unbox);

        if (unboxArea == null) {
            NUtils.getGameUI().error("No unbox zone area found!");
            return Results.ERROR("No unbox zone area found");
        }

        Pair<Coord2d,Coord2d> area = unboxArea.getRCArea();
        ArrayList<Container> containers = new ArrayList<>();

        if(area!=null) {
            // Free containers in the area
            for (Gob sm : Finder.findGobs(area, new NAlias(new ArrayList<>(NContext.contcaps.keySet())))) {
                Container cand = new Container(sm, NContext.contcaps.get(sm.ngob.name), unboxArea);
                cand.initattr(Container.Space.class);
                containers.add(cand);
            }
            if (!containers.isEmpty())
                new FreeContainers(containers).run(gui);
        }

        ArrayList<Gob> gobs;
        HashSet<String> targets = new HashSet<>();
        HashSet<Long> processedPiles = new HashSet<>(); // Защита от бесконечного цикла
        while(!(gobs = Finder.findGobs(area, new NAlias("stockpile"))).isEmpty())
        {
            boolean anyProcessed = false;
            for (Gob pile : gobs) {
                // Пропускаем уже обработанные или недоступные pile
                if (processedPiles.contains(pile.id)) {
                    continue;
                }
                if(PathFinder.isAvailable(pile)) {
                    anyProcessed = true;
                    Coord size = StockpileUtils.itemMaxSize.get(pile.ngob.name);
                    new PathFinder(pile).run(gui);
                    new OpenTargetContainer("Stockpile",pile).run(gui);
                    int target_size = 0;
                    while (Finder.findGob(pile.id) != null)
                        if ( NUtils.getGameUI().getInventory().getNumberFreeCoord((size != null) ?size:new Coord(1,1)) > 0) {
                            NISBox spbox = gui.getStockpile();
                            if (spbox != null) {
                                do {
                                    if (Finder.findGob(pile.id) == null&&target_size!=0) {
                                        break;
                                    }
                                    target_size = NUtils.getGameUI().getInventory().getNumberFreeCoord((size != null) ?size:new Coord(1,1));
                                    if (target_size == 0) {
                                        new FreeInventory2(context).run(gui);
                                        if(Finder.findGob(pile.id)==null && (Boolean) NConfig.get(NConfig.Key.useGlobalPf)) {
                                            context.goToArea(Specialisation.SpecName.unbox);
//                                            new RoutePointNavigator(this.closestRoutePoint).run(NUtils.getGameUI());
                                        }
                                        targets.clear();
                                        // Проверяем что pile существует И доступен (можно построить путь)
                                        if (Finder.findGob(pile.id) != null && PathFinder.isAvailable(pile)) {
                                            new PathFinder(pile).run(gui);
                                            new OpenTargetContainer("Stockpile", pile).run(gui);
                                        } else break;
                                    } else {
                                        TakeItemsFromPile tifp = new TakeItemsFromPile(pile, spbox, target_size);
                                        tifp.run(gui);
                                        for (NGItem item : tifp.newItems())
                                            targets.add((item).name());
                                    }
                                }
                                while (target_size!=0);
                            } else {
                                // Stockpile закрылся или закончился - пробуем открыть снова
                                if (Finder.findGob(pile.id) != null && PathFinder.isAvailable(pile)) {
                                    new OpenTargetContainer("Stockpile", pile).run(gui);
                                    // Если всё ещё не открылся - выходим (pile пустой)
                                    if (gui.getStockpile() == null) {
                                        break;
                                    }
                                } else {
                                    break; // pile недоступен
                                }
                            }
                        }
                    else
                        {
                            new FreeInventory2(context).run(gui);
                            if(Finder.findGob(pile.id) == null && (Boolean) NConfig.get(NConfig.Key.useGlobalPf)) {
                                context.goToArea(Specialisation.SpecName.unbox);
//                                new RoutePointNavigator(this.closestRoutePoint).run(NUtils.getGameUI());
                            }
                            // Проверяем что pile существует И доступен (можно построить путь)
                            if(Finder.findGob(pile.id) != null && PathFinder.isAvailable(pile)) {
                                new PathFinder(pile).run(gui);
                                new OpenTargetContainer("Stockpile", pile).run(gui);
                            } else {
                                break; // pile недоступен - выходим
                            }
                        }
                    // Помечаем pile как обработанный
                    processedPiles.add(pile.id);
                } else {
                    // Pile недоступен (возможно на другом этаже) - помечаем как обработанный чтобы не зависнуть
                    processedPiles.add(pile.id);
                }
            }
            // Если ни один pile не был обработан - выходим из цикла
            if (!anyProcessed) {
                break;
            }
        }
        gui.msg("FINAL TRANSFER!");
        new FreeInventory2(context).run(gui);
        gui.msg("FINAL TRANSFER END!");
        return Results.SUCCESS();
    }
}
