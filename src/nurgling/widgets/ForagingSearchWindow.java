package nurgling.widgets;

import haven.*;
import haven.Locked;
import nurgling.NGameUI;
import nurgling.tools.ForageMarkerLogic;

import java.util.*;
import java.util.stream.Collectors;

public class ForagingSearchWindow extends Window {
    private final NGameUI gui;

    private TextEntry thresholdEntry;
    private Dropbox<String> itemTypeDropdown;
    private ForageResultsList resultsList;
    private List<String> itemTypes;
    private int controlX;
    private int itemDropdownY;

    private static final int WINDOW_WIDTH = UI.scale(400);
    private static final int WINDOW_HEIGHT = UI.scale(450);

    public ForagingSearchWindow(NGameUI gui) {
        super(new Coord(WINDOW_WIDTH, WINDOW_HEIGHT), "Foraging Search", true);
        this.gui = gui;

        int y = UI.scale(10);
        int labelX = UI.scale(10);
        controlX = UI.scale(120);
        itemDropdownY = y;
        int lineHeight = UI.scale(30);

        add(new Label("Item type:"), labelX, y + UI.scale(5));
        refreshItemTypeDropdown();
        y += lineHeight;

        add(new Label("Quality threshold:"), labelX, y + UI.scale(5));
        thresholdEntry = add(new TextEntry(UI.scale(150), ""), controlX, y);
        y += lineHeight;

        add(new Button(UI.scale(150), "Search") {
            @Override
            public void click() {
                performSearch();
            }
        }, UI.scale(125), y);
        y += lineHeight + UI.scale(10);

        add(new Label("Results:"), labelX, y);
        y += UI.scale(25);

        Coord resultsSize = new Coord(WINDOW_WIDTH - UI.scale(20), WINDOW_HEIGHT - y - UI.scale(10));
        resultsList = add(new ForageResultsList(resultsSize), labelX, y);

        pack();
    }

    private List<String> getDistinctItemTypes() {
        if (gui == null || gui.labeledMarkService == null) return new ArrayList<>();

        Collection<LabeledMinimapMark> allMarks = gui.labeledMarkService.getAllMarks();
        return allMarks.stream()
            .filter(mark -> ForageMarkerLogic.isForageId(mark.getLocationId()))
            .map(mark -> mark.resourceType)
            .filter(type -> type != null)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    private void refreshItemTypeDropdown() {
        if (itemTypeDropdown != null) {
            ui.destroy(itemTypeDropdown);
        }

        itemTypes = getDistinctItemTypes();
        itemTypes.add(0, "Any");

        itemTypeDropdown = add(new Dropbox<String>(UI.scale(250), Math.min(itemTypes.size(), 10), UI.scale(20)) {
            @Override
            protected String listitem(int i) {
                return itemTypes.get(i);
            }

            @Override
            protected int listitems() {
                return itemTypes.size();
            }

            @Override
            protected void drawitem(GOut g, String item, int i) {
                g.text(item, Coord.z);
            }
        }, controlX, itemDropdownY);
        itemTypeDropdown.change(itemTypes.get(0));
    }

    private void performSearch() {
        if (gui == null || gui.labeledMarkService == null) return;

        String selectedType = itemTypeDropdown.sel;
        double threshold = Double.NaN;
        try {
            String txt = thresholdEntry.text().trim();
            if (!txt.isEmpty()) {
                threshold = Double.parseDouble(txt.replace(',', '.'));
            }
        } catch (Exception e) {
        }
        final Double minQuality = Double.isNaN(threshold) ? null : Double.valueOf(threshold);

        Collection<LabeledMinimapMark> allMarks = gui.labeledMarkService.getAllMarks();
        List<LabeledMinimapMark> results = allMarks.stream()
            .filter(mark -> ForageMarkerLogic.isForageId(mark.getLocationId()))
            .filter(mark -> ForageMarkerLogic.matchesWindowSearch(mark.resourceType, mark.label, selectedType, minQuality))
            .collect(Collectors.toList());

        results.sort((a, b) -> Double.compare(
            ForageMarkerLogic.parseQuality(b.label),
            ForageMarkerLogic.parseQuality(a.label)));

        resultsList.setResults(results);
    }

    private class ForageResultsList extends SListBox<LabeledMinimapMark, Widget> {
        private List<LabeledMinimapMark> results = new ArrayList<>();

        public ForageResultsList(Coord sz) {
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

                    add(new Button(UI.scale(20), "×") {
                        @Override
                        public void click() {
                            if (gui != null && gui.labeledMarkService != null) {
                                gui.labeledMarkService.removeMark(mark);
                                performSearch();
                            }
                        }
                    }, new Coord(sz.x - UI.scale(25), 0));
                }

                @Override
                public boolean mousedown(MouseDownEvent ev) {
                    if (ev.b == 1 && checkhit(ev.c)) {
                        panMapToLocation(mark);
                        return true;
                    }
                    return super.mousedown(ev);
                }
            };
        }
    }

    private void panMapToLocation(LabeledMinimapMark mark) {
        if (gui == null || gui.mapfile == null) return;

        NMapWnd mapWnd = gui.mapfile;
        if (mapWnd == null || mapWnd.view == null) return;

        if (!mapWnd.visible()) {
            gui.togglewnd(mapWnd);
        }

        if (gui.mmap != null && gui.mmap.file != null) {
            try (Locked lk = new Locked(gui.mmap.file.lock.readLock())) {
                MapFile.Segment segment = gui.mmap.file.segments.get(mark.segmentId);
                if (segment != null) {
                    MiniMap.Location targetLoc = new MiniMap.Location(segment, mark.tileCoords);
                    mapWnd.view.center(targetLoc);
                    mapWnd.view.follow(null);
                    gui.msg("Map moved to " + mark.resourceType + " " + mark.label, java.awt.Color.GREEN);
                } else {
                    gui.msg("Forage location is in a different area", java.awt.Color.YELLOW);
                }
            } catch (Exception e) {
                gui.msg("Error panning to forage location", java.awt.Color.RED);
            }
        }
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (msg.equals("close")) {
            destroy();
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }
}
