package nurgling.actions.bots.areamover;

import haven.Indir;
import haven.GItem;
import haven.Loading;
import haven.Resource;
import haven.WItem;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.tasks.NTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * What the Area Mover is carrying: the player inventory minus what was in it when the run started.
 * Protect the original physical items even when new cargo has the same name and another quality.
 * If stack formation recreates a personal item, keep all same-name items rather than guessing.
 */
class Cargo {
    private final Set<GItem> original = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<GItem, String> originalNames = new IdentityHashMap<>();

    Cargo(NGameUI gui) throws InterruptedException {
        this(gui.getInventory().getItems());
    }

    Cargo(Collection<WItem> initial) {
        for(WItem item : initial) {
            original.add(item.item);
            originalNames.put(item.item, ((NGItem)item.item).name());
        }
    }

    List<WItem> carriedItems(Collection<WItem> current) {
        Set<GItem> present = Collections.newSetFromMap(new IdentityHashMap<>());
        for(WItem item : current) present.add(item.item);
        Set<String> ambiguousNames = new HashSet<>();
        for(Map.Entry<GItem, String> item : originalNames.entrySet())
            if(!present.contains(item.getKey()) && item.getValue() != null)
                ambiguousNames.add(item.getValue());
        List<WItem> carried = new ArrayList<>();
        for(WItem item : current)
            if(!original.contains(item.item) && !ambiguousNames.contains(((NGItem)item.item).name()))
                carried.add(item);
        return carried;
    }

    static Map<String, Integer> countByName(NGameUI gui) throws InterruptedException {
        Map<String, Integer> counts = new HashMap<>();
        for (WItem item : gui.getInventory().getItems()) {
            String name = ((NGItem) item.item).name();
            if (name != null)
                counts.merge(name, 1, Integer::sum);
        }
        return counts;
    }

    /** Carried count per item name; names with nothing carried are left out. */
    Map<String, Integer> current(NGameUI gui) throws InterruptedException {
        Map<String, Integer> result = new HashMap<>();
        for(WItem item : carriedItems(gui.getInventory().getItems())) {
            String name = ((NGItem)item.item).name();
            if(name != null) result.merge(name, 1, Integer::sum);
        }
        return result;
    }

    int count(NGameUI gui, String name) throws InterruptedException {
        return carriedItems(gui, name).size();
    }

    ArrayList<WItem> carriedItems(NGameUI gui, String name) throws InterruptedException {
        return new ArrayList<>(carriedItems(items(gui, name)));
    }

    boolean hasOriginalItems(NGameUI gui, String name) throws InterruptedException {
        List<WItem> all = items(gui, name);
        return carriedItems(all).size() < all.size();
    }

    int total(NGameUI gui) throws InterruptedException {
        int total = 0;
        for (int c : current(gui).values())
            total += c;
        return total;
    }

    /** All exact-name items, for footprint lookup and original-item checks. */
    static ArrayList<WItem> items(NGameUI gui, String name) throws InterruptedException {
        ArrayList<WItem> result = new ArrayList<>();
        for (WItem item : gui.getInventory().getItems()) {
            if (name.equals(((NGItem) item.item).name()))
                result.add(item);
        }
        return result;
    }

    /** Resource name of an inventory item, waiting for it to load. */
    static String resName(WItem item) {
        return resName(item.item.res);
    }

    static String resName(Indir<Resource> res) {
        if (res == null)
            return null;
        try {
            return res.get().name;
        } catch (Loading l) {
            return null;
        }
    }

    static String waitResName(Indir<Resource> res) throws InterruptedException {
        if (res == null)
            return null;
        String[] out = {null};
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                out[0] = resName(res);
                return out[0] != null;
            }
        });
        return out[0];
    }
}
