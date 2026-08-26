package nurgling.actions.bots;

import haven.*;
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
import nurgling.tools.VSpec;
import nurgling.NInventory;
import nurgling.widgets.NEquipory;
import nurgling.widgets.bots.MasterMinerWnd;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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
    
    private static final AtomicReference<ExecutorService> markerExecutorRef = new AtomicReference<>(createMarkerExecutor());
    private static final AtomicReference<ExecutorService> iconLoaderExecutorRef = new AtomicReference<>(createIconLoaderExecutor());

    private static ExecutorService createMarkerExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MasterMiner-MarkerCreator");
            t.setDaemon(true);
            return t;
        });
    }

    private static ExecutorService createIconLoaderExecutor() {
        return Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "MasterMiner-IconLoader");
            t.setDaemon(true);
            return t;
        });
    }

    private static ExecutorService markerExecutor() {
        return markerExecutorRef.get();
    }

    private static ExecutorService iconLoaderExecutor() {
        return iconLoaderExecutorRef.get();
    }

    public static void resetExecutors() {
        ExecutorService oldMarker = markerExecutorRef.getAndSet(createMarkerExecutor());
        if (oldMarker != null) oldMarker.shutdownNow();
        ExecutorService oldIcon = iconLoaderExecutorRef.getAndSet(createIconLoaderExecutor());
        if (oldIcon != null) oldIcon.shutdownNow();
        oreIconCache.clear();
    }
    
    // Кэш для иконок руд, чтобы не загружать их каждый раз
    private static final ConcurrentHashMap<String, BufferedImage> oreIconCache = new ConcurrentHashMap<>();
    
    // Система батчинга для маркеров - собирает камни за период и обрабатывает одной задачей
    private static class MarkerBatch {
        final String oreName;
        final NGItem item;
        final double wallQ;
        final Coord tileCoords;
        final long segmentId;
        final String markerType; // "ore", "gem", "quarryartz"
        
        MarkerBatch(String oreName, NGItem item, double wallQ, Coord tileCoords, long segmentId, String markerType) {
            this.oreName = oreName;
            this.item = item;
            this.wallQ = wallQ;
            this.tileCoords = tileCoords;
            this.segmentId = segmentId;
            this.markerType = markerType;
        }
        
        // Ключ для группировки: тип + координаты
        String getGroupKey() {
            return markerType + ":" + oreName + ":" + segmentId + ":" + tileCoords.x + "," + tileCoords.y;
        }
    }
    
    // Очередь батчинга для маркеров
    private final List<MarkerBatch> markerBatchQueue = new ArrayList<>();
    private volatile long lastBatchProcessTime = 0;
    private static final long BATCH_DELAY_MS = 1000; // Увеличена задержка для сбора большего количества камней (1000мс = 1 секунда)
    private final Object batchLock = new Object();

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        // сброс состояния для повторного запуска
        stop = false;
        known.clear();
        wnd = NUtils.addCentered(gui, new MasterMinerWnd());

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
            ArrayList<WItem> allItems = collectAllWItemsFromWidget(gui.getInventory());
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

                // Получаем все предметы из инвентаря рекурсивно (getItems() смотрит только child/next и может не видеть слоты)
                allItems = collectAllWItemsFromWidget(gui.getInventory());
                allItems = addItemsFromStacks(gui.getInventory(), allItems);
                ArrayList<WItem> cur = filterMinedItems(allItems);
                
                // Обрабатываем все новые камни, а не только первый
                ArrayList<WItem> newItems = new ArrayList<>();
                for (WItem it : cur) {
                    if (!known.contains(it)) {
                        newItems.add(it);
                    }
                }
                
                // Также проверяем ВСЕ стаки в инвентаре каждый цикл (они могут обновляться без появления новых предметов)
                ArrayList<WItem> stacksToCheck = new ArrayList<>();
                for (WItem it : cur) {
                    try {
                        NGItem ngItem = (NGItem) it.item;
                        haven.GItem.Amount amount = ngItem.getInfo(haven.GItem.Amount.class);
                        if (amount != null && amount.itemnum() > 1) {
                            stacksToCheck.add(it);
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки
                    }
                }

                // Сколько камней можно сбросить: всего - сколько оставляем для подпорки (в т.ч. в руках)
                int totalStones = countTotalStones(cur);
                WItem vhandItem = gui.vhand;
                if (vhandItem != null && vhandItem.item instanceof NGItem) {
                    NGItem vhandNGItem = (NGItem) vhandItem.item;
                    String vhandName = vhandNGItem.name();
                    if (vhandName != null && !isGemstone(vhandNGItem) && !isGemstone(vhandName) &&
                        (NParser.checkName(vhandName, MINED_ITEMS) || NParser.checkName(vhandName, ORE_ITEMS))) {
                        String vhandStoneType = classifyStoneType(vhandName);
                        if (!"Shell".equals(vhandStoneType) && !"Cat Gold".equals(vhandStoneType)) {
                            haven.GItem.Amount vhAm = vhandNGItem.getInfo(haven.GItem.Amount.class);
                            totalStones += (vhAm != null && vhAm.itemnum() > 0) ? vhAm.itemnum() : 1;
                        }
                    }
                }
                int keepStones = wnd.getKeepStonesForSupport();
                int[] needToDropRef = new int[] { Math.max(0, totalStones - keepStones) };

                // Проверяем предмет в руках (vhand) — камень/руда может попасть туда, если инвентарь полон
                if (vhandItem != null && vhandItem.item instanceof NGItem) {
                    NGItem vhandNGItem = (NGItem) vhandItem.item;
                    String vhandName = vhandNGItem.name();
                    if (vhandName != null) {
                        boolean isMinedItem = NParser.checkName(vhandName, MINED_ITEMS) || 
                                             NParser.checkName(vhandName, ORE_ITEMS) ||
                                             isGemstone(vhandNGItem) || 
                                             isGemstone(vhandName);
                        if (isMinedItem && !known.contains(vhandItem)) {
                            try {
                                processNewStone(gui, vhandItem, wnd, needToDropRef);
                                known.add(vhandItem);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                
                if (newItems.isEmpty() && stacksToCheck.isEmpty()) {
                    NUtils.addTask(new WaitTicks(5));
                    continue;
                }
                known = cur;
                
                // Сначала стаки — иначе лимит сброса забирают одиночные и стаки не трогаем
                for (WItem stackItem : stacksToCheck) {
                    try {
                        checkAndDropStack(gui, stackItem, wnd, needToDropRef);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                // Затем одиночные/новые камни
                for (WItem newItem : newItems) {
                    processNewStone(gui, newItem, wnd, needToDropRef);
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
     * Получает МАКСИМАЛЬНОЕ качество в стаке (для решения о выбрасывании).
     * Стак может быть: 1) виджет ItemStack (несколько GItem); 2) один слот с Amount > 1 (parent = NInventory).
     */
    private double getMaxStackQuality(NGItem item, WItem wItem) {
        if (item == null) return -1;
        
        try {
            haven.GItem.Amount amount = item.getInfo(haven.GItem.Amount.class);
            if (amount != null && amount.itemnum() > 1) {
                // Стак в виде виджета ItemStack (несколько GItem)
                if (wItem != null && wItem.parent instanceof haven.res.ui.stackinv.ItemStack) {
                    haven.res.ui.stackinv.ItemStack itemStack = (haven.res.ui.stackinv.ItemStack) wItem.parent;
                    double maxQuality = -1;
                    for (WItem w : itemStack.wmap.values()) {
                        if (w.item instanceof NGItem) {
                            NGItem ngItem = (NGItem) w.item;
                            if (ngItem.quality != null && ngItem.quality > maxQuality) {
                                maxQuality = ngItem.quality;
                            }
                        }
                    }
                    if (maxQuality > 0) return maxQuality;
                }
                
                // Стак в одном слоте (один GItem с Amount > 1, parent = NInventory): Stack info или quality предмета
                haven.res.ui.tt.stackn.Stack stackInfo = item.getInfo(haven.res.ui.tt.stackn.Stack.class);
                if (stackInfo != null && stackInfo.quality > 0) {
                    return stackInfo.quality;
                }
                if (item.quality != null) {
                    return item.quality;
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        
        if (item.quality != null) {
            return item.quality;
        }
        return -1;
    }
    
    /** Проверяет, что предмет находится в главном инвентаре (в т.ч. внутри стака — parent может быть ItemStack). */
    private boolean isInMainInventory(NGameUI gui, WItem witem) {
        if (witem == null || gui == null) return false;
        if (witem == gui.vhand) return true;
        for (Widget w = witem; w != null; w = w.parent) {
            if (w == gui.getInventory()) return true;
        }
        return false;
    }

    /**
     * Рекурсивно собирает все WItem из виджета (инвентаря). GetItems() обходит только child/next,
     * в NInventory слоты могут быть вложены — без рекурсии камни не находятся.
     */
    private ArrayList<WItem> collectAllWItemsFromWidget(Widget w) {
        ArrayList<WItem> out = new ArrayList<>();
        collectAllWItemsRecur(w, out);
        return out;
    }

    private void collectAllWItemsRecur(Widget w, ArrayList<WItem> out) {
        if (w == null) return;
        if (w instanceof WItem) {
            WItem wi = (WItem) w;
            if (wi.item != null && !out.contains(wi)) out.add(wi);
        }
        for (Widget ch = w.child; ch != null; ch = ch.next) {
            collectAllWItemsRecur(ch, out);
        }
    }

    /**
     * Добавляет в список WItem'ы из виджетов ItemStack (стаки типа "Gneiss, stack of 3").
     */
    private ArrayList<WItem> addItemsFromStacks(Widget inv, ArrayList<WItem> list) {
        if (inv == null || list == null) return list;
        ArrayList<WItem> out = new ArrayList<>(list);
        collectWItemsFromStacks(inv, out);
        return out;
    }

    private void collectWItemsFromStacks(Widget w, ArrayList<WItem> out) {
        if (w == null) return;
        if (w instanceof haven.res.ui.stackinv.ItemStack) {
            for (WItem wi : ((haven.res.ui.stackinv.ItemStack) w).wmap.values()) {
                if (wi != null && wi.item != null && !out.contains(wi)) out.add(wi);
            }
            return;
        }
        for (Widget ch = w.child; ch != null; ch = ch.next) {
            collectWItemsFromStacks(ch, out);
        }
    }

    /**
     * Проверяет стак и сбрасывает камни по одному, если МАКСИМАЛЬНОЕ качество стака ниже порога.
     * Сбрасывает не весь стак, а по 1 шт., с учётом лимита needToDropRef (держать N для подпорки).
     */
    private void checkAndDropStack(NGameUI gui, WItem stackItem, MasterMinerWnd wnd, int[] needToDropRef) throws InterruptedException {
        if (stackItem == null || stackItem.item == null || !(stackItem.item instanceof NGItem) ||
            needToDropRef == null || needToDropRef[0] <= 0) {
            return;
        }
        
        NGItem ngItem = (NGItem) stackItem.item;
        String itemName = ngItem.name();
        if (itemName == null) return;
        
        boolean isMinedItem = NParser.checkName(itemName, MINED_ITEMS) || NParser.checkName(itemName, ORE_ITEMS);
        if (isGemstone(ngItem) || isGemstone(itemName) || !isMinedItem) return;
        
        double maxQ = -1;
        for (int attempt = 0; attempt < 5; attempt++) {
            maxQ = getMaxStackQuality(ngItem, stackItem);
            if (maxQ >= 0) break;
            if (attempt < 4) NUtils.addTask(new WaitTicks(2));
        }
        // Если качество известно — сбрасываем только если ниже порога
        if (maxQ >= 0) {
            String stoneType = classifyStoneType(itemName);
            double threshold = "Shell".equals(stoneType) || "Cat Gold".equals(stoneType)
                ? wnd.getShellCatGoldThreshold() : wnd.getDropThreshold();
            if (!Double.isNaN(threshold) && maxQ >= threshold) return;
        }
        // maxQ < 0: качество у стака часто недоступно — всё равно сбрасываем лишнее сверх лимита «держать N»
        
        // Предмет может быть внутри стака: parent = ItemStack, а не инвентарь
        boolean isInInventory = isInMainInventory(gui, stackItem);
        boolean isInHand = (stackItem == gui.vhand);
        if (!isInInventory && !isInHand) return;
        String itemNameLower = itemName.toLowerCase();
        if (itemNameLower.contains("axe") || itemNameLower.contains("pickaxe") || 
            itemNameLower.contains("топор") || itemNameLower.contains("кирк")) return;
        
        haven.GItem.Amount amount = ngItem.getInfo(haven.GItem.Amount.class);
        int stackSize = (amount != null && amount.itemnum() > 0) ? amount.itemnum() : 1;
        int toDrop = Math.min(stackSize, needToDropRef[0]);
        for (int i = 0; i < toDrop; i++) {
            if (needToDropRef[0] <= 0) break;
            if (!isInMainInventory(gui, stackItem) && stackItem != gui.vhand) break;
            if (stackItem.item == null) break;
            NUtils.addTask(new WaitTicks(3));
            if (isInMainInventory(gui, stackItem) || stackItem == gui.vhand) {
                NUtils.dropOne(stackItem);
                needToDropRef[0]--;
            }
        }
    }
    
    /**
     * Считает общее количество камней (включая стаки) в списке — обычные камни и руды, без драгоценных.
     * Используется для ограничения «держать N камней для подпорки».
     */
    private int countTotalStones(ArrayList<WItem> items) {
        if (items == null) return 0;
        int total = 0;
        for (WItem w : items) {
            if (w == null || w.item == null || !(w.item instanceof NGItem)) continue;
            NGItem ng = (NGItem) w.item;
            String name = ng.name();
            if (name == null) continue;
            if (isGemstone(ng) || isGemstone(name)) continue;
            if (!NParser.checkName(name, MINED_ITEMS) && !NParser.checkName(name, ORE_ITEMS)) continue;
            String stoneType = classifyStoneType(name);
            if ("Shell".equals(stoneType) || "Cat Gold".equals(stoneType)) continue;
            haven.GItem.Amount amount = ng.getInfo(haven.GItem.Amount.class);
            total += (amount != null && amount.itemnum() > 0) ? amount.itemnum() : 1;
        }
        return total;
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
     * Обрабатывает один новый камень.
     * needToDropRef[0] — сколько ещё камней можно сбросить (с учётом лимита «держать N для подпорки»).
     */
    private void processNewStone(NGameUI gui, WItem newItem, MasterMinerWnd wnd, int[] needToDropRef) throws InterruptedException {
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
                
                // Если enabled == null, используем значение по умолчанию (драгоценные камни включены)
                boolean shouldMark = false;
                if (enabled == null) {
                    // По умолчанию драгоценные камни включены
                    shouldMark = true;
                } else {
                    // Используем явное значение из настроек
                    shouldMark = enabled;
                }
                
                if (shouldMark) {
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
                // Если камень ниже инструмента - качество в стене равно качеству камня
                if (f3 < f4) {
                    wallQ = f3;
                } else {
                    // Новая формула для квариарца: f3 - f4 + f3 = 2*f3 - f4
                    // wallQ - это качество в стене, оно одинаково для всех инструментов
                    wallQ = (2.0 * f3) - f4;
                }
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
                    // Если качество в стене ниже инструмента - предсказанное качество равно wallQ
                    if (wallQ < set.stoneAxeQ) {
                        pred = wallQ;
                    } else {
                        // Для квариарца: обратная формула от wallQ = 2*f3 - f4
                        // Если wallQ = 2*f3_new - f4_new, то f3_new = (wallQ + f4_new) / 2
                        // wallQ одинаково для всех инструментов, поэтому используем то же wallQ
                        pred = (wallQ + set.stoneAxeQ) / 2.0;
                    }
                } else {
                    pred = invDropQ(wallQ, set.stoneAxeQ, 0.8);
                }
                if (bestAltQ == null || (pred != null && pred > bestAltQ)) bestAltQ = pred;
            }
            if (currentToolType != ToolType.TINKER_AXE && set.tinkerAxeQ != null) {
                Double pred;
                if ("Quarryartz".equals(stoneType)) {
                    // Если качество в стене ниже инструмента - предсказанное качество равно wallQ
                    if (wallQ < set.tinkerAxeQ) {
                        pred = wallQ;
                    } else {
                        // Для квариарца: обратная формула от wallQ = 2*f3 - f4
                        // Если wallQ = 2*f3_new - f4_new, то f3_new = (wallQ + f4_new) / 2
                        // wallQ одинаково для всех инструментов, поэтому используем то же wallQ
                        pred = (wallQ + set.tinkerAxeQ) / 2.0;
                    }
                } else {
                    pred = invDropQ(wallQ, set.tinkerAxeQ, 0.9);
                }
                if (bestAltQ == null || (pred != null && pred > bestAltQ)) bestAltQ = pred;
            }
            if (currentToolType != ToolType.PICKAXE && set.pickaxeQ != null) {
                Double pred;
                if ("Quarryartz".equals(stoneType)) {
                    // Если качество в стене ниже инструмента - предсказанное качество равно wallQ
                    if (wallQ < set.pickaxeQ) {
                        pred = wallQ;
                    } else {
                        // Для квариарца: обратная формула от wallQ = 2*f3 - f4
                        // Если wallQ = 2*f3_new - f4_new, то f3_new = (wallQ + f4_new) / 2
                        // wallQ одинаково для всех инструментов, поэтому используем то же wallQ
                        pred = (wallQ + set.pickaxeQ) / 2.0;
                    }
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
                    
                    // Определяем, является ли это рудой, Quarryartz или драгоценным камнем для значений по умолчанию
                    boolean isOre = isOre(stoneName) || 
                                   stoneName.equals("Black Coal") || 
                                   stoneName.equals("Quartz") || 
                                   stoneName.equals("Flint");
                    boolean isQuarryartz = "Quarryartz".equals(stoneType);
                    // isGem уже определена выше в методе
                    
                    // Если enabled == null, используем значения по умолчанию (руды, Quarryartz и драгоценные камни включены)
                    boolean shouldMark = false;
                    if (enabled == null) {
                        // По умолчанию руды, Quarryartz и драгоценные камни включены
                        shouldMark = isOre || isQuarryartz || isGem;
                    } else {
                        // Используем явное значение из настроек
                        shouldMark = enabled;
                    }
                    
                    // Если элемент включен в настройках и качество в стене >= порога
                    if (shouldMark) {
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
            
            // Сброс только если разрешено лимитом «держать N камней» и качество ниже порога
            if (needToDropRef != null && needToDropRef[0] > 0 && !Double.isNaN(threshold) && f3 < threshold) {
                boolean isInInventory = (newItem != null && isInMainInventory(gui, newItem));
                boolean isInHand = (newItem != null && newItem == gui.vhand);
                if (isInInventory || isInHand) {
                    String itemName = stoneName != null ? stoneName.toLowerCase() : "";
                    boolean isTool = itemName.contains("axe") || itemName.contains("pickaxe") || 
                                   itemName.contains("топор") || itemName.contains("кирк");
                    if (!isTool) {
                        NUtils.addTask(new WaitTicks(3));
                        if (isInMainInventory(gui, newItem) || newItem == gui.vhand) {
                            NUtils.drop(newItem);
                            known.remove(newItem);
                            needToDropRef[0]--;
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
     * Добавляет квариарц в батч для обработки маркера (батчинг для устранения лагов)
     */
    private void addQuarryartzMarker(NGameUI gui, String stoneName, double wallQ) {
        try {
            if (gui.mmap == null || gui.mmap.sessloc == null) {
                return;
            }
            
            Gob player = NUtils.player();
            if (player == null) {
                return;
            }
            
            // Получаем позицию игрока и направление копания
            Coord2d playerPos = player.rc;
            double angle = player.a;
            
            // Смещение в направлении копания на 1 тайл (для квариарца)
            Coord2d minedTile = new Coord2d(
                playerPos.x + (Math.cos(angle) * MCache.tilesz.x),
                playerPos.y + (Math.sin(angle) * MCache.tilesz.y)
            );
            
            // Получаем segment ID и tile coordinates
            long segmentId = gui.mmap.sessloc.seg.id;
            Coord tileCoords = minedTile.floor(MCache.tilesz).add(gui.mmap.sessloc.tc);
            
            // Добавляем в батч
            synchronized (batchLock) {
                markerBatchQueue.add(new MarkerBatch(stoneName, null, wallQ, tileCoords, segmentId, "quarryartz"));
                scheduleBatchProcessing(gui);
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
    }
    
    /**
     * Синхронная версия добавления метки на карту для квариарца
     * Ставит четко в месте выкопан, всегда ставится при выкапывании квариарца
     * Не склеивается с другими маркерами (не обновляет существующие)
     */
    private void addQuarryartzMarkerSync(NGameUI gui, String stoneName, double wallQ) {
        try {
            if (gui.mmap == null || gui.mmap.sessloc == null || gui.labeledMarkService == null) return;
            
            Gob player = NUtils.player();
            if (player == null) return;
            
            // Получаем позицию игрока и направление копания
            // Квариарц ставится на 1 тайл в сторону куда смотрит игрок (без проверки на существующие маркеры)
            Coord2d playerPos = player.rc;
            double angle = player.a; // угол направления игрока
            
            // Смещение в направлении копания на 1 тайл
            Coord2d minedTile = new Coord2d(
                playerPos.x + (Math.cos(angle) * MCache.tilesz.x),
                playerPos.y + (Math.sin(angle) * MCache.tilesz.y)
            );
            
            // Получаем segment ID и tile coordinates
            long segmentId = gui.mmap.sessloc.seg.id;
            Coord tileCoords = minedTile.floor(MCache.tilesz).add(gui.mmap.sessloc.tc);
            
            // Квариарц ставится всегда, без проверки на существующие маркеры рядом
            // LabeledMarkService удалит дубликаты только в радиусе 2 тайлов, но мы ставим на 1 тайл
            {
                // Создаем метку (например, "q101")
                String label = String.format("q%.0f", wallQ);
                
                // Создаем маркер БЕЗ иконки (null), иконка загрузится асинхронно
                String locationId = gui.labeledMarkService.addLabeledMarkAsync(label, "Quarryartz", segmentId, tileCoords, null);
                // Загружаем иконку квариарца асинхронно
                if (locationId != null) {
                    loadQuarryartzIconAndUpdateMarker(gui, locationId);
                }
                
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
     * Добавляет камень в батч для обработки маркера (батчинг для устранения лагов)
     */
    private void addOreSpotMarker(NGameUI gui, NGItem oreItem, String oreName, double wallQ) {
        try {
            if (gui.mmap == null || gui.mmap.sessloc == null) {
                return;
            }
            
            Gob player = NUtils.player();
            if (player == null) {
                return;
            }
            
            // Получаем позицию игрока и направление копания
            Coord2d playerPos = player.rc;
            double angle = player.a;
            
            // Смещение в направлении копания на 1 тайл (для квариарца)
            Coord2d minedTile = new Coord2d(
                playerPos.x + (Math.cos(angle) * MCache.tilesz.x),
                playerPos.y + (Math.sin(angle) * MCache.tilesz.y)
            );
            
            // Получаем segment ID и tile coordinates
            long segmentId = gui.mmap.sessloc.seg.id;
            Coord tileCoords = minedTile.floor(MCache.tilesz).add(gui.mmap.sessloc.tc);
            
            // Добавляем в батч
            synchronized (batchLock) {
                markerBatchQueue.add(new MarkerBatch(oreName, oreItem, wallQ, tileCoords, segmentId, "ore"));
                scheduleBatchProcessing(gui);
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
    }
    
    /**
     * Планирует обработку батча через небольшую задержку (батчинг)
     */
    private void scheduleBatchProcessing(NGameUI gui) {
        long currentTime = System.currentTimeMillis();
        if (lastBatchProcessTime == 0 || (currentTime - lastBatchProcessTime) >= BATCH_DELAY_MS) {
            // Обрабатываем батч через задержку в отдельном потоке
            markerExecutor().submit(() -> {
                try {
                    // Увеличенная задержка для сбора большего количества камней перед обработкой
                    Thread.sleep(BATCH_DELAY_MS);
                    processMarkerBatch(gui);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
            });
            lastBatchProcessTime = currentTime;
        }
    }
    
    /**
     * Обрабатывает батч маркеров - группирует по типу и координатам, выбирает лучший
     * Для камней и руд: "плавающий" маркер - обновляется только если качество выше существующего
     */
    private void processMarkerBatch(NGameUI gui) {
        List<MarkerBatch> batch;
        synchronized (batchLock) {
            if (markerBatchQueue.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(markerBatchQueue);
            markerBatchQueue.clear();
        }
        
        if (batch.isEmpty() || gui.labeledMarkService == null) {
            return;
        }
        
        // Группируем по ключу (тип + название + координаты) и выбираем лучший (максимальное качество)
        Map<String, MarkerBatch> bestMarkers = new HashMap<>();
        for (MarkerBatch item : batch) {
            String key = item.getGroupKey();
            MarkerBatch existing = bestMarkers.get(key);
            if (existing == null || item.wallQ > existing.wallQ) {
                bestMarkers.put(key, item);
            }
        }
        
        // Обрабатываем все маркеры
        for (MarkerBatch best : bestMarkers.values()) {
            try {
                String label = String.format("q%.0f", best.wallQ);
                
                // Определяем радиус и логику на основе типа
                final String finalLabel = label;
                final String finalOreName = best.oreName;
                final long finalSegmentId = best.segmentId;
                final Coord finalTileCoords = best.tileCoords;
                final String finalMarkerType = best.markerType;
                final NGItem finalItem = best.item;
                final double finalWallQ = best.wallQ;
                
                if ("quarryartz".equals(best.markerType)) {
                    // Квариарц: всегда создаём новый маркер (радиус 0)
                    createMarkerDirect(gui, finalLabel, finalOreName, finalSegmentId, finalTileCoords, finalMarkerType, finalItem, 0);
                    playQuarryartzSound(gui);
                } else if ("gem".equals(best.markerType)) {
                    // Драгоценные камни: радиус 2
                    createMarkerDirect(gui, finalLabel, finalOreName, finalSegmentId, finalTileCoords, finalMarkerType, finalItem, 2);
                } else {
                    // Камни и руды: "плавающий" маркер - обновляем только если качество выше
                    // Радиус 40 тайлов для камней и руд (большой спот)
                    final int radiusTiles = 40;
                    
                    // Ищем существующий маркер в радиусе (в фоне, чтобы не блокировать)
                    markerExecutor().submit(() -> {
                        try {
                            java.util.List<nurgling.widgets.LabeledMinimapMark> existingMarks = 
                                gui.labeledMarkService.getMarksByResourceType(finalOreName);
                            
                            String existingLocationId = null;
                            double existingQ = 0;
                            int checkedCount = 0;
                            int maxChecks = 50;
                            
                            for (nurgling.widgets.LabeledMinimapMark mark : existingMarks) {
                                if (checkedCount++ >= maxChecks) break;
                                if (mark.segmentId == finalSegmentId && 
                                    mark.isNear(finalSegmentId, finalTileCoords, radiusTiles) && 
                                    finalOreName.equals(mark.resourceType)) {
                                    // Парсим качество из метки
                                    double markQ = 0;
                                    if (mark.label != null && mark.label.startsWith("q")) {
                                        try {
                                            markQ = Double.parseDouble(mark.label.substring(1).trim());
                                        } catch (NumberFormatException e) {
                                            // Игнорируем
                                        }
                                    }
                                    if (markQ > existingQ) {
                                        existingQ = markQ;
                                        existingLocationId = mark.getLocationId();
                                    }
                                }
                            }
                            
                            if (existingLocationId != null) {
                                // Маркер найден - обновляем ТОЛЬКО если качество выше
                                if (finalWallQ > existingQ) {
                                    gui.labeledMarkService.updateMarkPosition(existingLocationId, finalLabel, finalTileCoords);
                                }
                                // Если качество не выше - ничего не делаем
                            } else {
                                // Маркера нет - создаём новый
                                createMarkerDirect(gui, finalLabel, finalOreName, finalSegmentId, finalTileCoords, finalMarkerType, finalItem, radiusTiles);
                            }
                        } catch (Exception e) {
                            // Игнорируем ошибки
                        }
                    });
                }
            } catch (Exception e) {
                // Игнорируем ошибки
            }
        }
    }
    
    /**
     * Создаёт маркер напрямую (для квариарца, драгоценных камней и новых спотов)
     */
    private void createMarkerDirect(NGameUI gui, String label, String oreName, long segmentId, 
                                    Coord tileCoords, String markerType, NGItem item, int radiusTiles) {
        // Получаем иконку из кэша
        BufferedImage icon = null;
        if ("quarryartz".equals(markerType)) {
            icon = getQuarryartzIcon();
        } else if (item != null) {
            icon = getOreIconFromItem(item, oreName);
        } else {
            icon = getOreIcon(oreName);
        }
        
        // Добавляем маркер (неблокирующий вызов)
        String locationId = gui.labeledMarkService.addLabeledMarkAsync(
            label, oreName, segmentId, tileCoords, icon, radiusTiles);
        
        // Если иконка не была загружена, загружаем асинхронно
        if (locationId != null && icon == null) {
            final String fLocationId = locationId;
            final NGItem fItem = item;
            final String fOreName = oreName;
            final String fMarkerType = markerType;
            iconLoaderExecutor().submit(() -> {
                try {
                    if ("quarryartz".equals(fMarkerType)) {
                        loadQuarryartzIconAndUpdateMarker(gui, fLocationId);
                    } else {
                        loadIconAndUpdateMarker(gui, fLocationId, fItem, fOreName);
                    }
                } catch (Exception e) {
                    // Игнорируем
                }
            });
        }
    }
    
    /**
     * Синхронная версия добавления или обновления метки спота руды на карте
     * Обновляет маркер в радиусе 30 клеток только если качество выше
     * Не заменяет другие руды или камни (проверяет resourceType)
     */
    private void addOreSpotMarkerSync(NGameUI gui, NGItem oreItem, String oreName, double wallQ) {
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
            
            // Смещение в направлении копания на 1 тайл (для квариарца)
            Coord2d minedTile = new Coord2d(
                playerPos.x + (Math.cos(angle) * MCache.tilesz.x),
                playerPos.y + (Math.sin(angle) * MCache.tilesz.y)
            );
            
            // Получаем segment ID и tile coordinates
            long segmentId = gui.mmap.sessloc.seg.id;
            Coord tileCoords = minedTile.floor(MCache.tilesz).add(gui.mmap.sessloc.tc);
            
            // УПРОЩЕННАЯ ВЕРСИЯ: Создаем маркер сразу без проверки существующих
            // Это устраняет блокировки и лаги, так как не нужно ждать lock
            // LabeledMarkService сам удалит дубликаты в радиусе 2 тайлов при создании
            // Создаем маркер БЕЗ иконки (null), иконка загрузится асинхронно
            String label = String.format("q%.0f", wallQ);
            String locationId = gui.labeledMarkService.addLabeledMarkAsync(label, oreName, segmentId, tileCoords, null);
            // Загружаем иконку асинхронно и обновим маркер когда загрузится
            if (locationId != null) {
                loadIconAndUpdateMarker(gui, locationId, oreItem, oreName);
            }
            
            // СТАРАЯ ВЕРСИЯ С ПРОВЕРКОЙ - отключена для устранения лагов
            // Если нужна проверка существующих маркеров, можно включить обратно, но это вызывает лаги
            /*
            // Ищем существующий маркер ТОЛЬКО этой же руды/камня в радиусе 30 клеток
            java.util.List<nurgling.widgets.LabeledMinimapMark> existingMarks = new ArrayList<>();
            try {
                existingMarks = gui.labeledMarkService.getMarksByResourceType(oreName);
            } catch (Exception e) {
                // Если не удалось получить маркеры, продолжаем без проверки
            }
            
            nurgling.widgets.LabeledMinimapMark nearbyMark = null;
            int checkedCount = 0;
            int maxChecks = 50;
            for (nurgling.widgets.LabeledMinimapMark mark : existingMarks) {
                if (checkedCount++ >= maxChecks) break;
                if (mark.segmentId != segmentId) continue;
                if (oreName.equals(mark.resourceType) && !"Quarryartz".equals(mark.resourceType)) {
                    int distX = Math.abs(mark.tileCoords.x - tileCoords.x);
                    int distY = Math.abs(mark.tileCoords.y - tileCoords.y);
                    if (distX <= 30 && distY <= 30) {
                        nearbyMark = mark;
                        break;
                    }
                }
            }
            
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
                        String locationId = gui.labeledMarkService.addLabeledMarkAsync(label, oreName, segmentId, tileCoords, null);
                        if (locationId != null) {
                            loadIconAndUpdateMarker(gui, locationId, oreItem, oreName);
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки парсинга
                }
            } else {
                // Маркер не найден - создаем новый
                String label = String.format("q%.0f", wallQ);
                String locationId = gui.labeledMarkService.addLabeledMarkAsync(label, oreName, segmentId, tileCoords, null);
                if (locationId != null) {
                    loadIconAndUpdateMarker(gui, locationId, oreItem, oreName);
                }
            }
            */
        } catch (Exception e) {
            // Игнорируем ошибки
        }
    }
    
    /**
     * Ищет путь к иконке в VSpec.object по названию руды
     * Преобразует путь из gfx/terobjs/bumlings/... в gfx/invobjs/...
     * 
     * @param resourceType название ресурса (например, "Wine Glance")
     * @return путь к иконке (например, "gfx/invobjs/cuprite") или null если не найден
     */
    private static String getIconPathFromVSpec(String resourceType) {
        if (resourceType == null || VSpec.object == null) return null;
        
        String lower = resourceType.toLowerCase().trim();
        String normalized = lower.replaceAll("\\s+", "");
        
        // Ищем в VSpec.object путь к иконке по названию руды
        for (String iconPath : VSpec.object.keySet()) {
            ArrayList<String> oreNames = VSpec.object.get(iconPath);
            if (oreNames != null) {
                for (String oreName : oreNames) {
                    String lowerOreName = oreName.toLowerCase().trim();
                    String normalizedOreName = lowerOreName.replaceAll("\\s+", "");
                    
                    // Проверяем точное совпадение или нормализованное
                    if (lowerOreName.equals(lower) || normalizedOreName.equals(normalized) ||
                        lowerOreName.equals(normalized) || normalizedOreName.equals(lower)) {
                        // Преобразуем путь из gfx/terobjs/bumlings/... в gfx/invobjs/...
                        if (iconPath.startsWith("gfx/terobjs/bumlings/")) {
                            String oreType = iconPath.substring("gfx/terobjs/bumlings/".length());
                            return "gfx/invobjs/" + oreType;
                        }
                        // Если путь уже в правильном формате, возвращаем как есть
                        return iconPath;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Получает иконку руды из ресурсов игры (приоритет) или из самого предмета (fallback)
     * Оптимизировано: сначала загружает из ресурсов через путь (быстро, не блокирует),
     * потом пробует получить из спрайта предмета (может быть медленнее)
     * Использует кэш и VSpec для оптимизации производительности
     */
    public static BufferedImage getOreIconFromItem(NGItem oreItem, String oreName) {
        // Проверяем кэш сначала
        if (oreName != null) {
            BufferedImage cached = oreIconCache.get(oreName);
            if (cached != null) {
                return cached;
            }
        }
        
        BufferedImage icon = null;
        
        // ПЕРВЫЙ ПРИОРИТЕТ: Загружаем из ресурсов через путь (быстро, как в проспектинге)
        // Это не блокирует поток и работает быстрее, чем получение спрайта
        icon = getOreIcon(oreName);
        if (icon != null) {
            // Кэшируем результат
            if (oreName != null) {
                oreIconCache.put(oreName, icon);
            }
            return icon;
        }
        
        // ВТОРОЙ ПРИОРИТЕТ: Пробуем получить изображение через спрайт предмета (только если есть предмет)
        // Убрали блокирующие задержки - пробуем только один раз, без ожидания
        if (oreItem != null) {
            try {
                // Пробуем получить спрайт только если ресурс уже готов
                if (oreItem.res != null && oreItem.res.isReady()) {
                    GSprite spr = oreItem.spr();
                    if (spr != null) {
                        // Используем ItemTex.sprimg для получения изображения из спрайта
                        BufferedImage sprImg = ItemTex.sprimg(spr);
                        if (sprImg != null) {
                            // Кэшируем результат
                            if (oreName != null) {
                                oreIconCache.put(oreName, sprImg);
                            }
                            return sprImg;
                        }
                        
                        // Альтернативный способ: если спрайт реализует ImageSprite
                        if (spr instanceof GSprite.ImageSprite) {
                            BufferedImage img = ((GSprite.ImageSprite) spr).image();
                            if (img != null) {
                                // Кэшируем результат
                                if (oreName != null) {
                                    oreIconCache.put(oreName, img);
                                }
                                return img;
                            }
                        }
                    }
                }
            } catch (Loading e) {
                // Ресурс еще загружается - пропускаем, не ждем (это не блокирует поток)
            } catch (Exception e) {
                // Игнорируем другие ошибки
            }
        }
        
        // Кэшируем результат для будущего использования (даже если null, чтобы не пытаться снова)
        if (oreName != null && icon == null) {
            // Не кэшируем null, чтобы можно было попробовать снова позже
        }
        
        return icon;
    }
    
    /**
     * Получает иконку руды из ресурсов игры (оптимизированный метод, как в проспектинге)
     * Использует VSpec для получения правильного пути, что быстрее и надежнее
     */
    public static BufferedImage getOreIcon(String oreName) {
        if (oreName == null) return null;
        
        // Сначала пробуем найти путь в VSpec (для руд с альтернативными названиями)
        // Это быстрее и надежнее, чем перебирать все возможные пути
        String vSpecPath = getIconPathFromVSpec(oreName);
        if (vSpecPath != null) {
            try {
                Resource res = Resource.remote().loadwait(vSpecPath);
                return res.layer(Resource.imgc).img;
            } catch (Exception e) {
                // Если не удалось загрузить из VSpec, пробуем другие пути
            }
        }
        
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
                    // Продолжаем с общими путями
                }
            }
        }
        
        String lower = oreName.toLowerCase().trim();
        
        // Специальные случаи преобразования названий
        String resourceName = lower;
        if (lower.equals("rock salt") || lower.equals("rocksalt")) {
            resourceName = "halite"; // Rock Salt использует иконку halite
        }
        
        // Нормализуем название: убираем пробелы (например, "lead glance" -> "leadglance")
        String normalized = resourceName.replaceAll("\\s+", "");
        
        // Список возможных путей к иконке (пробуем и с пробелами, и без)
        // Сократили список - сначала пробуем самые вероятные пути
        String[] possiblePaths = {
            "gfx/invobjs/" + normalized,  // Сначала пробуем нормализованное (без пробелов)
            "gfx/invobjs/" + resourceName,      // Затем с оригинальным названием
            "gfx/invobjs/ore-" + normalized,
            "gfx/invobjs/ore-" + resourceName,
            "gfx/invobjs/stone-" + normalized,
            "gfx/invobjs/stone-" + resourceName
        };
        
        // Пробуем загрузить из каждого пути
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
     * Добавляет драгоценный камень в батч для обработки маркера (батчинг для устранения лагов)
     */
    private void addGemstoneMarker(NGameUI gui, NGItem gemItem, String gemName, double quality) {
        try {
            if (gui.mmap == null || gui.mmap.sessloc == null) {
                return;
            }
            
            Gob player = NUtils.player();
            if (player == null) {
                return;
            }
            
            // Получаем позицию игрока и направление копания
            Coord2d playerPos = player.rc;
            double angle = player.a;
            
            // Смещение в направлении копания на 1 тайл (для квариарца)
            Coord2d minedTile = new Coord2d(
                playerPos.x + (Math.cos(angle) * MCache.tilesz.x),
                playerPos.y + (Math.sin(angle) * MCache.tilesz.y)
            );
            
            // Получаем segment ID и tile coordinates
            long segmentId = gui.mmap.sessloc.seg.id;
            Coord tileCoords = minedTile.floor(MCache.tilesz).add(gui.mmap.sessloc.tc);
            
            // Добавляем в батч
            synchronized (batchLock) {
                markerBatchQueue.add(new MarkerBatch(gemName, gemItem, quality, tileCoords, segmentId, "gem"));
                scheduleBatchProcessing(gui);
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
    }
    
    /**
     * Синхронная версия добавления метки на карту для драгоценного камня
     * Ставит с фактическим качеством (f3), без применения формул
     * Использует систему спотов (обновление в радиусе 30 клеток)
     * Использует иконку самого предмета
     */
    private void addGemstoneMarkerSync(NGameUI gui, NGItem gemItem, String gemName, double quality) {
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
            
            // Смещение в направлении копания на 1 тайл (для квариарца)
            Coord2d minedTile = new Coord2d(
                playerPos.x + (Math.cos(angle) * MCache.tilesz.x),
                playerPos.y + (Math.sin(angle) * MCache.tilesz.y)
            );
            
            // Получаем segment ID и tile coordinates
            long segmentId = gui.mmap.sessloc.seg.id;
            Coord tileCoords = minedTile.floor(MCache.tilesz).add(gui.mmap.sessloc.tc);
            
            // Драгоценные камни ставятся как квариарц - четко в месте выкопан, без системы спотов
            // УБРАНА ПРОВЕРКА существующих маркеров - она вызывала лаги из-за блокирующего lock
            // LabeledMarkService сам удалит дубликаты в радиусе 2 тайлов при создании маркера
            // Создаем маркер всегда - сервис сам обработает дубликаты
            {
                // Создаем новый маркер (как квариарц - четко в месте выкопан)
                String label = String.format("q%.0f", quality);
                // Создаем маркер БЕЗ иконки (null), иконка загрузится асинхронно
                String locationId = gui.labeledMarkService.addLabeledMarkAsync(label, gemName, segmentId, tileCoords, null);
                // Загружаем иконку асинхронно и обновим маркер когда загрузится
                if (locationId != null) {
                    loadIconAndUpdateMarker(gui, locationId, gemItem, gemName);
                }
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
    
    /**
     * Асинхронно загружает иконку руды/камня и обновляет маркер
     * Оптимизировано: загрузка иконки отложена, чтобы не блокировать создание маркера
     * Улучшено: проверяет существующие маркеры проспектинга и использует их иконки
     */
    private void loadIconAndUpdateMarker(NGameUI gui, String locationId, NGItem item, String resourceName) {
        iconLoaderExecutor().submit(() -> {
            try {
                // Небольшая задержка, чтобы маркер успел создаться без блокировки
                Thread.sleep(50);
                
                // Сначала проверяем существующие маркеры проспектинга
                BufferedImage icon = null;
                if (gui.mapfile != null && gui.mapfile.file != null) {
                    try {
                        // Получаем координаты маркера для проверки
                        nurgling.widgets.LabeledMinimapMark mark = gui.labeledMarkService.getMark(locationId);
                        if (mark != null) {
                            String iconPath = getIconPathFromVSpec(resourceName);
                            if (iconPath != null) {
                                MapFile.SMarker existingMarker = gui.mapfile.file.smarker(iconPath, mark.segmentId, mark.tileCoords);
                                if (existingMarker != null && existingMarker.res != null) {
                                    try {
                                        Resource res = existingMarker.res.get();
                                        if (res != null) {
                                            icon = res.layer(Resource.imgc).img;
                                        }
                                    } catch (Exception e) {
                                        // Игнорируем ошибки загрузки
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки проверки
                    }
                }
                
                // Если иконка не найдена в проспектинге, загружаем из предмета или VSpec
                if (icon == null) {
                    icon = getOreIconFromItem(item, resourceName);
                }
                
                // Обновляем маркер с загруженной иконкой
                if (gui.labeledMarkService != null && icon != null) {
                    gui.labeledMarkService.updateMarkIcon(locationId, icon);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // Игнорируем ошибки загрузки иконки
            }
        });
    }
    
    /**
     * Асинхронно загружает иконку квариарца и обновляет маркер
     * Улучшено: проверяет существующие маркеры проспектинга и использует их иконки
     */
    private void loadQuarryartzIconAndUpdateMarker(NGameUI gui, String locationId) {
        iconLoaderExecutor().submit(() -> {
            try {
                // Небольшая задержка, чтобы маркер успел создаться
                Thread.sleep(50);
                
                // Сначала проверяем существующие маркеры проспектинга
                BufferedImage icon = null;
                if (gui.mapfile != null && gui.mapfile.file != null) {
                    try {
                        // Получаем координаты маркера для проверки
                        nurgling.widgets.LabeledMinimapMark mark = gui.labeledMarkService.getMark(locationId);
                        if (mark != null) {
                            // Проверяем маркер проспектинга для квариарца
                            String iconPath = "gfx/invobjs/quarryquartz"; // Путь к иконке квариарца
                            MapFile.SMarker existingMarker = gui.mapfile.file.smarker(iconPath, mark.segmentId, mark.tileCoords);
                            if (existingMarker == null) {
                                // Пробуем альтернативный путь
                                iconPath = "gfx/invobjs/quarryartz";
                                existingMarker = gui.mapfile.file.smarker(iconPath, mark.segmentId, mark.tileCoords);
                            }
                            if (existingMarker != null && existingMarker.res != null) {
                                try {
                                    Resource res = existingMarker.res.get();
                                    if (res != null) {
                                        icon = res.layer(Resource.imgc).img;
                                    }
                                } catch (Exception e) {
                                    // Игнорируем ошибки загрузки
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки проверки
                    }
                }
                
                // Если иконка не найдена в проспектинге, загружаем из ресурсов
                if (icon == null) {
                    icon = getQuarryartzIcon();
                }
                
                // Обновляем маркер с загруженной иконкой
                if (gui.labeledMarkService != null && icon != null) {
                    gui.labeledMarkService.updateMarkIcon(locationId, icon);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // Игнорируем ошибки загрузки иконки
            }
        });
    }
}


