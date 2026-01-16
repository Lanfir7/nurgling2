package nurgling.widgets;

import haven.*;
import haven.Label;
import haven.Window;
import nurgling.*;
import nurgling.actions.bots.SimpleRouteAutoRecorder;
import nurgling.actions.PathFinder;
import nurgling.actions.SimpleRouteWorker;
import nurgling.actions.Action;
import nurgling.routes.SimpleRoute;
import nurgling.routes.SimpleRouteManager;
import nurgling.routes.SimpleRoutePoint;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import nurgling.conf.NDiscordNotification;

/**
 * Упрощенный виджет для управления простыми маршрутами без привязки к зонам.
 */
public class SimpleRoutesWidget extends Window {
    public String currentPath = "";

    public RouteList routeList;
    private final List<RouteItem> routeItems = new ArrayList<>();
    private WaypointList waypointList;
    private Widget actionContainer;
    
    // Discord notification settings
    private CheckBox discordNotifyEnabled;
    private ArrayList trackedObjects = new ArrayList();
    private Widget trackedObjectList;
    private TextEntry newObjectEntry;
    private Widget trackedObjectsContainer;
    private Thread objectTrackerThread;
    private boolean trackerRunning = false;
    private nurgling.actions.ObjectTracker objectTrackerInstance = null;

    public SimpleRoutesWidget() {
        super(UI.scale(new Coord(300, 400)), "Simple Routes");

        Coord p = new Coord(0, UI.scale(5));

        IButton createBtn = add(new IButton(NStyle.add[0].back, NStyle.add[1].back, NStyle.add[2].back) {
            @Override
            public void click() {
                ((NMapView) NUtils.getGameUI().map).addSimpleRoute();
                showRoutes();
            }
        }, p);
        createBtn.settip("Create new simple route");

        IButton importBtn = add(new IButton(NStyle.importb[0].back, NStyle.importb[1].back, NStyle.importb[2].back) {
            @Override
            public void click() {
                super.click();
                java.awt.EventQueue.invokeLater(() -> {
                    JFileChooser fc = new JFileChooser();
                    fc.setFileFilter(new FileNameExtensionFilter("Simple route setting file", "json"));
                    if(fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
                        return;
                    // TODO: Implement import
                    SimpleRoutesWidget.this.hide();
                    SimpleRoutesWidget.this.show();
                });
            }
        }, createBtn.pos("ur").adds(UI.scale(5, 0)));
        importBtn.settip("Import simple route");

        IButton exportBtn;
        add(exportBtn = new IButton(NStyle.exportb[0].back,NStyle.exportb[1].back,NStyle.exportb[2].back){
            @Override
            public void click()
            {
                super.click();
                java.awt.EventQueue.invokeLater(() -> {
                    JFileChooser fc = new JFileChooser();
                    fc.setFileFilter(new FileNameExtensionFilter("Simple routes setting file", "json"));
                    if(fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION)
                        return;
                    // TODO: Implement export
                });
            }
        },importBtn.pos("ur").adds(UI.scale(5,0)));
        exportBtn.settip("Export");

        IButton deleteBtn = add(new IButton(NStyle.remove[0].back, NStyle.remove[1].back, NStyle.remove[2].back) {
            @Override
            public void click() {
                if (routeList.sel != null) {
                    routeList.sel.deleteSelectedRoute();
                }
            }
        }, exportBtn.pos("ur").adds(UI.scale(5, 0)));
        deleteBtn.settip("Delete selected route");

        Label routeListLabel = add(new Label("Routes:", NStyle.areastitle), createBtn.pos("bl").adds(0, 5));
        routeList = add(new RouteList(UI.scale(new Coord(250, 190))), routeListLabel.pos("bl").adds(0, 5));

        Label actionsListLabel = add(new Label("Actions:", NStyle.areastitle), routeListLabel.pos("ur").add(UI.scale(105, 0)));
        actionContainer = add(new Widget(UI.scale(new Coord(200, 70))), actionsListLabel.pos("bl").adds(0, UI.scale(5)));

        Label routeInfoLabel = add(new Label("Route Info:", NStyle.areastitle), routeList.pos("bl").adds(0, UI.scale(5)));
        waypointList = add(new WaypointList(UI.scale(new Coord(350, 140))), routeInfoLabel.pos("bl").adds(0, UI.scale(5)));

        // Discord notification checkbox
        discordNotifyEnabled = add(new CheckBox("Оповещать в Discord"), waypointList.pos("bl").adds(0, UI.scale(10)));
        
        // Tracked objects section
        Label trackedObjectsLabel = add(new Label("Отслеживаемые объекты:", NStyle.areastitle), discordNotifyEnabled.pos("bl").adds(0, UI.scale(5)));
        trackedObjectsContainer = add(new Widget(UI.scale(new Coord(350, 150))), trackedObjectsLabel.pos("bl").adds(0, UI.scale(5)));
        
        trackedObjectList = trackedObjectsContainer.add(new TrackedObjectList(UI.scale(new Coord(340, 100))), new Coord(0, 0));
        
        newObjectEntry = trackedObjectsContainer.add(new TextEntry(UI.scale(200), ""), trackedObjectList.pos("bl").adds(0, UI.scale(5)));
        trackedObjectsContainer.add(new haven.Button(UI.scale(45), "Add") {
            @Override
            public void click() {
                if (!newObjectEntry.text().isEmpty()) {
                    TrackedObjectItem item = new TrackedObjectItem(newObjectEntry.text());
                    item.isEnabled.a = true;
                    trackedObjects.add(item);
                    objectTrackerInstance = null; // Пересоздаем tracker при добавлении объекта
                    ((TrackedObjectList)trackedObjectList).update();
                    saveTrackedObjectsSettings();
                    newObjectEntry.settext("");
                }
            }
        }, newObjectEntry.pos("ur").adds(UI.scale(10), 0));
        
        // При изменении настроек пересоздаем tracker
        discordNotifyEnabled.changed((val) -> {
            objectTrackerInstance = null;
            saveTrackedObjectsSettings();
        });

        loadTrackedObjectsSettings();
        // Не запускаем отслеживание автоматически - только при запуске маршрута
        pack();
    }
    
    /**
     * Запускает поток для постоянной проверки объектов
     */
    private void startObjectTracker() {
        if (objectTrackerThread != null && objectTrackerThread.isAlive()) {
            return;
        }
        
        trackerRunning = true;
        objectTrackerThread = new Thread(() -> {
            while (trackerRunning) {
                try {
                    if (discordNotifyEnabled != null && discordNotifyEnabled.a) {
                        ArrayList<String> enabledObjects = getEnabledTrackedObjects();
                        if (!enabledObjects.isEmpty() && NUtils.getGameUI() != null) {
                            // Создаем или обновляем ObjectTracker
                            if (objectTrackerInstance == null) {
                                objectTrackerInstance = new nurgling.actions.ObjectTracker(
                                    NUtils.getGameUI(), enabledObjects, true);
                                if (NUtils.getGameUI() != null) {
                                    NUtils.getGameUI().msg("ObjectTracker started with " + enabledObjects.size() + " patterns");
                                }
                            } else {
                                // Обновляем настройки если они изменились
                                objectTrackerInstance.updateSettings(enabledObjects, true);
                            }
                            // Проверяем объекты
                            if (objectTrackerInstance != null) {
                                objectTrackerInstance.checkObjects();
                            }
                        } else {
                            // Если настройки изменились, сбрасываем tracker
                            if (objectTrackerInstance != null && NUtils.getGameUI() != null) {
                                NUtils.getGameUI().msg("ObjectTracker stopped: no enabled objects or empty list");
                            }
                            objectTrackerInstance = null;
                        }
                    } else {
                        // Если отключено, сбрасываем tracker
                        objectTrackerInstance = null;
                    }
                    Thread.sleep(2000); // Проверяем каждые 2 секунды
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    // Логируем ошибки для отладки
                    if (NUtils.getGameUI() != null) {
                        NUtils.getGameUI().msg("ObjectTracker error: " + e.getMessage());
                    }
                }
            }
        }, "SimpleRoutesObjectTracker");
        objectTrackerThread.setDaemon(true);
        objectTrackerThread.start();
    }
    
    /**
     * Останавливает поток проверки объектов
     */
    private void stopObjectTracker() {
        trackerRunning = false;
        objectTrackerInstance = null;
        if (objectTrackerThread != null) {
            objectTrackerThread.interrupt();
        }
    }

    @Override
    public void show() {
        super.show();
        showRoutes();
    }

    public void showRoutes() {
        synchronized (routeItems) {
            routeItems.clear();
            SimpleRouteManager manager = ((NMapView) NUtils.getGameUI().map).simpleRouteManager;
            if (manager != null) {
                for (SimpleRoute route : manager.getRoutes().values()) {
                    routeItems.add(new RouteItem(route));
                }
            }
        }
        if (!routeItems.isEmpty()) {
            routeList.change(routeItems.get(routeItems.size() - 1));
        }
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (msg.equals("close")) {
            stopObjectTracker();
            hide();
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }
    
    @Override
    public void destroy() {
        stopObjectTracker();
        super.destroy();
    }

    private void select(int id) {
        SimpleRouteManager manager = ((NMapView) NUtils.getGameUI().map).simpleRouteManager;
        if (manager == null) return;
        
        SimpleRoute route = manager.getRoutes().get(id);

        if (route == null) return;

        int x = 0;

        // Button: Auto waypoint recorder bot
        final SimpleRouteAutoRecorder[] recorder = {null};
        final Thread[] thread = {null};
        final boolean[] active = {false};
        final IButton[] recordBtnNormal = new IButton[1];
        final IButton[] recordBtnActive = new IButton[1];

        final Runnable[] showActive = new Runnable[1];
        final Runnable[] showNormal = new Runnable[1];

        showNormal[0] = () -> {
            if (recordBtnActive[0] != null) {
                recordBtnActive[0].reqdestroy();
                recordBtnActive[0] = null;
            }
            recordBtnNormal[0] = new IButton(NStyle.record[0].back, NStyle.record[1].back, NStyle.record[2].back) {
                @Override
                public void click() {
                    // Start recording
                    recorder[0] = new SimpleRouteAutoRecorder(route);
                    thread[0] = new Thread(recorder[0], "SimpleRouteAutoRecorder");
                    thread[0].start();
                    NUtils.getGameUI().biw.addObserve(thread[0]);
                    NUtils.getGameUI().msg("Started simple route recording for: " + route.name);
                    active[0] = true;
                    // Swap to active button
                    showActive[0].run();
                }
            };
            actionContainer.add(recordBtnNormal[0], new Coord(0, 0)).settip("Start/Stop Auto Waypoint Bot");
        };

        showActive[0] = () -> {
            if (recordBtnNormal[0] != null) {
                recordBtnNormal[0].reqdestroy();
                recordBtnNormal[0] = null;
            }
            recordBtnActive[0] = new IButton(NStyle.record[3].back, NStyle.record[3].back, NStyle.record[3].back) {
                @Override
                public void click() {
                    // Stop recording
                    if (recorder[0] != null) {
                        recorder[0].stop();
                        thread[0].interrupt();
                        NUtils.getGameUI().msg("Stopped simple route recording for: " + route.name);
                    }
                    active[0] = false;
                    // Swap back to normal button
                    showNormal[0].run();
                }
            };
            actionContainer.add(recordBtnActive[0], new Coord(0, 0)).settip("Recording... (Click to stop)");
        };

        showNormal[0].run();

        x += UI.scale(36);

        // Button: Manual waypoint recording
        actionContainer.add(new IButton(NStyle.add[0].back, NStyle.add[1].back, NStyle.add[2].back) {
            @Override
            public void click() {
                NUtils.getGameUI().msg("Recording position for: " + route.name);
                route.addRandomWaypoint();
                waypointList.update(route.waypoints);
                manager.save();
            }
        }, new Coord(x, 0)).settip("Record Position");

        x += UI.scale(36);

        // Button: Navigate to end (go through entire route)
        actionContainer.add(new haven.Button(UI.scale(80), "Идти в конец") {
            @Override
            public void click() {
                if (route.waypoints.isEmpty()) {
                    NUtils.getGameUI().msg("Route is empty");
                    return;
                }
                Thread t = new Thread(() -> {
                    try {
                        // Запускаем отслеживание объектов при старте маршрута
                        startObjectTracker();
                        NUtils.getGameUI().msg("Starting navigation to end of route: " + route.name);
                        // Используем SimpleRouteWorker, который автоматически определяет waterMode
                        new SimpleRouteWorker(new Action() {
                            @Override
                            public nurgling.actions.Results run(NGameUI gui) throws InterruptedException {
                                return nurgling.actions.Results.SUCCESS();
                            }
                        }, route, false).run(NUtils.getGameUI());
                        NUtils.getGameUI().msg("Finished navigation to end of route: " + route.name);
                    } catch (InterruptedException e) {
                        NUtils.getGameUI().msg("Navigation interrupted");
                    } finally {
                        // Останавливаем отслеживание объектов при завершении маршрута
                        stopObjectTracker();
                    }
                }, "SimpleRouteNavigator");
                t.start();
                NUtils.getGameUI().biw.addObserve(t);
            }
        }, new Coord(0, UI.scale(36))).settip("Navigate through entire route to end");

        // Button: Navigate to start (go back to beginning)
        actionContainer.add(new haven.Button(UI.scale(80), "Идти в начало") {
            @Override
            public void click() {
                if (route.waypoints.isEmpty()) {
                    NUtils.getGameUI().msg("Route is empty");
                    return;
                }
                Thread t = new Thread(() -> {
                    try {
                        // Запускаем отслеживание объектов при старте маршрута
                        startObjectTracker();
                        NUtils.getGameUI().msg("Starting navigation to start of route: " + route.name);
                        
                        // Получаем текущую позицию (игрока или корабля)
                        Coord2d currentPos = getCurrentPosition();
                        if (currentPos == null) {
                            NUtils.getGameUI().msg("Cannot determine current position");
                            return;
                        }
                        
                        // Находим ближайшую точку маршрута к текущей позиции
                        int nearestIndex = findNearestWaypointIndex(route, currentPos);
                        if (nearestIndex < 0) {
                            NUtils.getGameUI().msg("Cannot find nearest waypoint");
                            return;
                        }
                        
                        // Идем от ближайшей точки к началу (в обратном порядке)
                        for (int i = nearestIndex; i >= 0; i--) {
                            SimpleRoutePoint point = route.waypoints.get(i);
                            Coord2d target = getWaypointCoord(point);
                            if (target != null) {
                                SimpleRoutesWidget.this.navigateToPoint(target);
                            }
                        }
                        
                        NUtils.getGameUI().msg("Arrived at start of route: " + route.name);
                    } catch (InterruptedException e) {
                        NUtils.getGameUI().msg("Navigation interrupted");
                    } finally {
                        // Останавливаем отслеживание объектов при завершении маршрута
                        stopObjectTracker();
                    }
                }, "SimpleRouteToStart");
                t.start();
                NUtils.getGameUI().biw.addObserve(t);
            }
        }, new Coord(UI.scale(85), UI.scale(36))).settip("Navigate to start of route");

        waypointList.update(route.waypoints);
    }

    public int getSelectedRouteId() {
        return this.routeList.selectedRouteId;
    }

    /**
     * Проверяет, находится ли игрок на корабле/лодке
     */
    private boolean isOnShip() {
        Gob player = NUtils.player();
        if (player == null) return false;
        
        haven.Following following = player.getattr(haven.Following.class);
        if (following == null) return false;
        
        Gob vehicle = following.tgt();
        if (vehicle == null) return false;
        
        String vehicleName = vehicle.ngob.name;
        
        // Проверяем, является ли транспорт водным
        return nurgling.tools.NParser.checkName(vehicleName, "/vehicle/snekkja") ||
               nurgling.tools.NParser.checkName(vehicleName, "/vehicle/knarr") ||
               nurgling.tools.NParser.checkName(vehicleName, "/vehicle/rowboat") ||
               nurgling.tools.NParser.checkName(vehicleName, "/vehicle/spark") ||
               nurgling.tools.NParser.checkName(vehicleName, "/vehicle/dugout");
    }

    /**
     * Получает текущую позицию (игрока или корабля, если игрок на корабле)
     */
    private Coord2d getCurrentPosition() {
        Gob player = NUtils.player();
        if (player == null) return null;
        
        // Если игрок на корабле, используем координаты корабля
        haven.Following following = player.getattr(haven.Following.class);
        if (following != null) {
            Gob vehicle = following.tgt();
            if (vehicle != null) {
                return vehicle.rc;
            }
        }
        
        return player.rc;
    }

    /**
     * Получает координаты waypoint, используя координаты корабля если нужно
     */
    private Coord2d getWaypointCoord(SimpleRoutePoint point) {
        MCache map = NUtils.getGameUI().map.glob.map;
        
        // Сначала пробуем стандартный способ
        Coord2d coord = point.toCoord2d(map);
        if (coord != null) {
            return coord;
        }
        
        // Если не получилось, пробуем использовать координаты корабля для определения gridId
        Gob player = NUtils.player();
        if (player != null) {
            haven.Following following = player.getattr(haven.Following.class);
            if (following != null) {
                Gob vehicle = following.tgt();
                if (vehicle != null) {
                    // Используем координаты корабля для поиска grid
                    Coord tilec = vehicle.rc.div(MCache.tilesz).floor();
                    MCache.Grid vehicleGrid = map.getgridt(tilec);
                    if (vehicleGrid != null && vehicleGrid.id == point.gridId) {
                        // Если grid совпадает, используем координаты из waypoint
                        Coord tilec2 = vehicleGrid.ul.add(point.localCoord);
                        return tilec2.mul(MCache.tilesz).add(MCache.tilehsz);
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Находит индекс ближайшей точки маршрута к текущей позиции
     */
    private int findNearestWaypointIndex(SimpleRoute route, Coord2d currentPos) {
        int nearestIndex = -1;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < route.waypoints.size(); i++) {
            SimpleRoutePoint point = route.waypoints.get(i);
            Coord2d pointCoord = getWaypointCoord(point);
            if (pointCoord != null) {
                double dist = currentPos.dist(pointCoord);
                if (dist < minDistance) {
                    minDistance = dist;
                    nearestIndex = i;
                }
            }
        }
        
        return nearestIndex;
    }

    /**
     * Навигация к точке - для корабля использует прямой клик, для пешего - PathFinder
     */
    private void navigateToPoint(Coord2d target) throws InterruptedException {
        if (isOnShip()) {
            // Для корабля используем прямой клик на карту
            NUtils.getGameUI().map.wdgmsg("click", Coord.z, target.floor(haven.OCache.posres), 1, 0);
            
            // Ждем немного для начала движения
            Thread.sleep(500);
            
            // Ждем, пока достигнем точки (с большей погрешностью для корабля)
            Coord2d currentPos = getCurrentPosition();
            int attempts = 0;
            while (currentPos != null && currentPos.dist(target) > 11.0 && attempts < 100) {
                Thread.sleep(200);
                currentPos = getCurrentPosition();
                attempts++;
            }
        } else {
            // Для пешего движения используем PathFinder
            new PathFinder(target).run(NUtils.getGameUI());
        }
    }

    public void updateWaypoints() {
        int routeId = this.routeList.selectedRouteId;
        SimpleRouteManager manager = ((NMapView) NUtils.getGameUI().map).simpleRouteManager;
        if (manager == null) {
            this.waypointList.update(new ArrayList<>());
            return;
        }

        SimpleRoute route = manager.getRoutes().get(routeId);

        if (route == null) {
            this.waypointList.update(new ArrayList<>());
            return;
        }

        ArrayList<SimpleRoutePoint> waypoints = route.waypoints;
        if (waypoints != null) {
            this.waypointList.update(waypoints);
            manager.updateRoute(route);
            manager.save();
        } else {
            this.waypointList.update(new ArrayList<>());
        }
    }

    public class RouteList extends SListBox<RouteItem, Widget> {
        private int selectedRouteId;

        public RouteList(Coord sz) {
            super(sz, UI.scale(20));
        }

        @Override
        protected List<RouteItem> items() {
            synchronized (routeItems) {
                return routeItems;
            }
        }

        @Override
        public void resize(Coord sz) {
            super.resize(new Coord(UI.scale(150) - UI.scale(6), sz.y));
        }

        @Override
        protected Widget makeitem(RouteItem item, int idx, Coord sz) {
            return new ItemWidget<RouteItem>(this, sz, item) {{
                add(item);
            }
                @Override
                public boolean mousedown(MouseDownEvent ev) {
                    if (ev.b == 3) {
                        routeList.change(item);
                        item.opts(ev.c);
                        return true;
                    } else if (ev.b == 1) {
                        list.change(item);
                        return true;
                    }
                    return super.mousedown(ev);
                }
            };
        }

        Color bg = new Color(30, 40, 40, 160);

        @Override
        public void draw(GOut g) {
            g.chcolor(bg);
            g.frect(Coord.z, g.sz());
            super.draw(g);
        }

        @Override
        public void change(RouteItem item) {
            super.change(item);
            if (item != null) {
                actionContainer.show();
                waypointList.show();
                this.selectedRouteId = item.route.id;
                SimpleRoutesWidget.this.select(selectedRouteId);
            }
        }
    }

    public class RouteItem extends Widget {
        Label label;
        SimpleRoute route;
        NFlowerMenu menu;

        public RouteItem(SimpleRoute route) {
            this.route = route;
            this.label = add(new Label(route.name));
            sz = label.sz.add(UI.scale(6), UI.scale(4));
        }

        @Override
        public void draw(GOut g) {
            if (routeList.sel == this) {
                g.chcolor(0, 0, 0, 0);
                g.frect(Coord.z, sz);
                g.chcolor();
            }
            super.draw(g);
        }

        public void opts(Coord c) {
            if (menu == null) {
                menu = new NFlowerMenu(new String[]{"Edit name", "Delete"}) {
                    @Override
                    public boolean mousedown(MouseDownEvent ev) {
                        if (super.mousedown(ev))
                            nchoose(null);
                        return true;
                    }

                    @Override
                    public void nchoose(NPetal option) {
                        if (option != null) {
                            if (option.name.equals("Edit name")) {
                                NEditSimpleRouteName.openChangeName(route, RouteItem.this);
                            } else if (option.name.equals("Delete")) {
                                deleteSelectedRoute();
                            }
                        }
                        uimsg("cancel");
                    }

                    @Override
                    public void destroy() {
                        menu = null;
                        super.destroy();
                    }
                };
            }

            Widget par = parent;
            Coord pos = c;
            while (par != null && !(par instanceof GameUI)) {
                pos = pos.add(par.c);
                par = par.parent;
            }
            ui.root.add(menu, pos.add(UI.scale(25, 38)));
        }

        public void deleteSelectedRoute() {
            SimpleRouteManager manager = ((NMapView) NUtils.getGameUI().map).simpleRouteManager;
            if (manager != null) {
                manager.deleteRoute(routeList.sel.route);
                manager.save();
            }
            showRoutes();
            if (manager == null || manager.getRoutes().isEmpty()) {
                actionContainer.hide();
                waypointList.hide();
            }
            waypointList.update(route.waypoints);
        }
    }

    public class WaypointList extends SListBox<CoordItem, Widget> {
        private final List<CoordItem> items = new ArrayList<>();

        WaypointList(Coord sz) {
            super(sz, UI.scale(16));
        }

        NFlowerMenu menu;

                private void startNavigation(SimpleRoutePoint point) {
            Thread t = new Thread(() -> {
                try {
                    Coord2d target = SimpleRoutesWidget.this.getWaypointCoord(point);
                    if (target != null) {
                        SimpleRoutesWidget.this.navigateToPoint(target);
                    }
                } catch (InterruptedException e) {
                    NUtils.getGameUI().error("Navigation interrupted by the user");
                }
            }, "SimpleRoutePointNavigator");
            t.start();
            NUtils.getGameUI().biw.addObserve(t);
        }

        @Override
        protected Widget makeitem(CoordItem item, int idx, Coord sz) {
            return new ItemWidget<CoordItem>(this, sz, item) {{
                add(item);
            }
                @Override
                public boolean mousedown(MouseDownEvent ev) {
                    if (ev.b == 3) {
                        SimpleRoutePoint rp = item.routePoint;
                        menu = new NFlowerMenu(new String[]{"Navigate To", "Delete"}) {
                            @Override
                            public boolean mousedown(MouseDownEvent ev) {
                                if(super.mousedown(ev))
                                    nchoose(null);
                                return true;
                            }

                            public void destroy() {
                                menu = null;
                                super.destroy();
                            }

                            @Override
                            public void nchoose(NPetal option) {
                                if (option != null) {
                                    if (option.name.equals("Navigate To")) {
                                        new Thread(() -> {
                                            try {
                                                Coord2d target = SimpleRoutesWidget.this.getWaypointCoord(rp);
                                                if (target != null) {
                                                    SimpleRoutesWidget.this.navigateToPoint(target);
                                                }
                                            } catch (InterruptedException e) {
                                                NUtils.getGameUI().error("Navigation interrupted: " + e.getMessage());
                                            }
                                        }, "SimpleRoutePointNavigator").start();
                                    } else if (option.name.equals("Delete")) {
                                        SimpleRouteManager manager = ((NMapView) NUtils.getGameUI().map).simpleRouteManager;
                                        if (manager != null) {
                                            routeList.sel.route.deleteWaypoint(rp);
                                            waypointList.update(routeList.sel.route.waypoints);
                                            manager.save();
                                        }
                                    }
                                }
                                uimsg("cancel");
                            }
                        };
                        Widget par = parent;
                        Coord pos = ev.c;
                        while (par != null && !(par instanceof GameUI)) {
                            pos = pos.add(par.c);
                            par = par.parent;
                        }
                        ui.root.add(menu, pos.add(UI.scale(25, 38)));
                        return true;
                    } else if (ev.b == 1) {
                        startNavigation(item.routePoint);
                        return true;
                    }
                    return super.mousedown(ev);
                }
            };
        }

        public void update(List<SimpleRoutePoint> points) {
            synchronized (items) {
                items.clear();
                for (SimpleRoutePoint point : points) {
                    items.add(new CoordItem(point.gridId, point.toCoord2d(NUtils.getGameUI().map.glob.map), point));
                }
            }
        }

        @Override
        protected List<CoordItem> items() {
            return items;
        }

        Color bg = new Color(30, 40, 40, 160);

        @Override
        public void draw(GOut g) {
            g.chcolor(bg);
            g.frect(Coord.z, g.sz());
            super.draw(g);
        }
    }

    public class CoordItem extends Widget {
        private final Label label;
        private final SimpleRoutePoint routePoint;

        public CoordItem(long gridid, Coord2d coord, SimpleRoutePoint routePoint) {
            this.routePoint = routePoint;
            String displayText = String.valueOf(gridid) + " " + routePoint.id;
            
            // Check all connections for door and gobName information
            for (int neighborHash : routePoint.getConnectedNeighbors()) {
                SimpleRoutePoint.Connection conn = routePoint.getConnection(neighborHash);
                if (conn != null && conn.gobHash != null) {
                    if (!conn.gobName.isEmpty()) {
                        displayText = conn.gobName + " " + routePoint.id;
                    }
                    if (conn.isDoor) {
                        displayText = "★ " + displayText;
                        break;
                    }
                }
            }
            
            this.label = add(new Label(displayText));
            this.sz = label.sz.add(UI.scale(4), UI.scale(4));
        }

        @Override
        public void draw(GOut g) {
            super.draw(g);
        }
    }

    public class TrackedObjectList extends SListBox<TrackedObjectItem, Widget> {
        TrackedObjectList(Coord sz) {
            super(sz, UI.scale(22));
        }

        @Override
        protected List<TrackedObjectItem> items() {
            return trackedObjects;
        }

        @Override
        protected Widget makeitem(TrackedObjectItem item, int idx, Coord sz) {
            return new ItemWidget<TrackedObjectItem>(this, sz, item) {
                {
                    add(item);
                    item.resize(sz);
                }
            };
        }

        Color bg = new Color(30, 40, 40, 160);

        @Override
        public void draw(GOut g) {
            g.chcolor(bg);
            g.frect(Coord.z, g.sz());
            g.chcolor();
            super.draw(g);
        }
    }

    public class TrackedObjectItem extends Widget {
        Label text;
        IButton remove;
        public CheckBox isEnabled;

        @Override
        public void resize(Coord sz) {
            if (isEnabled != null) {
                isEnabled.move(new Coord(isEnabled.c.x, (sz.y - isEnabled.sz.y) / 2));
            }
            if (text != null) {
                text.move(new Coord(text.c.x, (sz.y - text.sz.y) / 2));
            }
            if (remove != null) {
                remove.move(new Coord(sz.x - NStyle.removei[0].sz().x - UI.scale(5),
                        (sz.y - remove.sz.y) / 2));
            }
            super.resize(sz);
        }

        public TrackedObjectItem(String text) {
            prev = isEnabled = add(new CheckBox("") {
                public void set(boolean val) {
                    a = val;
                    objectTrackerInstance = null; // Пересоздаем tracker при изменении
                    saveTrackedObjectsSettings();
                }
            });
            this.text = add(new Label(text), prev.pos("ur").add(UI.scale(2), 0));
            remove = add(new IButton(NStyle.removei[0].back, NStyle.removei[1].back, NStyle.removei[2].back) {
                @Override
                public void click() {
                    trackedObjects.remove(TrackedObjectItem.this);
                    objectTrackerInstance = null; // Пересоздаем tracker при изменении списка
                    if (trackedObjectList != null) {
                        ((TrackedObjectList)trackedObjectList).update();
                    }
                    saveTrackedObjectsSettings();
                }
            }, new Coord(UI.scale(340) - NStyle.removei[0].sz().x, 0).sub(UI.scale(5), UI.scale(1)));
            remove.settip("Remove");
            pack();
        }

        public String text() {
            return text.text();
        }
    }

    private void loadTrackedObjectsSettings() {
        discordNotifyEnabled.a = getBool(NConfig.Key.simpleRoutesDiscordNotify);
        trackedObjects.clear();

        if (NConfig.get(NConfig.Key.simpleRoutesTrackedObjects) != null) {
            for (HashMap<String, Object> item : (ArrayList<HashMap<String, Object>>) NConfig.get(NConfig.Key.simpleRoutesTrackedObjects)) {
                TrackedObjectItem aitem = new TrackedObjectItem((String) item.get("name"));
                aitem.isEnabled.a = (Boolean) item.get("enabled");
                trackedObjects.add(aitem);
            }
        }

        if (trackedObjectList != null)
            ((TrackedObjectList)trackedObjectList).update();
    }

    private void saveTrackedObjectsSettings() {
        NConfig.set(NConfig.Key.simpleRoutesDiscordNotify, discordNotifyEnabled.a);

        ArrayList<HashMap<String, Object>> plist = new ArrayList<>();
        for (Object obj : trackedObjects) {
            TrackedObjectItem item = (TrackedObjectItem) obj;
            HashMap<String, Object> map = new HashMap<>();
            map.put("name", item.text());
            map.put("enabled", item.isEnabled.a);
            plist.add(map);
        }
        NConfig.set(NConfig.Key.simpleRoutesTrackedObjects, plist);
        NConfig.needUpdate();
    }

    private boolean getBool(NConfig.Key key) {
        Object val = NConfig.get(key);
        return val instanceof Boolean ? (Boolean) val : false;
    }

    public boolean isDiscordNotifyEnabled() {
        return discordNotifyEnabled != null && discordNotifyEnabled.a;
    }

    public ArrayList<String> getEnabledTrackedObjects() {
        ArrayList<String> result = new ArrayList<>();
        for (Object obj : trackedObjects) {
            TrackedObjectItem item = (TrackedObjectItem) obj;
            if (item.isEnabled.a) {
                result.add(item.text());
            }
        }
        return result;
    }
}

