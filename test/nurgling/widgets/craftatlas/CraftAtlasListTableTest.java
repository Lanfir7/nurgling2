package nurgling.widgets.craftatlas;

import nurgling.craftatlas.CraftAtlasEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasListTableTest {
    @Test
    void foodColumnsExposeFepValuesAndSortMissingValuesLast() {
        CraftAtlasEntry low = food("Low", "Dexterity", 2.5);
        CraftAtlasEntry high = food("High", "Dexterity", 7.5);
        CraftAtlasEntry missing = CraftAtlasEntry.builder("missing", "Missing").category("foods").build();
        CraftAtlasListTable.Column dexterity = CraftAtlasListTable.columnsFor(
                "foods", List.of(low, high, missing)).stream()
                .filter(column -> "food:dexterity".equals(column.id)).findFirst().orElseThrow();

        assertEquals(7.5, dexterity.value(high), 0.001);
        assertEquals(List.of("High", "Low", "Missing"), CraftAtlasListTable.sort(
                List.of(low, missing, high), dexterity, true).stream()
                .map(entry -> entry.displayName).toList());
    }

    @Test
    void foodColumnsUseGameAttributeIconsInStatOrder() {
        List<CraftAtlasEntry> foods = List.of(
                food("Strength", "Strength", 1), food("Agility", "Agility", 1),
                food("Intelligence", "Intelligence", 1), food("Constitution", "Constitution", 1),
                food("Perception", "Perception", 1), food("Charisma", "Charisma", 1),
                food("Dexterity", "Dexterity", 1), food("Will", "Will", 1),
                food("Psyche", "Psyche", 1));
        List<CraftAtlasListTable.Column> columns = CraftAtlasListTable.columnsFor("foods", foods);

        assertEquals(List.of(
                        "gfx/hud/chr/str", "gfx/hud/chr/agi", "gfx/hud/chr/int",
                        "gfx/hud/chr/con", "gfx/hud/chr/prc", "gfx/hud/chr/csm",
                        "gfx/hud/chr/dex", "gfx/hud/chr/wil", "gfx/hud/chr/psy"),
                columns.stream().map(column -> column.iconResource).toList());
        assertEquals(List.of(
                        "Strength", "Agility", "Intelligence", "Constitution", "Perception",
                        "Charisma", "Dexterity", "Will", "Psyche"),
                columns.stream().map(column -> column.tooltip).toList());
    }

    @Test
    void foodColumnsHideStatsMissingFromEveryVisibleRecipe() {
        List<CraftAtlasListTable.Column> columns = CraftAtlasListTable.columnsFor(
                "foods", List.of(food("Dex food", "Dexterity", 2)));

        assertEquals(List.of("Dexterity"), columns.stream().map(column -> column.tooltip).toList());
    }

    @Test
    void gildingColumnsComeFromActualBonuses() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("gild", "Gild")
                .category("gildings")
                .bonus(new CraftAtlasEntry.Bonus("gfx/hud/chr/agi", "Agility", 3.0))
                .bonus(new CraftAtlasEntry.Bonus("gild:chance", "Gild chance", null))
                .build();

        List<CraftAtlasListTable.Column> columns = CraftAtlasListTable.columnsFor("gildings", List.of(entry));

        assertEquals(1, columns.size());
        assertEquals("Agility", columns.get(0).tooltip);
        assertEquals("gfx/hud/chr/agi", columns.get(0).iconResource);
        assertEquals(3.0, columns.get(0).value(entry), 0.001);
    }

    @Test
    void gildingColumnsFollowCharacterOrderAndMergeEquivalentNames() {
        CraftAtlasEntry first = CraftAtlasEntry.builder("first", "First")
                .category("gildings")
                .bonus(new CraftAtlasEntry.Bonus("gild:inventory-space", "Inventory space", 2.0))
                .bonus(new CraftAtlasEntry.Bonus("gild:lore", "Lore", 4.0))
                .bonus(new CraftAtlasEntry.Bonus("gild:unarmed", "Unarmed", 3.0))
                .bonus(new CraftAtlasEntry.Bonus("gild:dexterity", "Dexterity", 1.0))
                .bonus(new CraftAtlasEntry.Bonus("gild:strength", "Strength", 5.0))
                .build();
        CraftAtlasEntry second = CraftAtlasEntry.builder("second", "Second")
                .category("gildings")
                .bonus(new CraftAtlasEntry.Bonus("gild:inventory", "Inventory", 1.0))
                .bonus(new CraftAtlasEntry.Bonus("gild:unarmed-combat", "Unarmed Combat", 6.0))
                .build();

        List<CraftAtlasListTable.Column> columns = CraftAtlasListTable.columnsFor(
                "gildings", List.of(first, second));

        assertEquals(List.of("Strength", "Dexterity", "Unarmed Combat", "Lore", "Inventory"),
                columns.stream().map(column -> column.tooltip).toList());
        assertEquals(1, columns.stream().filter(column -> "gilding:inventory".equals(column.id)).count());
        CraftAtlasListTable.Column inventory = columns.get(4);
        assertEquals(2.0, inventory.value(first), 0.001);
        assertEquals(1.0, inventory.value(second), 0.001);
    }

    @Test
    void curiosityColumnsUseRealHoursAndMentalWeight() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("curio", "Curio")
                .category("curiosities")
                .curiosity(new CraftAtlasEntry.Curiosity(5000, 1969, 14))
                .build();

        List<CraftAtlasListTable.Column> columns = CraftAtlasListTable.columnsFor(
                "curiosities", List.of(entry));

        assertEquals(List.of("curiosity:lp-hour", "curiosity:lp-hour-weight"),
                columns.stream().map(column -> column.id).toList());
        assertEquals(152.36, columns.get(0).value(entry), 0.01);
        assertEquals(10.88, columns.get(1).value(entry), 0.01);
    }

    private static CraftAtlasEntry food(String id, String stat, double value) {
        return CraftAtlasEntry.builder(id, id).category("foods")
                .bonus(new CraftAtlasEntry.Bonus("food:" + stat.toLowerCase(), stat, value)).build();
    }
}
