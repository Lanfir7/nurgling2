package nurgling.actions;

import haven.Area;
import haven.Coord;
import haven.Coord2d;
import haven.GItem;
import haven.ItemInfo;
import haven.MCache;
import haven.WItem;
import nurgling.NGItem;
import nurgling.tools.NAlias;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedCropTest {
    private static final NAlias RADISH = new NAlias("plants/radish");
    private static final NAlias WHITE_ONION = new NAlias("plants/whiteonion");
    private static final NAlias WATERMELON = new NAlias("plants/watermelon");
    private static final NAlias CARROT = new NAlias("plants/carrot");

    @Test
    void radishPlantsFromBarrelSeedsOnly() {
        assertEquals(Collections.singletonList("barrel"),
                SeedCrop.plantingSourceKinds(RADISH, true, true));
        assertEquals(Collections.emptyList(),
                SeedCrop.plantingSourceKinds(RADISH, false, true));
    }

    @Test
    void whiteOnionPlantsFromStockpileOnly() {
        assertEquals(Collections.singletonList("stockpile"),
                SeedCrop.plantingSourceKinds(WHITE_ONION, true, true));
        assertEquals(Collections.emptyList(),
                SeedCrop.plantingSourceKinds(WHITE_ONION, true, false));
    }

    @Test
    void watermelonPlantsFromBarrelSeedsOnly() {
        assertEquals(Collections.singletonList("barrel"),
                SeedCrop.plantingSourceKinds(WATERMELON, true, true));
        assertEquals(Collections.emptyList(),
                SeedCrop.plantingSourceKinds(WATERMELON, false, true));
    }

    @Test
    void cropWithBothPlantableSourcesKeepsBothWhenContainersExist() {
        List<String> both = Arrays.asList("barrel", "stockpile");
        assertEquals(both, SeedCrop.plantingSourceKinds(CARROT, true, true));
        assertEquals(Collections.singletonList("barrel"),
                SeedCrop.plantingSourceKinds(CARROT, true, false));
        assertEquals(Collections.singletonList("stockpile"),
                SeedCrop.plantingSourceKinds(CARROT, false, true));
    }

    @Test
    void vegItemAliasExcludesSeedSibling() {
        NAlias radishVeg = SeedCrop.vegItemAlias(new NAlias("Radish"));
        assertTrue(radishVeg.matches("Radish"));
        assertFalse(radishVeg.matches("Radish Seeds"));

        NAlias onionVeg = SeedCrop.vegItemAlias(new NAlias("White Onion"));
        assertTrue(onionVeg.matches("White Onion"));
        assertFalse(onionVeg.matches("White Onion Seeds"));
    }

    @Test
    void seedItemAliasDoesNotMatchVegetable() {
        NAlias seeds = SeedCrop.seedItemAlias(new NAlias("Radish Seeds"));
        assertTrue(seeds.matches("Radish Seeds"));
        assertFalse(seeds.matches("Radish"));
        assertTrue(SeedCrop.seedItemAlias(new NAlias("Watermelon Seeds")).matches("Watermelon Seeds"));
    }

    @Test
    void qualityPatchesPackWithoutOverlapAndSkipOccupiedOrNonFieldTiles() {
        Area area = new Area(new Coord(-10, 30), new Coord(-6, 33), true);
        Area.Tile[][] tiles = fieldTiles(area);
        tiles[0][0].isFree = false;
        tiles[2][2].name = "forest";
        ArrayList<ArrayList<Coord>> patches = SeedCrop.tilePatches(area, tiles, 2, 2);
        assertEquals(3, patches.size());
        assertTrue(patches.stream().anyMatch(p -> p.contains(new Coord(-10, 31))));
        assertTrue(patches.stream().noneMatch(p -> p.contains(new Coord(-10, 30))));
        assertTrue(patches.stream().noneMatch(p -> p.contains(new Coord(-8, 32))));
        assertTrue(patches.stream().allMatch(p -> p.size() == 4));
        java.util.Set<Coord> covered = new java.util.HashSet<>();
        for (List<Coord> patch : patches) for (Coord tile : patch) assertTrue(covered.add(tile));
        for (Area.Tile[] row : tiles) for (Area.Tile tile : row) tile.isFree = false;
        assertTrue(SeedCrop.tilePatches(area, tiles, 2, 2).isEmpty());
    }

    @Test
    void rotatedQualityPatternFitsMorePatches() {
        Area area = new Area(new Coord(100, 200), new Coord(102, 201), true);
        Area.Tile[][] tiles = fieldTiles(area);
        assertEquals(0, SeedCrop.tilePatches(area, tiles, 1, 3).size());
        assertEquals(2, SeedCrop.tilePatches(area, tiles, 3, 1).size());
    }

    @Test
    void nearestQualityPatchUsesWorldTileCentersEvenFarFromOrigin() {
        Area area = new Area(new Coord(-101, 241), new Coord(-98, 241), true);
        ArrayList<ArrayList<Coord>> patches = SeedCrop.tilePatches(area, fieldTiles(area), 2, 1);
        Coord2d player = new Coord(-98, 241).mul(MCache.tilesz).add(MCache.tilehsz);
        assertEquals(new Coord(-99, 241), SeedCrop.nearestPatch(patches, player).get(0));
    }

    @Test
    void qualityFetchPrefixCountsSmallStacksAndPlantingUsesBestAvailable() {
        WItem highRemainder = seedItem("Carrot Seeds", 100f, 4);
        WItem highStack = seedItem("Carrot Seeds", 90f, 20);
        WItem lowerVegetable = seedItem("Carrot", 80f, null);
        WItem lowStack = seedItem("Carrot Seeds", 20f, 50);
        WItem singleSeed = seedItem("Carrot Seeds", 10f, null);
        List<WItem> chest = Arrays.asList(lowStack, lowerVegetable, highStack, highRemainder);
        assertEquals(3, SeedCrop.itemsToCoverCells(chest, 5));
        assertEquals(15, SeedCrop.getSeedingCapacity(chest));
        List<WItem> order = SeedCrop.getPlantingOrder(chest, 5);
        assertEquals(Arrays.asList(highStack, highStack, highStack, highStack, lowerVegetable), order);
        assertEquals(0, SeedCrop.getSeedingCapacity(Collections.singletonList(highRemainder)));
        assertEquals(1, SeedCrop.getSeedingCapacity(Collections.singletonList(singleSeed)));
        assertEquals(4, SeedCrop.getPlantingOrder(Collections.singletonList(highStack), 5).size());
        assertEquals(1, SeedCrop.getPlantingOrder(Arrays.asList(highRemainder, lowerVegetable), 2).size());
    }

    private static Area.Tile[][] fieldTiles(Area area) {
        Area.Tile[][] tiles = new Area.Tile[area.br.x - area.ul.x + 1][area.br.y - area.ul.y + 1];
        for (int x = 0; x < tiles.length; x++)
            for (int y = 0; y < tiles[0].length; y++)
                tiles[x][y] = new Area.Tile(new Coord(area.ul.x + x, area.ul.y + y), "field", true);
        return tiles;
    }

    private static WItem seedItem(String name, Float quality, Integer amount) {
        NGItem item = new NGItem(null) {
            @Override public String name() { return name; }
        };
        item.quality = quality;
        if (amount != null) item.info = Collections.<ItemInfo>singletonList(new GItem.Amount(item, amount));
        return new WItem(item);
    }
}
