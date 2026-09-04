package nurgling.craftatlas;

import nurgling.craftatlas.CraftAtlasMaterialPlanner.Candidate;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Source;
import nurgling.db.dao.StorageItemDao;
import nurgling.tools.VSpec;
import nurgling.widgets.NStorageItemsWidget.GroupedItem;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasMaterialSourceTest {
    @Test
    void expandsEachVSpecOptionAndRemovesDuplicateNames() {
        String key = "CraftAtlasMaterialSourceFabric";
        VSpec.categories.put(key, jsonNames("Linen Cloth", "Hemp Cloth"));
        try {
            CraftAtlasEntry.InputSlot slot = new CraftAtlasEntry.InputSlot(2, false, List.of(
                    new CraftAtlasEntry.IngredientOption("gfx/invobjs/fabric", key),
                    new CraftAtlasEntry.IngredientOption("gfx/invobjs/linencloth", "Linen Cloth")));

            assertEquals(List.of("Linen Cloth", "Hemp Cloth"),
                    CraftAtlasMaterialSource.allowedNames(slot));
        } finally {
            VSpec.categories.remove(key);
        }
    }

    @Test
    void concreteInputKeepsItsName() {
        CraftAtlasEntry.InputSlot slot = new CraftAtlasEntry.InputSlot(1, false, List.of(
                new CraftAtlasEntry.IngredientOption("gfx/invobjs/glue", "Glue")));

        assertEquals(List.of("Glue"), CraftAtlasMaterialSource.allowedNames(slot));
    }

    @Test
    void mergeMarksInventoryAndKeepsStorageRowsSeparate() {
        GroupedItem warehouse = storage("Linen Cloth", 90, 4);
        CraftAtlasMaterialSource.MergedRows rows = CraftAtlasMaterialSource.merge(0,
                List.of(new CraftAtlasMaterialSource.InventorySample("Linen Cloth", 90, 2)),
                List.of(warehouse));

        assertEquals(2, rows.candidates.size());
        assertEquals(Source.INVENTORY, rows.candidates.get(0).source);
        assertEquals(Source.STORAGE, rows.candidates.get(1).source);
        assertEquals(2, rows.candidates.get(0).count);
        assertEquals(4, rows.candidates.get(1).count);
        Candidate storage = rows.candidates.get(1);
        assertSame(warehouse, rows.storageByCandidateId.get(storage.id));
    }

    @Test
    void snapshotIsCollectibleOnlyForObservedInputs() {
        CraftAtlasEntry wiki = CraftAtlasEntry.builder("wiki:cloth", "Cloth")
                .input(new CraftAtlasEntry.InputSlot(1, false, List.of(
                        new CraftAtlasEntry.IngredientOption("gfx/invobjs/fibre", "Fibre"))))
                .build();
        CraftAtlasEntry observed = CraftAtlasEntry.builder("paginae/craft/cloth", "Cloth")
                .inputsObserved(true)
                .input(new CraftAtlasEntry.InputSlot(1, false, List.of(
                        new CraftAtlasEntry.IngredientOption("gfx/invobjs/fibre", "Fibre"))))
                .build();

        assertFalse(CraftAtlasMaterialSource.emptySnapshot(wiki).collectible);
        assertTrue(CraftAtlasMaterialSource.emptySnapshot(observed).collectible);
    }

    @Test
    void snapshotCanUseInventoryCapturedByUiThread() {
        CraftAtlasEntry observed = CraftAtlasEntry.builder("paginae/craft/cloth", "Cloth")
                .inputsObserved(true)
                .input(new CraftAtlasEntry.InputSlot(2, false, List.of(
                        new CraftAtlasEntry.IngredientOption("gfx/invobjs/linencloth", "Linen Cloth"))))
                .build();

        CraftAtlasMaterialSource.Snapshot snapshot = new CraftAtlasMaterialSource().load(observed,
                List.of(new CraftAtlasMaterialSource.InventorySample("Linen Cloth", 77, 3)));

        assertEquals(1, snapshot.candidatesBySlot.get(0).size());
        assertEquals(Source.INVENTORY, snapshot.candidatesBySlot.get(0).get(0).source);
    }

    private static ArrayList<JSONObject> jsonNames(String... names) {
        ArrayList<JSONObject> values = new ArrayList<>();
        for(String name : names) values.add(new JSONObject().put("name", name));
        return values;
    }

    private static GroupedItem storage(String name, double quality, int count) {
        List<StorageItemDao.StorageItemData> items = new ArrayList<>();
        for(int i = 0; i < count; i++)
            items.add(new StorageItemDao.StorageItemData("hash-" + i, name, quality, "(0,0)", "box"));
        return new GroupedItem(name, quality, count, items, 5, "Chest");
    }
}
