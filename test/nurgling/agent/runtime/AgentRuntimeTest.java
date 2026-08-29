package nurgling.agent.runtime;

import nurgling.agent.llm.LLMMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeTest {
    @Test
    void agentErrorsAreVisibleToTheUser() {
        assertTrue(AgentRuntime.isUserVisibleLine("Agent error: HTTP 500"));
    }

    @Test
    void memoryContextIsMergedIntoLeadingSystemMessage() {
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage("system", "base instructions"));
        messages.add(new LLMMessage("user", "hello"));

        List<LLMMessage> result = AgentRuntime.withSystemContext(messages, "memory context");

        assertEquals(2, result.size());
        assertEquals("system", result.get(0).role);
        assertTrue(result.get(0).content.contains("base instructions"));
        assertTrue(result.get(0).content.contains("memory context"));
        assertEquals("user", result.get(1).role);
        assertFalse(result.subList(1, result.size()).stream().anyMatch(message -> "system".equals(message.role)));
    }

    @Test
    void memoryContextIsPrependedWhenHistoryHasNoSystemMessage() {
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage("user", "hello"));

        List<LLMMessage> result = AgentRuntime.withSystemContext(messages, "memory context");

        assertEquals("system", result.get(0).role);
        assertEquals("memory context", result.get(0).content);
        assertEquals("user", result.get(1).role);
    }
}
