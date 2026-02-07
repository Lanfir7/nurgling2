package nurgling;

import haven.Coord;
import haven.Resource;
import nurgling.db.dao.AnimalMarkerDao;
import nurgling.db.service.AnimalMarkerService;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Синхронизация маркеров животных из Postgres в LabeledMarkService каждые 30 сек.
 * Загрузка из БД и предзагрузка иконок выполняются в отдельном потоке (AnimalMarkerWorker);
 * merge на UI-поток передаётся уже с готовыми иконками, чтобы не блокировать UI.
 */
public class AnimalMarkerSyncService {
    private static final long SYNC_INTERVAL_SEC = 30;
    /** Задержка перед первой загрузкой после входа в игру (сек) - увеличена чтобы игра успела загрузиться */
    private static final long INITIAL_DELAY_SEC = 10;

    private final NGameUI gui;
    private final AnimalMarkerService animalMarkerService;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> syncTask;
    private ScheduledFuture<?> initialTask;
    private volatile BufferedImage defaultIcon;
    
    /**
     * Маркер животного с предзагруженной иконкой.
     * Иконка загружается в фоновом потоке, чтобы не блокировать UI.
     */
    public static class PreloadedAnimalMarker {
        public final String locationId;
        public final String label;
        public final String resourceType;
        public final long segmentId;
        public final Coord tileCoords;
        public final long gridId;
        public final Coord localTileCoords;
        public final BufferedImage icon;
        public final Long killedAtMs;
        public final String killedBy;
        public final String iconPath;
        public final String animalType;
        
        public PreloadedAnimalMarker(AnimalMarkerDao.AnimalMarkerData data, BufferedImage icon) {
            this.locationId = "animal_" + data.getGobId();
            this.label = data.getQuality() != null ? ("q" + (int) Math.round(data.getQuality())) : "";
            String displayName = data.getDisplayName() != null && !data.getDisplayName().isEmpty() 
                    ? data.getDisplayName() 
                    : (data.getAnimalType() != null && !data.getAnimalType().contains("/") ? data.getAnimalType() : "Animal");
            this.resourceType = displayName;
            this.segmentId = data.getSegmentId();
            this.tileCoords = new Coord(data.getTileX(), data.getTileY());
            this.gridId = data.getGridId() != null ? data.getGridId() : -1;
            this.localTileCoords = (data.getLocalTileX() != null && data.getLocalTileY() != null) 
                    ? new Coord(data.getLocalTileX(), data.getLocalTileY()) : null;
            this.icon = icon;
            this.killedAtMs = data.getKilledAt() != null ? data.getKilledAt().getTime() : null;
            this.killedBy = data.getKilledBy();
            this.iconPath = data.getIconPath();
            this.animalType = data.getAnimalType();
        }
    }

    public AnimalMarkerSyncService(NGameUI gui) {
        this.gui = gui;
        this.animalMarkerService = NCore.databaseManager != null ? NCore.databaseManager.getAnimalMarkerService() : null;
    }

    public void start() {
        if (animalMarkerService == null || !animalMarkerService.isAvailable()) {
            return;
        }
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AnimalMarkerSync");
            t.setDaemon(true);
            return t;
        });
        initialTask = scheduler.schedule(this::scheduleSync, INITIAL_DELAY_SEC, TimeUnit.SECONDS);
        syncTask = scheduler.scheduleWithFixedDelay(this::scheduleSync, INITIAL_DELAY_SEC + SYNC_INTERVAL_SEC, SYNC_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public void stop() {
        if (initialTask != null) {
            initialTask.cancel(false);
            initialTask = null;
        }
        if (syncTask != null) {
            syncTask.cancel(false);
            syncTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    /** Запуск синхронизации сейчас (например, при открытии карты). */
    public void syncNow() {
        scheduleSync();
    }

    /** 
     * Ставит загрузку из БД и предзагрузку иконок в очередь воркера.
     * Иконки загружаются в фоновом потоке, затем готовые маркеры передаются на UI-поток.
     * Это устраняет лаги UI при загрузке большого количества маркеров.
     */
    private void scheduleSync() {
        if (gui == null || gui.labeledMarkService == null || animalMarkerService == null || !animalMarkerService.isAvailable()) {
            return;
        }
        String profile = gui.getGenus();
        if (profile == null || profile.isEmpty()) {
            return;
        }
        try {
            gui.getAnimalMarkerWorker().submit(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    List<AnimalMarkerDao.AnimalMarkerData> list = animalMarkerService.findAllByProfile(profile);
                    if (list == null || list.isEmpty()) {
                        return;
                    }
                    
                    // Предзагружаем иконки в фоновом потоке
                    BufferedImage defaultIconForMerge = getDefaultIcon();
                    List<PreloadedAnimalMarker> preloadedMarkers = new ArrayList<>(list.size());
                    
                    for (AnimalMarkerDao.AnimalMarkerData data : list) {
                        BufferedImage icon = preloadIcon(data, defaultIconForMerge);
                        preloadedMarkers.add(new PreloadedAnimalMarker(data, icon));
                    }
                    
                    long preloadTime = System.currentTimeMillis() - startTime;
                    if (preloadTime > 500) {
                        System.out.println("AnimalMarkerSyncService: preloaded " + preloadedMarkers.size() + " markers with icons in " + preloadTime + "ms (background thread)");
                    }
                    
                    // Передаём на UI-поток уже готовые маркеры с иконками
                    NGameUI g = gui;
                    java.awt.EventQueue.invokeLater(() -> {
                        if (g != null && g.labeledMarkService != null) {
                            g.labeledMarkService.mergeAnimalMarkersFromDbPreloaded(preloadedMarkers);
                        }
                    });
                } catch (Exception e) {
                    System.err.println("AnimalMarkerSyncService: sync failed: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("AnimalMarkerSyncService: schedule failed: " + e.getMessage());
        }
    }
    
    /**
     * Предзагрузка иконки для маркера в фоновом потоке.
     * Порядок: кэш → icon_path → iconconf → animal_type → default.
     */
    private BufferedImage preloadIcon(AnimalMarkerDao.AnimalMarkerData data, BufferedImage defaultIcon) {
        // Проверяем кэш
        BufferedImage icon = gui.labeledMarkService.getAnimalIconFromCache(data.getGobId());
        if (icon != null) {
            return icon;
        }
        
        // Пробуем загрузить по icon_path
        if (icon == null && data.getIconPath() != null && !data.getIconPath().isEmpty()) {
            icon = nurgling.actions.ObjectTracker.loadIconFromResourcePath(data.getIconPath());
            if (icon != null) {
                gui.labeledMarkService.cacheAnimalIcon(data.getGobId(), icon);
            }
        }
        
        // Пробуем через Icon Settings (iconconf)
        if (icon == null && data.getAnimalType() != null && data.getAnimalType().startsWith("gfx/kritter/")) {
            icon = nurgling.actions.ObjectTracker.loadIconFromIconConf(data.getAnimalType(), gui);
            if (icon != null) {
                gui.labeledMarkService.cacheAnimalIcon(data.getGobId(), icon);
            }
        }
        
        // Пробуем загрузить по animal_type
        if (icon == null && data.getAnimalType() != null && data.getAnimalType().startsWith("gfx/kritter/")) {
            icon = nurgling.actions.ObjectTracker.loadAnimalIconFromPath(data.getAnimalType(), data.getDisplayName(), gui);
            if (icon != null) {
                gui.labeledMarkService.cacheAnimalIcon(data.getGobId(), icon);
            }
        }
        
        // Fallback на дефолтную иконку
        if (icon == null) {
            icon = defaultIcon;
        }
        
        return icon;
    }

    private BufferedImage getDefaultIcon() {
        if (defaultIcon != null) {
            return defaultIcon;
        }
        try {
            defaultIcon = Resource.loadsimg("gfx/invobjs/kritter");
        } catch (Exception ignored) {
        }
        return defaultIcon;
    }
}
