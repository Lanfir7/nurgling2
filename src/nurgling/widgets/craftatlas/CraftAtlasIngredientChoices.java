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
        List<Choice> result = new ArrayList<>();
        if(optional) result.add(new Choice(L10n.get("craft_atlas.material.ignore"), Selection.ignored(), null));
        if(grouped) result.add(new Choice(L10n.get("craft_atlas.material.all"), Selection.all(), null));
        for(Candidate candidate : CraftAtlasMaterialPlanner.sortedCandidates(candidates))
            result.add(new Choice(candidateLabel(candidate), Selection.preferred(candidate), candidate));
        if(result.isEmpty()) result.add(new Choice(L10n.get("craft_atlas.material.missing"), Selection.all(), null));
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
        List<Choice> values = choices(candidates, optional, grouped);
        if(selected != null) {
            Choice sameMaterial = null;
            for(Choice choice : values) {
                Selection value = choice.selection;
                if(value.mode != selected.mode) continue;
                if(value.mode != Selection.Mode.PREFERRED) return choice;
                if(eq(value.preferredCandidateId, selected.preferredCandidateId)) return choice;
                if(choice.candidate != null && choice.candidate.material.equals(selected.material)
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

    private static boolean eq(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
