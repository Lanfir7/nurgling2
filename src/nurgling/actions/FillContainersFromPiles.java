package nurgling.actions;

import haven.*;
import haven.res.ui.barterbox.Shopbox;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.areas.NArea;
import nurgling.tasks.WaitItems;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;

import java.util.ArrayList;
import java.util.HashSet;

public class FillContainersFromPiles implements Action {
    ArrayList<Container> conts;
    NAlias transferedItems;
    Pair<Coord2d,Coord2d> area;
    NArea nArea; // Добавляем поддержку NArea для поиска бартер стендов
    Coord targetCoord = new Coord(1, 1);
    boolean tetris = false;
    boolean tetris_done = true;


    public FillContainersFromPiles(ArrayList<Container> conts, Pair<Coord2d,Coord2d> area, NAlias transferedItems) {
        this.conts = conts;
        this.area = area;
        this.transferedItems = transferedItems;
        this.nArea = null;
    }

    // Новый конструктор с поддержкой NArea для поиска бартер стендов
    public FillContainersFromPiles(ArrayList<Container> conts, Pair<Coord2d,Coord2d> area, NAlias transferedItems, NArea nArea) {
        this.conts = conts;
        this.area = area;
        this.transferedItems = transferedItems;
        this.nArea = nArea;
    }


    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        ArrayList<Container> containers;

        while (!(containers = allDone(conts)).isEmpty())
        {
            ArrayList<WItem> oldItems = gui.getInventory().getItems(transferedItems);
            for (Container cont : containers) {
                Container.Space space = cont.getattr(Container.Space.class);
                while ((Integer) space.getRes().get(Container.Space.FREESPACE) != 0) {
                    if (gui.getInventory().getItems(transferedItems).isEmpty()) {
                        int target_size = 0;
                        if (targetCoord.equals(1, 1)) {
                            for (Container tcont : conts) {
                                if (tcont.getattr(Container.Tetris.class) != null) {
                                    tetris = true;
                                    Container.Tetris tspace = tcont.getattr(Container.Tetris.class);
                                    tetris_done = tetris_done && (boolean) tspace.getRes().get(Container.Tetris.DONE);
                                } else {
                                    Container.Space tspace = tcont.getattr(Container.Space.class);
                                    target_size += (Integer) tspace.getRes().get(Container.Space.FREESPACE);
                                }
                            }
                        }


                        while (((tetris && !tetris_done) || target_size != 0) && NUtils.getGameUI().getInventory().getNumberFreeCoord(targetCoord) != 0) {
                            // Сначала проверяем наличие бартер стенда в зоне
                            Gob barterStand = null;
                            Gob barterChest = null;
                            if (nArea != null) {
                                barterStand = Finder.findGob(nArea, new NAlias("gfx/terobjs/barterstand"));
                                if (barterStand != null) {
                                    barterChest = Finder.findGob(nArea, new NAlias("gfx/terobjs/chest"));
                                }
                            }
                            
                            // Если есть бартер стенд, используем его
                            if (barterStand != null && barterChest != null) {
                                // Получаем первый ключ из NAlias (обычно это название предмета)
                                String itemName = transferedItems.keys != null && !transferedItems.keys.isEmpty() 
                                    ? transferedItems.keys.get(0) : "";
                                
                                // Определяем размер предмета для расчета вместимости инвентаря
                                // Доска: 4x1 (4 по вертикали, 1 по горизонтали)
                                // Блок: 1x2 (1 по вертикали, 2 по горизонтали)
                                Coord itemSize;
                                if("Board".equals(itemName)) {
                                    itemSize = new Coord(4, 1); // 4 по вертикали, 1 по горизонтали
                                } else if("Block".equals(itemName) || "Block of Wood".equals(itemName)) {
                                    itemSize = new Coord(1, 2); // 1 по вертикали, 2 по горизонтали
                                } else {
                                    // Для других предметов используем стандартный размер или рассчитываем по target_size
                                    itemSize = new Coord(1, 1);
                                }
                                
                                // Берем ветки из сундука для покупки
                                new PathFinder(barterChest).run(gui);
                                new OpenTargetContainer("Chest", barterChest).run(gui);
                                ArrayList<WItem> branchItems = gui.getInventory("Chest").getItems("Branch");
                                int branchCount = branchItems.size();
                                
                                // Рассчитываем сколько предметов поместится в инвентарь ДО взятия веток
                                int itemsThatFitBeforeBranches = gui.getInventory().calcNumberFreeCoord(itemSize);
                                if (itemsThatFitBeforeBranches < 0) itemsThatFitBeforeBranches = 0;
                                
                                // Для tetris или обычных контейнеров ограничиваем по target_size
                                int maxItemsNeeded = tetris ? itemsThatFitBeforeBranches : Math.min(target_size, itemsThatFitBeforeBranches);
                                
                                // Ограничиваем количество веток тем, что нужно и что доступно
                                int branchesToTake = Math.min(maxItemsNeeded, branchCount);
                                
                                if (branchesToTake > 0 && !branchItems.isEmpty()) {
                                    ArrayList<WItem> itemsToTake = new ArrayList<>();
                                    for(int i = 0; i < Math.min(branchesToTake, branchItems.size()); i++) {
                                        itemsToTake.add(branchItems.get(i));
                                    }
                                    new SimpleTransferToContainer(gui.getInventory(), itemsToTake, branchesToTake).run(gui);
                                }
                                new CloseTargetWindow(NUtils.getGameUI().getWindow("Chest")).run(gui);
                                
                                // ПЕРЕСЧИТЫВАЕМ сколько предметов поместится ПОСЛЕ взятия веток
                                int itemsThatFitAfterBranches = gui.getInventory().calcNumberFreeCoord(itemSize);
                                if (itemsThatFitAfterBranches < 0) itemsThatFitAfterBranches = 0;
                                
                                // Для tetris или обычных контейнеров ограничиваем по target_size
                                int to_take = tetris ? itemsThatFitAfterBranches : Math.min(target_size, itemsThatFitAfterBranches);
                                
                                // Покупаем предметы в бартер стенде
                                new PathFinder(barterStand).run(gui);
                                new OpenTargetContainer("Barter Stand", barterStand).run(gui);
                                
                                Window barter_wnd = gui.getWindow("Barter Stand");
                                if(barter_wnd == null) {
                                    new CloseTargetWindow(NUtils.getGameUI().getWindow("Barter Stand")).run(gui);
                                    // Если не удалось открыть бартер, переходим к стокпайлам
                                } else {
                                    // Получаем список имен предметов для категорий через VSpec
                                    HashSet<String> itemNames = new HashSet<>();
                                    if("Board".equals(itemName)) {
                                        // Для категории Board получаем все типы досок из VSpec
                                        ArrayList<org.json.JSONObject> categoryItems = nurgling.tools.VSpec.categories.get("Board");
                                        if(categoryItems != null) {
                                            for(org.json.JSONObject categoryItem : categoryItems) {
                                                itemNames.add(categoryItem.getString("name"));
                                            }
                                        }
                                        // Если категория пуста, добавляем просто "Board"
                                        if(itemNames.isEmpty()) {
                                            itemNames.add("Board");
                                        }
                                    } else if("Block".equals(itemName) || "Block of Wood".equals(itemName)) {
                                        // Для категории Block/Block of Wood получаем все типы блоков из VSpec
                                        // В VSpec категория называется "Block of Wood"
                                        ArrayList<org.json.JSONObject> categoryItems = nurgling.tools.VSpec.categories.get("Block of Wood");
                                        if(categoryItems != null) {
                                            for(org.json.JSONObject categoryItem : categoryItems) {
                                                itemNames.add(categoryItem.getString("name"));
                                            }
                                        }
                                        // Если категория пуста, добавляем оба варианта названия
                                        if(itemNames.isEmpty()) {
                                            itemNames.add("Block");
                                            itemNames.add("Block of Wood");
                                        }
                                    } else {
                                        itemNames.add(itemName);
                                    }
                                    
                                    // Ищем нужный товар в бартер стенде
                                    boolean found = false;
                                    for(Widget ch = barter_wnd.child; ch != null; ch = ch.next) {
                                        if (ch instanceof Shopbox) {
                                            Shopbox sb = (Shopbox) ch;
                                            Shopbox.ShopItem offer = sb.getOffer();
                                            if (offer != null && itemNames.contains(offer.name)) {
                                                // Ограничиваем количество покупок доступными ветками и тем, что поместится ПОСЛЕ взятия веток
                                                int itemsToBuy = Math.min(branchesToTake, to_take);
                                                if (itemsToBuy > 0) {
                                                    for (int i = 0; i < itemsToBuy; i++) {
                                                        sb.wdgmsg("buy", new Object[0]);
                                                    }
                                                    
                                                    // Ждем появления предметов в инвентаре
                                                    if("Board".equals(itemName) || "Block".equals(itemName) || "Block of Wood".equals(itemName)) {
                                                        ArrayList<String> categoryItemNames = new ArrayList<>(itemNames);
                                                        NUtils.getUI().core.addTask(new WaitItems(NUtils.getGameUI().getInventory(), new NAlias(categoryItemNames, new ArrayList<>()), itemsToBuy));
                                                    } else {
                                                        NUtils.getUI().core.addTask(new WaitItems(NUtils.getGameUI().getInventory(), new NAlias(itemName), itemsToBuy));
                                                    }
                                                    found = true;
                                                    if (!tetris) {
                                                        target_size = Math.max(0, target_size - itemsToBuy);
                                                    }
                                                }
                                                break;
                                            }
                                        }
                                    }
                                    new CloseTargetWindow(NUtils.getGameUI().getWindow("Barter Stand")).run(gui);
                                    
                                    if (!found) {
                                        // Если не нашли нужный товар в бартере, переходим к стокпайлам
                                        barterStand = null;
                                    } else {
                                        // Успешно купили из бартера, продолжаем цикл
                                        continue;
                                    }
                                }
                            }
                            
                            // Если бартер стенда нет или не удалось использовать, используем стокпайлы
                            ArrayList<Gob> piles = Finder.findGobs(area, new NAlias("stockpile"));
                            if (piles.isEmpty()) {
                                if (gui.getInventory().getItems(transferedItems).isEmpty())
                                    return Results.ERROR("no items");
                                else
                                    break;
                            }
                            piles.sort(NUtils.d_comp);

                            Gob pile = piles.get(0);
                            new PathFinder(pile).run(gui);
                            new OpenTargetContainer("Stockpile", pile).run(gui);
                            if (tetris) {
                                TakeItemsByTetris tifp;
                                (tifp = new TakeItemsByTetris(pile, gui.getStockpile(), conts)).run(gui);
                                new CloseTargetWindow(NUtils.getGameUI().getWindow("Stockpile")).run(gui);
                                tetris_done = tifp.isDone();
                            } else {
                                TakeItemsFromPile tifp;
                                (tifp = new TakeItemsFromPile(pile, gui.getStockpile(), Math.min(target_size, gui.getInventory().getFreeSpace()))).run(gui);
                                new CloseTargetWindow(NUtils.getGameUI().getWindow("Stockpile")).run(gui);
                                target_size = target_size - tifp.getResult();
                            }

                        }
                    }
                    if (tetris) {
                        Container.Tetris tetr;
                        if ((tetr = cont.getattr(Container.Tetris.class)) != null) {
                            ArrayList<WItem> witems = gui.getInventory().getItems(transferedItems);
                            boolean hole = false;
                            for (WItem witem : witems)
                                if (tetr.calcNumberFreeCoord(Container.Tetris.SRC, witem.item.spr.sz().div(UI.scale(32)).swapXY()) > 0) {
                                    hole = true;
                                    break;
                                }
                            if (!hole) {
                                break;
                            }
                        }
                    }
                    new TransferToContainer(cont, transferedItems).run(gui);
                }
                new CloseTargetContainer(cont).run(gui);
            }
            if(!oldItems.isEmpty()) {
                if (NUtils.getGameUI().getInventory().getItems(transferedItems).containsAll(oldItems)) {
                    break;
                }
            }
        }
        return Results.SUCCESS();
    }

    ArrayList<Container> allDone(ArrayList<Container> containers) throws InterruptedException {
        ArrayList<Container> result = new ArrayList<>();
        for (Container cont : containers) {
            Container.Tetris tetris;
            if((tetris = cont.getattr(Container.Tetris.class)) != null) {
               if(!(Boolean)tetris.getRes().get(Container.Tetris.DONE)) {
                   result.add(cont);
               }
            }
            else
            {
                Container.Space space = cont.getattr(Container.Space.class);
                if(space != null) {
                   if((Integer)space.getRes().get(Container.Space.FREESPACE) != 0) {
                       result.add(cont);
                   }
                }
            }
        }
        return result;
    }
}
