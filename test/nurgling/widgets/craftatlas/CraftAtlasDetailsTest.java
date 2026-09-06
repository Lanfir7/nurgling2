package nurgling.widgets.craftatlas;

import haven.Coord;
import haven.Widget;
import nurgling.craftatlas.CraftAtlasEntry;
import nurgling.craftatlas.CraftRecipeGraph;
import nurgling.i18n.L10n;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasDetailsTest {
    @Test
    void visibleChildControlOwnsClicksInsideItsBounds() {
        Widget control = new Widget(Coord.of(60, 24));
        control.move(Coord.of(200, 100));

        assertTrue(CraftAtlasDetails.hitsVisibleControl(Coord.of(220, 110), Collections.singleton(control)));
        assertFalse(CraftAtlasDetails.hitsVisibleControl(Coord.of(180, 110), Collections.singleton(control)));
        control.hide();
        assertFalse(CraftAtlasDetails.hitsVisibleControl(Coord.of(220, 110), Collections.singleton(control)));
    }

    @Test
    void availabilityUsesStableLocalizationKeys() {
        assertEquals("craft_atlas.status.open", CraftAtlasDetails.statusKey(CraftAtlasEntry.Availability.OPEN));
        assertEquals("craft_atlas.status.unavailable", CraftAtlasDetails.statusKey(CraftAtlasEntry.Availability.UNAVAILABLE_NOW));
        assertEquals("craft_atlas.status.unavailable", CraftAtlasDetails.statusKey(CraftAtlasEntry.Availability.CHECKING));
        assertEquals("craft_atlas.status.reference", CraftAtlasDetails.statusKey(CraftAtlasEntry.Availability.REFERENCE_ONLY));
    }

    @Test
    void linksOnlyRowsWithUsefulTargets() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("axe", "Axe")
                .input(new CraftAtlasEntry.InputSlot(1, false, Collections.singletonList(
                        new CraftAtlasEntry.IngredientOption("glue", "Glue"))))
                .requirement(new CraftAtlasEntry.Requirement(CraftAtlasEntry.RequirementKind.STATION,
                        "workbench", "Workbench", null))
                .requirement(new CraftAtlasEntry.Requirement(CraftAtlasEntry.RequirementKind.SKILL,
                        null, "Carpentry", "Learn it"))
                .requirement(new CraftAtlasEntry.Requirement(CraftAtlasEntry.RequirementKind.TOOL,
                        "hammer", "Hammer", null))
                .bonus(new CraftAtlasEntry.Bonus("str", "Strength", 2.0)).build();
        List<CraftAtlasDetails.DetailRow> rows = CraftAtlasDetails.buildRows(entry, resource ->
                "glue".equals(resource) ? CraftRecipeGraph.LinkState.SINGLE :
                        "workbench".equals(resource) ? CraftRecipeGraph.LinkState.MULTIPLE : CraftRecipeGraph.LinkState.NONE);
        assertEquals(CraftAtlasDetails.Target.INGREDIENT, find(rows, "Glue").target);
        assertEquals(CraftAtlasDetails.Target.INGREDIENT, find(rows, "Workbench").target);
        assertFalse(rows.stream().anyMatch(row -> "Carpentry".equals(row.name)));
        assertEquals(CraftAtlasDetails.Kind.REQUIREMENT, find(rows, "Hammer").kind);
        assertEquals(CraftAtlasDetails.Target.NONE, find(rows, "Strength").target);
    }

    @Test
    void textualWikiBonusDoesNotShowAQuestionMarkValue() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("wiki:gilding", "Gilding")
                .bonus(new CraftAtlasEntry.Bonus("gild:chance", "Gild chance: 45%-95%", null)).build();
        CraftAtlasDetails.DetailRow row = CraftAtlasDetails.buildRows(entry,
                resource -> CraftRecipeGraph.LinkState.NONE).get(0);
        assertNull(row.value);
    }

    @Test
    void detailsDoNotShowCraftQualityModifiers() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("bonepins", "Bone Pins")
                .category("gildings")
                .gilding(new CraftAtlasEntry.Gilding(0.35, 1.0, Collections.singletonList(
                        new CraftAtlasEntry.AttributeRef("gfx/hud/chr/stealth", "Stealth"))))
                .qualityModifier(new CraftAtlasEntry.AttributeRef("gfx/hud/chr/sewing", "Sewing"))
                .bonus(new CraftAtlasEntry.Bonus("gfx/hud/chr/stealth", "Stealth", 5.0))
                .build();

        List<CraftAtlasDetails.DetailRow> rows = CraftAtlasDetails.buildRows(entry,
                resource -> CraftRecipeGraph.LinkState.NONE);

        assertEquals(CraftAtlasDetails.Kind.GILDING, find(rows, "35%–100%").kind);
        assertEquals(CraftAtlasDetails.Kind.BONUS, find(rows, "Stealth").kind);
        assertFalse(rows.stream().anyMatch(row -> row.kind == CraftAtlasDetails.Kind.QUALITY));
        assertFalse(rows.stream().anyMatch(row -> "Sewing".equals(row.name)));
    }

    @Test
    void foodDetailsHideEnergyAndHunger() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("liveronions", "Liver & Onions")
                .category("foods")
                .bonus(new CraftAtlasEntry.Bonus("food:energy", "Energy", 500.0))
                .bonus(new CraftAtlasEntry.Bonus("food:hunger", "Hunger", 1.0))
                .bonus(new CraftAtlasEntry.Bonus("gfx/hud/chr/int", "Intelligence", 1.0))
                .build();

        List<CraftAtlasDetails.DetailRow> rows = CraftAtlasDetails.buildRows(entry,
                resource -> CraftRecipeGraph.LinkState.NONE);

        assertEquals(1, rows.size());
        assertEquals("Intelligence", rows.get(0).name);
    }

    @Test
    void cauldronDetailsShowImplicitWaterQualityFactor() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("boneglue", "Bone Glue")
                .requirement(new CraftAtlasEntry.Requirement(CraftAtlasEntry.RequirementKind.STATION,
                        "gfx/invobjs/cauldron", "Cauldron", null))
                .build();

        List<CraftAtlasDetails.DetailRow> rows = CraftAtlasDetails.buildRows(entry,
                resource -> CraftRecipeGraph.LinkState.NONE);

        assertTrue(rows.stream().anyMatch(row -> row.kind == CraftAtlasDetails.Kind.REQUIREMENT &&
                "context:cauldron-water".equals(row.resource)));
    }

    @Test
    void anvilDetailsShowImplicitSmithysHammerQualityFactor() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("metal", "Metal Craft")
                .requirement(new CraftAtlasEntry.Requirement(CraftAtlasEntry.RequirementKind.STATION,
                        "gfx/terobjs/anvil", "Anvil", null))
                .build();

        List<CraftAtlasDetails.DetailRow> rows = CraftAtlasDetails.buildRows(entry,
                resource -> CraftRecipeGraph.LinkState.NONE);

        assertTrue(rows.stream().anyMatch(row -> row.kind == CraftAtlasDetails.Kind.REQUIREMENT &&
                "Smithy's Hammer".equals(row.name)));
    }

    @Test
    void curiosityLearningPointsReactToSelectedQuality() {
        String previousLanguage = L10n.getLanguage();
        try {
            L10n.setLanguage("en");
            CraftAtlasEntry entry = CraftAtlasEntry.builder("curio", "Curio")
                    .category("curiosities")
                    .curiosity(new CraftAtlasEntry.Curiosity(5000, 120, 5))
                    .build();

            List<CraftAtlasDetails.DetailRow> rows = CraftAtlasDetails.buildRows(entry,
                    (resource, name) -> CraftRecipeGraph.LinkState.NONE, 40);

            assertEquals("10,000", find(rows, L10n.get("craft_atlas.curiosity.lp")).value);
            assertEquals("5000", find(rows, L10n.get("craft_atlas.curiosity.lp_hour")).value);
            assertEquals("1000", find(rows, L10n.get("craft_atlas.curiosity.lp_hour_weight")).value);
            assertEquals("2h 0m", find(rows, L10n.get("craft_atlas.curiosity.time")).value);
            assertEquals("5", find(rows, L10n.get("craft_atlas.curiosity.weight")).value);
        } finally {
            L10n.setLanguage(previousLanguage);
        }
    }

    @Test
    void alternativeIngredientsShareOneSelectableRecipeSlot() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("cloth", "Cloth")
                .input(new CraftAtlasEntry.InputSlot(2, false, Arrays.asList(
                        new CraftAtlasEntry.IngredientOption("linen", "Linen Cloth"),
                        new CraftAtlasEntry.IngredientOption("hemp", "Hemp Cloth"))))
                .input(new CraftAtlasEntry.InputSlot(1, false, Collections.singletonList(
                        new CraftAtlasEntry.IngredientOption("thread", "Thread"))))
                .build();

        List<CraftAtlasDetails.DetailRow> rows = CraftAtlasDetails.buildRows(entry,
                resource -> CraftRecipeGraph.LinkState.NONE);

        assertEquals(2, rows.size());
        assertEquals("Linen Cloth / Hemp Cloth", rows.get(0).name);
        assertEquals(0, rows.get(0).slotIndex);
        assertEquals(1, rows.get(1).slotIndex);
    }

    @Test
    void unavailableAutomaticQualityIsNotRenderedAsZero() {
        assertEquals("—", CraftAtlasDetails.autoQualityText(null));
        assertEquals("95.5", CraftAtlasDetails.autoQualityText(95.5));
    }

    @Test
    void qualityControlsStayInsideNarrowDetailsHeader() {
        int[] x = CraftAtlasDetails.qualityControlPositions(320, 54, 58, 8, 12);

        assertTrue(x[0] >= 0);
        assertTrue(x[0] + 54 <= x[1]);
        assertTrue(x[1] + 58 <= 320);
        assertEquals(1, x[2]);
    }

    @Test
    void qualityEntryStaysInsideHeaderWhenAutoControlCannotFit() {
        int[] x = CraftAtlasDetails.qualityControlPositions(80, 54, 58, 8, 12);

        assertEquals(0, x[2]);
        assertTrue(x[0] + 54 <= 80);
    }

    @Test
    void equipmentDetailsShowOccupiedSlots() {
        String previousLanguage = L10n.getLanguage();
        try {
            L10n.setLanguage("ru");
            CraftAtlasEntry entry = CraftAtlasEntry.builder("equipment", "Equipment")
                    .category("equipment")
                    .equipmentSlot("11R")
                    .equipmentSlot("7L; 7R")
                    .equipmentSlot("1L and 11L")
                    .equipmentSlot("8L; 1L (optional)")
                    .build();

            List<CraftAtlasDetails.DetailRow> rows = CraftAtlasDetails.buildRows(entry,
                    resource -> CraftRecipeGraph.LinkState.NONE);

            assertEquals(CraftAtlasDetails.Kind.SLOT, find(rows, "Обувь (11R)").kind);
            assertNotNull(find(rows, "Левое кольцо (7L); Правое кольцо (7R)"));
            assertNotNull(find(rows, "Головной убор (1L); Накидка (11L)"));
            assertNotNull(find(rows, "Плащи и робы (8L); Головной убор (1L) — необязательно"));
        } finally {
            L10n.setLanguage(previousLanguage);
        }
    }

    private CraftAtlasDetails.DetailRow find(List<CraftAtlasDetails.DetailRow> rows, String name) {
        for(CraftAtlasDetails.DetailRow row : rows) if(name.equals(row.name)) return row;
        throw new AssertionError(name);
    }

}
