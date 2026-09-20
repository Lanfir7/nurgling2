package nurgling.widgets.craftatlas;

import nurgling.craftatlas.quality.QualityWorkshopModel.Key;
import nurgling.i18n.L10n;
import nurgling.tools.CraftIngredientStock;
import java.util.*;

/** Stable game/wiki names are separate from localized UI labels. */
final class QualityWorkshopCatalog {
    private QualityWorkshopCatalog() { }
    static String label(Key key) { return L10n.get("quality_workshop.item." + key.name().toLowerCase(Locale.ROOT)); }
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
        default:
            StringBuilder result = new StringBuilder();
            for(String word : key.name().toLowerCase(Locale.ROOT).split("_")) {
                if(result.length() > 0) result.append(' ');
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return result.toString();
        }
    }
    static boolean character(Key key) { return key == Key.DEXTERITY || key == Key.INTELLIGENCE || key == Key.MASONRY; }
    static boolean station(Key key) { return key == Key.CAULDRON || key == Key.KILN || key == Key.POTTERS_WHEEL; }
    static String stationKey(Key key, boolean clayCauldron) {
        if(key == Key.CAULDRON) return clayCauldron ? "station:clay-cauldron" : "station:cauldron";
        if(key == Key.KILN) return "station:kiln";
        if(key == Key.POTTERS_WHEEL) return "station:potters-wheel";
        return null;
    }
    static String attribute(Key key) {
        return key == Key.DEXTERITY ? "dex" : key == Key.INTELLIGENCE ? "intel" : key == Key.MASONRY ? "masonry" : null;
    }
    static Set<String> storageNames(Key key) {
        if(character(key) || station(key)) return Collections.emptySet();
        Set<String> names = new LinkedHashSet<>(CraftIngredientStock.namesFor(name(key), false, null));
        // Only actual combustible wood, not arbitrary items whose names happen to contain "fuel".
        if(key == Key.FUEL) {
            names.clear();
            names.addAll(CraftIngredientStock.namesFor("Branch", false, null));
            names.addAll(CraftIngredientStock.namesFor("Block of Wood", false, null));
            names.add("Coal");
        }
        return names;
    }
    static String wiki(Key key) {
        String name = key == Key.ASH ? "Bone Ash" : name(key);
        return "https://ringofbrodgar.com/wiki/" + name.replace(" ", "_").replace("'", "%27");
    }
}
