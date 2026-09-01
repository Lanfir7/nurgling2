package nurgling.contextmenu;

/** Ore Smelter / Smith's Smelter gob names. Stack furnace is {@code primsmelter} — exclude it first. */
final class SmelterGobs {
    static boolean matches(String name) {
        if (name == null || name.isEmpty())
            return false;
        if (name.contains("primsmelter"))
            return false;
        return name.contains("gfx/terobjs/smelter");
    }

    private SmelterGobs() {}
}
