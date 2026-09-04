package nurgling.craftatlas;

/** Q10 projection used by the encyclopedia for food FEPs and gilding effects. */
public final class CraftAtlasQuality {
    private CraftAtlasQuality() { }

    public static Double project(CraftAtlasEntry entry, CraftAtlasEntry.Bonus bonus, double quality) {
        if(bonus == null || bonus.value == null) return null;
        double value = bonus.value;
        double multiplier = Math.sqrt(Math.max(1, quality) / 10.0);
        if(entry.categories.contains("equipment")) return value * multiplier;
        if(entry.categories.contains("gildings")) {
            if("gild:inventory".equals(bonus.attributeResource) ||
                    "gfx/hud/chr/invmore".equals(bonus.attributeResource)) return value;
            return (double)Math.round(value * multiplier);
        }
        if(entry.categories.contains("foods")) {
            if("food:energy".equals(bonus.attributeResource) || "food:hunger".equals(bonus.attributeResource))
                return value;
            return value * multiplier;
        }
        return value;
    }
}
