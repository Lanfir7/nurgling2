package nurgling.widgets;

import haven.*;
import haven.Locked;
import nurgling.NGameUI;
import nurgling.widgets.LabeledMinimapMark;

import java.util.*;

/**
 * Окно поиска меток квариарца на карте с фильтром по порогу качества.
 */
public class QuarryartzSearchWindow extends Window {
    private final NGameUI gui;

    private TextEntry thresholdEntry;
    private QuarryartzResultsList resultsList;

    private static final int WINDOW_WIDTH = UI.scale(350);
    private static final int WINDOW_HEIGHT = UI.scale(400);

    public QuarryartzSearchWindow(NGameUI gui) {
        super(new Coord(WINDOW_WIDTH, WINDOW_HEIGHT), "Поиск квариарца", true);
        this.gui = gui;

        int y = UI.scale(10);
        int labelX = UI.scale(10);
        int controlX = UI.scale(100);
        int lineHeight = UI.scale(30);

        // Порог качества
        add(new Label("Порог качества:"), labelX, y + UI.scale(5));
        thresholdEntry = add(new TextEntry(UI.scale(150), ""), controlX, y);
        y += lineHeight;

        // Кнопка поиска
        add(new Button(UI.scale(150), "Поиск") {
            @Override
            public void click() {
                performSearch();
            }
        }, UI.scale(100), y);
        y += lineHeight + UI.scale(10);

        // Список результатов
        add(new Label("Результаты:"), labelX, y);
        y += UI.scale(25);

        Coord resultsSize = new Coord(WINDOW_WIDTH - UI.scale(20), WINDOW_HEIGHT - y - UI.scale(10));
        resultsList = add(new QuarryartzResultsList(resultsSize), labelX, y);

        pack();
    }

    /**
     * Выполнить поиск по порогу качества
     */
    private void performSearch() {
        if (gui == null || gui.labeledMarkService == null) return;

        double threshold = Double.NaN;
        try {
            String txt = thresholdEntry.text().trim();
            if (!txt.isEmpty()) {
                threshold = Double.parseDouble(txt.replace(',', '.'));
            }
        } catch (Exception e) {
            // Игнорируем ошибки парсинга
        }

        List<LabeledMinimapMark> results;
        if (Double.isNaN(threshold)) {
            // Если порог не указан, показываем все метки квариарца
            results = gui.labeledMarkService.getMarksByResourceType("Quarryartz");
        } else {
            // Фильтруем по порогу
            results = gui.labeledMarkService.getQuarryartzMarksAboveThreshold(threshold);
        }

        // Сортируем по качеству (убывание)
        results.sort((a, b) -> {
            try {
                double qa = parseQuality(a.label);
                double qb = parseQuality(b.label);
                return Double.compare(qb, qa); // Убывание
            } catch (Exception e) {
                return 0;
            }
        });

        resultsList.setResults(results);
    }

    private double parseQuality(String label) {
        if (label == null || !label.startsWith("q")) return 0;
        try {
            return Double.parseDouble(label.substring(1).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Список результатов поиска
     */
    private class QuarryartzResultsList extends SListBox<LabeledMinimapMark, Widget> {
        private List<LabeledMinimapMark> results = new ArrayList<>();

        public QuarryartzResultsList(Coord sz) {
            super(sz, UI.scale(25));
        }

        public void setResults(List<LabeledMinimapMark> results) {
            this.results = results;
        }

        @Override
        protected List<LabeledMinimapMark> items() {
            return results;
        }

        @Override
        protected Widget makeitem(LabeledMinimapMark mark, int idx, Coord sz) {
            return new ItemWidget<LabeledMinimapMark>(this, sz, mark) {
                {
                    int deleteButtonWidth = UI.scale(22);
                    int panButtonWidth = sz.x - deleteButtonWidth - UI.scale(4);

                    // Основная кнопка для перехода к метке
                    add(new Button(panButtonWidth, "") {
                        @Override
                        public void draw(GOut g) {
                            String text = mark.label + " @ " + mark.tileCoords.x + "," + mark.tileCoords.y;
                            g.text(text, Coord.z);
                        }

                        @Override
                        public void click() {
                            panMapToLocation(mark);
                        }
                    }, Coord.z);

                    // Кнопка удаления
                    add(new IButton(nurgling.NStyle.crossSquare[0].back,
                                   nurgling.NStyle.crossSquare[1].back,
                                   nurgling.NStyle.crossSquare[2].back) {
                        @Override
                        public void click() {
                            if (gui != null && gui.labeledMarkService != null) {
                                gui.labeledMarkService.removeMark(mark);
                                gui.msg("Удалена метка " + mark.label, java.awt.Color.YELLOW);
                                performSearch();
                            }
                        }
                    }, new Coord(panButtonWidth + UI.scale(2), (sz.y - UI.scale(22)) / 2));
                }
            };
        }
    }

    /**
     * Переместить карту к выбранной метке
     */
    private void panMapToLocation(LabeledMinimapMark mark) {
        if (gui == null || gui.mapfile == null) return;

        NMapWnd mapWnd = gui.mapfile;
        if (mapWnd == null || mapWnd.view == null) return;

        // Открыть окно карты если не видно
        if (!mapWnd.visible()) {
            gui.togglewnd(mapWnd);
        }

        // Получить сегмент для этой метки
        if (gui.mmap != null && gui.mmap.file != null) {
            try (Locked lk = new Locked(gui.mmap.file.lock.readLock())) {
                MapFile.Segment segment = gui.mmap.file.segments.get(mark.segmentId);
                if (segment != null) {
                    MiniMap.Location targetLoc = new MiniMap.Location(segment, mark.tileCoords);
                    mapWnd.view.center(targetLoc);
                    mapWnd.view.follow(null);
                    gui.msg("Карта перемещена к " + mark.label, java.awt.Color.GREEN);
                } else {
                    gui.msg("Метка находится в другой области", java.awt.Color.YELLOW);
                }
            }
        }
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (msg.equals("close")) {
            hide();
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }
}


