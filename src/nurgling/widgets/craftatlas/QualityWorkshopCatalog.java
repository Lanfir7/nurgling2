package nurgling.widgets.craftatlas;

import nurgling.craftatlas.quality.QualityWorkshopModel.Key;
import nurgling.i18n.L10n;
import nurgling.tools.CraftIngredientStock;
import java.util.*;

/** Stable game/wiki names are separate from localized UI labels. */
final class QualityWorkshopCatalog {
    private QualityWorkshopCatalog() { }
    static String label(Key key) { return L10n.get("quality_workshop.item." + key.name().toLowerCase(Locale.ROOT)); }
    static int group(Key key) {
        if(key == Key.ORE_SMELTER || key == Key.STACK_FURNACE || key == Key.SMELTED_METAL || key == Key.ANVIL) return 1;
        if(key == Key.STONE_AXE || key == Key.MINED_STONE || key == Key.MINED_ORE) return 2;
        return 0;
    }
    static String iconResource(Key key) {
        if(key == null) return null;
        switch(key) {
        case SOAP_CLAY: return "gfx/invobjs/clay-soap";
        case BONE_CLAY: return "gfx/invobjs/clay-bone";
        case POTTER_CLAY: return "gfx/invobjs/clay-potters";
        case COADE_CLAY: return "gfx/invobjs/clay-coade";
        case RAW_GLASS: return "gfx/invobjs/rawglass";
        case FELDSPAR: return "gfx/invobjs/feldspar";
        case STONE_AXE: return "gfx/invobjs/stoneaxe";
        case SALT_WATER: return "gfx/invobjs/saltwater";
        case POTTERS_WHEEL: return "paginae/bld/potterswheel";
        case ORE_SMELTER: return "paginae/bld/smelter";
        case STACK_FURNACE: return "paginae/bld/primsmelter";
        case CAULDRON: return "paginae/bld/cauldron";
        case STONE_WALL: case ORE_WALL: return "paginae/act/mine";
        default: return null;
        }
    }
    static String name(Key key) {
        switch(key) {
        case SOAP_CLAY: return "Soap Clay";
        case BONE_CLAY: return "Bone Clay";
        case POTTER_CLAY: return "Potter's Clay";
        case COADE_CLAY: return "Coade Clay";
        case POTTERS_WHEEL: return "Potter's Wheel";
        case BONE_ASH: return "Bone Ash";
        case ASH: return "Ashes";
        case BONES: return "Bone Material";
        case BLOCK: return "Block of Wood";
        case GRAY_CLAY: return "Gray Clay";
        case HARD_METAL: return "Bar of Hard Metal";
        case KILN_CLAY: return "Clay";
        case MINED_STONE: return "Stone";
        case MINED_ORE: return "Ore";
        case SMELTED_METAL: return "Bar of Cast Iron";
        case STONE_WALL: case ORE_WALL: return "Mining";
        default:
            StringBuilder result = new StringBuilder();
            for(String word : key.name().toLowerCase(Locale.ROOT).split("_")) {
                if(result.length() > 0) result.append(' ');
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return result.toString();
        }
    }
    static boolean character(Key key) { return key == Key.DEXTERITY || key == Key.INTELLIGENCE || key == Key.MASONRY || key == Key.SURVIVAL; }
    static boolean station(Key key) { return key == Key.CAULDRON || key == Key.KILN || key == Key.POTTERS_WHEEL || key == Key.ORE_SMELTER || key == Key.STACK_FURNACE || key == Key.ANVIL; }
    static String stationKey(Key key, boolean clayCauldron) {
        if(key == Key.CAULDRON) return clayCauldron ? "station:clay-cauldron" : "station:cauldron";
        if(key == Key.KILN) return "station:kiln";
        if(key == Key.POTTERS_WHEEL) return "station:potters-wheel";
        if(key == Key.ORE_SMELTER) return "station:smelter";
        if(key == Key.STACK_FURNACE) return "station:stack-furnace";
        if(key == Key.ANVIL) return "station:anvil";
        return null;
    }
    static String attribute(Key key) {
        return key == Key.DEXTERITY ? "dex" : key == Key.INTELLIGENCE ? "intel" : key == Key.MASONRY ? "masonry" : key == Key.SURVIVAL ? "survive" : null;
    }
    static Set<String> storageNames(Key key) {
        if(character(key) || station(key) || key == Key.STONE_WALL || key == Key.ORE_WALL || key == Key.SMELTED_METAL)
            return Collections.emptySet();
        Set<String> names = new LinkedHashSet<>(CraftIngredientStock.namesFor(
                key == Key.HARD_METAL ? "Bar of Bronze, Iron or Steel" : name(key), false, null));
        if(key == Key.CASTING_MATERIAL) names.add("Bad Sand");
        // Only actual combustible wood, not arbitrary items whose names happen to contain "fuel".
        if(key == Key.FUEL) {
            names.clear();
            names.addAll(CraftIngredientStock.namesFor("Branch", false, null));
            names.addAll(CraftIngredientStock.namesFor("Block of Wood", false, null));
            names.addAll(CraftIngredientStock.namesFor("Coal", false, null));
        }
        return names;
    }
    static String wiki(Key key) {
        String name = key == Key.ASH ? "Bone Ash" : name(key);
        return "https://ringofbrodgar.com/wiki/" + name.replace(" ", "_").replace("'", "%27");
    }
}
