package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftAtlasQualityTest {
    @Test
    void gildingEffectsUseTheQualityMultiplierAndRoundToWholeNumbers() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("gild", "Gild")
                .category("gildings").build();

        assertEquals(6.0, CraftAtlasQuality.project(entry,
                new CraftAtlasEntry.Bonus("gfx/hud/chr/agi", "Agility", 3.0), 34));
    }

    @Test
    void foodFepsScaleButEnergyAndHungerStayAtTheirBaseValues() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("food", "Food")
                .category("foods").build();

        assertEquals(12.0, CraftAtlasQuality.project(entry,
                new CraftAtlasEntry.Bonus("food:con", "Constitution", 4.0), 90));
        assertEquals(800.0, CraftAtlasQuality.project(entry,
                new CraftAtlasEntry.Bonus("food:energy", "Energy", 800.0), 90));
        assertEquals(3.0, CraftAtlasQuality.project(entry,
                new CraftAtlasEntry.Bonus("food:hunger", "Hunger", 3.0), 90));
    }
}
