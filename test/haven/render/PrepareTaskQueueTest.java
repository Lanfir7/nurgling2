package haven.render;

import haven.Loading;
import haven.Waitable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PrepareTaskQueueTest {
    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "worker did not reach latch");
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test void submissionAndPollingNeverRunOrWaitForTheSupplier() {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try(PrepareTaskQueue queue = new PrepareTaskQueue("test-preparation", 1)) {
            Thread caller = Thread.currentThread();
            PrepareTaskQueue.Job<Thread> job;
            try {
                job = assertTimeoutPreemptively(Duration.ofSeconds(5),
                        () -> queue.submit(() -> {
                            entered.countDown();
                            await(release);
                            return Thread.currentThread();
                        }, 0));
                await(entered);
                assertThrows(Loading.class, job::get);
            } finally {
                release.countDown();
            }
            Thread worker = Loading.waitfor(job);
            assertNotSame(caller, worker);
            assertTrue(worker.isDaemon());
            assertEquals("test-preparation", worker.getName());
        }
    }

    @Test void capacityIncludesRunningAndQueuedJobsThenReopensOnCompletion() {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try(PrepareTaskQueue queue = new PrepareTaskQueue("capacity", 2)) {
            PrepareTaskQueue.Job<Integer> first = queue.submit(() -> {
                entered.countDown();
                await(release);
                return 1;
            }, 0);
            await(entered);
            PrepareTaskQueue.Job<Integer> second = queue.submit(() -> 2, 0);
            assertNotNull(first);
            assertNotNull(second);
            assertNull(queue.submit(() -> 3, 0));
            CountDownLatch secondCompleted = new CountDownLatch(1);
            assertThrows(Loading.class, second::get)
                    .waitfor(secondCompleted::countDown, wait -> {});
            release.countDown();
            assertEquals(Integer.valueOf(1), Loading.waitfor(first));
            assertEquals(Integer.valueOf(2), Loading.waitfor(second));
            await(secondCompleted);
            assertEquals(Integer.valueOf(4), Loading.waitfor(queue.submit(() -> 4, 0)));
        }
    }

    @Test void queuedWorkUsesLowerPriorityFirstAndFifoForEqualPriority() {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        List<String> order = new ArrayList<>();
        try(PrepareTaskQueue queue = new PrepareTaskQueue("priority", 5)) {
            PrepareTaskQueue.Job<?> gate = queue.submit(() -> {
                entered.countDown();
                await(release);
                return null;
            }, 0);
            await(entered);
            PrepareTaskQueue.Job<?> low = queue.submit(() -> { order.add("low"); return null; }, 20);
            PrepareTaskQueue.Job<?> high1 = queue.submit(() -> { order.add("high1"); return null; }, -1);
            PrepareTaskQueue.Job<?> mid = queue.submit(() -> { order.add("mid"); return null; }, 5);
            PrepareTaskQueue.Job<?> high2 = queue.submit(() -> { order.add("high2"); return null; }, -1);
            release.countDown();
            Loading.waitfor(gate);
            Loading.waitfor(low);
            Loading.waitfor(high1);
            Loading.waitfor(mid);
            Loading.waitfor(high2);
            assertEquals(Arrays.asList("high1", "high2", "mid", "low"), order);
        }
    }

    @Test void pendingLoadingSignalsRegisteredWaiterOnceAndHonorsCancellation() {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try(PrepareTaskQueue queue = new PrepareTaskQueue("wait", 1)) {
            PrepareTaskQueue.Job<Integer> job = queue.submit(() -> {
                entered.countDown();
                await(release);
                return 8;
            }, 0);
            await(entered);
            Loading pending = assertThrows(Loading.class, job::get);
            AtomicInteger called = new AtomicInteger();
            CountDownLatch fired = new CountDownLatch(1);
            AtomicReference<Waitable.Waiting> cancelled = new AtomicReference<>();
            pending.waitfor(called::incrementAndGet, cancelled::set);
            cancelled.get().cancel();
            AtomicReference<Waitable.Waiting> active = new AtomicReference<>();
            pending.waitfor(() -> { called.incrementAndGet(); fired.countDown(); }, active::set);
            release.countDown();
            assertEquals(Integer.valueOf(8), Loading.waitfor(job));
            await(fired);
            assertEquals(1, called.get());
            active.get().cancel();
            AtomicInteger late = new AtomicInteger();
            pending.waitfor(late::incrementAndGet, wait -> wait.cancel());
            assertEquals(1, late.get());
        }
    }

    @Test void completionDuringRegistrationRunsCallbackAfterRegistration() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        CountDownLatch registering = new CountDownLatch(1), allowRegistration = new CountDownLatch(1);
        try(PrepareTaskQueue queue = new PrepareTaskQueue("race", 1)) {
            PrepareTaskQueue.Job<Integer> job = queue.submit(() -> {
                entered.countDown();
                await(release);
                return 9;
            }, 0);
            await(entered);
            Loading pending = assertThrows(Loading.class, job::get);
            AtomicInteger called = new AtomicInteger();
            FutureTask<Void> registration = new FutureTask<>(() -> {
                pending.waitfor(() -> {
                    assertEquals(0, registering.getCount());
                    assertEquals(0, allowRegistration.getCount());
                    called.incrementAndGet();
                }, wait -> {
                    registering.countDown();
                    await(allowRegistration);
                });
                return null;
            });
            new Thread(registration, "registration").start();
            await(registering);
            release.countDown();
            assertEquals(Integer.valueOf(9), Loading.waitfor(job));
            allowRegistration.countDown();
            registration.get(5, TimeUnit.SECONDS);
            assertEquals(1, called.get());
        }
    }

    @Test void supplierFailuresReachGetWithoutWrappingAndDoNotStopWorker() {
        try(PrepareTaskQueue queue = new PrepareTaskQueue("failure", 3)) {
            IllegalArgumentException problem = new IllegalArgumentException("bad shader");
            PrepareTaskQueue.Job<?> failed = queue.submit(() -> { throw problem; }, 0);
            assertSame(problem, assertThrows(IllegalArgumentException.class,
                    () -> Loading.waitfor(failed)));
            AssertionError fatal = new AssertionError("bad pipeline");
            PrepareTaskQueue.Job<?> errored = queue.submit(() -> { throw fatal; }, 0);
            assertSame(fatal, assertThrows(AssertionError.class,
                    () -> Loading.waitfor(errored)));
            assertEquals(Integer.valueOf(3), Loading.waitfor(queue.submit(() -> 3, 0)));
        }
    }

    @Test void completionCallbackCanSubmitAfterCapacityIsReleased() {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try(PrepareTaskQueue queue = new PrepareTaskQueue("callback", 1)) {
            PrepareTaskQueue.Job<Integer> first = queue.submit(() -> {
                entered.countDown();
                await(release);
                return 1;
            }, 0);
            await(entered);
            AtomicReference<PrepareTaskQueue.Job<Integer>> second = new AtomicReference<>();
            CountDownLatch submitted = new CountDownLatch(1);
            Loading pending = assertThrows(Loading.class, first::get);
            pending.waitfor(() -> {
                second.set(queue.submit(() -> 2, 0));
                submitted.countDown();
            }, wait -> {});
            release.countDown();
            assertEquals(Integer.valueOf(1), Loading.waitfor(first));
            await(submitted);
            assertNotNull(second.get());
            assertEquals(Integer.valueOf(2), Loading.waitfor(second.get()));
        }
    }

    @Test void closeRejectsNewWorkAndDrainsRunningAndQueuedWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        PrepareTaskQueue queue = new PrepareTaskQueue("shutdown", 2);
        PrepareTaskQueue.Job<Integer> first = queue.submit(() -> {
            entered.countDown();
            await(release);
            return 1;
        }, 0);
        await(entered);
        PrepareTaskQueue.Job<Integer> second = queue.submit(() -> 2, 0);
        FutureTask<Void> closing = new FutureTask<>(() -> { queue.close(); return null; });
        Thread closer = new Thread(closing, "closer");
        closer.start();
        try {
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> closing.get(100, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
        }
        closing.get(5, TimeUnit.SECONDS);
        assertNull(queue.submit(() -> 3, 0));
        assertEquals(Integer.valueOf(1), first.get());
        assertEquals(Integer.valueOf(2), second.get());
    }

    @Test void constructorRejectsUnusableCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new PrepareTaskQueue("invalid", 0));
    }

    @Test void interruptedCloseStillDrainsAndRestoresInterruptStatus() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        PrepareTaskQueue queue = new PrepareTaskQueue("interrupted-close", 1);
        PrepareTaskQueue.Job<Integer> job = queue.submit(() -> {
            entered.countDown();
            await(release);
            return 1;
        }, 0);
        await(entered);
        FutureTask<Boolean> closing = new FutureTask<>(() -> {
            Thread.currentThread().interrupt();
            queue.close();
            return Thread.currentThread().isInterrupted();
        });
        new Thread(closing, "interrupted-closer").start();
        try {
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> closing.get(100, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
        }
        assertTrue(closing.get(5, TimeUnit.SECONDS));
        assertEquals(Integer.valueOf(1), job.get());
    }

    @Test void closeRacingAdmissionsDrainsEveryAcceptedJob() throws Exception {
        CountDownLatch workerEntered = new CountDownLatch(1), releaseWorker = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(17), start = new CountDownLatch(1);
        PrepareTaskQueue queue = new PrepareTaskQueue("admission-race", 32);
        PrepareTaskQueue.Job<Integer> first = queue.submit(() -> {
            workerEntered.countDown();
            await(releaseWorker);
            return 0;
        }, 0);
        await(workerEntered);
        AtomicInteger executed = new AtomicInteger();
        List<FutureTask<PrepareTaskQueue.Job<Integer>>> submissions = new ArrayList<>();
        for(int i = 0; i < 16; i++) {
            final int value = i + 1;
            FutureTask<PrepareTaskQueue.Job<Integer>> submission = new FutureTask<>(() -> {
                ready.countDown();
                await(start);
                return queue.submit(() -> { executed.incrementAndGet(); return value; }, 0);
            });
            submissions.add(submission);
            new Thread(submission, "admission-" + value).start();
        }
        FutureTask<Void> closing = new FutureTask<>(() -> {
            ready.countDown();
            await(start);
            queue.close();
            return null;
        });
        new Thread(closing, "racing-closer").start();
        await(ready);
        start.countDown();
        List<PrepareTaskQueue.Job<Integer>> accepted = new ArrayList<>();
        try {
            for(FutureTask<PrepareTaskQueue.Job<Integer>> submission : submissions) {
                PrepareTaskQueue.Job<Integer> job = submission.get(5, TimeUnit.SECONDS);
                if(job != null)
                    accepted.add(job);
            }
        } finally {
            releaseWorker.countDown();
        }
        closing.get(5, TimeUnit.SECONDS);
        assertEquals(Integer.valueOf(0), first.get());
        for(PrepareTaskQueue.Job<Integer> job : accepted)
            assertNotNull(job.get());
        assertEquals(accepted.size(), executed.get());
        assertNull(queue.submit(() -> 99, 0));
    }
}
