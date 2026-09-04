package nurgling.craftatlas;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Resolves wiki stat names to the same icon resources used by game tooltips. */
public final class CraftAtlasAttributes {
    private static final Map<String, String> RESOURCES;

    static {
        Map<String, String> values = new LinkedHashMap<>();
        add(values, "Strength", "str");
        add(values, "Agility", "agi");
        add(values, "Intelligence", "int");
        add(values, "Constitution", "con");
        add(values, "Perception", "prc");
        add(values, "Charisma", "csm");
        add(values, "Dexterity", "dex");
        add(values, "Will", "wil");
        add(values, "Psyche", "psy");
        add(values, "Exploration", "explore");
        add(values, "Lore", "lore");
        add(values, "Stealth", "stealth");
        add(values, "Survival", "survive");
        add(values, "Farming", "farming");
        add(values, "Cooking", "cooking");
        add(values, "Sewing", "sewing");
        add(values, "Smithing", "smithing");
        add(values, "Carpentry", "carpentry");
        add(values, "Masonry", "masonry");
        add(values, "Unarmed", "unarmed");
        add(values, "Unarmed Combat", "unarmed");
        add(values, "Melee Combat", "melee");
        add(values, "Marksmanship", "ranged");
        add(values, "Inventory", "invmore");
        RESOURCES = Collections.unmodifiableMap(values);
    }

    private CraftAtlasAttributes() { }

    private static void add(Map<String, String> values, String name, String suffix) {
        values.put(normalize(name), "gfx/hud/chr/" + suffix);
    }

    public static String resource(String name, String fallback) {
        String value = RESOURCES.get(normalize(baseName(name)));
        return value == null ? fallback : value;
    }

    /** Game food events use labels such as "Psyche +1" while the wiki uses "Psyche". */
    public static String baseName(String name) {
        return name == null ? null : name.trim().replaceFirst("\\s+\\+[12]$", "");
    }

    public static CraftAtlasEntry.AttributeRef ref(String resource, String name) {
        return new CraftAtlasEntry.AttributeRef(resource(name, resource), name);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
