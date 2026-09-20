package nurgling.actions.bots;

import haven.*;
import haven.MCache;
import haven.Resource;
import haven.res.lib.itemtex.ItemTex;
import haven.res.ui.stackinv.ItemStack;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.ActionWithFinal;
import nurgling.actions.PathFinder;
import nurgling.actions.Results;
import nurgling.i18n.L10n;
import nurgling.tasks.GetCurs;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitConstructionObject;
import nurgling.tasks.WaitPlob;
import nurgling.tasks.WaitTicks;
import nurgling.tasks.WaitWindow;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.tools.VSpec;
import nurgling.NInventory;
import nurgling.widgets.NEquipory;
import nurgling.widgets.bots.MasterMinerGroundStacks;
import nurgling.widgets.bots.MasterMinerWnd;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
import java.util.HashSet;
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
    /** An item's origin is fixed when its GItem first appears, never when a WItem widget is rebuilt. */
    enum Origin { CARRIED, MINED }

    static final class Seen {
        final Origin origin;
        boolean counted;
        boolean recorded;
        boolean settled;

        Seen(Origin origin) {
            this.origin = origin;
        }
    }

    /** Identity-keyed state; production instantiates it with GItem, tests use ordinary identity tokens. */
    static final class ItemOrigins<K> {
        private final Map<K, Seen> items = new WeakHashMap<>();

        void observe(Iterable<K> keys, boolean baselineOrNotMining) {
            observe(keys, baselineOrNotMining ? Origin.CARRIED : Origin.MINED);
        }

        void observe(Iterable<K> keys, Origin arrival) {
            for (K key : keys) if (key != null) items.computeIfAbsent(key, ignored -> new Seen(arrival));
        }

        Seen get(K key) { return items.get(key); }

        void clear() { items.clear(); }

        boolean claimCount(K key) {
            Seen seen = items.get(key);
            if (seen == null || seen.origin != Origin.MINED || seen.counted) return false;
            seen.counted = true;
            return true;
        }

        boolean claimRecord(K key) {
            Seen seen = items.get(key);
            if (seen == null || seen.origin != Origin.MINED || seen.recorded) return false;
            seen.recorded = true;
            return true;
        }
    }

    private final ItemOrigins<GItem> seen = new ItemOrigins<>();
    private final Map<GItem, GItem> stackHolders = new HashMap<>();
    /** Holder contents first observed during this run; later leaves are new arrivals themselves. */
    private final Set<GItem> stackContentsObserved = new HashSet<>();
    private final Map<GItem, Boolean> stackLeafFromFirstContents = new HashMap<>();
    private static final int DROP_CONFIRM_TICKS = 30;
    
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

    static <T> List<T> withHand(List<T> carried, T hand) {
        Set<T> unique = new LinkedHashSet<>();
        if (carried != null) unique.addAll(carried);
        if (hand != null) unique.add(hand);
        return new ArrayList<>(unique);
    }

    static int dropBudget(int totalStones, int keepForSupport) {
        return Math.max(0, totalStones - Math.max(0, keepForSupport));
    }

    static boolean shouldRecordMined(Origin origin, boolean singleStone) {
        return origin == Origin.MINED && singleStone;
    }

    static Origin stackLeafOrigin(Origin arrival, Origin holderOrigin, boolean firstContentsSnapshot) {
        return firstContentsSnapshot && holderOrigin != null ? holderOrigin : arrival;
    }

    static boolean usesSingleDropProtocol(boolean stackLeaf, int stackAmount) {
        return stackLeaf || stackAmount > 1;
    }

    static boolean dropConfirmed(boolean departed, int beforeAmount, int afterAmount) {
        return departed || (beforeAmount > 1 && afterAmount >= 0 && afterAmount < beforeAmount);
    }

    static boolean isAggregateStackAmount(int amount) {
        return amount > 1;
    }

    static boolean isFirstPopulatedContentsSnapshot(boolean alreadyObserved, int orderedMemberCount) {
        return !alreadyObserved && orderedMemberCount > 0;
    }

    static <K, V> List<V> orderedStackMembers(Iterable<K> order, Map<K, V> widgets) {
        ArrayList<V> result = new ArrayList<>();
        if (order == null || widgets == null) return result;
        for (K key : order) {
            V widget = widgets.get(key);
            if (widget != null && !result.contains(widget)) result.add(widget);
        }
        return result;
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
        final int masonry;
        final String stoneType;
        
        MarkerBatch(String oreName, NGItem item, double wallQ, Coord tileCoords, long segmentId, String markerType,
                    int masonry, String stoneType) {
            this.oreName = oreName;
            this.item = item;
            this.wallQ = wallQ;
            this.tileCoords = tileCoords;
            this.segmentId = segmentId;
            this.markerType = markerType;
            this.masonry = masonry;
            this.stoneType = stoneType;
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

    /** Returns false only while a requested drop cannot yet be issued, so the GItem stays pending. */
    private boolean settleDrop(NGameUI gui, WItem item, String stoneName, double quality,
                               MasterMinerWnd wnd, int[] needToDropRef) throws InterruptedException {
        String type = classifyStoneType(stoneName);
        double threshold = "Shell".equals(type) || "Cat Gold".equals(type)
                ? wnd.getShellCatGoldThreshold() : wnd.getDropThreshold();
        if (needToDropRef == null || needToDropRef[0] <= 0 || Double.isNaN(threshold) || quality >= threshold)
            return true;
        if (!isInMainInventory(gui, item) && item != gui.vhand)
            return true;
        String lower = stoneName == null ? "" : stoneName.toLowerCase();
        if (lower.contains("axe") || lower.contains("pickaxe") || lower.contains("топор") || lower.contains("кирк"))
            return true;
        if (!dropStone(gui, item)) return hasLeftInventory(gui, item);
        needToDropRef[0]--;
        return true;
    }

    /** A removed widget must finish a pending quality wait instead of keeping the miner blocked. */
    private boolean hasLeftInventory(NGameUI gui, WItem item) {
        return item == null || item.parent == null || (!isInMainInventory(gui, item) && item != gui.vhand);
    }

    /**
     * Wait only until this item has usable quality or has left the carried inventory.  In the
     * latter case it remains unrecorded: a rebuilt widget for the same GItem can retry safely.
     */
    private double awaitQuality(NGameUI gui, WItem item) throws InterruptedException {
        if (item == null || !(item.item instanceof NGItem)) return -1;
        NGItem stone = (NGItem) item.item;
        double quality = getItemQuality(stone, item);
        if (quality >= 0) return quality;
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                return hasLeftInventory(gui, item)
                        || (stone.name() != null && getItemQuality(stone, item) >= 0);
            }
        });
        quality = getItemQuality(stone, item);
        if (quality < 0 && !hasLeftInventory(gui, item)) NUtils.addTask(new WaitTicks(2));
        return quality;
    }

    /**
     * Sends one controlled drop and waits for the client widget to disappear.  Stack leaves
     * receive the amount-one protocol so the support reserve cannot lose the whole stack.
     */
    private boolean dropStone(NGameUI gui, WItem item) throws InterruptedException {
        if (item == null || item.item == null || (!isInMainInventory(gui, item) && item != gui.vhand))
            return false;
        // The local client has no dropSlotReady hook. A short per-message delay is its throttle.
        NUtils.addTask(new WaitTicks(3));
        if (!isInMainInventory(gui, item) && item != gui.vhand) return false;
        int beforeAmount = itemAmount(item);
        if (usesSingleDropProtocol(item.parent instanceof ItemStack, beforeAmount)) {
            item.item.wdgmsg("drop", Coord.z, 1);
        } else {
            NUtils.drop(item);
        }
        NUtils.addTask(new NTask() {
            int ticks;

            @Override
            public boolean check() {
                return dropConfirmed(hasLeftInventory(gui, item), beforeAmount, itemAmount(item))
                        || ++ticks >= DROP_CONFIRM_TICKS;
            }
        });
        return dropConfirmed(hasLeftInventory(gui, item), beforeAmount, itemAmount(item));
    }

    private int itemAmount(WItem item) {
        if (!(item != null && item.item instanceof NGItem)) return 1;
        GItem.Amount info = ((NGItem) item.item).getInfo(GItem.Amount.class);
        return info != null && info.itemnum() > 0 ? info.itemnum() : 1;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        // сброс состояния для повторного запуска
        stop = false;
        seen.clear();
        stackHolders.clear();
        stackContentsObserved.clear();
        stackLeafFromFirstContents.clear();
        MasterMinerWnd created = new MasterMinerWnd();
        Coord savedPos = created.savedWindowPos();
        if (savedPos != null) {
            gui.add(created, savedPos);
            gui.fitwdg(created);
            wnd = created;
        } else {
            wnd = NUtils.addCentered(gui, created);
        }

        if (gui.map instanceof nurgling.NMapView) {
            ((nurgling.NMapView) gui.map).restoreMinesweeperOverlay();
        }

        // Capture all pre-existing inventory/stack/hand items before the cursor can create a drop.
        observeCarriedItems(collectCarriedItems(gui), Origin.CARRIED);

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
            while (!stop && wnd != null && !wnd.isClosed()) {
                int masonry = 0;
                try {
                    masonry = NUtils.getUI().sess.glob.getcattr("masonry").comp;
                } catch (Exception ignored) {
                }
                wnd.setMasonry(masonry);

                String curs = NUtils.getCursorName();
                boolean mining = (curs != null) && NParser.checkName(curs, "mine");

                ArrayList<WItem> allItems = collectCarriedItems(gui);
                observeCarriedItems(allItems, mining ? Origin.MINED : Origin.CARRIED);

                if (!mining) {
                    NUtils.addTask(new WaitTicks(10));
                    continue;
                }

                ArrayList<WItem> cur = filterMinedItems(allItems);

                ArrayList<WItem> pending = new ArrayList<>();
                for (WItem it : cur) {
                    Seen itemSeen = seen.get(it.item);
                    if (itemSeen != null && !itemSeen.settled && !isAggregateStack(it)) {
                        pending.add(it);
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
                int keepStones = wnd.getKeepStonesForSupport();
                int[] needToDropRef = new int[] { dropBudget(totalStones, keepStones) };
                
                if (pending.isEmpty() && stacksToCheck.isEmpty()) {
                    NUtils.addTask(new WaitTicks(5));
                    continue;
                }
                
                // Сначала стаки — иначе лимит сброса забирают одиночные и стаки не трогаем
                for (WItem stackItem : stacksToCheck) {
                    try {
                        checkAndDropStack(gui, stackItem, wnd, needToDropRef);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                // Затем все pending items. Origin state prevents retries and rebuilt WItems from double-recording.
                for (WItem item : pending) {
                    processStone(gui, item, seen.get(item.item), wnd, needToDropRef);
                }
                
                // Небольшой yield после обработки всех камней
                NUtils.addTask(new WaitTicks(2));
            }
        } finally {
            processMarkerBatch(gui);
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
    
    /** Checks the complete parent chain, hopping from a stack ContentsWindow back to its holder. */
    private boolean isInMainInventory(NGameUI gui, WItem witem) {
        if (witem == null || gui == null) return false;
        if (witem == gui.vhand) return true;
        for (Widget w = witem; w != null; ) {
            if (w == gui.getInventory()) return true;
            if (w instanceof GItem.ContentsWindow) {
                w = ((GItem.ContentsWindow) w).cont;
            } else {
                w = w.parent;
            }
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

    /** One snapshot across normal inventory, nested stack slots, and the hand. */
    private ArrayList<WItem> collectCarriedItems(NGameUI gui) {
        stackHolders.clear();
        stackLeafFromFirstContents.clear();
        ArrayList<WItem> inventory = collectAllWItemsFromWidget(gui.getInventory());
        return new ArrayList<>(withHand(inventory, gui.vhand));
    }

    private void observeCarriedItems(List<WItem> items, Origin arrival) {
        if (items == null) return;
        for (WItem item : items) {
            if (item == null || item.item == null) continue;
            GItem holder = stackHolders.get(item.item);
            Seen holderSeen = holder == null ? null : seen.get(holder);
            seen.observe(Collections.singletonList(item.item),
                    stackLeafOrigin(arrival, holderSeen == null ? null : holderSeen.origin,
                            Boolean.TRUE.equals(stackLeafFromFirstContents.get(item.item))));
        }
    }

    private void collectAllWItemsRecur(Widget w, ArrayList<WItem> out) {
        if (w == null) return;
        if (w instanceof WItem) {
            WItem wi = (WItem) w;
            if (wi.item != null && !out.contains(wi)) {
                out.add(wi);
                collectStackMembers(wi, out);
            }
        }
        for (Widget ch = w.child; ch != null; ch = ch.next) {
            collectAllWItemsRecur(ch, out);
        }
    }

    /** Stack leaves are kept off the inventory widget tree in GItem.contents. */
    private void collectStackMembers(WItem holder, ArrayList<WItem> out) {
        if (holder == null || !(holder.item.contents instanceof ItemStack)) return;
        ItemStack stack = (ItemStack) holder.item.contents;
        List<WItem> leaves = orderedStackMembers(new ArrayList<>(stack.order), stack.wmap);
        boolean firstContentsSnapshot = isFirstPopulatedContentsSnapshot(
                stackContentsObserved.contains(holder.item), leaves.size());
        if (firstContentsSnapshot) stackContentsObserved.add(holder.item);
        for (WItem leaf : leaves) {
            if (leaf != null && leaf.item != null) {
                stackHolders.put(leaf.item, holder.item);
                stackLeafFromFirstContents.put(leaf.item, firstContentsSnapshot);
                if (!out.contains(leaf)) out.add(leaf);
            }
        }
    }

    private boolean isAggregateStack(WItem item) {
        try {
            return isAggregateStackAmount(itemAmount(item));
        } catch (Exception ignored) {
            return false;
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
            if (!dropStone(gui, stackItem)) break;
            needToDropRef[0]--;
        }
    }
    
    /**
     * Считает общее количество камней (включая стаки) в списке — обычные камни и руды, без драгоценных.
     * Используется для ограничения «держать N камней для подпорки».
     */
    private int countTotalStones(ArrayList<WItem> items) {
        return supportStoneCount(items);
    }

    /** The same stone set used by the support reserve and its automatic drop budget. */
    public static boolean isSupportStone(String name) {
        if (name == null || isGemstone(name)) return false;
        if (!NParser.checkName(name, MINED_ITEMS) && !NParser.checkName(name, ORE_ITEMS)) return false;
        String stoneType = classifyStoneType(name);
        return !"Shell".equals(stoneType) && !"Cat Gold".equals(stoneType);
    }

    /** Counts individual support stones, including members of item stacks. */
    public static int supportStoneCount(List<WItem> items) {
        if (items == null) return 0;
        int total = 0;
        for (WItem w : items) {
            if (w == null || !(w.item instanceof NGItem)) continue;
            NGItem ng = (NGItem) w.item;
            if (!isSupportStone(ng.name())) continue;
            haven.GItem.Amount amount = ng.getInfo(haven.GItem.Amount.class);
            total += (amount != null && amount.itemnum() > 0) ? amount.itemnum() : 1;
        }
        return total;
    }

    /**
     * Collects loose stones accepted by the support reserve, then returns to the point where
     * the action started. The action deliberately shares the normal PathFinder/take protocol.
    */
    public static final class CollectSupportStones implements nurgling.actions.Action {
        private static final String STONE_COLUMN_NAME = "Stone Column";
        private static final String STONE_COLUMN_PAGINA = "paginae/bld/column";
        private final int requested;

        public CollectSupportStones(int requested) {
            this.requested = Math.max(0, Math.min(MasterMinerGroundStacks.CLICK_PICKUP_LIMIT, requested));
        }

        @Override
        public Results run(NGameUI gui) throws InterruptedException {
            Gob player = NUtils.player();
            if (gui == null || player == null || requested == 0) return Results.SUCCESS();
            clearMiningCursor(gui, player);
            player = NUtils.player();
            if (player == null) return Results.SUCCESS();
            Coord2d origin = Coord2d.of(player.rc.x, player.rc.y);
            Results collection = Results.SUCCESS();
            try {
                int taken = 0;
                while (taken < requested) {
                    player = NUtils.player();
                    if (player == null || gui.getInventory() == null || gui.getInventory().getFreeSpace() <= 0)
                        break;
                    Gob item = nearestSupportStone(player, origin);
                    if (item == null) break;
                    if (item.rc.dist(player.rc) > MCache.tilesz.x) {
                        Results walked = new PathFinder(item).run(gui);
                        if (!walked.IsSuccess()) {
                            collection = walked;
                            break;
                        }
                    }
                    NUtils.takeFromEarth(item);
                    taken++;
                }
            } finally {
                /* Best effort also covers a full inventory, path failure, and interruption. */
                try {
                    Gob current = NUtils.player();
                    if (current != null && current.rc.dist(origin) > MCache.tilesz.x)
                        new PathFinder(origin).run(gui);
                } catch (InterruptedException ignored) {
                    // An explicit cancellation may prevent the return path from being planned.
                } catch (Exception ignored) {
                    // Returning must not hide the original collection result.
                }
            }
            return collection.IsSuccess()
                    ? placeStoneColumn(gui, origin)
                    : collection;
        }

        private void clearMiningCursor(NGameUI gui, Gob player) throws InterruptedException {
            /* Force the click even for an existing arrow cursor: it also drops tile selection. */
            gui.map.wdgmsg("click", Coord.z, player.rc.floor(OCache.posres), 3, 0);
            NUtils.addTask(new GetCurs("arw"));
        }

        private Results placeStoneColumn(NGameUI gui, Coord2d origin) throws InterruptedException {
            Coord originTile = origin.div(MCache.tilesz).floor();
            Coord targetTile = MasterMinerSupportPlacement.chooseAdjacent(originTile,
                    tile -> isOpenCaveTile(gui, tile),
                    tile -> nurgling.tools.Finder.findGob(tileCenter(tile)) == null);
            if (targetTile == null) {
                return supportError("bot.masterminer.support_no_tile");
            }
            Coord2d target = tileCenter(targetTile);
            if (nurgling.tools.Finder.findGob(target) != null) {
                return supportError("bot.masterminer.support_no_tile");
            }
            MenuGrid.Pagina stoneColumnPagina = stoneColumnPagina(gui);
            if (stoneColumnPagina == null) {
                return supportError("bot.masterminer.support_no_pagina");
            }
            try {
                stoneColumnPagina.button().use(new MenuGrid.Interaction(1, 0));
            } catch (Loading ignored) {
                return supportError("bot.masterminer.support_no_pagina");
            }
            NUtils.addTask(WaitPlob.withSoftTimeout(true, 200, gui));
            if (gui.map.placing == null || !gui.map.placing.ready()) {
                return supportError("bot.masterminer.support_place_failed");
            }
            gui.map.wdgmsg("place", target.floor(OCache.posres), 0, 1, 0);
            NUtils.addTask(WaitConstructionObject.withSoftTimeout(target, 200));
            if (nurgling.tools.Finder.findGob(target) == null) {
                return supportError("bot.masterminer.support_place_failed");
            }
            NUtils.addTask(WaitWindow.withSoftTimeout(STONE_COLUMN_NAME, 200));
            if (gui.getWindow(STONE_COLUMN_NAME) == null) {
                return supportError("bot.masterminer.support_place_failed");
            }
            return Results.SUCCESS();
        }

        private MenuGrid.Pagina stoneColumnPagina(NGameUI gui) {
            if (gui.menu == null) return null;
            for (MenuGrid.Pagina pagina : gui.menu.paginae) {
                try {
                    if (pagina != null && STONE_COLUMN_PAGINA.equals(pagina.res().name)) {
                        return pagina;
                    }
                } catch (Loading ignored) {
                    // The page has not finished loading; leave stones untouched and try again later.
                }
            }
            return null;
        }

        private boolean isOpenCaveTile(NGameUI gui, Coord tile) {
            if (gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null) return false;
            try {
                Resource resource = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tile));
                return resource != null && MasterMinerSupportPlacement.isOpenCaveTileName(resource.name);
            } catch (Loading ignored) {
                return false;
            }
        }

        private Coord2d tileCenter(Coord tile) {
            return new Coord2d(tile.x * MCache.tilesz.x + MCache.tilesz.x / 2,
                    tile.y * MCache.tilesz.y + MCache.tilesz.y / 2);
        }

        private Results supportError(String key) {
            return Results.ERROR(L10n.get(key));
        }

        private Gob nearestSupportStone(Gob player, Coord2d origin) {
            Gob best = null;
            double bestDist = MasterMinerGroundStacks.PICKUP_RADIUS;
            OCache oc = player.glob.oc;
            synchronized (oc) {
                for (Gob gob : oc) {
                    if (gob == null || gob == player || gob instanceof OCache.Virtual || gob.ngob == null)
                        continue;
                    String path = gob.ngob.name;
                    if (!MasterMinerGroundStacks.isGroundItem(path)
                            || !isSupportStone(MasterMinerGroundStacks.minedItemName(path)))
                        continue;
                    double fromOrigin = gob.rc.dist(origin);
                    double fromPlayer = gob.rc.dist(player.rc);
                    if (fromOrigin < MasterMinerGroundStacks.PICKUP_RADIUS && fromPlayer < bestDist) {
                        bestDist = fromPlayer;
                        best = gob;
                    }
                }
            }
            return best;
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
                if (ngItem.contents instanceof ItemStack) continue;
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
    private void processStone(NGameUI gui, WItem newItem, Seen seenItem, MasterMinerWnd wnd, int[] needToDropRef) throws InterruptedException {
        if (newItem == null || !(newItem.item instanceof NGItem) || seenItem == null) return;
        NGItem dropped = (NGItem) newItem.item;
        
        // Для стаков нужно получить качество через Stack info
        double f3 = awaitQuality(gui, newItem);
        if (f3 < 0 || dropped.name() == null) {
            if (!hasLeftInventory(gui, newItem)) NUtils.addTask(new WaitTicks(2));
            return;
        }
        String stoneName = dropped.name();
        String stoneType = classifyStoneType(stoneName);

        // Проверяем, является ли это драгоценным камнем
        boolean isGem = isGemstone(dropped);
        if (!isGem) {
            isGem = isGemstone(stoneName);
        }

        if (isGem && seenItem.origin != Origin.MINED) {
            seenItem.settled = true;
            return;
        }

        if (!isGem && seenItem.origin == Origin.CARRIED) {
            seenItem.settled = settleDrop(gui, newItem, stoneName, f3, wnd, needToDropRef);
            return;
        }
        
        // Драгоценные камни НЕ учитываются при подсчете качества и НЕ обновляют UI
        // Но маркеры для них ставятся
        if (isGem) {
            // Для драгоценных камней качество в руках и в стене совпадает
            int masonryForLastMined = masonrySkill();
            wnd.setLastMined(stoneName, f3, f3, masonryForLastMined, stoneType);
            
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
                
                // When no explicit value is saved, use the same defaults as the settings UI.
                boolean shouldMark = false;
                if (enabled == null) {
                    shouldMark = defaultMarkerEnabled(configKey);
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
                            addGemstoneMarker(gui, dropped, baseGemName, f3, masonryForLastMined, stoneType);
                        } catch (Exception e) {
                            // Игнорируем ошибки
                        }
                    }
                }
            }
            // Драгоценные камни НЕ сбрасываются и НЕ учитываются в статистике
            seenItem.recorded = true;
            seenItem.settled = true;
            return;
        }

        WItem tool = findMiningTool();
        if (tool == null) {
            NUtils.addTask(new WaitTicks(10));
            return;
        }

        // Keep the item pending while tool metadata resolves; the widget can be rebuilt or disappear.
        if (!(tool.item instanceof NGItem)) return;
        WItem ftool = tool;
        String toolName = ((NGItem) ftool.item).name();
        Double f4 = ((NGItem) ftool.item).quality != null ? (double) ((NGItem) ftool.item).quality : null;
        if (toolName == null || f4 == null) {
            NUtils.addTask(new WaitTicks(2));
            return;
        }
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
            if (stoneType != null && shouldRecordMined(seenItem.origin, isSingleStone(dropped))
                    && seen.claimRecord(newItem.item)) {
                int masonryForUI = masonrySkill();
                wnd.setStoneInfo(stoneType, stoneName, f3, wallQ, bestAltQ, masonryForUI, set, currentToolType);
                wnd.setLastMined(stoneName, f3, wallQ, masonryForUI, stoneType);
                if (seen.claimCount(newItem.item)) {
                    wnd.incrementCounter();
                }
                
                // Проверяем, нужно ли поставить метку на карте согласно настройкам
                nurgling.conf.NMasterMinerMarkingConfig markingConfig = nurgling.conf.NMasterMinerMarkingConfig.get();
                if (markingConfig != null) {
                    // Для остальных камней используем полное название
                    String configKey = stoneName;
                    
                    Boolean enabled = markingConfig.isEnabled(configKey);
                    Double threshold = markingConfig.getThreshold(configKey);
                    
                    // When no explicit value is saved, use the same defaults as the settings UI.
                    boolean shouldMark = false;
                    if (enabled == null) {
                        shouldMark = defaultMarkerEnabled(configKey);
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
                                addQuarryartzMarker(gui, stoneName, wallQ, masonryForUI, stoneType);
                            } else {
                                // Остальные камни и руды - система спотов (обновление в радиусе 30 клеток)
                                addOreSpotMarker(gui, dropped, stoneName, wallQ, masonryForUI, stoneType);
                            }
                        }
                    }
                }
            }

            seenItem.recorded = true;

            // проверка порога и сброс камня (включая стаки)
            // Сброс происходит по фактическому качеству камня (f3), а не по qWall
            // Для ракух и кэтголдов используется отдельный порог
            // Драгоценные камни НЕ сбрасываются (они уже обработаны выше и вернулись)
            seenItem.settled = settleDrop(gui, newItem, stoneName, f3, wnd, needToDropRef);
        }
    }

    /** A stack's aggregate must never be treated as one new stone. */
    private static boolean isSingleStone(NGItem item) {
        haven.GItem.Amount amount = item.getInfo(haven.GItem.Amount.class);
        return amount == null || amount.itemnum() <= 1;
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

    private static int masonrySkill() {
        try {
            return NUtils.getUI().sess.glob.getcattr("masonry").comp;
        } catch (Exception ignored) {
            return 0;
        }
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

    /** Default marker selection shared by the runtime and Mining Mastery settings. */
    public static boolean defaultMarkerEnabled(String stoneName) {
        return isOre(stoneName)
                || isGemstone(stoneName)
                || "Feldspar".equals(stoneName)
                || "Flint".equals(stoneName)
                || "Quartz".equals(stoneName)
                || "Quarryartz".equals(stoneName)
                || "Rock Salt".equals(stoneName)
                || "Black Coal".equals(stoneName);
    }

    /**
     * Ordinary mined stone: Chipper/MINED_ITEMS names that are not ore, gemstone, or exact Quarryartz.
     */
    public static boolean isStone(String stoneName) {
        if (stoneName == null) return false;
        if ("Quarryartz".equals(stoneName.trim())) return false;
        if (isOre(stoneName) || isGemstone(stoneName)) return false;
        return NParser.checkName(stoneName, MINED_ITEMS);
    }

    /** The numeric quality remains on the mark; the suffix is display-only. */
    static String markerLabel(double quality, int masonry, String stoneType) {
        return String.format("q%.0f%s", quality,
                MasterMinerWnd.isMasonryCapped(quality, masonry, stoneType) ? "*" : "");
    }

    static boolean shouldUpdateMarker(double newQuality, String newLabel, double existingQuality, String existingLabel) {
        return newQuality > existingQuality || (Double.compare(newQuality, existingQuality) == 0
                && newLabel.endsWith("*") && (existingLabel == null || !existingLabel.endsWith("*")));
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
    private void addQuarryartzMarker(NGameUI gui, String stoneName, double wallQ, int masonry, String stoneType) {
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
                markerBatchQueue.add(new MarkerBatch(stoneName, null, wallQ, tileCoords, segmentId, "quarryartz",
                        masonry, stoneType));
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
    private void addOreSpotMarker(NGameUI gui, NGItem oreItem, String oreName, double wallQ, int masonry, String stoneType) {
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
                markerBatchQueue.add(new MarkerBatch(oreName, oreItem, wallQ, tileCoords, segmentId, "ore",
                        masonry, stoneType));
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
                String label = markerLabel(best.wallQ, best.masonry, best.stoneType);
                
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
                            String existingLabel = null;
                            int checkedCount = 0;
                            int maxChecks = 50;
                            
                            for (nurgling.widgets.LabeledMinimapMark mark : existingMarks) {
                                if (checkedCount++ >= maxChecks) break;
                                if (mark.segmentId == finalSegmentId && 
                                    mark.isNear(finalSegmentId, finalTileCoords, radiusTiles) && 
                                    finalOreName.equals(mark.resourceType)) {
                                    // Numeric quality is persisted separately from the display label.
                                    double markQ = mark.quality;
                                    if (markQ > existingQ) {
                                        existingQ = markQ;
                                        existingLocationId = mark.getLocationId();
                                        existingLabel = mark.label;
                                    }
                                }
                            }
                            
                            if (existingLocationId != null) {
                                // Маркер найден - обновляем ТОЛЬКО если качество выше
                                if (shouldUpdateMarker(finalWallQ, finalLabel, existingQ, existingLabel)) {
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
                    // Quality is persisted separately from the optional display suffix.
                    double existingQ = nearbyMark.quality;
                    
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
    private void addGemstoneMarker(NGameUI gui, NGItem gemItem, String gemName, double quality, int masonry,
                                   String stoneType) {
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
                markerBatchQueue.add(new MarkerBatch(gemName, gemItem, quality, tileCoords, segmentId, "gem",
                        masonry, stoneType));
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


