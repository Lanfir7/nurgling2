package nurgling.widgets;

import haven.*;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.tools.VSpec;

import java.util.Map;
import java.util.ArrayList;

import static haven.MCache.tilesz;
import static haven.MCache.cmaps;

public class NMapWnd extends MapWnd {
    public String searchPattern = "";  // For terrain/tile search
    public String markerSearchPattern = "";  // For marker/icon search
    public Resource.Image searchRes = null;
    MapToggleButton treeBtn;
    MapToggleButton fishBtn;
    MapToggleButton oresBtn;
    MapToggleButton prospectBtn;
    MapToggleButton quarryartzBtn;
    MapToggleButton oreSpotsBtn; // Кнопка для переключения видимости маркеров спотов руд
    MapToggleButton gemstonesBtn; // Кнопка для переключения видимости маркеров драгоценных камней
    MapToggleButton vectorClearBtn;
    TextEntry markerSearchField;
    private static final int btnw = UI.scale(95);

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
        
        // Ores button (rightmost) - opens Terrain Search Window (no icon toggle)
        oresBtn = add(new MapToggleButton("ores", "Ores Search", this::openOresSearch), btnPos);
        oresBtn.a = false; // Always show as unpressed (no toggle state)
        oresBtn.click(this::openOresSearch); // Left click opens window
        
        // Fish button
        btnPos = btnPos.sub(oresBtn.sz.x + btnSpacing, 0);
        fishBtn = add(new MapToggleButton("fish", "Toggle fish icons (Right-click: Fish Search)", this::openFishSearch), btnPos);
        fishBtn.a = getFishIconsState(); // Set initial state
        fishBtn.changed(val -> setFishIconsState(val));
        
        // Tree button
        btnPos = btnPos.sub(fishBtn.sz.x + btnSpacing, 0);
        treeBtn = add(new MapToggleButton("tree", "Toggle tree icons (Right-click: Tree Search)", this::openTreeSearch), btnPos);
        treeBtn.a = getTreeIconsState(); // Set initial state
        treeBtn.changed(val -> setTreeIconsState(val));
        
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
        oreSpotsBtn = add(new MapToggleButton("tree", "Toggle Ore Spot markers (Right-click: Ore Search)", this::openOreSearch), btnPos);
        oreSpotsBtn.a = getOreSpotsIconsState(); // Set initial state
        oreSpotsBtn.changed(val -> setOreSpotsIconsState(val));
        
        // Gemstones button (для маркеров драгоценных камней)
        btnPos = btnPos.sub(oreSpotsBtn.sz.x + btnSpacing, 0);
        gemstonesBtn = add(new MapToggleButton("tree", "Toggle Gemstone markers (Right-click: Gemstone Search)", this::openGemstoneSearch), btnPos);
        gemstonesBtn.a = getGemstonesIconsState(); // Set initial state
        gemstonesBtn.changed(val -> setGemstonesIconsState(val));
        
        // Vector clear button (leftmost)
        btnPos = btnPos.sub(oreSpotsBtn.sz.x + btnSpacing, 0);
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
    }

    private boolean getTreeIconsState() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            return ((NMiniMap) gui.mmap).showTreeIcons;
        return true;
    }

    private void setTreeIconsState(boolean val) {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            ((NMiniMap) gui.mmap).showTreeIcons = val;
        if(view instanceof NMiniMap)
            ((NMiniMap) view).showTreeIcons = val;
    }

    private boolean getFishIconsState() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            return ((NMiniMap) gui.mmap).showFishIcons;
        return true;
    }

    private void setFishIconsState(boolean val) {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            ((NMiniMap) gui.mmap).showFishIcons = val;
        if(view instanceof NMiniMap)
            ((NMiniMap) view).showFishIcons = val;
    }

    private boolean getProspectingIconsState() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            return ((NMiniMap) gui.mmap).showProspectingIcons;
        // Загружаем из конфига
        Boolean saved = (Boolean) NConfig.get(NConfig.Key.showProspectingIcons);
        return saved != null ? saved : true;
    }

    private void setProspectingIconsState(boolean val) {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            ((NMiniMap) gui.mmap).showProspectingIcons = val;
        if(view instanceof NMiniMap)
            ((NMiniMap) view).showProspectingIcons = val;
        // Сохраняем состояние
        NConfig.set(NConfig.Key.showProspectingIcons, val);
        NConfig.needUpdate();
    }

    private boolean getQuarryartzIconsState() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            return ((NMiniMap) gui.mmap).showQuarryartzIcons;
        // Загружаем из конфига
        Boolean saved = (Boolean) NConfig.get(NConfig.Key.showQuarryartzIcons);
        return saved != null ? saved : true;
    }

    private void setQuarryartzIconsState(boolean val) {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            ((NMiniMap) gui.mmap).showQuarryartzIcons = val;
        if(view instanceof NMiniMap)
            ((NMiniMap) view).showQuarryartzIcons = val;
        // Сохраняем состояние
        NConfig.set(NConfig.Key.showQuarryartzIcons, val);
        NConfig.needUpdate();
    }
    
    private boolean getOreSpotsIconsState() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            return ((NMiniMap) gui.mmap).showOreSpotIcons;
        // Загружаем из конфига
        Boolean saved = (Boolean) NConfig.get(NConfig.Key.showOreSpotIcons);
        return saved != null ? saved : true;
    }
    
    private void setOreSpotsIconsState(boolean val) {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            ((NMiniMap) gui.mmap).showOreSpotIcons = val;
        if(view instanceof NMiniMap)
            ((NMiniMap) view).showOreSpotIcons = val;
        // Сохраняем состояние
        NConfig.set(NConfig.Key.showOreSpotIcons, val);
        NConfig.needUpdate();
    }
    
    private boolean getGemstonesIconsState() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            return ((NMiniMap) gui.mmap).showGemstoneIcons;
        // Загружаем из конфига
        Boolean saved = (Boolean) NConfig.get(NConfig.Key.showGemstoneIcons);
        return saved != null ? saved : true;
    }
    
    private void setGemstonesIconsState(boolean val) {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null && gui.mmap instanceof NMiniMap)
            ((NMiniMap) gui.mmap).showGemstoneIcons = val;
        if(view instanceof NMiniMap)
            ((NMiniMap) view).showGemstoneIcons = val;
        // Сохраняем состояние
        NConfig.set(NConfig.Key.showGemstoneIcons, val);
        NConfig.needUpdate();
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

    private void openOreSearch() {
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui != null) {
            if(gui.oreSearchWindow != null) {
                if(gui.oreSearchWindow.visible()) {
                    gui.oreSearchWindow.hide();
                } else {
                    gui.oreSearchWindow.show();
                    gui.oreSearchWindow.raise();
                }
            } else {
                gui.oreSearchWindow = new OreSearchWindow(gui);
                gui.add(gui.oreSearchWindow, new Coord(100, 100));
                gui.oreSearchWindow.show();
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
        for (Map.Entry<Long, MapFile.SMarker> e : file.smarkers.entrySet()) {
            MapFile.SMarker m = e.getValue();
            if(m.seg == sessloc.seg.id && m.nm!= null && name!=null && m.nm.contains(name)) {
                return m.tc.sub(sessloc.tc).mul(tilesz);
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
        if(oresBtn != null && fishBtn != null && treeBtn != null && prospectBtn != null && vectorClearBtn != null) {
            int btnSpacing = UI.scale(5);
            Coord btnPos = view.c.add(view.sz.x - UI.scale(35), UI.scale(15));

            oresBtn.c = btnPos;
            btnPos = btnPos.sub(oresBtn.sz.x + btnSpacing, 0);
            fishBtn.c = btnPos;
            btnPos = btnPos.sub(fishBtn.sz.x + btnSpacing, 0);
            treeBtn.c = btnPos;
            btnPos = btnPos.sub(treeBtn.sz.x + btnSpacing, 0);
            prospectBtn.c = btnPos;
            btnPos = btnPos.sub(prospectBtn.sz.x + btnSpacing, 0);
            vectorClearBtn.c = btnPos;
        }
        
        // Keep marker search field at bottom-right
        if(markerSearchField != null)
            markerSearchField.c = view.c.add(view.sz.x - UI.scale(205), view.sz.y - UI.scale(25));
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
                
                // alt+left-click for waypoint queueing
                if(ev.b == 1 && ui.modmeta) {
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
        // Check if Forager window is open and in recording mode
        NGameUI gui = (NGameUI) NUtils.getGameUI();
        if(gui == null) return false;
        
        // Find the Forager window
        nurgling.widgets.bots.Forager foragerWnd = null;
        for(Widget wdg = gui.lchild; wdg != null; wdg = wdg.prev) {
            if(wdg instanceof nurgling.widgets.bots.Forager) {
                foragerWnd = (nurgling.widgets.bots.Forager) wdg;
                break;
            }
        }
        
        if(foragerWnd == null || !foragerWnd.isRecording()) {
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
        foragerWnd.addWaypointToRecording(waypoint);
        
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
    
    /**
     * Переопределяем markobj для исправления пути к ресурсу иконки для маркеров проспектинга
     * Использует VSpec для получения правильного пути (как в Icon Settings)
     */
    @Override
    public void markobj(long gobid, long oid, Indir<Resource> resid, String nm) {
        // Сначала получаем правильный путь к ресурсу через VSpec
        try {
            Resource res = resid.get();
            String rnm = nm;
            if(rnm == null) {
                Resource.Tooltip tt = res.layer(Resource.tooltip);
                if(tt != null) {
                    rnm = tt.t;
                }
            }
            
            // Получаем правильный путь к ресурсу из VSpec (как в редакторе категорий - используем JSONObject напрямую)
            String correctResourcePath = null;
            
            // Ищем JSONObject в VSpec по названию (как в NCatSelection)
            org.json.JSONObject vspecObj = findVSpecObjectByName(rnm);
            if(vspecObj != null && vspecObj.has("static")) {
                correctResourcePath = vspecObj.getString("static");
            } else {
                // Fallback на старый метод, если не найден в VSpec
                correctResourcePath = getCorrectResourcePathForProspecting(rnm, res.name);
            }
            
            // Если путь нужно исправить, используем исправленный ресурс
            if(correctResourcePath != null && !correctResourcePath.equals(res.name)) {
                // Используем исправленный путь к ресурсу (как в MasterMiner - сначала пробуем wineglance, потом cuprite)
                // Проверяем, что ресурс действительно существует и имеет изображение с img (как в ItemTex.create())
                try {
                    Resource testRes = Resource.remote().loadwait(correctResourcePath);
                    if(testRes != null) {
                        Resource.Image loadedImg = testRes.layer(Resource.imgc);
                        if(loadedImg != null && loadedImg.img != null) {
                            // Ресурс загружен правильно, создаем обертку Indir, которая возвращает ресурс с правильным путем
                            // Проблема: родительский метод использует res.name напрямую, поэтому нужно создать обертку
                            final Resource finalTestRes = testRes;
                            final String finalCorrectPath = correctResourcePath;
                            final int finalVer = testRes.ver;
                            Indir<Resource> wrappedRes = new Indir<Resource>() {
                                public Resource get() {
                                    // Возвращаем загруженный ресурс, но с правильным путем через Resource.Saved
                                    return finalTestRes;
                                }
                            };
                            // Используем Resource.Saved напрямую - он будет использован в родительском методе
                            Resource.Saved savedRes = new Resource.Saved(Resource.remote(), finalCorrectPath, finalVer);
                            super.markobj(gobid, oid, savedRes, nm);
                            
                            // Обновляем маркер с правильным путем после создания (родительский метод использует res.name)
                            // Используем отложенное обновление через UI thread
                            final String finalRnm = rnm;
                            ui.root.add(new Widget() {
                                public void tick(double dt) {
                                    try {
                                        Gob gob = ui.sess.glob.oc.getgob(gobid);
                                        if(gob != null) {
                                            MiniMap.MarkerID markerId = gob.getattr(MiniMap.MarkerID.class);
                                            if(markerId != null && markerId.mark instanceof MapFile.SMarker) {
                                                MapFile.SMarker mark = (MapFile.SMarker)markerId.mark;
                                                if(!mark.res.name.equals(finalCorrectPath)) {
                                                    mark.res = new Resource.Saved(Resource.remote(), finalCorrectPath, finalVer);
                                                    view.file.update(mark);
                                                    if(finalRnm != null && finalRnm.toLowerCase().contains("wine")) {
                                                        System.err.println("NMapWnd.markobj: Marker updated with corrected path: path='" + finalCorrectPath + "'");
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        // Игнорируем ошибки
                                    }
                                    reqdestroy();
                                }
                            });
                            
                            if(rnm != null && rnm.toLowerCase().contains("wine")) {
                                System.err.println("NMapWnd.markobj: Using corrected path: name='" + rnm + "', path='" + correctResourcePath + "', ver=" + testRes.ver);
                            }
                            return;
                        } else {
                            if(rnm != null && rnm.toLowerCase().contains("wine")) {
                                System.err.println("NMapWnd.markobj: Corrected resource loaded but img is null: path='" + correctResourcePath + "'");
                            }
                        }
                    }
                } catch (Exception e) {
                    // Если не удалось загрузить, используем оригинальный
                    if(rnm != null && rnm.toLowerCase().contains("wine")) {
                        System.err.println("NMapWnd.markobj: Error loading corrected resource: path='" + correctResourcePath + "', error=" + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // Если не удалось получить правильный путь, используем оригинальный
        }
        
        // Вызываем родительский метод с оригинальным ресурсом
        super.markobj(gobid, oid, resid, nm);
    }
    
    /**
     * Ищет JSONObject в VSpec по названию ресурса (как в NCatSelection)
     * Возвращает JSONObject из VSpec, если найден, иначе null
     */
    private org.json.JSONObject findVSpecObjectByName(String resourceName) {
        if (resourceName == null || resourceName.trim().isEmpty() || VSpec.categories == null) {
            return null;
        }
        
        String lowerName = resourceName.trim().toLowerCase();
        
        // Ищем во всех категориях VSpec
        for (String categoryName : VSpec.categories.keySet()) {
            ArrayList<org.json.JSONObject> items = VSpec.categories.get(categoryName);
            if (items != null) {
                for (org.json.JSONObject obj : items) {
                    try {
                        String name = obj.optString("name", "");
                        if (name != null && !name.isEmpty()) {
                            String lowerVSpecName = name.toLowerCase().trim();
                            
                            // Точное совпадение (без учета регистра)
                            if (lowerVSpecName.equals(lowerName)) {
                                return obj;
                            }
                            // Нормализованное совпадение (без пробелов)
                            String normalizedVSpecName = lowerVSpecName.replaceAll("\\s+", "");
                            String normalizedInputName = lowerName.replaceAll("\\s+", "");
                            if (normalizedVSpecName.equals(normalizedInputName)) {
                                return obj;
                            }
                            // Частичное совпадение
                            if (normalizedVSpecName.contains(normalizedInputName) || normalizedInputName.contains(normalizedVSpecName)) {
                                return obj;
                            }
                            // Проверяем, содержит ли одно название другое
                            if (lowerVSpecName.contains(lowerName) || lowerName.contains(lowerVSpecName)) {
                                return obj;
                            }
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Получает правильный путь к ресурсу из VSpec по названию (как в Icon Settings)
     * Ищет в категориях Ore и Stones
     */
    private String getCorrectResourcePathForProspecting(String resourceName, String currentResourcePath) {
        if (resourceName == null || resourceName.trim().isEmpty()) {
            return null;
        }
        
        // Нормализуем название для поиска (убираем лишние пробелы, приводим к нижнему регистру)
        String normalizedName = resourceName.trim();
        String lowerName = normalizedName.toLowerCase();
        
        // ПРИОРИТЕТ 1: Ищем в VSpec (как просил пользователь - использовать VSpec для всех ресурсов)
        if (VSpec.categories != null) {
            // Ищем в категории Ore
        ArrayList<org.json.JSONObject> oreList = VSpec.categories.get("Ore");
        if (oreList != null) {
            for (org.json.JSONObject ore : oreList) {
                try {
                    String name = ore.optString("name", "");
                    if (name != null && !name.isEmpty()) {
                        String lowerVSpecName = name.toLowerCase().trim();
                        String normalizedVSpecName = lowerVSpecName.replaceAll("\\s+", "");
                        String normalizedInputName = lowerName.replaceAll("\\s+", "");
                        
                        // Точное совпадение (без учета регистра)
                        if (lowerVSpecName.equals(lowerName)) {
                            String staticPath = ore.optString("static", null);
                            if (staticPath != null && !staticPath.isEmpty()) {
                                return staticPath;
                            }
                        }
                        // Нормализованное совпадение (без пробелов)
                        if (normalizedVSpecName.equals(normalizedInputName)) {
                            String staticPath = ore.optString("static", null);
                            if (staticPath != null && !staticPath.isEmpty()) {
                                return staticPath;
                            }
                        }
                        // Частичное совпадение (для случаев типа "Wine Glace" vs "Wine Glance")
                        if (normalizedVSpecName.contains(normalizedInputName) || normalizedInputName.contains(normalizedVSpecName)) {
                            String staticPath = ore.optString("static", null);
                            if (staticPath != null && !staticPath.isEmpty()) {
                                return staticPath;
                            }
                        }
                        // Проверяем, содержит ли одно название другое (для случаев типа "Lead Glance" vs "LeadGlance")
                        if (lowerVSpecName.contains(lowerName) || lowerName.contains(lowerVSpecName)) {
                            String staticPath = ore.optString("static", null);
                            if (staticPath != null && !staticPath.isEmpty()) {
                                return staticPath;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
            }
        }
        
        // Ищем в категории Stones
        ArrayList<org.json.JSONObject> stonesList = VSpec.categories.get("Stones");
        if (stonesList != null) {
            for (org.json.JSONObject stone : stonesList) {
                try {
                    String name = stone.optString("name", "");
                    if (name != null && !name.isEmpty()) {
                        String lowerVSpecName = name.toLowerCase().trim();
                        String normalizedVSpecName = lowerVSpecName.replaceAll("\\s+", "");
                        String normalizedInputName = lowerName.replaceAll("\\s+", "");
                        
                        // Точное совпадение (без учета регистра)
                        if (lowerVSpecName.equals(lowerName)) {
                            String staticPath = stone.optString("static", null);
                            if (staticPath != null && !staticPath.isEmpty()) {
                                return staticPath;
                            }
                        }
                        // Нормализованное совпадение (без пробелов)
                        if (normalizedVSpecName.equals(normalizedInputName)) {
                            String staticPath = stone.optString("static", null);
                            if (staticPath != null && !staticPath.isEmpty()) {
                                return staticPath;
                            }
                        }
                        // Частичное совпадение (для случаев типа "Wine Glace" vs "Wine Glance")
                        if (normalizedVSpecName.contains(normalizedInputName) || normalizedInputName.contains(normalizedVSpecName)) {
                            String staticPath = stone.optString("static", null);
                            if (staticPath != null && !staticPath.isEmpty()) {
                                return staticPath;
                            }
                        }
                        // Проверяем, содержит ли одно название другое (для случаев типа "Lead Glance" vs "LeadGlance")
                        if (lowerVSpecName.contains(lowerName) || lowerName.contains(lowerVSpecName)) {
                            String staticPath = stone.optString("static", null);
                            if (staticPath != null && !staticPath.isEmpty()) {
                                return staticPath;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
            }
        }
        }
        
        // ПРИОРИТЕТ 2: Специальная обработка для известных проблемных ресурсов (только если не найдено в VSpec)
        // Сначала проверяем по текущему пути к ресурсу (может быть неправильный путь)
        if (currentResourcePath != null && !currentResourcePath.isEmpty()) {
            String lowerPath = currentResourcePath.toLowerCase();
            // Если путь содержит "cuprite" или "wineglance", но не в правильной папке
            if ((lowerPath.contains("cuprite") || lowerPath.contains("wineglance")) && !lowerPath.equals("gfx/invobjs/cuprite")) {
                return "gfx/invobjs/cuprite";
            }
            // Если путь содержит "argentite" или "silvershine", но не в правильной папке
            if ((lowerPath.contains("argentite") || lowerPath.contains("silvershine")) && !lowerPath.equals("gfx/invobjs/argentite")) {
                return "gfx/invobjs/argentite";
            }
        }
        
        if (lowerName.contains("wine") && (lowerName.contains("glance") || lowerName.contains("glace"))) {
            // Сначала пробуем wineglance, потом cuprite (как в MasterMiner)
            try {
                Resource res = Resource.remote().loadwait("gfx/invobjs/wineglance");
                if (res != null && res.layer(Resource.imgc) != null) {
                    return "gfx/invobjs/wineglance";
                }
            } catch (Exception e) {
                // Если не удалось, используем cuprite как fallback
            }
            // Всегда возвращаем cuprite как fallback (как в MasterMiner)
            try {
                Resource res = Resource.remote().loadwait("gfx/invobjs/cuprite");
                if (res != null && res.layer(Resource.imgc) != null) {
                    return "gfx/invobjs/cuprite";
                }
            } catch (Exception e) {
                // Игнорируем ошибки
            }
            // Если ничего не загрузилось, все равно возвращаем cuprite
            return "gfx/invobjs/cuprite";
        }
        if (lowerName.contains("silvershine")) {
            return "gfx/invobjs/argentite";
        }
        
        // ПРИОРИТЕТ 3: Fallback - если ресурс не найден в VSpec, пробуем загрузить напрямую по нормализованному названию
        // Это помогает для ресурсов, которые не добавлены в VSpec или имеют другое название
        String normalized = lowerName.replaceAll("\\s+", "");
        String[] possiblePaths = {
            "gfx/invobjs/" + normalized,  // Сначала пробуем нормализованное (без пробелов)
            "gfx/invobjs/" + lowerName,    // Затем с оригинальным названием
            "gfx/invobjs/ore-" + normalized,
            "gfx/invobjs/ore-" + lowerName,
            "gfx/invobjs/stone-" + normalized,
            "gfx/invobjs/stone-" + lowerName
        };
        
        for (String path : possiblePaths) {
            try {
                Resource res = Resource.remote().loadwait(path);
                if (res != null && res.layer(Resource.imgc) != null) {
                    return path;
                }
            } catch (Exception e) {
                // Пробуем следующий путь
                continue;
            }
        }
        
        return null;
    }
}
