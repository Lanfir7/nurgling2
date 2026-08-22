package nurgling;

import nurgling.db.dao.LocalTimerDao;
import nurgling.db.service.LocalTimerService;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Synchronizes local resource timers between client and Postgres database.
 * Sync interval: 5 minutes (300 seconds).
 * 
 * The sync process:
 * 1. Uploads local timers to DB (upsert to handle updates)
 * 2. Downloads timers from DB and merges into local service
 * 3. Cleans up expired timers in DB
 * 
 * All times are stored as UTC milliseconds to avoid timezone issues
 * (server is GMT+0, clients may be in different timezones like GMT+2).
 */
public class LocalTimerSyncService {
    /** Sync interval: 5 minutes */
    private static final long SYNC_INTERVAL_SEC = 300;
    /** Initial delay before first sync: 5 seconds */
    private static final long INITIAL_DELAY_SEC = 5;

    private final NGameUI gui;
    private final LocalTimerService localTimerService;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> syncTask;
    private volatile boolean syncing = false;

    public LocalTimerSyncService(NGameUI gui) {
        this.gui = gui;
        this.localTimerService = NCore.databaseManager != null ? NCore.databaseManager.getLocalTimerService() : null;
    }

    /**
     * Start the periodic sync.
     */
    public void start() {
        if (localTimerService == null || !localTimerService.isAvailable()) {
            System.out.println("LocalTimerSyncService: Postgres not available, sync disabled");
            return;
        }
        if (scheduler != null) {
            return; // Already started
        }
        
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LocalTimerSync");
            t.setDaemon(true);
            return t;
        });
        
        // Initial sync after short delay
        syncTask = scheduler.scheduleWithFixedDelay(
            this::syncTimers,
            INITIAL_DELAY_SEC,
            SYNC_INTERVAL_SEC,
            TimeUnit.SECONDS
        );
        
        System.out.println("LocalTimerSyncService: Started (interval: " + SYNC_INTERVAL_SEC + "s)");
    }

    /**
     * Stop the sync service.
     */
    public void stop() {
        if (syncTask != null) {
            syncTask.cancel(false);
            syncTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
            scheduler = null;
        }
        System.out.println("LocalTimerSyncService: Stopped");
    }

    /**
     * Force sync now (can be called manually, e.g., when opening timer window).
     */
    public void syncNow() {
        if (localTimerService == null || !localTimerService.isAvailable()) {
            return;
        }
        // Run in background thread to avoid blocking UI
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.submit(this::syncTimers);
        } else {
            // If scheduler not running, sync in current thread
            syncTimers();
        }
    }

    /**
     * Main sync logic: upload local -> download remote -> merge.
     */
    private void syncTimers() {
        if (syncing) {
            return; // Avoid concurrent syncs
        }
        syncing = true;
        
        try {
            String profile = getProfile();
            if (profile == null || profile.isEmpty()) {
                return;
            }
            
            LocalizedResourceTimerService timerService = gui.getLocalizedResourceTimerService();
            if (timerService == null) {
                return;
            }

            timerService.reloadFromDisk();
            
            // Step 1: Upload local timers to DB
            uploadLocalTimers(profile, timerService);
            
            // Step 2: Clean up expired timers in DB
            int deleted = localTimerService.deleteExpired(profile);
            if (deleted > 0) {
                System.out.println("LocalTimerSyncService: Deleted " + deleted + " expired timers from DB");
            }
            
            // Step 3: Download timers from DB and merge
            downloadAndMerge(profile, timerService);
            
        } catch (Exception e) {
            System.err.println("LocalTimerSyncService: Sync failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            syncing = false;
        }
    }

    /**
     * Upload all local non-expired timers to the database.
     */
    private void uploadLocalTimers(String profile, LocalizedResourceTimerService timerService) {
        java.util.Collection<LocalizedResourceTimer> localTimers = timerService.getAllTimers();
        int uploaded = 0;
        
        for (LocalizedResourceTimer timer : localTimers) {
            if (timer.isEphemeral() || timer.isExpired()) {
                continue;
            }
            
            localTimerService.upsert(
                profile,
                timer.getResourceId(),
                timer.getSegmentId(),
                timer.getTileCoords().x,
                timer.getTileCoords().y,
                timer.getResourceName(),
                timer.getResourceType(),
                timer.getStartTime(),
                timer.getDuration(),
                timer.getDescription()
            );
            uploaded++;
        }
        
        if (uploaded > 0) {
            System.out.println("LocalTimerSyncService: Uploaded " + uploaded + " timers to DB");
        }
    }

    /**
     * Download timers from DB and merge into local service.
     */
    private void downloadAndMerge(String profile, LocalizedResourceTimerService timerService) {
        List<LocalTimerDao.LocalTimerData> dbTimers = localTimerService.findAllByProfile(profile);
        if (dbTimers.isEmpty()) {
            return;
        }
        
        int merged = 0;
        for (LocalTimerDao.LocalTimerData dbTimer : dbTimers) {
            // Check if we already have this timer locally with same or newer start time
            LocalizedResourceTimer existing = timerService.getTimer(dbTimer.getResourceId());
            
            if (existing != null && existing.isEphemeral()) {
                continue;
            }

            if (existing == null) {
                // Timer doesn't exist locally - add it
                timerService.addTimerFromDb(
                    dbTimer.getResourceId(),
                    dbTimer.getSegmentId(),
                    new haven.Coord(dbTimer.getTileX(), dbTimer.getTileY()),
                    dbTimer.getResourceName(),
                    dbTimer.getResourceType(),
                    dbTimer.getStartTimeUtc(),
                    dbTimer.getDurationMs(),
                    dbTimer.getDescription()
                );
                merged++;
            } else if (dbTimer.getStartTimeUtc() > existing.getStartTime()) {
                // DB has newer timer - update local
                timerService.updateTimerFromDb(
                    dbTimer.getResourceId(),
                    dbTimer.getStartTimeUtc(),
                    dbTimer.getDurationMs(),
                    dbTimer.getDescription()
                );
                merged++;
            }
        }
        
        if (merged > 0) {
            System.out.println("LocalTimerSyncService: Merged " + merged + " timers from DB");
            // Refresh UI if timer window is open
            timerService.refreshTimerWindowFromSync();
        }
    }

    /**
     * Get current profile (world identifier).
     */
    private String getProfile() {
        if (gui == null) {
            return null;
        }
        return gui.getGenus();
    }
}
