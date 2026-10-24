package nurgling.actions;

import haven.Gob;
import haven.WItem;
import haven.Widget;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.areas.NContext;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.ItemVariants;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Selects individual silk items across every source container before taking any. */
public final class FindQualityThreshold implements Action {
    private static final float QUALITY_EPSILON = 0.0001f;
    private final NContext context;
    private final String item;
    private final int required;
    private final Specialisation.SpecName origin;
    private final List<Container> sources = new ArrayList<>();
    private final List<RankedCopy> ranked = new ArrayList<>();
    private Float threshold;
    private int moved;

    static final class RankedCopy {
        final long sourceId;
        final float quality;
        RankedCopy(long sourceId, float quality) {
            this.sourceId = sourceId;
            this.quality = quality;
        }
    }

    /** A confirmed move removes only that unit; failed or untried units remain for later loads. */
    static final class QualityPlan {
        private final List<RankedCopy> pending;
        private final Float threshold;

        QualityPlan(List<RankedCopy> copies, Float threshold) {
            pending = new ArrayList<>(copies);
            pending.sort((a, b) -> Float.compare(b.quality, a.quality));
            this.threshold = threshold;
        }

        List<RankedCopy> next(int limit) {
            List<RankedCopy> batch = new ArrayList<>();
            for (RankedCopy copy : pending) {
                if (batch.size() >= limit || !eligible(copy.quality, threshold)) break;
                batch.add(copy);
            }
            return batch;
        }

        void confirmed(RankedCopy copy) { pending.remove(copy); }
    }

    private QualityPlan plan;

    public FindQualityThreshold(NContext context, String item, int required,
                                Specialisation.SpecName origin) {
        this.context = context;
        this.item = item;
        this.required = required;
        this.origin = origin;
    }

    public FindQualityThreshold(NContext context, String item, int required) {
        this(context, item, required, null);
    }

    /** Null and non-finite qualities cannot be ranked safely. */
    static boolean known(Float quality) {
        return quality != null && Float.isFinite(quality) && quality > 0;
    }

    /** Ties at the boundary are interchangeable; the transfer limit picks only the needed units. */
    static Float selectThreshold(List<Float> qualities, int wanted) {
        if (wanted <= 0 || qualities.isEmpty()) return null;
        for (Float quality : qualities)
            if (!known(quality)) return null;
        List<Float> ordered = new ArrayList<>(qualities);
        ordered.sort(Collections.reverseOrder());
        return ordered.get(Math.min(wanted, ordered.size()) - 1);
    }

    static boolean eligible(Float quality, Float threshold) {
        return known(quality) && threshold != null && quality + QUALITY_EPSILON >= threshold;
    }

    public Float getThreshold() { return threshold; }
    public int getMovedCount() { return moved; }

    public int eligibleCarried(NGameUI gui) {
        List<WItem> held = new ArrayList<>();
        if (!snapshot(gui.getInventory(), item, held)) return -1;
        int count = 0;
        for (WItem copy : held)
            if (eligible(((NGItem) copy.item).quality, threshold)) count++;
        return count;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        sources.clear();
        ranked.clear();
        plan = null;
        threshold = null;
        moved = 0;
        if (required <= 0) return Results.SUCCESS();
        ArrayList<NContext.ObjectStorage> inputs = origin == null
                ? context.getInStorages(item) : context.getSpecStorages(origin);

        List<Float> qualities = new ArrayList<>();
        NInventory carried = gui.getInventory();
        if (carried == null)
            return Results.ERROR("Cannot read carried " + item);
        List<WItem> held = new ArrayList<>();
        if (!snapshot(carried, item, held)) return Results.ERROR("Cannot read carried " + item);
        for (WItem copy : held) qualities.add(((NGItem) copy.item).quality);

        if (inputs == null || inputs.isEmpty())
            return finishCarriedOnly(held, qualities);

        for (NContext.ObjectStorage input : inputs) {
            // Piles cannot expose individual quality choices. Silk is fetched only from cabinets.
            if (!(input instanceof Container)) continue;
            Container container = (Container) input;
            Gob gob = Finder.findGob(container.gobHash);
            if (gob == null || container.cap == null)
                return Results.ERROR("Silk source changed while scanning");
            Results path = new PathFinder(gob).run(gui);
            if (path == null || !path.IsSuccess()) return Results.FAIL();
            Results opened = new OpenTargetContainer(container).run(gui);
            if (opened == null || !opened.IsSuccess()) return Results.FAIL();
            boolean readable;
            try {
                NInventory inventory = gui.getInventory(container.cap);
                readable = owns(inventory, container);
                if (readable) {
                    List<WItem> found = new ArrayList<>();
                    readable = snapshot(inventory, item, found);
                    if (readable)
                        for (WItem copy : found) {
                            float quality = ((NGItem) copy.item).quality;
                            qualities.add(quality);
                            ranked.add(new RankedCopy(container.gobid, quality));
                        }
                }
            } finally {
                new CloseTargetContainer(container).run(gui);
            }
            if (!readable) return Results.ERROR("Silk source inventory or quality is not ready");
            sources.add(container);
        }
        if (sources.isEmpty()) return finishCarriedOnly(held, qualities);
        threshold = selectThreshold(qualities, required);
        plan = new QualityPlan(ranked, threshold);
        return Results.SUCCESS();
    }

    Results finishCarriedOnly(List<WItem> held, List<Float> qualities) {
        if (held.size() < required)
            return Results.ERROR("No container source for " + item);
        threshold = selectThreshold(qualities, required);
        plan = new QualityPlan(ranked, threshold);
        return Results.SUCCESS();
    }

    /** Fetches at most one load. Every chosen WItem is re-read from its original owner. */
    public Results fetchUpTo(NGameUI gui, int limit) throws InterruptedException {
        moved = 0;
        if (threshold == null || limit <= 0) return Results.SUCCESS();
        NInventory target = gui.getInventory();
        if (target == null) return Results.FAIL();
        Map<Long, List<RankedCopy>> planned = new LinkedHashMap<>();
        for (RankedCopy candidate : plan.next(limit)) {
            planned.computeIfAbsent(candidate.sourceId, ignored -> new ArrayList<>())
                    .add(candidate);
        }
        for (Map.Entry<Long, List<RankedCopy>> entry : planned.entrySet()) {
            Container source = null;
            for (Container candidate : sources)
                if (candidate.gobid == entry.getKey()) { source = candidate; break; }
            if (source == null) return Results.ERROR("Ranked silk source disappeared");
            Gob gob = Finder.findGob(source.gobHash);
            if (gob == null) return Results.ERROR("Silk source changed after ranking");
            Results path = new PathFinder(gob).run(gui);
            if (path == null || !path.IsSuccess()) return Results.FAIL();
            Results opened = new OpenTargetContainer(source).run(gui);
            if (opened == null || !opened.IsSuccess()) return Results.FAIL();
            Results fetched = Results.SUCCESS();
            try {
                NInventory inventory = gui.getInventory(source.cap);
                if (!owns(inventory, source)) fetched = Results.ERROR("Silk source window changed");
                for (RankedCopy selected : entry.getValue()) {
                    if (!fetched.IsSuccess()) break;
                    List<WItem> current = new ArrayList<>();
                    if (!snapshot(inventory, item, current)) {
                        fetched = Results.ERROR("Silk source quality changed");
                        break;
                    }
                    current.removeIf(copy -> !eligible(((NGItem) copy.item).quality,
                            selected.quality));
                    if (current.isEmpty()) {
                        fetched = Results.ERROR("Ranked silk item disappeared before collection");
                        break;
                    }
                    current.sort((a, b) -> Float.compare(((NGItem) b.item).quality,
                            ((NGItem) a.item).quality));
                    WItem chosen = current.get(0);
                    PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(
                            PouchContainerTransfer.liveSelected(chosen, target, item,
                                    ItemVariants.keyOf((NGItem) chosen.item)), 1);
                    if (outcome.moved == 1) {
                        moved++;
                        plan.confirmed(selected);
                    } else if (outcome.failed || outcome.handStuck) {
                        fetched = Results.FAIL();
                    } else {
                        return Results.SUCCESS(); // No inventory room; let the caller unload this trip.
                    }
                }
            } finally {
                new CloseTargetContainer(source).run(gui);
            }
            if (!fetched.IsSuccess()) return fetched;
        }
        return Results.SUCCESS();
    }

    static boolean owns(NInventory inventory, Container container) {
        return inventory != null && inventory.parentGob != null
                && inventory.parentGob.id == container.gobid;
    }

    /** Inspect the live widget tree once; an unresolved item makes the ranking incomplete. */
    static boolean snapshot(NInventory inventory, String name, List<WItem> copies) {
        if (inventory == null) return false;
        if (inventory.ui == null) return collect(inventory.child, name, copies);
        synchronized (inventory.ui) { return collect(inventory.child, name, copies); }
    }

    private static boolean collect(Widget first, String name, List<WItem> copies) {
        for (Widget child = first; child != null; child = child.next) {
            if (!(child instanceof WItem)) continue;
            WItem copy = (WItem) child;
            if (!(copy.item instanceof NGItem) || !NGItem.validateItem(copy)) return false;
            if (copy.item.contents != null) {
                if (!collect(copy.item.contents.child, name, copies)) return false;
            } else if (name.equals(((NGItem) copy.item).name())) {
                if (!known(((NGItem) copy.item).quality)) return false;
                if (copies != null) copies.add(copy);
            }
        }
        return true;
    }
}
