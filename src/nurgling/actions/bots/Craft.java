package nurgling.actions.bots;

import haven.*;
import haven.res.lib.itemtex.ItemTex;
import haven.res.ui.tt.cn.CustomName;
import nurgling.*;
import nurgling.actions.*;
import nurgling.areas.*;
import nurgling.tasks.*;
import nurgling.tools.*;
import nurgling.widgets.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.awt.image.BufferedImage;

import static haven.OCache.posres;
import nurgling.tools.StackSupporter;
import nurgling.tools.RecipeIngredientCache;


public class Craft implements Action {


    public Craft(List<NMakewindow.Spec> in, List<NMakewindow.Spec> out, String station, int count) {

    }

    public Craft(List<NMakewindow.Spec> in, List<NMakewindow.Spec> out, String station) {
        this(in, out, station, 1);
    }

    public Craft(NMakewindow mwnd, int size) {
        this.mwnd = mwnd;
        this.count = size;
    }

    public Craft(NMakewindow mwnd) {
        this(mwnd, 1);
    }

    /**
     * Создает Craft в режиме prefilled - ингредиенты уже в инвентаре.
     * TakeItems2 будет пропущен, крафт начнётся сразу.
     * @param mwnd окно крафта
     * @param size количество крафтов
     * @param prefilled true если ингредиенты уже в инвентаре
     */
    public Craft(NMakewindow mwnd, int size, boolean prefilled) {
        this.mwnd = mwnd;
        this.count = size;
        this.prefilled = prefilled;
    }

    NMakewindow mwnd = null;
    String tools = null;
    int count = 0;

    boolean isGlobalMode = false;

    /**
     * Режим prefilled - ингредиенты уже загружены в инвентарь.
     * Пропускает TakeItems2 при крафте.
     */
    boolean prefilled = false;
    /** Opt-in for a caller that already fetched exact, countable ingredients from the storage index. */
    private Map<String, Integer> confirmedPreloaded = Collections.emptyMap();
    private boolean preserveConsumables = false;
    private CraftWindowSelection protectedSelection;
    private boolean storageIndexMode = false;
    private nurgling.widgets.storage.VillageStorage storageVillage;
    private TakeFromStorageIndex.Source storageSource;
    private final Set<String> storageInputs = new HashSet<>();
    private int storageCrafted = 0;

    /** Only the craft-window toggle opts in; presets and one-off variation crafts keep their old routes. */
    public Craft withStorageIndex(nurgling.widgets.storage.VillageStorage village) {
        this.storageIndexMode = true;
        this.storageVillage = village;
        return preserveConsumables();
    }

    public Craft withConfirmedPreloaded(Map<String, Integer> counts) {
        this.confirmedPreloaded = counts == null ? Collections.emptyMap() : Map.copyOf(counts);
        return this;
    }

    /** Opt-in for one-off exploration crafts: never eat or use the player's drinking vessels. */
    public Craft preserveConsumables() {
        this.preserveConsumables = true;
        this.protectedSelection = mwnd == null ? null : new CraftWindowSelection(mwnd);
        return this;
    }

    boolean protectedWindowReady(NGameUI gui) {
        if(!preserveConsumables)
            return true;
        return gui != null && gui.craftwnd != null && protectedSelection != null
                && gui.craftwnd.makeWidget == mwnd && protectedSelection.matches(mwnd);
    }

    private Results verifyConfirmedPreloaded(NGameUI gui) throws InterruptedException {
        if(confirmedPreloaded.isEmpty())
            return Results.SUCCESS();
        Map<String, Integer> required = neededPreloaded(mwnd.inputs, confirmedPreloaded.keySet(), count);
        for(NMakewindow.Spec spec : mwnd.inputs) {
            if(spec.ing != null && spec.ing.isIgnored)
                continue;
            String name = ingredientKey(spec);
            if(!confirmedPreloaded.containsKey(name))
                continue;
            if(name == null || ItemVariants.isKey(name) || ItemVariants.has(name)
                    || isMeasuredSpec(spec) || isWaterSpec(spec) || spec.count <= 0)
                return Results.ERROR("Unsupported preloaded ingredient: " + name);
        }
        for(Map.Entry<String, Integer> entry : confirmedPreloaded.entrySet()) {
            String name = entry.getKey();
            int need = required.getOrDefault(name, 0);
            if(need <= 0 || entry.getValue() < need)
                return Results.ERROR("Unconfirmed preloaded ingredient: " + name);
            if(!sufficientPreloaded(gui.getInventory().getItems(new NAlias(name)), name, need))
                return Results.ERROR("Not enough preloaded " + name);
        }
        return Results.SUCCESS();
    }

    static Map<String, Integer> neededPreloaded(List<NMakewindow.Spec> inputs,
                                                Set<String> names, int crafts) {
        Map<String, Integer> required = new HashMap<>();
        for(NMakewindow.Spec spec : inputs) {
            if(spec.ing != null && spec.ing.isIgnored)
                continue;
            String name = ingredientKey(spec);
            if(names.contains(name))
                required.merge(name, spec.count * crafts, Integer::sum);
        }
        return required;
    }

    static boolean sufficientPreloaded(Collection<WItem> items, String name, int need) {
        if(need <= 0) return false;
        int held = 0;
        for(WItem item : items)
            if(item.item instanceof NGItem && name.equals(((NGItem)item.item).name()))
                held += preloadedUnits(item);
        return held >= need;
    }

    int subCraftDepth = 0;
    private static final int MAX_SUB_CRAFT_DEPTH = 10;
    private Set<String> subCraftedItems = new HashSet<>();

    private int getActualItemCount(WItem item) {
        if (item.item.info != null) {
            for (ItemInfo inf : item.item.info) {
                if (inf instanceof CustomName) {
                    float count = ((CustomName) inf).count;
                    if (count > 0) {
                        return (int) (count * 100);
                    }
                }
            }
        }
        return 1;
    }

    /** Counts one fetched leaf or its loaded bundle amount, never both a wrapper and its contents. */
    static int preloadedUnits(WItem item) {
        if(item == null || !(item.item instanceof NGItem) || item.item.contents != null)
            return 0;
        GItem.Amount amount = ((NGItem)item.item).getInfo(GItem.Amount.class);
        return amount == null ? 1 : Math.max(0, amount.itemnum());
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (mwnd != null) {
            if (!RecipeIngredientCache.isDbLoaded()) {
                RecipeIngredientCache.loadFromDatabase();
            }
            return mwnd_run(gui);
        }
        return Results.SUCCESS();
    }

    private Results mwnd_run(NGameUI gui) throws InterruptedException {
        if(!protectedWindowReady(gui))
            return Results.ERROR("The selected crafting recipe changed; stopped before crafting");
        Results preloaded = verifyConfirmedPreloaded(gui);
        if(!preloaded.IsSuccess())
            return preloaded;
        if(storageIndexMode) {
            for(NMakewindow.Spec spec : mwnd.inputs) {
                if(spec.ing != null && spec.ing.isIgnored) continue;
                if(spec.selectedZone == null && (spec.isLocalZone || spec.isSubCraft))
                    return Results.ERROR("Choose an explicit input area or disable storage-index mode for local and sub-craft inputs");
            }
            if(storageVillage == null || !storageVillage.knowable())
                return Results.ERROR("Village storage is not ready; no indexed items were touched");
            Set<Long> village = storageVillage.village();
            if(village.isEmpty())
                return Results.ERROR("Village storage is unknown; no indexed items were touched");
            Set<String> candidates = new LinkedHashSet<>();
            for(NMakewindow.Spec spec : mwnd.inputs) {
                if(spec.ing != null && spec.ing.isIgnored) continue;
                String chosen = ingredientKey(spec);
                if(TakeFromStorageIndex.eligibleName(chosen)) candidates.add(chosen);
                if(spec.categories && spec.ing == null && !spec.useCategory)
                    for(org.json.JSONObject option : VariantCraftOptions.options(spec.name)) {
                        String name = option.optString("name", null);
                        if(TakeFromStorageIndex.eligibleName(name)) candidates.add(name);
                    }
            }
            storageSource = TakeFromStorageIndex.Source.load(candidates, village);
            if(!storageSource.available())
                return Results.ERROR("Storage index could not be read; no indexed items were touched");
            for(NMakewindow.Spec spec : mwnd.inputs)
                if(spec.categories && spec.ing == null && !spec.useCategory)
                    selectIngredientFromCategory(spec);
            if(!protectedWindowReady(gui))
                return Results.ERROR("The selected crafting ingredient changed; stopped");
        }
        // Phase 0: Collect all Local Zone selections upfront before any crafting
        if (mwnd.autoMode && subCraftDepth == 0 && !preserveConsumables) {
            resolveLocalZones(gui);
        }

        // Phase 1: Resolve missing ingredients via sub-crafts
        if (mwnd.autoMode && subCraftDepth < MAX_SUB_CRAFT_DEPTH && !preserveConsumables) {
            resolveSubCrafts(gui);
        }

        NContext ncontext = new NContext(gui);
        int size = 0;
        for (NMakewindow.Spec s : mwnd.inputs) {
            // Skip ignored optional ingredients
            if (s.ing != null && s.ing.isIgnored) {
                continue;
            }
            // Recipe water comes from a barrel at the fire. Waterskins stay for drinking.
            if (isWaterSpec(s)) {
                if (!attachWaterBarrel(ncontext, ingredientKey(s)))
                    return Results.ERROR("No water barrel. Waterskins are kept for drinking.");
                continue;
            }

            // Determine the item name: if useCategory is set, use category name; otherwise use specific item
            String itemName;
            BufferedImage itemImg;
            if (s.useCategory && s.categories && s.name != null && VSpec.categories.containsKey(s.name)) {
                // useCategory checkbox is set - use category name
                itemName = s.name;
                // Используем ванильную иконку для категории
                try {
                    if("Block of Wood".equals(itemName)) {
                        itemImg = Resource.loadsimg("gfx/invobjs/wblock-oak");
                    } else if("Board".equals(itemName)) {
                        itemImg = Resource.loadsimg("gfx/invobjs/board-oak");
                    } else {
                        itemImg = ItemTex.create(ItemTex.save(s.spr));
                    }
                } catch (Exception e) {
                    itemImg = ItemTex.create(ItemTex.save(s.spr));
                }
            } else if (!s.categories) {
                itemName = s.name;
                itemImg = ItemTex.create(ItemTex.save(s.spr));
            } else if (s.ing != null) {
                itemName = s.ing.name;
                itemImg = s.ing.img;
            } else {
                // Auto-select any available ingredient from category
                selectIngredientFromCategory(s);
                if (s.ing != null && !s.ing.isIgnored) {
                    itemName = s.ing.name;
                    itemImg = s.ing.img;
                } else {
                    continue; // Не удалось выбрать ингредиент
                }
            }

            if(storageIndexMode && s.categories && s.ing == null && !s.useCategory)
                return Results.ERROR("Choose an exact ingredient before storage autocraft");

            // Skip zone lookup for sub-crafted items (already in inventory)
            if (subCraftedItems.contains(itemName)) {
                continue;
            }

            if(confirmedPreloaded.containsKey(itemName)) {
                // The exact input is already present. Do not request a new TAKE area.
            } else if(storageIndexMode && storageIndexEligible(s, itemName)) {
                if(storageSource == null || !storageSource.has(itemName))
                    return Results.ERROR("No TAKE area or countable village storage for " + itemName);
                storageInputs.add(itemName);
            } else if (s.selectedZone != null) {
                ncontext.addInItemWithArea(itemName, s.selectedZone);
            } else if (s.isLocalZone) {
                ncontext.addInItem(itemName, itemImg);
            } else {
                ncontext.addInItem(itemName, itemImg);
            }
            // Доски и блоки обычно не хранятся в бочках, проверяем только если это не категория
            if (!VSpec.categories.containsKey(itemName)) {
                if (!ncontext.isInBarrel(itemName)) {
                    size += s.count;
                }
            } else {
                // Для категорий считаем, что они не в бочках
                size += s.count;
            }
        }

        for (NMakewindow.Spec s : mwnd.outputs) {

            if (s.isInventory) {
                // Leave in Inventory — skip output zone setup for this item
                continue;
            }

            if (!mwnd.noTransfer.a && !storageIndexMode) {
                String outName = s.ing != null ? s.ing.name : s.name;
                if(s.selectedZone != null && outName != null) {
                    if(!ncontext.isInBarrel(outName))
                        size += s.count;
                    ncontext.addOutItemWithArea(outName, s.selectedZone, 1);
                } else if(s.isLocalZone && outName != null) {
                    if(!ncontext.isInBarrel(outName))
                        size += s.count;
                    java.awt.image.BufferedImage outImg = s.spr != null ? ItemTex.create(ItemTex.save(s.spr)) : null;
                    ncontext.addOutItem(outName, outImg, 1);
                } else if (!s.categories) {
                    if(!ncontext.isInBarrel(s.name))
                        size += s.count;
                    ncontext.addOutItem(s.name,
                            hasKindOutArea(ncontext, s.name) ? null : ItemTex.create(ItemTex.save(s.spr)), 1);
                } else if (s.ing != null) {
                    if(!ncontext.isInBarrel(s.ing.name))
                        size += s.count;
                    ncontext.addOutItem(s.ing.name, s.ing.img, 1);
                }
            }
        }

        if (!mwnd.tools.isEmpty()) {
            ncontext.addTools(mwnd.tools);
        } else {
            if (mwnd.outputs.size() == 1) {
                String outName = mwnd.outputs.get(0).name;
                ncontext.addCustomTool(outName);
            }
        }

        if (storageIndexMode && ncontext.equip != null)
            return Results.ERROR("Equip the required crafting tool manually before storage autocraft");
        if (ncontext.equip != null)
            new Equip(new NAlias(ncontext.equip)).run(gui);

        AtomicInteger left = new AtomicInteger(count);

        for (NMakewindow.Spec s : mwnd.inputs) {
            // Skip ignored optional ingredients
            if (s.ing != null && s.ing.isIgnored) {
                continue;
            }
            
            String item = ingredientKey(s);
            if (item == null || !ncontext.isInBarrel(item))
                continue;
            if (ncontext.workstation == null) {
                NArea barrelwa = ncontext.getSpecArea(Specialisation.SpecName.barrelworkarea);
                if (barrelwa == null)
                    return Results.ERROR("Not found area for work with barrels!");
                ncontext.bwaused = true;
            } else {
                ncontext.bwaused = true;
            }
        }

        // Prepare workstation once before craft loop
        if (ncontext.workstation != null) {
            if (!new PrepareWorkStation(ncontext, ncontext.workstation.station).run(gui).IsSuccess()) {
                return Results.ERROR("Failed to prepare workstation");
            }
            if (ncontext.workstation.targetPoint != null) {
                new PathFinder(ncontext.workstation.targetPoint.getCurrentCoord()).run(gui);
            }
            // Refresh mwnd reference after PrepareWorkStation (may have changed due to LightFire)
            refreshMakeWidget(gui);
            if(!protectedWindowReady(gui))
                return Results.ERROR("The selected crafting recipe changed; stopped before crafting");
        }

        Results craftResult = null;
        while (left.get() > 0 && !CraftTarget.reachedCap(count - left.get(), count)) {
            craftResult = crafting(ncontext, gui, size, left);
            if (!craftResult.IsSuccess()) {
                return craftResult;
            }
        }

        for (NMakewindow.Spec s : mwnd.inputs) {
            // Skip ignored optional ingredients
            if (s.ing != null && s.ing.isIgnored) {
                continue;
            }
            
            String item = ingredientKey(s);
            if (item != null && ncontext.isInBarrel(item)) {
                new ReturnBarrelFromWorkArea(ncontext, item).run(gui);
            }
        }

        if (!preserveConsumables && !mwnd.noTransfer.a) {
            new FreeInventory2(ncontext).run(gui);
        }


        return Results.SUCCESS();
    }

    Results crafting(NContext ncontext, NGameUI gui, int size, AtomicInteger left) throws InterruptedException {
        if(!protectedWindowReady(gui))
            return Results.ERROR("The selected crafting recipe changed; stopped before crafting");

        double currentEnergy = NUtils.getEnergy();

        if(preserveConsumables && (!Double.isFinite(currentEnergy) || currentEnergy < 0.25))
            return Results.ERROR("Energy is unknown or too low to craft without eating");

        if (currentEnergy < 0.25) {
            if (!new RestoreResources().run(gui).IsSuccess()) {
                return Results.ERROR("Energy too low and failed to restore resources");
            }
        }
        
        int freeSpace = NUtils.getGameUI().getInventory().getFreeSpace();

        int for_craft;
        if (size == 0) {
            for_craft = left.get();
        } else {
            // Use stack-aware calculation for better inventory utilization
            for_craft = calculateMaxCraftsWithStacking(ncontext, freeSpace, left.get());
        }
        if(storageIndexMode) for_craft = Math.min(for_craft, 1);
        for_craft = CraftTarget.capIterations(count, for_craft);
        for_craft = Math.min(for_craft, left.get());
        

        if (for_craft <= 0) {
            return Results.ERROR("Not enough inventory space");
        }
        
        Map<String, Integer> indexedNeed = new LinkedHashMap<>();
        for (NMakewindow.Spec s : mwnd.inputs) {
            // Auto-select ingredient from category if not already selected
            if (s.categories && s.ing == null) {
                selectIngredientFromCategory(s);
            }

            // Skip ignored optional ingredients
            if (s.ing != null && s.ing.isIgnored) {
                continue;
            }

            // Determine the item name: if useCategory is set, use category name; otherwise use specific item
            String item;
            if (s.useCategory && s.categories && s.name != null && VSpec.categories.containsKey(s.name)) {
                // useCategory checkbox is set - use category name
                item = s.name;
            } else if (s.ing != null && VSpec.categories.containsKey(s.ing.name)) {
                // Category selected - use category name
                item = s.ing.name;
            } else {
                // Specific item selected or no selection
                item = s.ing == null ? s.name : s.ing.name;
            }

            // Sub-crafted items are already in inventory — skip zone fetch
            if (subCraftedItems.contains(item)) {
                continue;
            }
            if (isWaterSpec(s) && !ncontext.isInBarrel(item)) {
                continue;
            }

            if(confirmedPreloaded.containsKey(item)) {
                Results ready = verifyConfirmedPreloaded(gui);
                if(!ready.IsSuccess())
                    return ready;
                continue;
            }
            if(storageIndexMode && storageInputs.contains(item)) {
                int units = s.count * for_craft;
                if(units <= 0 || indexedNeed.getOrDefault(item, 0) > Integer.MAX_VALUE - units)
                    return Results.ERROR("Invalid storage ingredient quantity: " + item);
                indexedNeed.merge(item, units, Integer::sum);
                continue;
            }
            if (ItemVariants.isKey(item)) {
                Results ready = prepareButterKind(ncontext, gui, item);
                if (!ready.IsSuccess())
                    return ready;
            }

            if (ncontext.isInBarrel(item) && ncontext.getPlacedBarrelHash(item) == null) {
                Results moved = ncontext.workstation == null
                        ? new TransferBarrelInWorkArea(ncontext, item).run(gui)
                        : new TransferBarrelToWorkstation(ncontext, item).run(gui);
                if (!moved.IsSuccess())
                    return Results.ERROR("Failed to bring barrel: " + item);
            } else if (!prefilled && !ncontext.isInBarrel(item)) {
                if (isMeasuredSpec(s)) {
                    Results pulled = pullMeasuredFromStorage(ncontext, gui, item, s.count * for_craft);
                    if (!pulled.IsSuccess()) {
                        return pulled;
                    }
                } else {
                    int needed = s.count * for_craft;
                    needed -= heldHundredths(item);
                    if (needed > 0) {
                        if (!new TakeItems2(ncontext, item, needed).run(gui).IsSuccess()) {
                            return Results.ERROR("Failed to take items: " + item);
                        }
                    }
                }
            }
        }

        for(Map.Entry<String, Integer> need : indexedNeed.entrySet()) {
            if(!protectedWindowReady(gui))
                return Results.ERROR("Craft selection changed while fetching storage items");
            TakeFromStorageIndex fetch = new TakeFromStorageIndex(storageSource, need.getKey(),
                    need.getValue(), () -> protectedWindowReady(gui));
            if(!fetch.run(gui).IsSuccess()) {
                if(CraftTarget.isAll(count) && storageCrafted > 0
                        && fetch.outcome() == TakeFromStorageIndex.Outcome.EXHAUSTED) {
                    left.set(0);
                    return Results.SUCCESS();
                }
                return Results.ERROR("Could not confirm indexed ingredient: " + need.getKey());
            }
        }

        for (NMakewindow.Spec s : mwnd.inputs) {
            if (s.ing != null && s.ing.isIgnored)
                continue;
            String selected = s.ing != null ? s.ing.name : s.name;
            if (ItemVariants.isKey(selected)) {
                Results verified = verifyButterInventory(gui, selected);
                if (!verified.IsSuccess())
                    return verified;
            }
        }



        if (ncontext.workstation != null) {
            if (!new UseWorkStation(ncontext).run(gui).IsSuccess()) {
                return Results.ERROR("Failed to use workstation");
            }
        }
        else if (ncontext.bwaused) {
            NArea barrelwa = ncontext.getSpecArea(Specialisation.SpecName.barrelworkarea);
            Pair<Coord2d, Coord2d> rcArea = barrelwa.getRCArea();
            Coord2d center = rcArea.b.sub(rcArea.a).div(2).add(rcArea.a);
            new PathFinder(center).run(gui);
        }

        openBarrelWindows(ncontext, gui);
        ArrayList<Window> windows = NUtils.getGameUI().getWindows("Barrel");
        
        boolean hasEnoughResources = true;
        String insufficientItem = null;
        double foundAmount = 0;
        double requiredAmount = 0;
        
        for (NMakewindow.Spec s : mwnd.inputs) {
            // Skip ignored optional ingredients
            if (s.ing != null && s.ing.isIgnored) {
                continue;
            }
            
            // Determine the item name: if useCategory is set, use category name; otherwise use specific item
            String item;
            if (s.useCategory && s.categories && s.name != null && VSpec.categories.containsKey(s.name)) {
                // useCategory checkbox is set - use category name
                item = s.name;
            } else if (s.ing != null && VSpec.categories.containsKey(s.ing.name)) {
                // Category selected - use category name
                item = s.ing.name;
            } else {
                // Specific item selected or no selection
                item = s.ing == null ? s.name : s.ing.name;
            }
            if (ncontext.isInBarrel(item)) {
                double val = barrelAmount(gui, windows, item);
                
                // Handle case when barrel content not found (-1 means not found)
                if (val < 0) {
                    hasEnoughResources = false;
                    insufficientItem = item;
                    foundAmount = 0; // Not found = 0
                    requiredAmount = s.count;
                    break;
                }
                
                double valInMilligrams = val * 100;
                if(valInMilligrams < s.count)
                {
                    hasEnoughResources = false;
                    insufficientItem = item;
                    foundAmount = valInMilligrams;
                    requiredAmount = s.count;
                    break;
                }
            }
        }
        
        if (!hasEnoughResources) {
            for (NMakewindow.Spec s : mwnd.inputs) {
                // Skip ignored optional ingredients
                if (s.ing != null && s.ing.isIgnored) {
                    continue;
                }
                
                // Determine the item name: if useCategory is set, use category name; otherwise use specific item
                String item;
                if (s.useCategory && s.categories && s.name != null && VSpec.categories.containsKey(s.name)) {
                    // useCategory checkbox is set - use category name
                    item = s.name;
                } else if (s.ing != null && VSpec.categories.containsKey(s.ing.name)) {
                    // Category selected - use category name
                    item = s.ing.name;
                } else {
                    // Specific item selected or no selection
                    item = s.ing == null ? s.name : s.ing.name;
                }
                
                if (ncontext.isInBarrel(item)) {
                    new ReturnBarrelFromWorkArea(ncontext, item).run(gui);
                }
            }
            return Results.ERROR("Not enough resources in barrels: '" + insufficientItem + 
                    "' found " + String.format("%.2f", foundAmount) + 
                    ", required " + String.format("%.2f", requiredAmount));
        }

        if(!preserveConsumables)
            new Drink(0.9, false).run(gui);
        int resfc = for_craft;
        String targetName = null;
        for (NMakewindow.Spec s : mwnd.outputs) {
            String itemName = s.ing != null ? s.ing.name : s.name;
            int outputMultiplier = NContext.getOutputMultiplier(itemName);
            resfc = s.count * for_craft * outputMultiplier;
            ArrayList<WItem> currentItems;
            if (s.ing != null) {
                targetName = s.ing.name;
                currentItems = NUtils.getGameUI().getInventory().getItems(new NAlias(s.ing.name));
            } else {
                targetName = s.name;
                currentItems = NUtils.getGameUI().getInventory().getItems(new NAlias(s.name));
            }
            
            int actualCurrentCount = 0;
            for (WItem item : currentItems) {
                actualCurrentCount += getActualItemCount(item);
            }
            
            resfc += actualCurrentCount;
            
        }

        String receiptName = null;
        int receiptBefore = 0;
        int receiptRequired = 1;
        if(storageIndexMode) {
            for(NMakewindow.Spec out : mwnd.outputs) {
                String candidate = out.ing == null ? out.name : out.ing.name;
                if(candidate != null && !candidate.isBlank() && !out.categories) {
                    receiptName = ItemVariants.base(candidate);
                    long expected = (long) out.count * NContext.getOutputMultiplier(receiptName);
                    if(expected < 1 || expected > Integer.MAX_VALUE)
                        return Results.ERROR("Storage autocraft output quantity is invalid");
                    receiptRequired = (int) expected;
                    break;
                }
            }
            if(receiptName == null)
                return Results.ERROR("Storage autocraft cannot verify this recipe output");
            receiptBefore = TakeFromStorageIndex.countExactUnits(gui.getInventory(), receiptName);
            if(receiptBefore < 0)
                return Results.ERROR("Storage autocraft output is still loading");
        }
        int completed = 0;
        boolean batchDone;
        if (CraftTarget.isAll(count) && !storageIndexMode) {
            batchDone = craftProc(ncontext, gui, resfc, targetName);
            completed = for_craft;
        } else {
            completed = craftOneByOne(gui, for_craft);
            batchDone = completed == for_craft;
        }
        if(storageIndexMode && !batchDone)
            return Results.ERROR("Storage autocraft did not confirm the requested craft");
        if(storageIndexMode) {
            final String outputName = receiptName;
            final int before = receiptBefore;
            final int required = receiptRequired;
            final boolean[] arrived = {false};
            NUtils.addTask(new NTask() {
                { infinite = false; criticalOnTimeout = false; maxCounter = 200; }
                @Override public boolean check() {
                    int after = TakeFromStorageIndex.countExactUnits(gui.getInventory(), outputName);
                    return arrived[0] = after >= 0 && (long) after - before >= required;
                }
            });
            if(!arrived[0])
                return Results.ERROR("Storage autocraft output was not confirmed; items were kept");
            storageCrafted += completed;
        }

        boolean isCauldron = ncontext.workstation != null &&
                ncontext.workstation.station != null &&
                ncontext.workstation.station.contains("gfx/terobjs/cauldron");

        if (isCauldron)
        {

            Gob cauldron = Finder.findGob(ncontext.workstation.selected);
            PrepareCauldron pc = new PrepareCauldron(cauldron, ncontext);
            pc.run(gui);
            // Refresh mwnd reference after PrepareCauldron (may have changed due to LightFire)
            refreshMakeWidget(gui);
            if(!protectedWindowReady(gui))
                return Results.ERROR("The selected crafting recipe changed; stopped before crafting");
            if(pc.wasUpdate)
            {
                if (!new UseWorkStation(ncontext).run(gui).IsSuccess()) {
                    return Results.ERROR("Failed to use workstation");
                }
                openBarrelWindows(ncontext, gui);
                int retry = cauldronRetry(count, storageIndexMode, completed, for_craft);
                if (retry < 0) {
                    batchDone = craftProc(ncontext, gui, resfc, targetName);
                } else if (retry > 0) {
                    completed += craftOneByOne(gui, retry);
                    batchDone = completed == for_craft;
                }
            }
        }
        if (batchDone && !preserveConsumables) {
            for (NMakewindow.Spec s : mwnd.outputs) {
                if (s.ing != null) {
                    NUtils.getUI().core.addTask(new WaitItems(NUtils.getGameUI().getInventory(), new NAlias(s.ing.name), resfc));
                } else {
                    NUtils.getUI().core.addTask(new WaitItems(NUtils.getGameUI().getInventory(), new NAlias(s.name), resfc));
                }
            }
        }
        HashSet<String> targets = new HashSet<>();
        for (NMakewindow.Spec s : mwnd.outputs) {
            if(preserveConsumables)
                break; // The one-off caller confirms the actual output with its own bounded receipt.
            GetItems gi;
            if (s.ing != null) {
                NUtils.getUI().core.addTask(gi = new GetItems(NUtils.getGameUI().getInventory(), new NAlias(s.ing.name)));
                targets.add(s.ing.name);
            } else {
                NUtils.getUI().core.addTask(gi = new GetItems(NUtils.getGameUI().getInventory(), new NAlias(s.name)));
                targets.add(s.name);
            }


        }
        if (!preserveConsumables && !mwnd.noTransfer.a) {
            new FreeInventory2(ncontext).run(gui);
        }
        if (CraftTarget.isAll(count) || batchDone)
            left.set(left.get() - for_craft);
        else
            left.set(0);
        return Results.SUCCESS();
    }

    private boolean craftProc(NContext ncontext, NGameUI gui, int resfc, String targetName) throws InterruptedException
    {
        final int finalResfc = resfc;
        String finalTargetName = targetName;
        int maxRetries = 3;
        
        for (int retry = 0; retry < maxRetries; retry++) {
            if(!protectedWindowReady(gui))
                return false;
            // Сбрасываем ошибку перед попыткой крафта
            NUtils.getUI().dropLastError();
            
            mwnd.wdgmsg("make", 1);
            
            // Ждём появления прогресс-бара или ошибки
            final boolean[] progAppeared = {false};
            NUtils.addTask(new NTask() {
                private int waitTicks = 0;
                private static final int MAX_WAIT_FOR_PROG = 100;
                
                @Override
                public boolean check() {
                    // Проверяем ошибку
                    String error = NUtils.getUI().getLastError();
                    if (error != null) {
                        return true;
                    }
                    
                    // Прогресс-бар появился
                    if (gui.prog != null && gui.prog.prog > 0) {
                        boolean wsReady = (ncontext.workstation == null) || 
                                          (ncontext.workstation.selected == -1) || 
                                          NUtils.isWorkStationReady(ncontext.workstation.station, Finder.findGob(ncontext.workstation.selected));
                        if (wsReady) {
                            progAppeared[0] = true;
                            return true;
                        }
                    }
                    
                    // Таймаут ожидания прогресс-бара
                    waitTicks++;
                    return waitTicks >= MAX_WAIT_FOR_PROG;
                }
            });
            
            // Если была ошибка при старте - проверяем нужен ли retry
            String startError = NUtils.getUI().getLastError();
            if (startError != null || !progAppeared[0]) {
                if (hasEnoughIngredientsForCraft(ncontext, gui)) {
                    continue; // Повторяем попытку
                } else {
                    // Недостаточно ингредиентов - выходим
                    break;
                }
            }
            
            // Ждём завершения крафта - предметы или ошибка
            final boolean[] craftSucceeded = {false};
            NUtils.addTask(new NTask() {
                private int ticksAfterProgGone = 0;
                private static final int MAX_TICKS_AFTER_PROG = 50;
                
                @Override
                public boolean check() {
                    // Проверяем ошибку
                    String error = NUtils.getUI().getLastError();
                    if (error != null) {
                        return true;
                    }
                    
                    // Проверяем количество предметов
                    GetItems gi = new GetItems(NUtils.getGameUI().getInventory(), new NAlias(finalTargetName));
                    gi.check();
                    if (gi.getResult().size() >= finalResfc) {
                        craftSucceeded[0] = true;
                        return true;
                    }
                    
                    // Прогресс-бар ещё видим - продолжаем ждать
                    if (gui.prog != null && gui.prog.visible) {
                        ticksAfterProgGone = 0;
                        return false;
                    }
                    
                    // Прогресс-бар исчез - даём немного времени на появление предметов
                    ticksAfterProgGone++;
                    return ticksAfterProgGone >= MAX_TICKS_AFTER_PROG;
                }
            });
            
            // Проверяем результат крафта
            if (craftSucceeded[0]) {
                // Успех! Делаем клики для сброса состояния и выходим
                NUtils.getGameUI().map.wdgmsg("click", Coord.z, NUtils.player().rc.floor(posres), 3, 0);
                NUtils.getGameUI().map.wdgmsg("click", Coord.z, NUtils.player().rc.floor(posres), 1, 0);
                return true;
            }
            
            // Крафт не завершился успешно - проверяем нужен ли retry
            if (hasEnoughIngredientsForCraft(ncontext, gui)) {
                continue; // Повторяем попытку
            } else {
                // Недостаточно ингредиентов - делаем клики и выходим
                NUtils.getGameUI().map.wdgmsg("click", Coord.z, NUtils.player().rc.floor(posres), 3, 0);
                NUtils.getGameUI().map.wdgmsg("click", Coord.z, NUtils.player().rc.floor(posres), 1, 0);
                return true;
            }
        }
        
        // Исчерпали все попытки
        NUtils.getGameUI().map.wdgmsg("click", Coord.z, NUtils.player().rc.floor(posres), 3, 0);
        NUtils.getGameUI().map.wdgmsg("click", Coord.z, NUtils.player().rc.floor(posres), 1, 0);
        return true;
    }

    private int craftOneByOne(NGameUI gui, int for_craft) throws InterruptedException {
        int done = 0;
        for (int i = 0; i < for_craft; i++) {
            refreshMakeWidget(gui);
            if(!protectedWindowReady(gui))
                break;
            if (!CraftMake.windowOpen(mwnd))
                break;
            if (NUtils.getUI() != null)
                NUtils.getUI().dropLastError();
            mwnd.wdgmsg("make", 0);
            if (!CraftMake.waitOne(gui, mwnd, storageIndexMode))
                break;
            done++;
        }
        if (NUtils.getGameUI() != null && NUtils.getGameUI().map != null && NUtils.player() != null) {
            NUtils.getGameUI().map.wdgmsg("click", Coord.z, NUtils.player().rc.floor(posres), 3, 0);
            NUtils.getGameUI().map.wdgmsg("click", Coord.z, NUtils.player().rc.floor(posres), 1, 0);
        }
        return done;
    }

    static int cauldronRetry(int requested, boolean storageIndexMode, int completed, int batch) {
        if (storageIndexMode) return 0;
        return CraftTarget.isAll(requested) ? -1 : Math.max(0, batch - completed);
    }
    
    /**
     * Check if there are enough ingredients in inventory and open containers to continue crafting
     */
    private boolean hasEnoughIngredientsForCraft(NContext ncontext, NGameUI gui) {
        try {
            ArrayList<Window> barrelWindows = gui.getWindows("Barrel");
            
            for (NMakewindow.Spec s : mwnd.inputs) {
                // Skip ignored optional ingredients
                if (s.ing != null && s.ing.isIgnored) {
                    continue;
                }
                
                // Determine the item name: if useCategory is set, use category name; otherwise use specific item
                String itemName;
                if (s.useCategory && s.categories && s.name != null && VSpec.categories.containsKey(s.name)) {
                    // useCategory checkbox is set - use category name
                    itemName = s.name;
                } else if (s.ing != null && VSpec.categories.containsKey(s.ing.name)) {
                    // Category selected - use category name
                    itemName = s.ing.name;
                } else {
                    // Specific item selected or no selection
                    itemName = s.ing == null ? s.name : s.ing.name;
                }
                int required = s.count;
                
                if (ncontext.isInBarrel(itemName)) {
                    // Check barrel content
                    double val = barrelAmount(gui, barrelWindows, itemName);
                    if (val < 0 || val * 100 < required) {
                        return false;
                    }
                } else {
                    // Check inventory
                    int available = 0;
                    
                    // Если itemName является категорией, ищем все предметы из этой категории
                    if(VSpec.categories.containsKey(itemName)) {
                        ArrayList<org.json.JSONObject> categoryItems = nurgling.tools.VSpec.categories.get(itemName);
                        if(categoryItems != null) {
                            for(org.json.JSONObject categoryItem : categoryItems) {
                                String categoryItemName = categoryItem.getString("name");
                                ArrayList<WItem> items = gui.getInventory().getItems(new NAlias(categoryItemName));
                                for (WItem item : items) {
                                    available += getActualItemCount(item);
                                }
                            }
                        }
                    } else {
                        // Обычная проверка по точному имени
                        ArrayList<WItem> items = gui.getInventory().getItems(new NAlias(ItemVariants.base(itemName)));
                        for (WItem item : items) {
                            if (!ItemVariants.isKey(itemName) || ItemVariants.matches((NGItem) item.item, itemName))
                                available += getActualItemCount(item);
                        }
                    }
                    
                    // Если недостаточно в инвентаре, проверяем наличие в бартере
                    if (available < required) {
                        // Проверяем, есть ли бартер для этого предмета
                        try {
                            ArrayList<NContext.ObjectStorage> storages = ncontext.getInStorages(itemName);
                            if(storages != null && !storages.isEmpty()) {
                                for(NContext.ObjectStorage storage : storages) {
                                    if(storage instanceof NContext.Barter) {
                                        // Если есть бартер, считаем что предметы доступны (можно купить)
                                        available = required;
                                        break;
                                    }
                                }
                            }
                        } catch (InterruptedException e) {
                            // Игнорируем исключение при проверке
                        }
                    }
                    
                    if (available < required) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openBarrelWindows(NContext ncontext, NGameUI gui) throws InterruptedException {
        ArrayList<Long> barrelIds = GetBarrelsIds(ncontext);
        int count = 0;
        for (Long barrelid : barrelIds) {
            Gob barrel = Finder.findGob(barrelid);
            if (barrel == null) {
                continue;
            }

            double distToBarrel = NUtils.player().rc.dist(barrel.rc);
            if (distToBarrel > 20) {
                new PathFinder(barrel).run(gui);
            }

            gui.map.wdgmsg("click", Coord.z, barrel.rc.floor(posres), 3, 0, 0, (int) barrel.id,
                    barrel.rc.floor(posres), 0, -1);
            count++;
            int finalCount = count;
            NUtils.addTask(new NTask() {
                @Override
                public boolean check() {
                    return NUtils.getGameUI().getWindowsNum("Barrel") >= finalCount;
                }
            });
        }
    }

    ArrayList<Long> GetBarrelsIds(NContext ncontext) throws InterruptedException
    {
        ArrayList<Long> ids = new ArrayList<>();
        NUtils.getGameUI().msg("GetBarrelsIds: Checking " + mwnd.inputs.size() + " inputs for barrel items");
        
        for (NMakewindow.Spec s : mwnd.inputs)
        {
            // Skip ignored optional ingredients
            if (s.ing != null && s.ing.isIgnored) {
                continue;
            }
            
            // Determine the item name: if useCategory is set, use category name; otherwise use specific item
            String item;
            if (s.useCategory && s.categories && s.name != null && VSpec.categories.containsKey(s.name)) {
                // useCategory checkbox is set - use category name
                item = s.name;
            } else if (s.ing != null && VSpec.categories.containsKey(s.ing.name)) {
                // Category selected - use category name
                item = s.ing.name;
            } else {
                // Specific item selected or no selection
                item = s.ing == null ? s.name : s.ing.name;
            }
            String storedHash = ncontext.getPlacedBarrelHash(item);
            NUtils.getGameUI().msg("GetBarrelsIds: Checking item '" + item + "', isInBarrel=" + ncontext.isInBarrel(item) + 
                    ", storedHash=" + (storedHash != null ? storedHash.substring(0, Math.min(16, storedHash.length())) + "..." : "null"));
            
            if (ncontext.isInBarrel(item))
            {
                Gob barrel = ncontext.getBarrelInWorkArea(item);
                NUtils.getGameUI().msg("GetBarrelsIds: getBarrelInWorkArea('" + item + "') returned " + 
                        (barrel != null ? "barrel id=" + barrel.id + " hash=" + barrel.ngob.hash.substring(0, Math.min(16, barrel.ngob.hash.length())) + "..." : "NULL"));
                if (barrel != null)
                    ids.add(barrel.id);
            }
        }
        return ids;
    }

    private static double barrelAmount(NGameUI gui, ArrayList<Window> windows, String requested) {
        String liquid = ItemVariants.liquidOf(requested);
        if (liquid == null)
            return gui.findBarrelContent(windows, new NAlias(requested));
        for (Window window : windows) {
            LiquidContent content = LiquidContent.read(window);
            double amount = selectedLiquidAmount(content, requested);
            if (amount >= 0)
                return amount;
        }
        return -1;
    }

    static double selectedLiquidAmount(LiquidContent content, String requested) {
        return ItemVariants.liquidMatches(content, requested) ? content.amount : -1;
    }

    private static boolean hasKindOutArea(NContext context, String gameName) throws InterruptedException {
        for (String key : ItemVariants.keys(gameName))
            if (context.addOutItem(key, null, 1))
                return true;
        return false;
    }

    /** The server chooses recipe inputs by game name; remove wrong Butter before a kind-specific craft. */
    private Results prepareButterKind(NContext context, NGameUI gui, String selected) throws InterruptedException {
        ArrayList<WItem> held = gui.getInventory().getItems(new NAlias(ItemVariants.base(selected)));
        if (!ItemVariants.awaitKnown(held))
            return Results.ERROR("Butter kind is still loading; crafting stopped before using it.");
        HashSet<String> targets = new HashSet<>();
        for (WItem wrapper : held) {
            NGItem item = (NGItem) wrapper.item;
            if (!ItemVariants.base(selected).equals(item.name()) || ItemVariants.matches(item, selected))
                continue;
            if (prefilled)
                return Results.ERROR(prefilledButterProblem(held, selected));
            double quality = item.quality != null ? item.quality : 1;
            String route = context.outKey(item, quality);
            if (route == null || !context.addOutItem(route, null, quality))
                return Results.ERROR("No PUT area for the other Butter kind; crafting stopped.");
            targets.add(route);
        }
        if (!targets.isEmpty()) {
            TransferItems2 transfer = new TransferItems2(context, targets);
            transfer.filter = item -> !ItemVariants.matches(item, selected);
            if (!transfer.run(gui).IsSuccess())
                return Results.ERROR("Could not return the other Butter kind before crafting.");
        }
        return verifyButterInventory(gui, selected);
    }

    private Results verifyButterInventory(NGameUI gui, String selected) throws InterruptedException {
        ArrayList<WItem> held = gui.getInventory().getItems(new NAlias(ItemVariants.base(selected)));
        if (!ItemVariants.awaitKnown(held))
            return Results.ERROR("Butter kind is still loading; crafting stopped before using it.");
        if (hasWrongButter(held, selected))
            return Results.ERROR("Inventory still contains a different Butter kind; crafting stopped.");
        return Results.SUCCESS();
    }

    static boolean hasWrongButter(Collection<WItem> held, String selected) {
        for (WItem wrapper : held) {
            NGItem item = (NGItem) wrapper.item;
            if (ItemVariants.base(selected).equals(item.name()) && !ItemVariants.matches(item, selected))
                return true;
        }
        return false;
    }

    static String prefilledButterProblem(Collection<WItem> held, String selected) {
        if (ItemVariants.anyUnread(held))
            return "Butter kind is still loading; crafting stopped before using it.";
        return hasWrongButter(held, selected)
                ? "Prefilled inventory contains a different Butter kind." : null;
    }

    private void selectIngredientFromCategory(NMakewindow.Spec spec) {
        if (!spec.categories || spec.ing != null) {
            return;
        }

        // Only VSpec categories have a shared transfer rule. Catalog-only inputs need a concrete choice.
        if (!shouldAutoSelectCategory(spec.name, spec.useCategory)) {
            return;
        }

        // Try to find specific items from category
        ArrayList<org.json.JSONObject> categoryItems = VariantCraftOptions.options(spec.name);
        if (categoryItems.isEmpty()) {
            NUtils.getGameUI().msg("Category '" + spec.name + "' has no known items");
            return;
        }

        NUtils.getGameUI().msg("Searching ingredient for category: " + spec.name + " (" + categoryItems.size() + " options)");

        // Try to find items from category in nearby areas
        for (org.json.JSONObject obj : categoryItems) {
            String itemName = (String) obj.get("name");
            if (NContext.findIn(itemName) != null) {
                NUtils.getGameUI().msg("Found nearby: " + itemName + " for category " + spec.name);
                if(storageIndexMode && !protectedWindowReady(NUtils.getGameUI())) return;
                spec.ing = mwnd.new Ingredient(obj);
                if(storageIndexMode) protectedSelection = new CraftWindowSelection(mwnd);
                return;
            }
        }

        // Global search
        for (org.json.JSONObject obj : categoryItems) {
            String itemName = (String) obj.get("name");
            if (NContext.findInGlobal(itemName) != null) {
                NUtils.getGameUI().msg("Found globally: " + itemName + " for category " + spec.name);
                if(storageIndexMode && !protectedWindowReady(NUtils.getGameUI())) return;
                spec.ing = mwnd.new Ingredient(obj);
                if(storageIndexMode) protectedSelection = new CraftWindowSelection(mwnd);
                return;
            }
        }

        if(storageIndexMode && storageSource != null) {
            for(org.json.JSONObject obj : categoryItems) {
                String itemName = obj.optString("name", null);
                if(TakeFromStorageIndex.eligibleName(itemName) && storageSource.has(itemName)) {
                    if(!protectedWindowReady(NUtils.getGameUI())) return;
                    spec.ing = mwnd.new Ingredient(obj);
                    protectedSelection = new CraftWindowSelection(mwnd);
                    return;
                }
            }
        }

        NUtils.getGameUI().msg("No available ingredients found for category: " + spec.name);
    }

    public static boolean storageIndexEligible(NMakewindow.Spec spec, String itemName) {
        return TakeFromStorageIndex.eligibleName(itemName) && !spec.useCategory
                && spec.selectedZone == null && !spec.isLocalZone && !spec.isSubCraft && !spec.isInventory
                && !isMeasuredSpec(spec) && !isWaterSpec(spec)
                && NContext.findIn(itemName) == null && NContext.findInGlobal(itemName) == null;
    }

    static boolean shouldAutoSelectCategory(String name, boolean useCategory) {
        return !useCategory || !VSpec.categories.containsKey(name);
    }

    /**
     * Pre-collect all [Local Zone] selections before crafting starts.
     * Converts isLocalZone specs into selectedZone with user-picked temp areas.
     */
    private void resolveLocalZones(NGameUI gui) throws InterruptedException {
        for (NMakewindow.Spec s : mwnd.inputs) {
            if (!s.isLocalZone) continue;
            if (s.ing != null && s.ing.isIgnored) continue;
            if (isWaterSpec(s)) continue;

            String itemName = getEffectiveItemName(s);
            if (itemName == null) continue;

            gui.msg("Please select area for: " + itemName);
            BufferedImage itemImg;
            try {
                itemImg = (s.spr != null) ? ItemTex.create(ItemTex.save(s.spr)) : null;
            } catch (Exception e) {
                itemImg = null;
            }

            SelectArea sa = (itemImg != null)
                    ? new SelectArea(Resource.loadsimg("baubles/custom"), itemImg)
                    : new SelectArea(Resource.loadsimg("baubles/custom"));
            sa.run(gui);

            NArea tempArea = new NArea("localIn_" + itemName);
            tempArea.space = sa.result;
            tempArea.lastLocalChange = System.currentTimeMillis();
            tempArea.grids_id.clear();
            tempArea.grids_id.addAll(tempArea.space.space.keySet());

            s.selectedZone = tempArea;
            s.isLocalZone = false;
        }

        for (NMakewindow.Spec s : mwnd.outputs) {
            if (!s.isLocalZone) continue;

            String outName = s.ing != null ? s.ing.name : s.name;
            if (outName == null) continue;

            gui.msg("Please select output area for: " + outName);
            BufferedImage outImg;
            try {
                outImg = (s.spr != null) ? ItemTex.create(ItemTex.save(s.spr)) : null;
            } catch (Exception e) {
                outImg = null;
            }

            SelectArea sa = (outImg != null)
                    ? new SelectArea(Resource.loadsimg("baubles/custom"), outImg)
                    : new SelectArea(Resource.loadsimg("baubles/custom"));
            sa.run(gui);

            NArea tempArea = new NArea("localOut_" + outName);
            tempArea.space = sa.result;
            tempArea.lastLocalChange = System.currentTimeMillis();
            tempArea.grids_id.clear();
            tempArea.grids_id.addAll(tempArea.space.space.keySet());

            s.selectedZone = tempArea;
            s.isLocalZone = false;
        }

        // Pre-resolve inputs that have no zone at all (would trigger addInItem→createArea mid-craft)
        for (NMakewindow.Spec s : mwnd.inputs) {
            if (s.selectedZone != null || s.isLocalZone || s.isSubCraft) continue;
            if (s.ing != null && s.ing.isIgnored) continue;
            if (isWaterSpec(s)) continue;

            String itemName = getEffectiveItemName(s);
            if (itemName == null) continue;

            // Check if a zone can be found automatically
            NArea found = NContext.findIn(itemName);
            if (found == null) found = NContext.findInGlobal(itemName);
            // For VSpec categories, also search by members
            if (found == null && VSpec.categories.containsKey(itemName)) {
                ArrayList<org.json.JSONObject> members = VSpec.categories.get(itemName);
                if (members != null) {
                    for (org.json.JSONObject m : members) {
                        String mName = m.optString("name");
                        if (mName != null) {
                            found = NContext.findIn(mName);
                            if (found == null) found = NContext.findInGlobal(mName);
                            if (found != null) break;
                        }
                    }
                }
            }
            if (found != null) continue;

            gui.msg("Please select area for: " + itemName);
            BufferedImage img;
            try {
                img = (s.spr != null) ? ItemTex.create(ItemTex.save(s.spr)) : null;
            } catch (Exception e) {
                img = null;
            }
            SelectArea sa2 = (img != null)
                    ? new SelectArea(Resource.loadsimg("baubles/custom"), img)
                    : new SelectArea(Resource.loadsimg("baubles/custom"));
            sa2.run(gui);

            NArea tempArea = new NArea("preIn_" + itemName);
            tempArea.space = sa2.result;
            tempArea.lastLocalChange = System.currentTimeMillis();
            tempArea.grids_id.clear();
            tempArea.grids_id.addAll(tempArea.space.space.keySet());

            s.selectedZone = tempArea;
        }

        // Also resolve local zones for sub-ingredient selections
        for (NMakewindow.Spec s : mwnd.inputs) {
            if (s.subIngredientZones == null) continue;
            for (Map.Entry<String, NArea> entry : s.subIngredientZones.entrySet()) {
                if (entry.getValue() != null && "[Local Zone]".equals(entry.getValue().name)) {
                    String subName = entry.getKey();
                    gui.msg("Please select area for sub-ingredient: " + subName);
                    SelectArea sa =
                            new SelectArea(Resource.loadsimg("baubles/custom"));
                    sa.run(gui);

                    NArea tempArea = new NArea("localSub_" + subName);
                    tempArea.space = sa.result;
                    tempArea.lastLocalChange = System.currentTimeMillis();
                    tempArea.grids_id.clear();
                    tempArea.grids_id.addAll(tempArea.space.space.keySet());

                    entry.setValue(tempArea);
                }
            }
        }
    }

    /**
     * Scans inputs for missing zones and attempts to sub-craft those ingredients.
     * After sub-crafting, re-opens the main recipe so mwnd is valid again.
     */
    private void resolveSubCrafts(NGameUI gui) throws InterruptedException {
        String mainRecipeResource = mwnd.recipeResource;
        if (mainRecipeResource == null) return;

        List<SubCraftRequest> requests = new ArrayList<>();

        for (NMakewindow.Spec s : mwnd.inputs) {
            if (s.ing != null && s.ing.isIgnored) continue;

            String itemName = getEffectiveItemName(s);
            if (itemName == null) continue;

            if (subCraftDepth == 0) {
                // Top-level: respect user's Auto UI selection
                if (s.selectedZone != null || s.isLocalZone) continue;
                if (!s.isSubCraft) continue;
            } else {
                // Sub-craft level: automatic detection
                if (SubRecipeResolver.hasZone(itemName)) continue;
                if (s.categories && s.ing == null) continue;
            }

            Set<RecipeIngredientCache.RecipeEntry> recipes = SubRecipeResolver.findRecipesFor(itemName);
            if (recipes.isEmpty()) continue;

            int totalNeeded = s.count * count;

            int inInventory = 0;
            try {
                if (VSpec.categories.containsKey(itemName)) {
                    ArrayList<org.json.JSONObject> members = VSpec.categories.get(itemName);
                    if (members != null) {
                        for (org.json.JSONObject m : members) {
                            String mName = m.optString("name");
                            if (mName != null) {
                                for (WItem wi : NUtils.getGameUI().getInventory().getItems(new NAlias(mName)))
                                    inInventory += getActualItemCount(wi);
                            }
                        }
                    }
                } else {
                    for (WItem wi : NUtils.getGameUI().getInventory().getItems(new NAlias(itemName)))
                        inInventory += getActualItemCount(wi);
                }
            } catch (Exception ignored) {}
            totalNeeded -= inInventory;
            if (totalNeeded <= 0) {
                subCraftedItems.add(itemName);
                continue;
            }

            requests.add(new SubCraftRequest(itemName, totalNeeded, recipes, s.subIngredientZones));
        }

        if (requests.isEmpty()) return;

        for (SubCraftRequest req : requests) {
            RecipeIngredientCache.RecipeEntry recipe;
            if (req.recipes.size() == 1) {
                recipe = req.recipes.iterator().next();
            } else {
                recipe = selectSubRecipeViaDialog(gui, req.itemName, req.recipes);
                if (recipe == null) continue;
            }

            gui.msg("Sub-craft: " + recipe.recipeName + " (" + req.itemName + ") x" + req.count);
            Results result = executeSubCraft(gui, recipe, req.count, req.subIngredientZones);
            if (result.IsSuccess()) {
                subCraftedItems.add(req.itemName);
            } else {
                gui.msg("Sub-craft failed: " + req.itemName + " - " + result.toString());
            }
        }

        if (!subCraftedItems.isEmpty()) {
            reopenMainRecipe(gui, mainRecipeResource);
        }
    }

    private static class SubCraftRequest {
        final String itemName;
        int count;
        final Set<RecipeIngredientCache.RecipeEntry> recipes;
        final Map<String, NArea> subIngredientZones;

        SubCraftRequest(String itemName, int count, Set<RecipeIngredientCache.RecipeEntry> recipes,
                        Map<String, NArea> subIngredientZones) {
            this.itemName = itemName;
            this.count = count;
            this.recipes = recipes;
            this.subIngredientZones = subIngredientZones != null ? new HashMap<>(subIngredientZones) : new HashMap<>();
        }
    }

    private String getEffectiveItemName(NMakewindow.Spec s) {
        if (s.useCategory && s.categories && s.name != null &&
                VSpec.categories.containsKey(s.name)) {
            return s.name;
        } else if (!s.categories) {
            return s.name;
        } else if (s.ing != null) {
            return s.ing.name;
        }
        return s.name;
    }

    private Results executeSubCraft(NGameUI gui, RecipeIngredientCache.RecipeEntry recipe,
                                     int quantity, Map<String, NArea> subIngredientZones) throws InterruptedException {
        Indir<Resource> res = Resource.remote().load(recipe.paginaResource);
        MenuGrid.Pagina pag = gui.menu.paginafor(res);
        if (pag == null) {
            return Results.ERROR("Sub-recipe not found in menu: " + recipe.paginaResource);
        }

        final NMakewindow oldMwnd = (gui.craftwnd != null) ? gui.craftwnd.makeWidget : null;
        gui.menu.use(pag.button(), new MenuGrid.Interaction(), false);

        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                return gui.craftwnd != null && gui.craftwnd.makeWidget != null
                        && gui.craftwnd.makeWidget != oldMwnd;
            }
        });

        NMakewindow subMwnd = gui.craftwnd.makeWidget;
        subMwnd.autoMode = true;
        if (subMwnd.noTransfer != null) {
            subMwnd.noTransfer.a = true;
        }

        final boolean isHeadless = nurgling.headless.Headless.isHeadless();
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                if (subMwnd.inputs == null || subMwnd.inputs.isEmpty()) return false;
                for (NMakewindow.Spec spec : subMwnd.inputs) {
                    if (spec.name == null) return false;
                    if (!isHeadless && spec.spr == null) return false;
                }
                if (subMwnd.outputs == null || subMwnd.outputs.isEmpty()) return false;
                for (NMakewindow.Spec spec : subMwnd.outputs) {
                    if (spec.name == null) return false;
                }
                return true;
            }
        });

        // Auto-enable useCategory for all category inputs in sub-crafts
        // and apply zone selections from parent's sub-ingredient dropdowns
        for (NMakewindow.Spec spec : subMwnd.inputs) {
            if (spec.categories) {
                spec.useCategory = true;
            }
            String specName = spec.name;
            if (specName != null && subIngredientZones != null && subIngredientZones.containsKey(specName)) {
                spec.selectedZone = subIngredientZones.get(specName);
            }
            // Also check category name for zone match
            if (spec.categories && specName != null) {
                for (Map.Entry<String, NArea> entry : subIngredientZones.entrySet()) {
                    if (specName.equals(entry.getKey())) {
                        spec.selectedZone = entry.getValue();
                        break;
                    }
                }
            }
        }

        Craft subCraft = new Craft(subMwnd, quantity);
        subCraft.subCraftDepth = this.subCraftDepth + 1;
        return subCraft.run(gui);
    }

    private static class SpecSettings {
        NArea selectedZone;
        boolean isSubCraft;
        boolean isLocalZone;
        boolean isInventory;
        boolean useCategory;
        Map<String, NArea> subIngredientZones;
    }

    private Map<String, SpecSettings> saveSpecSettings(List<NMakewindow.Spec> specs) {
        Map<String, SpecSettings> map = new LinkedHashMap<>();
        for (NMakewindow.Spec s : specs) {
            if (s.name == null) continue;
            SpecSettings ss = new SpecSettings();
            ss.selectedZone = s.selectedZone;
            ss.isSubCraft = s.isSubCraft;
            ss.isLocalZone = s.isLocalZone;
            ss.isInventory = s.isInventory;
            ss.useCategory = s.useCategory;
            ss.subIngredientZones = s.subIngredientZones != null
                    ? new HashMap<>(s.subIngredientZones) : new HashMap<>();
            map.put(s.name, ss);
        }
        return map;
    }

    private void restoreSpecSettings(List<NMakewindow.Spec> specs, Map<String, SpecSettings> saved) {
        for (NMakewindow.Spec s : specs) {
            if (s.name == null) continue;
            SpecSettings ss = saved.get(s.name);
            if (ss == null) continue;
            s.selectedZone = ss.selectedZone;
            s.isSubCraft = ss.isSubCraft;
            s.isLocalZone = ss.isLocalZone;
            s.isInventory = ss.isInventory;
            s.useCategory = ss.useCategory;
            s.subIngredientZones = ss.subIngredientZones;
        }
    }

    private void reopenMainRecipe(NGameUI gui, String recipeResource) throws InterruptedException {
        // Save settings from current mwnd before reopening
        Map<String, SpecSettings> savedInputs = saveSpecSettings(mwnd.inputs);
        Map<String, SpecSettings> savedOutputs = saveSpecSettings(mwnd.outputs);

        Indir<Resource> res = Resource.remote().load(recipeResource);
        MenuGrid.Pagina pag = gui.menu.paginafor(res);
        if (pag == null) return;

        final NMakewindow oldMwnd = (gui.craftwnd != null) ? gui.craftwnd.makeWidget : null;
        gui.menu.use(pag.button(), new MenuGrid.Interaction(), false);

        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                return gui.craftwnd != null && gui.craftwnd.makeWidget != null
                        && gui.craftwnd.makeWidget != oldMwnd;
            }
        });

        mwnd = gui.craftwnd.makeWidget;
        mwnd.autoMode = true;

        final boolean isHeadless = nurgling.headless.Headless.isHeadless();
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                if (mwnd.inputs == null || mwnd.inputs.isEmpty()) return false;
                for (NMakewindow.Spec spec : mwnd.inputs) {
                    if (spec.name == null) return false;
                    if (!isHeadless && spec.spr == null) return false;
                }
                if (mwnd.outputs == null || mwnd.outputs.isEmpty()) return false;
                for (NMakewindow.Spec spec : mwnd.outputs) {
                    if (spec.name == null) return false;
                }
                return true;
            }
        });

        // Restore all settings to the new specs
        restoreSpecSettings(mwnd.inputs, savedInputs);
        restoreSpecSettings(mwnd.outputs, savedOutputs);
    }

    /**
     * Shows a dialog for the user to select which recipe to use for sub-crafting.
     * Blocks until the user makes a selection or cancels.
     */
    private RecipeIngredientCache.RecipeEntry selectSubRecipeViaDialog(
            NGameUI gui, String itemName, Set<RecipeIngredientCache.RecipeEntry> recipes)
            throws InterruptedException {
        List<MenuGrid.Pagina> paginae = new ArrayList<>();
        Map<String, RecipeIngredientCache.RecipeEntry> resourceToEntry = new HashMap<>();

        for (RecipeIngredientCache.RecipeEntry entry : recipes) {
            MenuGrid.Pagina pag = gui.menu.paginafor(Resource.remote().load(entry.paginaResource));
            if (pag != null) {
                paginae.add(pag);
                try {
                    resourceToEntry.put(pag.res().name, entry);
                } catch (Loading e) {
                    resourceToEntry.put(entry.paginaResource, entry);
                }
            }
        }

        if (paginae.isEmpty()) return null;
        if (paginae.size() == 1) {
            try {
                return resourceToEntry.get(paginae.get(0).res().name);
            } catch (Loading e) {
                return recipes.iterator().next();
            }
        }

        final RecipeIngredientCache.RecipeEntry[] selected = {null};
        final boolean[] done = {false};

        NUtils.addCentered(new SubRecipeSelectWindow(itemName, paginae, pag -> {
            try {
                selected[0] = resourceToEntry.get(pag.res().name);
            } catch (Loading e) {
                // fallback
            }
            done[0] = true;
        }, () -> done[0] = true));

        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                return done[0];
            }
        });

        return selected[0];
    }

    /**
     * Refresh the mwnd reference from the current craft window.
     * This is needed after operations that may change the craft widget (like LightFire).
     */
    private void refreshMakeWidget(NGameUI gui) {
        if(preserveConsumables)
            return; // A replacement is never adopted by the one-off caller.
        if (gui.craftwnd != null && gui.craftwnd.makeWidget != null) {
            mwnd = gui.craftwnd.makeWidget;
        }
    }

    /**
     * Calculate the number of slots needed for a given number of crafts, considering stacking.
     * @param ncontext The crafting context
     * @param numCrafts Number of crafts to calculate for
     * @return Total number of inventory slots needed
     */
    private int calculateSlotsNeeded(NContext ncontext, int numCrafts) {
        int totalSlots = 0;
        
        // Calculate slots for inputs
        for (NMakewindow.Spec s : mwnd.inputs) {
            // Skip ignored optional ingredients
            if (s.ing != null && s.ing.isIgnored) {
                continue;
            }
            
            String itemName = s.ing != null ? s.ing.name : s.name;
            if(confirmedPreloaded.containsKey(itemName))
                continue;
            
            // Skip items stored in barrels
            if (ncontext.isInBarrel(itemName)) {
                continue;
            }
            if (isWaterSpec(s)) {
                continue;
            }
            if (isMeasuredSpec(s)) {
                int stackSize = Math.max(1, StackSupporter.getFullStackSize(ItemVariants.base(itemName)));
                totalSlots += MeasuredCraft.slotsFor((long) s.count * numCrafts, stackSize);
                continue;
            }
            
            int itemsNeeded = s.count * numCrafts;
            int stackSize = StackSupporter.getFullStackSize(ItemVariants.base(itemName));
            
            // Calculate slots needed: ceil(itemsNeeded / stackSize)
            int slotsForItem = (itemsNeeded + stackSize - 1) / stackSize;
            totalSlots += slotsForItem;
        }
        
        // Calculate slots for outputs (if noTransfer is not enabled)
        if (!mwnd.noTransfer.a) {
            for (NMakewindow.Spec s : mwnd.outputs) {
                String itemName = s.ing != null ? s.ing.name : s.name;
                
                // Skip items stored in barrels
                if (ncontext.isInBarrel(itemName)) {
                    continue;
                }
                
                int outputMultiplier = NContext.getOutputMultiplier(itemName);
                int itemsProduced = s.count * numCrafts * outputMultiplier;
                int stackSize = StackSupporter.getFullStackSize(itemName);
                
                // Calculate slots needed: ceil(itemsProduced / stackSize)
                int slotsForItem = (itemsProduced + stackSize - 1) / stackSize;
                totalSlots += slotsForItem;
            }
        }
        
        return totalSlots;
    }

    /**
     * Calculate the maximum number of crafts that can fit in the inventory, considering stacking.
     * Uses binary search to find the optimal number.
     * @param ncontext The crafting context
     * @param freeSpace Available inventory slots
     * @param maxCrafts Maximum number of crafts desired
     * @return Maximum number of crafts that can fit
     */
    private int calculateMaxCraftsWithStacking(NContext ncontext, int freeSpace, int maxCrafts) {
        if (freeSpace <= 0) {
            return 0;
        }
        
        // Binary search for the maximum number of crafts
        int low = 0;
        int high = maxCrafts;
        int result = 0;
        
        while (low <= high) {
            int mid = (low + high) / 2;
            int slotsNeeded = calculateSlotsNeeded(ncontext, mid);
            
            if (slotsNeeded <= freeSpace) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        
        return result;
    }

    private static boolean isWaterSpec(NMakewindow.Spec s) {
        if (MeasuredCraft.isWater(s.name))
            return true;
        if (s.ing != null && MeasuredCraft.isWater(s.ing.name))
            return true;
        return MeasuredCraft.isWater(ingredientKey(s));
    }

    /** Weight or volume on the slot (flour, water, honey) is hundredths, not a stack count. */
    private static boolean isMeasuredSpec(NMakewindow.Spec s) {
        try {
            for (ItemInfo inf : s.info()) {
                if (inf instanceof CustomName && ((CustomName) inf).count > 0)
                    return true;
            }
        } catch (Exception ignored) {
        }
        return MeasuredCraft.pricedByWeight(ingredientKey(s));
    }

    private static String ingredientKey(NMakewindow.Spec s) {
        if (s.useCategory && s.categories && s.name != null && VSpec.categories.containsKey(s.name))
            return s.name;
        if (s.ing != null)
            return s.ing.name;
        return s.name;
    }

    private int heldHundredths(String item) throws InterruptedException {
        int have = 0;
        NInventory inv = NUtils.getGameUI().getInventory();
        if (VSpec.categories.containsKey(item)) {
            ArrayList<org.json.JSONObject> members = VSpec.categories.get(item);
            if (members != null) {
                for (org.json.JSONObject member : members) {
                    String memberName = member.optString("name");
                    if (memberName != null) {
                        for (WItem wi : inv.getItems(new NAlias(memberName)))
                            have += getActualItemCount(wi);
                    }
                }
            }
        } else {
            for (WItem wi : inv.getItems(new NAlias(ItemVariants.base(item))))
                if (!ItemVariants.isKey(item) || ItemVariants.matches((NGItem) wi.item, item))
                    have += getActualItemCount(wi);
        }
        return have;
    }

    private Results pullMeasuredFromStorage(NContext ncontext, NGameUI gui, String item, int need) throws InterruptedException {
        int guard = 0;
        while (heldHundredths(item) < need) {
            if (++guard > 48)
                return Results.ERROR("Not enough " + item);
            int before = heldHundredths(item);
            Results took = new TakeItems2(ncontext, item, 1).run(gui);
            if (!took.IsSuccess() || heldHundredths(item) <= before)
                return Results.ERROR("Failed to take items: " + item);
        }
        return Results.SUCCESS();
    }

    /** Barrel of water from the ingredient zone, or a barrel in the water area. */
    private boolean attachWaterBarrel(NContext ncontext, String name) {
        if (name == null || name.isEmpty())
            name = "Water";
        NArea ingredient = NContext.findIn(name);
        if (ingredient == null)
            ingredient = NContext.findInGlobal(name);
        if (ingredient != null) {
            NArea.Ingredient input = ingredient.getInput(name);
            if (input != null && input.type == NArea.Ingredient.Type.BARREL) {
                ncontext.useBarrelArea(name, ingredient);
                return true;
            }
        }
        NArea source = NContext.findSpec("water");
        if (source == null)
            source = NContext.findSpecGlobal("water");
        if (source == null)
            return false;
        ncontext.useBarrelArea(name, source);
        return true;
    }

}
