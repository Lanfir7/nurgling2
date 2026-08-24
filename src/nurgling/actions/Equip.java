package nurgling.actions;

import haven.Coord;
import haven.Inventory;
import haven.WItem;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.tasks.WaitItemInHand;
import nurgling.tasks.WaitItemInEquip;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.tools.QualityPick;
import nurgling.widgets.NEquipory;

import java.util.ArrayList;
import java.util.HashSet;

public class Equip implements Action {

    NAlias target_name;
    NAlias exception = null;
    NInventory.QualityType qualityType = null;

    public Equip(NAlias target_name) {
        this.target_name = target_name;
    }

    public Equip(NAlias target_name, NAlias exception) {
        this.target_name = target_name;
        this.exception = exception;
    }

    public Equip(NAlias target_name, NInventory.QualityType qualityType) {
        this.target_name = target_name;
        this.qualityType = qualityType;
    }

    public Equip(NAlias target_name, NAlias exception, NInventory.QualityType qualityType) {
        this.target_name = target_name;
        this.exception = exception;
        this.qualityType = qualityType;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if(target_name.keys.contains("Traveller's Sack")) {
            target_name.keys.add("Traveler's Sack");
            target_name.buildCaches();
        } else if (target_name.keys.contains("Traveler's Sack")) {
            target_name.keys.add("Traveller's Sack");
            target_name.buildCaches();
        }
        WItem lhand = NUtils.getEquipment().findItem (NEquipory.Slots.HAND_LEFT.idx);

        WItem rhand = NUtils.getEquipment().findItem (NEquipory.Slots.HAND_RIGHT.idx);
        WItem wbelt = NUtils.getEquipment().findItem (NEquipory.Slots.BELT.idx);
        NInventory beltInv = (wbelt != null && wbelt.item.contents instanceof NInventory)
                ? (NInventory) wbelt.item.contents : null;

        if (qualityType == NInventory.QualityType.High) {
            ArrayList<Double> qualities = new ArrayList<>();
            ArrayList<Boolean> equippedFlags = new ArrayList<>();
            if (matches(lhand)) {
                qualities.add(QualityPick.orZero(((NGItem) lhand.item).quality));
                equippedFlags.add(true);
            }
            if (matches(rhand) && rhand != lhand) {
                qualities.add(QualityPick.orZero(((NGItem) rhand.item).quality));
                equippedFlags.add(true);
            }
            WItem beltBest = beltInv != null ? beltInv.getItem(target_name, qualityType) : null;
            if (beltBest != null) {
                qualities.add(QualityPick.orZero(((NGItem) beltBest.item).quality));
                equippedFlags.add(false);
            }
            if (qualities.isEmpty()) {
                return Results.ERROR("No target item");
            }
            double[] qarr = new double[qualities.size()];
            boolean[] earr = new boolean[qualities.size()];
            for (int i = 0; i < qualities.size(); i++) {
                qarr[i] = qualities.get(i);
                earr[i] = equippedFlags.get(i);
            }
            if (earr[QualityPick.highest(qarr, earr)]) {
                return Results.SUCCESS();
            }
        } else if((lhand!=null && NParser.checkName(((NGItem)lhand.item).name(), target_name) || (rhand!=null && NParser.checkName(((NGItem)rhand.item).name(),target_name))))
        {
            return Results.SUCCESS();
        }
        if(beltInv != null) {
                WItem witem = qualityType != null
                        ? beltInv.getItem(target_name, qualityType)
                        : beltInv.getItem(target_name);
                if(witem != null) {
                    if (isTwoHanded(witem) && ((lhand != null && rhand != null && lhand != rhand && !isTwoHanded(lhand)))) {
                        NUtils.takeItemToHand(rhand);
                        if (beltInv.getFreeSpace() == 0) {
                            WItem item = NUtils.getGameUI().vhand;
                            Coord pos = NUtils.getGameUI().getInventory().getFreeCoord(item);
                            gui.getInventory().dropOn(pos, ((NGItem) item.item).name());
                        } else {
                            NUtils.transferToBelt();
                        }

                        NUtils.takeItemToHand(lhand);
                        beltInv.dropOn(witem.c.div(Inventory.sqsz));
                        NUtils.getUI().core.addTask(new WaitItemInHand(witem));
                        NUtils.getEquipment().wdgmsg("drop", -1);
                    } else {
                        if ((rhand == null && lhand == null) || (!isTwoHanded(witem) && (rhand == null || lhand == null))) {
                            NUtils.takeItemToHand(witem);
                            NUtils.getEquipment().wdgmsg("drop", -1);
                        } else {

                            if(lhand!=null && !NParser.checkName(((NGItem)lhand.item).name(), exception))
                            {
                                NUtils.takeItemToHand(lhand);
                                beltInv.dropOn(witem.c.div(Inventory.sqsz));
                                NUtils.getUI().core.addTask(new WaitItemInHand(witem));
                                NUtils.getEquipment().wdgmsg("drop", -1);

                            }
                            else
                            {
                                NUtils.takeItemToHand(rhand);
                                beltInv.dropOn(witem.c.div(Inventory.sqsz));
                                NUtils.getUI().core.addTask(new WaitItemInHand(witem));
                                NUtils.getEquipment().wdgmsg("drop", -1);
                            }
                        }
                    }
                    NUtils.getUI().core.addTask(new WaitItemInEquip(witem,new NEquipory.Slots[]{NEquipory.Slots.HAND_LEFT, NEquipory.Slots.HAND_RIGHT}));
                }
                else {
                        return Results.ERROR("No target item");
                }

        }

        return Results.SUCCESS();
    }

    private boolean matches(WItem item) {
        return item != null && NParser.checkName(((NGItem) item.item).name(), target_name);
    }

    boolean isTwoHanded(WItem item)
    {
        HashSet<String> items = new HashSet<>();
        items.add("Scythe");
        items.add("Pickaxe");
        items.add("Glass Blowing Rod");
        items.add("Boar Spear");
        items.add("Metal Shovel");
        items.add("Tinker's Shovel");
        items.add("Wooden Shovel");
        items.add("Dowsing Rod");
        items.add("Battle Axe of the Twelfth Bay");
        items.add("Cutblade");
        return items.contains(((NGItem)item.item).name());
    }
}
