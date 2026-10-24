package nurgling.actions.bots;

import haven.*;
import nurgling.NFlowerMenu;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUI;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.tasks.NTask;
import nurgling.tools.Finder;
import nurgling.tools.HarvestState;
import nurgling.tools.LpExplorer;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.tools.VSpec;
import nurgling.widgets.NCharacterInfo;
import nurgling.widgets.NEquipory;

import java.util.*;

import static haven.OCache.posres;

/**
 * Collects one of every product the LP helper (LpExplorer) still marks undiscovered on the gobs
 * in view. Harvested items remain in the player's inventory.
 */
public class LpGatherer implements Action {
    private static final NAlias SOURCES = new NAlias("gfx/terobjs/trees", "gfx/terobjs/bushes", "gfx/terobjs/bumlings");

    private static final NAlias SAWS = new NAlias("Bonesaw", "Bone Saw", "Metal Saw");
    private static final NAlias AXES = new NAlias("Woodsman's Axe", "Stone Axe", "Metal Axe", "Butcher's cleaver",
            "Tinker's Throwing Axe", "Battle Axe of the Twelfth Bay");
    private static final NAlias CHIPPERS = new NAlias("Woodsman's Axe", "Stone Axe", "Metal Axe", "Butcher's cleaver",
            "Tinker's Throwing Axe", "Battle Axe of the Twelfth Bay", "Pickaxe");

    /** A gob that never got its product credited is given up on, and a product after this many such gobs. */
    private static final int MAX_FAILS_PER_PRODUCT = 2;
    /** The player standing idle this long after the petal was chosen means the action ended (or never began). */
    private static final long IDLE_MS = 8000;
    /** An item that arrived but was not credited within this long was not the product we were after. */
    private static final long CREDIT_GRACE_MS = 8000;
    private static final long ACTION_CAP_MS = 60_000;
    private static final long MENU_WAIT_MS = 5000;
    /** A walk ends beside the gob, not at its centre, so it can come in this much under the straight line. */
    private static final double REACH_SLACK = 2 * MCache.tilesz.x;

    private enum Kind {
        LEAF("leaf", null, new Coord(1, 1)),
        SEED("seed or fruit", null, new Coord(1, 1)),
        BOUGH("bough", null, new Coord(2, 1)),
        BARK("bark", null, new Coord(1, 1)),
        BOARD("board", SAWS, new Coord(4, 1)),
        BLOCK("block", AXES, new Coord(1, 2)),
        STONE("stone", CHIPPERS, new Coord(1, 1));

        final String label;
        final NAlias tool;
        final Coord size;

        Kind(String label, NAlias tool, Coord size) {
            this.label = label;
            this.tool = tool;
            this.size = size;
        }

        static Kind of(String gobResName, String product) {
            if (gobResName.startsWith("gfx/terobjs/bumlings"))
                return STONE;
            if (product.startsWith("Board of"))
                return BOARD;
            if (product.startsWith("Block of"))
                return BLOCK;
            if (LpExplorer.isLeafProduct(product))
                return LEAF;
            if (LpExplorer.isBoughProduct(product))
                return BOUGH;
            if (HarvestState.isBarkProductName(product))
                return BARK;
            return SEED;
        }

        /** The petal that yields this product, out of the ones the gob offers, or null when none does. */
        String petal(List<String> petals, String product) {
            String known = knownPetal(petals);
            return known != null ? known : namedPetal(petals, product);
        }

        private String knownPetal(List<String> petals) {
            for (String name : petals) {
                switch (this) {
                    case LEAF: if (name.equals("Pick leaf")) return name; break;
                    case BOUGH: if (name.equals("Take bough")) return name; break;
                    case BARK: if (name.equals("Take bark")) return name; break;
                    case BOARD: if (name.equals("Make boards")) return name; break;
                    case BLOCK: if (name.equals("Chop into blocks")) return name; break;
                    case STONE: if (name.equals("Chip stone")) return name; break;
                    // Fruit petals vary; the product-named fallback below avoids a wrong pick.
                    case SEED: break;
                }
            }
            return null;
        }

        /**
         * A gathering petal that names the product, for products whose petal is not one of the
         * known ones (e.g. "Cork" from a "Take cork"). Matched on the product's last word without
         * its plural ending; only Pick/Take petals, so nothing destructive like "Chop" is chosen.
         */
        private static String namedPetal(List<String> petals, String product) {
            String[] words = product.toLowerCase(Locale.ROOT).split("\\s+");
            String key = words[words.length - 1].replaceAll("(es|s)$", "");
            if (key.length() < 3)
                return null;
            for (String petal : petals) {
                String name = petal.toLowerCase(Locale.ROOT);
                if (name.startsWith("pick ") || name.startsWith("take "))
                    for (String word : name.split("[^a-z]+"))
                        if (word.replaceAll("(es|s)$", "").equals(key))
                            return petal;
            }
            return null;
        }
    }

    enum Outcome { DISCOVERED, GONE, NO_MENU, NO_PETAL, IDLE, NOT_CREDITED, TIMEOUT }

    static String petalFor(String gobResource, String product, List<String> petals) {
        return Kind.of(gobResource, product).petal(petals, product);
    }

    private static class Target {
        final Gob gob;
        final String product;
        final Kind kind;

        Target(Gob gob, String product, Kind kind) {
            this.gob = gob;
            this.product = product;
            this.kind = kind;
        }
    }

    private final Set<Long> unreachable = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    private final Map<String, Integer> failsPerProduct = new HashMap<>();
    private final Set<String> skipped = new TreeSet<>();
    private final List<String> found = new ArrayList<>();
    /** Why each product that was tried and not credited failed, for the closing summary. */
    private final Map<String, String> problems = new TreeMap<>();
    /** The petals the last NO_PETAL menu offered. */
    private List<String> offered = Collections.emptyList();
    private OwnedTool ownedTool;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        // The bot menu reuses one instance, so every run starts from a clean slate.
        unreachable.clear();
        failed.clear();
        failsPerProduct.clear();
        found.clear();
        problems.clear();
        offered = Collections.emptyList();
        ownedTool = null;
        if (gui == null || gui.vhand != null)
            return Results.ERROR("LP Gatherer: clear the cursor before starting; held items are not dropped");
        if (gui.map == null || gui.map.placing != null)
            return Results.ERROR("LP Gatherer: finish the current placement before starting");
        if (gui == null || gui.ui == null || gui.getCharInfo() == null || gui.getCharInfo().chrid == null)
            return Results.ERROR("LP Gatherer needs loaded character information");
        try {
        while (true) {
            skipped.clear();
            Target target = nextTarget(gui);
            if (target == null)
                break;

            if (!new RestoreResources().run(gui).IsSuccess())
                return Results.ERROR("Failed to restore resources");
            if (!new PathFinder(target.gob).run(gui).IsSuccess()) {
                unreachable.add(target.gob.id);
                continue;
            }
            if (target.kind.tool != null) {
                if (!prepareTool(gui, target.kind.tool)) {
                    if (ownedTool != null || gui.vhand != null)
                        return Results.ERROR("LP Gatherer: tool could not be moved safely; item was kept");
                    fail(target, "could not equip a tool for it");
                    continue;
                }
            }

            Outcome outcome = harvest(gui, target);
            String abort = abortReason(outcome);
            if (abort != null)
                return Results.ERROR(abort);
            switch (outcome) {
                case DISCOVERED: found.add(target.product); problems.remove(target.product); break;
                case NO_PETAL: fail(target, "no matching menu option on " + target.gob.ngob.name + ", it offered " + offered); break;
                default: break;
            }
        }

        String summary = found.isEmpty() ? "nothing new found" : "found " + String.join(", ", found);
        if (!skipped.isEmpty())
            summary += ". Skipped: " + String.join("; ", skipped);
        if (!problems.isEmpty()) {
            List<String> lines = new ArrayList<>();
            problems.forEach((product, why) -> lines.add(product + ": " + why));
            summary += ". Failed: " + String.join("; ", lines);
        }
        gui.msg("LP Gatherer: " + summary);
        return Results.SUCCESS();
        } finally {
            if (ownedTool != null && !returnOwnedTool(gui))
                return Results.ERROR("LP Gatherer: borrowed tool could not be returned and was kept in hand or equipment");
        }
    }

    /**
     * The gob in view with an undiscovered product we can still collect that is the shortest walk
     * away. Products we lack a tool or inventory room for are named in {@link #skipped}.
     * <p>
     * Gobs are taken in straight-line order and each one's walk is planned; a walk is never
     * shorter than the straight line (less the reach to the gob's edge), so the search ends at the
     * first gob whose straight line is already longer than the best walk, and only the nearest few
     * get a path planned.
     */
    private Target nextTarget(NGameUI gui) throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null)
            return null;
        ArrayList<Gob> gobs = Finder.findGobs(player.rc, SOURCES, null, Double.MAX_VALUE);
        gobs.removeIf(g -> g.ngob == null || !VSpec.object.containsKey(g.ngob.name) || unreachable.contains(g.id));
        Map<Gob, List<String>> products = undiscovered(gobs);
        gobs.removeIf(g -> !products.containsKey(g));
        gobs.sort(NUtils.d_comp);

        Map<Kind, Boolean> doable = new EnumMap<>(Kind.class);
        Target best = null;
        double bestWalk = Double.POSITIVE_INFINITY;
        for (Gob gob : gobs) {
            if (gob.rc.dist(player.rc) - REACH_SLACK >= bestWalk)
                break;
            Target target = null;
            for (String product : products.get(gob)) {
                if (failed.contains(failKey(gob, product)) || failsPerProduct.getOrDefault(product, 0) >= MAX_FAILS_PER_PRODUCT)
                    continue;
                Kind kind = Kind.of(gob.ngob.name, product);
                Boolean ok = doable.get(kind);
                if (ok == null)
                    doable.put(kind, ok = canDo(gui, kind));
                if (ok) {
                    target = new Target(gob, product, kind);
                    break;
                }
            }
            if (target == null)
                continue;
            double walk = PathFinder.walkLength(gob);
            if (walk == Double.POSITIVE_INFINITY) {
                unreachable.add(gob.id);
            } else if (walk < bestWalk) {
                best = target;
                bestWalk = walk;
            }
        }
        return best;
    }

    /** A loading sprite cannot stall scanning every other gob. */
    private static Map<Gob, List<String>> undiscovered(List<Gob> gobs) throws InterruptedException {
        Map<Gob, List<String>> res = new HashMap<>();
        for (Gob gob : gobs) {
            final List<String>[] products = new List[]{Collections.emptyList()};
            long deadline = System.currentTimeMillis() + 1500;
            NUtils.addTask(new NTask() {
                @Override public boolean check() {
                    try {
                        products[0] = LpExplorer.allUndiscoveredProducts(gob);
                        return true;
                    } catch (Loading loading) {
                        return System.currentTimeMillis() >= deadline;
                    }
                }
            });
            if (!products[0].isEmpty())
                res.put(gob, products[0]);
        }
        return res;
    }

    private boolean canDo(NGameUI gui, Kind kind) throws InterruptedException {
        if (kind.tool != null && !hasTool(gui, kind.tool)) {
            skipped.add(kind.label + " (no tool in hands or on the belt)");
            return false;
        }
        if (gui.getInventory().findFreeCoord(kind.size) == null) {
            skipped.add(kind.label + " (no room in the inventory)");
            return false;
        }
        return true;
    }

    /** Harvest once and leave every acquired item in the inventory. */
    private Outcome harvest(NGameUI gui, Target target) throws InterruptedException {
        Gob gob = target.gob;
        NFlowerMenu fm = openMenu(gui, gob);
        if (fm == null)
            return Outcome.NO_MENU;
        List<String> menuNames = new ArrayList<>();
        for (NFlowerMenu.NPetal option : fm.nopts)
            menuNames.add(option.name);
        String petal = petalFor(gob.ngob.name, target.product, menuNames);
        if (petal == null) {
            offered = new ArrayList<>();
            for (NFlowerMenu.NPetal p : fm.nopts)
                offered.add(p.name);
            closeOwnMenu(fm);
            return Outcome.NO_PETAL;
        }
        NCharacterInfo character = gui.getCharInfo();
        if (character == null || character.chrid == null) {
            closeOwnMenu(fm);
            return Outcome.TIMEOUT;
        }
        String resource = HarvestState.isBarkProductName(target.product)
                ? "bark:" + target.product : gob.ngob.name;
        LpExplorer.BotHarvest token = LpExplorer.armBotHarvest(gui.ui, character.chrid,
                resource, target.product, System.currentTimeMillis());
        int freeBefore = gui.getInventory().calcFreeSpace();
        try {
            if (!fm.chooseOpt(petal))
                return Outcome.NO_PETAL;
            Object ourAction = gui.ui.core != null ? gui.ui.core.getLastActions() : null;
            if (!awaitClosed(fm))
                return Outcome.TIMEOUT;
            WaitCredit wait = new WaitCredit(gui, gob, target.product, freeBefore);
            NUtils.addTask(wait);
            // Cancel only while our chosen action is still the last action in this session.
            if (gui.ui.core != null && ourAction != null && gui.ui.core.getLastActions() == ourAction)
                stopAction(gui);
            return wait.outcome;
        } finally {
            LpExplorer.clearBotHarvest(gui.ui, token);
        }
    }

    /**
     * Never bind an existing menu or issue a second click after a timeout: either could belong
     * to another gob or the player's own action.
     */
    private static NFlowerMenu openMenu(NGameUI gui, Gob gob) throws InterruptedException {
        if (gui.vhand != null || gui.map == null || gui.map.placing != null)
            return null;
        if (!(gui.ui instanceof NUI) || ((NUI) gui.ui).findInRoot(NFlowerMenu.class) != null)
            return null;
        gui.map.wdgmsg("click", Coord.z, gob.rc.floor(posres), 3, 0, 1, (int) gob.id, gob.rc.floor(posres), 0, -1);
        return awaitMenu(gui);
    }

    /** The flower menu once it shows, or null after MENU_WAIT_MS; timed by the clock, not by ticks, so lag can't cut it short. */
    private static NFlowerMenu awaitMenu(NGameUI gui) throws InterruptedException {
        long since = System.currentTimeMillis();
        NFlowerMenu[] res = {null};
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                NFlowerMenu fm = (NFlowerMenu) ((NUI) gui.ui).findInRoot(NFlowerMenu.class);
                if (fm != null && fm.nopts != null && fm.nopts.length > 0) {
                    res[0] = fm;
                    return true;
                }
                return System.currentTimeMillis() - since > MENU_WAIT_MS;
            }
        });
        return res[0];
    }

    private static void closeOwnMenu(NFlowerMenu fm) throws InterruptedException {
        if (fm == null || fm.parent == null)
            return;
        fm.wdgmsg("cl", -1);
        awaitClosed(fm);
    }

    private static boolean awaitClosed(NFlowerMenu fm) throws InterruptedException {
        long deadline = System.currentTimeMillis() + MENU_WAIT_MS;
        NUtils.addTask(new NTask() {
            @Override public boolean check() {
                return fm.parent == null || System.currentTimeMillis() >= deadline;
            }
        });
        return fm.parent == null;
    }

    /** Ends whatever the player is still doing (one item is all we wanted) by stepping onto its own tile. */
    private static void stopAction(NGameUI gui) throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null || isIdle(player))
            return;
        gui.map.wdgmsg("click", Coord.z, player.rc.floor(posres), 1, 0);
        long since = System.currentTimeMillis();
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                return isIdle(player) || System.currentTimeMillis() - since > IDLE_MS;
            }
        });
    }

    /** Real elapsed-time state shared by the live wait and tests; a vanished source can still yield a late item. */
    static final class CreditWindow {
        private final long start;
        private int freeAtStart = -1;
        private long idleSince = -1, itemAt = -1, goneAt = -1;

        CreditWindow(long start) { this(start, -1); }

        CreditWindow(long start, int freeBefore) {
            this.start = start;
            this.freeAtStart = freeBefore;
        }

        Outcome observe(long now, boolean discovered, boolean present, int free, boolean idle) {
            if (discovered)
                return Outcome.DISCOVERED;
            if (now - start >= ACTION_CAP_MS)
                return itemAt >= 0 ? Outcome.NOT_CREDITED : Outcome.TIMEOUT;
            if (!present && goneAt < 0)
                goneAt = now;
            if (free >= 0) {
                if (freeAtStart < 0)
                    freeAtStart = free;
                else if (free < freeAtStart && itemAt < 0)
                    itemAt = now;
            }
            // A received item may still be waiting for its resource/name. Keep the bot token
            // alive until the overall cap rather than treating loading as a failed product.
            if (itemAt >= 0)
                return null;
            if (goneAt >= 0 && now - goneAt >= CREDIT_GRACE_MS)
                return Outcome.GONE;
            if (goneAt >= 0)
                return null;
            if (idle) {
                if (idleSince < 0)
                    idleSince = now;
                else if (now - idleSince >= IDLE_MS)
                    return Outcome.IDLE;
            } else {
                idleSince = -1;
            }
            return null;
        }
    }

    private static class WaitCredit extends NTask {
        private final NGameUI gui;
        private final Gob gob;
        private final Gob player = NUtils.player();
        private final String product;
        private final CreditWindow window;
        Outcome outcome = Outcome.TIMEOUT;

        WaitCredit(NGameUI gui, Gob gob, String product, int freeBefore) {
            this.gui = gui;
            this.gob = gob;
            this.product = product;
            this.window = new CreditWindow(System.currentTimeMillis(), freeBefore);
        }

        @Override
        public boolean check() {
            long now = System.currentTimeMillis();
            NCharacterInfo character = gui.getCharInfo();
            if (character == null || character.chrid == null)
                return done(Outcome.TIMEOUT);
            Outcome result = window.observe(now,
                    !LpExplorer.isProductUndiscovered(character, gob.ngob.name, product),
                    Finder.findGob(gob.id) != null,
                    gui.getInventory().calcFreeSpace(), player != null && isIdle(player));
            return result != null && done(result);
        }

        private boolean done(Outcome outcome) {
            this.outcome = outcome;
            return true;
        }
    }

    private static boolean isIdle(Gob player) {
        String pose = player.pose();
        return pose != null && pose.contains("gfx/borka/idle");
    }

    private void fail(Target target, String why) {
        failed.add(failKey(target.gob, target.product));
        failsPerProduct.merge(target.product, 1, Integer::sum);
        problems.put(target.product, why);
        System.out.println("LpGatherer: " + target.product + " from " + target.gob.ngob.name + " failed: " + why);
    }

    private static String failKey(Gob gob, String product) {
        return gob.id + "|" + product;
    }

    /** A delayed menu or product may still arrive; never issue a click on another source then. */
    static String abortReason(Outcome outcome) {
        switch (outcome) {
            case GONE: return "LP Gatherer: source vanished without confirmed discovery";
            case NO_MENU: return "LP Gatherer: menu did not open; a late menu may still arrive";
            case IDLE: return "LP Gatherer: action stopped without confirmed discovery";
            case NOT_CREDITED: return "LP Gatherer: received item was not credited";
            case TIMEOUT: return "LP Gatherer: harvest timed out";
            default: return null;
        }
    }

    private static class OwnedTool {
        final GItem item;
        final NInventory belt;
        final Coord size;

        OwnedTool(WItem source, NInventory belt) {
            this.item = source.item;
            this.belt = belt;
            this.size = source.item.spr.sz().div(UI.scale(32));
        }
    }

    private static WItem[] hands(NGameUI gui) {
        NEquipory equip = equipment(gui);
        if (equip == null)
            return new WItem[]{null, null};
        return new WItem[]{equip.quickslots[NEquipory.Slots.HAND_LEFT.idx],
                equip.quickslots[NEquipory.Slots.HAND_RIGHT.idx]};
    }

    private static NEquipory equipment(NGameUI gui) {
        if (gui.equwnd == null)
            return null;
        for (Widget child = gui.equwnd.lchild; child != null; child = child.prev)
            if (child instanceof NEquipory)
                return (NEquipory) child;
        return null;
    }

    private static boolean matches(WItem item, NAlias name) {
        try {
            return item != null && item.item instanceof NGItem
                    && NParser.checkName(((NGItem) item.item).name(), name);
        } catch (Loading loading) {
            return false;
        }
    }

    private static boolean inHands(NGameUI gui, NAlias tool) {
        for (WItem hand : hands(gui))
            if (matches(hand, tool))
                return true;
        return false;
    }

    private static NInventory beltInventory(NGameUI gui) {
        NEquipory equip = equipment(gui);
        if (equip == null)
            return null;
        WItem belt = equip.quickslots[NEquipory.Slots.BELT.idx];
        return belt != null && belt.item.contents instanceof NInventory
                ? (NInventory) belt.item.contents : null;
    }

    private static WItem beltTool(NInventory belt, NAlias tool) {
        if (belt == null)
            return null;
        for (Widget child = belt.child; child != null; child = child.next)
            if (child instanceof WItem && matches((WItem) child, tool))
                return (WItem) child;
        return null;
    }

    private boolean hasTool(NGameUI gui, NAlias tool) {
        if (inHands(gui, tool))
            return true;
        WItem[] hands = hands(gui);
        if (!canBorrowTool(gui.vhand, hands[0], hands[1], ownedTool == null ? null : ownedTool.item))
            return false;
        return beltTool(beltInventory(gui), tool) != null;
    }

    static boolean canBorrowTool(WItem cursor, WItem left, WItem right, GItem owned) {
        return cursor == null && (left == null || left.item == owned && owned != null)
                && (right == null || right.item == owned && owned != null);
    }

    private boolean prepareTool(NGameUI gui, NAlias tool) throws InterruptedException {
        if (inHands(gui, tool))
            return true;
        if (ownedTool != null && !returnOwnedTool(gui))
            return false;
        if (gui.vhand != null || hands(gui)[0] != null || hands(gui)[1] != null)
            return false;
        NInventory belt = beltInventory(gui);
        WItem source = beltTool(belt, tool);
        if (source == null || source.item.spr == null)
            return false;
        ownedTool = new OwnedTool(source, belt);
        source.item.wdgmsg("take", Coord.z);
        if (!await(() -> gui.vhand != null && gui.vhand.item == ownedTool.item, 3000))
            return false;
        if (hands(gui)[0] != null || hands(gui)[1] != null)
            return false;
        NEquipory equip = equipment(gui);
        if (equip == null)
            return false;
        equip.wdgmsg("drop", -1);
        return await(() -> ownedInHands(gui), 3000) && gui.vhand == null;
    }

    private boolean ownedInHands(NGameUI gui) {
        if (ownedTool == null)
            return false;
        for (WItem hand : hands(gui))
            if (hand != null && hand.item == ownedTool.item)
                return true;
        return false;
    }

    private boolean returnOwnedTool(NGameUI gui) throws InterruptedException {
        OwnedTool owned = ownedTool;
        if (owned == null)
            return true;
        if (gui.vhand != null && gui.vhand.item != owned.item)
            return false;
        if (gui.vhand == null) {
            WItem held = null;
            for (WItem hand : hands(gui))
                if (hand != null && hand.item == owned.item)
                    held = hand;
            if (held == null) {
                ownedTool = null; // The player moved it; never take another item in its place.
                return true;
            }
            held.item.wdgmsg("take", Coord.z);
            if (!await(() -> gui.vhand != null && gui.vhand.item == owned.item, 3000))
                return false;
        }
        Coord free = owned.belt.findFreeCoord(owned.size);
        NInventory destination = free != null ? owned.belt : gui.getInventory();
        if (free == null)
            free = destination.findFreeCoord(owned.size);
        if (free == null)
            return false;
        destination.wdgmsg("drop", free);
        NInventory receiptInventory = destination;
        if (!await(() -> toolReturnReceipt(owned.item, gui.vhand, receiptInventory), 3000))
            return false;
        ownedTool = null;
        return true;
    }

    static boolean toolReturnReceipt(GItem expected, WItem cursor, NInventory destination) {
        if (expected == null || cursor != null || destination == null)
            return false;
        for (Widget child = destination.child; child != null; child = child.next)
            if (child instanceof WItem && ((WItem) child).item == expected)
                return true;
        return false;
    }

    private static boolean await(java.util.function.BooleanSupplier ready, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        NUtils.addTask(new NTask() {
            @Override public boolean check() {
                return ready.getAsBoolean() || System.currentTimeMillis() >= deadline;
            }
        });
        return ready.getAsBoolean();
    }
}
