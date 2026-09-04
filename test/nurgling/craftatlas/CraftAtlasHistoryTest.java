package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasHistoryTest {
    @Test
    void visitingAfterBackDropsForwardBranch() {
        CraftAtlasHistory h = new CraftAtlasHistory();
        h.visit(new CraftAtlasHistory.CardState("axe", 18, Collections.<String>emptySet()));
        h.visit(new CraftAtlasHistory.CardState("glue", 42, Collections.singleton("requirements")));
        assertEquals("axe", h.back().recipeResource);
        h.visit(new CraftAtlasHistory.CardState("workbench", 0, Collections.<String>emptySet()));
        assertFalse(h.canForward());
    }

    @Test
    void boundsHistoryAndRestoresCardState() {
        CraftAtlasHistory h = new CraftAtlasHistory();
        for(int i = 0; i < CraftAtlasHistory.LIMIT + 10; i++)
            h.visit(new CraftAtlasHistory.CardState("r" + i, i, Collections.singleton("g" + i)));
        int steps = 0;
        while(h.canBack()) { h.back(); steps++; }
        assertEquals(CraftAtlasHistory.LIMIT - 1, steps);
        assertEquals(10, h.current().scroll);
        assertThrows(UnsupportedOperationException.class, () -> h.current().expandedGroups.add("x"));
    }
}
