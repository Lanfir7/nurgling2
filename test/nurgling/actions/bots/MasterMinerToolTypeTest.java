package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MasterMinerToolTypeTest {

    @Test
    void pickaxeNamesAreRecognized() {
        assertEquals(MasterMiner.ToolType.PICKAXE, MasterMiner.classifyTool("Pickaxe"));
        assertEquals(MasterMiner.ToolType.PICKAXE, MasterMiner.classifyTool("Кирка"));
        assertNotEquals(MasterMiner.ToolType.PICKAXE, MasterMiner.classifyTool("Stone Axe"));
        assertNotEquals(MasterMiner.ToolType.PICKAXE, MasterMiner.classifyTool(null));
    }
}
