package nurgling;

import haven.*;
import haven.res.ui.rbuff.RealmBuff;
import haven.res.ui.relcnt.RelCont;
import nurgling.conf.NDiscordNotification;
import nurgling.conf.NToolBeltProp;
import nurgling.i18n.L10n;
import nurgling.notifications.DiscordHookObject;
import nurgling.overlays.QualityOl;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.tools.NSearchItem;
import nurgling.widgets.*;
import nurgling.widgets.SwimmingStatusBuff;
import nurgling.widgets.TrackingStatusBuff;
import nurgling.widgets.CrimeStatusBuff;
import nurgling.widgets.AllowVisitingStatusBuff;
import nurgling.widgets.LocalizedResourceTimersWindow;
import nurgling.widgets.LocalizedResourceTimerDialog;
import nurgling.widgets.StudyDeskPlannerWidget;
import nurgling.widgets.FishingWindowExtension;
import nurgling.sessions.BotExecutor;
import haven.MapFile;
import haven.MiniMap;
import haven.MCache;
import static haven.MCache.tilesz;
import static haven.MCache.cmaps;

import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static haven.Inventory.invsq;

public class NGameUI extends GameUI
{
    public boolean nomadMod = false;
    public NBotsMenu botsMenu;
    public NAlarmWdg alarmWdg;
    public StarvationAlertWidget starvationAlertWidget;
    public AutoLogoutWidget autoLogoutWidget;
    public NQuestInfo questinfo;
    public NGUIInfo guiinfo;
    public NSearchItem itemsForSearch = null;
    public NCraftWindow craftwnd;
    public NEditAreaName nean;
    public NEditFolderName nefn;
    public NImportStrategyDialog importDialog;
    public Specialisation spec;
    public BotsInterruptWidget biw;
    public NEquipProxy nep;
    public NBeltProxy nbp;
    private SwimmingStatusBuff swimmingBuff = null;
    private TrackingStatusBuff trackingBuff = null;
    private CrimeStatusBuff crimeBuff = null;
    private AllowVisitingStatusBuff allowVisitingBuff = null;
    public NRecentActionsPanel recentActionsPanel;
    public DrinkMeter drinkMeter;
    public LocalizedResourceTimersWindow localizedResourceTimersWindow = null;
    private LocalizedResourceTimerDialog localizedResourceTimerDialog = null;
    public LocalizedResourceTimerService localizedResourceTimerService;
    public WaypointMovementService waypointMovementService;
    public PingService pingService;
    public FishLocationService fishLocationService;
    public PeerPositionService peerPositionService;
    public FishSearchWindow fishSearchWindow = null;
    public final Map<String, FishLocationDetailsWindow> openFishDetailWindows = new HashMap<>();
    public TreeLocationService treeLocationService;
    public TreeSearchWindow treeSearchWindow = null;
    public final Map<String, TreeLocationDetailsWindow> openTreeDetailWindows = new HashMap<>();
    public ProspectingLocationService prospectingLocationService;
    public ProspectingSearchWindow prospectingSearchWindow = null;
    public nurgling.widgets.QuarryartzSearchWindow quarryartzSearchWindow = null;
    public nurgling.widgets.OreSearchWindow oreSearchWindow = null;
    public nurgling.widgets.GemstoneSearchWindow gemstoneSearchWindow = null;
    public nurgling.widgets.ForagingSearchWindow foragingSearchWindow = null;
    public LabeledMarkService labeledMarkService;
    public final ForagePickupMarker foragePickupMarker;
    public MapToolsWindow mapToolsWindow = null;
    public StudyDeskPlannerWidget studyDeskPlanner = null;
    public NDraggableWidget studyReportWidget = null;
    public SimpleRoutesWidget simpleRoutesWidget = null;
    public AgentWindow agentWindow = null;
    public DbStatsOverlay dbStatsOverlay = null;
    public AnimalMarkerSyncService animalMarkerSyncService = null;
    public LocalTimerSyncService localTimerSyncService = null;
    /** Отдельный поток для установки маркеров в БД и загрузки из БД (merge выполняется на UI-потоке). */
    private volatile java.util.concurrent.ExecutorService animalMarkerWorker = null;

    public synchronized java.util.concurrent.ExecutorService getAnimalMarkerWorker() {
        if (animalMarkerWorker == null) {
            animalMarkerWorker = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "AnimalMarkerWorker");
                t.setDaemon(true);
                return t;
            });
        }
        return animalMarkerWorker;
    }

    /** Макрос «Маркеры животных»: при включении ставит маркер при первой встрече с любым криттером; качество добавляется при инспекте лупы по трупу. */
    private volatile boolean animalMarkerMacroRunning = false;
    private Thread animalMarkerMacroThread = null;
    private volatile Thread heavyWidgetsThread = null;
    private nurgling.actions.ObjectTracker animalMarkerMacroTracker = null;
    public boolean isAnimalMarkerMacroEnabled() { return animalMarkerMacroRunning; }

    /** Паттерны для макроса маркеров животных берутся из настроек (Animal markers). */
    private static java.util.ArrayList<String> getAnimalMarkerMacroPatterns() {
        return nurgling.widgets.nsettings.AnimalMarkersSettings.getEnabledPatterns();
    }
    
    public void startAnimalMarkerMacro() {
        if (animalMarkerMacroRunning) return;
        // Проверяем, включены ли метки в настройках
        if (!nurgling.widgets.nsettings.AnimalMarkersSettings.isMarkerEnabled()) {
            msg("Маркеры животных отключены в настройках (Bots -> Animal markers)");
            return;
        }
        animalMarkerMacroRunning = true;
        animalMarkerMacroThread = BotExecutor.runTask("AnimalMarkerMacro", () -> {
            nurgling.NGameUI gui = nurgling.NUtils.getGameUI();
            if (gui == null) {
                animalMarkerMacroRunning = false;
                return;
            }
            while (animalMarkerMacroRunning && gui.ui != null) {
                try {
                    // Проверяем настройку каждую итерацию
                    if (!nurgling.widgets.nsettings.AnimalMarkersSettings.isMarkerEnabled()) {
                        stopAnimalMarkerMacro();
                        return;
                    }
                    if (animalMarkerMacroTracker == null) {
                        // filterKritterOnly = true: только kritter + пропускаем трупы (knock)
                        animalMarkerMacroTracker = new nurgling.actions.ObjectTracker(gui, getAnimalMarkerMacroPatterns(), false, false, true);
                    }
                    if (animalMarkerMacroTracker != null)
                        animalMarkerMacroTracker.checkObjects();
                } catch (Exception e) {
                    if (animalMarkerMacroRunning) gui.msg("Макрос маркеров животных: " + e.getMessage());
                }
                for (int i = 0; i < 20 && animalMarkerMacroRunning; i++) {
                    try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
            }
            animalMarkerMacroTracker = null;
        });
    }
    
    public void stopAnimalMarkerMacro() {
        animalMarkerMacroRunning = false;
        if (animalMarkerMacroThread != null) {
            animalMarkerMacroThread.interrupt();
            animalMarkerMacroThread = null;
        }
        animalMarkerMacroTracker = null;
        msg("Макрос: маркеры животных выключен.");
    }

    public nurgling.routes.ForagerPath activeBotPath = null;

    /** Prospecting results waiting to be paired up with their window; see NProspecting. */
    public final NProspecting.Pending prospecting = new NProspecting.Pending();

    // Temporary rings (session-only, for objects without GobIcon)
    // Maps resource name to ring enabled state
    public final Map<String, Boolean> tempRingResources = Collections.synchronizedMap(new HashMap<>());
    
    // Maps gob id to kin name for party member names on minimap
    public static Map<Long, String> gobIdToKinName = new ConcurrentHashMap<>();

    /** Saved when inspection tooltip is processed (e.g. name); used when "Will refill in" arrives later via system msg. */
    private MapView.ClickedGob pendingRefillGob = null;

    public void setPendingRefillGob(MapView.ClickedGob g) { this.pendingRefillGob = g; }

    public static float worldSpeed;
    public static final float DEFAULT_WORLD_SPEED = 3.29f;
    private static final Map<String, Float> WORLD_SPEED_MAP = new HashMap<>();

    private void initWorldSpeedMap() {
        WORLD_SPEED_MAP.put("c646473983afec09", DEFAULT_WORLD_SPEED); // W16
    }

    /**
     * Gets the genus (world identifier) for this game instance
     */
    public String getGenus() {
        return genus;
    }

    /**
     * Удаляет маркер животного из БД (вызывается при любом удалении метки animal_ — с карты или из окна поиска).
     */
    public void deleteAnimalMarkerFromDb(long gobId) {
        String profile = getGenus();
        if (profile == null || profile.isEmpty()) return;
        nurgling.db.service.AnimalMarkerService svc = NCore.databaseManager != null ? NCore.databaseManager.getAnimalMarkerService() : null;
        if (svc != null && svc.isAvailable()) {
            svc.deleteByGobId(profile, gobId);
        }
    }
    
    /**
     * Gets equipment proxy slots from config and converts to Slots array.
     * Handles both Integer and Long types that may come from JSON parsing.
     */
    public static NEquipory.Slots[] getEquipProxySlotsFromConfig() {
        Object configValue = NConfig.get(NConfig.Key.equipProxySlots);

        // Convert config value to list of integers, handling various types
        ArrayList<Integer> slotIndices = new ArrayList<>();
        if (configValue instanceof ArrayList) {
            for (Object item : (ArrayList<?>) configValue) {
                if (item instanceof Number) {
                    slotIndices.add(((Number) item).intValue());
                }
            }
        }

        if (slotIndices.isEmpty()) {
            // Return default slots if config is empty
            return new NEquipory.Slots[]{NEquipory.Slots.HAND_LEFT, NEquipory.Slots.HAND_RIGHT, NEquipory.Slots.BELT};
        }

        ArrayList<NEquipory.Slots> slots = new ArrayList<>();
        for (Integer idx : slotIndices) {
            for (NEquipory.Slots slot : NEquipory.Slots.values()) {
                if (slot.idx == idx) {
                    slots.add(slot);
                    break;
                }
            }
        }
        return slots.toArray(new NEquipory.Slots[0]);
    }
    
    public NGameUI(String chrid, long plid, String genus, NUI nui)
    {
        super(chrid, plid, genus, nui);
        foragePickupMarker = new ForagePickupMarker(this);

        // Initialize world-specific profile
        nurgling.profiles.ConfigFactory.initializeProfile(genus);

        // Initialize local allowed zones manager (for local hide control)
        nurgling.areas.AllowedZonesManager.getInstance().initialize(genus);
        add(new NDraggableWidget(botsMenu = new NBotsMenu(), "botsmenu", botsMenu.sz.add(NDraggableWidget.delta)));

        // Initialize world speed
        initWorldSpeedMap();
        Float actualWorldSpeed = WORLD_SPEED_MAP.get(genus);
        worldSpeed = Objects.requireNonNullElse(actualWorldSpeed, DEFAULT_WORLD_SPEED);
    }
    
    /**
     * Инициализация критичных виджетов, которые нужны сразу после подключения
     * (например, questinfo - сервер отправляет квесты сразу)
     */
    private void initCriticalWidgets() {
        // questinfo нужен сразу, так как сервер отправляет квесты при подключении
        NResizableWidget questwdg = new NResizableWidget(
            questinfo = new NQuestInfo(), "quests", questinfo.sz.add(NDraggableWidget.delta));
        questwdg.minSize = new Coord(200, 110);
        add(questwdg);
    }
    
    /**
     * Инициализация некритичных тяжелых виджетов (можно загружать асинхронно)
     */
    private void initNonCriticalHeavyWidgets() {
        itemsForSearch = new NSearchItem();
        // Replace Cal with NCal to keep calendar customizations in nurgling package
        Widget oldCalendarWidget = null;
        for(Widget wdg : children()) {
            if(wdg instanceof NDraggableWidget) {
                // Check if this draggable widget contains the Cal
                for(Widget child : wdg.children()) {
                    if(child instanceof Cal && !(child instanceof NCal)) {
                        oldCalendarWidget = wdg;
                        break;
                    }
                }
                if(oldCalendarWidget != null) break;
            }
        }
        if(oldCalendarWidget != null) {
            Coord calPos = oldCalendarWidget.c;
            oldCalendarWidget.destroy();
            calendar = new NCal();
            add(new NDraggableWidget(calendar, "Calendar", NCal.COMPACT_SZ), calPos);
        }
        add(new NDraggableWidget(alarmWdg = new NAlarmWdg(),"alarm",NStyle.alarm[0].sz().add(NDraggableWidget.delta)));
        // Starvation alert widget - monitors energy and shows warnings
        add(starvationAlertWidget = new StarvationAlertWidget());
        // Auto-logout widget - logs out when energy is critically low
        add(autoLogoutWidget = new AutoLogoutWidget());
        nep = new NEquipProxy(getEquipProxySlotsFromConfig());
        add(new NDraggableWidget(nep, "EquipProxy", nep.sz.add(NDraggableWidget.delta)));
        add(new NDraggableWidget(nbp = new NBeltProxy(), "BeltProxy", UI.scale(825, 55)));
        for(int i = 0; i<(Integer)NConfig.get(NConfig.Key.numbelts); i++)
        {
            String name = "belt" + String.valueOf(i);
            NDraggableWidget belt = add(new NDraggableWidget(new NToolBelt(name, i * 12, 4, 12), name, UI.scale(new Coord(500, 56))));
            belt.setFlipped(true);
        }

        add(new NDraggableWidget(recentActionsPanel = new NRecentActionsPanel(), "recentactions", recentActionsPanel.sz.add(NDraggableWidget.delta)));
        // Add drink meter widget to show water/tea capacity (uses IMeter.fsz to match other meters)
        drinkMeter = new DrinkMeter();
        add(new NDraggableWidget(drinkMeter, "drinkmeter", IMeter.fsz));
        add(guiinfo = new NGUIInfo(),new Coord(sz.x/2 - NGUIInfo.xs/2,sz.y/5 ));
        if(!(Boolean) NConfig.get(NConfig.Key.show_drag_menu))
            guiinfo.hide();
        // Position NEditAreaName relative to areas widget center
        add(nean = new NEditAreaName(), new Coord(sz.x/2 - nean.sz.x/2, sz.y/2 - nean.sz.y/2));
        nean.hide();
        // Position NImportStrategyDialog relative to areas widget center
        add(importDialog = new NImportStrategyDialog(), new Coord(sz.x/2 - importDialog.sz.x/2, sz.y/2 - importDialog.sz.y/2));
        importDialog.hide();
        // Position BotsInterruptWidget (observer with gears) in center of screen
        add(biw = new BotsInterruptWidget(), new Coord(sz.x/2 - biw.sz.x/2, sz.y/2 - biw.sz.y/2));
        waypointMovementService = new WaypointMovementService(this);
        pingService = new PingService(this);
        fishLocationService = new FishLocationService(this, genus);
        peerPositionService = new PeerPositionService(this);
        treeLocationService = new TreeLocationService(this, genus);
        prospectingLocationService = new ProspectingLocationService(this, genus);
        labeledMarkService = new LabeledMarkService(this, genus);
        // These widgets depend on areas which is created in GameUI constructor
        // Position NEditFolderName relative to areas widget
        add(nefn = new NEditFolderName(areas), new Coord(sz.x/2 - nefn.sz.x/2, sz.y/2 - nefn.sz.y/2));
        nefn.hide();
        // Position Specialisation relative to areas widget center
        add(spec = new Specialisation(), new Coord(sz.x/2 - spec.sz.x/2, sz.y/2 - spec.sz.y/2));
        spec.hide();

        // Heavy service widgets
        add(localizedResourceTimerDialog = new LocalizedResourceTimerDialog(), new Coord(200, 200));
        localizedResourceTimerService = new LocalizedResourceTimerService(this, genus);
        add(localizedResourceTimersWindow = new LocalizedResourceTimersWindow(localizedResourceTimerService), new Coord(100, 100));
        
        // Database debug overlay - shows in top-right corner
        add(dbStatsOverlay = new DbStatsOverlay(), new Coord(sz.x - 290, 10));
        dbStatsOverlay.hide(); // Hidden by default, toggle with F11 or settings

        // Start animal marker sync from Postgres (labeledMarkService уже создан здесь; в attached() он ещё null)
        if (animalMarkerSyncService == null && labeledMarkService != null && genus != null) {
            animalMarkerSyncService = new AnimalMarkerSyncService(this);
            animalMarkerSyncService.start();
        }

        // Start local timer sync from Postgres (every 5 minutes)
        if (localTimerSyncService == null && localizedResourceTimerService != null && genus != null) {
            localTimerSyncService = new LocalTimerSyncService(this);
            localTimerSyncService.start();
        }

        // Simple routes widget (initialized in attached() after SimpleRouteManager is ready)
        // Will be added in attached() method

        // Profile-aware components are now initialized in attached() before super.attached()

        // Load external plugins and let them attach to this session's UI.
        nurgling.plugins.NPluginManager.onGameUIReady(this);
    }

    @Override
    protected void attached() {
        // Initialize profile-aware components BEFORE calling super.attached()
        // This ensures RouteGraphManager is available when RoutesWidget is created
        if (map instanceof NMapView) {
            ((NMapView) map).initializeWithGenus(genus);
        }

        // Update NCore to use profile-aware config (now that UI and core are available)
        if (ui != null && ui.core != null) {
            ui.core.updateConfigForProfile(genus);
        }

        // Reload explored area with profile-specific data (async to avoid blocking startup)
        if (mmap != null && mmap instanceof NCornerMiniMap) {
            NCornerMiniMap nmmap = (NCornerMiniMap) mmap;
            if (nmmap.exploredArea != null) {
                nmmap.exploredArea.reloadFromFileAsync();
            }
        }

        // Load areas now that genus is available - делаем асинхронно для ускорения старта
        if (map != null && map.glob != null && map.glob.map != null) {
            // Загружаем зоны асинхронно, чтобы не блокировать старт
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        map.glob.map.loadAreasIfNeeded();
                        // ВАЖНО: Создаем визуальное отображение для загруженных зон
                        if (map instanceof NMapView) {
                            ((NMapView) map).initDummys();
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to load areas asynchronously: " + e.getMessage());
                    }
                }
            }, "Areas-Loader").start();
        }

        super.attached();
        
        // Инициализируем критичные виджеты синхронно (нужны сразу после подключения)
        initCriticalWidgets();
        
        // Остальные тяжелые виджеты инициализируем асинхронно
        heavyWidgetsThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    initNonCriticalHeavyWidgets();
                } catch (InterruptedException ignored) {
                } catch (Exception e) {
                    System.err.println("Failed to initialize heavy widgets: " + e.getMessage());
                }
            }
        }, "HeavyWidgets-Init");
        heavyWidgetsThread.setDaemon(true);
        heavyWidgetsThread.start();
        
        // Initialize SimpleRoutesWidget when NMapView is available.
        // Some branches don't expose simpleRouteManager field directly.
        if (map instanceof NMapView) {
            if (simpleRoutesWidget == null) {
                simpleRoutesWidget = new SimpleRoutesWidget();
                add(simpleRoutesWidget, new Coord(100, 200));
                simpleRoutesWidget.hide(); // Скрываем виджет при создании
            }
        }

        // Start animal marker sync from Postgres (every 30s into LabeledMarkService)
        if (animalMarkerSyncService == null && labeledMarkService != null && genus != null) {
            animalMarkerSyncService = new AnimalMarkerSyncService(this);
            animalMarkerSyncService.start();
        }

        // Start local timer sync from Postgres (every 5 minutes)
        if (localTimerSyncService == null && localizedResourceTimerService != null && genus != null) {
            localTimerSyncService = new LocalTimerSyncService(this);
            localTimerSyncService.start();
        }
        
        // Автозапуск макроса маркеров животных если включён в настройках
        if (nurgling.widgets.nsettings.AnimalMarkersSettings.isMarkerEnabled()) {
            // Запускаем с небольшой задержкой чтобы UI полностью загрузился
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) { return; }
                if (NGameUI.this.ui != null && !animalMarkerMacroRunning) {
                    startAnimalMarkerMacro();
                }
            }, "AnimalMarkerMacro-AutoStart").start();
        }
        
    }

    private void initializeInventoryVisibility() {
        if (!LoginPreferences.shouldOpenInventory(NConfig.get(NConfig.Key.openInventoryOnLogin))) {
            return;
        }
        Window inventoryWindow = maininv != null ? maininv.getparent(Window.class) : null;
        if (inventoryWindow == null) {
            inventoryWindow = getWindow(L10n.get("inventory.window_title"));
        }
        if (inventoryWindow != null && !inventoryWindow.visible()) {
            togglewnd(inventoryWindow);
        }
    }

    @Override
    public void togglewnd(haven.Window wnd) {
        super.togglewnd(wnd);
        // При открытии карты — подгрузить маркеры животных из БД (на случай перезахода)
        if (wnd != null && wnd == mapfile && wnd.visible() && animalMarkerSyncService != null) {
            animalMarkerSyncService.syncNow();
        }
    }

    @Override
    public void dispose() {
        if (heavyWidgetsThread != null) {
            heavyWidgetsThread.interrupt();
            heavyWidgetsThread = null;
        }
        if(localizedResourceTimerService != null)
            localizedResourceTimerService.dispose();
        if(localTimerSyncService != null)
            localTimerSyncService.stop();
        if(animalMarkerSyncService != null)
            animalMarkerSyncService.stop();
        if(fishLocationService != null)
            fishLocationService.dispose();
        if (animalMarkerWorker != null) {
            animalMarkerWorker.shutdownNow();
            animalMarkerWorker = null;
        }
        animalMarkerMacroRunning = false;
        if (animalMarkerMacroThread != null) {
            animalMarkerMacroThread.interrupt();
            animalMarkerMacroThread = null;
        }
        if(labeledMarkService != null)
            labeledMarkService.dispose();
        foragePickupMarker.dispose();
        /* Take this character's published position out on the way down. It would age out on its own
         * within the minute, but that minute is a minute of showing someone who has left, and
         * "logged out" and "standing still" are exactly the two states these markers exist to tell
         * apart, so it is worth one delete to make the marker go the instant the player does. */
        if(peerPositionService != null && nurgling.NCore.databaseManager != null
           && nurgling.NCore.databaseManager.getPeerPositionService() != null) {
            String profile = getGenus();
            nurgling.NCore.databaseManager.getPeerPositionService()
                .withdraw((profile == null || profile.isEmpty()) ? "global" : profile, chrid);
            peerPositionService.clear();
        }
        if(nurgling.NUtils.getUI().core!=null)
            NUtils.getUI().core.dispose();
        if(map instanceof NMapView) {
            NMapView nmapView = (NMapView) map;
            if(nmapView.getChunkNavManager() != null)
                nmapView.getChunkNavManager().shutdown();
        }
        nurgling.tools.ExploredArea.resetExecutor();
        nurgling.tools.CheckGridsState.resetExecutor();
        nurgling.actions.bots.MasterMiner.resetExecutors();
        nurgling.overlays.map.MinimapExploredAreaRenderer.clearCaches();
        gobIdToKinName.clear();
        nurgling.NCore.clearSessionCaches();
        synchronized(haven.res.ui.croster.RosterWindow.rosters) {
            haven.res.ui.croster.RosterWindow.rosters.clear();
        }
        try {
            if (mapfile != null && mapfile.view != null && mapfile.view.file != null)
                mapfile.view.file.dispose();
        } catch (Exception e) { System.err.println("[NGameUI] MapFile dispose error: " + e.getMessage()); }
        try {
            if (map != null && map.glob != null && map.glob.map != null)
                map.glob.map.trimall();
        } catch (Exception e) { System.err.println("[NGameUI] MCache trimall error: " + e.getMessage()); }
        try { haven.Resource.remote().clearCache(); } catch (Exception ignore) {}
        super.dispose();
        System.gc();
    }

    public int getMaxBase(){
        return chrwdg.battr.attrs.stream().max(new Comparator<BAttrWnd.Attr>() {
                    @Override
                    public int compare(BAttrWnd.Attr o1, BAttrWnd.Attr o2) {
                        return Integer.compare(o1.attr.base,o2.attr.base);
                    }
                }).get().attr.base;
    }

    public NCharacterInfo getCharInfo() {
        return ((NUI)ui).sessInfo.characterInfo;
    }

    public Window getWindow ( String cap ) {
        for ( Widget w = lchild ; w != null ; w = w.prev ) {
            if ( w instanceof Window ) {
                Window wnd = ( Window ) w;
                if ( wnd.cap != null && wnd.cap.equals(cap)) {
                    return wnd;
                }
            }
        }
        return null;
    }

    public int getWindowsNum(String name) {
        int count = 0;
        for ( Widget w = lchild ; w != null ; w = w.prev ) {
            if ( w instanceof Window ) {
                Window wnd = ( Window ) w;
                if ( wnd.cap != null && wnd.cap.equals(name)) {
                    count++;
                }
            }
        }
        return count;
    }

    public ArrayList<Window> getWindows(String name) {
        ArrayList<Window> windows = new ArrayList<>();
        for ( Widget w = lchild ; w != null ; w = w.prev ) {
            if ( w instanceof Window ) {
                Window wnd = ( Window ) w;
                if ( wnd.cap != null && wnd.cap.equals(name)) {
                    windows.add(wnd);
                }
            }
        }
        return windows;
    }

    public Window getWindowWithButton ( String cap, String button ) {
        for ( Widget w = lchild ; w != null ; w = w.prev ) {
            if ( w instanceof Window ) {
                Window wnd = ( Window ) w;
                if ( wnd.cap != null && wnd.cap.equals(cap)) {
                    for(Widget w2 = wnd.lchild ; w2 !=null ; w2= w2.prev )
                    {
                        if ( w2 instanceof Button ) {
                            Button b = ((Button)w2);
                            if(b.text!=null && b.text.text.equals(button)){
                                return (Window)w;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public boolean isWindowExist ( Window twnd )
    {
        for (Widget w = lchild; w != null; w = w.prev)
        {
            if (w instanceof Window)
            {
                Window wnd = (Window) w;
                if (wnd.equals(twnd))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public double getTableMod() {
        double table_mod = 1;
        Window table = getTableWindow();
        if(table!=null)
        {
            for (Label text : table.children(Label.class)) {
                if (nurgling.widgets.TableInventoryExtension.isFoodEventLabel(text.text())) {
                    table_mod = table_mod + Double.parseDouble(text.text().substring(text.text().indexOf(":") + 1, text.text().indexOf("%"))) / 100.;
                    break;
                }
            }
        }
        return table_mod;
    }

    private Window getTableWindow() {
        for (Widget w = lchild; w != null; w = w.prev) {
            if (!(w instanceof Window)) continue;
            Window wnd = (Window) w;
            if (!nurgling.widgets.TableInventoryExtension.isTableWindowCap(wnd.cap)) continue;
            for (Button b : wnd.children(Button.class)) {
                if (b.text != null && nurgling.widgets.TableInventoryExtension.isFeastText(b.text.text))
                    return wnd;
            }
        }
        return getWindowWithButton("Table", "Feast!");
    }

    public double getRealmMod()
    {
        double realmBuff = 0;

        for (Widget wdg1 = child; wdg1 != null; wdg1 = wdg1.next)
        {
            if (wdg1 instanceof Bufflist)
            {
                for (Widget pbuff = wdg1.child; pbuff != null; pbuff = pbuff.next)
                {
                    if (pbuff instanceof RealmBuff)
                    {
                        if (((Buff) pbuff).info!=null)
                        {
                            ArrayList<ItemInfo> realm = new ArrayList<>(((Buff) pbuff).info);
                            for (Object data : realm)
                            {
                                if (data instanceof ItemInfo.AdHoc)
                                {
                                    ItemInfo.AdHoc ah = ((ItemInfo.AdHoc) data);
                                    if (NParser.checkName(ah.str.text, new NAlias("Food event")))
                                    {
                                        realmBuff = realmBuff + Double.parseDouble(ah.str.text.substring(ah.str.text.indexOf("+") + 1, ah.str.text.indexOf("%"))) / 100.;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return realmBuff;
    }

    /**
     * Called when swimming toggle state changes (event-driven)
     */
    public void onSwimmingStateChanged(boolean isSwimmingEnabled) {
        if (isSwimmingEnabled && swimmingBuff == null) {
            // Create and add swimming status buff
            swimmingBuff = new SwimmingStatusBuff();
            buffs.addchild(swimmingBuff);
        } else if (!isSwimmingEnabled && swimmingBuff != null) {
            // Remove swimming status buff
            swimmingBuff.reqdestroy();
            swimmingBuff = null;
        }
    }

    /**
     * Called when tracking toggle state changes (event-driven)
     */
    public void onTrackingStateChanged(boolean isTrackingEnabled) {
        if (isTrackingEnabled && trackingBuff == null) {
            // Create and add tracking status buff
            trackingBuff = new TrackingStatusBuff();
            buffs.addchild(trackingBuff);
        } else if (!isTrackingEnabled && trackingBuff != null) {
            // Remove tracking status buff
            trackingBuff.reqdestroy();
            trackingBuff = null;
        }
    }

    /**
     * Called when crime toggle state changes (event-driven)
     */
    public void onCrimeStateChanged(boolean isCrimeEnabled) {
        if (isCrimeEnabled && crimeBuff == null) {
            // Create and add crime status buff
            crimeBuff = new CrimeStatusBuff();
            buffs.addchild(crimeBuff);
        } else if (!isCrimeEnabled && crimeBuff != null) {
            // Remove crime status buff
            crimeBuff.reqdestroy();
            crimeBuff = null;
        }
    }

    /**
     * Called when allow visiting toggle state changes (event-driven)
     */
    public void onAllowVisitingStateChanged(boolean isAllowVisitingEnabled) {
        if (isAllowVisitingEnabled && allowVisitingBuff == null) {
            // Create and add allow visiting status buff
            allowVisitingBuff = new AllowVisitingStatusBuff();
            buffs.addchild(allowVisitingBuff);
        } else if (!isAllowVisitingEnabled && allowVisitingBuff != null) {
            // Remove allow visiting status buff
            allowVisitingBuff.reqdestroy();
            allowVisitingBuff = null;
        }
    }

    @Override
    public void addchild(Widget child, Object... args)
    {
        String place = ((String) args[0]).intern();
        if (place == "craft") {
            if (craftwnd == null) {
                NCraftWindow cwnd = new NCraftWindow();
                cwnd.posmem("craft");
                craftwnd = add(cwnd, cwnd.restorepos(new Coord(400, 200)));
                fitwdg(craftwnd);
            }
            craftwnd.add(child);
            craftwnd.pack();
            fitwdg(craftwnd);
            craftwnd.raise();
            craftwnd.show();
        }
        else
        {
            super.addchild(child, args);

            // Apply preferred movement speed when Speedget widget is loaded
            if (place != null && place.equals("meter") && child instanceof haven.Speedget) {
                applyUserPreferredSpeed();
                new Thread(() -> {
                    try {
                        Thread.sleep(400);
                        applyUserPreferredSpeed();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "PreferredSpeed-Retry").start();
            }

            // Add fishing extension if this is the "This is bait" window
            if (child instanceof Window) {
                Window wnd = (Window) child;
                if ("This is bait".equals(wnd.cap)) {
                    FishingWindowExtension.addSaveFishButton(wnd, this);
                }
            }

            if (maininv != null && !((NInventory) maininv).mainInvInstalled)
            {
                ((NInventory) maininv).installMainInv();
            }

            // Check if this is the inventory being added
            if (place != null && place.equals("inv")) {
                // Inventory window was just created, now check the setting
                initializeInventoryVisibility();
            }
        }
    }

    public void tickmsg(String msg) {
        msg("TICK#" + NUtils.getTickId() + " MSG: " + msg);
    }

    public NInventory getInventory ( String name ) {
        Window spwnd = getWindow ( name );
        if(spwnd == null){
            return null;
        }
        for ( Widget sp = spwnd.lchild ; sp != null ; sp = sp.prev ) {
            if ( sp instanceof Inventory ) {
                return ( ( NInventory ) sp );
            }
        }
        return null;
    }

    public NInventory getInventory () {
        return (NInventory) maininv;
    }

    public NISBox getStockpile () {
        Window spwnd = getWindow ( "Stockpile" );
        if(spwnd == null){
            return null;
        }
        for ( Widget sp = spwnd.lchild ; sp != null ; sp = sp.prev ) {
            if ( sp instanceof NISBox ) {
                return ( ( NISBox ) sp );
            }
        }
        return null;
    }

    private boolean layoutHintDone = false;
    private double layoutHintAge = 0;
    private boolean layoutPickerDone = false;

    /**
     * A non-empty placement list means this player has already arranged their
     * HUD, so they must not be offered a preset that would throw it away.
     */
    private static boolean hasCustomLayout()
    {
        Object v = NConfig.get(NConfig.Key.dragprop);
        return (v instanceof Collection) && !((Collection<?>)v).isEmpty();
    }

    @Override
    public void tick(double dt)
    {
        super.tick(dt);
        if(!layoutPickerDone && sz.x > 0)
        {
            layoutPickerDone = true;
            if(!Boolean.TRUE.equals(NConfig.get(NConfig.Key.layoutPresetChosen)) && !hasCustomLayout())
            {
                NLayoutPicker picker = new NLayoutPicker();
                add(picker, new Coord(Math.max(0, (sz.x - picker.sz.x) / 2), sz.y / 6));
                /* The picker explains the same thing far better than a line in
                 * the message log, so don't say it twice. */
                layoutHintDone = true;
                NConfig.set(NConfig.Key.layoutHintShown, true);
            }
        }
        if(!layoutHintDone)
        {
            Object shown = NConfig.get(NConfig.Key.layoutHintShown);
            if(Boolean.TRUE.equals(shown))
            {
                layoutHintDone = true;
            }
            else
            {
                /* Wait for the login chatter to settle, otherwise the hint is
                 * pushed out of the message log before it can be read. */
                layoutHintAge += dt;
                if(layoutHintAge > 5.0)
                {
                    layoutHintDone = true;
                    NConfig.set(NConfig.Key.layoutHintShown, true);
                    msg(nurgling.i18n.L10n.get("hint.layout"), new java.awt.Color(190, 220, 255));
                }
            }
        }
    }

    @Override
    public void resize(Coord sz)
    {
        super.resize(sz);
        if(guiinfo != null)
            guiinfo.move(new Coord(sz.x / 2 - NGUIInfo.xs / 2, sz.y / 5));
        if(areas != null)
            areas.move(new Coord(sz.x / 2 - NGUIInfo.xs / 2, sz.y / 5));
        if(cookBook != null)
            cookBook.move(new Coord(sz.x / 2 - NGUIInfo.xs / 2, sz.y / 5));
        if(storageItemsWidget != null)
            storageItemsWidget.move(new Coord(sz.x / 2 - NGUIInfo.xs / 2, sz.y / 5));
        if(nean != null)
            nean.move(new Coord(sz.x / 2 - NGUIInfo.xs / 2, sz.y / 7));
        if(spec != null)
            spec.move(new Coord(sz.x / 2 - NGUIInfo.xs / 2, sz.y / 7));
        if(biw != null)
            biw.move(new Coord(sz.x / 2 - biw.sz.x / 2, sz.y / 2 - biw.sz.y / 2));
        if(blueprintWidget != null)
            blueprintWidget.move(new Coord(sz.x / 2 - NGUIInfo.xs / 2, sz.y / 5));
    }

    public List<IMeter.Meter> getmeters (String name ) {
        synchronized (meters) {
            try {
                for (Widget meter : new ArrayList<>(meters)) {
                    if (meter instanceof IMeter) {
                        IMeter im = (IMeter) meter;
                        Resource res = im.bg.get();
                        if (res != null) {
                            if (res.basename().equals(name)) {
                                return im.meters;
                            }
                        }
                    }
                }
            } catch (IndexOutOfBoundsException | ConcurrentModificationException e) {
                // Handle concurrent modification or index errors gracefully
                return null;
            }
        }
        return null;
    }

    public IMeter.Meter getmeter (
            String name,
            int midx
    ) {
        List<IMeter.Meter> meters = getmeters ( name );
        if ( meters != null && midx < meters.size () ) {
            return meters.get ( midx );
        }
        return null;
    }

    public double getBarrelContent()
    {
        return getBarrelContent(new NAlias(""));
    }

    public double getBarrelContent(NAlias content){
        Window spwnd = getWindow ( "Barrel" );
        if(spwnd!=null) {
            for (Widget sp = spwnd.lchild; sp != null; sp = sp.prev) {
                /// Выбираем внутренний контейнер
                if (sp instanceof RelCont) {
                    for(Pair<Widget, Supplier<Coord>> pair:((RelCont) sp).childpos) {
                        if (pair.a.getClass().getName().contains("TipLabel")) {
                            try {
                                ///TODO
                                for (ItemInfo inf : (Collection<ItemInfo>) (pair.a.getClass().getField("info").get(pair.a))) {
                                    if (inf instanceof ItemInfo.Name) {
                                        String name = ((ItemInfo.Name) inf).str.text;
                                        if (NParser.checkName(name.toLowerCase(), content))
                                            return Double.parseDouble(name.substring(0, name.indexOf(' ')));
                                        // Handle seed name format difference: "Flax Seeds" vs "1234 seeds of Flax"
                                        if (name.toLowerCase().contains(" seeds of ")) {
                                            int ofIndex = name.toLowerCase().indexOf(" seeds of ");
                                            String seedType = name.substring(ofIndex + 10).trim(); // Extract "Flax" from "1234 seeds of Flax"
                                            String inventoryFormat = seedType + " seeds"; // Convert to "Flax seeds"
                                            if (NParser.checkName(inventoryFormat.toLowerCase(), content))
                                                return Double.parseDouble(name.substring(0, name.indexOf(' ')));
                                        }
                                    } else if (inf instanceof ItemInfo.AdHoc) {
                                        if (NParser.checkName(((ItemInfo.AdHoc) inf).str.text, "Empty")) {
                                            return 0;
                                        }
                                    }
                                }
                            } catch (NoSuchFieldException | IllegalAccessException e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
        }
        return -1;
    }

    public double findBarrelContent(ArrayList<Window> windows, NAlias content){
        for (Window spwnd: windows) {
            if (spwnd != null) {
                for (Widget sp = spwnd.lchild; sp != null; sp = sp.prev) {
                    if (sp instanceof RelCont) {
                        for (Pair<Widget, Supplier<Coord>> pair : ((RelCont) sp).childpos) {
                            if (pair.a.getClass().getName().contains("TipLabel")) {
                                try {
                                    for (ItemInfo inf : (Collection<ItemInfo>) (pair.a.getClass().getField("info").get(pair.a))) {
                                        if (inf instanceof ItemInfo.Name) {
                                            String name = ((ItemInfo.Name) inf).str.text;
                                            if (NParser.checkName(name.toLowerCase(), content))
                                                return Double.parseDouble(name.substring(0, name.indexOf(' ')));
                                            // Handle seed name format difference: "Flax Seeds" vs "1234 seeds of Flax"
                                            if (name.toLowerCase().contains(" seeds of ")) {
                                                int ofIndex = name.toLowerCase().indexOf(" seeds of ");
                                                String seedType = name.substring(ofIndex + 10).trim(); // Extract "Flax" from "1234 seeds of Flax"
                                                String inventoryFormat = seedType + " seeds"; // Convert to "Flax seeds"
                                                if (NParser.checkName(inventoryFormat.toLowerCase(), content))
                                                    return Double.parseDouble(name.substring(0, name.indexOf(' ')));
                                            }
                                        }
                                    }
                                } catch (NoSuchFieldException | IllegalAccessException e) {
                                    e.printStackTrace();
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                }
            }
        }
        return -1;
    }


    public void msgToDiscord(NDiscordNotification settings, String message)
    {
        if (message != null && !message.isEmpty())
        {
            if (settings != null)
            {
                DiscordHookObject webhook = new DiscordHookObject(settings.webhookUrl);

                webhook.setContent(message);

                webhook.setAvatarUrl(settings.webhookIcon);
                webhook.setUsername(settings.webhookUsername);
                webhook.addEmbed(new nurgling.notifications.DiscordHookObject.EmbedObject()
                        .setColor(java.awt.Color.RED)
                        .setThumbnail(settings.webhookIcon)
                        .setAuthor("Nurgling Evolution", NUpdateFeed.SOURCE_REPO_URL, "https://github.com/Lanfir7.png")
                        .setUrl(NUpdateFeed.SOURCE_REPO_URL));
                new Thread(webhook).start();

            }
            else
            {
                error("No discord wrapper settings");
            }
        }
    }

    public void toggleol(String tag, boolean a) {
        if(map != null) {
            if(a)
                map.enol(tag);
            else
                map.disol(tag);
        }
    }



    public class NToolBelt extends Belt implements KeyBinding.Bindable{

        public static final int GAP = 10;
        public static final int PAD = 2;
        public static final int BTNSZ = 17;
        public final Coord INVSZ = invsq.sz();

        final int group;
        final int start;
        final int size;
        final String name;
        private boolean vertical = false;
        ArrayList<NKeyBinding> beltkeys = new ArrayList<>();
        public NToolBelt(String name, int start, int group, int size) {
            super( new Coord(0,0) );
            this.start = start;
            this.group = group;
            this.size = size;
            this.name = name;
            sz = beltc(size - 1).add(INVSZ);
            NToolBeltProp prop = NToolBeltProp.get(name);
            for(KeyBinding kb: prop.getKb())
            {
                beltkeys.add(new NKeyBinding(kb));
            }
        }

        @Override
        public void flip(boolean val) {
            vertical = val;
            resize();
        }

        private void resize() {
            sz = beltc(size - 1).add(INVSZ);
        }

        @Override
        public int beltslot(Coord c) {
            for (int i = 0; i < size; i++) {
                if(c.isect(beltc(i), invsq.sz())) {
                    return slot(i);
                }
            }
            return (-1);
        }

        private Object curtt = null;
        private Object curitem = null;
        private boolean curttl = false;
        private double hoverstart;

        @Override
        public Object tooltip(Coord c, Widget prev) {
            int slot = beltslot(c);
            if(slot < 0)
                return super.tooltip(c, prev);
            Object item = belt(slot);
            if(item == null)
                return super.tooltip(c, prev);
            double now = Utils.rtime();
            if(prev != this)
                hoverstart = now;
            boolean ttl = (now - hoverstart) > 0.5;
            if((item != curitem) || (ttl != curttl)) {
                curtt = NToolBeltTip.from(item, ttl);
                curitem = item;
                curttl = ttl;
            }
            return curtt;
        }

        @Override
        public KeyBinding getbinding(Coord cc) {
            int slot = beltslot(cc);
            if(slot!=-1)
                return beltkeys.get(slot - start).kb;
            return null;
        }

        @Override
        public void draw(GOut g) {
            for (int i = 0; i < size; i++) {
                Coord c = beltc(i);
                int slot = slot(i);
                g.image(invsq, c);
                try {
                    Object item = belt(slot);
                    if (item != null) {
                        if(item instanceof BeltSlot)
                            ((BeltSlot)item).draw(g.reclip(c.add(1, 1), invsq.sz().sub(2, 2)));
                        else if (item instanceof NBotsMenu.NButton)
                            ((NBotsMenu.NButton)item).btn.draw(g.reclip(c.add(1, 1), invsq.sz().sub(2, 2)));
                        else if (item instanceof NScenarioButton)
                            ((NScenarioButton)item).draw(g.reclip(c.add(1, 1), invsq.sz().sub(2, 2)));
                        else if (item instanceof nurgling.widgets.NEquipmentPresetButton)
                            ((nurgling.widgets.NEquipmentPresetButton)item).draw(g.reclip(c.add(1, 1), invsq.sz().sub(2, 2)));
                    }
                } catch (Loading ignored) {
                }
                if (beltkeys.get(i).tex != null) {
                    g.aimage(beltkeys.get(i).tex, c.add(INVSZ.sub(2, 0)), 1, 1);
                }
            }
            super.draw(g);
        }

        @Override
        public void keyact(int slot) {
            if(map != null) {
                NToolBeltProp prop = NToolBeltProp.get(name);
                String path;
                if((path = prop.custom.get(slot))!=null) {
                    if(path.startsWith("scenario:")) {
                        // Handle scenario button execution
                        String scenarioName = path.substring("scenario:".length());
                        ui.core.scenarioManager.executeScenarioByName(scenarioName, ui.gui);
                        return;
                    } else if(path.startsWith("equippreset:")) {
                        // Handle equipment preset button execution
                        String presetId = path.substring("equippreset:".length());
                        ui.core.equipmentPresetManager.executePreset(presetId);
                        return;
                    } else {
                        // Handle regular bot button
                        NBotsMenu.NButton btn = NUtils.getGameUI().botsMenu.find(path);
                        if(btn!=null) {
                            btn.btn.click();
                            return;
                        }
                    }
                }
                super.keyact(slot);
            }
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            NToolBeltProp prop = NToolBeltProp.get(name);
            int slot = beltslot(ev.c);
            if(ev.b == 3)
            {
                if(prop.custom.get(slot)!=null) {
                    prop.custom.remove(slot);
                    NToolBeltProp.set(name, prop);
                    return true;
                }
            }
            else if (ev.b == 1)
            {
                String path;
                if((path = prop.custom.get(slot))!=null) {
                    if(path.startsWith("scenario:")) {
                        // Handle scenario button execution
                        String scenarioName = path.substring("scenario:".length());
                        ui.core.scenarioManager.executeScenarioByName(scenarioName, ui.gui);
                        return true;
                    } else if(path.startsWith("equippreset:")) {
                        // Handle equipment preset button execution
                        String presetId = path.substring("equippreset:".length());
                        ui.core.equipmentPresetManager.executePreset(presetId);
                        return true;
                    } else {
                        // Handle regular bot button
                        NBotsMenu.NButton btn = NUtils.getGameUI().botsMenu.find(path);
                        if(btn!=null) {
                            btn.btn.click();
                            return true;
                        }
                    }
                }
            }
            return super.mousedown(ev);
        }


        private Object belt(int slot) {
            if(slot < 0) {return null;}
            String path;
            if((path = NToolBeltProp.get(name).custom.get(slot) )== null) {
                GameUI.BeltSlot res = null;
                if (ui != null && belt[slot] != null)
                    res = belt[slot];
                return res;
            }
            else
            {
                Object customObj = null;
                if(path.startsWith("scenario:")) {
                    String scenarioName = path.substring("scenario:".length());
                    for(nurgling.scenarios.Scenario scenario : ui.core.scenarioManager.getScenarios().values()) {
                        if(scenario.getName().equals(scenarioName)) {
                            customObj = new NScenarioButton(scenario);
                            break;
                        }
                    }
                } else if(path.startsWith("equippreset:")) {
                    String presetId = path.substring("equippreset:".length());
                    nurgling.equipment.EquipmentPreset preset = ui.core.equipmentPresetManager.getPreset(presetId);
                    if(preset != null) {
                        customObj = new nurgling.widgets.NEquipmentPresetButton(preset);
                    }
                } else {
                    customObj = botsMenu.find(path);
                }

                // Fallback to account hotbelt slot if custom mapping is stale or missing.
                if (customObj != null) {
                    return customObj;
                }

                GameUI.BeltSlot res = null;
                if (ui != null && belt[slot] != null)
                    res = belt[slot];
                return res;
            }
        }

        private int slot(int i) {return i + start;}

        private Coord beltc(int i) {
            return vertical ?
                    new Coord(0, BTNSZ + ((INVSZ.y + PAD) * i) + (GAP * (i / group))) :
                    new Coord(BTNSZ + ((INVSZ.x + PAD) * i) + (GAP * (i /group )), 0);
        }

        @Override
        public void tick(double dt) {
            super.tick(dt);
            boolean res = false;
            for(NKeyBinding kb : beltkeys)
                res |= kb.tick();
            if(res)
            {
                NToolBeltProp.set(name,NToolBeltProp.get(name));
            }
        }

        @Override
        public boolean globtype(GlobKeyEvent ev) {
            if (!visible) {
                return false;
            }
            for (int i = 0; i < beltkeys.size(); i++) {
                if ((beltkeys.get(i).key != null && ev.code == beltkeys.get(i).key.code && ui.modflags() == beltkeys.get(i).key.modmatch)) {
                    keyact(slot(i));
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean dropthing(Coord c, Object thing) {
            boolean res = super.dropthing(c,thing);
            int slot = beltslot(c);
            if(res) {
                if(slot!=-1)
                {
                    NToolBeltProp prop = NToolBeltProp.get(name);
                    prop.custom.remove(slot);
                    NToolBeltProp.set(name,prop);
                }
                return true;
            }

            if(slot != -1) {
                if(thing instanceof NBotsMenu.NButton) {
                    NBotsMenu.NButton pag = (NBotsMenu.NButton)thing;
                    NToolBeltProp prop = NToolBeltProp.get(name);
                    prop.custom.put(slot,pag.path);
                    NToolBeltProp.set(name,prop);
                    return(true);
                } else if(thing instanceof nurgling.widgets.NScenarioButton) {
                    nurgling.widgets.NScenarioButton scenarioBtn = (nurgling.widgets.NScenarioButton)thing;
                    NToolBeltProp prop = NToolBeltProp.get(name);
                    // Use scenario name as the identifier for scenarios
                    prop.custom.put(slot, "scenario:" + scenarioBtn.getScenario().getName());
                    NToolBeltProp.set(name,prop);
                    return(true);
                } else if(thing instanceof nurgling.widgets.NEquipmentPresetButton) {
                    nurgling.widgets.NEquipmentPresetButton presetBtn = (nurgling.widgets.NEquipmentPresetButton)thing;
                    NToolBeltProp prop = NToolBeltProp.get(name);
                    // Use preset id as the identifier for equipment presets
                    prop.custom.put(slot, "equippreset:" + presetBtn.getPreset().getId());
                    NToolBeltProp.set(name,prop);
                    return(true);
                }
            }
            return(false);
        }

    }

    public static class NKeyBinding
    {
        public int modign;
        public KeyMatch key;
        KeyBinding kb;
        public NKeyBinding(KeyBinding old) {
            this.kb = old;
            this.key = effective(old);
            this.modign = old.modign;
            updateTex();
        }

        static KeyMatch effective(KeyBinding kb) {
            KeyMatch k = kb.key();
            if (k == null || k == KeyMatch.nil || k.code == KeyEvent.VK_UNDEFINED)
                return null;
            return k;
        }

        Tex tex;
        public void set(KeyMatch key) {
            kb.set(key);
            updateTex();
        }

        void updateTex()
        {
            String hotKey;
            int mode  = 0;
            if( key != null)
            {
                hotKey = KeyEvent.getKeyText(key.code);
                mode = key.modmatch;

                if (NParser.checkName(hotKey, new NAlias("Num")))
                {
                    hotKey = "N" + hotKey.substring(hotKey.indexOf("-") + 1);
                }
                if (NParser.checkName(hotKey, new NAlias("inus")))
                {
                    hotKey = "-";
                }
                else if (NParser.checkName(hotKey, new NAlias("quals")))
                {
                    hotKey = "=";
                }
                if ((mode & KeyMatch.C) != 0)
                    hotKey = "C" + hotKey;
                if ((mode & KeyMatch.S) != 0)
                    hotKey = "S" + hotKey;
                if ((mode & KeyMatch.M) != 0)
                    hotKey = "A" + hotKey;
                tex = NStyle.hotkey.render(hotKey).tex();
            } else {
                tex = null;
            }
        }

        boolean tick()
        {
            if(effective(kb)!=key || kb.modign!=modign)
            {
                key = effective(kb);
                modign = kb.modign;
                updateTex();
                return true;
            }
            return false;
        }


    }



    public boolean msg(UI.Notice msg) {
        String message = msg.message();
        
        if (message.contains("Quality")) {
            if(map.clickedGob!=null)
            {
                Matcher m = Pattern.compile("Quality:\\s*(\\d+)").matcher(message);
                if(m.find()) {  // find() вместо matches() — ищем подстроку, а не полное совпадение
                    try {
                        int quality = Integer.parseInt(m.group(1));
                        map.clickedGob.gob.addcustomol(new QualityOl(map.clickedGob.gob, quality));
                        // Обновить маркер животного на карте (качество приходит сообщением от сервера, не из sdt)
                        if (map instanceof NMapView) {
                            ((NMapView) map).applyAnimalMarkerQuality(map.clickedGob.gob, quality);
                        }
                    } catch (NumberFormatException ignored) {
                    } finally {
                        map.clickedGob = null;
                    }
                }
            }
        }
        
        // Keep pendingRefillGob updated so we have a gob when "Will refill in" arrives later (even if resource isn't in VSpec / checkLpExplorer wasn't called)
        if (map.clickedGob != null) {
            pendingRefillGob = map.clickedGob;
        }
        // Handle resource refill time messages (e.g., "Will refill in 10 hours" or "Refills in 2 weeks")
        // Use clickedGob or pendingRefillGob: "Will refill in" often arrives via system msg after tooltip, when clickedGob may already be cleared
        if (localizedResourceTimerService != null) {
            MapView.ClickedGob refillGob = map.clickedGob != null ? map.clickedGob : pendingRefillGob;
            if (refillGob != null) {
                // Match "Will refill in X units", "Refill(s) in X units" (with or without "Will")
                Matcher refillMatcher = Pattern.compile(".*(?:[Ww]ill\\s+)?[Rr]efills?\\s+in\\s+(\\d+)\\s*(week|weeks|day|days|hour|hours|minute|minutes|second|seconds|hr|hrs|min|mins|sec|secs)").matcher(message);
                if (refillMatcher.find()) {
                    try {
                        int timeValue = Integer.parseInt(refillMatcher.group(1));
                        String timeUnit = refillMatcher.group(2).toLowerCase();
                        
                        // Convert to milliseconds
                        long durationMs = 0;
                        if (timeUnit.startsWith("week")) {
                            durationMs = timeValue * 7L * 24L * 60L * 60L * 1000L;
                        } else if (timeUnit.startsWith("day")) {
                            // Convert days to hours: 1 day = 24 hours
                            durationMs = timeValue * 24L * 60L * 60L * 1000L;
                        } else if (timeUnit.startsWith("hour") || timeUnit.startsWith("hr")) {
                            durationMs = timeValue * 60L * 60L * 1000L;
                        } else if (timeUnit.startsWith("minute") || timeUnit.startsWith("min")) {
                            durationMs = timeValue * 60L * 1000L;
                        } else if (timeUnit.startsWith("second") || timeUnit.startsWith("sec")) {
                            durationMs = timeValue * 1000L;
                        }
                        
                        if (durationMs > 0) {
                            Gob gob = refillGob.gob;
                            String resName = gob.ngob != null ? gob.ngob.name : null;
                            if (resName != null && localizedResourceTimerService.isTimerSupportedResource(resName)) {
                                long segmentId = 0;
                                Coord tileCoords = null;
                                String resourceName = null;
                                MapFile.SMarker marker = findOrCreateMarker(gob);
                                if (marker != null) {
                                    segmentId = marker.seg;
                                    tileCoords = marker.tc;
                                    resourceName = marker.nm != null ? marker.nm : marker.res.name;
                                } else {
                                    // Fallback when marker fails (e.g. grid not in map file - Rock Crystal, caves): use player segment and same-grid offset
                                    segmentId = mapfile != null ? mapfile.playerSegmentId() : 0;
                                    Gob player = map.player();
                                    if (segmentId != 0 && player != null) {
                                        Coord gobTc = gob.rc.floor(tilesz);
                                        Coord playerTc = player.rc.floor(tilesz);
                                        if (gobTc.div(cmaps).equals(playerTc.div(cmaps))) {
                                            MiniMap.Location sessloc = (mapfile instanceof nurgling.widgets.NMapWnd) ? ((nurgling.widgets.NMapWnd) mapfile).view.sessloc : null;
                                            if (sessloc != null) {
                                                tileCoords = sessloc.tc.add(gobTc.sub(playerTc));
                                                try {
                                                    Resource.Tooltip tt = Resource.remote().load(resName).get().layer(Resource.Tooltip.class);
                                                    resourceName = tt != null ? tt.t : resName;
                                                } catch (Exception e) {
                                                    resourceName = resName;
                                                }
                                            } else {
                                                segmentId = 0;
                                            }
                                        } else {
                                            segmentId = 0;
                                        }
                                    }
                                }
                                if (segmentId != 0 && tileCoords != null && resourceName != null) {
                                    localizedResourceTimerService.createTimer(
                                        segmentId,
                                        tileCoords,
                                        resourceName,
                                        resName,
                                        durationMs,
                                        resourceName
                                    );
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Silently ignore errors
                    }
                    map.clickedGob = null;
                    pendingRefillGob = null;
                }
            }
        }
        
        return super.msg(msg);
    }
    
    /**
     * Find or create a map marker for the given Gob
     */
    private MapFile.SMarker findOrCreateMarker(Gob gob) {
        if (gob == null || gob.ngob == null || gob.ngob.name == null) {
            return null;
        }
        
        // First, try to get existing marker from MarkerID attribute
        MiniMap.MarkerID markerId = gob.getattr(MiniMap.MarkerID.class);
        if (markerId != null && markerId.mark instanceof MapFile.SMarker) {
            return (MapFile.SMarker) markerId.mark;
        }
        
        // If no marker exists, try to find or create one
        if (mapfile == null || mapfile.file == null) {
            return null;
        }
        
        try {
            Coord tc = gob.rc.floor(tilesz);
            MCache.Grid obg = map.glob.map.getgrid(tc.div(cmaps));
            
            if (!mapfile.file.lock.writeLock().tryLock()) {
                return null;
            }
            
            try {
                MapFile.GridInfo info = mapfile.file.gridinfo.get(obg.id);
                if (info == null) {
                    return null;
                }
                
                Coord sc = tc.add(info.sc.sub(obg.gc).mul(cmaps));
                
                // Try to find existing marker
                MapFile.SMarker marker = mapfile.file.smarker(gob.ngob.name, info.seg, sc);
                
                if (marker == null) {
                    // Create new marker - use version 0 as default, will be updated when resource loads
                    int resVer = 0;
                    String displayName = gob.ngob.name;
                    
                    try {
                        Indir<Resource> resIndir = Resource.remote().load(gob.ngob.name);
                        Resource res = resIndir.get();
                        resVer = res.ver;
                        Resource.Tooltip tt = res.layer(Resource.Tooltip.class);
                        if (tt != null) {
                            displayName = tt.t;
                        }
                    } catch (Loading e) {
                        // Resource not loaded yet, use default values
                    } catch (Exception e) {
                        // Ignore errors, use default values
                    }
                    
                    marker = new MapFile.SMarker(mapfile.file, info.seg, sc, displayName,
                        new Resource.Saved(Resource.remote(), gob.ngob.name, resVer));
                    mapfile.file.add(marker);
                }
                
                // Set MarkerID attribute on gob
                synchronized (gob) {
                    gob.setattr(new MiniMap.MarkerID(gob, marker));
                }
                
                return marker;
            } finally {
                mapfile.file.lock.writeLock().unlock();
            }
        } catch (Exception e) {
            // Silently ignore errors
            return null;
        }
    }

    @Override
    public boolean keydown(KeyDownEvent ev) {
        nurgling.tasks.WaitKeyPress.setLastKeyPressed(ev.code);

        // F11 - Toggle DB stats overlay
        if (ev.code == KeyEvent.VK_F11 && (Boolean) NConfig.get(NConfig.Key.ndbenable)) {
            if (dbStatsOverlay != null) {
                if (dbStatsOverlay.visible()) {
                    dbStatsOverlay.hide();
                } else {
                    dbStatsOverlay.show();
                    dbStatsOverlay.raise();
                }
            }
            return true;
        }

        // F10 - toggle LLM agent window
        if (ev.code == KeyEvent.VK_F10) {
            toggleAgentWindow();
            return true;
        }

        return super.keydown(ev);
    }

    @Override
    public boolean globtype(GlobKeyEvent ev) {
        nurgling.sessions.SessionManager sm = nurgling.sessions.SessionManager.getInstance();

        // Check session switching keybindings
        if (nurgling.sessions.SessionTabBar.kb_session_prev.key().match(ev.awt)) {
            sm.switchToPreviousSession();
            return true;
        }
        if (nurgling.sessions.SessionTabBar.kb_session_next.key().match(ev.awt)) {
            sm.switchToNextSession();
            return true;
        }

        // Check per-session hotkeys (Alt+1 through Alt+0)
        for (int i = 0; i < nurgling.sessions.SessionTabBar.SESSION_BINDINGS.length; i++) {
            if (nurgling.sessions.SessionTabBar.SESSION_BINDINGS[i].key().match(ev.awt)) {
                sm.switchToSessionByIndex(i);
                return true;
            }
        }

        return super.globtype(ev);
    }

    public void toggleResourceTimerWindow() {
        if(localizedResourceTimerService != null) {
            localizedResourceTimerService.showTimerWindow();
        }
    }
    
    /**
     * Get the localized resource timer service
     */
    public LocalizedResourceTimerService getLocalizedResourceTimerService() {
        return localizedResourceTimerService;
    }
    
    public LocalizedResourceTimerDialog getAddResourceTimerWidget() {
        return localizedResourceTimerDialog;
    }

    public void toggleAgentWindow() {
        if (agentWindow == null) {
            agentWindow = add(new AgentWindow(this), new Coord(sz.x / 2 - UI.scale(260), sz.y / 2 - UI.scale(180)));
        } else if (agentWindow.visible()) {
            agentWindow.hide();
        } else {
            agentWindow.show();
            agentWindow.raise();
        }
    }

    /**
     * Apply user's preferred movement speed from config.
     * Called when Speedget is created and again when server raises max (login starts at crawl).
     */
    public void applyUserPreferredSpeed() {
        try {
            if (speedget == null) {
                return;
            }
            Integer preferred = LoginPreferences.preferredSpeedFromConfig(NConfig.get(NConfig.Key.preferredMovementSpeed));
            Integer target = LoginPreferences.speedToApply(speedget.cur, speedget.max, preferred);
            if (target != null) {
                NUtils.setSpeed(target);
            }
        } catch (Exception e) {
            System.err.println("[NGameUI] Failed to apply preferred movement speed: " + e.getMessage());
        }
    }
}
