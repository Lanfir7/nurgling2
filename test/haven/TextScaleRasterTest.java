package haven;

import haven.render.BufPipe;
import nurgling.NConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextScaleRasterTest {
    private static NConfig previousConfig;

    @BeforeAll
    static void fonts() {
        previousConfig = NConfig.current;
        if(NConfig.current == null)
            NConfig.current = new NConfig();
    }

    @AfterAll
    static void restoreConfig() {
        NConfig.current = previousConfig;
    }

    @Test
    void scalekeyRoundsToHundredths() {
        assertEquals(100, Text.Furnace.scalekey(1.0));
        assertEquals(150, Text.Furnace.scalekey(1.5));
        assertEquals(150, Text.Furnace.scalekey(1.504));
        assertEquals(50, Text.Furnace.scalekey(0.5));
    }

    @Test
    void foundryScaledKeepsIdentityAtUnitScale() {
        Text.Foundry f = new Text.Foundry(new Font("SansSerif", Font.PLAIN, 12), Color.WHITE).aa(true);
        assertSame(f, f.scaled(1.0));
        assertSame(f, f.scaled(1.001));
    }

    @Test
    void foundryScaledSnapsFontToIntegerPixels() {
        Text.Foundry f = new Text.Foundry(new Font("SansSerif", Font.PLAIN, 12), Color.WHITE).aa(true);
        Text.Foundry s = f.scaled(1.5);
        float expect = Math.max(1f, Math.round(f.font.getSize2D() * 1.5f));
        assertEquals(expect, s.font.getSize2D(), 0.01f);
        assertTrue(s.aa);
        assertTrue(s.height() > f.height());
    }

    @Test
    void blurFurnaceRerasterizesInsteadOfStretching() {
        Text.Furnace meter = new PUtils.BlurFurn(
            new Text.Foundry(new Font("SansSerif", Font.PLAIN, 12), Color.WHITE).aa(true),
            2, 1, new Color(60, 30, 30));
        Text base = meter.render("94.02%");
        Text scaled = base.rescaled(1.5);
        assertTrue(scaled.sz().x > base.sz().x, "scaled glyphs must be wider, not a stretched copy");
        assertTrue(scaled.sz().y > base.sz().y);
        assertEquals(base.sz(), base.tex().sz());
        assertTrue(base.tex() instanceof Text.ScaledTex);
        assertFalse(base.tex() instanceof TexI);
    }

    @Test
    void liveTexKeepsLayoutSizeAndIsScaleAware() {
        Text.Foundry f = new Text.Foundry(new Font("SansSerif", Font.PLAIN, 12), Color.WHITE).aa(true);
        Tex tex = Text.live(s -> f.scaled(s).render("25.5").img);
        assertEquals(f.render("25.5").sz(), tex.sz());
        assertTrue(tex instanceof Text.ScaleRasterTex);
        assertTrue(f.scaled(1.5).render("25.5").sz().x > f.render("25.5").sz().x);
    }

    @Test
    void numberInfoOverlayIsScaleAware() {
        GItem.NumberInfo info = new GItem.NumberInfo() {
            public int itemnum() { return 4; }
        };
        Tex tex = info.overlay();
        assertTrue(tex instanceof Text.ScaleRasterTex);
        assertTrue(tex.sz().x > 0);
    }

    @Test
    void tfpixelSnapsThroughWidgetScale() {
        GOut g = new GOut(null, new BufPipe(), Coord.of(200, 200));
        g.tfscale = 1.5f;
        g.tforigin = Coord.of(10, 20);
        assertEquals(new Coord(10, 20), g.tfpixel(Coord.of(10, 20)));
        assertEquals(new Coord(10 + Math.round(20 * 1.5f), 20 + Math.round(30 * 1.5f)),
            g.tfpixel(Coord.of(30, 50)));
        GOut child = g.reclipl(Coord.of(4, 6), Coord.of(80, 40));
        assertEquals(1.5f, child.tfscale, 0.0001f);
        assertEquals(Coord.of(10, 20), child.tforigin);
    }
}
