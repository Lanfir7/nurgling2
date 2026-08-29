package nurgling.widgets;

import haven.GItem;
import haven.ItemInfo;
import haven.Loading;
import haven.WItem;
import haven.res.ui.tt.attrmod.AttrMod;
import haven.res.ui.tt.attrmod.Attribute;
import haven.res.ui.tt.attrmod.Mod;
import haven.res.ui.tt.slot.Slotted;
import haven.res.ui.tt.slots.ISlots;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared AttrMod summing used by Equipment and Wardrobe so both stay visually
 * and numerically the same (icons + name + green +N via {@link AttrMod#modimg}).
 */
public final class EquipmentStatsCollector {
    private EquipmentStatsCollector() {
    }

    public interface Source {
        Object identity();

        List<ItemInfo> info();
    }

    public static final class Totals {
        private final Map<String, Attribute> attributes = new HashMap<>();
        private final Map<String, Double> values = new HashMap<>();
        private List<Map.Entry<String, Double>> sorted;

        public boolean isEmpty() {
            return values.isEmpty();
        }

        public double value(String attrName) {
            return values.getOrDefault(attrName, 0.0);
        }

        public Attribute attribute(String attrName) {
            return attributes.get(attrName);
        }

        public List<Map.Entry<String, Double>> sorted() {
            if (sorted == null) {
                sorted = new ArrayList<>(values.entrySet());
                sorted.sort((a, b) -> Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue())));
            }
            return sorted;
        }

        public Collection<Mod> mods() {
            Collection<Mod> mods = new ArrayList<>();
            for (Map.Entry<String, Double> entry : sorted()) {
                Attribute attr = attributes.get(entry.getKey());
                if (attr != null)
                    mods.add(new Mod(attr, entry.getValue()));
            }
            return mods;
        }

        void add(Attribute attr, double value) {
            String name = attr.name();
            attributes.put(name, attr);
            values.put(name, values.getOrDefault(name, 0.0) + value);
            sorted = null;
        }
    }

    public static Totals collect(Iterable<? extends Source> sources) {
        Totals totals = new Totals();
        Set<Object> processed = new HashSet<>();
        for (Source source : sources) {
            if (source == null)
                continue;
            Object id = source.identity();
            if (id != null && !processed.add(id))
                continue;
            List<ItemInfo> info;
            try {
                info = source.info();
            } catch (Loading e) {
                continue;
            } catch (Exception e) {
                continue;
            }
            collectFromInfoList(totals, info);
        }
        return totals;
    }

    public static Totals collectFromInfoLists(Iterable<? extends List<ItemInfo>> infos) {
        List<Source> sources = new ArrayList<>();
        int i = 0;
        for (List<ItemInfo> info : infos) {
            final int key = i++;
            final List<ItemInfo> list = info;
            sources.add(new Source() {
                @Override
                public Object identity() {
                    return key;
                }

                @Override
                public List<ItemInfo> info() {
                    return list;
                }
            });
        }
        return collect(sources);
    }

    public static Totals collectFromItems(Iterable<WItem> items) {
        List<Source> sources = new ArrayList<>();
        if (items == null)
            return collect(sources);
        for (WItem item : items) {
            if (item == null || item.item == null)
                continue;
            sources.add(ofItem(item));
        }
        return collect(sources);
    }

    public static Totals collectFromItems(WItem[] items) {
        if (items == null)
            return collect(java.util.Collections.emptyList());
        return collectFromItems(java.util.Arrays.asList(items));
    }

    public static Source ofItem(WItem item) {
        final GItem gitem = item.item;
        return new Source() {
            @Override
            public Object identity() {
                return gitem;
            }

            @Override
            public List<ItemInfo> info() {
                return gitem.info();
            }
        };
    }

    static void collectFromInfoList(Totals totals, List<ItemInfo> info) {
        if (info == null)
            return;
        for (ItemInfo inf : info) {
            addAttrMod(totals, inf);
        }
        for (ItemInfo inf : info) {
            addISlots(totals, inf);
            addSlotted(totals, inf);
        }
    }

    private static void addAttrMod(Totals totals, ItemInfo inf) {
        if (!(inf instanceof AttrMod))
            return;
        AttrMod attrMod = (AttrMod) inf;
        if (attrMod.tab == null)
            return;
        for (haven.res.ui.tt.attrmod.Entry entry : attrMod.tab) {
            if (entry instanceof Mod) {
                Mod mod = (Mod) entry;
                totals.add(mod.attr, mod.mod);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void addISlots(Totals totals, ItemInfo inf) {
        if (inf instanceof ISlots) {
            for (ISlots.SItem slot : ((ISlots) inf).s)
                collectFromInfoList(totals, slot.info);
            return;
        }
        String name = inf.getClass().getName();
        if (name.equals("haven.res.ui.tt.slots.ISlots") || name.equals("haven.res.ui.tt.slots_alt.ISlots")) {
            try {
                java.lang.reflect.Field sField = inf.getClass().getField("s");
                Collection<?> slots = (Collection<?>) sField.get(inf);
                for (Object slot : slots) {
                    try {
                        java.lang.reflect.Field infoField = slot.getClass().getField("info");
                        collectFromInfoList(totals, (List<ItemInfo>) infoField.get(slot));
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void addSlotted(Totals totals, ItemInfo inf) {
        if (inf instanceof Slotted) {
            collectFromInfoList(totals, ((Slotted) inf).sub);
            return;
        }
        String name = inf.getClass().getName();
        if (name.equals("haven.res.ui.tt.slot.Slotted") || name.equals("haven.res.ui.tt.slot_alt.Slotted")) {
            try {
                java.lang.reflect.Field subField = inf.getClass().getField("sub");
                collectFromInfoList(totals, (List<ItemInfo>) subField.get(inf));
            } catch (Exception ignored) {
            }
        }
    }
}
