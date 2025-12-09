package nurgling.widgets;

import haven.*;
import haven.Locked;
import nurgling.ProspectingLocation;
import nurgling.ProspectingLocationService;
import nurgling.NGameUI;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Window for searching saved prospecting locations by resource type
 * Similar to TreeSearchWindow - only resource type filter
 */
public class ProspectingSearchWindow extends Window {
    private final NGameUI gui;
    private final ProspectingLocationService prospectingService;

    private Dropbox<String> resourceTypeDropdown;
    private ProspectingResultsList resultsList;
    private List<String> resourceTypes;
    private int controlX;
    private int resourceDropdownY;

    private static final int WINDOW_WIDTH = UI.scale(350);
    private static final int WINDOW_HEIGHT = UI.scale(400);

    public ProspectingSearchWindow(NGameUI gui) {
        super(new Coord(WINDOW_WIDTH, WINDOW_HEIGHT), "Prospecting Location Search", true);
        this.gui = gui;
        this.prospectingService = gui.prospectingLocationService;

        int y = UI.scale(10);
        int labelX = UI.scale(10);
        controlX = UI.scale(100);
        resourceDropdownY = y;
        int lineHeight = UI.scale(30);

        // Resource type filter
        add(new Label("Resource Type:"), labelX, y + UI.scale(5));
        refreshResourceTypeDropdown();
        y += lineHeight;

        // Search button
        add(new Button(UI.scale(150), "Search") {
            @Override
            public void click() {
                performSearch();
            }
        }, UI.scale(100), y);
        y += lineHeight + UI.scale(10);

        // Results list
        add(new Label("Results:"), labelX, y);
        y += UI.scale(25);

        Coord resultsSize = new Coord(WINDOW_WIDTH - UI.scale(20), WINDOW_HEIGHT - y - UI.scale(10));
        resultsList = add(new ProspectingResultsList(resultsSize), labelX, y);

        pack();
    }

    /**
     * Get list of distinct resource types from saved locations
     */
    private List<String> getDistinctResourceTypes() {
        if (prospectingService == null) return new ArrayList<>();

        return prospectingService.getAllProspectingLocations().stream()
            .map(ProspectingLocation::getResourceType)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Refresh the resource type dropdown with current saved resource types
     */
    private void refreshResourceTypeDropdown() {
        // Remove old dropdown if it exists
        if (resourceTypeDropdown != null) {
            ui.destroy(resourceTypeDropdown);
        }

        // Get fresh resource types
        resourceTypes = getDistinctResourceTypes();
        resourceTypes.add(0, "Any"); // Add "Any" option at the beginning

        // Create new dropdown
        resourceTypeDropdown = add(new Dropbox<String>(UI.scale(230), Math.min(resourceTypes.size(), 10), UI.scale(20)) {
            @Override
            protected String listitem(int i) {
                return resourceTypes.get(i);
            }

            @Override
            protected int listitems() {
                return resourceTypes.size();
            }

            @Override
            protected void drawitem(GOut g, String item, int i) {
                g.text(item, Coord.z);
            }
        }, controlX, resourceDropdownY);
        resourceTypeDropdown.change(resourceTypes.get(0)); // Select "Any" by default
    }

    /**
     * Perform search based on selected filter
     */
    private void performSearch() {
        if (prospectingService == null) return;

        String selectedResource = resourceTypeDropdown.sel;

        // Get all locations and filter
        List<ProspectingLocation> results = prospectingService.getAllProspectingLocations().stream()
            .filter(loc -> {
                // Filter by resource type
                if (!selectedResource.equals("Any") && !loc.getResourceType().equals(selectedResource)) {
                    return false;
                }
                return true;
            })
            .collect(Collectors.toList());

        resultsList.setResults(results);
    }

    /**
     * List widget for displaying search results
     */
    private class ProspectingResultsList extends SListBox<ProspectingLocation, Widget> {
        private List<ProspectingLocation> results = new ArrayList<>();

        public ProspectingResultsList(Coord sz) {
            super(sz, UI.scale(25));
        }

        public void setResults(List<ProspectingLocation> results) {
            this.results = results;
        }

        @Override
        protected List<ProspectingLocation> items() {
            return results;
        }

        @Override
        protected Widget makeitem(ProspectingLocation location, int idx, Coord sz) {
            return new ItemWidget<ProspectingLocation>(this, sz, location) {
                {
                    int deleteButtonWidth = UI.scale(22); // crossSquare button size
                    int panButtonWidth = sz.x - deleteButtonWidth - UI.scale(4);

                    // Main button for panning to location
                    add(new Button(panButtonWidth, "") {
                        @Override
                        public void draw(GOut g) {
                            // Custom drawing to show resource info
                            String text = location.getResourceType();
                            g.text(text, Coord.z);
                        }

                        @Override
                        public void click() {
                            panMapToLocation(location);
                        }
                    }, Coord.z);

                    // X button for deletion using crossSquare style
                    add(new IButton(nurgling.NStyle.crossSquare[0].back,
                                   nurgling.NStyle.crossSquare[1].back,
                                   nurgling.NStyle.crossSquare[2].back) {
                        @Override
                        public void click() {
                            if (gui != null && gui.prospectingLocationService != null) {
                                gui.prospectingLocationService.removeProspectingLocation(location.getLocationId());
                                gui.msg("Removed " + location.getResourceType() + " location", java.awt.Color.YELLOW);
                                // Refresh the search results to remove the deleted item
                                performSearch();
                            }
                        }
                    }, new Coord(panButtonWidth + UI.scale(2), (sz.y - UI.scale(22)) / 2));
                }
            };
        }
    }

    /**
     * Pan the main map to the selected prospecting location
     */
    private void panMapToLocation(ProspectingLocation location) {
        if (gui == null || gui.mapfile == null) return;

        NMapWnd mapWnd = gui.mapfile;
        if (mapWnd == null || mapWnd.view == null) return;

        // Open map window if not visible
        if (!mapWnd.visible()) {
            gui.togglewnd(mapWnd);
        }

        // Get the segment for this prospecting location
        if (gui.mmap != null && gui.mmap.file != null) {
            try (Locked lk = new Locked(gui.mmap.file.lock.readLock())) {
                MapFile.Segment segment = gui.mmap.file.segments.get(location.getSegmentId());
                if (segment != null) {
                    // Create a location for the prospecting coordinates
                    MiniMap.Location targetLoc = new MiniMap.Location(segment, location.getTileCoords());
                    // Center the map view on this location
                    mapWnd.view.center(targetLoc);
                    mapWnd.view.follow(null);
                    gui.msg("Map centered on " + location.getResourceType() + " location", java.awt.Color.GREEN);
                } else {
                    gui.msg("Prospecting location is in a different area", java.awt.Color.YELLOW);
                }
            }
        }
    }

    @Override
    public void show() {
        // Refresh resource types when window is shown
        refreshResourceTypeDropdown();
        super.show();
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
