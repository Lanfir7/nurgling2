package nurgling.widgets.craftatlas;

import nurgling.craftatlas.quality.QualityWorkshopModel.Key;
import nurgling.db.dao.StorageItemDao.StorageItemData;
import java.util.*;

/** Pure max-quality reduction, never writes into the player's scenario. */
final class QualityWorkshopHints {
    static final class Hint {
        final double quality;
        final String name;
        Hint(double quality, String name) { this.quality = quality; this.name = name; }
    }
    static Map<Key, Hint> best(Map<Key, Set<String>> names, List<StorageItemData> rows) {
        Map<Key, Hint> result = new EnumMap<>(Key.class);
        Map<String, Hint> maxima = new HashMap<>();
        for(StorageItemData row : rows) {
            if(row == null || row.getName() == null || !Double.isFinite(row.getQuality()) || row.getQuality() < 1) continue;
            Hint old = maxima.get(row.getName());
            if(old == null || row.getQuality() > old.quality)
                maxima.put(row.getName(), new Hint(row.getQuality(), row.getName()));
        }
        for(Map.Entry<Key, Set<String>> entry : names.entrySet()) for(String name : entry.getValue()) {
            Hint candidate = maxima.get(name), old = result.get(entry.getKey());
            if(candidate != null && (old == null || candidate.quality > old.quality)) result.put(entry.getKey(), candidate);
        }
        return result;
    }
}
