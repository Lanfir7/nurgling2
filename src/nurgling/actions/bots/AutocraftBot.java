package nurgling.actions.bots;

import haven.*;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.scenarios.CraftPreset;
import nurgling.scenarios.CraftPresetManager;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tasks.NTask;
import nurgling.tools.ItemVariants;
import nurgling.tools.VariantCraftOptions;
import nurgling.widgets.NMakewindow;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Predicate;

/**
 * AutocraftBot executes crafting based on a saved preset.
 * Used in scenarios to automate crafting with pre-configured recipes.
 */
public class AutocraftBot implements Action {
    private String presetId;
    private int quantity = 1;

    public AutocraftBot() {
    }

    public AutocraftBot(Map<String, Object> settings) {
        if (settings != null) {
            if (settings.containsKey("presetId")) {
                this.presetId = (String) settings.get("presetId");
            }
            if (settings.containsKey("quantity")) {
                Object q = settings.get("quantity");
                if (q instanceof Integer) {
                    this.quantity = (Integer) q;
                } else if (q instanceof Long) {
                    this.quantity = ((Long) q).intValue();
                } else if (q instanceof Number) {
                    this.quantity = ((Number) q).intValue();
                }
            }
        }
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (presetId == null || presetId.isEmpty()) {
            return Results.ERROR("No craft preset selected");
        }

        CraftPreset preset = CraftPresetManager.getInstance().getPreset(presetId);
        if (preset == null) {
            return Results.ERROR("Craft preset not found: " + presetId);
        }

        gui.msg("Starting autocraft: " + preset.getName() + " x" + quantity);

        // Get the recipe resource path and activate it via menu
        String recipeResource = preset.getRecipeResource();
        if (recipeResource == null || recipeResource.isEmpty()) {
            return Results.ERROR("No recipe resource in preset");
        }

        // Check if craft window is already open with the correct recipe
        boolean needToOpenRecipe = true;
        if (gui.craftwnd != null && gui.craftwnd.makeWidget != null) {
            NMakewindow existingMwnd = gui.craftwnd.makeWidget;
            if (existingMwnd.recipeResource != null && existingMwnd.recipeResource.equals(recipeResource)) {
                needToOpenRecipe = false;
            }
        }

        if (needToOpenRecipe) {
            // Capture old makeWidget so we can wait for the NEW one to appear
            final NMakewindow oldMwnd = (gui.craftwnd != null) ? gui.craftwnd.makeWidget : null;

            // Load the resource and find the pagina
            Indir<Resource> res = Resource.remote().load(recipeResource);
            MenuGrid.Pagina pag = gui.menu.paginafor(res);

            if (pag == null) {
                return Results.ERROR("Could not find recipe in menu: " + recipeResource);
            }

            // Activate the recipe via menu
            gui.menu.use(pag.button(), new MenuGrid.Interaction(), false);

            // Wait for a NEW crafting window to appear (different from the old one)
            NUtils.addTask(new NTask() {
                @Override
                public boolean check() {
                    return gui.craftwnd != null && gui.craftwnd.makeWidget != null
                            && gui.craftwnd.makeWidget != oldMwnd;
                }
            });
        }

        NMakewindow mwnd = gui.craftwnd.makeWidget;
        if (mwnd == null) {
            return Results.ERROR("Crafting window did not open");
        }

        // Enable auto mode FIRST - this is needed for tick() to populate categories
        mwnd.autoMode = true;

        // Wait for inputs to be fully populated (names loaded and categories checked)
        // In headless mode, skip sprite check since sprites won't render
        final boolean isHeadless = nurgling.headless.Headless.isHeadless();
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                if (mwnd.inputs == null || mwnd.inputs.isEmpty()) {
                    return false;
                }
                // Ensure all specs have their names loaded (skip sprite check in headless mode)
                for (NMakewindow.Spec spec : mwnd.inputs) {
                    if (spec.name == null) {
                        return false;
                    }
                    if (!isHeadless && spec.spr == null) {
                        return false;
                    }
                }
                return true;
            }
        });

        // Verify recipe name matches
        if (preset.getRecipeName() != null && !preset.getRecipeName().isEmpty()) {
            if (!preset.getRecipeName().equals(mwnd.rcpnm)) {
                gui.msg("Warning: Recipe name mismatch. Expected: " + preset.getRecipeName() + ", Got: " + mwnd.rcpnm);
            }
        }

        // Configure ingredients from preset
        Results configured = configureIngredients(mwnd, preset);
        if (!configured.IsSuccess())
            return configured;

        // Run the craft
        Craft craft = new Craft(mwnd, quantity);
        Results result = craft.run(gui);

        gui.msg("Autocraft completed: " + preset.getName());
        return result;
    }

    /**
     * Configures the NMakewindow ingredients based on preset preferences.
     */
    private Results configureIngredients(NMakewindow mwnd, CraftPreset preset) {
        for (CraftPreset.InputSpec presetInput : preset.getInputs()) {
            for (NMakewindow.Spec spec : mwnd.inputs) {
                if (spec.name != null && spec.name.equals(presetInput.getName())) {
                    spec.isSubCraft = presetInput.isSubCraft();
                    spec.isLocalZone = presetInput.isLocalZone();
                    spec.useCategory = presetInput.isUseCategory();

                    if (presetInput.getSelectedZoneId() > 0) {
                        spec.selectedZoneId = presetInput.getSelectedZoneId();
                        NArea zone = findAreaById(presetInput.getSelectedZoneId());
                        if (zone != null) {
                            spec.selectedZone = zone;
                        }
                    }

                    if (presetInput.isCategory()) {
                        if (presetInput.isIgnored()) {
                            spec.ing = mwnd.new Ingredient(
                                Resource.loadsimg("nurgling/hud/autocraft/ignore"),
                                "Ignore ingredient",
                                true
                            );
                        } else if (presetInput.getPreferredIngredient() != null) {
                            if (!setPreferredIngredient(mwnd, spec, presetInput.getPreferredIngredient()))
                                return Results.ERROR("Preferred ingredient is unavailable: " + presetInput.getPreferredIngredient());
                        }
                    }
                    break;
                }
            }
        }
        for (int i = 0; i < preset.getOutputs().size() && i < mwnd.outputs.size(); i++) {
            CraftPreset.OutputSpec presetOutput = preset.getOutputs().get(i);
            NMakewindow.Spec outSpec = mwnd.outputs.get(i);
            outSpec.isInventory = presetOutput.isInventory();
            outSpec.isLocalZone = presetOutput.isLocalZone();

            if (presetOutput.getSelectedZoneId() > 0) {
                outSpec.selectedZoneId = presetOutput.getSelectedZoneId();
                NArea zone = findAreaById(presetOutput.getSelectedZoneId());
                if (zone != null) {
                    outSpec.selectedZone = zone;
                }
            }
        }
        return Results.SUCCESS();
    }

    private NArea findAreaById(int areaId) {
        try {
            for (java.util.Map.Entry<Integer, NArea> entry :
                    NUtils.getGameUI().map.glob.map.areas.entrySet()) {
                if (entry.getKey() == areaId) {
                    return entry.getValue();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Sets the preferred ingredient for a category spec.
     * An explicit preset choice must resolve exactly; otherwise crafting stops before fetching.
     */
    private boolean setPreferredIngredient(NMakewindow mwnd, NMakewindow.Spec spec, String preferredName) {
        boolean hasDedicatedSource = hasDedicatedSource(spec.selectedZone != null, spec.isLocalZone, spec.isSubCraft);
        JSONObject choice = preferredOption(spec.name, preferredName, hasDedicatedSource,
                name -> NContext.findIn(name) != null || NContext.findInGlobal(name) != null);
        if (choice == null)
            return false;
        spec.ing = mwnd.new Ingredient(choice);
        spec.useCategory = false;
        return true;
    }

    static JSONObject preferredOption(String category, String preferredName,
                                      boolean hasDedicatedSource, Predicate<String> hasArea) {
        if (preferredName == null)
            return null;
        ArrayList<JSONObject> categoryItems = VariantCraftOptions.options(category);
        if (categoryItems.isEmpty()) {
            return null;
        }

        for (JSONObject obj : categoryItems) {
            if (obj == null) {
                continue;
            }
            if (!obj.has("name")) {
                continue;
            }
            String itemName = obj.getString("name");
            if (preferredName.equals(itemName))
                return hasDedicatedSource || hasArea.test(itemName) || (ItemVariants.isKey(itemName)
                        && hasArea.test(ItemVariants.base(itemName))) ? obj : null;
        }
        return null;
    }

    static boolean hasDedicatedSource(boolean selectedZone, boolean localZone, boolean subCraft) {
        return selectedZone || localZone || subCraft;
    }
}
