package nurgling;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NSkillWndCredoProgressTest {
    @Test
    void pursuedCredoBonusesMatchByNameAndRebuildOnPcr() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/NSkillWnd.java"));
        String credoGrid = src.substring(src.indexOf("new CredoGrid"));

        assertFalse(credoGrid.contains("cr == pcr"),
                "pursued credo must not be matched by instance identity");
        assertTrue(credoGrid.contains("CredoBonusFormatter.isPursuing"),
                "bonus progress must match selected and pursued credos by name");
        assertTrue(credoGrid.contains("public void pcr("),
                "CredoGrid must override pcr so a level-up can refresh the info panel");
        assertTrue(credoGrid.contains("change(pcr)"),
                "pcr updates must rebuild the info panel via change(pcr) when that credo is selected");
    }
}
