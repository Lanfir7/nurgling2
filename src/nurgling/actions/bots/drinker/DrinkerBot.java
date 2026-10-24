package nurgling.actions.bots.drinker;

import haven.*;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NCore;
import nurgling.NInventory;
import nurgling.NFlowerMenu;
import nurgling.NUI;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.conf.NDrinkerProp;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitCheckable;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.widgets.bots.DrinkerWnd;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Drinks from checked cups, returning only cups fetched by this run. */
public class DrinkerBot implements Action {
    public static final String SETTING_PREFIX = "sips:";
    public static final String CUP_PREFIX = "cup:";
    private static final long RECEIPT_MS = 5000;
    private static final long CONTENT_MS = 8000;
    private static final long SIP_MS = 22000;
    private static final Pattern AMOUNT = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)\\s+.+\\s+of\\s+.+$");

    private final Map<String, Integer> presetSips;
    private final Map<String, String> presetCups;
    private final boolean scenario;
    private final String settingsError;
    private boolean uncertain;

    public DrinkerBot() { this(Map.of(), false); }
    public DrinkerBot(Map<String, Object> settings) { this(settings, false); }

    public DrinkerBot(Map<String, Object> settings, boolean scenario) {
        this.scenario = scenario;
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> cups = new LinkedHashMap<>();
        String problem = null;
        if (settings != null) for (Map.Entry<String, Object> e : settings.entrySet()) {
            if (e.getKey().startsWith(SETTING_PREFIX)) {
                String drink = e.getKey().substring(SETTING_PREFIX.length());
                if (Vessels.drink(drink) == null || !(e.getValue() instanceof Number)
                        || ((Number) e.getValue()).doubleValue() < 0
                        || ((Number) e.getValue()).doubleValue() > Integer.MAX_VALUE
                        || ((Number) e.getValue()).doubleValue() != ((Number) e.getValue()).intValue())
                    problem = "invalid sip count for " + drink;
                else counts.put(drink, ((Number) e.getValue()).intValue());
            } else if (e.getKey().startsWith(CUP_PREFIX)) {
                String drink = e.getKey().substring(CUP_PREFIX.length());
                if (Vessels.drink(drink) == null || !(e.getValue() instanceof String)
                        || !Vessels.isCup((String) e.getValue()))
                    problem = "invalid cup for " + drink;
                else cups.put(drink, (String) e.getValue());
            }
        }
        this.presetSips = Collections.unmodifiableMap(counts);
        this.presetCups = Collections.unmodifiableMap(cups);
        this.settingsError = problem;
    }

    static final class Order {
        final String drink;
        final String cup;
        final int sips;
        Order(String drink, String cup, int sips) { this.drink = drink; this.cup = cup; this.sips = sips; }
    }

    static final class Cup {
        final String name;
        final Container origin; // null for a cup already carried by the player
        GItem item;
        Gob filledAt;
        Cup(String name, Container origin, GItem item) {
            this.name = name; this.origin = origin; this.item = item;
        }
    }

    static List<Order> active(Map<String, Integer> sips, Map<String, String> cups) {
        List<Order> result = new ArrayList<>();
        for (Vessels.Drink d : Vessels.DRINKS) {
            Integer count = sips.get(d.name);
            if (count != null && count > 0)
                result.add(new Order(d.name, cups.getOrDefault(d.name, d.cup), count));
        }
        return result;
    }

    boolean configured() {
        return settingsError == null && !active(presetSips, presetCups).isEmpty();
    }

    @Override public Results run(NGameUI gui) throws InterruptedException {
        uncertain = false;
        List<Order> orders;
        if (scenario || !presetSips.isEmpty()) {
            if (!configured()) return Results.ERROR("Drinker: " + (settingsError == null ? "set at least one valid drink above 0 sips" : settingsError));
            orders = active(presetSips, presetCups);
            List<String> issues = findProblems(orders);
            if (!issues.isEmpty()) return Results.ERROR("Drinker: " + String.join("; ", issues));
        } else {
            orders = askOrders(gui);
            if (orders == null) return Results.FAIL();
        }
        if (!readyForAction(gui)) return Results.ERROR("Drinker: put the held item or placement/menu away first");

        List<Cup> fetched = new ArrayList<>();
        String leftover = null;
        Results result;
        try {
            result = perform(gui, orders, fetched);
        } finally {
            // This never selects cups by name alone: only receipts of cups fetched by this run.
            List<Cup> safeReturns = returnPlan(uncertain, fetched);
            leftover = uncertain ? "an action is unconfirmed; fetched cups were left with the character"
                    : returnFetched(gui, safeReturns);
        }
        if (leftover != null) return Results.ERROR("Drinker: " + leftover);
        return result;
    }

    static List<Cup> returnPlan(boolean uncertain, List<Cup> fetched) {
        return uncertain ? List.of() : List.copyOf(fetched);
    }

    private Results perform(NGameUI gui, List<Order> orders, List<Cup> fetched) throws InterruptedException {
        Map<Order, Cup> assigned = new LinkedHashMap<>();
        Set<GItem> reserved = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Order order : orders) {
            if (!readyForAction(gui)) return Results.ERROR("Drinker: another item, placement or menu is active");
            Cup cup = null;
            for (WItem w : exactCups(gui.getInventory(), order.cup)) {
                if (reserved.contains(w.item) || !knownEmpty(w)) continue;
                cup = new Cup(order.cup, null, w.item);
                break;
            }
            if (cup == null) {
                if (NContext.findInGlobal(order.cup) == null)
                    return Results.ERROR("Drinker: no TAKE area for " + order.cup);
                if (gui.getInventory().getNumberFreeCoord(new Coord(1, 1)) <= 0)
                    return Results.ERROR("Drinker: inventory is full; cups and other items remain untouched");
                cup = fetchOne(gui, order.cup);
                if (cup == null) return Results.ERROR("Drinker: no confirmed empty " + order.cup + " was fetched");
                fetched.add(cup);
            }
            reserved.add(cup.item);
            assigned.put(order, cup);
        }
        List<String> report = new ArrayList<>();
        for (Map.Entry<Order, Cup> e : assigned.entrySet()) {
            String line = drink(gui, e.getKey(), e.getValue());
            report.add(line);
            if (!line.startsWith("ok:")) {
                gui.msg("Drinker: " + String.join(", ", report));
                return Results.ERROR("Drinker: " + line);
            }
        }
        gui.msg("Drinker: " + String.join(", ", report).replace("ok:", ""));
        return Results.SUCCESS();
    }

    private List<Order> askOrders(NGameUI gui) throws InterruptedException {
        NDrinkerProp prop = NDrinkerProp.get(NUtils.getUI().sessInfo);
        Map<String, Integer> startSips = prop != null ? prop.sips : Map.of();
        Map<String, String> startCups = prop != null ? prop.cups : Map.of();
        List<String> problems = List.of();
        while (true) {
            DrinkerWnd window = new DrinkerWnd(startSips, startCups, problems);
            try { NUtils.getUI().core.addTask(new WaitCheckable(gui.add(window, UI.scale(200, 200)))); }
            finally { window.destroy(); }
            if (window.sips == null) return null;
            List<Order> orders = active(window.sips, window.cups);
            problems = orders.isEmpty() ? List.of("Set at least one drink above 0 sips") : findProblems(orders);
            if (problems.isEmpty()) {
                if (prop != null) {
                    prop.sips.clear(); prop.sips.putAll(window.sips);
                    prop.cups.clear(); prop.cups.putAll(window.cups);
                    NDrinkerProp.set(prop);
                }
                return orders;
            }
            startSips = window.sips; startCups = window.cups;
        }
    }

    private static List<String> findProblems(List<Order> orders) {
        LinkedHashSet<String> issues = new LinkedHashSet<>();
        for (Order order : orders) {
            if (!Vessels.isCup(order.cup)) issues.add("Invalid cup for " + order.drink);
            if (drinkArea(order.drink) == null) issues.add("No TAKE area with " + order.drink + " barrels");
        }
        return new ArrayList<>(issues);
    }

    private static NArea drinkArea(String drink) {
        Vessels.Drink choice = Vessels.drink(drink);
        if (choice == null) return null;
        for (String name : choice.areaNames) {
            NArea area = NContext.findInGlobal(name);
            NArea.Ingredient in = area == null ? null : area.getInput(name);
            if (in != null && in.type == NArea.Ingredient.Type.BARREL) return area;
        }
        return null;
    }

    private static NFlowerMenu currentMenu(NGameUI gui) {
        return gui != null && gui.ui instanceof NUI ? (NFlowerMenu) ((NUI) gui.ui).findInRoot(NFlowerMenu.class) : null;
    }

    /** This bot never cancels a placement, hand item or menu it did not create. */
    static boolean readyForAction(NGameUI gui) {
        return gui != null && gui.vhand == null && gui.map != null && gui.map.placing == null
                && currentMenu(gui) == null;
    }

    private static boolean readyForOwnedHand(NGameUI gui, Cup cup) {
        return gui != null && gui.vhand != null && gui.vhand.item == cup.item
                && gui.map != null && gui.map.placing == null && currentMenu(gui) == null;
    }

    /** A server transfer may replace the GItem widget. Only a unique destination receipt is owned. */
    static <T> T uniqueArrival(Set<T> before, Collection<T> after, T source,
                               boolean sourceGone, Predicate<T> fingerprintMatches) {
        if (after.contains(source) && !before.contains(source) && fingerprintMatches.test(source)) return source;
        if (!sourceGone) return null;
        T found = null;
        for (T value : after) if (!before.contains(value)) {
            if (found != null) return null;
            found = value;
        }
        return found != null && fingerprintMatches.test(found) ? found : null;
    }

    static <T> T uniqueArrival(Set<T> before, Collection<T> after, T source, boolean sourceGone) {
        return uniqueArrival(before, after, source, sourceGone, value -> true);
    }

    static final class Fingerprint {
        final String name;
        final String resource;
        final Float quality;
        final String content;
        Fingerprint(String name, String resource, Float quality, String content) {
            this.name = name; this.resource = resource; this.quality = quality; this.content = content;
        }
        @Override public boolean equals(Object value) {
            if (!(value instanceof Fingerprint)) return false;
            Fingerprint other = (Fingerprint) value;
            return Objects.equals(name, other.name) && Objects.equals(resource, other.resource)
                    && Objects.equals(quality, other.quality) && Objects.equals(content, other.content);
        }
        @Override public int hashCode() { return Objects.hash(name, resource, quality, content); }
    }

    static Fingerprint fingerprint(GItem value) {
        if (!(value instanceof NGItem)) return null;
        NGItem item = (NGItem) value;
        if (item.name() == null || item.infoseq <= 0) return null;
        try {
            Resource res = item.getres();
            if (res == null || res.name == null) return null;
            String content = item.content().isEmpty() ? "" : item.content().get(0).name();
            return content == null ? null : new Fingerprint(item.name(), res.name, item.quality, content);
        } catch (Loading unavailable) { return null; }
    }

    private static Set<GItem> identities(List<WItem> items) {
        Set<GItem> ids = Collections.newSetFromMap(new IdentityHashMap<>());
        for (WItem w : items) ids.add(w.item);
        return ids;
    }

    private static List<WItem> directCups(NInventory inventory, String name) {
        if (inventory == null) return new ArrayList<>();
        if (inventory.ui == null) return directCupsUnlocked(inventory, name);
        synchronized (inventory.ui) { return directCupsUnlocked(inventory, name); }
    }

    private static List<WItem> directCupsUnlocked(NInventory inventory, String name) {
        List<WItem> result = new ArrayList<>();
        for (Widget child = inventory.child; child != null; child = child.next)
            if (child instanceof WItem && ((WItem) child).item instanceof NGItem
                    && name.equals(((NGItem) ((WItem) child).item).name())) result.add((WItem) child);
        return result;
    }

    private static GItem awaitArrival(NInventory destination, String name, Set<GItem> before,
                                      GItem source, Fingerprint expected, BooleanSupplier sourceGone) throws InterruptedException {
        GItem[] found = {null};
        await(() -> {
            found[0] = uniqueArrival(before, identities(directCups(destination, name)), source,
                    sourceGone.getAsBoolean(), value -> expected != null && expected.equals(fingerprint(value)));
            return found[0] != null;
        }, RECEIPT_MS);
        return found[0];
    }

    private static List<WItem> exactCups(NInventory inventory, String name) throws InterruptedException {
        List<WItem> cups = new ArrayList<>();
        if (inventory == null) return cups;
        for (WItem w : inventory.getItems(new NAlias(name)))
            if (w.item instanceof NGItem && name.equals(((NGItem) w.item).name())) cups.add(w);
        return cups;
    }

    private static WItem find(NInventory inventory, Cup cup) throws InterruptedException {
        for (WItem w : exactCups(inventory, cup.name)) if (w.item == cup.item) return w;
        return null;
    }

    static boolean knownEmpty(WItem cup) {
        if (!(cup.item instanceof NGItem)) return false;
        NGItem item = (NGItem) cup.item;
        return item.infoseq > 0 && item.content().isEmpty();
    }

    private static String content(WItem cup) {
        if (cup == null || !(cup.item instanceof NGItem)) return null;
        NGItem item = (NGItem) cup.item;
        if (item.infoseq <= 0 || item.content().isEmpty()) return null;
        String text = item.content().get(0).name();
        if (text == null) return null;
        int of = text.indexOf(" of ");
        return (of < 0 ? text : text.substring(of + 4)).trim();
    }

    static boolean knownContent(WItem cup, String drink) {
        Vessels.Drink selection = Vessels.drink(drink);
        return selection != null && selection.holds(content(cup));
    }

    static boolean canReturn(Cup cup, WItem item) {
        return cup != null && cup.origin != null && item != null
                && item.item == cup.item && knownEmpty(item);
    }

    private static boolean await(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        class Wait extends NTask {
            final long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
            boolean reached;
            Wait() { infinite = true; criticalOnTimeout = false; }
            @Override public boolean check() {
                if (condition.getAsBoolean()) { reached = true; return true; }
                return System.nanoTime() >= deadline;
            }
        }
        Wait wait = new Wait();
        NUtils.addTask(wait);
        return wait.reached;
    }

    private Cup fetchOne(NGameUI gui, String cupName) throws InterruptedException {
        if (!readyForAction(gui)) { uncertain = true; return null; }
        NContext context = new NContext(gui);
        context.addInItem(cupName, null);
        for (NContext.ObjectStorage storage : context.getInStorages(cupName)) {
            if (!(storage instanceof Container)) continue;
            Container container = (Container) storage;
            if (!readyForAction(gui)) { uncertain = true; return null; }
            if (Container.pathTo(gui, container) == null || !new OpenTargetContainer(container).run(gui).IsSuccess()) continue;
            try {
                NInventory source = gui.getInventory(container.cap);
                for (WItem w : exactCups(source, cupName)) {
                    if (!knownEmpty(w) || gui.getInventory().findFreeCoord(w) == null) continue;
                    Set<GItem> before = identities(directCups(gui.getInventory(), cupName));
                    Fingerprint signature = fingerprint(w.item);
                    if (signature == null) { uncertain = true; return null; }
                    int sourceId = w.item.wdgid();
                    if (TransferToContainer.transfer(w, gui.getInventory(), 1) != 1) { uncertain = true; return null; }
                    GItem arrived = awaitArrival(gui.getInventory(), cupName, before, w.item, signature,
                            () -> NUtils.getUI().getwidget(sourceId) == null);
                    if (arrived == null || !await(() -> arrived.infoseq > 0, CONTENT_MS)) { uncertain = true; return null; }
                    WItem receipt = null;
                    for (WItem item : directCups(gui.getInventory(), cupName)) if (item.item == arrived) receipt = item;
                    if (receipt == null || !knownEmpty(receipt)) { uncertain = true; return null; }
                    return new Cup(cupName, container, arrived);
                }
            } finally { new CloseTargetContainer(container).run(gui); }
        }
        return null;
    }

    private String drink(NGameUI gui, Order order, Cup cup) throws InterruptedException {
        if (!readyForAction(gui)) return order.drink + ": another item, placement or menu is active";
        NArea area = drinkArea(order.drink);
        if (area == null || !NUtils.navigateToArea(area, true)) return order.drink + ": area unreachable";
        ArrayList<Gob> vessels = Finder.findGobs(area, new NAlias("barrel", "demijohn"));
        vessels.sort(NUtils.d_comp);
        int done = 0;
        Gob barrel = null;
        for (; done < order.sips; done++) {
            WItem w = find(gui.getInventory(), cup);
            if (w == null) return order.drink + " " + done + "/" + order.sips + ": cup missing";
            if (knownEmpty(w)) {
                boolean filled = false;
                for (Gob candidate : vessels) {
                    if (!NUtils.barrelHasContent(candidate)) continue;
                    if (fill(gui, cup, candidate, order.drink)) {
                        barrel = candidate; filled = true; break;
                    }
                    if (uncertain) return order.drink + " " + done + "/" + order.sips + ": action unconfirmed";
                    // An unconfirmed or wrong liquid is retained, not tried on another barrel.
                    WItem uncertain = find(gui.getInventory(), cup);
                    if (uncertain == null || !knownEmpty(uncertain))
                        return order.drink + " " + done + "/" + order.sips + ": liquid unconfirmed";
                }
                if (!filled) return order.drink + " " + done + "/" + order.sips + ": no matching liquid";
                w = find(gui.getInventory(), cup);
            }
            if (!knownContent(w, order.drink)) return order.drink + " " + done + "/" + order.sips + ": unknown cup contents";
            if (barrel == null || cup.filledAt != barrel) return order.drink + " " + done + "/" + order.sips + ": source barrel unknown";
            if (!readyForAction(gui)) return order.drink + ": another action is active";
            NUtils.getUI().dropLastError();
            int seq = ((NGItem) w.item).infoseq;
            Double beforeAmount = amount(w);
            if (beforeAmount == null || beforeAmount <= 0)
                return order.drink + " " + done + "/" + order.sips + ": liquid amount unknown";
            if (!chooseSip(gui, w))
                return order.drink + " " + done + "/" + order.sips + ": Sip refused";
            if (!awaitSip(w, seq, beforeAmount)) { uncertain = true; return order.drink + " " + done + "/" + order.sips + ": Sip unconfirmed"; }
        }
        WItem w = find(gui.getInventory(), cup);
        if (w == null) return order.drink + ": cup missing after sipping";
        if (!knownEmpty(w) && !pour(gui, cup, barrel, order.drink))
            return order.drink + ": remaining drink kept in cup";
        return "ok:" + order.drink + " " + done + "/" + order.sips;
    }

    private static Double amount(WItem cup) {
        if (cup == null || !(cup.item instanceof NGItem)) return null;
        if (knownEmpty(cup)) return 0.0;
        NGItem item = (NGItem) cup.item;
        if (item.infoseq <= 0 || item.content().isEmpty()) return null;
        String line = item.content().get(0).name();
        Matcher match = line == null ? null : AMOUNT.matcher(line);
        return match != null && match.matches() ? Double.parseDouble(match.group(1)) : null;
    }

    static boolean sipCompleted(Double before, Double after, boolean tooltipAdvanced, boolean poseSettled) {
        return before != null && after != null && tooltipAdvanced && poseSettled && after + 0.00001 < before;
    }

    private static boolean awaitSip(WItem w, int beforeSeq, double beforeAmount) throws InterruptedException {
        boolean[] sawPose = {false};
        long[] changedAt = {0};
        boolean observed = await(() -> {
            if (NUtils.getUI().getLastError() != null) return true;
            Gob player = NUtils.player();
            String pose = player == null ? null : player.pose();
            boolean drinking = pose != null && pose.contains("drinkan");
            if (drinking) sawPose[0] = true;
            boolean changed = ((NGItem) w.item).infoseq > beforeSeq;
            if (changed && changedAt[0] == 0) changedAt[0] = System.nanoTime();
            boolean settled = !drinking && (sawPose[0]
                    || changedAt[0] != 0 && System.nanoTime() - changedAt[0] >= 500_000_000L);
            return sipCompleted(beforeAmount, amount(w), changed, settled);
        }, SIP_MS);
        return observed && NUtils.getUI().getLastError() == null;
    }

    private boolean chooseSip(NGameUI gui, WItem cup) throws InterruptedException {
        if (!readyForAction(gui)) { uncertain = true; return false; }
        if (!(gui.ui instanceof NUI) || ((NUI) gui.ui).core == null) { uncertain = true; return false; }
        NCore core = ((NUI) gui.ui).core;
        core.setLastAction(cup);
        NCore.LastActions marker = core.getLastActions();
        cup.item.wdgmsg("iact", cup.c, 0);
        NFlowerMenu[] opened = {null};
        boolean[] displaced = {false};
        boolean appeared = await(() -> {
            if (!menuOwnerStill(core, marker, cup)) { displaced[0] = true; return true; }
            NFlowerMenu menu = currentMenu(gui);
            if (menu == null || menu.nopts == null || menu.nopts.length == 0) return false;
            opened[0] = menu;
            return true;
        }, RECEIPT_MS);
        if (!appeared || displaced[0] || opened[0] == null || !menuOwnerStill(core, marker, cup)) {
            uncertain = true; return false;
        }
        NFlowerMenu menu = opened[0];
        if (!menu.hasOpt("Sip")) {
            if (!menuOwnerStill(core, marker, cup)) { uncertain = true; return false; }
            menu.wdgmsg("cl", -1);
            if (!await(() -> menu.parent == null, RECEIPT_MS)) uncertain = true;
            return false;
        }
        if (!menuOwnerStill(core, marker, cup)) { uncertain = true; return false; }
        menu.chooseOpt("Sip");
        if (!await(() -> menu.parent == null, RECEIPT_MS)) { uncertain = true; return false; }
        return true;
    }

    static boolean menuOwnerStill(NCore core, NCore.LastActions marker, WItem cup) {
        return core != null && marker != null && cup != null && core.getLastActions() == marker
                && marker.item == cup && marker.item.item == cup.item;
    }

    private boolean fill(NGameUI gui, Cup cup, Gob barrel, String drink) throws InterruptedException {
        if (!readyForAction(gui)) { uncertain = true; return false; }
        if (!new PathFinder(barrel).run(gui).IsSuccess()) return false;
        if (!take(gui, cup)) return false;
        WItem hand = gui.vhand;
        int seq = ((NGItem) hand.item).infoseq;
        if (!readyForOwnedHand(gui, cup)) { uncertain = true; return false; }
        NUtils.activateItem(barrel);
        boolean updated = await(() -> gui.vhand != null && gui.vhand.item == cup.item
                && ((NGItem) gui.vhand.item).infoseq > seq, CONTENT_MS);
        String got = updated ? content(gui.vhand) : null;
        boolean match = Vessels.drink(drink).holds(got);
        if (match) cup.filledAt = barrel;
        if (!updated || !match) { uncertain = true; return false; }
        boolean stored = storeHand(gui, cup);
        if (!stored) uncertain = true;
        return updated && match && stored;
    }

    private boolean pour(NGameUI gui, Cup cup, Gob barrel, String drink) throws InterruptedException {
        if (!readyForAction(gui) || barrel == null || cup.filledAt != barrel
                || !new PathFinder(barrel).run(gui).IsSuccess()) return false;
        WItem w = find(gui.getInventory(), cup);
        if (!knownContent(w, drink) || !take(gui, cup)) return false;
        int seq = ((NGItem) gui.vhand.item).infoseq;
        if (!readyForOwnedHand(gui, cup)) { uncertain = true; return false; }
        NUtils.activateItem(barrel, true);
        boolean emptied = await(() -> gui.vhand != null && gui.vhand.item == cup.item
                && ((NGItem) gui.vhand.item).infoseq > seq && knownEmpty(gui.vhand), CONTENT_MS);
        if (!emptied) { uncertain = true; return false; }
        boolean stored = storeHand(gui, cup);
        if (!stored) uncertain = true;
        if (emptied) cup.filledAt = null;
        return emptied && stored;
    }

    private boolean take(NGameUI gui, Cup cup) throws InterruptedException {
        if (!readyForAction(gui)) { uncertain = true; return false; }
        WItem w = find(gui.getInventory(), cup);
        if (w == null) return false;
        Fingerprint signature = fingerprint(w.item);
        if (signature == null) { uncertain = true; return false; }
        int sourceId = w.item.wdgid();
        w.item.wdgmsg("take", Coord.z);
        boolean received = await(() -> gui.vhand != null && signature.equals(fingerprint(gui.vhand.item)), RECEIPT_MS);
        if (!received || gui.vhand == null || !(gui.vhand.item instanceof NGItem)) { uncertain = true; return false; }
        // The server may re-create the item widget on its way into the hand.
        if ((NUtils.getUI().getwidget(sourceId) != null && gui.vhand.item != w.item)
                || !cup.name.equals(((NGItem) gui.vhand.item).name())) { uncertain = true; return false; }
        cup.item = gui.vhand.item;
        return true;
    }

    private static boolean storeHand(NGameUI gui, Cup cup) throws InterruptedException {
        if (gui.vhand == null || gui.vhand.item != cup.item) return false;
        Coord free = gui.getInventory().findFreeCoord(gui.vhand);
        if (free == null) return false; // Keep the cup in hand; never ground-drop it.
        Set<GItem> before = identities(directCups(gui.getInventory(), cup.name));
        GItem source = cup.item;
        Fingerprint signature = fingerprint(source);
        if (signature == null) return false;
        int sourceId = source.wdgid();
        gui.getInventory().wdgmsg("drop", free);
        GItem arrived = awaitArrival(gui.getInventory(), cup.name, before, source, signature,
                () -> gui.vhand == null && NUtils.getUI().getwidget(sourceId) == null);
        if (arrived == null) return false;
        cup.item = arrived;
        return true;
    }

    private String returnFetched(NGameUI gui, List<Cup> fetched) throws InterruptedException {
        List<String> stranded = new ArrayList<>();
        for (Cup cup : fetched) {
            if (gui.vhand != null) {
                if (!readyForOwnedHand(gui, cup) || !storeHand(gui, cup)) {
                    stranded.add(cup.name + " in hand");
                    break;
                }
            }
            if (!readyForAction(gui)) { stranded.add(cup.name + " carried"); break; }
            WItem w = find(gui.getInventory(), cup);
            if (!canReturn(cup, w)) { stranded.add(cup.name + " carried"); continue; }
            if (cup.origin == null || Container.pathTo(gui, cup.origin) == null
                    || !new OpenTargetContainer(cup.origin).run(gui).IsSuccess()) {
                stranded.add(cup.name + " carried"); continue;
            }
            try {
                NInventory target = gui.getInventory(cup.origin.cap);
                if (target == null || target.findFreeCoord(w) == null) { stranded.add(cup.name + " carried"); continue; }
                Set<GItem> before = identities(directCups(target, cup.name));
                Fingerprint signature = fingerprint(w.item);
                if (signature == null) { stranded.add(cup.name + " unread"); break; }
                int sourceId = w.item.wdgid();
                if (TransferToContainer.transfer(w, target, 1) != 1) {
                    uncertain = true;
                    stranded.add(cup.name + " transfer unconfirmed"); break;
                }
                GItem receipt = awaitArrival(target, cup.name, before, cup.item, signature,
                        () -> NUtils.getUI().getwidget(sourceId) == null);
                if (receipt == null) { uncertain = true; stranded.add(cup.name + " receipt unconfirmed"); break; }
            } finally { new CloseTargetContainer(cup.origin).run(gui); }
        }
        return stranded.isEmpty() ? null : "could not return " + String.join(", ", stranded);
    }
}
