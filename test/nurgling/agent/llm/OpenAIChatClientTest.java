package nurgling.agent.llm;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIChatClientTest {
    @Test
    void toolRequestsDisableParallelCalls() {
        JSONArray tools = new JSONArray().put(new JSONObject()
                .put("type", "function")
                .put("function", new JSONObject().put("name", "get_world_state")));

        JSONObject payload = OpenAIChatClient.buildPayload(
                "local",
                Collections.singletonList(new LLMMessage("user", "Что рядом?")),
                tools,
                0.2,
                1024
        );

        assertTrue(payload.has("parallel_tool_calls"));
        assertFalse(payload.getBoolean("parallel_tool_calls"));
    }
}
