package nurgling.actions.bots;

import haven.Coord;
import haven.WItem;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.actions.Equip;
import nurgling.tasks.WaitFreeHand;
import nurgling.tools.NAlias;
import nurgling.widgets.NEquipory;

import java.util.ArrayList;
import java.util.Objects;

public final class HandLoadout {
    private static final NAlias SACKS = new NAlias("Traveller's Sack", "Wanderer's Bindle", "Traveler's Sack");

    public final String left;
    public final String right;

    public HandLoadout(String left, String right) {
        this.left = left;
        this.right = right;
    }

    public boolean isEmpty() {
        return left == null && right == null;
    }

    public boolean sameAs(HandLoadout other) {
        return other != null && Objects.equals(left, other.left) && Objects.equals(right, other.right);
    }

    public ArrayList<String> restoreEquipNames() {
        ArrayList<String> names = new ArrayList<>();
        if (left != null) {
            names.add(left);
        }
        if (right != null && !right.equals(left)) {
            names.add(right);
        }
        return names;
    }

    static String nameOf(WItem item) {
        if (item == null || !(item.item instanceof NGItem)) {
            return null;
        }
        return ((NGItem) item.item).name();
    }

    public static HandLoadout capture() throws InterruptedException {
        NEquipory eq = NUtils.getEquipment();
        if (eq == null) {
            return new HandLoadout(null, null);
        }
        return new HandLoadout(
                nameOf(eq.findItem(NEquipory.Slots.HAND_LEFT.idx)),
                nameOf(eq.findItem(NEquipory.Slots.HAND_RIGHT.idx)));
    }

    public static void restore(NGameUI gui, HandLoadout before) throws InterruptedException {
        if (before == null) {
            return;
        }
        HandLoadout now = capture();
        if (before.sameAs(now)) {
            return;
        }
        if (before.isEmpty()) {
            unequipHandsToBelt(gui);
            return;
        }
        for (String name : before.restoreEquipNames()) {
            new Equip(new NAlias(name), SACKS).run(gui);
        }
    }

    private static void unequipHandsToBelt(NGameUI gui) throws InterruptedException {
        NEquipory eq = NUtils.getEquipment();
        if (eq == null) {
            return;
        }
        WItem left = eq.findItem(NEquipory.Slots.HAND_LEFT.idx);
        WItem right = eq.findItem(NEquipory.Slots.HAND_RIGHT.idx);
        if (left != null) {
            putAway(gui, left);
        }
        WItem stillRight = eq.findItem(NEquipory.Slots.HAND_RIGHT.idx);
        if (stillRight != null && stillRight != left) {
            putAway(gui, stillRight);
        }
    }

    private static void putAway(NGameUI gui, WItem item) throws InterruptedException {
        NUtils.takeItemToHand(item);
        if (NUtils.getGameUI().vhand == null) {
            return;
        }
        NUtils.transferToBelt();
        NUtils.addTask(new WaitFreeHand());
        if (NUtils.getGameUI().vhand == null) {
            return;
        }
        NInventory inv = gui.getInventory();
        if (inv != null) {
            Coord pos = inv.getFreeCoord(NUtils.getGameUI().vhand);
            if (pos != null) {
                inv.dropOn(pos);
                NUtils.addTask(new WaitFreeHand());
            }
        }
    }
}
