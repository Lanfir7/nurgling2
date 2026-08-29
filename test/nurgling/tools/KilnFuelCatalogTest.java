package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KilnFuelCatalogTest {

    @Test
    void wikiSnapshotHasThirtyUniqueItems() {
        List<KilnFuelCatalog.Entry> entries = KilnFuelCatalog.all();
        assertEquals(30, entries.size());
        Set<String> names = new HashSet<>();
        for (KilnFuelCatalog.Entry entry : entries) {
            assertTrue(names.add(entry.item), "duplicate item: " + entry.item);
            assertTrue(entry.fuelUnits > 0, "missing fuel: " + entry.item);
            assertFalse(entry.realTime.isEmpty(), "missing real time: " + entry.item);
            assertFalse(entry.inGameTime.isEmpty(), "missing in-game time: " + entry.item);
        }
    }

    @Test
    void botFuelAmountsMatchExistingSetMaxlvl() {
        assertEquals(2, fuel("Brick"));
        assertEquals(6, fuel("Bone Ash"));
        assertEquals(3, fuel("Ashes (Board)"));
        assertEquals(8, fuel("Ashes (Block of Wood)"));
        assertEquals(23, fuel("Garden Pot"));
        assertEquals(4, fuel("Fishwrap"));
        assertEquals(4, fuel("Fruitroast"));
        assertEquals(4, fuel("Mushroom-Burst Glutton"));
        assertEquals(4, fuel("Nutjerky"));
        assertEquals(4, fuel("Stuffed Bird"));
    }

    @Test
    void coadeBrickAndMaltMatchWiki() {
        assertEquals(23, fuel("Brick (Coade Clay)"));
        assertEquals(1, fuel("Malted Barley"));
        assertEquals(1, fuel("Malted Wheat"));
        assertEquals(1, fuel("Branding Iron"));
    }

    @Test
    void brickTimesMatchWiki() {
        KilnFuelCatalog.Entry brick = KilnFuelCatalog.find("Brick");
        assertNotNull(brick);
        assertEquals("0:08:58", brick.realTime);
        assertEquals("0:29:30", brick.inGameTime);
    }

    @Test
    void remainingWikiFuelUnits() {
        assertEquals(12, fuel("Ashes (Pitbaked Goods)"));
        assertEquals(8, fuel("Ashes (Branch)"));
        assertEquals(8, fuel("Ashes (Bark)"));
        assertEquals(12, fuel("Ceramic Knife"));
        assertEquals(12, fuel("Clay Jar"));
        assertEquals(5, fuel("Clay Pipe"));
        assertEquals(8, fuel("Earthenware Platter"));
        assertEquals(5, fuel("Hand Impression"));
        assertEquals(12, fuel("Mug"));
        assertEquals(23, fuel("Pot"));
        assertEquals(8, fuel("Porcelain Plate"));
        assertEquals(8, fuel("Stoneware Vase"));
        assertEquals(12, fuel("Teapot"));
        assertEquals(5, fuel("Toy Chariot"));
        assertEquals(8, fuel("Treeplanter's Pot"));
        assertEquals(23, fuel("Urn"));
    }

    private static int fuel(String item) {
        KilnFuelCatalog.Entry entry = KilnFuelCatalog.find(item);
        assertNotNull(entry, item);
        return entry.fuelUnits;
    }
}
