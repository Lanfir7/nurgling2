package nurgling.actions;

import haven.Loading;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AnimalIconLoadingTest {
    @Test
    void pendingCandidateIsNotReplacedByFallbackAndCanAppearNextFrame() {
        AtomicBoolean ready = new AtomicBoolean();
        List<String> paths = new ArrayList<>();
        BufferedImage icon = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        java.util.function.Function<String, BufferedImage> loader = path -> {
            paths.add(path);
            if (!ready.get()) throw new Loading();
            return icon;
        };
        assertThrows(Loading.class, () -> ObjectTracker.loadAnimalIconFromPath(
                "gfx/kritter/minimaptest/beast", "Test beast", null, loader));
        assertEquals(Arrays.asList("gfx/invobjs/kritter/minimaptest/beast"), paths);
        ready.set(true);
        assertSame(icon, ObjectTracker.loadAnimalIconFromPath(
                "gfx/kritter/minimaptest/beast", "Test beast", null, loader));
    }

    @Test
    void missingCandidateStillFallsBackToShorterPath() {
        List<String> paths = new ArrayList<>();
        BufferedImage icon = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        BufferedImage actual = ObjectTracker.loadAnimalIconFromPath(
                "gfx/kritter/minimaptest/beast", "Test beast", null, path -> {
                    paths.add(path);
                    return path.endsWith("/beast") ? null : icon;
                });
        assertSame(icon, actual);
        assertEquals(Arrays.asList("gfx/invobjs/kritter/minimaptest/beast",
                "gfx/invobjs/kritter/minimaptest"), paths);
    }
}
