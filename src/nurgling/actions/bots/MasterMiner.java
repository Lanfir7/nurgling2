package nurgling.actions.bots;

import haven.*;
import haven.Audio;
import haven.MCache;
import haven.Resource;
import haven.res.lib.itemtex.ItemTex;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.ActionWithFinal;
import nurgling.actions.Results;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitTicks;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.NInventory;
import nurgling.widgets.NEquipory;
import nurgling.widgets.bots.MasterMinerWnd;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Информативный "MiningMaster": висит и ждёт выпадения камня в инвентарь
 * (проверка выполняется только когда курсор в режиме майнинга).
 * По выпавшему камню считает "реальное качество в стене" и показывает максимум.
 *
 * Формула:
 * ((F3−F4)*2 + (F4−10)/F5) + 10
 * F3 — качество выпавшего предмета,
 * F4 — качество инструмента,
 * F5 — дебаф инструмента:
 *   каменный топор 0.8, тинкер-топор 0.9, кирка 1.0
 */
public class MasterMiner extends ActionWithFinal {

    private static final NAlias MINED_ITEMS;
    private static final NAlias ORE_ITEMS; // Список руд для системы спотов
    static {
        // используем полный список камней из Chipper
        MINED_ITEMS = Chipper.stones;
        
        // Список руд для системы спотов (с указанием приоритета в скобках)
        ORE_ITEMS = new NAlias(new ArrayList<>(List.of(
            "Black Ore", "Bloodstone", "Cassiterite", "Chalcopyrite", "Cinnabar",
            "Direvein", "Galena", "Heavy Earth", "Horn Silver", "Iron Ochre",
            "Lead Glance", "Leaf Ore", "Malachite", "Meteorite", "Peacock Ore",
            "Schrifterz", "Silvershine", "Wine Glance"
        )));
    }

    private volatile boolean stop = false;
    private MasterMinerWnd wnd = null;
    private ArrayList<WItem> known = new ArrayList<>();

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        // сброс состояния для повторного запуска
        stop = false;
        known.clear();
        wnd = gui.add(new MasterMinerWnd(), UI.scale(200, 200));

        // Активируем курсор майнинга при запуске
        try {
            Gob player = NUtils.player();
            if (player != null) {
                NUtils.mine(player.rc);
            }
        } catch (Exception ignored) {
            // Игнорируем ошибки активации курсора
        }

        try {
            // Получаем все предметы из инвентаря и фильтруем камни (обычные и драгоценные)
            ArrayList<WItem> allItems;
            try {
                allItems = gui.getInventory().getItems();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Results.SUCCESS();
            }
            known = filterMinedItems(allItems);

            while (!stop && wnd != null && !wnd.isClosed()) {
                int masonry = 0;
                try {
                    masonry = NUtils.getUI().sess.glob.getcattr("masonry").comp;
                } catch (Exception ignored) {
                }
                wnd.setMasonry(masonry);

                String curs = NUtils.getCursorName();
                boolean mining = (curs != null) && NParser.checkName(curs, "mine");

                if (!mining) {
                    NUtils.addTask(new WaitTicks(10));
                    continue;
                }

                // Получаем все предметы из инвентаря и фильтруем камни (обычные и драгоценные)
                ArrayList<WItem> cur;
                try {
                    allItems = gui.getInventory().getItems();
                    cur = filterMinedItems(allItems);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                // Проверяем также предмет в руках (vhand) - камень/руда может попасть туда, если инвентарь полон
                WItem vhandItem = gui.vhand;
                if (vhandItem != null && vhandItem.item instanceof NGItem) {
                    NGItem vhandNGItem = (NGItem) vhandItem.item;
                    String vhandName = vhandNGItem.name();
                    if (vhandName != null) {
                        // Проверяем, является ли это камнем/рудой/драгоценным камнем
                        boolean isMinedItem = NParser.checkName(vhandName, MINED_ITEMS) || 
                                             NParser.checkName(vhandName, ORE_ITEMS) ||
                                             isGemstone(vhandNGItem) || 
                                             isGemstone(vhandName);
                        if (isMinedItem && !known.contains(vhandItem)) {
                            // Обрабатываем камень из рук
                            try {
                                processNewStone(gui, vhandItem, wnd);
                                known.add(vhandItem);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                
                // Обрабатываем все новые камни, а не только первый
                ArrayList<WItem> newItems = new ArrayList<>();
                for (WItem it : cur) {
                    if (!known.contains(it)) {
                        newItems.add(it);
                    }
                }
                
                // Также проверяем ВСЕ стаки в инвентаре каждый цикл (они могут обновляться без появления новых предметов)
                // Проверяем все стаки, независимо от того, новые они или нет
                ArrayList<WItem> stacksToCheck = new ArrayList<>();
                for (WItem it : cur) {
                    // Проверяем, является ли это стаком
                    try {
                        NGItem ngItem = (NGItem) it.item;
                        haven.GItem.Amount amount = ngItem.getInfo(haven.GItem.Amount.class);
                        if (amount != null && amount.itemnum() > 1) {
                            // Это стак - проверяем его качество
                            stacksToCheck.add(it);
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки
                    }
                }
                
                if (newItems.isEmpty() && stacksToCheck.isEmpty()) {
                    NUtils.addTask(new WaitTicks(5));
                    continue;
                }
                known = cur;
                
                // Обрабатываем каждый новый камень
                for (WItem newItem : newItems) {
                    processNewStone(gui, newItem, wnd);
                }
                
                // Обрабатываем стаки (проверяем их качество и сбрасываем если нужно)
                // Важно: обрабатываем стаки отдельно, чтобы не мешать обработке новых предметов
                for (WItem stackItem : stacksToCheck) {
                    try {
                        // Для стаков проверяем качество и сбрасываем напрямую, если нужно
                        checkAndDropStack(gui, stackItem, wnd);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                // Небольшой yield после обработки всех камней
                NUtils.addTask(new WaitTicks(2));
            }
        } finally {
            if (wnd != null) {
                try { wnd.destroy(); } catch (Exception ignored) {}
            }
            wnd = null;
        }
        return Results.SUCCESS();
    }
    
    /**
     * Получает качество предмета, учитывая стаки
     * Для стаков использует Stack info, для отдельных предметов - item.quality
     */
    private double getItemQuality(NGItem item, WItem wItem) {
        if (item == null) return -1;
        
        // Проверяем, является ли это стаком
        try {
            haven.GItem.Amount amount = item.getInfo(haven.GItem.Amount.class);
            if (amount != null && amount.itemnum() > 1) {
                // Это стак - получаем качество через Stack info
                haven.res.ui.tt.stackn.Stack stackInfo = item.getInfo(haven.res.ui.tt.stackn.Stack.class);
                if (stackInfo != null && stackInfo.quality > 0) {
                    return stackInfo.quality;
                }
                // Если Stack info еще не готов, пробуем получить через Quality info из info()
                List<ItemInfo> infoList = item.info();
                if (infoList != null) {
                    haven.res.ui.tt.q.quality.Quality qualityInfo = haven.ItemInfo.find(haven.res.ui.tt.q.quality.Quality.class, infoList);
                    if (qualityInfo != null && qualityInfo.q > 0) {
                        return qualityInfo.q;
                    }
                }
                // Если и это не сработало, пробуем получить среднее качество из всех предметов в стаке
                // (это fallback на случай, если Stack info еще не обновился)
                if (wItem != null && wItem.parent instanceof haven.res.ui.stackinv.ItemStack) {
                    haven.res.ui.stackinv.ItemStack itemStack = (haven.res.ui.stackinv.ItemStack) wItem.parent;
                    double sumQuality = 0;
                    int count = 0;
                    for (WItem w : itemStack.wmap.values()) {
                        if (w.item instanceof NGItem) {
                            NGItem ngItem = (NGItem) w.item;
                            if (ngItem.quality != null) {
                                sumQuality += ngItem.quality;
                                count++;
                            }
                        }
                    }
                    if (count > 0) {
                        return sumQuality / count;
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        
        // Для отдельных предметов используем item.quality
        if (item.quality != null) {
            return item.quality;
        }
        
        return -1; // Качество еще не готово
    }
    
    /**
     * Проверяет стак и сбрасывает его, если качество ниже порога
     */
    private void checkAndDropStack(NGameUI gui, WItem stackItem, MasterMinerWnd wnd) throws InterruptedException {
        if (stackItem == null || stackItem.item == null || !(stackItem.item instanceof NGItem)) {
            return;
        }
        
        NGItem ngItem = (NGItem) stackItem.item;
        String itemName = ngItem.name();
        if (itemName == null) {
            return;
        }
        
        // Проверяем, является ли это камнем/рудой (драгоценные камни не сбрасываются)
        boolean isMinedItem = NParser.checkName(itemName, MINED_ITEMS) || 
                             NParser.checkName(itemName, ORE_ITEMS);
        // Драгоценные камни не сбрасываются
        if (isGemstone(ngItem) || isGemstone(itemName)) {
            return;
        }
        if (!isMinedItem) {
            return; // Не камень/руда - пропускаем
        }
        
        // Получаем качество стака - пробуем несколько раз, так как Stack info может быть еще не готов
        double f3 = -1;
        for (int attempt = 0; attempt < 5; attempt++) {
            f3 = getItemQuality(ngItem, stackItem);
            if (f3 >= 0) {
                break; // Качество получено
            }
            // Небольшая задержка перед следующей попыткой
            if (attempt < 4) {
                NUtils.addTask(new WaitTicks(2));
            }
        }
        
        if (f3 < 0) {
            return; // Качество все еще не готово после всех попыток
        }
        
        // Определяем тип камня и порог
        String stoneType = classifyStoneType(itemName);
        double threshold;
        if ("Shell".equals(stoneType) || "Cat Gold".equals(stoneType)) {
            threshold = wnd.getShellCatGoldThreshold();
        } else {
            threshold = wnd.getDropThreshold();
        }
        
        // Проверяем порог и сбрасываем стак, если качество ниже
        if (!Double.isNaN(threshold) && f3 < threshold) {
            // Проверяем, что это действительно камень из инвентаря, а не инструмент
            boolean isInInventory = (stackItem.parent == gui.getInventory());
            boolean isInHand = (stackItem == gui.vhand);
            if (isInInventory || isInHand) {
                // Дополнительная проверка: убеждаемся, что это не инструмент
                String itemNameLower = itemName != null ? itemName.toLowerCase() : "";
                boolean isTool = itemNameLower.contains("axe") || itemNameLower.contains("pickaxe") || 
                               itemNameLower.contains("топор") || itemNameLower.contains("кирк");
                if (!isTool) {
                    // Небольшая задержка перед сбросом
                    NUtils.addTask(new WaitTicks(3));
                    // Еще раз проверяем, что предмет все еще в инвентаре или в руках
                    if ((stackItem.parent == gui.getInventory()) || (stackItem == gui.vhand)) {
                        NUtils.drop(stackItem);
                        // Удаляем из known, чтобы не обрабатывать повторно
                        known.remove(stackItem);
                    }
                }
            }
        }
    }
    
    /**
     * Фильтрует предметы из инвентаря, оставляя только выкопанные камни (обычные и драгоценные)
     */
    private ArrayList<WItem> filterMinedItems(ArrayList<WItem> allItems) {
        ArrayList<WItem> result = new ArrayList<>();
        if (allItems == null) return result;
        
        for (WItem item : allItems) {
            if (item == null || item.item == null) continue;
            
            try {
                NGItem ngItem = (NGItem) item.item;
                String itemName = ngItem.name();
                
                if (itemName == null) continue;
                
                // Проверяем, является ли это обычным камнем (из MINED_ITEMS)
                if (NParser.checkName(itemName, MINED_ITEMS)) {
                    result.add(item);
                    continue;
                }
                
                // Проверяем, является ли это драгоценным камнем
                if (isGemstone(ngItem) || isGemstone(itemName)) {
                    result.add(item);
                    continue;
                }
            } catch (Exception e) {
                // Игнорируем ошибки при проверке предмета
            }
        }
        
        return result;
    }
    
    /**
     * Обрабатывает один новый камень
     */
    private void processNewStone(NGameUI gui, WItem newItem, MasterMinerWnd wnd) throws InterruptedException {
        NGItem dropped = (NGItem) newItem.item;
        
        // Для стаков нужно получить качество через Stack info
        double f3 = getItemQuality(dropped, newItem);
        
        if (f3 < 0) {
            // Качество еще не готово - ждем
            WItem finalNewItem = newItem;
            NUtils.addTask(new NTask() {
                { this.infinite = true; }
                @Override
                public boolean check() {
                    NGItem gi = (NGItem) finalNewItem.item;
                    if (gi.name() == null) return false;
                    double quality = getItemQuality(gi, finalNewItem);
                    return quality >= 0;
                }
            });
            f3 = getItemQuality(dropped, newItem);
            if (f3 < 0) {
                NUtils.addTask(new WaitTicks(2));
                return;
            }
        }
        String stoneName = dropped.name();
        String stoneType = classifyStoneType(stoneName);

        // Проверяем, является ли это драгоценным камнем
        boolean isGem = isGemstone(dropped);
        if (!isGem) {
            isGem = isGemstone(stoneName);
        }
        
        // Драгоценные камни НЕ учитываются при подсчете качества и НЕ обновляют UI
        // Но маркеры для них ставятся
        if (isGem) {
            // Обновляем UI с последним выкопанным драгоценным камнем
            // Для драгоценных камней wallQ = f3 (без формулы)
            int masonryForUI = 0;
            try {
                masonryForUI = NUtils.getUI().sess.glob.getcattr("masonry").comp;
            } catch (Exception ignored) {
            }
            wnd.setLastMined(stoneName, f3, masonryForUI); // Для драгоценных камней f3 = wallQ
            
            // Только ставим маркер для драгоценного камня, если он включен в настройках
            nurgling.conf.NMasterMinerMarkingConfig markingConfig = nurgling.conf.NMasterMinerMarkingConfig.get();
            if (markingConfig != null) {
                String configKey = extractGemstoneBaseName(stoneName);
                
                // Пробуем найти в конфиге с разными вариантами регистра
                Boolean enabled = markingConfig.isEnabled(configKey);
                if (enabled == null && !configKey.equals(configKey.toLowerCase())) {
                    // Пробуем с маленькой буквы
                    enabled = markingConfig.isEnabled(configKey.toLowerCase());
                    if (enabled != null) {
                        configKey = configKey.toLowerCase();
                    }
                }
                if (enabled == null && !configKey.equals(configKey.substring(0, 1).toUpperCase() + configKey.substring(1).toLowerCase())) {
                    // Пробуем с правильным регистром (первая буква заглавная)
                    String properCase = configKey.substring(0, 1).toUpperCase() + configKey.substring(1).toLowerCase();
                    enabled = markingConfig.isEnabled(properCase);
                    if (enabled != null) {
                        configKey = properCase;
                    }
                }
                
                Double threshold = markingConfig.getThreshold(configKey);
                
                if (enabled != null && enabled) {
                    double itemThreshold = (threshold != null && !threshold.isNaN()) ? threshold : 10.0;
                    if (f3 >= itemThreshold) {
                        // Драгоценные камни - отдельный слой, ставим с фактическим качеством (f3)
                        // Используем базовое название для resourceType (например, "Moonstone" вместо "Small Smooth Moonstone")
                        String baseGemName = extractGemstoneBaseName(stoneName);
                        try {
                            addGemstoneMarker(gui, dropped, baseGemName, f3);
                        } catch (Exception e) {
                            // Игнорируем ошибки
                        }
                    }
                }
            }
            // Драгоценные камни НЕ сбрасываются и НЕ учитываются в статистике
            return;
        }

        WItem tool = findMiningTool();
        if (tool == null) {
            NUtils.addTask(new WaitTicks(10));
            return;
        }

        // ждём имя/качество инструмента
        final WItem ftool = tool;
        NUtils.addTask(new NTask() {
            { this.infinite = true; }
            @Override
            public boolean check() {
                NGItem ti = (NGItem) ftool.item;
                return ti.name() != null && ti.quality != null;
            }
        });

        String toolName = ((NGItem) ftool.item).name();
        Double f4 = ((NGItem) ftool.item).quality != null ? (double) ((NGItem) ftool.item).quality : null;
        double f5 = toolCoef(toolName);
        ToolType currentToolType = classifyTool(toolName);

        if (f4 != null) {
            // Для квариарца: новая формула (камень в рюкзаке - инструмент + камень в рюкзаке)
            // Для остальных камней: старая формула с дебафами инструмента
            double wallQ;
            if ("Quarryartz".equals(stoneType)) {
                // Новая формула для квариарца: f3 - f4 + f3 = 2*f3 - f4
                // wallQ - это качество в стене, оно одинаково для всех инструментов
                wallQ = (2.0 * f3) - f4;
            } else {
                // Старая формула с дебафами инструмента для остальных камней
                wallQ = calcWallQ(f3, f4, f5);
            }

            // находим лучшее качество с другим инструментом (исключая текущий)
            ToolSet set = scanTools(gui, ftool);
            Double bestAltQ = null;
            if (currentToolType != ToolType.STONE_AXE && set.stoneAxeQ != null) {
                Double pred;
                if ("Quarryartz".equals(stoneType)) {
                    // Для квариарца: обратная формула от wallQ = 2*f3 - f4
                    // Если wallQ = 2*f3_new - f4_new, то f3_new = (wallQ + f4_new) / 2
                    // wallQ одинаково для всех инструментов, поэтому используем то же wallQ
                    pred = (wallQ + set.stoneAxeQ) / 2.0;
                } else {
                    pred = invDropQ(wallQ, set.stoneAxeQ, 0.8);
                }
                if (bestAltQ == null || (pred != null && pred > bestAltQ)) bestAltQ = pred;
            }
            if (currentToolType != ToolType.TINKER_AXE && set.tinkerAxeQ != null) {
                Double pred;
                if ("Quarryartz".equals(stoneType)) {
                    // Для квариарца: обратная формула от wallQ = 2*f3 - f4
                    // Если wallQ = 2*f3_new - f4_new, то f3_new = (wallQ + f4_new) / 2
                    // wallQ одинаково для всех инструментов, поэтому используем то же wallQ
                    pred = (wallQ + set.tinkerAxeQ) / 2.0;
                } else {
                    pred = invDropQ(wallQ, set.tinkerAxeQ, 0.9);
                }
                if (bestAltQ == null || (pred != null && pred > bestAltQ)) bestAltQ = pred;
            }
            if (currentToolType != ToolType.PICKAXE && set.pickaxeQ != null) {
                Double pred;
                if ("Quarryartz".equals(stoneType)) {
                    // Для квариарца: обратная формула от wallQ = 2*f3 - f4
                    // Если wallQ = 2*f3_new - f4_new, то f3_new = (wallQ + f4_new) / 2
                    // wallQ одинаково для всех инструментов, поэтому используем то же wallQ
                    pred = (wallQ + set.pickaxeQ) / 2.0;
                } else {
                    pred = invDropQ(wallQ, set.pickaxeQ, 1.0);
                }
                if (bestAltQ == null || (pred != null && pred > bestAltQ)) bestAltQ = pred;
            }

            // обновляем UI для соответствующего типа камня
            if (stoneType != null) {
                int masonryForUI = 0;
                try {
                    masonryForUI = NUtils.getUI().sess.glob.getcattr("masonry").comp;
                } catch (Exception ignored) {
                }
                wnd.setStoneInfo(stoneType, stoneName, f3, wallQ, bestAltQ, masonryForUI, set, currentToolType);
                wnd.setLastMined(stoneName, wallQ, masonryForUI); // Передаем wallQ вместо f3
                wnd.incrementCounter();
                
                // Проверяем, нужно ли поставить метку на карте согласно настройкам
                nurgling.conf.NMasterMinerMarkingConfig markingConfig = nurgling.conf.NMasterMinerMarkingConfig.get();
                if (markingConfig != null) {
                    // Для остальных камней используем полное название
                    String configKey = stoneName;
                    
                    Boolean enabled = markingConfig.isEnabled(configKey);
                    Double threshold = markingConfig.getThreshold(configKey);
                    
                    // Если элемент включен в настройках и качество в стене >= порога
                    if (enabled != null && enabled) {
                        double itemThreshold = (threshold != null && !threshold.isNaN()) ? threshold : 10.0;
                        
                        if (wallQ >= itemThreshold) {
                            if ("Quarryartz".equals(stoneType)) {
                                // Квариарц ставится четко в месте выкопан
                                addQuarryartzMarker(gui, stoneName, wallQ);
                            } else {
                                // Остальные камни и руды - система спотов (обновление в радиусе 30 клеток)
                                addOreSpotMarker(gui, dropped, stoneName, wallQ);
                            }
                        }
                    }
                }
            }

            // проверка порога и сброс камня (включая стаки)
            // Сброс происходит по фактическому качеству камня (f3), а не по qWall
            // Для ракух и кэтголдов используется отдельный порог
            // Драгоценные камни НЕ сбрасываются (они уже обработаны выше и вернулись)
            double threshold;
            if ("Shell".equals(stoneType) || "Cat Gold".equals(stoneType)) {
                threshold = wnd.getShellCatGoldThreshold();
            } else {
                threshold = wnd.getDropThreshold();
            }
            
            if (!Double.isNaN(threshold) && f3 < threshold) {
                // проверяем, что это действительно камень из инвентаря или из рук (vhand), а не инструмент
                boolean isInInventory = (newItem != null && newItem.parent == gui.getInventory());
                boolean isInHand = (newItem != null && newItem == gui.vhand);
                if (isInInventory || isInHand) {
                    // дополнительная проверка: убеждаемся, что это не инструмент
                    String itemName = stoneName != null ? stoneName.toLowerCase() : "";
                    boolean isTool = itemName.contains("axe") || itemName.contains("pickaxe") || 
                                   itemName.contains("топор") || itemName.contains("кирк");
                    // Стаки теперь тоже сбрасываются
                    if (!isTool) {
                        // небольшая задержка перед сбросом, чтобы игра успела обработать появление камня
                        NUtils.addTask(new WaitTicks(3));
                        // еще раз проверяем, что предмет все еще в инвентаре или в руках
                        if ((newItem.parent == gui.getInventory()) || (newItem == gui.vhand)) {
                            NUtils.drop(newItem);
                            // удаляем из known, чтобы не обрабатывать повторно
                            known.remove(newItem);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void endAction() {
        stop = true;
        if (wnd != null) {
            try { wnd.destroy(); } catch (Exception ignored) {}
        }
    }

    private static double calcWallQ(double f3, double f4, double f5) {
        // Если порода ВЫШЕ качества инструмента - используем формулу
        // Если ниже - остается как есть (wallQ = f3)
        if (f3 < f4) {
            return f3;
        }
        if (f5 <= 0) f5 = 1.0;
        return ((f3 - f4) * 2.0 + (f4 - 10.0) / f5) + 10.0;
    }


    /**
     * Проверяет, является ли камень рудой для системы спотов
     */
    public static boolean isOre(String stoneName) {
        if (stoneName == null) return false;
        // Проверяем точное совпадение с названиями руд (регистронезависимо)
        String lowerName = stoneName.toLowerCase().trim();
        for (String oreKey : ORE_ITEMS.keys) {
            if (oreKey != null) {
                String lowerOreKey = oreKey.toLowerCase().trim();
                // Проверяем точное совпадение или содержит (на случай если есть дополнительные символы)
                if (lowerName.equals(lowerOreKey) || lowerName.contains(lowerOreKey)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Проверяет, является ли камень драгоценным
     * Проверяет последнее слово в названии (например, "Onyx", "Amethyst")
     * До этого идут слова об огранке и размере
     */
    public static boolean isGemstone(String stoneName) {
        if (stoneName == null || stoneName.trim().isEmpty()) return false;
        
        // Разбиваем название на слова и берем последнее слово
        String[] words = stoneName.trim().split("\\s+");
        if (words.length == 0) return false;
        
        String lastWord = words[words.length - 1].toLowerCase();
        
        // Специальная обработка для составных названий (два слова)
        if (words.length > 1) {
            String secondLastWord = words[words.length - 2].toLowerCase();
            
            // "Dust Jewel" - последнее слово "jewel", предпоследнее "dust"
            if (lastWord.equals("jewel") && secondLastWord.equals("dust")) {
                return true;
            }
            
            // "Star Shard" - последнее слово "shard", предпоследнее "star"
            if (lastWord.equals("shard") && secondLastWord.equals("star")) {
                return true;
            }
            
            // "Sugar Diamond" - последнее слово "diamond", предпоследнее "sugar"
            if (lastWord.equals("diamond") && secondLastWord.equals("sugar")) {
                return true;
            }
            
            // "Red Coral" - последнее слово "coral", предпоследнее "red"
            if (lastWord.equals("coral") && secondLastWord.equals("red")) {
                return true;
            }
            
            // "Oyster Pearl" - последнее слово "pearl", предпоследнее "oyster"
            if (lastWord.equals("pearl") && secondLastWord.equals("oyster")) {
                return true;
            }
            
            // "River Pearl" - последнее слово "pearl", предпоследнее "river"
            if (lastWord.equals("pearl") && secondLastWord.equals("river")) {
                return true;
            }
        }
        
        // Список простых названий драгоценных камней (одно слово - последнее слово)
        // Полный список из игры: Amber, Amethyst, Diamond, Emerald, Jade,
        // Moonstone, Onyx, Opal, Ruby, Sapphire, Topaz, Turquoise
        String[] simpleGemstoneNames = {
            "amber", "amethyst", "diamond", "emerald", "jade",
            "moonstone", "onyx", "opal", "ruby",
            "sapphire", "topaz", "turquoise"
        };
        
        // Проверяем простые названия (но исключаем "diamond", если это "Sugar Diamond")
        for (String gemName : simpleGemstoneNames) {
            if (lastWord.equals(gemName)) {
                // Для "diamond" проверяем, что это не "Sugar Diamond"
                if (gemName.equals("diamond") && words.length > 1 && 
                    words[words.length - 2].toLowerCase().equals("sugar")) {
                    continue; // Это "Sugar Diamond", уже обработан выше
                }
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Извлекает базовое название драгоценного камня из полного названия
     * Например: "Fair Cabochon Onyx" -> "Onyx", "Small Rough Onyx" -> "Onyx"
     * "Dust Jewel" -> "Dust Jewel", "Sugar Diamond" -> "Sugar Diamond"
     */
    private static String extractGemstoneBaseName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return fullName;
        
        String[] words = fullName.trim().split("\\s+");
        if (words.length == 0) return fullName;
        
        String lastWord = words[words.length - 1];
        if (lastWord == null || lastWord.isEmpty()) return fullName;
        
        // Проверяем составные названия (два слова)
        if (words.length > 1) {
            String secondLastWord = words[words.length - 2];
            if (secondLastWord != null && !secondLastWord.isEmpty()) {
                String secondLastWordLower = secondLastWord.toLowerCase();
                String lastWordLower = lastWord.toLowerCase();
                
                // Составные названия возвращаем полностью
                if ((lastWordLower.equals("jewel") && secondLastWordLower.equals("dust")) ||
                    (lastWordLower.equals("shard") && secondLastWordLower.equals("star")) ||
                    (lastWordLower.equals("diamond") && secondLastWordLower.equals("sugar")) ||
                    (lastWordLower.equals("coral") && secondLastWordLower.equals("red")) ||
                    (lastWordLower.equals("pearl") && (secondLastWordLower.equals("oyster") || secondLastWordLower.equals("river")))) {
                    return secondLastWord.substring(0, 1).toUpperCase() + secondLastWord.substring(1).toLowerCase() + " " + 
                           lastWord.substring(0, 1).toUpperCase() + lastWord.substring(1).toLowerCase();
                }
            }
        }
        
        // Для простых названий возвращаем последнее слово с заглавной буквы
        if (lastWord.length() > 1) {
            return lastWord.substring(0, 1).toUpperCase() + lastWord.substring(1).toLowerCase();
        } else {
            return lastWord.toUpperCase();
        }
    }
    
    /**
     * Проверяет, является ли предмет драгоценным камнем по ресурсному пути
     * Более надежный способ определения драгоценных камней
     */
    public static boolean isGemstone(NGItem item) {
        if (item == null) return false;
        
        // Сначала проверяем по названию (для обратной совместимости)
        String name = item.name();
        if (isGemstone(name)) {
            return true;
        }
        
        // Проверяем ресурсный путь (более надежно)
        // Проверяем, загружен ли ресурс, и если да - проверяем его путь
        try {
            // Пробуем получить ресурс через res.get()
            if (item.res != null) {
                // Сначала проверяем, готов ли ресурс
                if (item.res.isReady()) {
                    try {
                        Resource res = item.res.get();
                        if (res != null && res.name != null) {
                            String resName = res.name.toLowerCase();
                            // Проверяем различные паттерны для драгоценных камней в пути ресурса
                            // Учитываем форматы: ns/gemstone, gems/gemstone, /gems/, invobjs/gems, gfx/invobjs/gems
                            // Простая проверка: если путь содержит "gemstone" или заканчивается на "/gems"
                            if (resName.contains("gemstone") || 
                                resName.contains("/gems/") ||
                                resName.endsWith("/gems") ||
                                resName.contains("invobjs/gems") ||
                                resName.contains("gfx/invobjs/gems")) {
                                return true;
                            }
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки
                    }
                }
            }
            
            // Также пробуем через getres() метод (может работать даже если res не готов)
            try {
                Resource res2 = item.getres();
                if (res2 != null && res2.name != null) {
                    String resName2 = res2.name.toLowerCase();
                    // Проверяем различные паттерны для драгоценных камней в пути ресурса
                    if (resName2.contains("gemstone") || 
                        resName2.contains("/gems/") ||
                        resName2.endsWith("/gems") ||
                        resName2.contains("invobjs/gems") ||
                        resName2.contains("gfx/invobjs/gems")) {
                        return true;
                    }
                }
            } catch (Loading e) {
                // Ресурс еще загружается, пропускаем - полагаемся на проверку по названию
            } catch (Exception ignored) {
                // Игнорируем другие ошибки getres()
            }
        } catch (Exception e) {
            // Если не удалось проверить ресурс, используем только проверку по названию
        }
        
        return false;
    }
    
    /**
     * Определяет тип камня по названию
     */
    private static String classifyStoneType(String stoneName) {
        if (stoneName == null) return null;
        String name = stoneName.toLowerCase();
        if (name.contains("quarryartz")) return "Quarryartz";
        if (name.contains("cat gold") || name.contains("кэт голд")) return "Cat Gold";
        if (name.contains("rakuh") || name.contains("ракуха") || 
            name.contains("shard of conch") || name.contains("parifai") || 
            name.contains("seashell") || name.contains("petrifiedshell") || 
            name.contains("petrified seashell")) {
            return "Shell";
        }
        // любой другой камень (кроме квариарц, кэт голд и ракухи)
        // проверяем, что это не один из исключений
        if (!name.contains("quarryartz") && !name.contains("cat gold") && !name.contains("кэт голд") &&
            !name.contains("rakuh") && !name.contains("ракуха") && 
            !name.contains("shard of conch") && !name.contains("parifai") && 
            !name.contains("seashell") && !name.contains("petrifiedshell") && 
            !name.contains("petrified seashell")) {
            return "Stone";
        }
        return null;
    }

    private static double toolCoef(String toolName) {
        if (toolName == null) return 1.0;
        String n = toolName.toLowerCase();
        // кирка (приоритетно, чтобы не пересекалось с "axe")
        if (n.contains("pickaxe") || n.contains("кирк")) return 1.0;
        // тинкер топор (в т.ч. "Tinker's Throwing Axe")
        if ((n.contains("tinker") && n.contains("axe")) || (n.contains("тинкер") && n.contains("топор"))) return 0.9;
        // каменный топор
        if ((n.contains("stone") && n.contains("axe")) || (n.contains("камен") && n.contains("топор"))) return 0.8;
        return 1.0;
    }

    public enum ToolType { STONE_AXE, TINKER_AXE, PICKAXE, OTHER }

    public static boolean isKnownMiningTool(WItem w) {
        if (w == null) return false;
        String name = ((NGItem) w.item).name();
        if (name == null) return false;
        return classifyTool(name) != ToolType.OTHER || name.toLowerCase().contains("топор") || name.toLowerCase().contains("axe");
    }

    private static WItem findMiningTool() throws InterruptedException {
        if (NUtils.getEquipment() == null) return null;
        WItem l = NUtils.getEquipment().findItem(NEquipory.Slots.HAND_LEFT.idx);
        WItem r = NUtils.getEquipment().findItem(NEquipory.Slots.HAND_RIGHT.idx);
        if (isKnownMiningTool(l)) return l;
        if (isKnownMiningTool(r)) return r;
        return (l != null) ? l : r;
    }

    public static class ToolSet {
        public Double stoneAxeQ;
        public Double tinkerAxeQ;
        public Double pickaxeQ;
    }
    
    static ToolType classifyTool(String name) {
        if (name == null) return ToolType.OTHER;
        String n = name.toLowerCase();
        if (n.contains("pickaxe") || n.contains("кирк")) return ToolType.PICKAXE;
        if ((n.contains("tinker") && n.contains("axe")) || (n.contains("тинкер") && n.contains("топор"))) return ToolType.TINKER_AXE;
        if ((n.contains("stone") && n.contains("axe")) || (n.contains("камен") && n.contains("топор"))) return ToolType.STONE_AXE;
        return ToolType.OTHER;
    }
    
    public static double invDropQ(double wallQ, double f4, double f5) {
        // Если wallQ < f4, значит порода ниже качества инструмента, F3 = wallQ
        if (wallQ < f4) {
            return wallQ;
        }
        if (f5 <= 0) f5 = 1.0;
        // F3 = F4 + 0.5 * ((W-10) - (F4-10)/F5)
        return f4 + 0.5 * ((wallQ - 10.0) - (f4 - 10.0) / f5);
    }

    /**
     * Ищем инструменты в руке (как fallback), в поясе и в инвентаре.
     * Нужны только качества, берём максимальные по каждому типу.
     */
    private static ToolSet scanTools(NGameUI gui, WItem currentTool) throws InterruptedException {
        ToolSet set = new ToolSet();

        // 1) текущий инструмент (fallback)
        try {
            NGItem ci = (NGItem) currentTool.item;
            if (ci.name() != null && ci.quality != null) {
                putBest(set, classifyTool(ci.name()), (double) ci.quality);
            }
        } catch (Exception ignored) {
        }

        // 2) пояс (его инвентарь)
        try {
            WItem belt = NUtils.getEquipment().findItem(NEquipory.Slots.BELT.idx);
            if (belt != null && belt.item != null && belt.item.contents instanceof NInventory) {
                scanInventoryForTools(set, (NInventory) belt.item.contents);
            }
        } catch (Exception ignored) {
        }

        // 3) основной инвентарь
        scanInventoryForTools(set, gui.getInventory());

        return set;
    }

    private static void scanInventoryForTools(ToolSet set, NInventory inv) throws InterruptedException {
        if (inv == null) return;
        ArrayList<WItem> all = inv.getItems();
        for (WItem wi : all) {
            if (wi == null || wi.item == null) continue;
            NGItem gi = (NGItem) wi.item;
            if (gi.name() == null) continue;
            ToolType tp = classifyTool(gi.name());
            if (tp == ToolType.OTHER) continue;

            if (gi.quality == null) {
                final WItem fwi = wi;
                NUtils.addTask(new NTask() {
                    { this.maxCounter = 120; }
                    @Override
                    public boolean check() {
                        NGItem g = (NGItem) fwi.item;
                        return g.name() != null && g.quality != null;
                    }
                });
            }
            if (gi.quality != null) {
                putBest(set, tp, (double) gi.quality);
            }
        }
    }

    private static void putBest(ToolSet set, ToolType tp, double q) {
        switch (tp) {
            case STONE_AXE:
                if (set.stoneAxeQ == null || q > set.stoneAxeQ) set.stoneAxeQ = q;
                break;
            case TINKER_AXE:
                if (set.tinkerAxeQ == null || q > set.tinkerAxeQ) set.tinkerAxeQ = q;
                break;
            case PICKAXE:
                if (set.pickaxeQ == null || q > set.pickaxeQ) set.pickaxeQ = q;
                break;
            case OTHER:
            default:
                break;
        }
    }

    /**
     * Добавляет метку на карту для квариарца
     * Ставит четко в месте выкопан, всегда ставится при выкапывании квариарца
     * Не склеивается с другими маркерами (не обновляет существующие)
     */
    private void addQuarryartzMarker(NGameUI gui, String stoneName, double wallQ) {
        try {
            if (gui.mmap == null || gui.mmap.sessloc == null || gui.labeledMarkService == null) return;
            
            Gob player = NUtils.player();
            if (player == null) return;
            
            // Получаем позицию игрока и направление копания
            // Ставим четко в месте выкопан (с учетом направления копания)
            Coord2d playerPos = player.rc;
            double angle = player.a; // угол направления игрока
            
            // Смещение в направлении копания (примерно 13.75 тайлов, как в MiningSafetyAssistant)
            Coord2d minedTile = new Coord2d(
                playerPos.x + (Math.cos(angle) * 13.75),
                playerPos.y + (Math.sin(angle) * 13.75)
            );
            
            // Получаем segment ID и tile coordinates
            long segmentId = gui.mmap.sessloc.seg.id;
            Coord tileCoords = minedTile.floor(MCache.tilesz).add(gui.mmap.sessloc.tc);
            
            // Проверяем, есть ли уже маркер квариарца рядом (в радиусе 2 тайлов)
            // Если есть - не ставим новый (квариарц ставится четко в месте выкопан, не склеивается)
            java.util.List<nurgling.widgets.LabeledMinimapMark> existingMarks = 
                gui.labeledMarkService.getMarksByResourceType("Quarryartz");
            boolean nearbyMarkExists = false;
            for (nurgling.widgets.LabeledMinimapMark mark : existingMarks) {
                if (mark.segmentId == segmentId) {
                    int distX = Math.abs(mark.tileCoords.x - tileCoords.x);
                    int distY = Math.abs(mark.tileCoords.y - tileCoords.y);
                    if (distX <= 2 && distY <= 2) {
                        nearbyMarkExists = true;
                        break;
                    }
                }
            }
            
            if (!nearbyMarkExists) {
                // Создаем метку (например, "q101")
                String label = String.format("q%.0f", wallQ);
                
                // Получаем иконку квариарца из ресурсов игры
                BufferedImage iconImage = getQuarryartzIcon();
                
                // Добавляем метку через сервис (обрабатывает персистентность)
                gui.labeledMarkService.addLabeledMark(label, "Quarryartz", segmentId, tileCoords, iconImage);
                
                // Воспроизводим звук при выпадении квариарца
                playQuarryartzSound(gui);
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
    }
    
    /**
     * Воспроизводит приятный звук при выпадении квариарца
     */
    private void playQuarryartzSound(NGameUI gui) {
        try {
            if (gui == null || gui.ui == null) return;
            
            // Используем приятный звук из встроенных ресурсов игры
            // Пробуем несколько вариантов звуков в порядке приоритета
            String[] soundPaths = {
                "sfx/msg",              // Приятный звук сообщения (надежный)
                "sfx/fx/ore",           // Звук руды
                "sfx/fx/stone",         // Звук камня
                "sfx/fx/water"          // Звук воды (приятный)
            };
            
            for (String soundPath : soundPaths) {
                try {
                    // Используем local ресурсы для надежности
                    Resource soundRes = Resource.local().loadwait(soundPath);
                    if (soundRes != null) {
                        // Воспроизводим через ui.sfx() - это надежный способ
                        gui.ui.sfx(soundRes);
                        break; // Воспроизвели успешно, выходим
                    }
                } catch (Exception ignored) {
                    // Пробуем следующий звук
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки воспроизведения звука
        }
    }
    
    /**
     * Добавляет или обновляет метку спота руды на карте
     * Обновляет маркер в радиусе 30 клеток только если качество выше
     * Не заменяет другие руды или камни (проверяет resourceType)
     */
    private void addOreSpotMarker(NGameUI gui, NGItem oreItem, String oreName, double wallQ) {
        try {
            if (gui.mmap == null || gui.mmap.sessloc == null || gui.labeledMarkService == null) {
                return;
            }
            
            Gob player = NUtils.player();
            if (player == null) {
                return;
            }
            
            // Получаем позицию игрока и направление копания
            Coord2d playerPos = player.rc;
            double angle = player.a; // угол направления игрока
            
            // Смещение в направлении копания (примерно 13.75 тайлов)
            Coord2d minedTile = new Coord2d(
                playerPos.x + (Math.cos(angle) * 13.75),
                playerPos.y + (Math.sin(angle) * 13.75)
            );
            
            // Получаем segment ID и tile coordinates
            long segmentId = gui.mmap.sessloc.seg.id;
            Coord tileCoords = minedTile.floor(MCache.tilesz).add(gui.mmap.sessloc.tc);
            
            // Ищем существующий маркер ТОЛЬКО этой же руды/камня в радиусе 30 клеток
            // Важно: проверяем resourceType, чтобы не заменять другие руды или камни
            // И НЕ трогаем маркеры квариарца (они в отдельном слое)
            java.util.List<nurgling.widgets.LabeledMinimapMark> existingMarks = 
                gui.labeledMarkService.getMarksByResourceType(oreName);
            
            nurgling.widgets.LabeledMinimapMark nearbyMark = null;
            for (nurgling.widgets.LabeledMinimapMark mark : existingMarks) {
                // Проверяем, что это именно та же руда/камень (не другая руда или камень)
                // И что это НЕ квариарц (квариарц в отдельном слое)
                if (mark.segmentId == segmentId && oreName.equals(mark.resourceType) && !"Quarryartz".equals(mark.resourceType)) {
                    int distX = Math.abs(mark.tileCoords.x - tileCoords.x);
                    int distY = Math.abs(mark.tileCoords.y - tileCoords.y);
                    // Проверяем радиус 30 тайлов
                    if (distX <= 30 && distY <= 30) {
                        nearbyMark = mark;
                        break;
                    }
                }
            }
            
            // Убрали проверку на другие типы маркеров - теперь все маркеры могут сосуществовать
            if (nearbyMark != null) {
                // Маркер найден рядом - проверяем качество
                // Обновляем ТОЛЬКО если выкопал выше
                try {
                    // Парсим качество из метки (формат "q130")
                    double existingQ = 0;
                    if (nearbyMark.label != null && nearbyMark.label.startsWith("q")) {
                        existingQ = Double.parseDouble(nearbyMark.label.substring(1).trim());
                    }
                    
                    // Если новое качество выше - обновляем маркер
                    if (wallQ > existingQ) {
                        // Удаляем старый маркер
                        gui.labeledMarkService.removeMark(nearbyMark);
                        
                        // Создаем новый с обновленным качеством
                        String label = String.format("q%.0f", wallQ);
                        BufferedImage iconImage = getOreIconFromItem(oreItem, oreName);
                        // Создаем маркер даже если иконка не загрузилась (используется fallback)
                        gui.labeledMarkService.addLabeledMark(label, oreName, segmentId, tileCoords, iconImage);
                    }
                    // Если качество не выше - не трогаем маркер
                } catch (Exception e) {
                    // Игнорируем ошибки парсинга
                }
            } else {
                // Маркер не найден - создаем новый
                String label = String.format("q%.0f", wallQ);
                BufferedImage iconImage = getOreIconFromItem(oreItem, oreName);
                // Создаем маркер даже если иконка не загрузилась (используется fallback)
                gui.labeledMarkService.addLabeledMark(label, oreName, segmentId, tileCoords, iconImage);
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
    }
    
    /**
     * Получает иконку руды из самого предмета (приоритет) или из ресурсов игры (fallback)
     * Пытается несколько раз получить спрайт, так как он может быть еще не загружен
     */
    public static BufferedImage getOreIconFromItem(NGItem oreItem, String oreName) {
        if (oreItem == null) {
            return getOreIcon(oreName);
        }
        
        // ПЕРВЫЙ ПРИОРИТЕТ: Пробуем получить изображение через спрайт предмета
        // Используем spr() который пытается создать спрайт если его нет
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                GSprite spr = oreItem.spr();
                if (spr != null) {
                    // Используем ItemTex.sprimg для получения изображения из спрайта
                    BufferedImage sprImg = ItemTex.sprimg(spr);
                    if (sprImg != null) {
                        return sprImg;
                    }
                    
                    // Альтернативный способ: если спрайт реализует ImageSprite
                    if (spr instanceof GSprite.ImageSprite) {
                        BufferedImage img = ((GSprite.ImageSprite) spr).image();
                        if (img != null) {
                            return img;
                        }
                    }
                }
            } catch (Loading e) {
                // Ресурс еще загружается - попробуем еще раз после небольшой задержки
                if (attempt < 2) {
                    try {
                        Thread.sleep(50); // Небольшая задержка 50мс
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
            } catch (Exception e) {
                // Игнорируем другие ошибки и пробуем следующий способ
                break;
            }
        }
        
        // ВТОРОЙ ПРИОРИТЕТ: Пробуем создать спрайт из ресурса напрямую
        try {
            if (oreItem.res != null && oreItem.res.isReady() && oreItem.sdt != null) {
                Resource res = oreItem.res.get();
                if (res != null) {
                    // Создаем спрайт из ресурса
                    GSprite spr = GSprite.create(oreItem, res, oreItem.sdt.clone());
                    if (spr != null) {
                        BufferedImage sprImg = ItemTex.sprimg(spr);
                        if (sprImg != null) {
                            return sprImg;
                        }
                        
                        if (spr instanceof GSprite.ImageSprite) {
                            BufferedImage img = ((GSprite.ImageSprite) spr).image();
                            if (img != null) {
                                return img;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        
        // ТРЕТИЙ ПРИОРИТЕТ: Fallback - пробуем загрузить напрямую из ресурсов (старый способ)
        return getOreIcon(oreName);
    }
    
    /**
     * Получает иконку руды из ресурсов игры (fallback метод)
     */
    public static BufferedImage getOreIcon(String oreName) {
        if (oreName == null) return null;
        
        // Специальная обработка для Wine Glance - используем правильный путь
        if (oreName.equalsIgnoreCase("Wine Glance")) {
            try {
                Resource res = Resource.remote().loadwait("gfx/invobjs/wineglance");
                return res.layer(Resource.imgc).img;
            } catch (Exception e) {
                // Если не удалось, пробуем cuprite как fallback
                try {
                    Resource res = Resource.remote().loadwait("gfx/invobjs/cuprite");
                    return res.layer(Resource.imgc).img;
                } catch (Exception e2) {
                    return null;
                }
            }
        }
        
        // Нормализуем название: убираем пробелы и приводим к нижнему регистру
        String normalized = oreName.toLowerCase().replaceAll("\\s+", "");
        String original = oreName.toLowerCase();
        
        // Пробуем различные пути к иконке (как в NMiniMap.tryLoadProspectingIcon)
        String[] possiblePaths = {
            "gfx/invobjs/" + normalized,  // Сначала пробуем нормализованное (без пробелов)
            "gfx/invobjs/" + original,    // Затем с оригинальным названием
            "gfx/invobjs/ore-" + normalized,
            "gfx/invobjs/ore-" + original,
            "gfx/invobjs/stone-" + normalized,
            "gfx/invobjs/stone-" + original,
            "gfx/tiles/rocks/" + normalized,
            "gfx/tiles/rocks/" + original,
            "gfx/terobjs/bumblings/" + normalized,
            "gfx/terobjs/bumblings/" + original
        };
        
        for (String path : possiblePaths) {
            try {
                Resource res = Resource.remote().loadwait(path);
                return res.layer(Resource.imgc).img;
            } catch (Exception e) {
                // Пробуем следующий путь
                continue;
            }
        }
        
        // Если не удалось загрузить - возвращаем null (будет использован fallback)
        return null;
    }
    
    /**
     * Добавляет метку на карту для драгоценного камня
     * Ставит с фактическим качеством (f3), без применения формул
     * Использует систему спотов (обновление в радиусе 30 клеток)
     * Использует иконку самого предмета
     */
    private void addGemstoneMarker(NGameUI gui, NGItem gemItem, String gemName, double quality) {
        try {
            if (gui.mmap == null || gui.mmap.sessloc == null || gui.labeledMarkService == null) {
                return;
            }
            
            Gob player = NUtils.player();
            if (player == null) {
                return;
            }
            
            // Получаем позицию игрока и направление копания
            Coord2d playerPos = player.rc;
            double angle = player.a; // угол направления игрока
            
            // Смещение в направлении копания (примерно 13.75 тайлов)
            Coord2d minedTile = new Coord2d(
                playerPos.x + (Math.cos(angle) * 13.75),
                playerPos.y + (Math.sin(angle) * 13.75)
            );
            
            // Получаем segment ID и tile coordinates
            long segmentId = gui.mmap.sessloc.seg.id;
            Coord tileCoords = minedTile.floor(MCache.tilesz).add(gui.mmap.sessloc.tc);
            
            // Драгоценные камни ставятся как квариарц - четко в месте выкопан, без системы спотов
            // Проверяем, есть ли уже маркер этого же драгоценного камня рядом (в радиусе 2 тайлов)
            // Если есть - не ставим новый (как квариарц, не склеивается)
            java.util.List<nurgling.widgets.LabeledMinimapMark> existingMarks = 
                gui.labeledMarkService.getMarksByResourceType(gemName);
            
            boolean nearbyMarkExists = false;
            for (nurgling.widgets.LabeledMinimapMark mark : existingMarks) {
                // Проверяем, что это именно тот же драгоценный камень
                if (mark.segmentId == segmentId && gemName.equals(mark.resourceType)) {
                    int distX = Math.abs(mark.tileCoords.x - tileCoords.x);
                    int distY = Math.abs(mark.tileCoords.y - tileCoords.y);
                    // Проверяем радиус 2 тайла (как квариарц)
                    if (distX <= 2 && distY <= 2) {
                        nearbyMarkExists = true;
                        break;
                    }
                }
            }
            
            if (!nearbyMarkExists) {
                // Создаем новый маркер (как квариарц - четко в месте выкопан)
                String label = String.format("q%.0f", quality);
                BufferedImage iconImage = getGemstoneIconFromItem(gemItem);
                // Создаем маркер даже если иконка не загрузилась (используется fallback)
                gui.labeledMarkService.addLabeledMark(label, gemName, segmentId, tileCoords, iconImage);
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
    }
    
    /**
     * Получает иконку драгоценного камня из самого предмета
     */
    public static BufferedImage getGemstoneIconFromItem(NGItem gemItem) {
        if (gemItem == null) {
            return null;
        }
        
        // ПЕРВЫЙ ПРИОРИТЕТ: Пробуем получить изображение через спрайт предмета
        // Используем spr() который пытается создать спрайт если его нет
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                GSprite spr = gemItem.spr();
                if (spr != null) {
                    // Используем ItemTex.sprimg для получения изображения из спрайта
                    BufferedImage sprImg = ItemTex.sprimg(spr);
                    if (sprImg != null) {
                        return sprImg;
                    }
                    
                    // Альтернативный способ: если спрайт реализует ImageSprite
                    if (spr instanceof GSprite.ImageSprite) {
                        BufferedImage img = ((GSprite.ImageSprite) spr).image();
                        if (img != null) {
                            return img;
                        }
                    }
                }
            } catch (Loading e) {
                // Ресурс еще загружается - попробуем еще раз после небольшой задержки
                if (attempt < 2) {
                    try {
                        Thread.sleep(50); // Небольшая задержка 50мс
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
            } catch (Exception e) {
                // Игнорируем другие ошибки и пробуем следующий способ
                break;
            }
        }
        
        // ВТОРОЙ ПРИОРИТЕТ: Пробуем создать спрайт из ресурса напрямую
        try {
            if (gemItem.res != null && gemItem.res.isReady() && gemItem.sdt != null) {
                Resource res = gemItem.res.get();
                if (res != null) {
                    // Создаем спрайт из ресурса
                    GSprite spr = GSprite.create(gemItem, res, gemItem.sdt.clone());
                    if (spr != null) {
                        BufferedImage sprImg = ItemTex.sprimg(spr);
                        if (sprImg != null) {
                            return sprImg;
                        }
                        
                        if (spr instanceof GSprite.ImageSprite) {
                            BufferedImage img = ((GSprite.ImageSprite) spr).image();
                            if (img != null) {
                                return img;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        
        // ТРЕТИЙ ПРИОРИТЕТ: Пробуем получить имя ресурса и загрузить его напрямую
        try {
            Resource resFromGetres = gemItem.getres();
            if (resFromGetres != null && resFromGetres.name != null) {
                String resourceName = resFromGetres.name;
                
                // Пробуем загрузить ресурс по имени напрямую
                try {
                    Resource res = Resource.remote().loadwait(resourceName);
                    if (res != null) {
                        Resource.Image imgLayer = res.layer(Resource.imgc);
                        if (imgLayer != null && imgLayer.img != null) {
                            return imgLayer.img;
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
                
                // Пробуем получить иконку напрямую из getres()
                try {
                    Resource.Image imgLayer = resFromGetres.layer(Resource.imgc);
                    if (imgLayer != null && imgLayer.img != null) {
                        return imgLayer.img;
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
            }
            
            // Пробуем через res.get()
            if (gemItem.res != null) {
                try {
                    if (gemItem.res.isReady()) {
                        Resource res = gemItem.res.get();
                        if (res != null) {
                            Resource.Image imgLayer = res.layer(Resource.imgc);
                            if (imgLayer != null && imgLayer.img != null) {
                                return imgLayer.img;
                            }
                        }
                    }
                } catch (Loading e) {
                    // Ресурс еще загружается
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        
        // Если не удалось загрузить - возвращаем null (будет использован fallback)
        return null;
    }
    
    /**
     * Получает иконку драгоценного камня (огранка бриллиант размера йотун)
     * Fallback метод для случаев, когда нет доступа к самому предмету
     */
    public static BufferedImage getGemstoneIcon(String gemName) {
        if (gemName == null) return null;
        
        // Пробуем найти иконку огранки бриллиант размера йотун
        // Путь может быть: gfx/invobjs/gems/cut-diamond-jotun или подобный
        String[] possiblePaths = {
            "gfx/invobjs/gems/cut-diamond-jotun",
            "gfx/invobjs/gems/diamond-cut-jotun",
            "gfx/invobjs/gems/jotun-cut-diamond",
            "gfx/invobjs/gems/cut-diamond",
            "gfx/invobjs/gems/diamond",
            "gfx/invobjs/gems/gemstone"
        };
        
        for (String path : possiblePaths) {
            try {
                Resource res = Resource.remote().loadwait(path);
                return res.layer(Resource.imgc).img;
            } catch (Exception e) {
                // Пробуем следующий путь
                continue;
            }
        }
        
        // Если не удалось загрузить - возвращаем null (будет использован fallback)
        return null;
    }
    
    /**
     * Получает иконку квариарца из ресурсов игры
     */
    public static BufferedImage getQuarryartzIcon() {
        try {
            // Пытаемся загрузить иконку квариарца из ресурсов (правильный путь с двумя 'q')
            Resource res = Resource.remote().loadwait("gfx/invobjs/quarryquartz");
            return res.layer(Resource.imgc).img;
        } catch (Exception e) {
            // Если не удалось, пробуем альтернативные пути
            try {
                Resource res = Resource.remote().loadwait("gfx/invobjs/quarryartz");
                return res.layer(Resource.imgc).img;
            } catch (Exception e2) {
                try {
                    Resource res = Resource.remote().loadwait("gfx/invobjs/stone");
                    return res.layer(Resource.imgc).img;
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }
}

