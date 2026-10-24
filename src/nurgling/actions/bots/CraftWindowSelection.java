package nurgling.actions.bots;

import nurgling.areas.NArea;
import nurgling.widgets.NMakewindow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The exact make-window choice a one-off bot is allowed to craft or restore. */
final class CraftWindowSelection {
    private final NMakewindow window;
    private final String recipe;
    private final boolean autoMode, noTransfer;
    private final List<Spec> inputs, outputs;

    CraftWindowSelection(NMakewindow window) {
        this.window = window;
        recipe = window.rcpnm;
        autoMode = window.autoMode;
        noTransfer = window.noTransfer != null && window.noTransfer.a;
        inputs = snapshot(window.inputs);
        outputs = snapshot(window.outputs);
    }

    private static List<Spec> snapshot(List<NMakewindow.Spec> specs) {
        List<Spec> copy = new ArrayList<>(specs.size());
        for(NMakewindow.Spec spec : specs) copy.add(new Spec(spec));
        return copy;
    }

    boolean matches(NMakewindow current) {
        if(current == null || current != window || !Objects.equals(recipe, current.rcpnm)
                || autoMode != current.autoMode || current.noTransfer == null
                || noTransfer != current.noTransfer.a
                || inputs.size() != current.inputs.size() || outputs.size() != current.outputs.size())
            return false;
        for(int i = 0; i < inputs.size(); i++)
            if(!inputs.get(i).matches(current.inputs.get(i))) return false;
        for(int i = 0; i < outputs.size(); i++)
            if(!outputs.get(i).matches(current.outputs.get(i))) return false;
        return true;
    }

    private static final class Spec {
        final NMakewindow.Spec ref;
        final String name, ingredientName;
        final int count, selectedZoneId;
        final NMakewindow.Ingredient ingredient;
        final boolean ignored, categories, useCategory, isSubCraft, isLocalZone, isInventory;
        final NArea selectedZone;
        final Map<String, NArea> subIngredientZones;

        Spec(NMakewindow.Spec spec) {
            ref = spec;
            name = spec.name;
            count = spec.count;
            ingredient = spec.ing;
            ingredientName = ingredient == null ? null : ingredient.name;
            ignored = ingredient != null && ingredient.isIgnored;
            categories = spec.categories;
            useCategory = spec.useCategory;
            isSubCraft = spec.isSubCraft;
            isLocalZone = spec.isLocalZone;
            isInventory = spec.isInventory;
            selectedZone = spec.selectedZone;
            selectedZoneId = spec.selectedZoneId;
            subIngredientZones = spec.subIngredientZones == null
                    ? null : new HashMap<>(spec.subIngredientZones);
        }

        boolean matches(NMakewindow.Spec spec) {
            return spec == ref && Objects.equals(name, spec.name) && count == spec.count
                    && ingredient == spec.ing
                    && Objects.equals(ingredientName, spec.ing == null ? null : spec.ing.name)
                    && ignored == (spec.ing != null && spec.ing.isIgnored)
                    && categories == spec.categories && useCategory == spec.useCategory
                    && isSubCraft == spec.isSubCraft && isLocalZone == spec.isLocalZone
                    && isInventory == spec.isInventory && selectedZone == spec.selectedZone
                    && selectedZoneId == spec.selectedZoneId
                    && Objects.equals(subIngredientZones, spec.subIngredientZones);
        }
    }
}
