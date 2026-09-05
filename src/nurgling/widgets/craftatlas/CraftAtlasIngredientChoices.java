package nurgling.widgets.craftatlas;

import nurgling.craftatlas.CraftAtlasMaterialPlanner;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Candidate;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Selection;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Source;
import nurgling.i18n.L10n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Resource-free choice model used by the Atlas ingredient dropdown. */
final class CraftAtlasIngredientChoices {
    static final class Choice {
        final String label;
        final Selection selection;
        final Candidate candidate;

        Choice(String label, Selection selection, Candidate candidate) {
            this.label = label;
            this.selection = selection;
            this.candidate = candidate;
        }
    }

    private CraftAtlasIngredientChoices() {}

    static List<Choice> choices(List<Candidate> candidates, boolean optional, boolean grouped) {
        return choices(candidates, optional, grouped, Collections.emptyList());
    }

    static List<Choice> choices(List<Candidate> candidates, boolean optional, boolean grouped,
                                List<String> allowedMaterials) {
        List<Choice> result = new ArrayList<>();
        if(optional) result.add(new Choice(L10n.get("craft_atlas.material.ignore"), Selection.ignored(), null));
        if(grouped && allowedMaterials != null && !allowedMaterials.isEmpty()) {
            result.add(new Choice(L10n.get("craft_atlas.material.all"), Selection.all(), null));
            for(String material : allowedMaterials)
                result.add(new Choice(material + " · " + L10n.get("craft_atlas.material.any_quality"),
                        Selection.material(material), null));
        }
        if(candidates == null || candidates.isEmpty()) {
            if(!grouped || allowedMaterials == null || allowedMaterials.isEmpty())
                result.add(new Choice(L10n.get("craft_atlas.material.missing"), Selection.all(), null));
            return Collections.unmodifiableList(result);
        }
        if(grouped && (allowedMaterials == null || allowedMaterials.isEmpty()))
            result.add(new Choice(L10n.get("craft_atlas.material.all"), Selection.all(), null));
        for(Candidate candidate : CraftAtlasMaterialPlanner.sortedCandidates(candidates))
            result.add(new Choice(candidateLabel(candidate), Selection.preferred(candidate), candidate));
        return Collections.unmodifiableList(result);
    }

    static List<Choice> choicesForMaterial(List<Candidate> candidates, boolean optional, String material) {
        if(material == null || material.trim().isEmpty())
            return choices(candidates, optional, false);
        List<Choice> result = new ArrayList<>();
        if(optional) result.add(new Choice(L10n.get("craft_atlas.material.ignore"), Selection.ignored(), null));
        result.add(new Choice(material + " · " + L10n.get("craft_atlas.material.any_quality"),
                Selection.material(material), null));
        if(candidates != null) for(Candidate candidate : CraftAtlasMaterialPlanner.sortedCandidates(candidates)) {
            if(material.equals(candidate.material))
                result.add(new Choice(candidateLabel(candidate), Selection.preferred(candidate), candidate));
        }
        return Collections.unmodifiableList(result);
    }

    static String candidateLabel(Candidate value) {
        String marker = value.source == Source.INVENTORY ? "★ " : "";
        String quality = value.quality == Math.rint(value.quality)
                ? String.format(Locale.ROOT, "%.0f", value.quality)
                : String.format(Locale.ROOT, "%.1f", value.quality);
        String location = value.location == null || value.location.isEmpty() ? "" : " · " + value.location;
        return marker + value.material + " · Q" + quality + " · ×" + value.count + location;
    }

    static Choice displaySelection(List<Candidate> candidates, boolean optional, boolean grouped,
                                   Selection selected) {
        return displaySelection(candidates, optional, grouped, Collections.emptyList(), selected);
    }

    static Choice displaySelection(List<Candidate> candidates, boolean optional, boolean grouped,
                                   List<String> allowedMaterials, Selection selected) {
        List<Choice> values = choices(candidates, optional, grouped, allowedMaterials);
        if(selected != null) {
            Choice sameMaterial = null;
            for(Choice choice : values) {
                Selection value = choice.selection;
                if(value.mode != selected.mode) continue;
                if(value.mode != Selection.Mode.PREFERRED) return choice;
                if(eq(value.material, selected.material) &&
                        eq(value.preferredCandidateId, selected.preferredCandidateId)) return choice;
                if(choice.candidate != null && choice.candidate.material.equals(selected.material)
                        && (selected.preferredQuality == null ||
                            choice.candidate.quality <= selected.preferredQuality)
                        && sameMaterial == null) sameMaterial = choice;
            }
            if(sameMaterial != null) return sameMaterial;
            if(selected.mode == Selection.Mode.PREFERRED && selected.material != null)
                return new Choice(selected.material + " · " + L10n.get("craft_atlas.material.missing"),
                        selected, null);
        }
        Selection fallback = optional ? Selection.ignored()
                : CraftAtlasMaterialPlanner.defaultSelection(candidates);
        for(Choice choice : values) {
            if(choice.selection.mode != fallback.mode) continue;
            if(fallback.mode != Selection.Mode.PREFERRED
                    || eq(choice.selection.preferredCandidateId, fallback.preferredCandidateId)) return choice;
        }
        return values.get(0);
    }

    static Choice displaySelectionForMaterial(List<Candidate> candidates, boolean optional,
                                              String material, Selection selected) {
        List<Choice> values = choicesForMaterial(candidates, optional, material);
        if(selected != null) for(Choice choice : values) {
            Selection value = choice.selection;
            if(value.mode != selected.mode) continue;
            if(value.mode != Selection.Mode.PREFERRED) return choice;
            if(eq(value.material, selected.material) &&
                    eq(value.preferredCandidateId, selected.preferredCandidateId)) return choice;
        }
        return values.get(0);
    }

    private static boolean eq(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
