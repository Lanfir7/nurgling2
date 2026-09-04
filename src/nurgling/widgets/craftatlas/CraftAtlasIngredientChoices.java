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
}
