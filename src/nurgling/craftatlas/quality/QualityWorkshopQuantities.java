package nurgling.craftatlas.quality;

import nurgling.craftatlas.quality.QualityWorkshopModel.Key;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ingredient planner for the bounded Quality Workshop recipe graph. */
public final class QualityWorkshopQuantities {
    private QualityWorkshopQuantities() { }

    public static final class Plan {
        public final Map<Key, Double> materials;
        public final Map<Key, Double> equipment;
        public final Map<Key, Double> production;
        public final List<String> warnings;

        private Plan(Map<Key, Double> materials, Map<Key, Double> equipment,
                     Map<Key, Double> production, Set<String> warnings) {
            this.materials = immutable(materials);
            this.equipment = immutable(equipment);
            this.production = immutable(production);
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        private static Map<Key, Double> immutable(Map<Key, Double> values) {
            Map<Key, Double> copy = new LinkedHashMap<>();
            for(Key key : Key.values()) if(values.containsKey(key)) copy.put(key, values.get(key));
            return Collections.unmodifiableMap(copy);
        }
    }

    public static Plan calculate(QualityWorkshopModel model) {
        if(model == null) return new Plan(Collections.<Key, Double>emptyMap(), Collections.<Key, Double>emptyMap(),
                Collections.<Key, Double>emptyMap(), Collections.<String>emptySet());
        return new Planner(model).calculate();
    }

    private static final class Planner {
        private final QualityWorkshopModel model;
        private final Set<Key> watched = EnumSet.noneOf(Key.class);
        private final Set<Key> nodes = new LinkedHashSet<>();
        private final Map<Key, List<Key>> edges = new EnumMap<>(Key.class);
        private final Map<Key, Double> demand = new EnumMap<>(Key.class);
        private final Map<Key, Double> materials = new EnumMap<>(Key.class);
        private final Map<Key, Double> equipment = new EnumMap<>(Key.class);
        private final Map<Key, Double> production = new EnumMap<>(Key.class);
        private final Set<String> warnings = new LinkedHashSet<>();

        Planner(QualityWorkshopModel model) {
            this.model = model;
            watched.addAll(model.watched());
        }

        Plan calculate() {
            for(Key key : watched) {
                double desired = model.desiredAmount(key);
                if(desired > 0) {
                    add(demand, key, desired);
                    visit(key);
                }
            }
            for(Key recipe : nodes) requireStations(recipe);
            processGraph();
            return new Plan(materials, equipment, production, warnings);
        }

        private void visit(Key recipe) {
            if(!isWatchedRecipe(recipe) || !nodes.add(recipe)) return;
            List<Key> children = new ArrayList<>();
            for(Key key : recipeChildren(recipe)) {
                if(isWatchedRecipe(key)) {
                    children.add(key);
                    visit(key);
                }
            }
            edges.put(recipe, children);
        }

        private boolean isWatchedRecipe(Key key) { return key != null && watched.contains(key) && model.isRecipe(key); }

        private void processGraph() {
            Map<Key, Integer> parents = new EnumMap<>(Key.class);
            for(Key key : nodes) parents.put(key, 0);
            for(List<Key> children : edges.values()) for(Key child : children)
                parents.put(child, parents.get(child) + 1);
            ArrayDeque<Key> ready = new ArrayDeque<>();
            for(Key key : nodes) if(parents.get(key) == 0) ready.add(key);
            int processed = 0;
            while(!ready.isEmpty()) {
                Key recipe = ready.removeFirst();
                process(recipe, demand.getOrDefault(recipe, 0.0));
                processed++;
                for(Key child : edges.get(recipe)) {
                    int remaining = parents.get(child) - 1;
                    parents.put(child, remaining);
                    if(remaining == 0) ready.addLast(child);
                }
            }
            if(processed != nodes.size()) warnings.add("planner_cycle");
        }

        private void process(Key recipe, double needed) {
            if(needed <= 0) return;
            if(recipe == Key.SMELTED_METAL) {
                add(materials, Key.SMELTED_METAL, needed);
                warnings.add("smelted_metal_yield");
                return;
            }
            if(recipe == Key.POTTER_CLAY) {
                int yield = model.potterOutputPerCraft();
                if(yield <= 0) {
                    add(materials, Key.POTTER_CLAY, needed);
                    warnings.add("potter_clay_yield");
                    return;
                }
                planPotterClay(needed, yield);
                return;
            }
            if(recipe == Key.LYE) {
                add(production, recipe, needed);
                consume(Key.ASH, needed * 10.0);
                warnings.add("cauldron_water");
                return;
            }
            if(recipe == Key.MINED_STONE) {
                double units = batches(needed, 1.0);
                int generations = model.miningGenerations();
                add(production, recipe, units + generations - 1);
                if(generations > 1) {
                    add(materials, Key.BRANCH, generations - 1);
                    if(isWatchedRecipe(Key.STONE_AXE)) add(production, Key.STONE_AXE, generations - 1);
                }
                return;
            }
            double crafts = batches(needed, yield(recipe));
            add(production, recipe, crafts * yield(recipe));
            switch(recipe) {
                case SOAP_CLAY:
                    consume(Key.CAVE_CLAY, crafts);
                    consume(Key.LYE, crafts * 0.02);
                    consume(Key.SALT_WATER, crafts * 0.1);
                    warnings.add("cauldron_water");
                    break;
                case BONE_CLAY:
                    consume(Key.BONE_ASH, crafts * 5);
                    consume(Key.FELDSPAR, crafts);
                    consume(model.boneClaySource(), crafts);
                    break;
                case COADE_CLAY:
                    consume(Key.BALL_CLAY, crafts * 6);
                    consume(Key.BRICK, crafts);
                    consume(Key.FLINT, crafts);
                    consume(Key.QUARTZ, crafts);
                    consume(Key.RAW_GLASS, crafts);
                    warnings.add("quern_required");
                    break;
                case BRICK:
                    consume(model.brickClaySource(), crafts);
                    warnings.add("kiln_fuel");
                    break;
                case BONE_ASH:
                    consume(Key.BONES, crafts);
                    warnings.add("kiln_fuel");
                    break;
                case ASH:
                    consume(Key.BONE_ASH, crafts);
                    break;
                case POTTERS_WHEEL:
                    consume(Key.BOARD, crafts * 6);
                    consume(Key.BLOCK, crafts * 8);
                    consume(Key.STONE, crafts * 5);
                    consume(Key.BALL_CLAY, crafts * 10);
                    consume(Key.ROPE, crafts);
                    break;
                case KILN:
                    consume(Key.KILN_CLAY, crafts * 35);
                    break;
                case ORE_SMELTER:
                    consume(Key.BRICK, crafts * 35);
                    consume(Key.STONE, crafts * 10);
                    consume(Key.HARD_METAL, crafts * 3);
                    break;
                case STACK_FURNACE:
                    consume(model.stackClaySource(), crafts * 15);
                    consume(Key.STONE, crafts * 10);
                    consume(Key.BOARD, crafts * 2);
                    consume(Key.BLOCK, crafts * 4);
                    consume(Key.LEATHER, crafts * 2);
                    break;
                case STONE_AXE:
                    consume(Key.STONE, crafts);
                    consume(Key.BRANCH, crafts);
                    break;
                case MINED_ORE:
                    break;
                case ANVIL:
                    consume(model.anvilCastingSource(), crafts * 10);
                    consume(Key.HARD_METAL, crafts * 5);
                    warnings.add("casting_fuel");
                    break;
                default:
                    break;
            }
        }

        private void consume(Key key, double amount) {
            if(amount <= 0 || key == null) return;
            if(isWatchedRecipe(key)) add(demand, key, amount);
            else add(materials, key, amount);
        }

        private void requireStations(Key recipe) {
            switch(recipe) {
                case SOAP_CLAY:
                case LYE:
                    requireEquipment(Key.CAULDRON);
                    break;
                case BONE_ASH:
                case BRICK:
                    requireEquipment(Key.KILN);
                    break;
                case POTTER_CLAY:
                    if(model.potterOutputPerCraft() > 0) {
                        requireEquipment(Key.POTTERS_WHEEL);
                        if(model.desiredAmount(Key.POTTER_CLAY) > 0 && model.generations() > 1)
                            requireEquipment(Key.KILN);
                    }
                    break;
                case SMELTED_METAL:
                    requireEquipment(model.smeltingFurnace());
                    break;
                case MINED_STONE:
                case MINED_ORE:
                    requireEquipment(Key.STONE_AXE);
                    break;
                case ANVIL:
                    requireEquipment(model.anvilFurnace());
                    break;
                default:
                    break;
            }
        }

        private void requireEquipment(Key key) {
            if(key == null) return;
            if(isWatchedRecipe(key)) {
                Double existing = demand.get(key);
                if(existing == null || existing < 1) demand.put(key, 1.0);
            } else {
                Double existing = equipment.get(key);
                if(existing == null || existing < 1) equipment.put(key, 1.0);
            }
        }

        private List<Key> recipeChildren(Key recipe) {
            List<Key> result = new ArrayList<>();
            switch(recipe) {
                case SOAP_CLAY: result.add(Key.LYE); break;
                case BONE_CLAY: result.add(Key.BONE_ASH); result.add(model.boneClaySource()); break;
                case POTTER_CLAY:
                    if(model.potterOutputPerCraft() > 0) {
                        result.add(Key.BONE_CLAY);
                        result.add(Key.BRICK);
                        result.add(Key.POTTERS_WHEEL);
                        if(model.desiredAmount(Key.POTTER_CLAY) > 0 && model.generations() > 1) result.add(Key.KILN);
                    }
                    break;
                case COADE_CLAY: result.add(Key.BRICK); break;
                case BRICK: result.add(model.brickClaySource()); result.add(Key.KILN); break;
                case ORE_SMELTER: result.add(Key.BRICK); break;
                case STACK_FURNACE: result.add(model.stackClaySource()); break;
                case BONE_ASH: result.add(Key.KILN); break;
                case ASH: result.add(Key.BONE_ASH); break;
                case LYE: result.add(Key.ASH); break;
                case SMELTED_METAL: result.add(model.smeltingFurnace()); break;
                case MINED_STONE:
                case MINED_ORE: result.add(Key.STONE_AXE); break;
                case ANVIL: result.add(model.anvilCastingSource()); result.add(model.anvilFurnace()); break;
                default: break;
            }
            return result;
        }

        private static double yield(Key recipe) {
            if(recipe == Key.COADE_CLAY) return 6.0;
            if(recipe == Key.ASH) return 0.2;
            return 1.0;
        }

        private void planPotterClay(double needed, int yield) {
            double finalDemand = Math.min(needed, model.desiredAmount(Key.POTTER_CLAY));
            double baseDemand = needed - finalDemand;
            double stageDemand = finalDemand;
            for(int generation = model.generations(); generation > 1; generation--) {
                if(stageDemand <= 0) break;
                double crafts = batches(stageDemand, yield);
                add(production, Key.POTTER_CLAY, crafts * yield);
                consume(Key.BONE_CLAY, crafts);
                consume(Key.ACRE_CLAY, crafts);
                consume(Key.GRAY_CLAY, crafts);
                add(production, Key.BRICK, crafts);
                warnings.add("kiln_fuel");
                stageDemand = crafts;
            }
            baseDemand += stageDemand;
            double crafts = batches(baseDemand, yield);
            add(production, Key.POTTER_CLAY, crafts * yield);
            consume(Key.BONE_CLAY, crafts);
            consume(Key.ACRE_CLAY, crafts);
            consume(Key.GRAY_CLAY, crafts);
            consume(Key.BRICK, crafts);
        }

        private static double batches(double needed, double yield) {
            if(needed <= 0 || yield <= 0) return 0;
            double ratio = needed / yield;
            double nearest = Math.rint(ratio);
            if(nearest >= 1 && Math.abs(ratio - nearest) <= Math.ulp(ratio) * 8.0) return nearest;
            return Math.ceil(ratio);
        }

        private static void add(Map<Key, Double> target, Key key, double amount) {
            if(key != null && amount > 0) target.put(key, target.getOrDefault(key, 0.0) + amount);
        }
    }
}
