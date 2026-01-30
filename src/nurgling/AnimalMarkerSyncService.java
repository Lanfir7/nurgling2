package nurgling;

import haven.Resource;
import nurgling.db.dao.AnimalMarkerDao;
import nurgling.db.service.AnimalMarkerService;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Синхронизация маркеров животных из Postgres в LabeledMarkService каждые 30 сек.
 * Загрузка из БД и применение merge выполняются в отдельном потоке (AnimalMarkerWorker);
 * merge вызывается на UI-потоке через EventQueue.invokeLater.
 */
public class AnimalMarkerSyncService {
    private static final long SYNC_INTERVAL_SEC = 30;
    /** Задержка перед первой загрузкой после входа в игру (сек) */
    private static final long INITIAL_DELAY_SEC = 2;

    private final NGameUI gui;
    private final AnimalMarkerService animalMarkerService;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> syncTask;
    private ScheduledFuture<?> initialTask;
    private volatile BufferedImage defaultIcon;

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

    /** Ставит загрузку из БД и merge в очередь воркера; merge выполнится на UI-потоке. */
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
                    List<AnimalMarkerDao.AnimalMarkerData> list = animalMarkerService.findAllByProfile(profile);
                    BufferedImage icon = getDefaultIcon();
                    List<AnimalMarkerDao.AnimalMarkerData> listCopy = new java.util.ArrayList<>(list);
                    NGameUI g = gui;
                    BufferedImage defaultIconForMerge = icon;
                    java.awt.EventQueue.invokeLater(() -> {
                        if (g != null && g.labeledMarkService != null) {
                            g.labeledMarkService.mergeAnimalMarkersFromDb(listCopy, defaultIconForMerge, g);
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
