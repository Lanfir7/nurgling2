package nurgling.render;

import haven.Coord;
import haven.Loading;
import haven.PView;
import haven.TexL;
import haven.render.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

class GroundReliefAsyncTest {
    private final List<PendingTexture> textures = new ArrayList<>();

    private class PendingTexture extends TexL {
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger fills = new AtomicInteger();

        PendingTexture() { super(new Coord(2, 2)); textures.add(this); }

        @Override
        public String loadname() { return "gfx/terobjs/stonecolumn"; }

        @Override
        public BufferedImage fill() {
            fills.incrementAndGet();
            try {
                if (!release.await(5, TimeUnit.SECONDS))
                    throw new AssertionError("Test did not release relief preparation");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 0xff000000);
            image.setRGB(1, 0, 0xffffffff);
            image.setRGB(0, 1, 0xffffffff);
            image.setRGB(1, 1, 0xff000000);
            return image;
        }
    }

    @AfterEach
    void finishOwnBackgroundWork() {
        Object flat = GroundRelief.heightfor(null, false).map;
        for (PendingTexture texture : textures) {
            texture.release.countDown();
            GroundRelief.heightfor(texture.draw, false);
            awaitRelief(texture, flat);
        }
    }

    private static GroundRelief.CacheState awaitRelief(PendingTexture texture, Object flat) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        GroundRelief.CacheState state;
        do {
            state = GroundRelief.poll();
            if (GroundRelief.heightfor(texture.draw, false).map != flat)
                return state;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        } while (System.nanoTime() < deadline);
        fail("Prepared relief was not published");
        return null;
    }

    @Test
    void finishedReliefPublishesOneInvalidationAndRemainsCached() {
        PendingTexture texture = new PendingTexture();
        try {
            GroundRelief.CacheState before = GroundRelief.poll();
            GroundRelief.Relief flat = GroundRelief.heightfor(texture.draw, false);
            assertSame(before, GroundRelief.poll(), "Pending work must not invalidate every frame");
            texture.release.countDown();
            GroundRelief.CacheState after = awaitRelief(texture, flat.map);
            assertNotSame(before, after, "The renderer must be told that a fallback became ready");
            GroundRelief.Relief ready = GroundRelief.heightfor(texture.draw, false);
            assertNotSame(flat.map, ready.map);
            assertTrue(ready.scale > 0);
            assertSame(ready, GroundRelief.heightfor(texture.draw, true));
            assertSame(after, GroundRelief.poll(), "Already published work must not invalidate again");
            assertEquals(1, texture.fills.get(), "Repeated reads must not restart completed preparation");
        } finally {
            texture.release.countDown();
        }
    }

    private static class UniformConsumer implements RenderList<Rendered> {
        Slot<? extends Rendered> draw;
        Object map;
        float scale;

        private void sample() {
            map = GroundRelief.uoheight.value.apply(draw.state());
            scale = (Float) GroundRelief.uoscale.value.apply(draw.state());
        }

        public void add(Slot<? extends Rendered> slot) { draw = slot; sample(); }
        public void remove(Slot<? extends Rendered> slot) { draw = null; }
        public void update(Slot<? extends Rendered> slot) { draw = slot; sample(); }
        public void update(Pipe group, int[] mask) {
            for (int changed : mask) {
                for (State.Slot<?> dependency : GroundRelief.uoheight.deps) {
                    if (dependency.id == changed) { sample(); return; }
                }
            }
        }
    }

    @Test
    void cachePublicationUpdatesExistingRenderTreeUniformsWithoutChangingTexture() {
        PendingTexture texture = new PendingTexture();
        try {
            RenderTree tree = new RenderTree();
            UniformConsumer consumer = new UniformConsumer();
            tree.add(consumer, Rendered.class);
            RenderTree.Slot root = tree.add((RenderTree.Node) null);
            GroundRelief.CacheState before = GroundRelief.poll();
            root.ostate(Pipe.Op.compose(texture.draw, before));
            root.add((Rendered & RenderTree.Node) (state, out) -> {});
            Object initial = consumer.map;
            assertEquals(0f, consumer.scale);
            texture.release.countDown();
            GroundRelief.CacheState after = awaitRelief(texture, initial);
            assertSame(initial, consumer.map, "Cached uniforms still contain the original fallback");
            assertDoesNotThrow(() -> root.ostate(Pipe.Op.compose(texture.draw, after)));
            assertNotSame(initial, consumer.map, "A cache-only state change must refresh existing uniforms");
            assertEquals(GroundRelief.heightfor(texture.draw, true).scale, consumer.scale);
            assertTrue(consumer.scale > 0);
        } finally {
            texture.release.countDown();
        }
    }

    @Test
    void every3dViewRefreshesItsCachedReliefAfterAnotherViewPublishesIt() {
        PendingTexture texture = new PendingTexture();
        PView first = new PView(new Coord(16, 16)) {};
        PView second = new PView(new Coord(16, 16)) {};
        try {
            UniformConsumer firstConsumer = new UniformConsumer();
            UniformConsumer secondConsumer = new UniformConsumer();
            first.tree.add(firstConsumer, Rendered.class);
            second.tree.add(secondConsumer, Rendered.class);
            RenderTree.Slot firstNode = first.basic.add((Rendered & RenderTree.Node) (state, out) -> {});
            RenderTree.Slot secondNode = second.basic.add((Rendered & RenderTree.Node) (state, out) -> {});
            firstNode.ostate(texture.draw);
            secondNode.ostate(texture.draw);
            first.tick(0);
            second.tick(0);
            Object initial = firstConsumer.map;
            assertSame(initial, secondConsumer.map);
            texture.release.countDown();
            awaitRelief(texture, initial);
            first.tick(0);
            second.tick(0);
            assertNotSame(initial, firstConsumer.map, "A standalone 3D view must update its cached fallback");
            assertNotSame(initial, secondConsumer.map, "Every view must observe already-published work");
            assertEquals(GroundRelief.heightfor(texture.draw, true).scale, firstConsumer.scale);
            assertEquals(firstConsumer.scale, secondConsumer.scale);
        } finally {
            texture.release.countDown();
            first.dispose();
            second.dispose();
        }
    }

    @Test
    void resourceLoadingIsRetriedWithoutEscapingUniformCallbacks() {
        CountDownLatch loadingSeen = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        PendingTexture texture = new PendingTexture() {
            public BufferedImage fill() {
                if (attempts.incrementAndGet() == 1) {
                    loadingSeen.countDown();
                    throw new Loading("Test texture is still loading");
                }
                return super.fill();
            }
        };
        try {
            Object initial = assertDoesNotThrow(() -> GroundRelief.heightfor(texture.draw, true)).map;
            assertTrue(assertDoesNotThrow(() -> loadingSeen.await(5, TimeUnit.SECONDS)));
            assertDoesNotThrow(GroundRelief::poll);
            assertSame(initial, assertDoesNotThrow(() -> GroundRelief.heightfor(texture.draw, true)).map);
            texture.release.countDown();
            awaitRelief(texture, initial);
            assertTrue(attempts.get() >= 2);
        } finally {
            texture.release.countDown();
        }
    }

    @Test
    void terrainUsesFlatReliefWhileBackgroundPreparationIsPending() {
        PendingTexture texture = new PendingTexture();
        try {
            GroundRelief.Relief pending = assertDoesNotThrow(() -> GroundRelief.heightfor(texture.draw, false));
            assertNotNull(pending.map);
            assertEquals(0f, pending.scale);
            assertSame(pending, assertDoesNotThrow(() -> GroundRelief.heightfor(texture.draw, false)));
        } finally {
            texture.release.countDown();
        }
    }

    @Test
    void objectUniformsNeverThrowLoadingDuringRenderStateUpdate() {
        PendingTexture texture = new PendingTexture();
        try {
            BufPipe pipe = new BufPipe();
            texture.draw.apply(pipe);
            Object firstMap = assertDoesNotThrow(() -> GroundRelief.uoheight.value.apply(pipe));
            assertNotNull(firstMap);
            assertEquals(0f, assertDoesNotThrow(() -> GroundRelief.uoscale.value.apply(pipe)));
            assertSame(firstMap, assertDoesNotThrow(() -> GroundRelief.uoheight.value.apply(pipe)));
        } finally {
            texture.release.countDown();
        }
    }
}
