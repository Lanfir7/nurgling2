package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterMinerSupportStoneTest {

    @Test
    void supportReserveUsesTheChipperStoneSetButExcludesSpecialDrops() {
        assertTrue(MasterMiner.isSupportStone("Granite"));
        assertTrue(MasterMiner.isSupportStone("Cassiterite"));
        assertFalse(MasterMiner.isSupportStone("Cat Gold"));
        assertFalse(MasterMiner.isSupportStone("Rakuh"));
        assertFalse(MasterMiner.isSupportStone("Onyx"));
    }

    @Test
    void collectorButtonRequestsThirtyLooseStones() throws Exception {
        String source = Files.readString(Path.of("src/nurgling/widgets/bots/MasterMinerWnd.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("new MasterMiner.CollectSupportStones(MasterMinerGroundStacks.CLICK_PICKUP_LIMIT)"));
    }

    @Test
    void ordinaryMinedStonesAreRecognizedButNotMarkedByDefault() {
        assertTrue(MasterMiner.isStone("Granite"));
        assertFalse(MasterMiner.defaultMarkerEnabled("Granite"));
        assertTrue(MasterMiner.defaultMarkerEnabled("Cassiterite"));
    }
}
