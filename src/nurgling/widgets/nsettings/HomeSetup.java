package nurgling.widgets.nsettings;

import haven.Button;
import haven.Coord;
import haven.GOut;
import haven.Label;
import haven.SListBox;
import haven.SListWidget;
import haven.UI;
import haven.Widget;
import haven.Window;
import monitoring.NGlobalSearchItems;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.db.DatabaseManager;
import nurgling.i18n.L10n;
import nurgling.tools.CurrentHomeTerritories;
import nurgling.tools.HomeInteriorRegistry;
import nurgling.tools.HomeInteriorStore;
import nurgling.tools.HomeTerritories;
import nurgling.tools.HomeTerritoryDebug;
import nurgling.tools.NSearchItem;
import nurgling.widgets.ChunkNavVisualizerWindow;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Configures territories that are treated as home for the current game world. */
public class HomeSetup extends Panel {
    private List<HomeTerritories.Entry> homes = new ArrayList<>();
    private List<HomeTerritories.Entry> loadedHomes = new ArrayList<>();
    private HomeInteriorRegistry indoorSnapshot = HomeInteriorRegistry.empty();
    private final Set<String> removedIndoorBindingIds = new LinkedHashSet<String>();
    private List<HomeInteriorRegistry.Binding> indoorItems = new ArrayList<HomeInteriorRegistry.Binding>();
    private final HomeTerritoryList homeList;
    private final IndoorHomeList indoorList;
    private final AtomicReference<StorageWipeResult> pendingStorageWipeResult = new AtomicReference<>();
    private boolean storageWipeInProgress;
    private NGameUI storageWipeGui;
    private Future<?> storageWipeTask;
    private final Supplier<? extends Widget> gameRoot;
    final Button chunkNav;

    private static final class StorageWipeResult {
        final String error;

        private StorageWipeResult(String error) {
            this.error = error;
        }

        static StorageWipeResult success() {
            return new StorageWipeResult(null);
        }

        static StorageWipeResult failed(Throwable error) {
            String message = error == null ? null : error.getMessage();
            if (message == null || message.trim().isEmpty())
                message = error == null ? "Database task rejected" : error.getClass().getSimpleName();
            return new StorageWipeResult(message);
        }
    }

    public HomeSetup() {
        this(NUtils::getGameUI);
    }

    HomeSetup(Supplier<? extends Widget> gameRoot) {
        super();
        this.gameRoot = gameRoot;
        Widget previous = add(new Label("● " + L10n.get("world.section.home_territories")),
                UI.scale(10, 10));
        Button saveCurrent = add(new Button(UI.scale(260), L10n.get("world.home.save_current")) {
            @Override
            public void click() {
                saveCurrentTerritories();
            }
        }, previous.pos("bl").adds(0, 8));
        add(new Button(UI.scale(28), "?") {
            @Override
            public void click() {
                showCurrentLocation();
            }
        }, saveCurrent.pos("ur").adds(5, 0));
        homeList = add(new HomeTerritoryList(UI.scale(430, 260)),
                saveCurrent.pos("bl").adds(0, 8));
        Widget indoorTitle = add(new Label("● " + L10n.get("world.home.indoor.section")),
                homeList.pos("bl").adds(0, 10));
        indoorList = add(new IndoorHomeList(UI.scale(430, 140)),
                indoorTitle.pos("bl").adds(0, 8));
        Widget storageWipe = add(new Button(UI.scale(430), L10n.get("world.home.storage_wipe")) {
            @Override
            public void click() {
                confirmStorageWipe();
            }
        }, indoorList.pos("bl").adds(0, 14));
        chunkNav = add(new Button(UI.scale(430), L10n.get("world.home.chunknav_map")) {
            @Override
            public void click() {
                openChunkNavMap();
            }
        }, storageWipe.pos("bl").adds(0, 8));
        resize(new Coord(sz.x, Math.max(sz.y, contentsz().y)));
    }

    @Override
    public void load() {
        loadedHomes = HomeTerritories.decodeForWorld(
                NConfig.get(NConfig.Key.homeTerritories), currentWorldGenus());
        homes = new ArrayList<>(loadedHomes);
        homeList.update();
        indoorSnapshot = HomeInteriorStore.load(currentWorldGenus());
        removedIndoorBindingIds.clear();
        refreshIndoorItems();
    }

    @Override
    public void save() {
        String genus = currentWorldGenus();
        List<HomeTerritories.Entry> edited = new ArrayList<>(homes);
        List<HomeTerritories.Entry> baseline = new ArrayList<>(loadedHomes);
        Object updated = NConfig.update(NConfig.Key.homeTerritories, stored -> {
            List<HomeTerritories.Entry> current = HomeTerritories.decodeForWorld(stored, genus);
            return HomeTerritories.encodeForWorld(stored, genus,
                    HomeTerritories.applyEdits(current, baseline, edited));
        });
        loadedHomes = HomeTerritories.decodeForWorld(updated, genus);
        homes = new ArrayList<>(loadedHomes);
        homeList.update();
        indoorSnapshot = HomeInteriorStore.update(genus,
                current -> current.applyRemovals(removedIndoorBindingIds));
        removedIndoorBindingIds.clear();
        refreshIndoorItems();
        NConfig.needUpdate();
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        StorageWipeResult result = pendingStorageWipeResult.getAndSet(null);
        if (result == null && storageWipeTask != null && storageWipeTask.isCancelled())
            result = StorageWipeResult.failed(new CancellationException("Database task cancelled"));
        if (result == null)
            return;
        storageWipeInProgress = false;
        storageWipeTask = null;
        NGameUI gui = storageWipeGui;
        storageWipeGui = null;
        if (gui == null || gui.ui != ui)
            return;
        if (result.error == null) {
            NGlobalSearchItems.clearResults();
            NGlobalSearchItems.clearQueryCache();
            NSearchItem.notifyContainerDataChanged();
            if (gui.storageItemsWidget != null)
                gui.storageItemsWidget.requestRefresh();
            gui.msg(L10n.get("world.home.storage_wipe.success"), Color.YELLOW);
        } else {
            gui.error(L10n.get("world.home.storage_wipe.failed", result.error));
        }
    }

    private void saveCurrentTerritories() {
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.ui == null || gui.ui.sess == null) {
            showHomeTerritoryError();
            return;
        }
        CurrentHomeTerritories.Detection detection = CurrentHomeTerritories.detect(gui);
        if (detection.loading) {
            gui.error(L10n.get("world.home.loading"));
            return;
        }
        if (detection.entries.isEmpty()) {
            showHomeTerritoryError();
            return;
        }
        homes = HomeTerritories.merge(homes, detection.entries);
        homeList.update();
        indoorList.reset();
        indoorList.update();
    }

    private void showCurrentLocation() {
        NGameUI gui = NUtils.getGameUI();
        if (gui == null)
            return;
        CurrentHomeTerritories.Detection detection = CurrentHomeTerritories.detect(gui);
        HomeTerritoryDebug.Snapshot snapshot = HomeTerritoryDebug.inspect(
                homes, detection.entries, CurrentHomeTerritories.status(gui));
        Window window = new Window(Coord.z, L10n.get("home.debug.title")) {
            @Override
            public void reqclose() {
                reqdestroy();
            }
        };
        Widget previous = window.add(new Label(valueLine("home.debug.data",
                snapshot.loading ? L10n.get("msg.loading") : L10n.get("home.debug.ready"))), Coord.z);
        previous = window.add(new Label(valueLine("home.debug.village",
                valueOrNone(snapshot.village))), previous.pos("bl").adds(0, 6));
        previous = window.add(new Label(valueLine("home.debug.claim",
                valueOrNone(snapshot.claim))), previous.pos("bl").adds(0, 6));
        previous = window.add(new Label(valueLine("home.debug.village_home",
                yesNo(snapshot.villageHome))), previous.pos("bl").adds(0, 6));
        previous = window.add(new Label(valueLine("home.debug.claim_home",
                yesNo(snapshot.claimHome))), previous.pos("bl").adds(0, 6));
        previous = window.add(new Label(valueLine("home.debug.home_zone",
                yesNo(snapshot.home))), previous.pos("bl").adds(0, 6));
        previous = window.add(new Label(valueLine("home.debug.indoor_home",
                yesNoUnknown(snapshot.indoorHome, snapshot.navigationLoading))),
                previous.pos("bl").adds(0, 6));
        previous = window.add(new Label(valueLine("home.debug.home_source",
                homeSourceText(snapshot))), previous.pos("bl").adds(0, 6));
        previous = window.add(new Label(valueLine("home.debug.stable_grid",
                Long.toString(snapshot.gridId))), previous.pos("bl").adds(0, 6));
        previous = window.add(new Label(valueLine("home.debug.instance",
                Long.toString(snapshot.instanceId))), previous.pos("bl").adds(0, 6));
        window.add(new Label(valueLine("home.debug.claim_tiles",
                Integer.toString(snapshot.claimTiles))), previous.pos("bl").adds(0, 6));
        window.pack();
        gui.add(window, gui.sz.sub(window.sz).div(2).max(Coord.z));
        window.raise();
    }

    private void openChunkNavMap() {
        Widget gui = gameRoot.get();
        if (gui == null)
            return;
        Window window = new ChunkNavVisualizerWindow();
        gui.add(window, gui.sz.sub(window.sz).div(2).max(Coord.z));
        window.raise();
    }

    private static String valueLine(String key, String value) {
        return L10n.get(key) + ": " + value;
    }

    private static String valueOrNone(String value) {
        return value == null || value.isEmpty() ? L10n.get("common.none") : value;
    }

    private static String yesNo(boolean value) {
        return L10n.get(value ? "common.yes" : "common.no");
    }

    private static String yesNoUnknown(boolean value, boolean unknown) {
        return unknown ? L10n.get("common.unknown") : yesNo(value);
    }

    private static String homeSourceText(HomeTerritoryDebug.Snapshot snapshot) {
        switch (snapshot.source) {
            case DIRECT_VILLAGE:
                return L10n.get("home.debug.source.village");
            case DIRECT_CLAIM:
                return L10n.get("home.debug.source.claim");
            case DIRECT_BOTH:
                return L10n.get("home.debug.source.both");
            case INDOOR_AUTO:
            case INDOOR_MANUAL:
                return valueOrNone(snapshot.homeSource);
            default:
                return L10n.get("common.none");
        }
    }

    private String currentWorldGenus() {
        NGameUI gui = NUtils.getGameUI();
        return gui == null ? "" : gui.getGenus();
    }

    private void showHomeTerritoryError() {
        NGameUI gui = NUtils.getGameUI();
        if (gui != null)
            gui.error(L10n.get("world.home.none_detected"));
    }

    private void confirmStorageWipe() {
        NGameUI gui = NUtils.getGameUI();
        if (storageWipeInProgress) {
            if (gui != null)
                gui.error(L10n.get("world.home.storage_wipe.busy"));
            return;
        }
        DatabaseManager databaseManager = databaseManager(gui);
        if (databaseManager == null) {
            if (gui != null)
                gui.error(L10n.get("world.home.storage_wipe.db_not_ready"));
            return;
        }
        Window confirm = new Window(UI.scale(new Coord(430, 110)),
                L10n.get("world.home.storage_wipe.confirm_title"), true) {
            @Override
            public void reqclose() {
                reqdestroy();
            }
        };
        confirm.add(new Label(L10n.get("world.home.storage_wipe.confirm_line1")), UI.scale(10, 5));
        confirm.add(new Label(L10n.get("world.home.storage_wipe.confirm_line2")), UI.scale(10, 27));
        confirm.adda(new Button(UI.scale(130), L10n.get("world.home.storage_wipe.confirm_go"), false, () -> {
            confirm.reqdestroy();
            wipeStorage(gui, databaseManager);
        }), UI.scale(new Coord(125, 65)), 0.5, 0.0);
        confirm.adda(new Button(UI.scale(110), L10n.get("common.cancel"), false, confirm::reqdestroy),
                UI.scale(new Coord(285, 65)), 0.5, 0.0);
        gui.add(confirm, gui.sz.sub(confirm.sz).div(2).max(Coord.z));
        confirm.raise();
    }

    private static DatabaseManager databaseManager(NGameUI gui) {
        if (gui == null || gui.ui == null || gui.ui.core == null
                || gui.ui.core.databaseManager == null || !gui.ui.core.databaseManager.isReady())
            return null;
        return gui.ui.core.databaseManager;
    }

    private void wipeStorage(NGameUI gui, DatabaseManager databaseManager) {
        storageWipeInProgress = true;
        storageWipeGui = gui;
        storageWipeTask = databaseManager.submitTask(() -> {
            try {
                databaseManager.getContainerService().deleteAllContainers();
                pendingStorageWipeResult.set(StorageWipeResult.success());
            } catch (Exception error) {
                pendingStorageWipeResult.set(StorageWipeResult.failed(error));
            }
        });
        if (storageWipeTask == null)
            pendingStorageWipeResult.set(StorageWipeResult.failed(null));
    }

    private void refreshIndoorItems() {
        indoorItems = new ArrayList<HomeInteriorRegistry.Binding>();
        for (HomeInteriorRegistry.Binding binding : indoorSnapshot.bindings()) {
            if (!removedIndoorBindingIds.contains(binding.id))
                indoorItems.add(binding);
        }
        indoorList.reset();
        indoorList.update();
    }

    private String formatIndoorRow(HomeInteriorRegistry.Binding binding) {
        String name = indoorDisplayName(binding);
        if (binding.manual)
            return name + " — " + L10n.get("world.home.indoor.manual");
        return name + " — " + L10n.get("world.home.indoor.auto") + " — " + indoorOriginText(binding);
    }

    private static String indoorDisplayName(HomeInteriorRegistry.Binding binding) {
        if (binding.displayName != null && !binding.displayName.isEmpty())
            return binding.displayName;
        if (binding.rootPortal != null && binding.rootPortal.resource != null) {
            String resource = binding.rootPortal.resource;
            int slash = resource.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < resource.length())
                return resource.substring(slash + 1);
            return resource;
        }
        return binding.id;
    }

    private String indoorOriginText(HomeInteriorRegistry.Binding binding) {
        String names = binding.activeOriginNames(homes);
        return names.isEmpty() ? L10n.get("world.home.indoor.inactive") : names;
    }

    private class HomeTerritoryList extends SListBox<HomeTerritories.Entry, Widget> {
        private final Color background = new Color(30, 40, 40, 160);

        HomeTerritoryList(Coord size) {
            super(size, UI.scale(24));
        }

        @Override
        protected List<HomeTerritories.Entry> items() {
            return homes;
        }

        @Override
        protected Widget makeitem(HomeTerritories.Entry entry, int index, Coord size) {
            return new SListWidget.ItemWidget<HomeTerritories.Entry>(this, size, entry) {
                private final Label label = add(new Label(entry.displayName()), UI.scale(5, 3));
                private final Button remove = add(new Button(UI.scale(24), "×") {
                    @Override
                    public void click() {
                        homes.remove(entry);
                        homeList.update();
                        indoorList.reset();
                        indoorList.update();
                    }
                });

                {
                    resize(size);
                }

                @Override
                public void resize(Coord newSize) {
                    super.resize(newSize);
                    label.move(new Coord(UI.scale(5), (newSize.y - label.sz.y) / 2));
                    remove.move(new Coord(newSize.x - remove.sz.x - UI.scale(5),
                            (newSize.y - remove.sz.y) / 2));
                }
            };
        }

        @Override
        public void draw(GOut g) {
            g.chcolor(background);
            g.frect(Coord.z, g.sz());
            g.chcolor();
            super.draw(g);
        }
    }

    private class IndoorHomeList extends SListBox<HomeInteriorRegistry.Binding, Widget> {
        private final Color background = new Color(30, 40, 40, 160);

        IndoorHomeList(Coord size) {
            super(size, UI.scale(24));
        }

        @Override
        protected List<HomeInteriorRegistry.Binding> items() {
            return indoorItems;
        }

        @Override
        protected Widget makeitem(HomeInteriorRegistry.Binding binding, int index, Coord size) {
            return new SListWidget.ItemWidget<HomeInteriorRegistry.Binding>(this, size, binding) {
                private final Label label = add(new Label(formatIndoorRow(binding)), UI.scale(5, 3));
                private final Button remove = add(new Button(UI.scale(24), "×") {
                    @Override
                    public void click() {
                        removedIndoorBindingIds.add(binding.id);
                        refreshIndoorItems();
                    }
                });

                {
                    resize(size);
                }

                @Override
                public void resize(Coord newSize) {
                    super.resize(newSize);
                    label.move(new Coord(UI.scale(5), (newSize.y - label.sz.y) / 2));
                    remove.move(new Coord(newSize.x - remove.sz.x - UI.scale(5),
                            (newSize.y - remove.sz.y) / 2));
                }
            };
        }

        @Override
        public void draw(GOut g) {
            g.chcolor(background);
            g.frect(Coord.z, g.sz());
            g.chcolor();
            super.draw(g);
        }
    }
}
