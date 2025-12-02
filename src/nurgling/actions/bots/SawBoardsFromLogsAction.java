package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.Pair;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.conf.NPrepBoardsProp;
import nurgling.tasks.WaitPose;
import nurgling.tasks.WaitPrepBoardsState;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;

import java.util.ArrayList;

/**
 * Action для пиления досок из бревен.
 * Используется как specialWay в Build.Ingredient для автоматического производства досок.
 * Build.java вызывает этот Action когда нужно пополнить инвентарь досками.
 * Action пилит доски строго до нужного количества (ingredient.count).
 */
public class SawBoardsFromLogsAction implements Action {
    private Pair<Coord2d, Coord2d> logsArea;
    private Build.Ingredient ingredient;
    private NPrepBoardsProp prop;
    
    public SawBoardsFromLogsAction(Pair<Coord2d, Coord2d> logsArea, Build.Ingredient ingredient, NPrepBoardsProp prop) {
        this.logsArea = logsArea;
        this.ingredient = ingredient;
        this.prop = prop;
    }
    
    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        // Проверяем сколько досок нужно (ingredient.count уже содержит сколько нужно собрать)
        // Build.java передает ingredient.count как (требуемое количество - уже имеющееся)
        int boardsNeeded = ingredient.count;
        
        if (boardsNeeded <= 0) {
            return Results.SUCCESS();
        }
        
        // Пилим доски строго до нужного количества
        ArrayList<Gob> logs;
        while (boardsNeeded > 0 && !(logs = Finder.findGobs(logsArea, new NAlias("log"))).isEmpty()) {
            logs.sort(NUtils.d_comp);
            Gob log = logs.get(0);
            
            while (Finder.findGob(log.id) != null && boardsNeeded > 0) {
                // Проверяем сколько досок нужно (ingredient.count может измениться)
                boardsNeeded = ingredient.count;
                if (boardsNeeded <= 0) {
                    break;
                }
                
                // Проверяем есть ли место в инвентаре для досок
                int freeSpace = NUtils.getGameUI().getInventory().calcNumberFreeCoord(ingredient.coord);
                if (freeSpace <= 0) {
                    // Нет места - это нормально, Build.java вызовет нас снова когда освободится место
                    return Results.SUCCESS();
                }
                
                new PathFinder(log).run(gui);
                new Equip(new NAlias(prop.tool)).run(gui);
                new SelectFlowerAction("Make boards", log).run(gui);
                NUtils.getUI().core.addTask(new WaitPose(NUtils.player(), "gfx/borka/sawing"));
                WaitPrepBoardsState wcs = new WaitPrepBoardsState(log, prop);
                NUtils.getUI().core.addTask(wcs);
                
                switch (wcs.getState()) {
                    case WORKING:
                        // Продолжаем пилить
                        break;
                    case LOGNOTFOUND:
                        break;
                    case TIMEFORDRINK: {
                        if (!(new Drink(0.9, true).run(gui)).IsSuccess())
                            return Results.ERROR("Drink is not found");
                        break;
                    }
                    case NOFREESPACE: {
                        // Инвентарь заполнен - это нормально, Build.java вызовет нас снова когда освободится место
                        return Results.SUCCESS();
                    }
                    case DANGER:
                        return Results.ERROR("SOMETHING WRONG, STOP WORKING");
                }
                
                // Проверяем сколько досок нужно после пиления
                // Build.java обновит ingredient.count после вызова этого метода
                boardsNeeded = ingredient.count;
            }
        }
        
        // Проверяем набрали ли мы нужное количество
        if (ingredient.count > 0) {
            // Не набрали нужное количество - Build.java вернет ошибку
            return Results.SUCCESS();
        }
        
        return Results.SUCCESS();
    }
}

