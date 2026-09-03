package nurgling;

import haven.Resource;
import nurgling.actions.ObjectTracker;
import nurgling.widgets.LabeledMinimapMark;

import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Loads animal-marker icons on {@code AnimalMarkerWorker} instead of the minimap draw thread.
 * In-flight jobs are deduped per {@link NGameUI} by locationId so a missing icon is not retried every frame.
 */
public final class AnimalMarkerIconLoad {
    private static final long RETRY_DELAY_MS = 400;

    public static final class InFlight {
        private final Set<String> ids = ConcurrentHashMap.newKeySet();

        public boolean tryAcquire(String locationId) {
            if (locationId == null || locationId.isEmpty()) {
                return false;
            }
            return ids.add(locationId);
        }

        public void release(String locationId) {
            if (locationId != null) {
                ids.remove(locationId);
            }
        }
    }

    private static final ScheduledExecutorService RETRIES = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AnimalMarkerIconRetry");
        t.setDaemon(true);
        return t;
    });

    private AnimalMarkerIconLoad() {}

    public static void enqueue(NGameUI gui, LabeledMinimapMark mark) {
        if (gui == null || mark == null) {
            return;
        }
        String locationId = mark.getLocationId();
        String iconPath = mark.iconPath;
        String animalType = mark.animalType;
        String displayName = mark.resourceType;
        enqueue(gui, locationId, () -> loadIcon(gui, iconPath, animalType, displayName));
    }

    public static void enqueue(NGameUI gui, String locationId, Supplier<BufferedImage> loader) {
        if (!tryAcquire(gui, locationId) || loader == null) {
            return;
        }
        submitAcquired(gui, locationId, loader);
    }

    /**
     * Reserve {@code locationId} before the local mark is visible so draw cannot steal the job.
     * Call {@link #submitAcquired} after {@code addAnimalMarkerLocal}.
     */
    public static boolean tryAcquire(NGameUI gui, String locationId) {
        if (gui == null || locationId == null || !locationId.startsWith("animal_")) {
            return false;
        }
        return gui.animalMarkerIconJobs.tryAcquire(locationId);
    }

    public static void submitAcquired(NGameUI gui, String locationId, Supplier<BufferedImage> loader) {
        if (gui == null || locationId == null || loader == null) {
            return;
        }
        submit(gui, locationId, loader);
    }

    /** iconPath → iconconf → animalType path → gfx/invobjs/kritter fallback. */
    public static BufferedImage loadIcon(NGameUI gui, String iconPath, String animalType, String displayName) {
        BufferedImage icon = null;
        if (iconPath != null && !iconPath.isEmpty()) {
            icon = ObjectTracker.loadIconFromResourcePath(iconPath);
        }
        if (icon == null && animalType != null && gui != null) {
            icon = ObjectTracker.loadIconFromIconConf(animalType, gui);
        }
        if (icon == null && animalType != null) {
            icon = ObjectTracker.loadAnimalIconFromPath(animalType, displayName, gui);
        }
        if (icon == null) {
            try {
                icon = Resource.loadsimg("gfx/invobjs/kritter");
            } catch (haven.Loading e) {
                throw e;
            } catch (Exception ignored) {
            }
        }
        return icon;
    }

    private static void submit(NGameUI gui, String locationId, Supplier<BufferedImage> loader) {
        try {
            ExecutorService worker = gui.getAnimalMarkerWorker();
            if (worker == null || worker.isShutdown()) {
                gui.animalMarkerIconJobs.release(locationId);
                return;
            }
            worker.submit(() -> run(gui, locationId, loader));
        } catch (RejectedExecutionException e) {
            gui.animalMarkerIconJobs.release(locationId);
        }
    }

    private static void run(NGameUI gui, String locationId, Supplier<BufferedImage> loader) {
        InFlight jobs = gui.animalMarkerIconJobs;
        try {
            if (gui.labeledMarkService == null) {
                jobs.release(locationId);
                return;
            }
            BufferedImage icon = loader.get();
            if (icon != null) {
                gui.labeledMarkService.updateAnimalMarkerIcon(locationId, icon);
                jobs.release(locationId);
                return;
            }
            /* Leave in-flight on null so draw does not re-submit every frame. */
        } catch (haven.Loading e) {
            RETRIES.schedule(() -> submit(gui, locationId, loader), RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (RuntimeException ignored) {
            jobs.release(locationId);
        }
    }
}
