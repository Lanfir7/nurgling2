package nurgling.widgets;

import haven.*;
import haven.Button;
import haven.CheckBox;
import haven.Label;
import haven.Window;
import haven.resutil.Curiosity;
import nurgling.NGItem;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.i18n.L10n;
import nurgling.iteminfo.NCuriosity;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Extension for adding Study Desk specific functionality to inventory windows
 */
public class StudyDeskInventoryExtension {

    static final int MIN_STOCK_HOURS = 6;
    static final int MAX_STOCK_HOURS = 168;

    private StudyDeskInventoryExtension() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Adds a Plan button and details panel to inventory windows that belong to Study Desk containers
     * @param inventory The inventory to potentially extend
     */
    public static void addPlanButtonIfStudyDesk(NInventory inventory) {
        if (inventory != null && isStudyDeskInventory(inventory)) {
            StudyDeskDetailsPanel detailsPanel = addDetailsPanel(inventory);
            addPlanButtonAndSlider(inventory, detailsPanel);
        }
    }

    /**
     * Checks if the given inventory belongs to a Study Desk container
     *
     * @param inventory The inventory to check
     * @return true if this is a Study Desk inventory
     */
    public static boolean isStudyDeskInventory(NInventory inventory) {
        String resName = getInventoryParentGobResName(inventory);
        return isStudyDesk(resName) || isStudyDeskFine(resName) || isStudyDeskGrand(resName);
    }

    private static boolean isStudyDeskGrand(String resName) {
        return "gfx/terobjs/grandstudydesk".equals(resName);
    }

    private static boolean isStudyDeskFine(String resName) {
        return "gfx/terobjs/studydesk-big".equals(resName);
    }

    private static boolean isStudyDesk(String resName) {
        return "gfx/terobjs/studydesk".equals(resName);
    }

    /**
     * Returns the inventory's parent gob res name.
     *
     * @param inventory holds the drawable attribute with parent gob res name
     * @return inventory's parent gob res name
     */
    private static String getInventoryParentGobResName(NInventory inventory) {
        String resName = null;
        if (inventory.parentGob == null) return resName;
        // Get the drawable attribute from the gob
        Drawable drawable = inventory.parentGob.getattr(Drawable.class);
        if (drawable != null && drawable.getres() != null) {
            resName = drawable.getres().name;
        }
        return resName;
    }

    /**
     * Adds the Plan button and stock-duration slider below the inventory grid.
     */
    private static void addPlanButtonAndSlider(NInventory inventory, StudyDeskDetailsPanel detailsPanel) {
        if (inventory.parent == null) return;

        Button planButton = new Button(UI.scale(50), "Plan") {
            @Override
            public void click() {
                openStudyDeskPlanner(inventory);
            }
        };

        int bottomY = inventory.sz.y + UI.scale(5);
        int planX = inventory.sz.x - planButton.sz.x;
        inventory.parent.add(planButton, new Coord(planX, bottomY));

        int stockHours = detailsPanel != null ? detailsPanel.stockHours : loadStockHours();
        Label durationLabel = new Label("6d 23h");
        int reservedLabelW = durationLabel.sz.x;
        durationLabel.settext(formatTime(stockHours * 3600));
        int labelX = planX - reservedLabelW - UI.scale(6);
        int sliderX = UI.scale(4);
        int sliderW = Math.max(UI.scale(40), labelX - sliderX - UI.scale(6));

        HSlider slider = new HSlider(sliderW, MIN_STOCK_HOURS, MAX_STOCK_HOURS, stockHours) {
            @Override
            public void changed() {
                if (detailsPanel != null) {
                    detailsPanel.setStockHours(val);
                }
                durationLabel.settext(formatTime(val * 3600));
                settip(L10n.get("study.stock_horizon") + ": " + formatTime(val * 3600));
            }

            @Override
            public void fchanged() {
                NConfig.set(NConfig.Key.studyDeskStockHours, val);
            }
        };
        slider.settip(L10n.get("study.stock_horizon") + ": " + formatTime(stockHours * 3600));

        int sliderY = bottomY + Math.max(0, (planButton.sz.y - slider.sz.y) / 2);
        inventory.parent.add(slider, new Coord(sliderX, sliderY));
        int labelY = bottomY + Math.max(0, (planButton.sz.y - durationLabel.sz.y) / 2);
        inventory.parent.add(durationLabel, new Coord(labelX, labelY));
        if (inventory.parent instanceof Window) {
            ((Window) inventory.parent).pack();
        }
    }

    /**
     * Returns the correct planner grid size for the given inventory's study desk type
     */
    private static Coord getDeskSize(NInventory inventory) {
        String resName = getInventoryParentGobResName(inventory);
        if (isStudyDeskFine(resName)) {
            return StudyDeskPlannerWidget.DESK_SIZE_FINE;
        } else if (isStudyDeskGrand(resName)) {
            return StudyDeskPlannerWidget.DESK_SIZE_GRAND;
        }
        return StudyDeskPlannerWidget.DESK_SIZE;
    }

    /**
     * Opens the Study Desk Planner widget positioned next to the study desk inventory
     */
    private static void openStudyDeskPlanner(NInventory inventory) {
        NGameUI gameUI = NUtils.getGameUI();
        if (gameUI != null) {
            // Calculate position to the right of the study desk window
            Coord plannerPos = calculatePlannerPosition(inventory);

            // Get study desk gob hash
            String gobHash = null;
            if (inventory.parentGob != null && inventory.parentGob.ngob != null) {
                gobHash = inventory.parentGob.ngob.hash;
            }

            Coord requiredSize = getDeskSize(inventory);

            // Recreate planner if desk type changed
            if (gameUI.studyDeskPlanner != null && !gameUI.studyDeskPlanner.getStudyDeskSize().equals(requiredSize)) {
                gameUI.studyDeskPlanner.reqdestroy();
                gameUI.studyDeskPlanner = null;
            }

            if (gameUI.studyDeskPlanner == null) {
                gameUI.studyDeskPlanner = new StudyDeskPlannerWidget(requiredSize);
                gameUI.add(gameUI.studyDeskPlanner, plannerPos);
                if (gobHash != null) {
                    gameUI.studyDeskPlanner.setStudyDeskHash(gobHash);
                }
                gameUI.studyDeskPlanner.show();
            } else {
                // Toggle visibility for subsequent clicks
                if (gameUI.studyDeskPlanner.visible()) {
                    gameUI.studyDeskPlanner.hide();
                } else {
                    // Reposition and set gob hash before showing
                    gameUI.studyDeskPlanner.move(plannerPos);
                    if (gobHash != null) {
                        gameUI.studyDeskPlanner.setStudyDeskHash(gobHash);
                    }
                    gameUI.studyDeskPlanner.show();
                }
            }
        }
    }

    /**
     * Calculates the position for the planner widget next to the study desk
     */
    private static Coord calculatePlannerPosition(NInventory inventory) {
        if (inventory.parent != null && inventory.parent instanceof Window) {
            Window window = (Window) inventory.parent;
            // Position to the right of the window with a small gap
            Coord windowPos = window.c;
            Coord windowSize = window.sz;
            return new Coord(windowPos.x + windowSize.x + UI.scale(10), windowPos.y);
        }
        // Fallback to default position if we can't determine window position
        return new Coord(200, 100);
    }

    /**
     * Adds a details panel showing curio information
     */
    private static StudyDeskDetailsPanel addDetailsPanel(NInventory inventory) {
        if (inventory.parent == null) return null;

        Coord panelPos = new Coord(inventory.sz.x + UI.scale(10), 0);
        StudyDeskDetailsPanel detailsPanel = new StudyDeskDetailsPanel(new Coord(UI.scale(160), UI.scale(50)), inventory);

        CheckBox hideLpBox = new CheckBox(L10n.get("study.hide_lp"));
        hideLpBox.a = detailsPanel.hideLp;
        hideLpBox.changed(val -> {
            detailsPanel.setHideLp(val);
            NConfig.set(NConfig.Key.studyDeskHideLp, val);
        });
        inventory.parent.add(hideLpBox, panelPos);

        int checkH = hideLpBox.sz.y + UI.scale(2);
        int scrollHeight = inventory.sz.y - UI.scale(55) - checkH;
        Coord scrollSize = new Coord(UI.scale(160), Math.max(UI.scale(40), scrollHeight));

        Scrollport scrollport = new Scrollport(scrollSize);
        scrollport.cont.add(detailsPanel, Coord.z);
        inventory.parent.add(scrollport, new Coord(panelPos.x, checkH));

        int statsY = checkH + scrollSize.y;
        Label expCostLabel = new Label("Exp cost: 0");
        expCostLabel.setcolor(new Color(255, 255, 192));
        inventory.parent.add(expCostLabel, new Coord(panelPos.x, statsY + UI.scale(5)));

        Label mentalWeightLabel = new Label("Mental Weight: 0");
        inventory.parent.add(mentalWeightLabel, new Coord(panelPos.x, statsY + UI.scale(18)));

        Label totalLPLabel = new Label("Total LP: 0");
        inventory.parent.add(totalLPLabel, new Coord(panelPos.x, statsY + UI.scale(31)));

        detailsPanel.expCostLabel = expCostLabel;
        detailsPanel.mentalWeightLabel = mentalWeightLabel;
        detailsPanel.totalLPLabel = totalLPLabel;
        detailsPanel.scrollport = scrollport;
        return detailsPanel;
    }

    static boolean loadHideLp() {
        return Boolean.TRUE.equals(NConfig.get(NConfig.Key.studyDeskHideLp));
    }

    static int loadStockHours() {
        Object v = NConfig.get(NConfig.Key.studyDeskStockHours);
        int hours = MAX_STOCK_HOURS;
        if (v instanceof Number) {
            hours = ((Number) v).intValue();
        }
        return clampStockHours(hours);
    }

    static int clampStockHours(int hours) {
        return Math.max(MIN_STOCK_HOURS, Math.min(MAX_STOCK_HOURS, hours));
    }

    static int toRealSeconds(int serverTime) {
        return (int) (serverTime / NCuriosity.server_ratio);
    }

    static final Color STOCK_SHORT = new Color(255, 80, 80);
    static final Color STOCK_OK = new Color(80, 220, 80);
    static final Color STOCK_LONG = Color.WHITE;

    static Color stockTimeColor(int realSeconds, int stockHours) {
        long horizon = Math.max(1, (long) stockHours) * 3600L;
        if (realSeconds < horizon) {
            return STOCK_SHORT;
        }
        if (realSeconds < horizon * 2) {
            return STOCK_OK;
        }
        return STOCK_LONG;
    }

    static int lineHeight(boolean hideLp) {
        return hideLp ? UI.scale(18) : UI.scale(30);
    }

    static String formatTime(int seconds) {
        if (seconds <= 0) {
            return "0s";
        }

        int days = seconds / 86400;
        int hours = (seconds % 86400) / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d");
        }
        if (hours > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(hours).append("h");
        }
        if (minutes > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(minutes).append("m");
        }
        if (secs > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(secs).append("s");
        }
        return sb.toString();
    }

    static List<CurioInfo> visibleSorted(Collection<CurioInfo> all) {
        List<CurioInfo> visible = new ArrayList<>(all);
        visible.sort(Comparator
                .comparingInt((CurioInfo a) -> a.totalTime)
                .thenComparing(a -> a.name, String.CASE_INSENSITIVE_ORDER));
        return visible;
    }

    /**
     * Panel that displays details about curios in the study desk
     */
    public static class StudyDeskDetailsPanel extends Widget {
        private final NInventory inventory;
        private static final Text.Foundry fnd = new Text.Foundry(Text.sans, 10);
        private Map<String, CurioInfo> cachedInfo = new HashMap<>();
        private int lastVisibleCount = -1;
        private int lastLineHeight = -1;
        boolean hideLp;
        int stockHours;
        Label expCostLabel;
        Label mentalWeightLabel;
        Label totalLPLabel;
        Scrollport scrollport;

        public StudyDeskDetailsPanel(Coord sz, NInventory inventory) {
            super(sz);
            this.inventory = inventory;
            this.hideLp = loadHideLp();
            this.stockHours = loadStockHours();
        }

        void setHideLp(boolean hideLp) {
            this.hideLp = hideLp;
            refreshLayout();
        }

        void setStockHours(int stockHours) {
            this.stockHours = clampStockHours(stockHours);
            refreshLayout();
        }

        private void refreshLayout() {
            List<CurioInfo> visible = visibleSorted(cachedInfo.values());
            lastVisibleCount = visible.size();
            lastLineHeight = lineHeight(hideLp);
            rebuildContent(lastVisibleCount, lastLineHeight);
        }

        @Override
        public void tick(double dt) {
            super.tick(dt);

            if (NUtils.getTickId() % 10 == 0) {
                cachedInfo = calculateCurioInfo();
                List<CurioInfo> visible = visibleSorted(cachedInfo.values());
                int lh = lineHeight(hideLp);
                if (visible.size() != lastVisibleCount || lh != lastLineHeight) {
                    lastVisibleCount = visible.size();
                    lastLineHeight = lh;
                    rebuildContent(visible.size(), lh);
                }
            }
        }

        @Override
        public void draw(GOut g) {
            super.draw(g);

            List<CurioInfo> sortedCurios = visibleSorted(cachedInfo.values());

            int totalLP = 0;
            int totalMentalWeight = 0;
            int totalExpCost = 0;
            for (CurioInfo info : cachedInfo.values()) {
                totalLP += info.totalLP;
                totalMentalWeight += info.mentalWeight;
                totalExpCost += info.totalExpCost;
            }
            updateExpCost(totalExpCost);
            updateMentalWeight(totalMentalWeight);
            updateTotalLP(totalLP);

            int lh = lineHeight(hideLp);
            int y = 0;
            for (CurioInfo info : sortedCurios) {
                if (info.resource != null) {
                    try {
                        Resource.Image img = info.resource.layer(Resource.imgc);
                        if (img != null) {
                            TexI scaledImg = new TexI(img.scaled());
                            Coord iconSize = UI.scale(new Coord(16, 16));
                            g.image(scaledImg, new Coord(0, y), iconSize);
                        }
                    } catch (Exception e) {
                        // Skip icon if there's an issue
                    }
                }

                int realTime = toRealSeconds(info.totalTime);
                String timeText = String.format("x%d - %s", info.count, formatTime(realTime));
                Text t = fnd.render(timeText, stockTimeColor(realTime, stockHours));
                g.image(t.tex(), new Coord(UI.scale(20), y + 2));

                if (!hideLp) {
                    String lpText = String.format("LP: %,d", info.totalLP);
                    Text lpTex = fnd.render(lpText, new Color(192, 192, 255));
                    g.image(lpTex.tex(), new Coord(UI.scale(20), y + UI.scale(14)));
                }

                y += lh;
            }
        }

        private void rebuildContent(int visibleCount, int lineHeight) {
            int contentHeight = visibleCount * lineHeight + UI.scale(10);
            contentHeight = Math.max(contentHeight, UI.scale(50));

            Coord newSize = new Coord(sz.x, contentHeight);
            if (!sz.equals(newSize)) {
                resize(newSize);
                if (scrollport != null && scrollport.cont != null) {
                    scrollport.cont.update();
                }
            }
        }

        private void updateExpCost(int expCost) {
            if (expCostLabel != null) {
                String text = String.format("Exp cost: %,d", expCost);
                expCostLabel.settext(text);
            }
        }

        private void updateMentalWeight(int mentalWeight) {
            if (mentalWeightLabel != null) {
                String text = String.format("Mental Weight: %d", mentalWeight);
                mentalWeightLabel.settext(text);
                mentalWeightLabel.setcolor(new Color(255, 192, 255)); // Light purple color (matches game's mental weight color)
            }
        }

        private void updateTotalLP(int totalLP) {
            if (totalLPLabel != null) {
                String totalText = String.format("Total LP: %,d", totalLP);
                totalLPLabel.settext(totalText);
                totalLPLabel.setcolor(new Color(255, 215, 0)); // Gold color
            }
        }

        private Map<String, CurioInfo> calculateCurioInfo() {
            Map<String, CurioInfo> curioInfo = new HashMap<>();
            if (inventory == null) {
                return curioInfo;
            }

            // Walk children directly. inventory.getItems() uses NCore.addTask/wait
            // and deadlocks if called from Widget.tick on the UI thread.
            for (Widget widget = inventory.child; widget != null; widget = widget.next) {
                if (!(widget instanceof WItem)) {
                    continue;
                }
                WItem witem = (WItem) widget;
                if (witem.item == null) {
                    continue;
                }

                try {
                    List<ItemInfo> itemInfos = witem.item.info();
                    if (itemInfos == null) {
                        continue;
                    }

                    Curiosity curiosity = ItemInfo.find(Curiosity.class, itemInfos);
                    if (curiosity == null) {
                        continue;
                    }

                    String resourceName = null;
                    String displayName = "Unknown";
                    Resource resource = null;

                    Resource res = witem.item.getres();
                    if (res != null) {
                        resource = res;
                        resourceName = res.name;
                        if (witem.item instanceof NGItem) {
                            String name = ((NGItem) witem.item).name();
                            if (name != null && !name.isEmpty()) {
                                displayName = name;
                            }
                        }
                    }

                    String key = resourceName != null ? resourceName : displayName;
                    CurioInfo info = curioInfo.get(key);
                    if (info == null) {
                        info = new CurioInfo(displayName, resource, curiosity.time, curiosity.exp, curiosity.mw, curiosity.enc);
                        curioInfo.put(key, info);
                    } else {
                        info.count++;
                        info.totalTime += curiosity.time;
                        info.totalLP += curiosity.exp;
                        info.totalExpCost += curiosity.enc;
                    }
                } catch (Loading e) {
                    // Item/resource not ready yet; retry on a later tick.
                }
            }

            return curioInfo;
        }
    }

    static class CurioInfo {
        String name;
        Resource resource;
        int studyTime;
        int learningPoints;
        int mentalWeight;
        int expCost;
        int count = 1;
        int totalTime;
        int totalLP;
        int totalExpCost;

        CurioInfo(String name, int totalTime) {
            this.name = name;
            this.totalTime = totalTime;
        }

        CurioInfo(String name, Resource resource, int studyTime, int learningPoints, int mentalWeight, int expCost) {
            this.name = name;
            this.resource = resource;
            this.studyTime = studyTime;
            this.learningPoints = learningPoints;
            this.mentalWeight = mentalWeight;
            this.expCost = expCost;
            this.totalTime = studyTime;
            this.totalLP = learningPoints;
            this.totalExpCost = expCost;
        }
    }
}