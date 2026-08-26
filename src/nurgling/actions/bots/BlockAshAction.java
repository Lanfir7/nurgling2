package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.Pair;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tasks.WaitForBurnout;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;
import java.util.HashSet;

public class BlockAshAction implements Action {

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        NArea.Specialisation rBoneForAsh = new NArea.Specialisation(Specialisation.SpecName.blockforash.toString());
        NArea.Specialisation rkilns = new NArea.Specialisation(Specialisation.SpecName.kiln.toString());

        ArrayList<NArea.Specialisation> req = new ArrayList<>();
        req.add(rkilns);
        req.add(rBoneForAsh);
        ArrayList<NArea.Specialisation> opt = new ArrayList<>();
        if(new Validator(req, opt).run(gui).IsSuccess()) {

            NArea blockForAshArea = NContext.findSpec(Specialisation.SpecName.blockforash.toString());
            if(blockForAshArea == null)
            {
                return Results.ERROR("Input area not found");
            }
            
            Pair<Coord2d,Coord2d> rca = blockForAshArea.getRCArea();
            if(rca==null)
            {
                return Results.ERROR("Input area not found");
            }

            // Определяем тип предмета: доски или блоки
            // Проверяем что находится в зоне через getInput
            boolean isBoard = false;
            
            // Проверяем есть ли в зоне доски (категория "Board")
            NArea.Ingredient boardIngredient = blockForAshArea.getInput("Board");
            if(boardIngredient != null) {
                isBoard = true;
            } else {
                // Проверяем есть ли в зоне блоки (категория "Block of Wood")
                NArea.Ingredient blockIngredient = blockForAshArea.getInput("Block of Wood");
                if(blockIngredient == null) {
                    // Если не найдены ни доски ни блоки через категории, проверяем stockpile как fallback
                    Gob testGob;
                    if((testGob = Finder.findGob(rca,new NAlias("stockpile")))!=null)
                    {
                       isBoard = testGob.ngob.name.contains("board");
                    }
                }
            }
            
            // Отладочное сообщение
            NUtils.getGameUI().msg("BlockAshAction: determined isBoard=" + isBoard + " (boardIngredient=" + (boardIngredient != null) + ")");

            ArrayList<Container> containers = new ArrayList<>();

            NArea kilnsarea = NContext.findSpec(rkilns.name);
            for (Gob kiln : Finder.findGobs(kilnsarea,
                    new NAlias("gfx/terobjs/kiln"))) {
                Container cand = new Container(kiln, "Kiln", kilnsarea);
                cand.initattr(Container.Space.class);
                cand.initattr(Container.FuelLvl.class);
                cand.getattr(Container.FuelLvl.class).setMaxlvl(isBoard ? 3 : 8);
                cand.getattr(Container.FuelLvl.class).setFueltype("Branch");
                cand.initattr(Container.Tetris.class);
                Container.Tetris tetris = cand.getattr(Container.Tetris.class);
                ArrayList<Coord> coords = new ArrayList<>();
                if(isBoard)
                {
                    coords.add(new Coord(4, 1));
                }
                else {
                    coords.add(new Coord(1, 2));
                }

                tetris.getRes().put(Container.Tetris.TARGET_COORD, coords);
                containers.add(cand);
            }

            ArrayList<String> flighted = new ArrayList<>();
            for (Container cont : containers) {
                flighted.add(cont.gobHash);
            }

            // Создаем контекст для работы с системой TAKE/PUT
            NContext context = new NContext(gui);
            // Используем правильное имя категории для блоков
            String itemName = isBoard ? "Board" : "Block of Wood";
            
            // Регистрируем предмет в системе TAKE/PUT если еще не зарегистрирован
            // Это позволит системе найти зоны с категориями
            try {
                context.addInItem(itemName, null);
            } catch (InterruptedException e) {
                // Игнорируем, если уже зарегистрирован
            }
            
            Results res = null;
            while (res == null || res.IsSuccess()) {
                NUtils.getUI().core.addTask(new WaitForBurnout(flighted, 1));
                // Освобождаем контейнеры, но ИГНОРИРУЕМ доски/блоки (они должны остаться в килнах)
                // Создаем паттерн который исключает доски и блоки
                HashSet<String> boardAndBlockNames = new HashSet<>();
                if(isBoard) {
                    // Для досок - добавляем все типы досок из категории
                    ArrayList<org.json.JSONObject> boardItems = nurgling.tools.VSpec.categories.get("Board");
                    if(boardItems != null) {
                        for(org.json.JSONObject boardItem : boardItems) {
                            boardAndBlockNames.add(boardItem.getString("name"));
                        }
                    }
                } else {
                    // Для блоков - добавляем все типы блоков из категории
                    ArrayList<org.json.JSONObject> blockItems = nurgling.tools.VSpec.categories.get("Block of Wood");
                    if(blockItems != null) {
                        for(org.json.JSONObject blockItem : blockItems) {
                            boardAndBlockNames.add(blockItem.getString("name"));
                        }
                    }
                }
                // Используем FreeContainers с фильтрацией - освобождаем все КРОМЕ досок/блоков
                new FreeContainersIgnoreItems(containers, boardAndBlockNames).run(gui);
                
                // Используем систему TAKE/PUT через TakeItems2 для взятия досок/блоков
                // Это автоматически найдет зоны с категориями (унифицированные доски)
                haven.Coord itemSize = isBoard ? new haven.Coord(4, 1) : new haven.Coord(1, 2);
                
                // Рассчитываем сколько досок/блоков поместится в инвентарь
                // ВАЖНО: берем МАКСИМУМ того что поместится, а не сумму по всем контейнерам
                int totalNeeded = gui.getInventory().calcNumberFreeCoord(itemSize);
                if (totalNeeded < 0) totalNeeded = 0;
                
                // Отладочное сообщение
                NUtils.getGameUI().msg("BlockAshAction: isBoard=" + isBoard + ", itemSize=" + itemSize + ", totalNeeded=" + totalNeeded);
                
                // Если ничего не поместится, берем минимум 1
                if (totalNeeded == 0) {
                    totalNeeded = 1;
                }
                
                if (totalNeeded > 0) {
                    // Используем TakeItems2 который работает через систему TAKE/PUT
                    // и автоматически найдет зоны с категориями (унифицированные доски)
                    // TakeItems2 сам правильно рассчитает количество веток с учетом того что они займут место
                    Results takeResult = new TakeItems2(context, itemName, totalNeeded).run(gui);
                    if (!takeResult.IsSuccess()) {
                        // Если система TAKE/PUT не нашла зоны, используем старую систему через специализацию
                        res = new FillContainersFromPiles(containers, rca, isBoard?new NAlias("Board"):new NAlias("Block"), blockForAshArea).run(gui);
                    } else {
                        // Теперь заполняем контейнеры из инвентаря
                        res = new FillContainers(containers, itemName, context).run(gui);
                    }
                } else {
                    // Если ничего не нужно, просто заполняем контейнеры
                    res = new FillContainers(containers, itemName, context).run(gui);
                }

                ArrayList<Container> forFuel = new ArrayList<>();
                for (Container container : containers) {
                    Container.Space space = container.getattr(Container.Space.class);
                    if (!space.isEmpty())
                        forFuel.add(container);
                }
                Results fuelRes = new FuelToContainers(forFuel).run(gui);
                if (!fuelRes.IsSuccess())
                    return fuelRes;

                flighted.clear();
                for (Container cont : forFuel) {
                    flighted.add(cont.gobHash);
                }
                if (!new LightGob(flighted, 1).run(gui).IsSuccess())
                    return Results.ERROR("I can't start a fire");
            }
            return Results.SUCCESS();
        }
        return Results.FAIL();
    }
}
