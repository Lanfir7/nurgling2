package nurgling.render;

import haven.Coord;
import haven.PView;
import haven.RenderContext.PostProcessor;
import haven.render.NumberFormat;
import haven.render.Texture2D;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ModernGraphicsRoutingTest {
    static class View extends PView {
        final List<PostProcessor> stages = new ArrayList<>();
        View() { super(Coord.of(7, 3)); }
        public void add(PostProcessor stage) { super.add(stage); stages.add(stage); }
        public void remove(PostProcessor stage) { super.remove(stage); stages.remove(stage); }
    }

    @Test void switchingMethodsNeverStacksFiltersAndPreservesUnrelatedStages() throws Exception {
        View view = new View();
        NPostFX.Manager manager = new NPostFX.Manager(view, () -> {}, () -> {});
        PostProcessor unrelated = new PostProcessor() {};
        view.add(unrelated);
        NGfx.Settings off = ModernGraphicsSettingsTest.settings(new HashMap<>());
        NGfx.Settings legacy = off.with("fxaa", true).with("sharpen", true);
        NGfx.Settings modern = legacy.with("aamethod", 1).with("sharpmethod", 1).with("sharpness", 0.7f);
        try {
            for(int i = 0; i < 4; i++) {
                manager.syncImage(legacy);
                assertEquals(3, view.stages.size());
                assertEquals(1, view.stages.stream().filter(p -> p instanceof NPostFX.FXAA).count());
                assertEquals(1, view.stages.stream().filter(p -> p instanceof NPostFX.Sharpen).count());
                manager.syncImage(modern);
                assertEquals(3, view.stages.size());
                assertEquals(1, view.stages.stream().filter(p -> p instanceof Smaa).count());
                Cas cas = (Cas)view.stages.stream().filter(p -> p instanceof Cas).findFirst().get();
                assertEquals(0.7f, cas.amount);
                List<PostProcessor> snapshot = new ArrayList<>(view.stages);
                manager.syncImage(modern);
                assertEquals(snapshot, view.stages, "Unchanged settings must reuse filters");
                manager.syncImage(off);
                assertEquals(java.util.Collections.singletonList(unrelated), view.stages);
            }
        } finally { manager.dispose(); view.dispose(); }
    }

    @Test void replacedAndDisabledFiltersReleaseTheirOutputBuffers() throws Exception {
        View view = new View();
        NPostFX.Manager manager = new NPostFX.Manager(view, () -> {}, () -> {});
        NGfx.Settings off = ModernGraphicsSettingsTest.settings(new HashMap<>());
        NGfx.Settings modern = off.with("sharpen", true).with("sharpmethod", 1);
        AtomicInteger released = new AtomicInteger();
        try {
            manager.syncImage(modern);
            PostProcessor filter = view.stages.get(0);
            Texture2D.Sampler2D buffer = NPostFX.mktarget(Coord.of(1, 1), NumberFormat.UNORM8);
            buffer.ro = released::incrementAndGet;
            filter.buf = buffer;
            manager.syncImage(modern.with("sharpmethod", 0));
            assertEquals(1, released.get());
            assertTrue(view.stages.get(0) instanceof NPostFX.Sharpen);
            manager.syncImage(off);
            assertTrue(view.stages.isEmpty());
            manager.dispose();
            assertEquals(1, released.get(), "Disposed buffers must not be released again");
        } finally { manager.dispose(); view.dispose(); }
    }
}
