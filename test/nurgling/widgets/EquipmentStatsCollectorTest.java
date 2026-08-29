package nurgling.widgets;

import haven.ItemInfo;
import haven.OwnerContext;
import haven.res.ui.tt.attrmod.AttrMod;
import haven.res.ui.tt.attrmod.Attribute;
import haven.res.ui.tt.attrmod.Mod;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentStatsCollectorTest {
    @Test
    void emptySourcesYieldEmptyTotals() {
        EquipmentStatsCollector.Totals totals = EquipmentStatsCollector.collect(Collections.emptyList());

        assertTrue(totals.isEmpty());
        assertTrue(totals.mods().isEmpty());
    }

    @Test
    void twoItemsWithSameAttributeAreSummed() {
        Attribute strength = attr("Strength");
        EquipmentStatsCollector.Source coat = source("coat", attrMod(strength, 20));
        EquipmentStatsCollector.Source gloves = source("gloves", attrMod(strength, 29));

        EquipmentStatsCollector.Totals totals = EquipmentStatsCollector.collect(Arrays.asList(coat, gloves));

        assertEquals(49.0, totals.value("Strength"));
        assertEquals(1, totals.sorted().size());
        assertEquals("Strength", totals.sorted().get(0).getKey());
    }

    @Test
    void differentAttributesStaySeparateAndSortByAbsValue() {
        Attribute strength = attr("Strength");
        Attribute melee = attr("Melee Combat");
        Attribute lore = attr("Lore");
        EquipmentStatsCollector.Source armor = source("armor", attrMod(strength, 10), attrMod(melee, 32));
        EquipmentStatsCollector.Source hat = source("hat", attrMod(lore, 3), attrMod(strength, 5));

        EquipmentStatsCollector.Totals totals = EquipmentStatsCollector.collect(Arrays.asList(armor, hat));

        assertEquals(15.0, totals.value("Strength"));
        assertEquals(32.0, totals.value("Melee Combat"));
        assertEquals(3.0, totals.value("Lore"));
        assertEquals("Melee Combat", totals.sorted().get(0).getKey());
        assertEquals("Strength", totals.sorted().get(1).getKey());
        assertEquals("Lore", totals.sorted().get(2).getKey());
    }

    @Test
    void duplicateIdentityIsCountedOnce() {
        Attribute agility = attr("Agility");
        ItemInfo mod = attrMod(agility, 17);
        Object sameItem = new Object();
        EquipmentStatsCollector.Source left = source(sameItem, mod);
        EquipmentStatsCollector.Source right = source(sameItem, mod);

        EquipmentStatsCollector.Totals totals = EquipmentStatsCollector.collect(Arrays.asList(left, right));

        assertEquals(17.0, totals.value("Agility"));
    }

    @Test
    void multipleAttrModsOnOneItemAreSummed() {
        Attribute survival = attr("Survival");
        EquipmentStatsCollector.Source item = source("gilded",
                attrMod(survival, 35),
                attrMod(survival, 12));

        assertEquals(47.0, EquipmentStatsCollector.collect(Collections.singletonList(item)).value("Survival"));
    }

    @Test
    void infoListsHookTreatsEachListAsItsOwnItem() {
        Attribute perception = attr("Perception");
        List<ItemInfo> first = Collections.singletonList(attrMod(perception, 1));
        List<ItemInfo> second = Collections.singletonList(attrMod(perception, 4));

        assertEquals(5.0, EquipmentStatsCollector.collectFromInfoLists(Arrays.asList(first, second)).value("Perception"));
        assertTrue(EquipmentStatsCollector.collectFromInfoLists(Collections.emptyList()).isEmpty());
    }

    @Test
    void wardrobeInfosDoNotUsePlayerEquiporyInfos() {
        Attribute melee = attr("Melee Combat");
        EquipmentStatsCollector.Totals doll = EquipmentStatsCollector.collectFromInfoLists(
                Collections.singletonList(Collections.singletonList(attrMod(melee, 32))));
        EquipmentStatsCollector.Totals worn = EquipmentStatsCollector.collectFromInfoLists(
                Collections.singletonList(Collections.singletonList(attrMod(melee, 99))));

        assertEquals(32.0, doll.value("Melee Combat"));
        assertEquals(99.0, worn.value("Melee Combat"));
    }

    private static EquipmentStatsCollector.Source source(Object id, ItemInfo... infos) {
        List<ItemInfo> list = Arrays.asList(infos);
        return new EquipmentStatsCollector.Source() {
            @Override
            public Object identity() {
                return id;
            }

            @Override
            public List<ItemInfo> info() {
                return list;
            }
        };
    }

    private static AttrMod attrMod(Attribute attr, double value) {
        return new AttrMod(owner(), Collections.singletonList(new Mod(attr, value)));
    }

    private static ItemInfo.Owner owner() {
        return new ItemInfo.Owner() {
            @Override
            public List<ItemInfo> info() {
                return Collections.emptyList();
            }

            @Override
            public <T> T context(Class<T> cl) {
                throw new OwnerContext.NoContext(cl);
            }
        };
    }

    private static Attribute attr(String name) {
        return new Attribute() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public BufferedImage icon() {
                return new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            }

            @Override
            public String format(double val) {
                return Double.toString(val);
            }
        };
    }
}
