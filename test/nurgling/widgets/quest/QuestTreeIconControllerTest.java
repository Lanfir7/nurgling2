package nurgling.widgets.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestTreeIconControllerTest {
    @Test
    void convertsTreeGobResourceToVanillaIconSettingsResource() {
        assertEquals("gfx/terobjs/mm/trees/oak",
                QuestTreeIconController.iconResourceForTree("gfx/terobjs/trees/oak"));
    }
}
