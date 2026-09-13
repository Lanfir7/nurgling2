package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadishFarmerQTest {
    @Test
    void cleanupSeedQContainerTargetsRadishSeedsNotBroadRadish() throws Exception {
        String source = Files.readString(Path.of("src/nurgling/actions/bots/RadishFarmerQ.java"));

        assertTrue(source.contains("new CleanupSeedQContainer(NContext.findSpec(seedQ), new NAlias(\"Radish Seeds\"), NContext.findSpec(trough))"),
                "CleanupSeedQContainer must target Radish Seeds so substring matching cannot capture the vegetable");
        assertFalse(source.contains("new CleanupSeedQContainer(NContext.findSpec(seedQ), new NAlias(\"Radish\"), NContext.findSpec(trough))"),
                "CleanupSeedQContainer must not use broad Radish, which substring-matches Radish Seeds");
    }
}
