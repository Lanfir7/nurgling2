package nurgling.widgets.bots;

import haven.Button;
import haven.Coord;
import haven.Gob;
import haven.Label;
import haven.Text;
import haven.TextEntry;
import haven.UI;
import haven.Window;
import haven.WItem;
import nurgling.actions.bots.MasterMiner;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.conf.NMasterMinerProp;
import nurgling.widgets.NEquipory;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;

/**
 * Информативное окно для МастерМайнер.
 * Показывает качества и рассчитанное "качество в стене" (wallQ) + максимум.
 */
public class MasterMinerWnd extends Window {
    private volatile boolean closed = false;

    private final Label masonryLbl;
    private final Label stoneLbl;      // Камень (любой кроме квариарц)
    private final Label quarryartzLbl; // Квариарц
    private final Label catGoldLbl;    // Кэт голд
    private final Label rakuhLbl;       // Ракуха
    private final Label counterLbl;    // Счетчик выкопанных камней
    private final TextEntry thresholdEntry; // Порог сброса для камней
    private final TextEntry shellCatGoldThresholdEntry; // Порог сброса для ракух и кэтголдов
    private MasterMiner masterMinerBot; // Ссылка на бот для переключения инструментов

    private int totalStonesMined = 0;
    private final Text.Foundry boldFoundry;
    private final Color masonryColor = new Color(255, 215, 0); // золотой цвет
    
    // Структура для хранения лучших значений по каждому типу камня
    private static class BestStoneData {
        String stoneName;  // Название конкретного камня
        double f3;         // Качество в рюкзаке
        double wallQ;      // Качество в стене
        Double bestAltQ;   // Лучшее качество с другим инструментом
    }
    
    private BestStoneData bestStone = null;      // Лучший обычный камень
    private BestStoneData bestQuarryartz = null;  // Лучший квариарц
    private BestStoneData bestCatGold = null;     // Лучший кэт голд
    private BestStoneData bestRakuh = null;       // Лучшая ракуха

    public MasterMinerWnd() {
        super(new Coord(UI.scale(550), UI.scale(380)), "Master Miner"); // Увеличено для полного отображения текста

        // Создаем жирный шрифт для Masonry
        Font boldFont = Text.std.font.deriveFont(Font.BOLD);
        boldFoundry = new Text.Foundry(boldFont, masonryColor);

        // Загружаем сохраненные настройки
        NMasterMinerProp prop = loadSettings();
        String savedDropThreshold = "";
        String savedShellCatGoldThreshold = "";
        if (prop != null) {
            if (!Float.isNaN(prop.dropThreshold)) {
                savedDropThreshold = String.valueOf((int)prop.dropThreshold == prop.dropThreshold ? 
                    (int)prop.dropThreshold : prop.dropThreshold);
            }
            if (!Float.isNaN(prop.shellCatGoldThreshold)) {
                savedShellCatGoldThreshold = String.valueOf((int)prop.shellCatGoldThreshold == prop.shellCatGoldThreshold ? 
                    (int)prop.shellCatGoldThreshold : prop.shellCatGoldThreshold);
            }
        }

        Coord pad = UI.scale(8, 6);
        Coord cur = pad;

        masonryLbl = add(new Label("Masonry: (waiting)", boldFoundry), cur);
        masonryLbl.setcolor(masonryColor);
        cur = masonryLbl.pos("bl").add(0, UI.scale(4));

        stoneLbl = add(new Label("Stone: -"), cur);
        cur = stoneLbl.pos("bl").add(0, UI.scale(4));

        quarryartzLbl = add(new Label("Quarryartz: -"), cur);
        cur = quarryartzLbl.pos("bl").add(0, UI.scale(4));

        catGoldLbl = add(new Label("Cat Gold: -"), cur);
        cur = catGoldLbl.pos("bl").add(0, UI.scale(4));

        rakuhLbl = add(new Label("Shell: -"), cur);
        cur = rakuhLbl.pos("bl").add(0, UI.scale(6));

        counterLbl = add(new Label("Mined: 0"), cur);
        cur = counterLbl.pos("bl").add(0, UI.scale(6));

        add(new Label("Drop threshold:"), cur);
        cur = cur.add(UI.scale(0, UI.scale(18)));
        thresholdEntry = add(new TextEntry(UI.scale(80), savedDropThreshold) {
            @Override
            public void changed() {
                super.changed();
                // Сохраняем при изменении текста
                saveSettings();
            }
        }, cur);
        Coord setBtn1Pos = thresholdEntry.pos("ur").add(UI.scale(5), -UI.scale(4));
        add(new Button(UI.scale(40), "Set") {
            @Override
            public void click() {
                super.click();
                saveSettings();
            }
        }, setBtn1Pos);
        cur = thresholdEntry.pos("bl").add(0, UI.scale(6));
        
        // Порог сброса для ракух и кэтголдов
        add(new Label("Drop threshold (Shell/Cat Gold):"), cur);
        cur = cur.add(UI.scale(0, UI.scale(18)));
        shellCatGoldThresholdEntry = add(new TextEntry(UI.scale(80), savedShellCatGoldThreshold) {
            @Override
            public void changed() {
                super.changed();
                // Сохраняем при изменении текста
                saveSettings();
            }
        }, cur);
        Coord setBtn2Pos = shellCatGoldThresholdEntry.pos("ur").add(UI.scale(5), -UI.scale(4));
        add(new Button(UI.scale(40), "Set") {
            @Override
            public void click() {
                super.click();
                saveSettings();
            }
        }, setBtn2Pos);
        cur = shellCatGoldThresholdEntry.pos("bl").add(0, UI.scale(6));

        // Кнопка Switch для смены кирки/топора между руками и рюкзаком
        add(new Button(UI.scale(160), "Switch") {
            @Override
            public void click() {
                super.click();
                switchMiningTool();
            }
        }, cur);
        cur = cur.add(0, UI.scale(26));

        add(new Button(UI.scale(160), "Reset All") {
            @Override
            public void click() {
                super.click();
                totalStonesMined = 0;
                counterLbl.settext("Mined: 0");
                bestStone = null;
                bestQuarryartz = null;
                bestCatGold = null;
                bestRakuh = null;
                stoneLbl.settext("Stone: -");
                quarryartzLbl.settext("Quarryartz: -");
                catGoldLbl.settext("Cat Gold: -");
                rakuhLbl.settext("Shell: -");
            }
        }, cur);

        pack();
    }

    public boolean isClosed() {
        return closed;
    }

    public void setMasonry(int masonry) {
        // Обновляем текст с сохранением жирного шрифта и цвета
        String newText = "Masonry: " + masonry;
        masonryLbl.text.dispose();
        masonryLbl.text = boldFoundry.render(newText, masonryColor);
        masonryLbl.texts = newText;
        masonryLbl.col = masonryColor;
        masonryLbl.f = boldFoundry;
        masonryLbl.resize(masonryLbl.text.sz());
    }

    public void setStoneInfo(String stoneType, String stoneName, double f3, double wallQ, Double bestAltQ, int masonry, MasterMiner.ToolSet toolSet, MasterMiner.ToolType currentToolType) {
        BestStoneData data = new BestStoneData();
        data.stoneName = stoneName;
        data.f3 = f3;
        data.wallQ = wallQ;
        data.bestAltQ = bestAltQ;
        
        // Обновляем лучшее значение, если текущее лучше (по wallQ)
        BestStoneData currentBest = null;
        boolean isNewBest = false;
        switch (stoneType) {
            case "Stone":
                if (bestStone == null || wallQ > bestStone.wallQ) {
                    bestStone = data;
                    isNewBest = true;
                }
                currentBest = bestStone;
                break;
            case "Quarryartz":
                // Для квариарца wallQ должно быть одинаковым для всех инструментов в одной клетке
                // Используем первое рассчитанное wallQ и не обновляем его при копании другими инструментами
                // Обновляем только если это первое значение или если wallQ значительно больше (другая клетка)
                if (bestQuarryartz == null) {
                    bestQuarryartz = data;
                    isNewBest = true;
                } else {
                    // Если wallQ отличается более чем на 2.0, считаем что это другая клетка
                    // Иначе используем уже сохраненное wallQ (оно должно быть одинаковым для всех инструментов)
                    double diff = Math.abs(wallQ - bestQuarryartz.wallQ);
                    if (diff > 2.0) {
                        // Это другая клетка - обновляем если лучше
                        if (wallQ > bestQuarryartz.wallQ) {
                            bestQuarryartz = data;
                            isNewBest = true;
                        }
                    } else {
                        // Это та же клетка - используем уже сохраненное wallQ (оно одинаково для всех инструментов)
                        // Обновляем только f3 для текущего инструмента
                        bestQuarryartz.f3 = data.f3; // Обновляем f3 для текущего инструмента
                        bestQuarryartz.stoneName = data.stoneName; // Обновляем название
                        // wallQ НЕ обновляем - оно должно быть одинаковым для всех инструментов
                        // bestAltQ будет пересчитан ниже на основе сохраненного wallQ
                    }
                }
                currentBest = bestQuarryartz;
                break;
            case "Cat Gold":
                if (bestCatGold == null || wallQ > bestCatGold.wallQ) {
                    bestCatGold = data;
                    isNewBest = true;
                }
                currentBest = bestCatGold;
                break;
            case "Shell":
                if (bestRakuh == null || wallQ > bestRakuh.wallQ) {
                    bestRakuh = data;
                    isNewBest = true;
                }
                currentBest = bestRakuh;
                break;
        }
        
        if (currentBest == null) return;
        
        // Для квариарца всегда пересчитываем bestAltQ, так как wallQ должно быть одинаковым для всех инструментов
        // Для остальных камней пересчитываем только если это новое лучшее значение
        boolean shouldRecalculate = isNewBest;
        if ("Quarryartz".equals(stoneType)) {
            shouldRecalculate = true; // Всегда пересчитываем для квариарца
        }
        
        // Пересчитываем bestAltQ на основе текущих инструментов
        if (shouldRecalculate && toolSet != null) {
            Double recalculatedBestAltQ = null;
            if (currentToolType != MasterMiner.ToolType.STONE_AXE && toolSet.stoneAxeQ != null) {
                Double pred;
                if ("Quarryartz".equals(stoneType)) {
                    // Для квариарца: обратная формула от wallQ = 2*f3 - f4
                    // f3 = (wallQ + f4) / 2
                    pred = (currentBest.wallQ + toolSet.stoneAxeQ) / 2.0;
                } else {
                    pred = MasterMiner.invDropQ(currentBest.wallQ, toolSet.stoneAxeQ, 0.8);
                }
                if (recalculatedBestAltQ == null || (pred != null && pred > recalculatedBestAltQ)) recalculatedBestAltQ = pred;
            }
            if (currentToolType != MasterMiner.ToolType.TINKER_AXE && toolSet.tinkerAxeQ != null) {
                Double pred;
                if ("Quarryartz".equals(stoneType)) {
                    // Для квариарца: обратная формула от wallQ = 2*f3 - f4
                    // f3 = (wallQ + f4) / 2
                    pred = (currentBest.wallQ + toolSet.tinkerAxeQ) / 2.0;
                } else {
                    pred = MasterMiner.invDropQ(currentBest.wallQ, toolSet.tinkerAxeQ, 0.9);
                }
                if (recalculatedBestAltQ == null || (pred != null && pred > recalculatedBestAltQ)) recalculatedBestAltQ = pred;
            }
            if (currentToolType != MasterMiner.ToolType.PICKAXE && toolSet.pickaxeQ != null) {
                Double pred;
                if ("Quarryartz".equals(stoneType)) {
                    // Для квариарца: обратная формула от wallQ = 2*f3 - f4
                    // f3 = (wallQ + f4) / 2
                    pred = (currentBest.wallQ + toolSet.pickaxeQ) / 2.0;
                } else {
                    pred = MasterMiner.invDropQ(currentBest.wallQ, toolSet.pickaxeQ, 1.0);
                }
                if (recalculatedBestAltQ == null || (pred != null && pred > recalculatedBestAltQ)) recalculatedBestAltQ = pred;
            }
            currentBest.bestAltQ = recalculatedBestAltQ;
        }
        
        // Формируем текст с названием камня (для "Stone" показываем конкретное название)
        String displayName = stoneType;
        if ("Stone".equals(stoneType) && currentBest.stoneName != null && !currentBest.stoneName.isEmpty()) {
            displayName = currentBest.stoneName;
        }
        
        String text;
        if (currentBest.bestAltQ != null && !currentBest.bestAltQ.isNaN() && !currentBest.bestAltQ.isInfinite()) {
            text = String.format("%s: %.2f [%.2f] (%.2f)", displayName, currentBest.f3, currentBest.wallQ, currentBest.bestAltQ);
        } else {
            text = String.format("%s: %.2f [%.2f]", displayName, currentBest.f3, currentBest.wallQ);
        }

        Label targetLabel = null;
        switch (stoneType) {
            case "Stone":
                targetLabel = stoneLbl;
                break;
            case "Quarryartz":
                targetLabel = quarryartzLbl;
                break;
            case "Cat Gold":
                targetLabel = catGoldLbl;
                break;
            case "Shell":
                targetLabel = rakuhLbl;
                break;
        }
        
        if (targetLabel != null) {
            targetLabel.settext(text);
            // Применяем цвет только для Stone, Quarryartz и Cat Gold
            if ("Stone".equals(stoneType) || "Quarryartz".equals(stoneType) || "Cat Gold".equals(stoneType)) {
                updateWallQColor(targetLabel, currentBest.wallQ, masonry);
            } else {
                targetLabel.setcolor(Color.WHITE);
            }
        }
    }
    
    private Color getWallQColor(double wallQ, int masonry) {
        // Красный когда примерно равно masonry (±1)
        if (wallQ >= masonry - 1.0 && wallQ <= masonry + 1.0) {
            return Color.RED;
        }
        // Оранжевый когда на ~10 меньше masonry (диапазон от masonry-11 до masonry-9)
        double diff = masonry - wallQ;
        if (diff >= 9.0 && diff <= 11.0) {
            return new Color(255, 165, 0); // Оранжевый
        }
        return Color.WHITE; // Белый по умолчанию
    }
    
    private void updateWallQColor(Label lbl, double wallQ, int masonry) {
        // Всегда применяем цвет, даже если белый
        Color color = getWallQColor(wallQ, masonry);
        lbl.setcolor(color);
        // Также обновляем цвет текста напрямую
        lbl.col = color;
    }

    public void incrementCounter() {
        totalStonesMined++;
        counterLbl.settext("Mined: " + totalStonesMined);
    }

    public double getDropThreshold() {
        try {
            String txt = thresholdEntry.text().trim();
            if (txt.isEmpty()) return Double.NaN;
            return Double.parseDouble(txt.replace(',', '.'));
        } catch (Exception e) {
            return Double.NaN;
        }
    }
    
    public double getShellCatGoldThreshold() {
        try {
            String txt = shellCatGoldThresholdEntry.text().trim();
            if (txt.isEmpty()) return Double.NaN;
            return Double.parseDouble(txt.replace(',', '.'));
        } catch (Exception e) {
            return Double.NaN;
        }
    }
    
    
    /**
     * Загружает сохраненные настройки
     */
    private NMasterMinerProp loadSettings() {
        try {
            if (NUtils.getUI() != null && NUtils.getUI().sessInfo != null) {
                return NMasterMinerProp.get(NUtils.getUI().sessInfo);
            }
        } catch (Exception e) {
            // Игнорируем ошибки загрузки
        }
        return null;
    }
    
    /**
     * Сохраняет текущие настройки
     */
    private void saveSettings() {
        try {
            if (NUtils.getUI() != null && NUtils.getUI().sessInfo != null) {
                NMasterMinerProp prop = NMasterMinerProp.get(NUtils.getUI().sessInfo);
                if (prop == null) {
                    if (NUtils.getGameUI() != null && NUtils.getGameUI().getCharInfo() != null) {
                        prop = new NMasterMinerProp(NUtils.getUI().sessInfo.username, 
                                                    NUtils.getGameUI().getCharInfo().chrid);
                    } else {
                        return;
                    }
                }
                
                // Сохраняем порог сброса для камней
                try {
                    String dropText = thresholdEntry.text().trim();
                    if (!dropText.isEmpty()) {
                        prop.dropThreshold = Float.parseFloat(dropText.replace(',', '.'));
                    } else {
                        prop.dropThreshold = Float.NaN;
                    }
                } catch (Exception e) {
                    prop.dropThreshold = Float.NaN;
                }
                
                // Сохраняем порог сброса для ракух и кэтголдов
                try {
                    String shellCatGoldText = shellCatGoldThresholdEntry.text().trim();
                    if (!shellCatGoldText.isEmpty()) {
                        prop.shellCatGoldThreshold = Float.parseFloat(shellCatGoldText.replace(',', '.'));
                    } else {
                        prop.shellCatGoldThreshold = Float.NaN;
                    }
                } catch (Exception e) {
                    prop.shellCatGoldThreshold = Float.NaN;
                }
                
                NMasterMinerProp.set(prop);
            }
        } catch (Exception e) {
            // Игнорируем ошибки сохранения
        }
    }

    /**
     * Переключает инструмент майнинга между руками и инвентарем
     * Выполняется асинхронно через Action, чтобы не блокировать UI поток
     */
    private void switchMiningTool() {
        // Запускаем в отдельном потоке, чтобы не блокировать UI
        Thread switchThread = new Thread(() -> {
            try {
                NGameUI gui = NUtils.getGameUI();
                if (gui == null) return;
                
                // Создаем и запускаем Action для переключения инструментов
                SwitchMiningToolAction action = new SwitchMiningToolAction();
                action.run(gui);
            } catch (Exception e) {
                // Игнорируем ошибки
            }
        });
        switchThread.setDaemon(true);
        switchThread.start();
    }
    
    /**
     * Action для переключения инструментов майнинга между руками и инвентарем
     */
    private static class SwitchMiningToolAction implements nurgling.actions.Action {
        @Override
        public nurgling.actions.Results run(NGameUI gui) throws InterruptedException {
            WItem lhand = NUtils.getEquipment().findItem(NEquipory.Slots.HAND_LEFT.idx);
            WItem rhand = NUtils.getEquipment().findItem(NEquipory.Slots.HAND_RIGHT.idx);
            WItem wbelt = NUtils.getEquipment().findItem(NEquipory.Slots.BELT.idx);
            
            // Определяем текущий инструмент в руках
            WItem currentTool = null;
            int handSlot = -1;
            if (MasterMiner.isKnownMiningTool(lhand)) {
                currentTool = lhand;
                handSlot = NEquipory.Slots.HAND_LEFT.idx;
            } else if (MasterMiner.isKnownMiningTool(rhand)) {
                currentTool = rhand;
                handSlot = NEquipory.Slots.HAND_RIGHT.idx;
            }
            
            if (currentTool != null) {
                // Инструмент в руках - перекладываем в инвентарь
                NUtils.takeItemToHand(currentTool);
                
                // Пытаемся положить в инвентарь (не в пояс)
                Coord pos = gui.getInventory().getFreeCoord(NUtils.getGameUI().vhand);
                if (pos != null) {
                    gui.getInventory().dropOn(pos, ((NGItem) NUtils.getGameUI().vhand.item).name());
                } else {
                    // Если нет места в инвентаре, пробуем в пояс
                    if (wbelt != null && wbelt.item.contents instanceof NInventory) {
                        NInventory beltInv = (NInventory) wbelt.item.contents;
                        if (beltInv.getFreeSpace() > 0) {
                            NUtils.transferToBelt();
                        } else {
                            return nurgling.actions.Results.ERROR("No free space in inventory or belt");
                        }
                    } else {
                        return nurgling.actions.Results.ERROR("No free space in inventory");
                    }
                }
                
                NUtils.getEquipment().wdgmsg("drop", handSlot);
                // Ждем освобождения руки перед поиском другого инструмента
                NUtils.getUI().core.addTask(new nurgling.tasks.WaitFreeHand());
                
                // Теперь ищем другой инструмент для экипировки
                WItem toolInBelt = null;
                WItem toolInInv = null;
                
                // Сначала ищем в инвентаре
                ArrayList<WItem> invItems = gui.getInventory().getItems();
                for (WItem item : invItems) {
                    if (MasterMiner.isKnownMiningTool(item) && item != currentTool) {
                        toolInInv = item;
                        break;
                    }
                }
                
                // Если не нашли в инвентаре, ищем в поясе
                if (toolInInv == null && wbelt != null && wbelt.item.contents instanceof NInventory) {
                    NInventory beltInv = (NInventory) wbelt.item.contents;
                    ArrayList<WItem> beltItems = beltInv.getItems();
                    for (WItem item : beltItems) {
                        if (MasterMiner.isKnownMiningTool(item) && item != currentTool) {
                            toolInBelt = item;
                            break;
                        }
                    }
                }
                
                WItem toolToEquip = toolInInv != null ? toolInInv : toolInBelt;
                if (toolToEquip != null) {
                    // Берем инструмент в руки
                    NUtils.takeItemToHand(toolToEquip);
                    
                    // Определяем в какую руку экипировать (в освобожденную)
                    NEquipory.Slots slot = (handSlot == NEquipory.Slots.HAND_LEFT.idx)
                            ? NEquipory.Slots.HAND_LEFT
                            : NEquipory.Slots.HAND_RIGHT;
                    
                    NUtils.getEquipment().wdgmsg("drop", handSlot);
                    NUtils.getUI().core.addTask(new nurgling.tasks.WaitItemInEquip(toolToEquip, new NEquipory.Slots[]{slot}));
                    
                    // Активируем курсор майнинга после переодевания
                    NUtils.getUI().core.addTask(new nurgling.tasks.NTask() {
                        @Override
                        public boolean check() {
                            try {
                                Gob player = NUtils.player();
                                if (player != null) {
                                    NUtils.mine(player.rc);
                                }
                            } catch (Exception ignored) {
                            }
                            return true;
                        }
                    });
                }
                
            } else {
                // Инструмента нет в руках - ищем в инвентаре и поясе
                WItem toolInBelt = null;
                WItem toolInInv = null;
                
                // Сначала ищем в инвентаре
                ArrayList<WItem> invItems = gui.getInventory().getItems();
                for (WItem item : invItems) {
                    if (MasterMiner.isKnownMiningTool(item)) {
                        toolInInv = item;
                        break;
                    }
                }
                
                // Если не нашли в инвентаре, ищем в поясе
                if (toolInInv == null && wbelt != null && wbelt.item.contents instanceof NInventory) {
                    NInventory beltInv = (NInventory) wbelt.item.contents;
                    ArrayList<WItem> beltItems = beltInv.getItems();
                    for (WItem item : beltItems) {
                        if (MasterMiner.isKnownMiningTool(item)) {
                            toolInBelt = item;
                            break;
                        }
                    }
                }
                
                WItem toolToEquip = toolInInv != null ? toolInInv : toolInBelt;
                if (toolToEquip == null) {
                    return nurgling.actions.Results.ERROR("No mining tool found in inventory or belt");
                }
                
                // Освобождаем руку если обе заняты
                if (lhand != null && rhand != null) {
                    WItem handToFree = lhand;
                    NUtils.takeItemToHand(handToFree);
                    
                    // Пытаемся положить в инвентарь
                    Coord freePos = gui.getInventory().getFreeCoord(NUtils.getGameUI().vhand);
                    if (freePos != null) {
                        gui.getInventory().dropOn(freePos, ((NGItem) NUtils.getGameUI().vhand.item).name());
                    } else {
                        // Если нет места, пробуем в пояс
                        if (wbelt != null && wbelt.item.contents instanceof NInventory) {
                            NInventory beltInv = (NInventory) wbelt.item.contents;
                            if (beltInv.getFreeSpace() > 0) {
                                NUtils.transferToBelt();
                            } else {
                                return nurgling.actions.Results.ERROR("No free space to free hand");
                            }
                        } else {
                            return nurgling.actions.Results.ERROR("No free space to free hand");
                        }
                    }
                    
                    NUtils.getEquipment().wdgmsg("drop", NEquipory.Slots.HAND_LEFT.idx);
                    NUtils.getUI().core.addTask(new nurgling.tasks.WaitFreeHand());
                }
                
                // Берем инструмент в руки
                NUtils.takeItemToHand(toolToEquip);
                
                // Определяем в какую руку экипировать
                int targetSlot = (lhand == null) ? NEquipory.Slots.HAND_LEFT.idx : NEquipory.Slots.HAND_RIGHT.idx;
                NEquipory.Slots slot = (targetSlot == NEquipory.Slots.HAND_LEFT.idx)
                        ? NEquipory.Slots.HAND_LEFT
                        : NEquipory.Slots.HAND_RIGHT;
                
                NUtils.getEquipment().wdgmsg("drop", targetSlot);
                NUtils.getUI().core.addTask(new nurgling.tasks.WaitItemInEquip(toolToEquip, new NEquipory.Slots[]{slot}));
                
                // Активируем курсор майнинга после переодевания
                NUtils.getUI().core.addTask(new nurgling.tasks.NTask() {
                    @Override
                    public boolean check() {
                        try {
                            Gob player = NUtils.player();
                            if (player != null) {
                                NUtils.mine(player.rc);
                            }
                        } catch (Exception ignored) {
                        }
                        return true;
                    }
                });
            }
            
            return nurgling.actions.Results.SUCCESS();
        }
    }

    @Override
    public void wdgmsg(String msg, Object... args) {
        if ("close".equals(msg)) {
            closed = true;
            // Сохраняем настройки при закрытии окна
            saveSettings();
            hide();
        }
        super.wdgmsg(msg, args);
    }
}

