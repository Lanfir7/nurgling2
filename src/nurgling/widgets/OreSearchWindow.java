package nurgling.widgets;

import haven.*;
import haven.Locked;
import nurgling.NGameUI;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Окно поиска меток руд/камней на карте с фильтром по типу руды/камня и качеству.
 * Аналогично QuarryartzSearchWindow, но с выпадающим списком типов руд/камней.
 */
public class OreSearchWindow extends Window {
    private final NGameUI gui;

    private TextEntry thresholdEntry;
    private Dropbox<String> oreTypeDropdown;
    private OreResultsList resultsList;
    private List<String> oreTypes;
    private int controlX;
    private int oreDropdownY;

    private static final int WINDOW_WIDTH = UI.scale(400);
    private static final int WINDOW_HEIGHT = UI.scale(450);

    public OreSearchWindow(NGameUI gui) {
        super(new Coord(WINDOW_WIDTH, WINDOW_HEIGHT), "Поиск руд и камней", true);
        this.gui = gui;

        int y = UI.scale(10);
        int labelX = UI.scale(10);
        controlX = UI.scale(120);
        oreDropdownY = y;
        int lineHeight = UI.scale(30);

        // Тип руды/камня
        add(new Label("Тип руды/камня:"), labelX, y + UI.scale(5));
        refreshOreTypeDropdown();
        y += lineHeight;

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
        }, UI.scale(125), y);
        y += lineHeight + UI.scale(10);

        // Список результатов
        add(new Label("Результаты:"), labelX, y);
        y += UI.scale(25);

        Coord resultsSize = new Coord(WINDOW_WIDTH - UI.scale(20), WINDOW_HEIGHT - y - UI.scale(10));
        resultsList = add(new OreResultsList(resultsSize), labelX, y);

        pack();
    }

    /**
     * Получает список уникальных типов руд/камней из сохраненных меток
     */
    private List<String> getDistinctOreTypes() {
        if (gui == null || gui.labeledMarkService == null) return new ArrayList<>();

        // Получаем все метки кроме квариарца (у него своё окно поиска)
        Collection<LabeledMinimapMark> allMarks = gui.labeledMarkService.getAllMarks();
        return allMarks.stream()
            .map(mark -> mark.resourceType)
            .filter(type -> type != null && !type.equals("Quarryartz")) // Исключаем квариарц
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Обновляет выпадающий список типов руд/камней
     */
    private void refreshOreTypeDropdown() {
        // Удаляем старый dropdown если существует
        if (oreTypeDropdown != null) {
            ui.destroy(oreTypeDropdown);
        }

        // Получаем свежие типы руд/камней
        oreTypes = getDistinctOreTypes();
        oreTypes.add(0, "Any"); // Добавляем опцию "Any" в начало

        // Создаем новый dropdown
        oreTypeDropdown = add(new Dropbox<String>(UI.scale(250), Math.min(oreTypes.size(), 10), UI.scale(20)) {
            @Override
            protected String listitem(int i) {
                return oreTypes.get(i);
            }

            @Override
            protected int listitems() {
                return oreTypes.size();
            }

            @Override
            protected void drawitem(GOut g, String item, int i) {
                g.text(item, Coord.z);
            }
        }, controlX, oreDropdownY);
        oreTypeDropdown.change(oreTypes.get(0)); // Выбираем "Any" по умолчанию
    }

    /**
     * Выполнить поиск по типу руды/камня и порогу качества
     */
    private void performSearch() {
        if (gui == null || gui.labeledMarkService == null) return;

        String selectedOreType = oreTypeDropdown.sel;
        double threshold = Double.NaN;
        try {
            String txt = thresholdEntry.text().trim();
            if (!txt.isEmpty()) {
                threshold = Double.parseDouble(txt.replace(',', '.'));
            }
        } catch (Exception e) {
            // Игнорируем ошибки парсинга, threshold остается NaN
        }
        final double finalThreshold = threshold;

        // Получаем все метки (кроме квариарца)
        Collection<LabeledMinimapMark> allMarks = gui.labeledMarkService.getAllMarks();
        List<LabeledMinimapMark> results = allMarks.stream()
            .filter(mark -> {
                // Фильтр по типу руды/камня
                if (!selectedOreType.equals("Any") && !mark.resourceType.equals(selectedOreType)) {
                    return false;
                }
                
                // Фильтр по качеству (если указан порог)
                if (!Double.isNaN(finalThreshold)) {
                    try {
                        double quality = parseQuality(mark.label);
                        if (quality <= finalThreshold) {
                            return false;
                        }
                    } catch (Exception e) {
                        // Если не удалось распарсить качество, исключаем из результатов
                        return false;
                    }
                }
                
                return true;
            })
            .collect(Collectors.toList());

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
    private class OreResultsList extends SListBox<LabeledMinimapMark, Widget> {
        private List<LabeledMinimapMark> results = new ArrayList<>();

        public OreResultsList(Coord sz) {
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
                            String text = mark.resourceType + " " + mark.label + " @ " + mark.tileCoords.x + "," + mark.tileCoords.y;
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
                                gui.msg("Удалена метка " + mark.resourceType + " " + mark.label, java.awt.Color.YELLOW);
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
                    gui.msg("Карта перемещена к " + mark.resourceType + " " + mark.label, java.awt.Color.GREEN);
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


