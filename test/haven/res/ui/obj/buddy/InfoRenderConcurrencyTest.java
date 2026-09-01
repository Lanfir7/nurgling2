package haven.res.ui.obj.buddy;

import haven.Coord;
import haven.GOut;
import haven.Tex;
import haven.render.Pipe;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfoRenderConcurrencyTest {
    @Test
    void dirtyDoesNotDisposeTextureWhileDrawUsesIt() throws Exception {
        Info info = new Info(null);
        GuardedTex texture = new GuardedTex();
        Field rend = Info.class.getDeclaredField("rend");
        rend.setAccessible(true);
        rend.set(info, texture);

        BlockingGOut out = new BlockingGOut();
        AtomicReference<Throwable> drawFailure = new AtomicReference<>();
        CountDownLatch invalidationStarted = new CountDownLatch(1);
        Thread draw = new Thread(() -> {
            try {
                info.draw(out, Pipe.nil);
            } catch(Throwable t) {
                drawFailure.set(t);
            }
        });
        Thread invalidation = new Thread(() -> {
            invalidationStarted.countDown();
            info.dirty();
        });
        draw.start();
        try {
            assertTrue(out.drawStarted.await(5, TimeUnit.SECONDS));
            invalidation.start();
            assertTrue(invalidationStarted.await(5, TimeUnit.SECONDS));
            assertEquals(Thread.State.BLOCKED, awaitBlockedOrTerminated(invalidation),
                    "dirty did not wait for the active draw");
            assertFalse(texture.disposed);
        } finally {
            out.allowDraw.countDown();
            draw.join(5000);
            invalidation.join(5000);
        }

        assertFalse(draw.isAlive());
        assertFalse(invalidation.isAlive());
        assertNull(drawFailure.get());
        assertTrue(texture.disposed);
    }

    private static Thread.State awaitBlockedOrTerminated(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Thread.State state;
        do {
            state = thread.getState();
            if((state == Thread.State.BLOCKED) || (state == Thread.State.TERMINATED))
                return state;
            Thread.sleep(1);
        } while(System.nanoTime() < deadline);
        return state;
    }

    private static class BlockingGOut extends GOut {
        final CountDownLatch drawStarted = new CountDownLatch(1);
        final CountDownLatch allowDraw = new CountDownLatch(1);

        BlockingGOut() {
            super(null, Pipe.nil, Coord.of(100));
        }

        @Override
        public void aimage(Tex tex, Coord c, double ax, double ay) {
            drawStarted.countDown();
            try {
                assertTrue(allowDraw.await(10, TimeUnit.SECONDS));
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            super.aimage(tex, c, ax, ay);
        }

        @Override
        public void chcolor(Color color) {
        }

        @Override
        public void chcolor() {
        }
    }

    private static class GuardedTex implements Tex {
        volatile boolean disposed;

        @Override
        public Coord sz() {
            if(disposed)
                throw new IllegalStateException("texture was disposed during draw");
            return Coord.of(10);
        }

        @Override
        public void render(GOut g, float[] gc, float[] tc) {
            if(disposed)
                throw new IllegalStateException("texture was disposed during draw");
        }

        @Override
        public void dispose() {
            disposed = true;
        }
    }
}
