package nurgling.craftatlas;

import nurgling.tools.CraftSlotQuality;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/** Pure material allocation and quality projection for Craft Atlas recipes. */
public final class CraftAtlasMaterialPlanner {
    public enum Source { INVENTORY, STORAGE }

    public static final class Candidate {
        public final String id;
        public final String material;
        public final double quality;
        public final int count;
        public final Source source;
        public final String location;

        public Candidate(String id, String material, double quality, int count,
                         Source source, String location) {
            if(id == null || id.trim().isEmpty()) throw new IllegalArgumentException("id must not be empty");
            if(material == null || material.trim().isEmpty()) throw new IllegalArgumentException("material must not be empty");
            if(!Double.isFinite(quality) || quality <= 0) throw new IllegalArgumentException("quality must be positive");
            if(count < 1) throw new IllegalArgumentException("count must be positive");
            if(source == null) throw new IllegalArgumentException("source must not be null");
            this.id = id;
            this.material = material;
            this.quality = quality;
            this.count = count;
            this.source = source;
            this.location = location == null ? "" : location;
        }
    }

    public static final Comparator<Candidate> CANDIDATE_ORDER =
            Comparator.comparingDouble((Candidate value) -> value.quality).reversed()
                    .thenComparingInt(value -> value.source == Source.INVENTORY ? 0 : 1)
                    .thenComparing(value -> value.material, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(value -> value.id);

    public static final class Selection {
        public enum Mode { ALL, PREFERRED, IGNORED }

        public final Mode mode;
        public final String material;
        public final String preferredCandidateId;
        public final Double preferredQuality;

        private Selection(Mode mode, String material, String preferredCandidateId, Double preferredQuality) {
            this.mode = mode;
            this.material = material;
            this.preferredCandidateId = preferredCandidateId;
            this.preferredQuality = preferredQuality;
        }

        public static Selection all() { return new Selection(Mode.ALL, null, null, null); }
        public static Selection ignored() { return new Selection(Mode.IGNORED, null, null, null); }
        public static Selection material(String material) {
            if(material == null || material.trim().isEmpty())
                throw new IllegalArgumentException("material must not be empty");
            return new Selection(Mode.PREFERRED, material, null, null);
        }
        public static Selection preferred(Candidate value) {
            if(value == null) throw new IllegalArgumentException("candidate must not be null");
            return new Selection(Mode.PREFERRED, value.material, value.id, value.quality);
        }
        public boolean isAll() { return mode == Mode.ALL; }
        public boolean isIgnored() { return mode == Mode.IGNORED; }
    }

    public static final class SlotRequest {
        public final int slotIndex;
        public final int unitsPerCraft;
        public final boolean optional;
        public final List<String> allowedMaterials;

        public SlotRequest(int slotIndex, int unitsPerCraft, boolean optional, List<String> allowedMaterials) {
            if(slotIndex < 0) throw new IllegalArgumentException("slotIndex must not be negative");
            if(unitsPerCraft < 1) throw new IllegalArgumentException("unitsPerCraft must be positive");
            if(allowedMaterials == null || allowedMaterials.isEmpty())
                throw new IllegalArgumentException("allowedMaterials must not be empty");
            this.slotIndex = slotIndex;
            this.unitsPerCraft = unitsPerCraft;
            this.optional = optional;
            this.allowedMaterials = immutable(allowedMaterials);
        }
    }

    public static final class Allocation {
        public final String candidateId;
        public final String material;
        public final double quality;
        public final int count;
        public final Source source;

        private Allocation(Candidate candidate, int count) {
            this.candidateId = candidate.id;
            this.material = candidate.material;
            this.quality = candidate.quality;
            this.count = count;
            this.source = candidate.source;
        }
    }

    public static final class SlotPlan {
        public final int slotIndex;
        public final int required;
        public final int supplied;
        public final int missing;
        public final boolean ignored;
        public final List<Allocation> allocations;
        public final Double quality;

        private SlotPlan(int slotIndex, int required, int supplied, boolean ignored,
                         List<Allocation> allocations, Double quality) {
            this.slotIndex = slotIndex;
            this.required = required;
            this.supplied = supplied;
            this.missing = Math.max(0, required - supplied);
            this.ignored = ignored;
            this.allocations = immutable(allocations);
            this.quality = quality;
        }
    }

    public static final class Plan {
        public final List<SlotPlan> slots;
        public final boolean complete;
        public final Double quality;

        private Plan(List<SlotPlan> slots, boolean complete, Double quality) {
            this.slots = immutable(slots);
            this.complete = complete;
            this.quality = quality;
        }
    }

    private CraftAtlasMaterialPlanner() {}

    public static List<Candidate> sortedCandidates(List<Candidate> candidates) {
        List<Candidate> result = new ArrayList<>();
        if(candidates != null) result.addAll(candidates);
        result.sort(CANDIDATE_ORDER);
        return Collections.unmodifiableList(result);
    }

    public static Selection defaultSelection(List<Candidate> candidates) {
        List<Candidate> ordered = sortedCandidates(candidates);
        for(Candidate candidate : ordered)
            if(candidate.source == Source.INVENTORY) return Selection.preferred(candidate);
        return ordered.isEmpty() ? Selection.all() : Selection.preferred(ordered.get(0));
    }

    public static Selection normalizeSelection(SlotRequest slot, List<Candidate> candidates,
                                               Selection selection) {
        if(selection == null || (selection.isIgnored() && !slot.optional))
            return defaultAllowedSelection(slot, candidates);
        if(selection.mode == Selection.Mode.PREFERRED &&
                !slot.allowedMaterials.contains(selection.material))
            return slot.optional ? Selection.ignored() : defaultAllowedSelection(slot, candidates);
        return selection;
    }

    /** Resolve display selections from user choices without persisting provisional defaults. */
    public static Map<Integer, Selection> resolveSelections(List<SlotRequest> slots,
                                                             Map<Integer, List<Candidate>> candidatesBySlot,
                                                             Map<Integer, Selection> explicitSelections) {
        Map<Integer, Selection> resolved = new HashMap<>();
        if(slots == null) return resolved;
        for(SlotRequest slot : slots) {
            List<Candidate> candidates = candidatesBySlot == null ? Collections.emptyList()
                    : candidatesBySlot.getOrDefault(slot.slotIndex, Collections.emptyList());
            Selection explicit = explicitSelections == null ? null : explicitSelections.get(slot.slotIndex);
            resolved.put(slot.slotIndex, explicit == null && slot.optional
                    ? Selection.ignored() : normalizeSelection(slot, candidates, explicit));
        }
        return resolved;
    }

    public static boolean supportsCraftCount(List<SlotRequest> slots, int craftCount) {
        return supportsCraftCount(slots, Collections.emptyMap(), craftCount);
    }

    public static boolean supportsCraftCount(List<SlotRequest> slots, Map<Integer, Selection> selections,
                                             int craftCount) {
        if(craftCount < 1) return false;
        if(slots != null) for(SlotRequest slot : slots) {
            Selection selection = selections == null ? null : selections.get(slot.slotIndex);
            if(slot.optional && selection != null && selection.isIgnored()) continue;
            if((long)slot.unitsPerCraft * craftCount > Integer.MAX_VALUE) return false;
        }
        return true;
    }

    public static Plan plan(List<SlotRequest> slots,
                            Map<Integer, List<Candidate>> candidatesBySlot,
                            Map<Integer, Selection> selections,
                            int craftCount) {
        if(craftCount < 1) throw new IllegalArgumentException("craftCount must be positive");
        List<SlotPlan> planned = new ArrayList<>();
        List<Double> qualities = new ArrayList<>();
        Map<String, Integer> remainingByCandidate = new HashMap<>();
        boolean complete = true;
        if(slots != null) for(SlotRequest slot : slots) {
            List<Candidate> candidates = candidatesBySlot == null ? Collections.emptyList()
                    : candidatesBySlot.getOrDefault(slot.slotIndex, Collections.emptyList());
            Selection selection = selections == null ? null : selections.get(slot.slotIndex);
            selection = normalizeSelection(slot, candidates, selection);

            if(selection.isIgnored()) {
                if(slot.optional) {
                    planned.add(new SlotPlan(slot.slotIndex, 0, 0, true,
                            Collections.emptyList(), null));
                } else {
                    planned.add(new SlotPlan(slot.slotIndex, slot.unitsPerCraft * craftCount, 0, false,
                            Collections.emptyList(), null));
                    complete = false;
                }
                continue;
            }

            long requiredLong = (long)slot.unitsPerCraft * craftCount;
            if(requiredLong > Integer.MAX_VALUE)
                throw new IllegalArgumentException("craftCount is too large for this recipe");
            int required = (int)requiredLong;

            List<Candidate> allowed = allowedCandidates(slot, candidates, selection);
            prefer(allowed, selection.preferredCandidateId);
            List<Allocation> allocations = new ArrayList<>();
            int supplied = 0;
            double weightedQuality = 0;
            for(Candidate candidate : allowed) {
                int available = remainingByCandidate.containsKey(candidate.id)
                        ? remainingByCandidate.get(candidate.id) : candidate.count;
                int take = Math.min(required - supplied, available);
                if(take <= 0) continue;
                allocations.add(new Allocation(candidate, take));
                remainingByCandidate.put(candidate.id, available - take);
                supplied += take;
                weightedQuality += candidate.quality * take;
                if(supplied >= required) break;
            }
            Double slotQuality = supplied == required ? weightedQuality / supplied : null;
            if(supplied < required) complete = false;
            if(slotQuality != null) qualities.add(slotQuality);
            planned.add(new SlotPlan(slot.slotIndex, required, supplied, false, allocations, slotQuality));
        }
        Double quality = complete ? CraftSlotQuality.meanOfSlotAverages(qualities) : null;
        return new Plan(planned, complete, quality);
    }

    private static List<Candidate> allowedCandidates(SlotRequest slot, List<Candidate> candidates,
                                                     Selection selection) {
        Set<String> allowedNames = new HashSet<>(slot.allowedMaterials);
        List<Candidate> result = new ArrayList<>();
        for(Candidate candidate : sortedCandidates(candidates)) {
            if(!allowedNames.contains(candidate.material)) continue;
            if(!selection.isAll() && !candidate.material.equals(selection.material)) continue;
            if(!selection.isAll() && selection.preferredQuality != null &&
                    candidate.quality > selection.preferredQuality) continue;
            result.add(candidate);
        }
        return result;
    }

    private static Selection defaultAllowedSelection(SlotRequest slot, List<Candidate> candidates) {
        Set<String> allowedNames = new HashSet<>(slot.allowedMaterials);
        List<Candidate> allowed = new ArrayList<>();
        for(Candidate candidate : sortedCandidates(candidates))
            if(allowedNames.contains(candidate.material)) allowed.add(candidate);
        return defaultSelection(allowed);
    }

    private static void prefer(List<Candidate> values, String candidateId) {
        if(candidateId == null) return;
        for(int i = 0; i < values.size(); i++) {
            if(candidateId.equals(values.get(i).id)) {
                if(i > 0) values.add(0, values.remove(i));
                return;
            }
        }
    }

    private static <T> List<T> immutable(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
