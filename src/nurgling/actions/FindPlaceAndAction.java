package nurgling.actions;

import haven.*;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.areas.NArea;
import nurgling.pf.CellsArray;
import nurgling.pf.NPFMap;
import nurgling.pf.Utils;
import nurgling.tools.Finder;

import static nurgling.tools.Finder.findLiftedbyPlayer;

public class FindPlaceAndAction implements Action {
    boolean dynamicPf = false;
    public FindPlaceAndAction(Gob gob, Pair<Coord2d, Coord2d> rcArea) {
        this.placed = gob;
        this.area = rcArea;
    }

    @Override
    public Results run ( NGameUI gui )
            throws InterruptedException {
        if(placed == null)
            placed = findLiftedbyPlayer();
        if ( placed != null ) {
            // ВАЖНО: Если зона не видна, навигируем к ней через ChunkNav перед использованием PathFinder
            // PathFinder не может найти путь к координатам в невидимых зонах (grid не загружен)
            if (targetArea != null && !targetArea.isVisible()) {
                // Навигируем к зоне через ChunkNav
                NUtils.navigateToArea(targetArea);
                // После навигации зона должна стать видимой, получаем координаты
                area = targetArea.getRCArea();
            }
            
            // Если area все еще null, пытаемся получить из сохраненных данных
            if (area == null && targetArea != null) {
                area = targetArea.getRCAreaFromStoredData();
            }
            
            if (area == null) {
                return Results.ERROR("Area coordinates not available");
            }
            
            Coord2d pos = Finder.getFreePlace(area, placed);
            if(pos!=null) {

                new PlaceObject(placed, pos,0, dynamicPf).run(gui);
                return Results.SUCCESS();
            }
            else
                return Results.ERROR("No free place");

        }
        return Results.ERROR("No gob for place");
    }



    public FindPlaceAndAction(
            Gob gob,
            NArea area)
    {
        this.placed = gob;
        this.area = area.getRCArea();
        this.targetArea = area;
    }

    public FindPlaceAndAction(
            Gob gob,
            NArea area,
            boolean dynamicPf)
    {
        this.placed = gob;
        this.area = area.getRCArea();
        this.dynamicPf = dynamicPf;
        this.targetArea = area;
    }

    public Gob getPlaced() {
        return placed;
    }

    Gob placed = null;
    Pair<Coord2d, Coord2d> area = null;
    NArea targetArea = null;
}