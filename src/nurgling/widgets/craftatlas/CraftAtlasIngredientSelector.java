package nurgling.widgets.craftatlas;

import haven.Coord;
import haven.Dropbox;
import haven.GOut;
import haven.UI;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Candidate;
import nurgling.craftatlas.CraftAtlasMaterialPlanner.Selection;
import nurgling.widgets.craftatlas.CraftAtlasIngredientChoices.Choice;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** One recipe-input selector backed by inventory and warehouse stock. */
public final class CraftAtlasIngredientSelector extends Dropbox<Choice> {
    private final Consumer<Selection> listener;
    private List<Choice> choices = Collections.emptyList();
    private boolean notifying = true;

    public CraftAtlasIngredientSelector(int width, List<Candidate> candidates, boolean optional,
                                        boolean grouped, Selection selected,
                                        Consumer<Selection> listener) {
        super(width, Math.min(10, Math.max(1, CraftAtlasIngredientChoices.choices(candidates, optional, grouped).size())), UI.scale(22));
        this.listener = listener;
        setChoices(candidates, optional, grouped, selected);
    }

    public void setChoices(List<Candidate> candidates, boolean optional, boolean grouped, Selection selected) {
        choices = CraftAtlasIngredientChoices.choices(candidates, optional, grouped);
        Choice match = findSelection(selected);
        if(match == null) {
            Selection fallback = optional && (selected == null || selected.isIgnored())
                    ? Selection.ignored() : nurgling.craftatlas.CraftAtlasMaterialPlanner.defaultSelection(candidates);
            match = findSelection(fallback);
        }
        notifying = false;
        super.change(match == null && !choices.isEmpty() ? choices.get(0) : match);
        notifying = true;
    }

    public Selection selection() {
        return sel == null ? Selection.all() : sel.selection;
    }

    @Override protected Choice listitem(int i) { return choices.get(i); }
    @Override protected int listitems() { return choices.size(); }

    @Override protected void drawitem(GOut g, Choice item, int i) {
        if(item != null) g.atext(item.label, Coord.of(UI.scale(4), itemh / 2), 0, 0.5);
    }

    @Override public void change(Choice item) {
        super.change(item);
        if(notifying && item != null && listener != null) listener.accept(item.selection);
    }

    private Choice findSelection(Selection selection) {
        if(selection == null) return null;
        for(Choice choice : choices) {
            Selection value = choice.selection;
            if(value.mode != selection.mode) continue;
            if(value.mode == Selection.Mode.PREFERRED) {
                if(eq(value.preferredCandidateId, selection.preferredCandidateId)) return choice;
            } else {
                return choice;
            }
        }
        return null;
    }

    private static boolean eq(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
