package nurgling.widgets;

import haven.*;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.tools.MapDbTransfer;
import nurgling.i18n.L10n;

import java.util.Map;

import static haven.MCache.tilesz;

public class NMapWnd extends MapWnd {
    public String searchPattern = "";  // For terrain/tile search
    public String markerSearchPattern = "";  // For marker/icon search
    public Resource.Image searchRes = null;
    MapToggleButton treeBtn;
    MapToggleButton fishBtn;
    MapToggleButton mapToolsBtn;
    MapToggleButton oresBtn;
    MapToggleButton prospectBtn;
    MapToggleButton quarryartzBtn;
    MapToggleButton oreSpotsBtn; // Кнопка для переключения видимости маркеров спотов руд
    MapToggleButton gemstoneBtn; // Кнопка для переключения видимости маркеров драгоценных камней
    MapToggleButton animalsBtn;  // Кнопка для переключения видимости маркеров животных (ObjectTracker + БД)
    MapToggleButton foragingBtn;
    MapToggleButton vectorClearBtn;
    TextEntry markerSearchField;
    Button dbExportBtn;
    Button dbImportBtn;
    private static final int btnw = UI.scale(95);
    private static final int dbbtnw = UI.scale(110);

    public class MapToggleButton extends ICheckBox {
        private final Runnable rightClickAction;
        
        public MapToggleButton(String base, String tooltip, Runnable rightClickAction) {
            super("nurgling/hud/buttons/" + base + "/", "u", "d", "h", "dh");
            this.rightClickAction = rightClickAction;
            settip(tooltip);
        }
        
        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if(ev.b == 3 && checkhit(ev.c)) {
                if(rightClickAction != null)
                    rightClickAction.run();
                return true;
            }
            return super.mousedown(ev);
        }
    }

    public NMapWnd(MapFile file, MapView mv, Coord sz, String title) {
        super(file, mv, sz, title);
        searchRes = Resource.local().loadwait("alttex/selectedtex").layer(Resource.imgc);
        
        // Position buttons in top-right corner (15px right, 10px down from original position)
        int btnSpacing = UI.scale(5);
        Coord btnPos = view.c.add(view.sz.x - UI.scale(35), UI.scale(15));
        
        // Map tools button (rightmost) - opens the Map Tools panel (no icon toggle)
        mapToolsBtn = add(new MapToggleButton("maptools", L10n.get("maptools.button_tip"), MapToolsWindow::toggle), btnPos);
        mapToolsBtn.a = false; // Always show as unpressed (no toggle state)
        mapToolsBtn.click(MapToolsWindow::toggle); // Left click opens the panel

        // Ores button - opens Terrain Search Window (no icon toggle)
        btnPos = btnPos.sub(mapToolsBtn.sz.x + btnSpacing, 0);
        oresBtn = add(new MapToggleButton("ores", "Ores Search", this::openOresSearch), btnPos);
        oresBtn.a = false; // Always show as unpressed (no toggle state)
        oresBtn.click(this::openOresSearch); // Left click opens window
        
        // Fish button - shares its state with the Map Tools panel through NConfig
        btnPos = btnPos.sub(oresBtn.sz.x + btnSpacing, 0);
        fishBtn = add(new MapToggleButton("fish", "Toggle fish icons (Right-click: Fish Search)", MapToolsWindow::openFishSearch), btnPos);
        fishBtn.state(() -> NMiniMap.showFishIcons());
        fishBtn.set(val -> NMiniMap.showFishIcons(val));

        // Tree button
        btnPos = btnPos.sub(fishBtn.sz.x + btnSpacing, 0);
        treeBtn = add(new MapToggleButton("tree", "Toggle tree icons (Right-click: Tree Search)", MapToolsWindow::openTreeSearch), btnPos);
        treeBtn.state(() -> NMiniMap.showTreeIcons());
        treeBtn.set(val -> NMiniMap.showTreeIcons(val));
        
        // Prospect button
        btnPos = btnPos.sub(treeBtn.sz.x + btnSpacing, 0);
        prospectBtn = add(new MapToggleButton("tree", "Toggle prospecting icons (Right-click: Prospecting Search)", this::openProspectingSearch), btnPos);
        prospectBtn.a = getProspectingIconsState(); // Set initial state
        prospectBtn.changed(val -> setProspectingIconsState(val));
        
        // Quarryartz button
        btnPos = btnPos.sub(prospectBtn.sz.x + btnSpacing, 0);
        quarryartzBtn = add(new MapToggleButton("tree", "Toggle Quarryartz markers (Right-click: Quarryartz Search)", this::openQuarryartzSearch), btnPos);
        quarryartzBtn.a = getQuarryartzIconsState(); // Set initial state
        quarryartzBtn.changed(val -> setQuarryartzIconsState(val));
        
        // Ore Spots button (для маркеров спотов руд)
        btnPos = btnPos.sub(quarryartzBtn.sz.x + btnSpacing, 0);
        oreSpotsBtn = add(new MapToggleButton("tree", "Toggle Ore Spot markers", null), btnPos);
        oreSpotsBtn.a = getOreSpotsIconsState(); // Set initial state
        oreSpotsBtn.changed(val -> setOreSpotsIconsState(val));
        
        // Gemstone button (для маркеров драгоценных камней)
        btnPos = btnPos.sub(oreSpotsBtn.sz.x + btnSpacing, 0);
        gemstoneBtn = add(new MapToggleButton("tree", "Toggle Gemstone markers (Right-click: Gemstone Search)", this::openGemstoneSearch), btnPos);
        gemstoneBtn.a = getGemstoneIconsState(); // Set initial state
        gemstoneBtn.changed(val -> setGemstoneIconsState(val));

        // Animals button (маркеры животных: ObjectTracker при обнаружении + синхронизация из БД)
        btnPos = btnPos.sub(gemstoneBtn.sz.x + btnSpacing, 0);
        animalsBtn = add(new MapToggleButton("tree", "Toggle Animal markers (from Discord notification list)", null), btnPos);
        animalsBtn.a = getAnimalIconsState(); // Set initial state
        animalsBtn.changed(val -> setAnimalIconsState(val));

        btnPos = btnPos.sub(animalsBtn.sz.x + btnSpacing, 0);
        foragingBtn = add(new MapToggleButton("tree", "Toggle Foraging markers (Right-click: Foraging Search)", this::openForagingSearch), btnPos);
        foragingBtn.a = getForagingIconsState();
        foragingBtn.changed(val -> setForagingIconsState(val));
        
        // Vector clear button (leftmost)
        btnPos = btnPos.sub(foragingBtn.sz.x + btnSpacing, 0);
        vectorClearBtn = add(new MapToggleButton("vector", "Clear tracking vectors", null), btnPos);
        vectorClearBtn.a = false; // Always show as unpressed
        vectorClearBtn.click(this::clearVectors);

        // Add marker search field at bottom-right (no label, no button)
        add(markerSearchField = new TextEntry(UI.scale(200), "") {
            @Override
            public void changed() {
                super.changed();
                applyMarkerSearch();
            }
            
            @Override
            public boolean keydown(KeyDownEvent ev) {
                if(ev.code == java.awt.event.KeyEvent.VK_ENTER) {
                    applyMarkerSearch();
                    return true;
                }
                return super.keydown(ev);
            }
        }, view.pos("br").sub(UI.scale(205), UI.scale(5)));

        /* The stock Export.../Import... buttons in the marker panel move a .hmap file; these move
         * the same data through the village database. Hidden unless a shared PostgreSQL is
         * configured, because there is nothing to share with otherwise. */
        add(dbExportBtn = new Button(dbbtnw, L10n.get("mapdb.btn_export"), false) {
            @Override
            public void click() {
                /* The window's own session, not whichever one happens to be in front: a second
                 * client in the same process must not upload this map under its name. */
                MapDbTransfer.export(getparent(GameUI.class), file);
            }
        });
        dbExportBtn.settip(L10n.get("mapdb.btn_export_tip"));
        add(dbImportBtn = new Button(dbbtnw, L10n.get("mapdb.btn_import"), false) {
            @Override
            public void click() {
                MapDbTransfer.importFrom(getparent(GameUI.class), file);
            }
        });
        dbImportBtn.settip(L10n.get("mapdb.btn_import_tip"));
        placeDbButtons();
    }

    /** Bottom-right of the map view, stacked above the marker search field. */
    private void placeDbButtons() {
        if((dbExportBtn == null) || (dbImportBtn == null))
            return;
        int spacing = UI.scale(5);
        int y = view.c.y + view.sz.y - UI.scale(25) - dbExportBtn.sz.y - spacing;
        int x = view.c.x + view.sz.x - UI.scale(5) - (dbbtnw * 2) - spacing;
        /* A window narrow enough to leave no room would otherwise push them off the left edge. */
        dbExportBtn.c = new Coord(Math.max(view.c.x, x), y);
        dbImportBtn.c = new Coord(Math.max(view.c.x, x) + dbbtnw + spacing, y);
    }

    private double dbBtnCheck = 0;

    @Override
    public void tick(double dt) {
        super.tick(dt);
        /* Database settings can be switched at runtime, so visibility is re-checked rather than
         * fixed at construction - but twice a second is plenty for a settings change. */
        if(dbExportBtn != null) {
            dbBtnCheck -= dt;
            if(dbBtnCheck <= 0) {
                dbBtnCheck = 0.5;
                boolean on = MapDbTransfer.configured();
                if(dbExportBtn.visible() != on) {
                    dbExportBtn.show(on);
                    dbImportBtn.show(on);
                }
            }
        }
    }

    private boolean getProspectingIconsState() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            return ((NMiniMap) gui.mmap).showProspectingIcons;
        return true;
    }

    private void setProspectingIconsState(boolean val) {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            ((NMiniMap) gui.mmap).showProspectingIcons = val;
        if(view instanceof NMiniMap)
            ((NMiniMap) view).showProspectingIcons = val;
        // Save to config for persistence
        NConfig.set(NConfig.Key.showProspectingIcons, val);
        NConfig.needUpdate();
    }

    private boolean getQuarryartzIconsState() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            return ((NMiniMap) gui.mmap).showQuarryartzIcons;
        return true;
    }

    private void setQuarryartzIconsState(boolean val) {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            ((NMiniMap) gui.mmap).showQuarryartzIcons = val;
        if(view instanceof NMiniMap)
            ((NMiniMap) view).showQuarryartzIcons = val;
        // Save to config for persistence
        NConfig.set(NConfig.Key.showQuarryartzIcons, val);
        NConfig.needUpdate();
    }
    
    private boolean getOreSpotsIconsState() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            return ((NMiniMap) gui.mmap).showOreSpotIcons;
        return true;
    }
    
    private void setOreSpotsIconsState(boolean val) {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            ((NMiniMap) gui.mmap).showOreSpotIcons = val;
        if(view instanceof NMiniMap)
            ((NMiniMap) view).showOreSpotIcons = val;
        // Save to config for persistence
        NConfig.set(NConfig.Key.showOreSpotIcons, val);
        NConfig.needUpdate();
    }
    
    private boolean getGemstoneIconsState() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            return ((NMiniMap) gui.mmap).showGemstoneIcons;
        return true;
    }
    
    private void setGemstoneIconsState(boolean val) {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            ((NMiniMap) gui.mmap).showGemstoneIcons = val;
        if(view instanceof NMiniMap)
            ((NMiniMap) view).showGemstoneIcons = val;
        // Save to config for persistence
        NConfig.set(NConfig.Key.showGemstoneIcons, val);
        NConfig.needUpdate();
    }

    private boolean getAnimalIconsState() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            return ((NMiniMap) gui.mmap).showAnimalIcons;
        return true;
    }

    private void setAnimalIconsState(boolean val) {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            ((NMiniMap) gui.mmap).showAnimalIcons = val;
        if(view instanceof NMiniMap)
            ((NMiniMap) view).showAnimalIcons = val;
        NConfig.set(NConfig.Key.showAnimalIcons, val);
    }

    private boolean getForagingIconsState() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            return ((NMiniMap) gui.mmap).showForagingIcons;
        return true;
    }

    private void setForagingIconsState(boolean val) {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            ((NMiniMap) gui.mmap).showForagingIcons = val;
        if(view instanceof NMiniMap)
            ((NMiniMap) view).showForagingIcons = val;
        NConfig.set(NConfig.Key.showForagingIcons, val);
        NConfig.needUpdate();
    }

    private void openForagingSearch() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null) {
            if(gui.foragingSearchWindow != null) {
                if(gui.foragingSearchWindow.visible()) {
                    gui.foragingSearchWindow.hide();
                } else {
                    gui.foragingSearchWindow.show();
                    gui.foragingSearchWindow.raise();
                }
            } else {
                gui.foragingSearchWindow = new ForagingSearchWindow(gui);
                gui.add(gui.foragingSearchWindow, new Coord(100, 100));
                gui.foragingSearchWindow.show();
            }
        }
    }
    
    private void openGemstoneSearch() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null) {
            if(gui.gemstoneSearchWindow != null) {
                if(gui.gemstoneSearchWindow.visible()) {
                    gui.gemstoneSearchWindow.hide();
                } else {
                    gui.gemstoneSearchWindow.show();
                    gui.gemstoneSearchWindow.raise();
                }
            } else {
                gui.gemstoneSearchWindow = new GemstoneSearchWindow(gui);
                gui.add(gui.gemstoneSearchWindow, new Coord(100, 100));
                gui.gemstoneSearchWindow.show();
            }
        }
    }

    private void openTreeSearch() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null) {
            if(gui.treeSearchWindow != null) {
                if(gui.treeSearchWindow.visible()) {
                    gui.treeSearchWindow.hide();
                } else {
                    gui.treeSearchWindow.show();
                    gui.treeSearchWindow.raise();
                }
            } else {
                gui.treeSearchWindow = new TreeSearchWindow(gui);
                gui.add(gui.treeSearchWindow, new Coord(100, 100));
                gui.treeSearchWindow.show();
            }
        }
    }

    private void openFishSearch() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null) {
            if(gui.fishSearchWindow != null) {
                if(gui.fishSearchWindow.visible()) {
                    gui.fishSearchWindow.hide();
                } else {
                    gui.fishSearchWindow.show();
                    gui.fishSearchWindow.raise();
                }
            } else {
                gui.fishSearchWindow = new FishSearchWindow(gui);
                gui.add(gui.fishSearchWindow, new Coord(100, 100));
                gui.fishSearchWindow.show();
            }
        }
    }

    private void openOresSearch() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null) {
            if(gui.terrainSearchWindow != null) {
                if(gui.terrainSearchWindow.visible()) {
                    gui.terrainSearchWindow.hide();
                } else {
                    gui.terrainSearchWindow.show();
                    gui.terrainSearchWindow.raise();
                }
            } else {
                gui.terrainSearchWindow = new TerrainSearchWindow();
                gui.add(gui.terrainSearchWindow, new Coord(100, 100));
                gui.terrainSearchWindow.show();
            }
        }
    }

    private void openProspectingSearch() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null) {
            if(gui.prospectingSearchWindow != null) {
                if(gui.prospectingSearchWindow.visible()) {
                    gui.prospectingSearchWindow.hide();
                } else {
                    gui.prospectingSearchWindow.show();
                    gui.prospectingSearchWindow.raise();
                }
            } else {
                gui.prospectingSearchWindow = new ProspectingSearchWindow(gui);
                gui.add(gui.prospectingSearchWindow, new Coord(100, 100));
                gui.prospectingSearchWindow.show();
            }
        }
    }

    private void openQuarryartzSearch() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null) {
            if(gui.quarryartzSearchWindow != null) {
                if(gui.quarryartzSearchWindow.visible()) {
                    gui.quarryartzSearchWindow.hide();
                } else {
                    gui.quarryartzSearchWindow.show();
                    gui.quarryartzSearchWindow.raise();
                }
            } else {
                gui.quarryartzSearchWindow = new QuarryartzSearchWindow(gui);
                gui.add(gui.quarryartzSearchWindow, new Coord(100, 100));
                gui.quarryartzSearchWindow.show();
            }
        }
    }

    private void clearVectors() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.map instanceof nurgling.NMapView) {
            nurgling.NMapView mapView = (nurgling.NMapView) gui.map;
            if(!mapView.directionalVectors.isEmpty()) {
                int count = mapView.directionalVectors.size();
                mapView.clearDirectionalVectors();
                nurgling.tools.DirectionalVector.resetColorCycle();
                gui.msg("Cleared " + count + " directional vector" + (count > 1 ? "s" : ""));
            }
        }
    }

    public long playerSegmentId() {
        MiniMap.Location sessloc = view.sessloc;
        if(sessloc == null) {return 0;}
        return sessloc.seg.id;
    }

    public Coord2d findMarkerPosition(String name) {
        MiniMap.Location sessloc = view.sessloc;
        if(sessloc == null) {return null;}
        for (MapFile.Marker mark : file.markers) {
            if(mark instanceof MapFile.SMarker) {
                MapFile.SMarker m = (MapFile.SMarker) mark;
                if(m.seg == sessloc.seg.id && m.nm != null && name != null && m.nm.contains(name)) {
                    return m.tc.sub(sessloc.tc).mul(tilesz);
                }
            }
        }
        return null;
    }
    
    private void applyMarkerSearch() {
        String pattern = markerSearchField.text().trim();
        markerSearchPattern = pattern;
    }

    @Override
    public void resize(Coord sz) {
        super.resize(sz);
        
        // Position buttons in top-right corner (15px right, 10px down from original position)
        if(mapToolsBtn != null && oresBtn != null && fishBtn != null && treeBtn != null && prospectBtn != null && gemstoneBtn != null && animalsBtn != null && foragingBtn != null && vectorClearBtn != null) {
            int btnSpacing = UI.scale(5);
            Coord btnPos = view.c.add(view.sz.x - UI.scale(35), UI.scale(15));

            mapToolsBtn.c = btnPos;
            btnPos = btnPos.sub(mapToolsBtn.sz.x + btnSpacing, 0);
            oresBtn.c = btnPos;
            btnPos = btnPos.sub(oresBtn.sz.x + btnSpacing, 0);
            fishBtn.c = btnPos;
            btnPos = btnPos.sub(fishBtn.sz.x + btnSpacing, 0);
            treeBtn.c = btnPos;
            btnPos = btnPos.sub(treeBtn.sz.x + btnSpacing, 0);
            prospectBtn.c = btnPos;
            btnPos = btnPos.sub(prospectBtn.sz.x + btnSpacing, 0);
            quarryartzBtn.c = btnPos;
            btnPos = btnPos.sub(quarryartzBtn.sz.x + btnSpacing, 0);
            oreSpotsBtn.c = btnPos;
            btnPos = btnPos.sub(oreSpotsBtn.sz.x + btnSpacing, 0);
            gemstoneBtn.c = btnPos;
            btnPos = btnPos.sub(gemstoneBtn.sz.x + btnSpacing, 0);
            animalsBtn.c = btnPos;
            btnPos = btnPos.sub(animalsBtn.sz.x + btnSpacing, 0);
            foragingBtn.c = btnPos;
            btnPos = btnPos.sub(foragingBtn.sz.x + btnSpacing, 0);
            vectorClearBtn.c = btnPos;
        }
        
        // Keep marker search field at bottom-right
        if(markerSearchField != null)
            markerSearchField.c = view.c.add(view.sz.x - UI.scale(205), view.sz.y - UI.scale(25));

        placeDbButtons();
    }
    
    @Override
    public boolean mousedown(MouseDownEvent ev) {
        // Handle alt+left-click for waypoint queueing (on button release handled below)
        // Handle shift+right-click for resource timers
        if(view.c != null) {
            // Convert global coordinates to view coordinates
            Coord viewCoord = ev.c.sub(view.parentpos(this));

            // Check if the click is within the view bounds
            if(viewCoord.x >= 0 && viewCoord.x < view.sz.x &&
               viewCoord.y >= 0 && viewCoord.y < view.sz.y) {

                // Shift+right-click for resource timers and tree locations
                if(ev.b == 3 && ui.modshift) {
                    // First check for tree icons
                    if(handleTreeSaveClick(viewCoord)) {
                        return true; // Consume the event
                    }
                    // Then check if there's a resource marker at this location
                    if(handleResourceTimerClick(viewCoord)) {
                        return true; // Consume the event
                    }
                }
            }
        }

        return super.mousedown(ev);
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
        if(view.c != null) {
            Coord viewCoord = ev.c.sub(view.parentpos(this));

            // Check if the click is within the view bounds
            if(viewCoord.x >= 0 && viewCoord.x < view.sz.x &&
               viewCoord.y >= 0 && viewCoord.y < view.sz.y) {

                // Left-click for forager path recording (without modifier)
                if(ev.b == 1 && !ui.modmeta && !ui.modshift && !ui.modctrl) {
                    if(handleForagerRecordingClick(viewCoord)) {
                        return true; // Consume the event
                    }
                }
                
                // alt+left-click for waypoint queueing; shift is excluded because
                // alt+shift+left-click is the map ping (NMiniMap.sendPointPing)
                if(ev.b == 1 && ui.modmeta && !ui.modshift) {
                    if(handleWaypointClick(viewCoord)) {
                        return true; // Consume the event
                    }
                }

                // Right-click for clearing waypoint queue (fish handling is in parent NMiniMap)
                if(ev.b == 3 && !ui.modshift) {
                    // Clear waypoint queue on regular right-click (if not on fish/marker)
                    NGameUI gui = (NGameUI) NUtils.getGameUI();
                    if(gui != null && gui.waypointMovementService != null) {
                        gui.waypointMovementService.clearQueue();
                    }
                    // Let parent handle fish location clicks and other right-click behavior
                }
            }
        }

        return super.mouseup(ev);
    }

    private boolean handleForagerRecordingClick(Coord c) {
        // Check if a PathRecordable window is open and in recording mode
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui == null) return false;

        // Find a PathRecordable window (Forager or TrufflePigHunter)
        nurgling.widgets.bots.PathRecordable pathWnd = null;
        for(Widget wdg = gui.lchild; wdg != null; wdg = wdg.prev) {
            if(wdg instanceof nurgling.widgets.bots.PathRecordable) {
                pathWnd = (nurgling.widgets.bots.PathRecordable) wdg;
                break;
            }
        }

        if(pathWnd == null || !pathWnd.isRecording()) {
            return false; // Not recording, don't consume the event
        }

        // Get the location at the clicked position
        MiniMap.Location clickLoc = view.xlate(c);
        if(clickLoc == null || view.sessloc == null) return false;

        // Only handle if in same segment
        if(clickLoc.seg.id != view.sessloc.seg.id) return false;

        // Create ForagerWaypoint from MiniMap.Location
        nurgling.routes.ForagerWaypoint waypoint = new nurgling.routes.ForagerWaypoint(clickLoc);

        // Add waypoint to the recording path
        pathWnd.addWaypointToRecording(waypoint);

        return true; // Consume the event
    }
    
    private boolean handleWaypointClick(Coord c) {
        // Try to get the location at clicked coordinates
        MiniMap.Location clickLoc = view.xlate(c);
        if(clickLoc == null || view.sessloc == null) return false;

        // Only handle if in same segment
        if(clickLoc.seg.id != view.sessloc.seg.id) return false;

        // Use the service to add waypoint
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.waypointMovementService != null) {
            gui.waypointMovementService.addWaypoint(clickLoc, view.sessloc);
            return true;
        }

        return false;
    }
    
    private boolean handleResourceTimerClick(Coord c) {
        // Try to find a resource marker at the clicked location
        MiniMap.Location clickLoc = view.xlate(c);
        if(clickLoc == null) return false;

        MiniMap.DisplayMarker marker = view.markerat(clickLoc.tc);
        if(marker != null && marker.m instanceof MapFile.SMarker) {
            MapFile.SMarker smarker = (MapFile.SMarker) marker.m;

            // Handle through service
            NGameUI gui = (NGameUI) NUtils.getGameUI();
            if(gui != null && gui.localizedResourceTimerService != null) {
                return gui.localizedResourceTimerService.handleResourceClick(smarker);
            }
        }

        return false;
    }

    private boolean handleTreeSaveClick(Coord c) {
        // TODO: Implement tree saving from map click
        // For now, trees can be saved through other means
        // This would require access to gobs at the clicked location
        return false;
    }

    @Override
    public void recenter() {
        super.recenter();
    }
}
