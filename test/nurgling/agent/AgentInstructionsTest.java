package nurgling.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentInstructionsTest {
    @Test
    void playbookCoversSightSafetyAndCartWork() {
        String prompt = AgentInstructions.SYSTEM_PROMPT;
        assertTrue(prompt.contains("get_world_state"));
        assertTrue(prompt.contains("get_player_state"));
        assertTrue(prompt.contains("aggressive"));
        assertTrue(prompt.contains("gate"));
        assertTrue(prompt.contains("Chop"));
        assertTrue(prompt.contains("cart"));
        assertTrue(prompt.contains("flower_action"));
        assertTrue(prompt.contains("lift_gob"));
    }
}
