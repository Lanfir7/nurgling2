package nurgling.widgets;

import haven.*;
import haven.Locked;
import nurgling.NGameUI;
import nurgling.actions.bots.MasterMiner;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Окно поиска меток драгоценных камней на карте с фильтром по типу камня и качеству.
 * Аналогично OreSearchWindow, но фильтрует только драгоценные камни.
 */
public class GemstoneSearchWindow extends Window {
    private final NGameUI gui;

    private TextEntry thresholdEntry;
    private Dropbox<String> gemstoneTypeDropdown;
    private GemstoneResultsList resultsList;
    private List<String> gemstoneTypes;
    private int controlX;
    private int gemstoneDropdownY;

    private static final int WINDOW_WIDTH = UI.scale(400);
    private static final int WINDOW_HEIGHT = UI.scale(450);

    public GemstoneSearchWindow(NGameUI gui) {
        super(new Coord(WINDOW_WIDTH, WINDOW_HEIGHT), "Gemstone Search", true);
        this.gui = gui;

        int y = UI.scale(10);
        int labelX = UI.scale(10);
        controlX = UI.scale(120);
        gemstoneDropdownY = y;
        int lineHeight = UI.scale(30);

        // Тип драгоценного камня
        add(new Label("Gemstone type:"), labelX, y + UI.scale(5));
        refreshGemstoneTypeDropdown();
        y += lineHeight;

        // Порог качества
        add(new Label("Quality threshold:"), labelX, y + UI.scale(5));
        thresholdEntry = add(new TextEntry(UI.scale(150), ""), controlX, y);
        y += lineHeight;

        // Кнопка поиска
        add(new Button(UI.scale(150), "Search") {
            @Override
            public void click() {
                performSearch();
            }
        }, UI.scale(125), y);
        y += lineHeight + UI.scale(10);

        // Список результатов
        add(new Label("Results:"), labelX, y);
        y += UI.scale(25);

        Coord resultsSize = new Coord(WINDOW_WIDTH - UI.scale(20), WINDOW_HEIGHT - y - UI.scale(10));
        resultsList = add(new GemstoneResultsList(resultsSize), labelX, y);

        pack();
    }

    /**
     * Получает список уникальных типов драгоценных камней из сохраненных меток
     */
    private List<String> getDistinctGemstoneTypes() {
        if (gui == null || gui.labeledMarkService == null) return new ArrayList<>();

        // Получаем все метки и фильтруем только драгоценные камни
        Collection<LabeledMinimapMark> allMarks = gui.labeledMarkService.getAllMarks();
        return allMarks.stream()
            .map(mark -> mark.resourceType)
            .filter(type -> type != null && MasterMiner.isGemstone(type))
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Обновляет выпадающий список типов драгоценных камней
     */
    private void refreshGemstoneTypeDropdown() {
        // Удаляем старый dropdown если существует
        if (gemstoneTypeDropdown != null) {
            ui.destroy(gemstoneTypeDropdown);
        }

        // Получаем свежие типы драгоценных камней
        gemstoneTypes = getDistinctGemstoneTypes();
        gemstoneTypes.add(0, "Any"); // Добавляем опцию "Any" в начало

        // Создаем новый dropdown
        gemstoneTypeDropdown = add(new Dropbox<String>(UI.scale(250), Math.min(gemstoneTypes.size(), 10), UI.scale(20)) {
            @Override
            protected String listitem(int i) {
                return gemstoneTypes.get(i);
            }

            @Override
            protected int listitems() {
                return gemstoneTypes.size();
            }

            @Override
            protected void drawitem(GOut g, String item, int i) {
                g.text(item, Coord.z);
            }
        }, controlX, gemstoneDropdownY);
        gemstoneTypeDropdown.change(gemstoneTypes.get(0)); // Выбираем "Any" по умолчанию
    }

    /**
     * Выполнить поиск по типу драгоценного камня и порогу качества
     */
    private void performSearch() {
        if (gui == null || gui.labeledMarkService == null) return;

        String selectedGemstoneType = gemstoneTypeDropdown.sel;
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

        // Получаем все метки и фильтруем только драгоценные камни
        Collection<LabeledMinimapMark> allMarks = gui.labeledMarkService.getAllMarks();
        List<LabeledMinimapMark> results = allMarks.stream()
            .filter(mark -> {
                // Фильтр: только драгоценные камни
                if (mark.resourceType == null || !MasterMiner.isGemstone(mark.resourceType)) {
                    return false;
                }
                
                // Фильтр по типу драгоценного камня
                if (!selectedGemstoneType.equals("Any") && !mark.resourceType.equals(selectedGemstoneType)) {
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
    private class GemstoneResultsList extends SListBox<LabeledMinimapMark, Widget> {
        private List<LabeledMinimapMark> results = new ArrayList<>();

        public GemstoneResultsList(Coord sz) {
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
                    String labelText = mark.label != null ? mark.label : "q0";
                    String resourceText = mark.resourceType != null ? mark.resourceType : "Unknown";
                    add(new Label(String.format("%s: %s", resourceText, labelText)), new Coord(UI.scale(5), 0));
                    
                    // Кнопка удаления
                    add(new Button(UI.scale(20), "×") {
                        @Override
                        public void click() {
                            if (gui != null && gui.labeledMarkService != null) {
                                gui.labeledMarkService.removeMark(mark);
                                performSearch(); // Обновляем результаты после удаления
                            }
                        }
                    }, new Coord(sz.x - UI.scale(25), 0));
                }
                
                @Override
                public boolean mousedown(MouseDownEvent ev) {
                    if (ev.b == 1 && checkhit(ev.c)) { // ЛКМ
                        panMapToLocation(mark);
                        return true;
                    }
                    return super.mousedown(ev);
                }
            };
        }
    }

    /**
     * Прокручивает карту к выбранной метке драгоценного камня
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
                    gui.msg("Map moved to " + mark.resourceType + " " + mark.label, java.awt.Color.GREEN);
                } else {
                    gui.msg("Gemstone location is in a different area", java.awt.Color.YELLOW);
                }
            } catch (Exception e) {
                gui.msg("Error panning to gemstone location", java.awt.Color.RED);
            }
        }
    }
}


