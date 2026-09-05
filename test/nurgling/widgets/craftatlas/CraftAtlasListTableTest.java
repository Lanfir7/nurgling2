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
    void gildingColumnsComeFromActualBonuses() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("gild", "Gild")
                .category("gildings")
                .bonus(new CraftAtlasEntry.Bonus("gfx/hud/chr/agi", "Agility", 3.0))
                .bonus(new CraftAtlasEntry.Bonus("gild:chance", "Gild chance", null))
                .build();

        List<CraftAtlasListTable.Column> columns = CraftAtlasListTable.columnsFor("gildings", List.of(entry));

        assertEquals(1, columns.size());
        assertEquals("Agility", columns.get(0).tooltip);
        assertEquals(3.0, columns.get(0).value(entry), 0.001);
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
