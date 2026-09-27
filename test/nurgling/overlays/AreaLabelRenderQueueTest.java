package nurgling.overlays;

import haven.Coord;
import haven.ItemInfo;
import haven.PUtils;
import haven.TexI;
import haven.Text;
import haven.UI;
import nurgling.areas.NArea;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AreaLabelRenderQueueTest {
    @Test
    void lastSlotRemovalReleasesTexturesButReadditionCanRenderAgain() {
        NAreaLabel label = new NAreaLabel(null, new NArea("Storage"));
        label.added(null);
        label.added(null);
        TexI texture = new TexI(TexI.mkbuf(new Coord(2, 2)));
        label.label = texture;
        label.requestNeeded = false;

        label.removed(null);
        assertSame(texture, label.label, "one remaining slot still uses the texture");
        label.removed(null);
        assertNull(label.label);
        assertTrue(label.requestNeeded);
        assertFalse(label.disposed, "render-tree detach can be temporary");

        label.added(null);
        label.requestNeeded = false;
        label.update();
        assertTrue(label.requestNeeded, "a reattached label accepts another request");
        label.removed(null);
    }

    @Test
    void finalDisposalCannotReviveOnUpdateOrReaddition() {
        NAreaLabel label = new NAreaLabel(null, new NArea("Storage"));
        label.added(null);
        label.label = new TexI(TexI.mkbuf(new Coord(2, 2)));
        label.dispose();
        assertTrue(label.disposed);
        assertNull(label.label);
        label.requestNeeded = false;
        label.update();
        label.added(null);
        assertFalse(label.requestNeeded);
        label.removed(null);
        assertNull(label.label);
    }

    @Test
    void pendingRequestCoalescesAndChangedRequestDiscardsOldPixels() throws Exception {
        AreaLabelRenderQueue.Mailbox<String> box = new AreaLabelRenderQueue.Mailbox<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger rendered = new AtomicInteger();
        assertTrue(box.submit(() -> {
            rendered.incrementAndGet();
            entered.countDown();
            await(release);
            return "old name";
        }, System.nanoTime()));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertFalse(box.submit(() -> "duplicate", System.nanoTime()));
        box.invalidate();
        release.countDown();
        waitUntilIdle(box);
        assertNull(box.take(), "old generation must not appear after rename");
        assertTrue(box.submit(() -> {
            rendered.incrementAndGet();
            return "new name";
        }, System.nanoTime()));
        assertEquals("new name", waitForResult(box));
        assertEquals(2, rendered.get());
    }

    @Test
    void closeDiscardsInflightResultAndCannotRevive() throws Exception {
        AreaLabelRenderQueue.Mailbox<String> box = new AreaLabelRenderQueue.Mailbox<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        assertTrue(box.submit(() -> {
            entered.countDown();
            await(release);
            return "orphaned";
        }, System.nanoTime()));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        box.close();
        release.countDown();
        waitUntilIdle(box);
        assertNull(box.take());
        assertFalse(box.submit(() -> "revived", System.nanoTime()));
    }

    @Test
    void fixedQueueRejectsExcessWorkAndCanRetryLater() throws Exception {
        AreaLabelRenderQueue.Mailbox<String> running = new AreaLabelRenderQueue.Mailbox<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        assertTrue(running.submit(() -> {
            entered.countDown();
            await(release);
            return "running";
        }, System.nanoTime()));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        AreaLabelRenderQueue.Mailbox<String>[] queued = new AreaLabelRenderQueue.Mailbox[64];
        CountDownLatch drained = new CountDownLatch(64);
        try {
            for (int i = 0; i < queued.length; i++) {
                queued[i] = new AreaLabelRenderQueue.Mailbox<>();
                assertTrue(queued[i].submit(() -> {
                    drained.countDown();
                    return "queued";
                }, System.nanoTime()));
            }
            AreaLabelRenderQueue.Mailbox<String> excess = new AreaLabelRenderQueue.Mailbox<>();
            assertFalse(excess.submit(() -> "excess", System.nanoTime()));
        } finally {
            release.countDown();
        }
        assertTrue(drained.await(5, TimeUnit.SECONDS));
        assertEquals("running", waitForResult(running));
    }

    @Test
    void workerFailureDoesNotPublishPixelsOrRetryEveryFrame() throws Exception {
        AreaLabelRenderQueue.Mailbox<String> box = new AreaLabelRenderQueue.Mailbox<>();
        CountDownLatch attempted = new CountDownLatch(1);
        assertTrue(box.submit(() -> {
            attempted.countDown();
            throw new IllegalStateException("fixture failure");
        }, System.nanoTime()));
        assertTrue(attempted.await(5, TimeUnit.SECONDS));
        waitUntilIdle(box);
        assertNull(box.take());
        long retryAt = box.retryAt();
        assertNotEquals(0, retryAt, "failed work must schedule a retry");
        assertFalse(box.canSubmit(retryAt - 1));
        assertTrue(box.canSubmit(retryAt));
    }

    @Test
    void rasterizedNameQualityAndIconsMatchPreviousPixels() {
        BufferedImage icon = TexI.mkbuf(new Coord(8, 8));
        Graphics iconGraphics = icon.getGraphics();
        iconGraphics.setColor(Color.ORANGE);
        iconGraphics.fillRect(0, 0, 8, 8);
        iconGraphics.dispose();
        Font qualityFont = Text.sans.deriveFont(Font.BOLD, UI.scale(11))
                .deriveFont(UI.scale(11f));
        NAreaLabel.Request request = new NAreaLabel.Request("Storage", "Q42", List.of(icon),
                new NAreaLabel.Style[] {
                        NAreaLabel.snapshotStyle(nurgling.NStyle.openings),
                        NAreaLabel.snapshotStyle(nurgling.NStyle.selopenings),
                        NAreaLabel.snapshotStyle(nurgling.NStyle.disabledopenings)
                }, qualityFont, UI.scale(32), UI.scale(5), UI.scale(1));
        NAreaLabel.Images actual = NAreaLabel.rasterize(request);
        BufferedImage[] expected = {
                previous(nurgling.NStyle.openings, "Storage", "Q42", Color.WHITE, icon),
                previous(nurgling.NStyle.selopenings, "Storage", "Q42", Color.GREEN, icon),
                previous(nurgling.NStyle.disabledopenings, "Storage", "Q42", Color.GRAY, icon)
        };
        assertSamePixels(expected[0], actual.normal);
        assertSamePixels(expected[1], actual.selected);
        assertSamePixels(expected[2], actual.disabled);
    }

    private static BufferedImage previous(Text.Furnace titleStyle, String title, String quality,
                                          Color color, BufferedImage icon) {
        BufferedImage titleImage = titleStyle.render(title).img;
        BufferedImage qualityImage = new PUtils.BlurFurn(
                new Text.Foundry(Text.sans.deriveFont(Font.BOLD, UI.scale(11)), 11, color).aa(true),
                1, 1, new Color(60, 30, 30)).render(quality).img;
        int width = Math.max(titleImage.getWidth(), qualityImage.getWidth());
        BufferedImage stacked = TexI.mkbuf(new Coord(width,
                titleImage.getHeight() + UI.scale(1) + qualityImage.getHeight()));
        Graphics2D graphics = stacked.createGraphics();
        graphics.drawImage(titleImage, (width - titleImage.getWidth()) / 2, 0, null);
        graphics.drawImage(qualityImage, (width - qualityImage.getWidth()) / 2,
                titleImage.getHeight() + UI.scale(1), null);
        graphics.dispose();
        BufferedImage scaledIcon = TexI.mkbuf(new Coord(UI.scale(32), UI.scale(32)));
        Graphics iconGraphics = scaledIcon.getGraphics();
        iconGraphics.drawImage(icon, 0, 0, UI.scale(32), UI.scale(32), null);
        iconGraphics.dispose();
        return ItemInfo.catimgsh(UI.scale(5), stacked, scaledIcon);
    }

    private static void assertSamePixels(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++)
            for (int x = 0; x < expected.getWidth(); x++)
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), x + "," + y);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("fixture was not released");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void waitUntilIdle(AreaLabelRenderQueue.Mailbox<?> box) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (box.pending() && System.nanoTime() < deadline)
            Thread.sleep(1);
        assertFalse(box.pending(), "worker did not finish");
    }

    private static <T> T waitForResult(AreaLabelRenderQueue.Mailbox<T> box) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        T result;
        while ((result = box.take()) == null && System.nanoTime() < deadline)
            Thread.sleep(1);
        return result;
    }
}
