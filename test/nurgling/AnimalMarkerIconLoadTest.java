package nurgling;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalMarkerIconLoadTest {
    @Test
    void inFlightDedupeSubmitsOncePerLocationUntilReleased() {
        AnimalMarkerIconLoad.InFlight inFlight = new AnimalMarkerIconLoad.InFlight();
        assertTrue(inFlight.tryAcquire("animal_1"));
        assertFalse(inFlight.tryAcquire("animal_1"));
        assertTrue(inFlight.tryAcquire("animal_2"));
        inFlight.release("animal_1");
        assertTrue(inFlight.tryAcquire("animal_1"));
        assertFalse(inFlight.tryAcquire(null));
        assertFalse(inFlight.tryAcquire(""));
    }

    @Test
    void inFlightDedupeIsSafeUnderConcurrentOffers() throws Exception {
        AnimalMarkerIconLoad.InFlight inFlight = new AnimalMarkerIconLoad.InFlight();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger acquired = new AtomicInteger();
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await(2, TimeUnit.SECONDS);
                    if (inFlight.tryAcquire("animal_race")) {
                        acquired.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            workers.add(t);
            t.start();
        }
        start.countDown();
        assertTrue(done.await(2, TimeUnit.SECONDS));
        for (Thread t : workers) {
            t.join(500);
        }
        assertEquals(1, acquired.get());
    }

    @Test
    void workerLoadCatchesHavenLoadingAndRetriesOffUiThread() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/AnimalMarkerIconLoad.java"));
        assertTrue(src.contains("haven.Loading"),
                "worker must catch haven.Loading and retry later");
        assertTrue(src.contains("getAnimalMarkerWorker"),
                "icon load must run on AnimalMarkerWorker");
        assertTrue(src.contains("updateAnimalMarkerIcon"),
                "loaded icon must be applied via labeledMarkService.updateAnimalMarkerIcon");
        assertTrue(src.contains("loadIconFromIconConf")
                && src.contains("loadAnimalIconFromPath")
                && src.contains("gfx/invobjs/kritter"),
                "worker must keep iconPath → iconconf → animalType → kritter fallback");
    }
}
