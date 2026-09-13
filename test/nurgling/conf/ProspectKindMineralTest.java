package nurgling.conf;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProspectKindMineralTest {

    @Test
    void firstValueStaysWater() {
        assertEquals(ProspectKind.WATER, ProspectKind.values()[0]);
    }

    @Test
    void oreGemStoneExistImmediatelyAfterOther() {
        ProspectKind[] values = ProspectKind.values();
        int other = ProspectKind.OTHER.ordinal();
        assertEquals(ProspectKind.OTHER, values[other]);
        assertEquals(ProspectKind.ORE, values[other + 1]);
        assertEquals(ProspectKind.GEM, values[other + 2]);
        assertEquals(ProspectKind.STONE, values[other + 3]);
        assertEquals(other + 1, ProspectKind.ORE.ordinal());
        assertEquals(other + 2, ProspectKind.GEM.ordinal());
        assertEquals(other + 3, ProspectKind.STONE.ordinal());
        assertEquals(other + 4, values.length);
    }

    @Test
    void ofUsesMasterMinerHelpersBeforeTerrainNames() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/conf/ProspectKind.java"), StandardCharsets.UTF_8);
        int of = src.indexOf("public static ProspectKind of(");
        assertTrue(of >= 0);
        int brace = src.indexOf('{', of);
        int depth = 0;
        int end = -1;
        for (int i = brace; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        assertTrue(end > of);
        String body = src.substring(of, end);
        int gem = body.indexOf("MasterMiner.isGemstone");
        int ore = body.indexOf("MasterMiner.isOre");
        int stone = body.indexOf("MasterMiner.isStone");
        int clay = body.indexOf("\"clay\"");
        assertTrue(gem >= 0 && ore > gem && stone > ore && clay > stone,
                "of() must classify mined names via MasterMiner before clay/sand");
    }

    @Test
    void claySaltwaterWaterStayOldKinds() {
        assertEquals(ProspectKind.CLAY, ProspectKind.of("Clay"));
        assertEquals(ProspectKind.SALTWATER, ProspectKind.of("Saltwater"));
        assertEquals(ProspectKind.WATER, ProspectKind.of("Water"));
    }

    @Test
    void oresBecomeOre() {
        assertEquals(ProspectKind.ORE, ProspectKind.of("Cassiterite"));
        assertEquals(ProspectKind.ORE, ProspectKind.of("Iron Ochre"));
    }

    @Test
    void gemsBecomeGem() {
        assertEquals(ProspectKind.GEM, ProspectKind.of("Fair Cabochon Onyx"));
        assertEquals(ProspectKind.GEM, ProspectKind.of("Onyx"));
    }

    @Test
    void graniteAndAlabasterBecomeStone() {
        assertEquals(ProspectKind.STONE, ProspectKind.of("Granite"));
        assertEquals(ProspectKind.STONE, ProspectKind.of("Alabaster"));
    }

    @Test
    void quarryartzStaysOtherNotMineral() {
        assertEquals(ProspectKind.OTHER, ProspectKind.of("Quarryartz"));
        assertNotEquals(ProspectKind.STONE, ProspectKind.of("Quarryartz"));
        assertNotEquals(ProspectKind.ORE, ProspectKind.of("Quarryartz"));
        assertNotEquals(ProspectKind.GEM, ProspectKind.of("Quarryartz"));
    }

    @Test
    void sandstoneBecomesStoneNotSand() {
        assertEquals(ProspectKind.STONE, ProspectKind.of("Sandstone"));
        assertNotEquals(ProspectKind.SAND, ProspectKind.of("Sandstone"));
    }

    @Test
    void mossAndNullStayOther() {
        assertEquals(ProspectKind.OTHER, ProspectKind.of("Moss"));
        assertEquals(ProspectKind.OTHER, ProspectKind.of(null));
    }

    @Test
    void mineralKindsKeepL10nKeys() {
        assertEquals("maptools.kind.ore", ProspectKind.ORE.l10nKey);
        assertEquals("maptools.kind.gem", ProspectKind.GEM.l10nKey);
        assertEquals("maptools.kind.stone", ProspectKind.STONE.l10nKey);
    }
}
