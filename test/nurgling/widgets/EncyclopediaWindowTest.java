package nurgling.widgets;

import haven.Coord;
import haven.Scrollport;
import haven.Widget;
import nurgling.ClientResourceFixture;
import nurgling.NConfig;
import nurgling.tools.EncyclopediaManager;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncyclopediaWindowTest {
    @Test
    void hiddenWindowDefersDocumentsAndReopeningKeepsSelectedDocumentAndScroll() throws Exception {
        try (ClientResourceFixture resources = new ClientResourceFixture()) {
            NConfig previous = NConfig.current;
            NConfig.current = new NConfig();
            try {
                checkLazyWindow();
            } finally {
                NConfig.current = previous;
            }
        }
    }

    private static void checkLazyWindow() {
        AtomicInteger managerCreations = new AtomicInteger();
        AtomicInteger documentReads = new AtomicInteger();
        Supplier<EncyclopediaManager> managerFactory = () -> {
            managerCreations.incrementAndGet();
            return new TestManager(documentReads);
        };
        TestWindow window = new TestWindow(managerFactory);

        window.hide();
        window.show(false);
        assertEquals(0, managerCreations.get());
        assertEquals(0, documentReads.get());
        assertEquals(0, window.renderCount);

        window.show(true);
        assertEquals(1, managerCreations.get());
        assertEquals(1, documentReads.get());
        assertEquals("welcome.md", window.documentKey);
        assertEquals(1, window.renderCount);

        window.loadDocument("other.md");
        assertEquals("other.md", window.documentKey);
        assertEquals(2, documentReads.get());
        Scrollport content = window.children().stream()
                .filter(Scrollport.class::isInstance)
                .map(Scrollport.class::cast)
                .findFirst().orElseThrow();
        assertTrue(content.bar.max > content.bar.min);
        content.bar.val = content.bar.max;

        window.hide();
        window.show();
        assertEquals(1, managerCreations.get());
        assertEquals(2, documentReads.get());
        assertEquals(2, window.renderCount);
        assertEquals("other.md", window.documentKey);
        assertSame(content, window.children().stream()
                .filter(Scrollport.class::isInstance)
                .findFirst().orElseThrow());
        assertEquals(content.bar.max, content.bar.val);
    }

    @Test
    void failedFirstDocumentRenderCanRetryWithoutDuplicatePanes() throws Exception {
        try (ClientResourceFixture resources = new ClientResourceFixture()) {
            NConfig previous = NConfig.current;
            NConfig.current = new NConfig();
            try {
                AtomicInteger managerCreations = new AtomicInteger();
                TestWindow window = new TestWindow(() -> {
                    managerCreations.incrementAndGet();
                    return new TestManager(new AtomicInteger());
                });
                window.failNextRender = true;
                window.hide();

                assertThrows(IllegalStateException.class, window::show);
                assertEquals(1, managerCreations.get());
                assertEquals(0, window.children().stream().filter(Scrollport.class::isInstance).count());

                window.show();
                assertEquals(1, managerCreations.get());
                assertEquals(1, window.children().stream().filter(Scrollport.class::isInstance).count());
                assertEquals("welcome.md", window.documentKey);
            } finally {
                NConfig.current = previous;
            }
        }
    }

    private static class TestManager extends EncyclopediaManager {
        private final AtomicInteger reads;

        TestManager(AtomicInteger reads) {
            this.reads = reads;
        }

        @Override
        public Set<String> getAllDocumentKeys() {
            return Set.of("other.md", "welcome.md");
        }

        @Override
        public String getDocumentContent(String key) {
            reads.incrementAndGet();
            return "Content for " + key;
        }
    }

    private static class TestWindow extends EncyclopediaWindow {
        int renderCount;
        String documentKey;
        boolean failNextRender;

        TestWindow(Supplier<? extends EncyclopediaManager> factory) {
            super(factory);
        }

        @Override
        Widget createMarkdownImageWidget(String key, String content, int maxWidth) {
            if (failNextRender) {
                failNextRender = false;
                throw new IllegalStateException("render failed");
            }
            renderCount++;
            documentKey = key;
            return new Widget(Coord.of(maxWidth, 2000));
        }
    }
}
