package nurgling.actions.bots;

import haven.*;
import haven.res.ui.tt.ingred.Ingredient;
import nurgling.NConfig;
import nurgling.NCore;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.areas.NContext;
import nurgling.cookbook.GenericTypes;
import nurgling.cookbook.Variations;
import nurgling.cookbook.connection.RecipeCatalogLoader;
import nurgling.db.dao.StorageItemDao;
import nurgling.i18n.L10n;
import nurgling.tasks.NTask;
import nurgling.tools.CraftCategories;
import nurgling.tools.ItemIcons;
import nurgling.tools.ItemVariants;
import nurgling.tools.VariantCraftOptions;
import nurgling.widgets.NCookBook;
import nurgling.widgets.NMakewindow;
import nurgling.widgets.NStorageItemsWidget;
import nurgling.widgets.storage.VillageStorage;
import org.json.JSONObject;

import java.util.*;

/**
 * Crafts a food once for every untried option of one of its ingredient choices (the cookbook's
 * untried variations), so the cookbook records them. The other choices stay at what the player
 * picked for them, or autocraft's own pick. Only the village's storage and input areas are drawn from, and each craft
 * fetches its own ingredients: one fetch, one craft. A food made in stages (Meatpie from Unbaked
 * Meatpie) is crafted to its first stage and recorded once the player finishes it.
 */
public class TryUntriedVariations implements Action {
    /** A pick for an optional choice: go without it. */
    public static final String NONE = "\u0000none";

    private final String dish;
    private final String slotTitle;
    private final Map<String, String> others;
    private final List<String> craftNames;
    private final VillageStorage village;
    private final Runnable done;

    /* One craft: the option tried, or null to go without the optional input. */
    private static final class Attempt {
        final Variations.Option option;
        final JSONObject ingredient;

        Attempt(Variations.Option option, JSONObject ingredient) {
            this.option = option;
            this.ingredient = ingredient;
        }

        String label() {
            return (option == null) ? "without it" : option.name;
        }
    }

    /**
     * @param others     per other choice's title, the option to keep it at or {@link #NONE}; a choice
     *                   not in it is left to autocraft
     * @param craftNames the crafting actions that may make the food, from {@link Variations#craftNames}
     * @param village    read on the UI thread
     * @param done       run when the bot ends, whatever the outcome
     */
    public TryUntriedVariations(String dish, String slotTitle, Map<String, String> others, List<String> craftNames, VillageStorage village, Runnable done) {
        this.dish = dish;
        this.slotTitle = slotTitle;
        this.others = Map.copyOf(others);
        this.craftNames = List.copyOf(craftNames);
        this.village = village;
        this.done = done;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        try {
            return tryAll(gui);
        } finally {
            if(done != null)
                done.run();
        }
    }

    private Results tryAll(NGameUI gui) throws InterruptedException {
        if(!(Boolean)NConfig.get(NConfig.Key.ndbenable) || (NCore.databaseManager == null))
            return Results.ERROR("Try untried variations needs the database");
        /* Strictly the village: with no village found, VillageStorage would let every recorded chest in. */
        Set<Long> chunks = village != null && village.knowable() ? village.village() : Collections.emptySet();
        if(chunks.isEmpty())
            return Results.ERROR("The village can't be worked out yet (needs ChunkNav and areas)");

        /* Recorded variants as of now, so what was crafted since the cookbook loaded counts as tried. */
        RecipeCatalogLoader loader = new RecipeCatalogLoader(NCore.databaseManager, village);
        loader.run();
        if(loader.error() != null)
            return Results.ERROR("Could not read the cookbook: " + loader.error());
        Variations variations = loader.variations();
        if(variations == null)
            return Results.ERROR("The offline recipe data could not be read");
        Variations.Dish d = variations.of(dish);
        Variations.Slot slot = null;
        for(Variations.Slot s : d.slots) {
            if(slotTitle.equals(s.title))
                slot = s;
        }
        if(slot == null)
            return Results.ERROR(dish + " has no ingredient choice " + slotTitle);

        NMakewindow mw = openWindow(gui);
        if(mw == null)
            return Results.ERROR("You don't know a recipe that makes " + dish);
        String window = mw.rcpnm;
        boolean staged = !GenericTypes.norm(window).equals(GenericTypes.norm(dish));

        int target = specFor(variations, mw, slot);
        if(target < 0)
            return Results.ERROR(slotTitle + " is not chosen in the " + window + " window");
        String targetName = mw.inputs.get(target).name;

        /* The other choices, as picked; a null ingredient goes without the input. */
        Map<Integer, JSONObject> baseline = new HashMap<>();
        Set<String> names = new HashSet<>();
        for(Variations.Slot s : d.slots) {
            String pick = others.get(s.title);
            if(s.wood() || (s == slot) || (pick == null))
                continue;
            int i = specFor(variations, mw, s);
            if(!mappedOther(i, target))
                return Results.ERROR("Can't map selected " + s.title + " to a distinct input in " + window);
            if(pick.equals(NONE)) {
                if(!mw.inputs.get(i).opt())
                    return Results.ERROR(s.title + " can't be left out of " + window);
                baseline.put(i, null);
                continue;
            }
            Variations.Option o = null;
            for(Variations.Option c : s.options) {
                if(c.name.equals(pick))
                    o = c;
            }
            JSONObject ing = (o != null) ? ingredient(variations, mw.inputs.get(i).name, o) : null;
            if(ing == null)
                return Results.ERROR("Can't select " + pick + " for " + s.title + " in the " + window + " window");
            baseline.put(i, ing);
            names.add(ing.getString("name"));
        }
        List<Attempt> attempts = new ArrayList<>();
        List<String> missing = new ArrayList<>(), unselectable = new ArrayList<>();
        Map<Variations.Option, JSONObject> untried = new LinkedHashMap<>();
        Set<String> picked = new HashSet<>();
        for(Variations.Option o : slot.options) {
            if(o.tried())
                continue;
            JSONObject ing = ingredient(variations, targetName, o);
            if(ing == null)
                unselectable.add(o.name);
            else if(picked.add(ing.getString("name"))) {
                untried.put(o, ing);
                names.add(ing.getString("name"));
            }
        }
        if(!unselectable.isEmpty())
            gui.msg(dish + ": can't select " + String.join(", ", unselectable) + " in the " + window + " window");
        Map<String, List<StorageItemDao.StorageItemData>> source;
        try {
            source = indexRows(chunks);
        } catch(Exception e) {
            return Results.ERROR("Could not read village storage: " + e.getMessage());
        }
        for(Map.Entry<Variations.Option, JSONObject> e : untried.entrySet()) {
            if(available(e.getValue().getString("name"), source))
                attempts.add(new Attempt(e.getKey(), e.getValue()));
            else
                missing.add(e.getKey().name);
        }
        if(canOmit(slot, mw.inputs.get(target)))
            attempts.add(new Attempt(null, null));
        for(JSONObject ing : baseline.values()) {
            if((ing != null) && !available(ing.getString("name"), source))
                return Results.ERROR(ing.getString("name") + " is not in the village's storage or an input area");
        }

        if(attempts.isEmpty()) {
            gui.msg(dish + ": no untried " + slotTitle + " in the village"
                    + (missing.isEmpty() ? "" : " (not in storage: " + String.join(", ", missing) + ")"));
            return Results.SUCCESS();
        }
        List<String> labels = new ArrayList<>();
        for(Attempt a : attempts)
            labels.add(a.label());
        gui.msg(dish + ": trying " + attempts.size() + " " + slotTitle + " — " + String.join(", ", labels));
        System.out.println("[TryUntried] " + dish + " / " + slotTitle + ": " + labels
                + ", others " + others + ", not in storage " + missing);

        List<String> tried = new ArrayList<>();
        for(Attempt a : attempts) {
            mw = currentWindow(gui, window);
            if(mw == null)
                return stopped(gui, tried, a, "the " + window + " window could not be opened again");
            Results r = craftOne(gui, mw, target, a, baseline, source);
            if(!r.IsSuccess())
                return stopped(gui, tried, a, null);
            tried.add(a.label());
        }

        StringBuilder sum = new StringBuilder(dish + ": tried " + tried.size() + " " + slotTitle);
        if(!missing.isEmpty())
            sum.append("; not in storage: ").append(String.join(", ", missing));
        gui.msg(sum.toString());
        if(staged)
            gui.msg("Finish these " + tried.size() + " " + window + " into " + dish + " to record them in the cookbook");
        return Results.SUCCESS();
    }

    private static Results stopped(NGameUI gui, List<String> tried, Attempt at, String why) {
        String head = "Tried " + tried.size() + (tried.isEmpty() ? "" : " (" + String.join(", ", tried) + ")")
                + ", stopped at " + at.label();
        return (why != null) ? Results.ERROR(head + ": " + why) : Results.ERROR(head);
    }

    /* ---- the crafting window ---- */

    private NMakewindow openWindow(NGameUI gui) throws InterruptedException {
        MenuGrid.Pagina pag = NCookBook.recipePagina(gui, craftNames);
        if(pag == null)
            return null;
        String name = pag.button().name();
        NMakewindow existing = (gui.craftwnd != null) ? gui.craftwnd.makeWidget : null;
        if((existing != null) && sameRecipe(existing, name)) {
            return ready(gui, existing) ? existing : null;
        }
        gui.menu.use(pag.button(), new MenuGrid.Interaction(), false);
        NTask opened = new NTask() {
            { infinite = false; maxCounter = 480; }
            @Override
            public boolean check() {
                return (gui.craftwnd != null) && (gui.craftwnd.makeWidget != null) && (gui.craftwnd.makeWidget != existing)
                        && sameRecipe(gui.craftwnd.makeWidget, name);
            }
        };
        NUtils.addTask(opened);
        if(opened.criticalExit || gui.craftwnd == null || gui.craftwnd.makeWidget == null)
            return null;
        NMakewindow mw = gui.craftwnd.makeWidget;
        return ready(gui, mw) ? mw : null;
    }

    /* The open window when it still shows the recipe (lighting a fire can swap it), else the recipe reopened. */
    private NMakewindow currentWindow(NGameUI gui, String window) throws InterruptedException {
        NMakewindow mw = (gui.craftwnd != null) ? gui.craftwnd.makeWidget : null;
        if((mw != null) && sameRecipe(mw, window))
            return mw;
        mw = openWindow(gui);
        return ((mw != null) && sameRecipe(mw, window)) ? mw : null;
    }

    private static boolean sameRecipe(NMakewindow mw, String name) {
        return (mw.rcpnm != null) && GenericTypes.norm(mw.rcpnm).equals(GenericTypes.norm(name));
    }

    /* Autocraft on, and every input named and sorted into category or not, as AutocraftBot waits. */
    private static boolean ready(NGameUI gui, NMakewindow mw) throws InterruptedException {
        final boolean headless = nurgling.headless.Headless.isHeadless();
        NTask task = new NTask() {
            { infinite = false; maxCounter = 480; }
            @Override
            public boolean check() {
                if((mw.inputs == null) || mw.inputs.isEmpty())
                    return false;
                for(NMakewindow.Spec s : mw.inputs) {
                    if((s.name == null) || (!headless && (s.spr == null)))
                        return false;
                }
                return true;
            }
        };
        NUtils.addTask(task);
        return !task.criticalExit && gui.craftwnd != null && gui.craftwnd.makeWidget == mw;
    }

    /* ---- matching choices to the window's inputs ---- */

    /* The category input of the window a choice's options go into, or -1. */
    private static int specFor(Variations variations, NMakewindow mw, Variations.Slot slot) {
        int best = -1, bestFit = 0;
        boolean ambiguous = false;
        for(int i = 0; i < mw.inputs.size(); i++) {
            NMakewindow.Spec s = mw.inputs.get(i);
            if(!CraftCategories.has(s.name))
                continue;
            int fit = 0;
            for(Variations.Option o : slot.options) {
                if(variations.fits(s.name, o.name) || variations.fits(s.name, o.item))
                    fit++;
            }
            if(fit > bestFit) {
                best = i;
                bestFit = fit;
                ambiguous = false;
            } else if(fit > 0 && fit == bestFit) {
                ambiguous = true;
            }
        }
        return ambiguous ? -1 : best;
    }

    static boolean canOmit(Variations.Slot slot, NMakewindow.Spec input) {
        return slot.optional && !slot.noneTried && input.opt();
    }

    static boolean mappedOther(int spec, int target) {
        return spec >= 0 && spec != target;
    }

    /*
     * What to select in a category input for an option: the input's own entry for one of the names
     * its item is stored under, else one built from the item's icon, as the cookbook's craft button does.
     */
    private static JSONObject ingredient(Variations variations, String input, Variations.Option o) {
        String kindName = null;
        if(ItemVariants.MILKS.contains(o.name) || ItemVariants.MILKS.contains(o.item)) {
            String milk = ItemVariants.MILKS.contains(o.name) ? o.name : o.item;
            if(ItemVariants.BUTTER.equals(input))
                kindName = ItemVariants.key(ItemVariants.BUTTER, milk);
            else if(ItemVariants.MILK.equals(input))
                kindName = milk;
        }
        Set<String> keys = new HashSet<>(o.stockKeys());
        List<String> names = new ArrayList<>(variations.itemNames(o.name));
        names.addAll(variations.itemNames(o.item));
        for(String n : names)
            keys.add(GenericTypes.stockKey(n));
        List<JSONObject> options = VariantCraftOptions.options(input);
        if(options != null) {
            JSONObject selected = null;
            for(JSONObject item : options) {
                String name = item.optString("name");
                if((kindName != null && kindName.equals(name))
                        || (kindName == null && keys.contains(GenericTypes.stockKey(name)))) {
                    if(selected != null && !selected.optString("name").equals(name))
                        return null;
                    selected = item;
                }
            }
            if(selected != null)
                return new JSONObject(selected.toString());
        }
        if(kindName != null && ItemVariants.BUTTER.equals(input))
            return null;
        if(!variations.fits(input, o.name) && !variations.fits(input, o.item))
            return null;
        JSONObject icon = ItemIcons.descriptor(null, names.get(0), false);
        if((icon != null) && (icon.has("static") || icon.has("layer")))
            return new JSONObject(icon.toString()).put("name", kindName != null ? kindName : names.get(0));
        return null;
    }

    /** What the picker can honestly offer before the bot's fresh storage read. */
    public static boolean supportedSource(Variations variations, Variations.Option option, Set<String> countable) {
        List<String> names = new ArrayList<>(variations.itemNames(option.name));
        names.addAll(variations.itemNames(option.item));
        for(String name : names) {
            if(NContext.findIn(name) != null || NContext.findInGlobal(name) != null)
                return true;
            if(!ItemVariants.isKey(name) && ItemVariants.liquidOf(name) == null
                    && !ItemVariants.MILK.equals(name) && !ItemVariants.has(name)
                    && countable.contains(GenericTypes.stockKey(name)))
                return true;
        }
        for(String milk : ItemVariants.MILKS) {
            if(milk.equals(option.name) || milk.equals(option.item)) {
                String butter = ItemVariants.key(ItemVariants.BUTTER, milk);
                if(NContext.findIn(butter) != null || NContext.findInGlobal(butter) != null
                        || NContext.findIn(ItemVariants.BUTTER) != null
                        || NContext.findInGlobal(ItemVariants.BUTTER) != null)
                    return true;
            }
        }
        return false;
    }

    static Map<String, List<StorageItemDao.StorageItemData>> countableRows(
            Collection<StorageItemDao.StorageItemData> rows) {
        Map<String, List<StorageItemDao.StorageItemData>> result = new HashMap<>();
        for(StorageItemDao.StorageItemData row : rows)
            if(row != null && !row.isBulk() && row.getName() != null && row.getQuality() >= 0)
                result.computeIfAbsent(row.getName(), k -> new ArrayList<>()).add(row);
        return result;
    }

    private static Map<String, List<StorageItemDao.StorageItemData>> indexRows(Set<Long> village) throws Exception {
        List<StorageItemDao.StorageItemData> all = NCore.databaseManager.getStorageItemService().loadAllStorageItems();
        return countableRows(VillageStorage.inVillage(VillageStorage.valid(all), village));
    }

    private static boolean available(String name, Map<String, List<StorageItemDao.StorageItemData>> source) {
        if(NContext.findIn(name) != null || NContext.findInGlobal(name) != null)
            return true;
        if(ItemVariants.isKey(name))
            return NContext.findIn(ItemVariants.base(name)) != null
                    || NContext.findInGlobal(ItemVariants.base(name)) != null;
        if(ItemVariants.liquidOf(name) != null || ItemVariants.MILK.equals(name) || ItemVariants.has(name))
            return false;
        return source.containsKey(name) && !source.get(name).isEmpty();
    }

    /* ---- one craft ---- */

    private Results craftOne(NGameUI gui, NMakewindow mw, int target, Attempt a,
                             Map<Integer, JSONObject> baseline,
                             Map<String, List<StorageItemDao.StorageItemData>> source) throws InterruptedException {
        WindowState original = new WindowState(mw);
        try {
            if(gui.vhand != null || gui.map == null || gui.map.placing != null)
                return Results.ERROR("Put the held item away and cancel placement before crafting");
            double energy = NUtils.getEnergy();
            if(!Double.isFinite(energy) || energy < 0.25)
                return Results.ERROR("Energy is unknown or too low to craft without eating");
            Map<NMakewindow.Spec, NMakewindow.Ingredient> planned = new LinkedHashMap<>();
            NMakewindow.Spec ts = mw.inputs.get(target);
            planned.put(ts, a.option == null ? ignore(mw) : mw.new Ingredient(a.ingredient));
            for(Map.Entry<Integer, JSONObject> e : baseline.entrySet()) {
                NMakewindow.Spec s = mw.inputs.get(e.getKey());
                planned.put(s, e.getValue() == null ? ignore(mw) : mw.new Ingredient(e.getValue()));
            }
            for(NMakewindow.Spec s : mw.inputs) {
                if(!CraftCategories.has(s.name) || s.ing != null || planned.containsKey(s))
                    continue;
                List<JSONObject> choices = VariantCraftOptions.options(s.name);
                JSONObject only = null;
                if(choices != null) for(JSONObject choice : choices) {
                    if(available(choice.optString("name"), source)) {
                        if(only != null)
                            return Results.ERROR("Choose " + s.name + " explicitly: several sources are available");
                        only = choice;
                    }
                }
                if(only == null)
                    return Results.ERROR("No safe source for " + s.name);
                planned.put(s, mw.new Ingredient(new JSONObject(only.toString())));
            }
            mw.autoMode = true;
            mw.noTransfer.a = true; // Keep all outputs and unrelated inventory items.
            for(Map.Entry<NMakewindow.Spec, NMakewindow.Ingredient> e : planned.entrySet()) {
                NMakewindow.Spec s = e.getKey();
                s.categories = true;
                s.useCategory = false;
                s.ing = e.getValue();
            }
            original.captureApplied(mw);
            String stray = stray(gui, mw);
            if(stray != null)
                return Results.ERROR("Inventory holds " + stray + "; put it away before trying variations");
            if(!original.appliedMatches(mw))
                return Results.ERROR("The ingredient choice changed while checking inventory");

            Map<String, Integer> preloaded = new LinkedHashMap<>();
            for(NMakewindow.Spec s : mw.inputs) {
                if(s.ing != null && s.ing.isIgnored)
                    continue;
                String name = s.ing != null ? s.ing.name : s.name;
                if(name == null)
                    return Results.ERROR("A crafting input is still loading");
                if(availableInArea(name))
                    continue;
                if(ItemVariants.isKey(name) || ItemVariants.liquidOf(name) != null
                        || ItemVariants.MILK.equals(name)
                        || ItemVariants.has(name) || s.count <= 0)
                    return Results.ERROR("No safe input area for " + name);
                List<StorageItemDao.StorageItemData> rows = source.get(name);
                if(rows == null || rows.size() < s.count)
                    return Results.ERROR("Not enough countable " + name + " in village storage");
                preloaded.merge(name, s.count, Integer::sum);
            }
            for(Map.Entry<String, Integer> e : preloaded.entrySet()) {
                List<StorageItemDao.StorageItemData> rows = source.get(e.getKey());
                NStorageItemsWidget.GroupedItem group = new NStorageItemsWidget.GroupedItem(
                        e.getKey(), -1, rows.size(), rows);
                FetchStorageItemBot fetch = new FetchStorageItemBot(group, e.getValue(), rows);
                Results taken = fetch.run(gui);
                if(!taken.IsSuccess() || fetch.actualCollected() < e.getValue())
                    return Results.ERROR("Storage fetch was not confirmed for " + e.getKey());
            }

            int before = outputCount(gui, mw);
            if(before < 0)
                return Results.ERROR("Output names are not loaded");
            if(gui.getInventory().getFreeSpace() <= 0)
                return Results.ERROR("Make room for the crafted food");
            if(strayExceptPreloaded(gui, mw, preloaded.keySet()) != null)
                return Results.ERROR("A different ingredient appeared in the inventory");
            if(!original.appliedMatches(mw))
                return Results.ERROR("The ingredient choice changed before crafting");
            Results crafted = new Craft(mw, 1).withConfirmedPreloaded(preloaded).preserveConsumables().run(gui);
            if(!crafted.IsSuccess())
                return crafted;
            if(!awaitOutput(gui, mw, before))
                return Results.ERROR("Craft output was not confirmed; stopped before another option");
            return Results.SUCCESS();
        } finally {
            if(gui.craftwnd != null && gui.craftwnd.makeWidget == mw)
                original.restoreIfOwned(mw);
        }
    }

    private static NMakewindow.Ingredient ignore(NMakewindow mw) {
        return mw.new Ingredient(Resource.loadsimg("nurgling/hud/autocraft/ignore"), L10n.get("craft.ignore_ingredient"), true);
    }

    private static boolean availableInArea(String name) {
        if(NContext.findIn(name) != null || NContext.findInGlobal(name) != null)
            return true;
        return ItemVariants.isKey(name) && (NContext.findIn(ItemVariants.base(name)) != null
                || NContext.findInGlobal(ItemVariants.base(name)) != null);
    }

    private static final class SpecState {
        final NMakewindow.Spec spec;
        final NMakewindow.Ingredient ing;
        final boolean categories, useCategory, isSubCraft, isLocalZone, isInventory;
        final nurgling.areas.NArea selectedZone;
        final int selectedZoneId;
        final Map<String, nurgling.areas.NArea> subIngredientZones;

        SpecState(NMakewindow.Spec s) {
            spec = s;
            ing = s.ing;
            categories = s.categories;
            useCategory = s.useCategory;
            isSubCraft = s.isSubCraft;
            isLocalZone = s.isLocalZone;
            isInventory = s.isInventory;
            selectedZone = s.selectedZone;
            selectedZoneId = s.selectedZoneId;
            subIngredientZones = new HashMap<>(s.subIngredientZones);
        }

        void restore() {
            spec.ing = ing;
            spec.categories = categories;
            spec.useCategory = useCategory;
            spec.isSubCraft = isSubCraft;
            spec.isLocalZone = isLocalZone;
            spec.isInventory = isInventory;
            spec.selectedZone = selectedZone;
            spec.selectedZoneId = selectedZoneId;
            spec.subIngredientZones.clear();
            spec.subIngredientZones.putAll(subIngredientZones);
        }
    }

    private static final class WindowState {
        final List<SpecState> inputs = new ArrayList<>(), outputs = new ArrayList<>();
        final boolean autoMode, noTransfer;
        final String recipe;
        CraftWindowSelection applied;

        WindowState(NMakewindow mw) {
            recipe = mw.rcpnm;
            autoMode = mw.autoMode;
            noTransfer = mw.noTransfer.a;
            for(NMakewindow.Spec s : mw.inputs) inputs.add(new SpecState(s));
            for(NMakewindow.Spec s : mw.outputs) outputs.add(new SpecState(s));
        }

        boolean owned(NMakewindow mw) {
            if(mw == null || !Objects.equals(recipe, mw.rcpnm)
                    || mw.inputs.size() != inputs.size() || mw.outputs.size() != outputs.size())
                return false;
            for(int i = 0; i < inputs.size(); i++)
                if(mw.inputs.get(i) != inputs.get(i).spec) return false;
            for(int i = 0; i < outputs.size(); i++)
                if(mw.outputs.get(i) != outputs.get(i).spec) return false;
            return true;
        }

        void captureApplied(NMakewindow mw) {
            applied = new CraftWindowSelection(mw);
        }

        boolean appliedMatches(NMakewindow mw) {
            return applied != null && applied.matches(mw);
        }

        boolean restoreIfOwned(NMakewindow mw) {
            if(!owned(mw) || !appliedMatches(mw)) return false;
            mw.autoMode = autoMode;
            mw.noTransfer.a = noTransfer;
            for(SpecState s : inputs) if(mw.inputs.contains(s.spec)) s.restore();
            for(SpecState s : outputs) if(mw.outputs.contains(s.spec)) s.restore();
            mw.refreshModeUi();
            return true;
        }
    }

    /* Before fetching, any matching inventory item could be consumed instead of village stock. */
    private static String stray(NGameUI gui, NMakewindow mw) throws InterruptedException {
        return strayExceptPreloaded(gui, mw, Set.of());
    }

    private static String strayExceptPreloaded(NGameUI gui, NMakewindow mw,
                                                Set<String> allowed) throws InterruptedException {
        Set<String> possible = new HashSet<>();
        for(NMakewindow.Spec s : mw.inputs) {
            if(CraftCategories.has(s.name)) {
                List<JSONObject> options = VariantCraftOptions.options(s.name);
                if(options == null || options.isEmpty())
                    return "an unresolved ingredient category";
                for(JSONObject o : options)
                    possible.add(ItemVariants.base(o.optString("name")));
            } else if(s.ing == null || !s.ing.isIgnored) {
                possible.add(ItemVariants.base(s.ing != null ? s.ing.name : s.name));
            }
        }
        for(WItem item : gui.getInventory().getItems()) {
            if(!(item.item instanceof NGItem))
                return "an unread item";
            String name = ((NGItem)item.item).name();
            if(name == null)
                return "an unread item";
            if(possible.contains(name) && !allowed.contains(name))
                return name;
            if(ItemVariants.BUTTER.equals(name) && possible.contains(ItemVariants.BUTTER))
                return name;
        }
        return null;
    }

    static int outputUnits(NMakewindow mw, Widget first) {
        Set<String> names = new HashSet<>();
        for(NMakewindow.Spec s : mw.outputs) {
            String name = s.ing != null ? s.ing.name : s.name;
            if(name == null) return -1;
            names.add(name);
        }
        if(names.isEmpty()) return -1;
        return outputUnits(first, names);
    }

    private static int outputUnits(Widget first, Set<String> names) {
        int count = 0;
        for(Widget w = first; w != null; w = w.next) {
            if(!(w instanceof WItem)) continue;
            WItem item = (WItem)w;
            if(item.item.contents != null) {
                int nested = outputUnits(item.item.contents.child, names);
                if(nested < 0) return -1;
                count += nested;
                continue;
            }
            if(!(item.item instanceof NGItem)) return -1;
            NGItem ng = (NGItem)item.item;
            String name = ng.name();
            if(name == null) return -1;
            if(names.contains(name)) {
                try {
                    GItem.Amount amount = ng.getInfo(GItem.Amount.class);
                    count += amount == null ? 1 : Math.max(1, amount.itemnum());
                } catch(Loading loading) {
                    return -1;
                }
            }
        }
        return count;
    }

    private static int outputCount(NGameUI gui, NMakewindow mw) {
        return outputUnits(mw, gui.getInventory().child);
    }

    private static boolean awaitOutput(NGameUI gui, NMakewindow mw, int before) throws InterruptedException {
        long deadline = System.nanoTime() + 8_000_000_000L;
        while(System.nanoTime() < deadline) {
            int now = outputCount(gui, mw);
            if(now > before) return true;
            Thread.sleep(50);
        }
        return false;
    }

}
