package nurgling.widgets.craftatlas;

import nurgling.craftatlas.CraftAtlasEntry;
import nurgling.craftatlas.CraftRecipeGraph;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasDetailsTest {
    @Test
    void linksOnlyRowsWithUsefulTargets() {
        CraftAtlasEntry entry = CraftAtlasEntry.builder("axe", "Axe")
                .input(new CraftAtlasEntry.InputSlot(1, false, Collections.singletonList(
                        new CraftAtlasEntry.IngredientOption("glue", "Glue"))))
                .requirement(new CraftAtlasEntry.Requirement(CraftAtlasEntry.RequirementKind.STATION,
                        "workbench", "Workbench", null))
                .requirement(new CraftAtlasEntry.Requirement(CraftAtlasEntry.RequirementKind.SKILL,
                        null, "Carpentry", "Learn it"))
                .bonus(new CraftAtlasEntry.Bonus("str", "Strength", 2.0)).build();
        List<CraftAtlasDetails.DetailRow> rows = CraftAtlasDetails.buildRows(entry, resource ->
                "glue".equals(resource) ? CraftRecipeGraph.LinkState.SINGLE :
                        "workbench".equals(resource) ? CraftRecipeGraph.LinkState.MULTIPLE : CraftRecipeGraph.LinkState.NONE);
        assertEquals(CraftAtlasDetails.Target.INGREDIENT, find(rows, "Glue").target);
        assertEquals(CraftAtlasDetails.Target.INGREDIENT, find(rows, "Workbench").target);
        assertEquals(CraftAtlasDetails.Target.REQUIREMENT_DESCRIPTION, find(rows, "Carpentry").target);
        assertEquals(CraftAtlasDetails.Target.NONE, find(rows, "Strength").target);
    }

    private CraftAtlasDetails.DetailRow find(List<CraftAtlasDetails.DetailRow> rows, String name) {
        for(CraftAtlasDetails.DetailRow row : rows) if(name.equals(row.name)) return row;
        throw new AssertionError(name);
    }
}
