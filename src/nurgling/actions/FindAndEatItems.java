package nurgling.actions;

import haven.*;
import nurgling.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.iteminfo.NFoodInfo;
import nurgling.tasks.WaitItems;
import nurgling.tasks.WaitWindow;
import nurgling.tools.Container;
import nurgling.tools.Context;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;

import java.util.ArrayList;

public class FindAndEatItems implements Action
{
    final NContext cnt;
    ArrayList<String> items;
    double level;
    Pair<Coord2d,Coord2d> area;
    NArea nArea;

    public FindAndEatItems(NContext context, ArrayList<String> items, int level, Pair<Coord2d,Coord2d> area)
    {
        this(context, items, level, area, null);
    }

    public FindAndEatItems(NContext context, ArrayList<String> items, int level, Pair<Coord2d,Coord2d> area, NArea nArea)
    {
        this.cnt = context;
        this.items = items;
        this.level = level;
        this.area = area;
        this.nArea = nArea;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        if (nArea != null)
            NUtils.navigateToArea(nArea);
        Coord2d center = area.a.add(area.b).div(2);
        new PathFinder(center).run(gui);

        NAlias foodAlias = new NAlias(items);
        while (needFood()) {
            eatAll(gui);
            if (!needFood()) break;

            boolean fed = false;
            ArrayList<Gob> tables = Finder.findGobs(area, new NAlias("gfx/terobjs/furn/table-elegant", "gfx/terobjs/furn/table-stone", "gfx/terobjs/furn/table-rustic", "gfx/terobjs/furn/cottagetable"));
            tables.sort(NUtils.d_comp);
            for (Gob table : tables) {
                if (!needFood()) break;
                new PathFinder(table).run(gui);
                for (int retry = 0; retry < 2 && !fed; retry++) {
                    new OpenTargetContainer("Table", table).run(gui);
                    NUtils.addTask(new WaitWindow("Table"));
                    NInventory tableInv = gui.getInventory("Table");
                    if (tableInv != null) {
                        waitTableFood(tableInv, foodAlias); // ждём прогрузки предметов
                        Thread.sleep(400);
                        fed = eatFromTable(gui, tableInv, foodAlias);
                    }
                    new CloseTargetContainer("Table").run(gui);
                    if (fed) break;
                }
                if (fed) break;
            }

            if (!fed) {
                ArrayList<Gob> piles = Finder.findGobs(area, new NAlias("stockpile"));
                piles.sort(NUtils.d_comp);
                for (Gob pile : piles) {
                    if (!needFood()) break;
                    new PathFinder(pile).run(gui);
                    new OpenTargetContainer("Stockpile", pile).run(gui);
                    NUtils.addTask(new WaitStockpile(true));
                    NISBox sp = gui.getStockpile();
                    if (sp != null && sp.calcCount() > 0 && gui.getInventory().getNumberFreeCoord(new Coord(1, 1)) > 0) {
                        int toTake = calcPiecesToTake(gui, sp, foodAlias);
                        if (toTake > 0) {
                            new TakeItemsFromPile(pile, sp, toTake).run(gui);
                            fed = true;
                        }
                    }
                    new CloseTargetWindow(gui.getWindow("Stockpile")).run(gui);
                    if (fed) break;
                }
            }
            if (!fed) break;
        }
        return Results.SUCCESS();
    }

    /** Ждёт появления еды на столе (прогрузка), до ~8 сек. */
    void waitTableFood(NInventory tableInv, NAlias foodAlias) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            if (!tableInv.getItems(foodAlias).isEmpty()) return;
            Thread.sleep(200);
        }
    }

    /** Едим прямо со стола, без переноса в инвентарь. */
    boolean eatFromTable(NGameUI gui, NInventory tableInv, NAlias foodAlias) throws InterruptedException {
        double need = level - NUtils.getEnergy() * 10000;
        if (need <= 0) return false;
        int eaten = 0;
        while (need > 0) {
            ArrayList<WItem> foodItems = tableInv.getItems(foodAlias);
            if (foodItems.isEmpty()) break;
            WItem item = foodItems.get(0);
            NFoodInfo fi = item.item instanceof NGItem ? ((NGItem) item.item).getInfo(NFoodInfo.class) : null;
            if (fi == null) break;
            double pieceEnergy = fi.end * 100;
            Results r = new SelectFlowerAction("Eat", item).run(gui);
            if (r.IsSuccess()) {
                need -= pieceEnergy;
                eaten++;
                Thread.sleep(250);
            } else break;
        }
        return eaten > 0;
    }

    /** Сколько кусков взять с пайла (до энергии level). */
    int calcPiecesToTake(NGameUI gui, NISBox sp, NAlias foodAlias) throws InterruptedException {
        double need = level - NUtils.getEnergy() * 10000;
        if (need <= 0) return 0;
        double energyPerPiece = getEnergyPerPiece(gui.getInventory().getItems(foodAlias));
        if (energyPerPiece <= 0) energyPerPiece = 800; // fallback
        int toTake = (int) Math.ceil(need / energyPerPiece);
        return Math.min(toTake, sp.calcCount());
    }

    double getEnergyPerPiece(ArrayList<WItem> items) {
        if (items.isEmpty()) return 0;
        NFoodInfo fi = items.get(0).item instanceof NGItem ? ((NGItem) items.get(0).item).getInfo(NFoodInfo.class) : null;
        return fi != null ? fi.end * 100 : 0;
    }

    public Results takeFromPile(NGameUI gui, Context.InputPile pile) throws InterruptedException
    {
        new PathFinder(pile.pile).run(gui);
        new OpenTargetContainer("Stockpile",  pile.pile).run(gui);
        while (needFood()) {
            if(gui.getInventory().getNumberFreeCoord(new Coord(1,1))==0)
            {
                eatAll(gui);
            }
            TakeItemsFromPile tifp;
            (tifp = new TakeItemsFromPile(pile.pile, gui.getStockpile(), 1)).run(gui);
            if(tifp.getResult() == 0)
                break;
        }
        new CloseTargetWindow(NUtils.getGameUI().getWindow("Stockpile")).run(gui);
        return Results.SUCCESS();
    }

    public Results takeFromContainer(NGameUI gui, Container cont) throws InterruptedException
    {
        new PathFinder(Finder.findGob(cont.gobid)).run(gui);
        new OpenTargetContainer(cont).run(gui);
        while (needFood()) {
            if(gui.getInventory().getNumberFreeCoord(new Coord(1,1))==0)
            {
                eatAll(gui);
            }
            WItem taritem = NUtils.getGameUI().getInventory(cont.cap).getItem(new NAlias(items));
            int oldSize = NUtils.getGameUI().getInventory().getItems(new NAlias(items)).size();
            if( taritem == null )
                break;
            taritem.item.wdgmsg("transfer", Coord.z);
            gui.ui.core.addTask(new WaitItems(NUtils.getGameUI().getInventory(), new NAlias(items), oldSize + 1));
        }

        new CloseTargetWindow(NUtils.getGameUI().getWindow("Stockpile")).run(gui);
        return Results.SUCCESS();
    }

    /** Нужна ли еда: только по текущей энергии, без учёта еды в инвентаре. */
    boolean needFood() {
        double e = NUtils.getEnergy();
        return e >= 0 && e * 10000 < level;
    }

    void eatAll(NGameUI gui) throws InterruptedException {
        ArrayList<WItem> titems = NUtils.getGameUI().getInventory().getItems(new NAlias(items));

        for (WItem item : titems)
        {
            new SelectFlowerAction("Eat", (NWItem) item).run(gui);
        }
    }
}
