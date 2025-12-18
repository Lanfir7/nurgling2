package nurgling.actions.bots;

import haven.*;
import haven.MCache;
import haven.Resource;
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
            known = gui.getInventory().getItems(MINED_ITEMS);

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

                ArrayList<WItem> cur = gui.getInventory().getItems(MINED_ITEMS);
                // Обрабатываем все новые камни, а не только первый
                ArrayList<WItem> newItems = new ArrayList<>();
                for (WItem it : cur) {
                    if (!known.contains(it)) {
                        newItems.add(it);
                    }
                }
                if (newItems.isEmpty()) {
                    NUtils.addTask(new WaitTicks(5));
                    continue;
                }
                known = cur;
                
                // Обрабатываем каждый новый камень
                for (WItem newItem : newItems) {
                    processNewStone(gui, newItem, wnd);
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
     * Обрабатывает один новый камень
     */
    private void processNewStone(NGameUI gui, WItem newItem, MasterMinerWnd wnd) throws InterruptedException {
        NGItem dropped = (NGItem) newItem.item;
        if (dropped.quality == null) {
            // на всякий случай дожидаемся качества
            WItem finalNewItem = newItem;
            NUtils.addTask(new NTask() {
                { this.infinite = true; }
                @Override
                public boolean check() {
                    NGItem gi = (NGItem) finalNewItem.item;
                    return gi.name() != null && gi.quality != null;
                }
            });
        }
        if (dropped.quality == null) {
            NUtils.addTask(new WaitTicks(2));
            return;
        }

        double f3 = dropped.quality;
        String stoneName = dropped.name();
        String stoneType = classifyStoneType(stoneName);

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
            if ("Квариарц".equals(stoneType)) {
                // Новая формула для квариарца: f3 - f4 + f3 = 2*f3 - f4
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
                if ("Квариарц".equals(stoneType)) {
                    // Для квариарца: обратная формула от wallQ = 2*f3 - f4
                    // Если wallQ = 2*f3_new - f4_new, то f3_new = (wallQ + f4_new) / 2
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
                wnd.incrementCounter();
                
                // Проверяем, нужно ли поставить метку на карте согласно настройкам
                nurgling.conf.NMasterMinerMarkingConfig markingConfig = nurgling.conf.NMasterMinerMarkingConfig.get();
                if (markingConfig != null) {
                    Boolean enabled = markingConfig.isEnabled(stoneName);
                    Double threshold = markingConfig.getThreshold(stoneName);
                    
                    // Если элемент включен в настройках и качество в стене >= порога
                    if (enabled != null && enabled) {
                        double itemThreshold = (threshold != null && !threshold.isNaN()) ? threshold : 10.0;
                        if (wallQ >= itemThreshold) {
                            if ("Quarryartz".equals(stoneType)) {
                                // Квариарц ставится четко в месте выкопан
                                addQuarryartzMarker(gui, stoneName, wallQ);
                            } else {
                                // Остальные камни и руды - система спотов (обновление в радиусе 30 клеток)
                                boolean isOreType = isOre(stoneName) || 
                                                   stoneName.equals("Black Coal") || 
                                                   stoneName.equals("Quartz") || 
                                                   stoneName.equals("Flint");
                                // Все камни ставим как споты (обновление в радиусе 30 клеток)
                                addOreSpotMarker(gui, stoneName, wallQ);
                            }
                        }
                    }
                }
            }

            // проверка порога и сброс камня (только если не стак)
            // Сброс происходит по фактическому качеству камня (f3), а не по qWall
            double threshold = wnd.getDropThreshold();
            if (!Double.isNaN(threshold) && f3 < threshold) {
                // проверяем, что это действительно камень из инвентаря, а не инструмент
                if (newItem != null && newItem.parent == gui.getInventory()) {
                    // дополнительная проверка: убеждаемся, что это не инструмент
                    String itemName = stoneName != null ? stoneName.toLowerCase() : "";
                    boolean isTool = itemName.contains("axe") || itemName.contains("pickaxe") || 
                                   itemName.contains("топор") || itemName.contains("кирк");
                    // проверяем, что это не стак (проверяем через Amount)
                    boolean isStack = false;
                    try {
                        haven.GItem.Amount amount = ((NGItem) newItem.item).getInfo(haven.GItem.Amount.class);
                        if (amount != null && amount.itemnum() > 1) {
                            isStack = true;
                        }
                    } catch (Exception ignored) {
                        // если не удалось проверить, считаем что не стак
                    }
                    if (!isTool && !isStack) {
                        // небольшая задержка перед сбросом, чтобы игра успела обработать появление камня
                        NUtils.addTask(new WaitTicks(3));
                        // еще раз проверяем, что предмет все еще в инвентаре
                        if (newItem.parent == gui.getInventory()) {
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
     * Ставит четко в месте выкопан, не обновляет если рядом есть маркер
     */
    private void addQuarryartzMarker(NGameUI gui, String stoneName, double wallQ) {
        // Выполняем в отдельном потоке, чтобы избежать лагов
        Thread markerThread = new Thread(() -> {
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
                // Если есть - не ставим новый (квариарц ставится четко в месте выкопан)
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
                }
            } catch (Exception e) {
                // Игнорируем ошибки
            }
        });
        markerThread.setDaemon(true);
        markerThread.start();
    }
    
    /**
     * Добавляет или обновляет метку спота руды на карте
     * Обновляет маркер в радиусе 30 клеток если качество выше
     */
    private void addOreSpotMarker(NGameUI gui, String oreName, double wallQ) {
        // Выполняем в отдельном потоке, чтобы избежать лагов
        Thread markerThread = new Thread(() -> {
            try {
                if (gui.mmap == null || gui.mmap.sessloc == null || gui.labeledMarkService == null) {
                    gui.msg("Ошибка: карта или сервис меток не инициализирован", java.awt.Color.RED);
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
                
                // Ищем существующий маркер этой руды в радиусе 30 клеток (30*11 = 330 единиц)
                // Радиус 30 тайлов для проверки
                java.util.List<nurgling.widgets.LabeledMinimapMark> existingMarks = 
                    gui.labeledMarkService.getMarksByResourceType(oreName);
                
                nurgling.widgets.LabeledMinimapMark nearbyMark = null;
                for (nurgling.widgets.LabeledMinimapMark mark : existingMarks) {
                    if (mark.segmentId == segmentId) {
                        int distX = Math.abs(mark.tileCoords.x - tileCoords.x);
                        int distY = Math.abs(mark.tileCoords.y - tileCoords.y);
                        // Проверяем радиус 30 тайлов
                        if (distX <= 30 && distY <= 30) {
                            nearbyMark = mark;
                            break;
                        }
                    }
                }
                
                if (nearbyMark != null) {
                    // Маркер найден рядом - проверяем качество
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
                            BufferedImage iconImage = getOreIcon(oreName);
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
                    BufferedImage iconImage = getOreIcon(oreName);
                    // Создаем маркер даже если иконка не загрузилась (используется fallback)
                    gui.labeledMarkService.addLabeledMark(label, oreName, segmentId, tileCoords, iconImage);
                    gui.msg("Маркер создан: " + oreName + " " + label + " на тайле (" + tileCoords.x + "," + tileCoords.y + ")", java.awt.Color.GREEN);
                }
            } catch (Exception e) {
                // Игнорируем ошибки
            }
        });
        markerThread.setDaemon(true);
        markerThread.start();
    }
    
    /**
     * Получает иконку руды из ресурсов игры
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

