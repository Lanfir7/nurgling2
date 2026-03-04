package nurgling.agent.llm;

import org.json.JSONArray;

public class LLMMessage {
    public final String role;
    public final String content;
    public final String name;
    public final String toolCallId;
    public final JSONArray toolCalls;

    public LLMMessage(String role, String content) {
        this(role, content, null, null, null);
    }

    public LLMMessage(String role, String content, String name, String toolCallId) {
        this(role, content, name, toolCallId, null);
    }

    public LLMMessage(String role, String content, String name, String toolCallId, JSONArray toolCalls) {
        this.role = role;
        this.content = content;
        this.name = name;
        this.toolCallId = toolCallId;
        this.toolCalls = toolCalls;
    }
}
