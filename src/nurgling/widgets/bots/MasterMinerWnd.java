package nurgling.widgets.bots;

import haven.Button;
import haven.Coord;
import haven.Gob;
import haven.Label;
import haven.OCache;
import haven.Text;
import haven.TextEntry;
import haven.UI;
import haven.Window;
import haven.Widget;
import haven.WItem;
import nurgling.actions.bots.MasterMiner;
import nurgling.actions.bots.PickupGroundItems;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.conf.NMasterMinerProp;
import nurgling.i18n.L10n;
import nurgling.sessions.BotExecutor;
import nurgling.widgets.NEquipory;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Информативное окно для МастерМайнер.
 * Показывает качества и рассчитанное "качество в стене" (wallQ) + максимум.
 */
public class MasterMinerWnd extends Window {
    private volatile boolean closed = false;

    private final Label masonryLbl;
    private final Label lastMinedLbl;  // Последний выкопанный камень
    private final Label stoneLbl;      // Камень (любой кроме квариарц)
    private final Label quarryartzLbl; // Квариарц
    private final Label catGoldLbl;    // Кэт голд
    private final Label rakuhLbl;       // Ракуха
    private final Label counterLbl;    // Счетчик выкопанных камней
    private final TextEntry thresholdEntry; // Порог сброса для камней
    private final TextEntry shellCatGoldThresholdEntry; // Порог сброса для ракух и кэтголдов
    private final TextEntry keepStonesEntry; // Сколько камней держать для подпорки
    private MasterMiner masterMinerBot; // Ссылка на бот для переключения инструментов

    private int totalStonesMined = 0;
    private final Text.Foundry boldFoundry;
    private final Color masonryColor = new Color(255, 215, 0); // золотой цвет
    private Coord savedWindowPos = null;
    private Coord lastPersistedPos = null;
    private final Widget groundRow;
    private final Map<String, MasterMinerGroundIcon> groundIcons = new LinkedHashMap<>();
    private double groundScanAcc = 0;
    private volatile Thread pickupThread;
    
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
        super(new Coord(UI.scale(280), UI.scale(1)), L10n.get("bot.masterminer.title"));

        Font boldFont = Text.std.font.deriveFont(Font.BOLD);
        boldFoundry = new Text.Foundry(boldFont, masonryColor);

        NMasterMinerProp prop = loadSettings();
        String savedDropThreshold = "";
        String savedShellCatGoldThreshold = "";
        String savedKeepStones = "30";
        if (prop != null) {
            if (!Float.isNaN(prop.dropThreshold)) {
                savedDropThreshold = String.valueOf((int)prop.dropThreshold == prop.dropThreshold ? 
                    (int)prop.dropThreshold : prop.dropThreshold);
            }
            if (!Float.isNaN(prop.shellCatGoldThreshold)) {
                savedShellCatGoldThreshold = String.valueOf((int)prop.shellCatGoldThreshold == prop.shellCatGoldThreshold ? 
                    (int)prop.shellCatGoldThreshold : prop.shellCatGoldThreshold);
            }
            savedKeepStones = String.valueOf(prop.keepStonesForSupport);
            if (prop.hasWindowPos()) {
                savedWindowPos = new Coord(prop.wndX, prop.wndY);
                lastPersistedPos = savedWindowPos;
            }
        }

        final int pad = UI.scale(10);
        final int gap = UI.scale(8);
        final int entryW = UI.scale(72);
        final int setW = UI.scale(56);
        final int contentW = UI.scale(300);
        Coord cur = new Coord(pad, UI.scale(8));

        groundRow = add(new Widget(new Coord(contentW - pad * 2, UI.scale(42))), cur);
        cur = groundRow.pos("bl").add(0, gap);

        masonryLbl = add(new Label(masonryWaitingText(), boldFoundry), cur);
        masonryLbl.setcolor(masonryColor);
        cur = masonryLbl.pos("bl").add(0, gap);

        Label lastMinedCap = add(new Label(lastMinedCaptionText()), cur);
        cur = lastMinedCap.pos("bl").add(0, UI.scale(2));
        lastMinedLbl = add(new Label(lastMinedValueText(null, 0, 0)), cur.add(UI.scale(6), 0));
        cur = lastMinedLbl.pos("bl").add(-UI.scale(6), gap);

        stoneLbl = add(new Label("Stone: -"), cur);
        cur = stoneLbl.pos("bl").add(0, UI.scale(3));
        quarryartzLbl = add(new Label("Quarryartz: -"), cur);
        cur = quarryartzLbl.pos("bl").add(0, UI.scale(3));
        catGoldLbl = add(new Label("Cat Gold: -"), cur);
        cur = catGoldLbl.pos("bl").add(0, UI.scale(3));
        rakuhLbl = add(new Label("Shell: -"), cur);
        cur = rakuhLbl.pos("bl").add(0, UI.scale(2));

        Label legend = add(new Label(L10n.get("bot.masterminer.q_legend")), cur);
        legend.setcolor(new Color(170, 170, 170));
        cur = legend.pos("bl").add(0, gap);

        counterLbl = add(new Label(minedText()), cur);
        cur = counterLbl.pos("bl").add(0, UI.scale(10));

        thresholdEntry = new TextEntry(entryW, savedDropThreshold) {
            @Override
            public void changed() {
                super.changed();
                saveSettings();
            }
        };
        cur = addSettingRow(cur, L10n.get("bot.masterminer.drop_threshold"), thresholdEntry, setW, gap);

        shellCatGoldThresholdEntry = new TextEntry(entryW, savedShellCatGoldThreshold) {
            @Override
            public void changed() {
                super.changed();
                saveSettings();
            }
        };
        cur = addSettingRow(cur, L10n.get("bot.masterminer.drop_threshold_shell"), shellCatGoldThresholdEntry, setW, gap);

        keepStonesEntry = new TextEntry(entryW, savedKeepStones) {
            @Override
            public void changed() {
                super.changed();
                saveSettings();
            }
        };
        Label keepStonesLabel = add(new Label(L10n.get("bot.masterminer.keep_stones")), cur);
        add(keepStonesEntry, keepStonesLabel.pos("bl").add(0, UI.scale(2)));
        add(new Button(setW, L10n.get("bot.masterminer.set")) {
            @Override
            public void click() {
                super.click();
                saveSettings();
            }
        }, keepStonesEntry.pos("ur").add(UI.scale(6), -UI.scale(4)));
        int collectW = contentW - (pad * 2) - entryW - setW - UI.scale(12);
        add(new Button(collectW, L10n.get("bot.masterminer.collect_support")) {
            @Override
            public void click() {
                super.click();
                collectSupportStones();
            }
        }, keepStonesEntry.pos("ur").add(setW + UI.scale(12), -UI.scale(4)));
        cur = keepStonesEntry.pos("bl").add(0, gap);
        cur = cur.add(0, UI.scale(4));

        int btnW = contentW - pad * 2;
        add(new Button(btnW, L10n.get("bot.masterminer.switch")) {
            @Override
            public void click() {
                super.click();
                switchMiningTool();
            }
        }, cur);
        cur = cur.add(0, UI.scale(28));

        add(new Button(btnW, L10n.get("bot.masterminer.reset_all")) {
            @Override
            public void click() {
                super.click();
                totalStonesMined = 0;
                counterLbl.settext(minedText());
                bestStone = null;
                bestQuarryartz = null;
                bestCatGold = null;
                bestRakuh = null;
                lastMinedLbl.settext(lastMinedValueText(null, 0, 0));
                stoneLbl.settext("Stone: -");
                quarryartzLbl.settext("Quarryartz: -");
                catGoldLbl.settext("Cat Gold: -");
                rakuhLbl.settext("Shell: -");
            }
        }, cur);

        add(new Widget(Coord.of(1, 1)), new Coord(contentW - 1, pad));
        pack();
    }

    private Coord addSettingRow(Coord cur, String caption, TextEntry entry, int setW, int gap) {
        Label label = add(new Label(caption), cur);
        add(entry, label.pos("bl").add(0, UI.scale(2)));
        add(new Button(setW, L10n.get("bot.masterminer.set")) {
            @Override
            public void click() {
                super.click();
                saveSettings();
            }
        }, entry.pos("ur").add(UI.scale(6), -UI.scale(4)));
        return entry.pos("bl").add(0, gap);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        groundScanAcc += dt;
        if (groundScanAcc < 0.4) {
            return;
        }
        groundScanAcc = 0;
        refreshGroundIcons();
    }

    void pickupGround(String resPath, boolean takeAll) {
        if (resPath == null || resPath.isEmpty()) {
            return;
        }
        Thread running = pickupThread;
        if (running != null && running.isAlive()) {
            return;
        }
        pickupThread = BotExecutor.runAsync("MasterMinerPickup",
                new PickupGroundItems(resPath, MasterMinerGroundStacks.pickupCap(takeAll)));
    }

    /** Fill the same support reserve used by auto-drop, without blocking the window. */
    void collectSupportStones() {
        Thread running = pickupThread;
        if (running != null && running.isAlive()) return;
        pickupThread = BotExecutor.runAsync("MasterMinerCollectSupport",
                new MasterMiner.CollectSupportStones(MasterMinerGroundStacks.CLICK_PICKUP_LIMIT));
    }

    private void refreshGroundIcons() {
        List<MasterMinerGroundStacks.Stack> stacks = scanGroundStacks();
        int gap = UI.scale(4);
        int iconW = UI.scale(32);
        int max = Math.max(1, (groundRow.sz.x + gap) / (iconW + gap));
        if (stacks.size() > max) {
            stacks = new ArrayList<>(stacks.subList(0, max));
        }
        java.util.Set<String> keep = new java.util.HashSet<>();
        int x = 0;
        for (MasterMinerGroundStacks.Stack stack : stacks) {
            keep.add(stack.resPath);
            MasterMinerGroundIcon icon = groundIcons.get(stack.resPath);
            if (icon == null) {
                icon = groundRow.add(new MasterMinerGroundIcon(this, stack), new Coord(x, 0));
                groundIcons.put(stack.resPath, icon);
            } else {
                icon.setCount(stack.count);
                icon.move(new Coord(x, 0));
            }
            x += iconW + gap;
        }
        java.util.Iterator<Map.Entry<String, MasterMinerGroundIcon>> it = groundIcons.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, MasterMinerGroundIcon> e = it.next();
            if (!keep.contains(e.getKey())) {
                e.getValue().reqdestroy();
                it.remove();
            }
        }
    }

    private List<MasterMinerGroundStacks.Stack> scanGroundStacks() {
        NGameUI gui = NUtils.getGameUI();
        Gob player = NUtils.player();
        if (gui == null || gui.ui == null || gui.ui.sess == null || player == null) {
            return java.util.Collections.emptyList();
        }
        List<MasterMinerGroundStacks.Drop> drops = new ArrayList<>();
        OCache oc = gui.ui.sess.glob.oc;
        synchronized (oc) {
            for (Gob gob : oc) {
                if (gob == null || gob == player || gob instanceof OCache.Virtual) {
                    continue;
                }
                if (gob.ngob == null || gob.ngob.name == null) {
                    continue;
                }
                drops.add(new MasterMinerGroundStacks.Drop(gob.ngob.name, gob.rc.x, gob.rc.y));
            }
        }
        return MasterMinerGroundStacks.group(drops, player.rc.x, player.rc.y, MasterMinerGroundStacks.PICKUP_RADIUS);
    }

    public boolean isClosed() {
        return closed;
    }

    private static String masonryWaitingText() {
        return L10n.get("bot.masterminer.masonry") + ": " + L10n.get("bot.masterminer.waiting");
    }

    static String lastMinedCaptionText() {
        return L10n.get("bot.masterminer.last_mined") + ":";
    }

    static String lastMinedValueText(String stoneName, double handQ, double wallQ) {
        return lastMinedValueText(stoneName, handQ, wallQ, false);
    }

    static String lastMinedValueText(String stoneName, double handQ, double wallQ, boolean masonryCapped) {
        if (stoneName == null || stoneName.isEmpty()) {
            return "-";
        }
        return String.format(Locale.US, "%s %.2f%s [%.2f]", stoneName, handQ,
                masonryCapped ? " *" : "", wallQ);
    }

    static String qualityLineText(String displayName, double handQ, double wallQ, Double bestAltQ) {
        if (bestAltQ != null && !bestAltQ.isNaN() && !bestAltQ.isInfinite()) {
            return String.format(Locale.US, "%s: %.2f [%.2f] (%.2f)", displayName, handQ, wallQ, bestAltQ);
        }
        return String.format(Locale.US, "%s: %.2f [%.2f]", displayName, handQ, wallQ);
    }

    private String minedText() {
        return L10n.get("bot.masterminer.mined") + ": " + totalStonesMined;
    }

    /** Saved screen position, or null to center on first open. */
    public Coord savedWindowPos() {
        return savedWindowPos;
    }

    @Override
    public void move(Coord c) {
        super.move(c);
        persistWindowPos();
    }

    @Override
    public boolean mouseup(Widget.MouseUpEvent ev) {
        boolean handled = super.mouseup(ev);
        persistWindowPos();
        return handled;
    }

    public void setMasonry(int masonry) {
        // Обновляем текст с сохранением жирного шрифта и цвета
        // Добавляем "(Masonry +25%)" в квадратных скобках
        int masonryWithBonus = (int) Math.round(masonry * 1.25);
        String newText = L10n.get("bot.masterminer.masonry") + ": " + masonry + " [" + masonryWithBonus + "]";
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
        
        String text = qualityLineText(displayName, currentBest.f3, currentBest.wallQ, currentBest.bestAltQ);

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
                updateWallQColor(targetLabel, currentBest.wallQ, masonry, stoneType);
            } else {
                targetLabel.setcolor(Color.WHITE);
            }
        }
    }
    
    public static boolean isMasonryCapped(double wallQ, int masonry, String stoneType) {
        if (masonry <= 0) return false;
        int comparisonValue = "Quarryartz".equals(stoneType)
                ? (int) Math.round(masonry * 1.25) : masonry;
        return wallQ >= comparisonValue - 1.0 && wallQ <= comparisonValue + 1.0;
    }

    private Color getWallQColor(double wallQ, int masonry, String stoneType) {
        // Для квариарца используем (masonry + 25%), для остальных - обычный masonry
        int comparisonValue = masonry;
        if ("Quarryartz".equals(stoneType)) {
            comparisonValue = (int) Math.round(masonry * 1.25);
        }
        
        // Красный когда примерно равно comparisonValue (±1)
        if (isMasonryCapped(wallQ, masonry, stoneType)) {
            return Color.RED;
        }
        // Оранжевый (желтый) когда на ~10 меньше comparisonValue (диапазон от comparisonValue-11 до comparisonValue-9)
        double diff = comparisonValue - wallQ;
        if (diff >= 9.0 && diff <= 11.0) {
            return new Color(255, 165, 0); // Оранжевый (желтый)
        }
        return Color.WHITE; // Белый по умолчанию
    }
    
    private void updateWallQColor(Label lbl, double wallQ, int masonry, String stoneType) {
        // Всегда применяем цвет, даже если белый
        Color color = getWallQColor(wallQ, masonry, stoneType);
        lbl.setcolor(color);
        // Также обновляем цвет текста напрямую
        lbl.col = color;
    }

    public void incrementCounter() {
        totalStonesMined++;
        counterLbl.settext(minedText());
    }

    /**
     * Last mined stone: quality in hands, then quality in the wall.
     */
    public void setLastMined(String stoneName, double handQ, double wallQ) {
        lastMinedLbl.settext(lastMinedValueText(stoneName, handQ, wallQ));
    }

    public void setLastMined(String stoneName, double handQ, double wallQ, int masonry, String stoneType) {
        lastMinedLbl.settext(lastMinedValueText(stoneName, handQ, wallQ,
                isMasonryCapped(wallQ, masonry, stoneType)));
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

    /** Сколько камней всегда держать в инвентаре (для подпорки). */
    public int getKeepStonesForSupport() {
        try {
            String txt = keepStonesEntry.text().trim();
            if (txt.isEmpty()) return 30;
            return Math.max(0, Integer.parseInt(txt));
        } catch (Exception e) {
            return 30;
        }
    }
    
    
    /**
     * Загружает сохраненные настройки
     */
    private NMasterMinerProp loadSettings() {
        if (NUtils.getUI() == null || NUtils.getUI().sessInfo == null) {
            return null;
        }
        return NMasterMinerProp.get(NUtils.getUI().sessInfo);
    }

    private void persistWindowPos() {
        if (c == null) {
            return;
        }
        if (lastPersistedPos != null && lastPersistedPos.equals(c)) {
            return;
        }
        saveSettings(true);
    }
    
    /**
     * Сохраняет текущие настройки
     */
    private void saveSettings() {
        saveSettings(false);
    }

    private void saveSettings(boolean includeWindowPos) {
        if (NUtils.getUI() == null || NUtils.getUI().sessInfo == null) {
            return;
        }
        NMasterMinerProp prop = NMasterMinerProp.get(NUtils.getUI().sessInfo);
        if (prop == null) {
            if (NUtils.getGameUI() != null && NUtils.getGameUI().getCharInfo() != null) {
                prop = new NMasterMinerProp(NUtils.getUI().sessInfo.username,
                                            NUtils.getGameUI().getCharInfo().chrid);
            } else {
                System.err.println("[MasterMiner] Cannot save settings: no character info");
                return;
            }
        }

        if (thresholdEntry != null) {
            try {
                String dropText = thresholdEntry.text().trim();
                if (!dropText.isEmpty()) {
                    prop.dropThreshold = Float.parseFloat(dropText.replace(',', '.'));
                } else {
                    prop.dropThreshold = Float.NaN;
                }
            } catch (NumberFormatException e) {
                prop.dropThreshold = Float.NaN;
            }
        }

        if (shellCatGoldThresholdEntry != null) {
            try {
                String shellCatGoldText = shellCatGoldThresholdEntry.text().trim();
                if (!shellCatGoldText.isEmpty()) {
                    prop.shellCatGoldThreshold = Float.parseFloat(shellCatGoldText.replace(',', '.'));
                } else {
                    prop.shellCatGoldThreshold = Float.NaN;
                }
            } catch (NumberFormatException e) {
                prop.shellCatGoldThreshold = Float.NaN;
            }
        }

        if (keepStonesEntry != null) {
            prop.keepStonesForSupport = getKeepStonesForSupport();
        }

        if (includeWindowPos && c != null) {
            prop.wndX = c.x;
            prop.wndY = c.y;
            lastPersistedPos = new Coord(c.x, c.y);
        }

        NMasterMinerProp.set(prop);
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
            saveSettings(true);
            hide();
        }
        super.wdgmsg(msg, args);
    }
}

