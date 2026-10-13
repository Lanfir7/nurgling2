package nurgling.widgets;

import nurgling.db.dao.StorageItemDao;
import nurgling.db.StorageVillageFilter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageVillageFilterTest {
    @Test
    void filtersForeignAndUnknownLocationsButFallsBackWithoutVillage() {
        StorageItemDao.StorageItemData here = new StorageItemDao.StorageItemData("here", "Milk", 12,
                "#bulk:2.5 l", "barrel", 1L);
        StorageItemDao.StorageItemData away = new StorageItemDao.StorageItemData("away", "Rope", 8,
                "(0, 0)", "box", 2L);
        StorageItemDao.StorageItemData unknown = new StorageItemDao.StorageItemData("unknown", "Clay", 0,
                null, "missing", null);
        List<StorageItemDao.StorageItemData> all = Arrays.asList(here, away, unknown);

        assertEquals(Collections.singletonList(here),
                StorageVillageFilter.apply(all, true, Set.of(1L)));
        assertEquals(all, StorageVillageFilter.apply(all, false, Set.of(1L)));
        assertEquals(all, StorageVillageFilter.apply(all, true, Collections.emptySet()));
        assertEquals("l", here.getBulkUnit());

        List<StorageItemDao.StorageItemData> afterDeletion = StorageVillageFilter.withoutHashes(
                all, Set.of("here"));
        assertEquals(Arrays.asList(away, unknown),
                StorageVillageFilter.apply(afterDeletion, false, Set.of(1L)));
        assertEquals(Collections.emptyList(),
                StorageVillageFilter.apply(afterDeletion, true, Set.of(1L)));
    }
}
