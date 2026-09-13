package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterMinerStoneTest {

    @Test
    void graniteIsStone() {
        assertTrue(MasterMiner.isStone("Granite"));
    }

    @Test
    void cassiteriteIsOreNotStone() {
        assertFalse(MasterMiner.isStone("Cassiterite"));
        assertTrue(MasterMiner.isOre("Cassiterite"));
        assertFalse(MasterMiner.isGemstone("Cassiterite"));
    }

    @Test
    void onyxIsGemNotStone() {
        assertFalse(MasterMiner.isStone("Onyx"));
        assertFalse(MasterMiner.isOre("Onyx"));
        assertTrue(MasterMiner.isGemstone("Onyx"));
    }

    @Test
    void quarryartzIsNotStoneOreOrGem() {
        assertFalse(MasterMiner.isStone("Quarryartz"));
        assertFalse(MasterMiner.isOre("Quarryartz"));
        assertFalse(MasterMiner.isGemstone("Quarryartz"));
    }

    @Test
    void sandstoneIsStoneNotOreOrGem() {
        assertTrue(MasterMiner.isStone("Sandstone"));
        assertFalse(MasterMiner.isOre("Sandstone"));
        assertFalse(MasterMiner.isGemstone("Sandstone"));
    }

    @Test
    void clayIsNotStoneOreOrGem() {
        assertFalse(MasterMiner.isStone("Clay"));
        assertFalse(MasterMiner.isOre("Clay"));
        assertFalse(MasterMiner.isGemstone("Clay"));
    }
}
