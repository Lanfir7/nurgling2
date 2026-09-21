package nurgling.craftatlas.quality;

import nurgling.tools.MiningQuality;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure state and formula model for the Clay Quality Workshop.
 * Potters' wheels use Ball Clay as their fixed first-version clay material.
 */
public final class QualityWorkshopModel {
    public enum Key {
        SOAP_CLAY, BONE_CLAY, POTTER_CLAY, COADE_CLAY, BRICK, BONE_ASH, LYE, ASH, POTTERS_WHEEL,
        BONES, FELDSPAR, PIT_CLAY, CAVE_CLAY, BALL_CLAY, ACRE_CLAY, GRAY_CLAY, FLINT, QUARTZ,
        RAW_GLASS, FUEL, WATER, SALT_WATER, CAULDRON, KILN, DEXTERITY, INTELLIGENCE, MASONRY,
        BOARD, BLOCK, STONE, ROPE,
        ORE_SMELTER, STACK_FURNACE, SMELTED_METAL, STONE_AXE, MINED_STONE, MINED_ORE,
        BRANCH, SURVIVAL, ORE, COAL, HARD_METAL, LEATHER, STONE_WALL, ORE_WALL, KILN_CLAY,
        ANVIL, CASTING_MATERIAL, SAND, BAD_SAND
    }

    public static final class Iteration {
        public final int number;
        public final double brickQuality;
        public final double clayQuality;

        public Iteration(int number, double brickQuality, double clayQuality) {
            this.number = number;
            this.brickQuality = brickQuality;
            this.clayQuality = clayQuality;
        }
    }

    public static final class MiningIteration {
        public final int number;
        public final double inputStoneQuality;
        public final double axeQuality;
        public final double stoneQuality;

        public MiningIteration(int number, double inputStoneQuality, double axeQuality, double stoneQuality) {
            this.number = number;
            this.inputStoneQuality = inputStoneQuality;
            this.axeQuality = axeQuality;
            this.stoneQuality = stoneQuality;
        }
    }

    private static final List<Key> RECIPES = Collections.unmodifiableList(Arrays.asList(
            Key.SOAP_CLAY, Key.BONE_CLAY, Key.POTTER_CLAY, Key.COADE_CLAY, Key.BRICK,
            Key.BONE_ASH, Key.LYE, Key.ASH, Key.POTTERS_WHEEL, Key.KILN, Key.ORE_SMELTER, Key.STACK_FURNACE,
            Key.SMELTED_METAL, Key.ANVIL, Key.STONE_AXE, Key.MINED_STONE, Key.MINED_ORE));
    private static final Set<Key> RECIPE_SET = Collections.unmodifiableSet(EnumSet.copyOf(RECIPES));
    private static final double DEFAULT_QUALITY = 10.0;
    private static final double MIN_QUALITY = 1.0;
    private static final double MAX_QUALITY = 100000.0;

    private final Set<Key> watched = new LinkedHashSet<>();
    private final Map<Key, Double> manual = new EnumMap<>(Key.class);
    private final Map<Key, Double> baseline = new EnumMap<>(Key.class);
    private final Map<Key, Double> desiredAmounts = new EnumMap<>(Key.class);
    private final Set<Key> hiddenInputs = EnumSet.noneOf(Key.class);
    private final Set<Key> hiddenResults = EnumSet.noneOf(Key.class);
    private Key boneClaySource = Key.PIT_CLAY;
    private Key brickClaySource = Key.BALL_CLAY;
    private Key smeltingFurnace = Key.ORE_SMELTER;
    private Key smeltingOreSource = Key.ORE;
    private Key stackClaySource = Key.BALL_CLAY;
    private Key anvilCastingSource = Key.CASTING_MATERIAL;
    private Key anvilFurnace = Key.ORE_SMELTER;
    private boolean clayCauldron;
    private boolean hiddenInputsExpanded;
    private boolean hiddenResultsExpanded;
    private int generations = 1;
    private int miningGenerations = 1;
    private int potterOutputPerCraft;

    public QualityWorkshopModel() {
        for(Key key : Key.values()) manual.put(key, DEFAULT_QUALITY);
        watched.add(Key.SOAP_CLAY);
        watched.add(Key.BONE_CLAY);
        watched.add(Key.POTTER_CLAY);
        watched.add(Key.COADE_CLAY);
    }

    public static List<Key> recipes() { return RECIPES; }

    public List<Key> watched() {
        return Collections.unmodifiableList(new ArrayList<>(watched));
    }

    public boolean inputHidden(Key key) { return key != null && hiddenInputs.contains(key); }

    public void setInputHidden(Key key, boolean hidden) {
        if(key == null) return;
        if(hidden) hiddenInputs.add(key);
        else hiddenInputs.remove(key);
    }

    public boolean resultHidden(Key key) { return isRecipe(key) && hiddenResults.contains(key); }

    public void setResultHidden(Key key, boolean hidden) {
        if(!isRecipe(key)) return;
        if(hidden) hiddenResults.add(key);
        else hiddenResults.remove(key);
    }

    public boolean hiddenInputsExpanded() { return hiddenInputsExpanded; }
    public void setHiddenInputsExpanded(boolean expanded) { hiddenInputsExpanded = expanded; }
    public boolean hiddenResultsExpanded() { return hiddenResultsExpanded; }
    public void setHiddenResultsExpanded(boolean expanded) { hiddenResultsExpanded = expanded; }

    /** Unresolved inputs, mapped to the recipes that consume each one directly. */
    public Map<Key, List<Key>> inputs() {
        Map<Key, LinkedHashSet<Key>> result = new LinkedHashMap<>();
        for(Key root : watched) collectInputs(root, result, EnumSet.noneOf(Key.class));
        if(generations > 1 && watched.contains(Key.POTTER_CLAY)) {
            addInput(result, Key.FUEL, Key.BRICK);
            if(watched.contains(Key.KILN)) collectInputs(Key.KILN, result, EnumSet.noneOf(Key.class));
            else addInput(result, Key.KILN, Key.BRICK);
        }
        if(miningGenerations > 1 && watched.contains(Key.MINED_STONE)) {
            addInput(result, Key.BRANCH, Key.STONE_AXE);
            addInput(result, Key.INTELLIGENCE, Key.STONE_AXE);
            addInput(result, Key.SURVIVAL, Key.STONE_AXE);
        }
        Map<Key, List<Key>> copy = new LinkedHashMap<>();
        for(Map.Entry<Key, LinkedHashSet<Key>> entry : result.entrySet())
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        return Collections.unmodifiableMap(copy);
    }

    private void collectInputs(Key recipe, Map<Key, LinkedHashSet<Key>> result, Set<Key> visiting) {
        if(!isRecipe(recipe) || !watched.contains(recipe) || !visiting.add(recipe)) return;
        for(Key dependency : dependencies(recipe)) {
            if(isRecipe(dependency) && watched.contains(dependency)) collectInputs(dependency, result, visiting);
            else addInput(result, dependency, recipe);
        }
        visiting.remove(recipe);
    }

    private static void addInput(Map<Key, LinkedHashSet<Key>> inputs, Key key, Key consumer) {
        LinkedHashSet<Key> consumers = inputs.get(key);
        if(consumers == null) {
            consumers = new LinkedHashSet<>();
            inputs.put(key, consumers);
        }
        consumers.add(consumer);
    }

    public boolean isRecipe(Key key) { return key != null && RECIPE_SET.contains(key); }

    public void watch(Key key) {
        if(isRecipe(key) && !wouldCycle(key)) watched.add(key);
    }

    /** Stops calculating this recipe and preserves its first-generation result as a manual input. */
    public void unwatch(Key key) {
        if(!isRecipe(key) || !watched.contains(key)) return;
        double frozen = baseValue(key);
        watched.remove(key);
        manual.put(key, frozen);
    }

    public double value(Key key) {
        if(key == Key.POTTER_CLAY && watched.contains(Key.POTTER_CLAY)) {
            List<Iteration> values = iterations();
            return values.get(values.size() - 1).clayQuality;
        }
        if(key == Key.MINED_STONE && watched.contains(Key.MINED_STONE)) {
            List<MiningIteration> values = miningIterations();
            return values.get(values.size() - 1).stoneQuality;
        }
        return baseValue(key);
    }

    /** Computes recipe dependencies at their first generation. */
    public double baseValue(Key key) {
        if(key == null) return DEFAULT_QUALITY;
        return resolve(key, EnumSet.noneOf(Key.class));
    }

    private double resolve(Key key, Set<Key> visiting) {
        if(!isRecipe(key) || !watched.contains(key)) return manual(key);
        if(!visiting.add(key)) return manual(key);
        double result;
        switch(key) {
            case BONE_ASH:
                result = (2 * resolve(Key.BONES, visiting) + resolve(Key.FUEL, visiting) + resolve(Key.KILN, visiting)) / 4.0;
                break;
            case ASH:
                result = resolve(Key.BONE_ASH, visiting);
                break;
            case LYE:
                result = (2 * resolve(Key.ASH, visiting) + resolve(Key.CAULDRON, visiting) + resolve(Key.WATER, visiting)) / 4.0;
                break;
            case SOAP_CLAY:
                result = (2 * (resolve(Key.LYE, visiting) + resolve(Key.SALT_WATER, visiting) + resolve(Key.CAVE_CLAY, visiting))
                        + resolve(Key.CAULDRON, visiting) + resolve(Key.WATER, visiting) / (clayCauldron ? 2.0 : 1.0)) / 8.0;
                result = softcap(result, cap(Key.DEXTERITY, Key.MASONRY, visiting));
                break;
            case BONE_CLAY:
                result = (resolve(Key.BONE_ASH, visiting) + resolve(Key.FELDSPAR, visiting)
                        + resolve(boneClaySource, visiting)) / 3.0;
                result = softcap(result, cap(Key.INTELLIGENCE, Key.MASONRY, visiting));
                break;
            case BRICK:
                result = brick(resolve(brickClaySource, visiting), resolve(Key.FUEL, visiting), resolve(Key.KILN, visiting));
                break;
            case POTTERS_WHEEL:
                result = (resolve(Key.BOARD, visiting) + resolve(Key.BLOCK, visiting) + resolve(Key.STONE, visiting)
                        + resolve(Key.BALL_CLAY, visiting) + resolve(Key.ROPE, visiting)) / 5.0;
                break;
            case POTTER_CLAY:
                result = potter(resolve(Key.BONE_CLAY, visiting), resolve(Key.ACRE_CLAY, visiting), resolve(Key.GRAY_CLAY, visiting),
                        resolve(Key.BRICK, visiting), resolve(Key.POTTERS_WHEEL, visiting), visiting);
                break;
            case COADE_CLAY:
                result = (resolve(Key.BALL_CLAY, visiting) + resolve(Key.BRICK, visiting) + resolve(Key.FLINT, visiting)
                        + resolve(Key.QUARTZ, visiting) + resolve(Key.RAW_GLASS, visiting)) / 5.0;
                result = softcap(result, cap(Key.DEXTERITY, Key.MASONRY, visiting));
                break;
            case ORE_SMELTER:
                result = average(resolve(Key.BRICK, visiting), resolve(Key.STONE, visiting), resolve(Key.HARD_METAL, visiting));
                break;
            case STACK_FURNACE:
                result = average(resolve(stackClaySource, visiting), resolve(Key.STONE, visiting), resolve(Key.BOARD, visiting),
                        resolve(Key.BLOCK, visiting), resolve(Key.LEATHER, visiting));
                break;
            case SMELTED_METAL:
                result = (2 * resolve(smeltingOreSource, visiting) + resolve(smeltingFurnace, visiting)
                        + resolve(smeltingFuel(), visiting)) / 4.0;
                break;
            case STONE_AXE:
                result = axe(resolve(Key.STONE, visiting), resolve(Key.BRANCH, visiting), visiting);
                break;
            case MINED_STONE:
                result = mined(resolve(Key.STONE_WALL, visiting), resolve(Key.STONE_AXE, visiting), resolve(Key.MASONRY, visiting));
                break;
            case MINED_ORE:
                result = mined(resolve(Key.ORE_WALL, visiting), resolve(Key.STONE_AXE, visiting), resolve(Key.MASONRY, visiting));
                break;
            case KILN:
                result = resolve(Key.KILN_CLAY, visiting);
                break;
            case ANVIL:
                result = (3 * resolve(Key.HARD_METAL, visiting) + resolve(anvilCastingSource, visiting)) / 4.0;
                break;
            default:
                result = manual(key);
        }
        visiting.remove(key);
        return result;
    }

    private double potter(double bone, double acre, double gray, double brick, double wheel, Set<Key> visiting) {
        double result = (3 * (bone + acre + gray + brick) / 4.0 + wheel) / 4.0;
        return softcap(result, cap(Key.DEXTERITY, Key.MASONRY, visiting));
    }

    private static double brick(double clay, double fuel, double kiln) {
        return (2 * clay + fuel + kiln) / 4.0;
    }

    private double axe(double stone, double branch, Set<Key> visiting) {
        return softcap((stone + branch) / 2.0, cap(Key.INTELLIGENCE, Key.SURVIVAL, visiting));
    }

    private static double mined(double wall, double tool, double masonry) {
        return MiningQuality.fromWall(Math.min(wall, masonry), tool, 0.8);
    }

    private static double average(double... qualities) {
        double total = 0;
        for(double quality : qualities) total += quality;
        return total / qualities.length;
    }

    private double cap(Key first, Key second, Set<Key> visiting) {
        return Math.sqrt(resolve(first, visiting) * resolve(second, visiting));
    }

    private static double softcap(double quality, double cap) {
        return quality > cap ? (quality + cap) / 2.0 : quality;
    }

    public List<Key> dependencies(Key key) {
        if(key == null) return Collections.emptyList();
        switch(key) {
            case BONE_ASH: return list(Key.BONES, Key.FUEL, Key.KILN);
            case ASH: return list(Key.BONE_ASH);
            case LYE: return list(Key.ASH, Key.CAULDRON, Key.WATER);
            case SOAP_CLAY: return list(Key.LYE, Key.SALT_WATER, Key.CAVE_CLAY, Key.CAULDRON, Key.WATER, Key.DEXTERITY, Key.MASONRY);
            case BONE_CLAY: return list(Key.BONE_ASH, Key.FELDSPAR, boneClaySource, Key.INTELLIGENCE, Key.MASONRY);
            case BRICK: return list(brickClaySource, Key.FUEL, Key.KILN);
            case POTTERS_WHEEL: return list(Key.BOARD, Key.BLOCK, Key.STONE, Key.BALL_CLAY, Key.ROPE);
            case POTTER_CLAY: return list(Key.BONE_CLAY, Key.ACRE_CLAY, Key.GRAY_CLAY, Key.BRICK, Key.POTTERS_WHEEL, Key.DEXTERITY, Key.MASONRY);
            case COADE_CLAY: return list(Key.BALL_CLAY, Key.BRICK, Key.FLINT, Key.QUARTZ, Key.RAW_GLASS, Key.DEXTERITY, Key.MASONRY);
            case ORE_SMELTER: return list(Key.BRICK, Key.STONE, Key.HARD_METAL);
            case STACK_FURNACE: return list(stackClaySource, Key.STONE, Key.BOARD, Key.BLOCK, Key.LEATHER);
            case SMELTED_METAL: return list(smeltingOreSource, smeltingFurnace, smeltingFuel());
            case STONE_AXE: return list(Key.STONE, Key.BRANCH, Key.INTELLIGENCE, Key.SURVIVAL);
            case MINED_STONE: return list(Key.STONE_AXE, Key.STONE_WALL, Key.MASONRY);
            case MINED_ORE: return list(Key.STONE_AXE, Key.ORE_WALL, Key.MASONRY);
            case KILN: return list(Key.KILN_CLAY);
            case ANVIL: return list(Key.HARD_METAL, anvilCastingSource);
            default: return Collections.emptyList();
        }
    }

    private Key smeltingFuel() { return smeltingFurnace == Key.STACK_FURNACE ? Key.FUEL : Key.COAL; }

    private static List<Key> list(Key... keys) {
        return Collections.unmodifiableList(Arrays.asList(keys));
    }

    public double manual(Key key) {
        Double value = manual.get(key);
        return value == null ? DEFAULT_QUALITY : value;
    }

    public boolean setManual(Key key, double quality) {
        if(key == null || !Double.isFinite(quality) || quality < MIN_QUALITY || quality > MAX_QUALITY) return false;
        manual.put(key, quality);
        return true;
    }

    public List<Iteration> iterations() {
        List<Iteration> result = new ArrayList<>();
        double brick = baseValue(Key.BRICK);
        double bone = baseValue(Key.BONE_CLAY);
        double acre = baseValue(Key.ACRE_CLAY);
        double gray = baseValue(Key.GRAY_CLAY);
        double wheel = baseValue(Key.POTTERS_WHEEL);
        double fuel = baseValue(Key.FUEL);
        double kiln = baseValue(Key.KILN);
        for(int number = 1; number <= generations; number++) {
            if(number > 1) brick = brick(result.get(result.size() - 1).clayQuality, fuel, kiln);
            double clay = potter(bone, acre, gray, brick, wheel, EnumSet.noneOf(Key.class));
            result.add(new Iteration(number, brick, clay));
        }
        return Collections.unmodifiableList(result);
    }

    public int generations() { return generations; }
    public void setGenerations(int generations) {
        if(generations >= 1 && generations <= 8) this.generations = generations;
    }

    public int miningGenerations() { return miningGenerations; }
    public void setMiningGenerations(int miningGenerations) {
        if(miningGenerations >= 1 && miningGenerations <= 8) this.miningGenerations = miningGenerations;
    }

    /** Output shown in the player's recipe; zero means not yet supplied. */
    public int potterOutputPerCraft() { return potterOutputPerCraft; }
    public boolean setPotterOutputPerCraft(int output) {
        if(output < 0 || output > 1000) return false;
        potterOutputPerCraft = output;
        return true;
    }

    public List<MiningIteration> miningIterations() {
        double firstStone = baseValue(Key.STONE);
        double firstAxe = baseValue(Key.STONE_AXE);
        if(!watched.contains(Key.MINED_STONE))
            return Collections.singletonList(new MiningIteration(1, firstStone, firstAxe, baseValue(Key.MINED_STONE)));
        List<MiningIteration> result = new ArrayList<>();
        double inputStone = firstStone;
        double axe = firstAxe;
        double wall = baseValue(Key.STONE_WALL);
        double masonry = baseValue(Key.MASONRY);
        double branch = baseValue(Key.BRANCH);
        for(int number = 1; number <= miningGenerations; number++) {
            if(number > 1) axe = axe(inputStone, branch, EnumSet.noneOf(Key.class));
            double stone = mined(wall, axe, masonry);
            result.add(new MiningIteration(number, inputStone, axe, stone));
            inputStone = stone;
        }
        return Collections.unmodifiableList(result);
    }

    public Key boneClaySource() { return boneClaySource; }
    public void setBoneClaySource(Key source) {
        if(source == Key.PIT_CLAY || source == Key.SOAP_CLAY) {
            boneClaySource = source;
            if(isRecipe(source)) watch(source);
        }
    }

    public Key brickClaySource() { return brickClaySource; }
    public void setBrickClaySource(Key source) {
        if(source == Key.BALL_CLAY || source == Key.BONE_CLAY || source == Key.SOAP_CLAY) {
            brickClaySource = source;
            if(isRecipe(source)) watch(source);
        }
    }

    public Key smeltingFurnace() { return smeltingFurnace; }
    public void setSmeltingFurnace(Key furnace) {
        if(furnace == Key.ORE_SMELTER || furnace == Key.STACK_FURNACE) smeltingFurnace = furnace;
    }

    public Key smeltingOreSource() { return smeltingOreSource; }
    public void setSmeltingOreSource(Key source) {
        if(source == Key.ORE || source == Key.MINED_ORE) {
            smeltingOreSource = source;
            if(source == Key.MINED_ORE) watch(source);
        }
    }

    public Key stackClaySource() { return stackClaySource; }
    public void setStackClaySource(Key source) {
        if(validStackClaySource(source)) {
            stackClaySource = source;
            if(isRecipe(source)) watch(source);
        }
    }

    public boolean clayCauldron() { return clayCauldron; }
    public void setClayCauldron(boolean clayCauldron) { this.clayCauldron = clayCauldron; }

    public Key anvilCastingSource() { return anvilCastingSource; }
    public void setAnvilCastingSource(Key source) {
        if(validCastingSource(source)) {
            anvilCastingSource = source;
            if(isRecipe(source)) watch(source);
        }
    }

    public Key anvilFurnace() { return anvilFurnace; }
    public void setAnvilFurnace(Key furnace) {
        if(furnace == Key.ORE_SMELTER || furnace == Key.STACK_FURNACE) anvilFurnace = furnace;
    }

    /** Extra finished output to keep, in pieces (ash and lye: kg). Zero only calculates quality. */
    public double desiredAmount(Key key) { return desiredAmounts.getOrDefault(key, 0.0); }
    public boolean setDesiredAmount(Key key, double amount) {
        if(!isRecipe(key) || !Double.isFinite(amount) || amount < 0 || amount > 1000000
                || (key != Key.LYE && key != Key.ASH && amount != Math.rint(amount))) return false;
        if(amount == 0) desiredAmounts.remove(key);
        else desiredAmounts.put(key, amount);
        return true;
    }

    public void snapshot() {
        baseline.clear();
        for(Key key : watched) baseline.put(key, value(key));
    }

    public Double baseline(Key key) { return baseline.get(key); }

    public JSONObject toJson() {
        JSONObject result = new JSONObject();
        JSONArray roots = new JSONArray();
        for(Key key : watched) roots.put(key.name());
        JSONObject qualities = new JSONObject();
        for(Key key : Key.values()) qualities.put(key.name(), manual(key));
        JSONObject savedBaseline = new JSONObject();
        for(Map.Entry<Key, Double> entry : baseline.entrySet()) savedBaseline.put(entry.getKey().name(), entry.getValue());
        JSONObject savedAmounts = new JSONObject();
        for(Map.Entry<Key, Double> entry : desiredAmounts.entrySet()) savedAmounts.put(entry.getKey().name(), entry.getValue());
        JSONArray savedHiddenInputs = keysJson(hiddenInputs);
        JSONArray savedHiddenResults = keysJson(hiddenResults);
        return result.put("watched", roots).put("manual", qualities)
                .put("boneClaySource", boneClaySource.name()).put("brickClaySource", brickClaySource.name())
                .put("smeltingFurnace", smeltingFurnace.name()).put("smeltingOreSource", smeltingOreSource.name())
                .put("stackClaySource", stackClaySource.name()).put("clayCauldron", clayCauldron)
                .put("anvilCastingSource", anvilCastingSource.name()).put("anvilFurnace", anvilFurnace.name())
                .put("desiredAmounts", savedAmounts)
                .put("generations", generations).put("miningGenerations", miningGenerations).put("baseline", savedBaseline)
                .put("potterOutputPerCraft", potterOutputPerCraft)
                .put("hiddenInputs", savedHiddenInputs).put("hiddenResults", savedHiddenResults)
                .put("hiddenInputsExpanded", hiddenInputsExpanded).put("hiddenResultsExpanded", hiddenResultsExpanded);
    }

    public static QualityWorkshopModel fromJson(JSONObject json) {
        QualityWorkshopModel result = new QualityWorkshopModel();
        if(json == null) return result;
        try {
            JSONArray roots = json.optJSONArray("watched");
            if(roots != null) {
                result.watched.clear();
                for(int i = 0; i < roots.length(); i++) {
                    Key key = key(roots.optString(i, null));
                    if(result.isRecipe(key)) result.watched.add(key);
                }
            }
            JSONObject qualities = json.optJSONObject("manual");
            if(qualities != null) for(String name : qualities.keySet()) {
                Key key = key(name);
                if(key == null) continue;
                double quality = qualities.optDouble(name, Double.NaN);
                result.setManual(key, quality);
            }
            Key boneSource = key(json.optString("boneClaySource", null));
            if(boneSource == Key.PIT_CLAY || boneSource == Key.SOAP_CLAY) result.boneClaySource = boneSource;
            Key brickSource = key(json.optString("brickClaySource", null));
            if(brickSource == Key.BALL_CLAY || brickSource == Key.BONE_CLAY || brickSource == Key.SOAP_CLAY)
                result.brickClaySource = brickSource;
            Key furnace = key(json.optString("smeltingFurnace", null));
            if(furnace == Key.ORE_SMELTER || furnace == Key.STACK_FURNACE) result.smeltingFurnace = furnace;
            Key oreSource = key(json.optString("smeltingOreSource", null));
            if(oreSource == Key.ORE || oreSource == Key.MINED_ORE) result.smeltingOreSource = oreSource;
            Key stackSource = key(json.optString("stackClaySource", null));
            if(validStackClaySource(stackSource)) result.stackClaySource = stackSource;
            Key castingSource = key(json.optString("anvilCastingSource", null));
            if(validCastingSource(castingSource)) result.anvilCastingSource = castingSource;
            Key castingFurnace = key(json.optString("anvilFurnace", null));
            if(castingFurnace == Key.ORE_SMELTER || castingFurnace == Key.STACK_FURNACE) result.anvilFurnace = castingFurnace;
            JSONObject amounts = json.optJSONObject("desiredAmounts");
            if(amounts != null) for(String name : amounts.keySet())
                result.setDesiredAmount(key(name), amounts.optDouble(name, Double.NaN));
            result.clayCauldron = json.optBoolean("clayCauldron", result.clayCauldron);
            int storedGenerations = json.optInt("generations", result.generations);
            result.setGenerations(storedGenerations);
            int storedMiningGenerations = json.optInt("miningGenerations", result.miningGenerations);
            result.setMiningGenerations(storedMiningGenerations);
            result.setPotterOutputPerCraft(json.optInt("potterOutputPerCraft", 0));
            JSONObject savedBaseline = json.optJSONObject("baseline");
            if(savedBaseline != null) for(String name : savedBaseline.keySet()) {
                Key key = key(name);
                double quality = savedBaseline.optDouble(name, Double.NaN);
                if(key != null && valid(quality)) result.baseline.put(key, quality);
            }
            readKeys(json.optJSONArray("hiddenInputs"), result.hiddenInputs, false);
            readKeys(json.optJSONArray("hiddenResults"), result.hiddenResults, true);
            result.hiddenInputsExpanded = json.optBoolean("hiddenInputsExpanded", false);
            result.hiddenResultsExpanded = json.optBoolean("hiddenResultsExpanded", false);
        } catch(Exception ignored) {
            return new QualityWorkshopModel();
        }
        return result;
    }

    private static Key key(String name) {
        if(name == null) return null;
        try { return Key.valueOf(name); }
        catch(IllegalArgumentException ignored) { return null; }
    }

    private static boolean valid(double quality) {
        return Double.isFinite(quality) && quality >= MIN_QUALITY && quality <= MAX_QUALITY;
    }

    private static boolean validStackClaySource(Key source) {
        return source == Key.PIT_CLAY || source == Key.CAVE_CLAY || source == Key.BALL_CLAY
                || source == Key.ACRE_CLAY || source == Key.GRAY_CLAY || source == Key.SOAP_CLAY
                || source == Key.BONE_CLAY || source == Key.POTTER_CLAY || source == Key.COADE_CLAY;
    }

    private static boolean validCastingSource(Key source) {
        return source == Key.CASTING_MATERIAL || source == Key.SAND || source == Key.BAD_SAND
                || validStackClaySource(source);
    }

    private static JSONArray keysJson(Set<Key> keys) {
        JSONArray result = new JSONArray();
        for(Key key : keys) result.put(key.name());
        return result;
    }

    private static void readKeys(JSONArray values, Set<Key> target, boolean recipesOnly) {
        if(values == null) return;
        for(int index = 0; index < values.length(); index++) {
            Key key = key(values.optString(index, null));
            if(key != null && (!recipesOnly || RECIPE_SET.contains(key))) target.add(key);
        }
    }

    private boolean wouldCycle(Key candidate) {
        return reaches(candidate, candidate, EnumSet.noneOf(Key.class));
    }

    private boolean reaches(Key current, Key target, Set<Key> seen) {
        if(!seen.add(current)) return false;
        for(Key dependency : dependencies(current)) {
            if(dependency == target) return true;
            if(isRecipe(dependency) && reaches(dependency, target, seen)) return true;
        }
        return false;
    }
}
