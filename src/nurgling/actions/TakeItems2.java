package nurgling.actions;

import haven.Gob;
import haven.WItem;
import haven.Widget;
import haven.Window;
import haven.res.ui.barterbox.Shopbox;
import nurgling.NGameUI;
import nurgling.NInventory.QualityType;
import nurgling.NUtils;
import nurgling.areas.NContext;
import nurgling.tasks.WaitItems;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

public class TakeItems2 implements Action
{
    final NContext cnt;
    String item;
    int count;
    Specialisation.SpecName specName;
    String specSubtype;
    QualityType qualityType;
    public boolean exactMatch = false;


    public TakeItems2(NContext context, String item, int count)
    {
        this.cnt = context;
        this.item = item;
        this.count = count;
        this.qualityType = null;
    }

    public TakeItems2(NContext context, String item, int count, Specialisation.SpecName specName)
    {
        this.cnt = context;
        this.item = item;
        this.count = count;
        this.specName = specName;
        this.qualityType = null;
    }

    public TakeItems2(NContext context, String item, int count, QualityType qualityType)
    {
        this.cnt = context;
        this.item = item;
        this.count = count;
        this.qualityType = qualityType;
    }

    public TakeItems2(NContext context, String item, int count, Specialisation.SpecName specName, QualityType qualityType)
    {
        this.cnt = context;
        this.item = item;
        this.count = count;
        this.specName = specName;
        this.qualityType = qualityType;
    }

    public TakeItems2(NContext context, String item, int count, Specialisation.SpecName specName, String specSubtype)
    {
        this.cnt = context;
        this.item = item;
        this.count = count;
        this.specName = specName;
        this.specSubtype = specSubtype;
        this.qualityType = QualityType.High;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        // Сначала проверяем наличие предметов в инвентаре (с учётом категорий)
        int itemsInInventory = 0;
        if(nurgling.tools.VSpec.categories.containsKey(item)) {
            ArrayList<org.json.JSONObject> categoryItems = nurgling.tools.VSpec.categories.get(item);
            if(categoryItems != null) {
                for(org.json.JSONObject categoryItem : categoryItems) {
                    String categoryItemName = categoryItem.getString("name");
                    itemsInInventory += NUtils.getGameUI().getInventory().getItems(new NAlias(categoryItemName)).size();
                }
            }
        } else {
            itemsInInventory = NUtils.getGameUI().getInventory().getItems(new NAlias(item)).size();
        }
        
        // Если предметов достаточно, сразу возвращаем SUCCESS
        if(itemsInInventory >= count) {
            return Results.SUCCESS();
        }
        
        AtomicInteger left = new AtomicInteger(count - itemsInInventory);
        ArrayList<NContext.ObjectStorage> inputs;
        if(specName == null) {
            // Для "Block" используем "Block of Wood" в системе TAKE/PUT
            String searchItem = "Block".equals(item) ? "Block of Wood" : item;
            inputs = cnt.getInStorages(searchItem);
        } else {
            inputs = cnt.getSpecStorages(this.specName, this.specSubtype);
        }

        if(inputs == null || inputs.isEmpty())
            return Results.FAIL();
            
        for(NContext.ObjectStorage input: inputs)
        {
            if(left.get() <= 0)
                break;
                
            if(input instanceof NContext.Barter)
                takeFromBarter(left,gui, (NContext.Barter)input);
            else if (input instanceof NContext.Pile)
            {
                takeFromPile(left, gui,(NContext.Pile) input);
            }
            else if (input instanceof Container)
            {
                takeFromContainer(left, gui, (Container) input);
            }
        }
        return Results.SUCCESS();
    }

    public Results takeFromBarter(AtomicInteger left, NGameUI gui, NContext.Barter barter) throws InterruptedException
    {
        Gob gchest = Finder.findGob(barter.chest);
        Gob gbarter = Finder.findGob(barter.barter);
        if(gbarter==null || gchest==null)
            return Results.FAIL();
        
        // Определяем размер предмета для расчета вместимости инвентаря
        // Доска: 4x1 (4 по вертикали, 1 по горизонтали)
        // Блок: 1x2 (1 по вертикали, 2 по горизонтали)
        haven.Coord itemSize;
        if("Board".equals(item)) {
            itemSize = new haven.Coord(4, 1); // 4 по вертикали, 1 по горизонтали
        } else if("Block".equals(item) || "Block of Wood".equals(item)) {
            itemSize = new haven.Coord(1, 2); // 1 по вертикали, 2 по горизонтали
        } else {
            // Для других предметов используем стандартный размер
            itemSize = new haven.Coord(1, 1);
        }
        
        // Рассчитываем сколько предметов поместится в ТЕКУЩИЙ инвентарь (ДО взятия веток)
        int itemsThatFitBeforeBranches = gui.getInventory().calcNumberFreeCoord(itemSize);
        if (itemsThatFitBeforeBranches < 0) itemsThatFitBeforeBranches = 0;
        
        // Ограничиваем количество тем, что нужно и тем, что поместится
        int needed = left.get();
        // Предварительная оценка - сколько досок мы хотим купить (максимум)
        int maxItemsToBuy = Math.min(needed, itemsThatFitBeforeBranches);
        
        // Отладочное сообщение
        NUtils.getGameUI().msg("TakeItems2.barter: item=" + item + ", needed=" + needed + ", itemsThatFitBeforeBranches=" + itemsThatFitBeforeBranches + ", maxItemsToBuy=" + maxItemsToBuy);
        
        new PathFinder(gchest).run(gui);
        new OpenTargetContainer("Chest", gchest).run(gui);
        
        // В сундуке ищем ветки (Branch) для покупки в бартере
        ArrayList<WItem> branchItems = gui.getInventory("Chest").getItems("Branch");
        int branchCount = branchItems.size();
        
        // Берем ветки из сундука (берем максимум, но не больше чем есть)
        int branchesToTake = Math.min(maxItemsToBuy, branchCount);
        
        // Отладочное сообщение
        NUtils.getGameUI().msg("TakeItems2.barter: branchCount=" + branchCount + ", branchesToTake=" + branchesToTake);
        
        // Берем ветки из сундука
        if(branchesToTake > 0 && !branchItems.isEmpty()) {
            ArrayList<WItem> itemsToTake = new ArrayList<>();
            for(int i = 0; i < Math.min(branchesToTake, branchItems.size()); i++) {
                itemsToTake.add(branchItems.get(i));
            }
            new SimpleTransferToContainer(gui.getInventory(), itemsToTake, branchesToTake).run(gui);
        }
        new CloseTargetWindow(NUtils.getGameUI().getWindow("Chest")).run(gui);
        
        // ВАЖНО: Пересчитываем сколько досок поместится ПОСЛЕ взятия веток
        int itemsThatFitAfterBranches = gui.getInventory().calcNumberFreeCoord(itemSize);
        if (itemsThatFitAfterBranches < 0) itemsThatFitAfterBranches = 0;
        
        // Финальное количество досок для покупки - минимум из нужного, помещающегося и имеющихся веток
        int itemsToBuy = Math.min(needed, Math.min(itemsThatFitAfterBranches, branchesToTake));
        
        // Отладочное сообщение
        NUtils.getGameUI().msg("TakeItems2.barter: itemsThatFitAfterBranches=" + itemsThatFitAfterBranches + ", final itemsToBuy=" + itemsToBuy);
        
        new PathFinder(gbarter).run(gui);
        
        // Используем OpenTargetContainer как в FillContainersFromPiles - он добавляет задачу FindBarterStand
        new OpenTargetContainer("Barter Stand", gbarter).run(gui);
        
        // Ждем пока окно откроется и загрузится (как в FillContainersFromPiles)
        Window barter_wnd = gui.getWindow("Barter Stand");
        if(barter_wnd == null) {
            // Если окно не открылось сразу, ждем немного
            int attempts = 0;
            while(attempts < 25 && barter_wnd == null) {
                Thread.sleep(200);
                barter_wnd = gui.getWindow("Barter Stand");
                attempts++;
            }
        }
        
        if(barter_wnd==null)
        {
            NUtils.getGameUI().msg("TakeItems2.barter: ERROR - barter_wnd is null!");
            return Results.ERROR("No Barter window");
        }
        
        // Ждем загрузки окна через FindBarterStand (как в FillContainersFromPiles)
        // OpenTargetContainer уже добавил задачу FindBarterStand в core
        int waitAttempts = 0;
        while(waitAttempts < 30) {
            nurgling.tasks.FindBarterStand findTask = new nurgling.tasks.FindBarterStand();
            if(findTask.check()) {
                NUtils.getGameUI().msg("TakeItems2.barter: Window fully loaded");
                break;
            }
            Thread.sleep(200);
            waitAttempts++;
        }
        
        // Отладочное сообщение - проверяем что окно действительно открыто
        NUtils.getGameUI().msg("TakeItems2.barter: barter_wnd found, checking widgets...");
        
        HashSet<String> itemNames = new HashSet<>();
        if(nurgling.tools.VSpec.categories.containsKey(item)) {
            ArrayList<org.json.JSONObject> categoryItems = nurgling.tools.VSpec.categories.get(item);
            if(categoryItems != null) {
                for(org.json.JSONObject categoryItem : categoryItems) {
                    itemNames.add(categoryItem.getString("name"));
                }
            }
            if(itemNames.isEmpty()) {
                itemNames.add(item);
            }
        } else {
            itemNames.add(item);
        }
        
        // Отладочное сообщение
        NUtils.getGameUI().msg("TakeItems2.barter: item=" + item + ", itemNames.size()=" + itemNames.size());
        if(itemNames.size() <= 5) {
            for(String name : itemNames) {
                NUtils.getGameUI().msg("TakeItems2.barter: itemName=" + name);
            }
        }
        
        // Отладочное сообщение - проверяем структуру окна
        int totalWidgets = 0;
        int shopboxWidgets = 0;
        for(Widget ch = barter_wnd.child; ch != null; ch = ch.next) {
            totalWidgets++;
            if (ch instanceof Shopbox) {
                shopboxWidgets++;
            }
        }
        NUtils.getGameUI().msg("TakeItems2.barter: barter_wnd widgets: total=" + totalWidgets + ", shopbox=" + shopboxWidgets);
        
        boolean foundOffer = false;
        int shopboxIndex = 0;
        // Используем прямой порядок как в FillContainersFromPiles (child и next)
        for(Widget ch = barter_wnd.child; ch != null; ch = ch.next)
        {
            if (ch instanceof Shopbox)
            {
                shopboxIndex++;
                Shopbox sb = (Shopbox) ch;
                
                // Всегда пытаемся получить имя, даже если getOffer() вернул null
                String offerName = null;
                
                // Сначала пробуем через getOffer()
                Shopbox.ShopItem offer = sb.getOffer();
                if(offer != null) {
                    offerName = offer.name;
                }
                
                // Если getOffer() вернул null, но есть res (иконка), пробуем получить имя из info() напрямую
                if(offerName == null && sb.res != null) {
                    try {
                        java.util.List<haven.ItemInfo> infos = sb.info();
                        if(infos != null && !infos.isEmpty()) {
                            haven.ItemInfo.Name nm = haven.ItemInfo.find(haven.ItemInfo.Name.class, infos);
                            if(nm != null && nm.str != null) {
                                offerName = nm.str.text;
                            }
                        }
                    } catch (Exception e) {
                        NUtils.getGameUI().msg("TakeItems2.barter: Error getting name from info: " + e.getMessage());
                    }
                }
                
                // Отладочное сообщение для всех Shopbox
                if(offerName != null) {
                    NUtils.getGameUI().msg("TakeItems2.barter: Shopbox[" + shopboxIndex + "] offerName=" + offerName + ", res=" + (sb.res != null) + ", contains=" + itemNames.contains(offerName));
                } else {
                    NUtils.getGameUI().msg("TakeItems2.barter: Shopbox[" + shopboxIndex + "] offerName=null, res=" + (sb.res != null) + ", price=" + (sb.price != null));
                }
                
                if (offerName != null)
                {
                    // Проверяем, входит ли предложение в категорию - используем простую проверку через HashSet
                    // Это та же логика, что в FillContainersFromPiles
                    if (itemNames.contains(offerName))
                    {
                        // НЕ проверяем leftNum - это глюк, если иконка есть, значит предметы есть
                        // Проверяем только что есть иконка предмета (res != null)
                        if(sb.res == null) {
                            NUtils.getGameUI().msg("TakeItems2.barter: No icon for offer, skipping");
                            continue; // Пропускаем если нет иконки
                        }
                        
                        foundOffer = true;
                        NUtils.getGameUI().msg("TakeItems2.barter: FOUND offer! name=" + offerName + ", itemsToBuy=" + itemsToBuy);
                        
                        // Покупаем столько, сколько нужно
                        for (int i = 0; i < itemsToBuy; i++)
                        {
                            sb.wdgmsg("buy", new Object[0]);
                            // Небольшая пауза между покупками
                            Thread.sleep(100);
                        }

                        // Небольшая пауза после всех покупок
                        Thread.sleep(200);

                        if(nurgling.tools.VSpec.categories.containsKey(item)) {
                            ArrayList<String> categoryItemNames = new ArrayList<>(itemNames);
                            NUtils.getUI().core.addTask(new WaitItems(NUtils.getGameUI().getInventory(), new NAlias(categoryItemNames, new ArrayList<>()), itemsToBuy));
                        } else {
                            NUtils.getUI().core.addTask(new WaitItems(NUtils.getGameUI().getInventory(), new NAlias(item), itemsToBuy));
                        }
                        left.set(left.get() - itemsToBuy);
                        
                        // ВАЖНО: Сначала закрываем окно бартера, ПОТОМ идём к сундуку
                        // Если сначала пойти к сундуку, окно бартера закроется автоматически (из-за удаления от стенда)
                        // и попытка закрыть его потом вызовет зависание на WindowIsClosed
                        new CloseTargetWindow(barter_wnd).run(gui);
                        
                        // Возвращаем лишние ветки в сундук сразу после покупки
                        ArrayList<WItem> remainingBranches = gui.getInventory().getItems("Branch");
                        if(!remainingBranches.isEmpty()) {
                            new PathFinder(gchest).run(gui);
                            new OpenTargetContainer("Chest", gchest).run(gui);
                            
                            // Возвращаем все оставшиеся ветки в сундук
                            new SimpleTransferToContainer(gui.getInventory("Chest"), remainingBranches).run(gui);
                            
                            // Закрываем сундук
                            new CloseTargetWindow(gui.getWindow("Chest")).run(gui);
                        }
                        
                        break;
                    }
                }
            }
        }
        
        if(!foundOffer) {
            NUtils.getGameUI().msg("TakeItems2.barter: NO OFFER FOUND! itemNames=" + itemNames);
            // Закрываем окно бартера если ничего не нашли
            new CloseTargetWindow(barter_wnd).run(gui);
            return Results.FAIL();
        }
        
        return Results.SUCCESS();
    }

    public Results takeFromPile(AtomicInteger left, NGameUI gui, NContext.Pile pile) throws InterruptedException
    {
        if(PathFinder.isAvailable(pile.pile))
        {
            new PathFinder(pile.pile).run(gui);
            new OpenTargetContainer("Stockpile", pile.pile).run(gui);
            TakeItemsFromPile tifp;
            (tifp = new TakeItemsFromPile(pile.pile, gui.getStockpile(), left.get())).run(gui);
            new CloseTargetWindow(NUtils.getGameUI().getWindow("Stockpile")).run(gui);
            left.set(left.get() - tifp.getResult());
        }
        return Results.SUCCESS();
    }

    public Results takeFromContainer(AtomicInteger left, NGameUI gui, Container cont) throws InterruptedException
    {
        Gob contgob = Finder.findGob(cont.gobHash);
        if(contgob == null)
            return Results.FAIL();
        if(!"Frame".equals(cont.cap) && contgob.ngob.isContainerEmpty())
            return Results.SUCCESS();
        new PathFinder(contgob).run(gui);
        new OpenTargetContainer(cont).run(gui);
        
        HashSet<String> itemNames = new HashSet<>();
        if(nurgling.tools.VSpec.categories.containsKey(item)) {
            ArrayList<org.json.JSONObject> categoryItems = nurgling.tools.VSpec.categories.get(item);
            if(categoryItems != null) {
                for(org.json.JSONObject categoryItem : categoryItems) {
                    itemNames.add(categoryItem.getString("name"));
                }
            }
        }
        if(itemNames.isEmpty()) {
            itemNames.add(item);
        }
        
        int countBefore = countItemsInInventory(gui, itemNames);
        TakeItemsFromContainer tifc = new TakeItemsFromContainer(cont, itemNames, null, qualityType);
        tifc.minSize = left.get();
        tifc.exactMatch = this.exactMatch;
        tifc.run(gui);
        int countAfter = countItemsInInventory(gui, itemNames);
        left.set(left.get() - (countAfter - countBefore));
        new CloseTargetContainer(cont).run(gui);
        return Results.SUCCESS();
    }

    private int countItemsInInventory(NGameUI gui, HashSet<String> names) throws InterruptedException {
        int total = 0;
        for (String name : names) {
            total += gui.getInventory().getItems(new NAlias(name)).size();
        }
        return total;
    }
}
