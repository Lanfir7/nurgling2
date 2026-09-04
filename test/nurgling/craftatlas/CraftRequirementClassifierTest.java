package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CraftRequirementClassifierTest {
    @Test
    void classifiesStationsToolsAndPreservesUnknownResources() {
        CraftRequirementClassifier c = new CraftRequirementClassifier(Collections.<String, CraftAtlasEntry.RequirementKind>emptyMap());
        assertEquals(CraftAtlasEntry.RequirementKind.STATION, c.classify("gfx/terobjs/workbench", "Workbench").kind);
        assertEquals(CraftAtlasEntry.RequirementKind.TOOL, c.classify("gfx/invobjs/hammer", "Hammer").kind);
        CraftAtlasEntry.Requirement unknown = c.classify("custom/requirement", "Mystery");
        assertEquals(CraftAtlasEntry.RequirementKind.TOOL, unknown.kind);
        assertNotNull(unknown.description);
    }

    @Test
    void trustedOverrideWins() {
        CraftRequirementClassifier c = new CraftRequirementClassifier(Collections.singletonMap(
                "custom/anvil", CraftAtlasEntry.RequirementKind.STATION));
        assertEquals(CraftAtlasEntry.RequirementKind.STATION, c.classify("custom/anvil", "Anvil").kind);
    }
}
