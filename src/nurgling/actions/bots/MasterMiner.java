package nurgling.actions.bots;

import haven.UI;
import haven.WItem;
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

import java.util.ArrayList;

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
    static {
        ArrayList<String> keys = new ArrayList<>(Chipper.stones.keys);
        keys.add("Stone");
        keys.add("Камень");
        MINED_ITEMS = new NAlias(keys);
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
                WItem newItem = null;
                for (WItem it : cur) {
                    if (!known.contains(it)) {
                        newItem = it;
                        break;
                    }
                }
                if (newItem == null) {
                    NUtils.addTask(new WaitTicks(5));
                    continue;
                }
                known = cur;

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
                    continue;
                }

                double f3 = dropped.quality;
                String stoneName = dropped.name();
                wnd.setStone(f3, stoneName);

                WItem tool = findMiningTool();
                if (tool == null) {
                    wnd.setTool("(не найден)");
                    NUtils.addTask(new WaitTicks(10));
                    continue;
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
                wnd.setTool(toolName);

                if (f4 != null) {
                    double wallQ = calcWallQ(f3, f4, f5);
                    wnd.setWallQ(wallQ, stoneName);

                    // проверка порога и сброс камня
                    double threshold = wnd.getDropThreshold();
                    if (!Double.isNaN(threshold) && wallQ < threshold) {
                        // проверяем, что это действительно камень из инвентаря, а не инструмент
                        if (newItem != null && newItem.parent == gui.getInventory()) {
                            // дополнительная проверка: убеждаемся, что это не инструмент
                            String itemName = stoneName != null ? stoneName.toLowerCase() : "";
                            boolean isTool = itemName.contains("axe") || itemName.contains("pickaxe") || 
                                           itemName.contains("топор") || itemName.contains("кирк");
                            if (!isTool) {
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

                    ToolSet set = scanTools(gui, ftool);
                    Double stoneAxePred = (set.stoneAxeQ != null) ? invDropQ(wallQ, set.stoneAxeQ, 0.8) : null;
                    Double tinkerAxePred = (set.tinkerAxeQ != null) ? invDropQ(wallQ, set.tinkerAxeQ, 0.9) : null;
                    Double pickaxePred = (set.pickaxeQ != null) ? invDropQ(wallQ, set.pickaxeQ, 1.0) : null;
                    wnd.setAltPredictions(stoneAxePred, tinkerAxePred, pickaxePred);
                }

                // небольшой “yield”, чтобы не долбить UI тасками
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

    @Override
    public void endAction() {
        stop = true;
        if (wnd != null) {
            try { wnd.destroy(); } catch (Exception ignored) {}
        }
    }

    private static double calcWallQ(double f3, double f4, double f5) {
        if (f5 <= 0) f5 = 1.0;
        return ((f3 - f4) * 2.0 + (f4 - 10.0) / f5) + 10.0;
    }

    /**
     * Инверсия формулы: по wallQ считает, какого качества (F3) упал бы камень
     * с другим инструментом (его F4 и F5).
     */
    private static double invDropQ(double wallQ, double f4, double f5) {
        if (f5 <= 0) f5 = 1.0;
        // F3 = F4 + 0.5 * ((W-10) - (F4-10)/F5)
        return f4 + 0.5 * ((wallQ - 10.0) - (f4 - 10.0) / f5);
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

    private enum ToolType { STONE_AXE, TINKER_AXE, PICKAXE, OTHER }

    private static ToolType classifyTool(String name) {
        if (name == null) return ToolType.OTHER;
        String n = name.toLowerCase();
        if (n.contains("pickaxe") || n.contains("кирк")) return ToolType.PICKAXE;
        if ((n.contains("tinker") && n.contains("axe")) || (n.contains("тинкер") && n.contains("топор"))) return ToolType.TINKER_AXE;
        if ((n.contains("stone") && n.contains("axe")) || (n.contains("камен") && n.contains("топор"))) return ToolType.STONE_AXE;
        return ToolType.OTHER;
    }

    private static boolean isKnownMiningTool(WItem w) {
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

    private static class ToolSet {
        Double stoneAxeQ;
        Double tinkerAxeQ;
        Double pickaxeQ;
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
}

