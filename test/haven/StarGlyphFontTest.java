package haven;

import nurgling.NConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarGlyphFontTest {
    @BeforeAll
    static void fonts() {
        if(NConfig.current == null)
            NConfig.current = new NConfig();
    }

    @Test
    void starsStayVisibleWhileBodyTextKeepsItsFont() throws Exception {
        Font body = Font.createFont(Font.TRUETYPE_FONT,
            new File("resources/src/nurgling/font/opensans.res/font/font_0.ttf")).deriveFont(Font.PLAIN, 16f);
        assertTrue(!body.canDisplay(0x2605), "Open Sans is the font that drops the star");
        assertTrue(!body.canDisplay(0x2606));
        Text.Foundry foundry = new Text.Foundry(body, Color.WHITE).aa(false);

        Text.Line filled = foundry.render("\u2605", Color.WHITE);
        Text.Line empty = foundry.render("\u2606", Color.WHITE);
        Text.Line check = foundry.render("\u2713", Color.WHITE);
        Text.Line arrow = foundry.render("\u25BC", Color.WHITE);
        assertTrue(ink(filled.img) > 20, "filled favourite star must be a glyph");
        assertTrue(ink(empty.img) > 8, "empty favourite star must be a glyph");
        assertTrue(ink(check.img) > 8, "checkmark must be a glyph");
        assertTrue(ink(arrow.img) > 8, "arrow must be a glyph");

        Text.Line name = foundry.render("Lynsary", Color.WHITE);
        Text.Line both = foundry.render("\u2605Lynsary", Color.WHITE);
        assertEquals(name.sz().y, both.sz().y);
        assertEquals(foundry.m.stringWidth("Lynsary"), name.sz().x);
        assertEquals(foundry.strsize("\u2605Lynsary").x, both.sz().x);
        assertEquals(both.advance(1), both.sz().x - name.sz().x);
        assertSuffixMatches(name.img, both.img, both.advance(1));

        RichText marked = new RichText.Foundry(body, Color.WHITE).render("\u2713");
        assertTrue(ink(marked.img) > 8, "rich text checkmark must be a glyph");
    }

    private static void assertSuffixMatches(BufferedImage name, BufferedImage mixed, int offset) {
        assertEquals(name.getHeight(), mixed.getHeight());
        assertTrue(mixed.getWidth() >= offset + name.getWidth());
        int mismatch = 0;
        for(int y = 0; y < name.getHeight(); y++) {
            for(int x = 2; x < name.getWidth(); x++) {
                if(name.getRGB(x, y) != mixed.getRGB(x + offset, y))
                    mismatch++;
            }
        }
        assertEquals(0, mismatch, "the name beside the star must stay in the body font");
    }

    private static int ink(BufferedImage img) {
        int count = 0;
        for(int y = 0; y < img.getHeight(); y++) {
            for(int x = 0; x < img.getWidth(); x++) {
                if((img.getRGB(x, y) >>> 24) > 40)
                    count++;
            }
        }
        return count;
    }
}
