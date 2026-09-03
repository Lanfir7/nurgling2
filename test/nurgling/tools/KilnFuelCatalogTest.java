package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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

    @Test
    void fuelUnitsForStripsUnfiredAndMatchesCatalog() {
        assertEquals(23, units("Unfired Garden Pot"));
        assertEquals(12, units("Unfired Clay Jar"));
        assertEquals(23, units("Unfired Pot"));
        assertEquals(4, units("Fishwrap"));
    }

    @Test
    void fuelUnitsForStripsUnbakedAndMatchesCatalog() {
        assertEquals(4, units("Unbaked Fishwrap"));
    }

    @Test
    void fuelUnitsForMapsClayToBrickAmounts() {
        assertEquals(2, units("Acre Clay"));
        assertEquals(2, units("Ball Clay"));
        assertEquals(2, units("Potter's Clay"));
        assertEquals(23, units("Coade Clay"));
    }

    @Test
    void fuelUnitsForMapsWoodAndBonePrecursors() {
        assertEquals(3, units("Board"));
        assertEquals(8, units("Block of Wood"));
        assertEquals(8, units("Branch"));
        assertEquals(8, units("Bark"));
        assertEquals(6, units("Bone Material"));
        assertEquals(6, units("Wishbone"));
    }

    @Test
    void fuelUnitsForUnknownItemIsEmpty() {
        assertFalse(KilnFuelCatalog.fuelUnitsFor("Mystery Goo").isPresent());
        assertFalse(KilnFuelCatalog.fuelUnitsFor("Board of Oak").isPresent());
        assertFalse(KilnFuelCatalog.fuelUnitsFor(null).isPresent());
    }

    @Test
    void remainingSecondsUsesCatalogRealTimeAndMeter() {
        int coadeFull = KilnFuelCatalog.parseRealTimeSeconds("1:49:25");
        assertEquals(1 * 3600 + 49 * 60 + 25, coadeFull);
        OptionalInt coadeLeft = KilnFuelCatalog.remainingSeconds("Coade Clay", 76);
        assertTrue(coadeLeft.isPresent());
        assertEquals(Math.round((100 - 76) / 100.0 * coadeFull), coadeLeft.getAsInt());
        assertEquals(26, Math.round(coadeLeft.getAsInt() / 60.0));

        OptionalInt brickUnlit = KilnFuelCatalog.remainingSeconds("Brick", 0);
        assertTrue(brickUnlit.isPresent());
        assertEquals(KilnFuelCatalog.parseRealTimeSeconds("0:08:58"), brickUnlit.getAsInt());
        assertEquals("0:08:58", KilnFuelCatalog.entryFor("Brick").get().realTime);
    }

    @Test
    void remainingSecondsMapsPrefixesClayWoodAndBone() {
        assertEquals(KilnFuelCatalog.remainingSeconds("Garden Pot", 0).getAsInt(),
                KilnFuelCatalog.remainingSeconds("Unfired Garden Pot", 0).getAsInt());
        assertEquals(KilnFuelCatalog.remainingSeconds("Fishwrap", 10).getAsInt(),
                KilnFuelCatalog.remainingSeconds("Unbaked Fishwrap", 10).getAsInt());
        assertEquals(KilnFuelCatalog.remainingSeconds("Brick", 0).getAsInt(),
                KilnFuelCatalog.remainingSeconds("Ball Clay", 0).getAsInt());
        assertEquals(KilnFuelCatalog.remainingSeconds("Brick (Coade Clay)", 50).getAsInt(),
                KilnFuelCatalog.remainingSeconds("Coade Clay", 50).getAsInt());
        assertEquals(KilnFuelCatalog.remainingSeconds("Ashes (Board)", 0).getAsInt(),
                KilnFuelCatalog.remainingSeconds("Board", 0).getAsInt());
        assertEquals(KilnFuelCatalog.remainingSeconds("Bone Ash", 0).getAsInt(),
                KilnFuelCatalog.remainingSeconds("Wishbone", 0).getAsInt());
    }

    @Test
    void remainingSecondsUnknownNameIsEmpty() {
        assertFalse(KilnFuelCatalog.remainingSeconds("Mystery Goo", 50).isPresent());
        assertFalse(KilnFuelCatalog.remainingSeconds(null, 0).isPresent());
        assertFalse(KilnFuelCatalog.entryFor("Board of Oak").isPresent());
        assertEquals(Optional.empty(), KilnFuelCatalog.entryFor(null));
    }

    @Test
    void mixedKilnLoadUsesMaxNotSum() {
        OptionalInt mixed = KilnFuelCatalog.maxFuelUnitsFor(Arrays.asList("Ball Clay", "Unfired Garden Pot"));
        assertTrue(mixed.isPresent());
        assertEquals(23, mixed.getAsInt());
        OptionalInt unbakedAndMug = KilnFuelCatalog.maxFuelUnitsFor(
                Arrays.asList("Unbaked Fishwrap", "Unfired Mug"));
        assertTrue(unbakedAndMug.isPresent());
        assertEquals(12, unbakedAndMug.getAsInt());
        assertEquals(0, KilnFuelCatalog.maxFuelUnitsFor(Arrays.asList()).orElse(-1));
        assertFalse(KilnFuelCatalog.maxFuelUnitsFor(Arrays.asList("Brick", "Mystery Goo")).isPresent());
    }

    private static int fuel(String item) {
        KilnFuelCatalog.Entry entry = KilnFuelCatalog.find(item);
        assertNotNull(entry, item);
        return entry.fuelUnits;
    }

    private static int units(String item) {
        OptionalInt resolved = KilnFuelCatalog.fuelUnitsFor(item);
        assertTrue(resolved.isPresent(), item);
        return resolved.getAsInt();
    }
}
