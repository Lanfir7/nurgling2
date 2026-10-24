package nurgling.widgets;

import java.util.Objects;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import nurgling.tools.ItemVariants;

/** Read-only description of the selected autocraft input source. */
final class CraftSourceHint {
    enum Kind {
        IGNORED, AREA, LOCAL_ZONE, INVENTORY, SUBCRAFT, CATEGORY,
        STORAGE, STORAGE_EMPTY, NO_SOURCE, CHECKING, UNAVAILABLE,
        WATER_AREA, WATER_CHECKED_AT_CRAFT
    }

    static final class Selection {
        final String specName, pickedName, selectedZoneName;
        final boolean ignored, category, useCategory, selectedZone, localZone, inventory, subCraft;

        Selection(String specName, String pickedName, String selectedZoneName,
                  boolean ignored, boolean category, boolean useCategory, boolean selectedZone,
                  boolean localZone, boolean inventory, boolean subCraft) {
            this.specName = specName;
            this.pickedName = pickedName;
            this.selectedZoneName = selectedZoneName;
            this.ignored = ignored;
            this.category = category;
            this.useCategory = useCategory;
            this.selectedZone = selectedZone;
            this.localZone = localZone;
            this.inventory = inventory;
            this.subCraft = subCraft;
        }

        String effectiveName() {
            return useCategory && category ? specName : pickedName != null ? pickedName : specName;
        }
    }

    static final class Hint {
        final Kind kind;
        final String detail;

        Hint(Kind kind, String detail) {
            this.kind = kind;
            this.detail = detail;
        }

        @Override public boolean equals(Object other) {
            if(!(other instanceof Hint)) return false;
            Hint hint = (Hint) other;
            return kind == hint.kind && Objects.equals(detail, hint.detail);
        }

        @Override public int hashCode() { return Objects.hash(kind, detail); }
    }

    static final class TooltipCache<S, T> {
        private S spec;
        private List<Object> state;
        private T shortTip, longTip;

        void context(S nextSpec, List<Object> nextState) {
            if(spec != nextSpec || !Objects.equals(state, nextState)) {
                spec = nextSpec;
                state = nextState;
                shortTip = longTip = null;
            }
        }

        T shortTip() { return shortTip; }
        T longTip() { return longTip; }
        void shortTip(T value) { shortTip = value; }
        void longTip(T value) { longTip = value; }
    }

    static <T> T inputArea(String name, Function<String, T> nearby, Function<String, T> global) {
        if(name == null) return null;
        T area = nearby.apply(name);
        if(area == null) area = global.apply(name);
        if(area == null && ItemVariants.isKey(name)) {
            String base = ItemVariants.base(name);
            area = nearby.apply(base);
            if(area == null) area = global.apply(base);
        }
        return area;
    }

    static <T> T waterArea(String name, Function<String, T> nearbyInput,
                           Function<String, T> globalInput, Predicate<T> barrel,
                           Function<String, T> nearbySpec, Function<String, T> globalSpec) {
        T area = inputArea(name, nearbyInput, globalInput);
        if(area != null && barrel.test(area)) return area;
        area = nearbySpec.apply("water");
        return area != null ? area : globalSpec.apply("water");
    }

    static Hint resolve(Selection selection, String areaName, boolean water, boolean indexEnabled,
                        boolean indexEligible, StorageCraftAvailability.Status indexStatus) {
        if(selection.ignored) return new Hint(Kind.IGNORED, null);
        if(water) return areaName == null ? new Hint(Kind.WATER_CHECKED_AT_CRAFT, null)
                : new Hint(Kind.WATER_AREA, areaName);
        if(selection.selectedZone) return new Hint(Kind.AREA, selection.selectedZoneName);
        if(selection.localZone) return new Hint(Kind.LOCAL_ZONE, null);
        if(selection.inventory) return new Hint(Kind.INVENTORY, null);
        if(selection.subCraft) return new Hint(Kind.SUBCRAFT, null);
        String name = selection.effectiveName();
        if(name == null || selection.category && (selection.useCategory || selection.pickedName == null))
            return new Hint(Kind.CATEGORY, name);
        if(areaName != null) return new Hint(Kind.AREA, areaName);
        if(indexEnabled && indexEligible) {
            if(indexStatus == StorageCraftAvailability.Status.PRESENT)
                return new Hint(Kind.STORAGE, null);
            if(indexStatus == StorageCraftAvailability.Status.CHECKING)
                return new Hint(Kind.CHECKING, null);
            if(indexStatus == StorageCraftAvailability.Status.UNAVAILABLE)
                return new Hint(Kind.UNAVAILABLE, null);
            if(indexStatus == StorageCraftAvailability.Status.EMPTY)
                return new Hint(Kind.STORAGE_EMPTY, null);
        }
        return new Hint(Kind.NO_SOURCE, null);
    }
}
