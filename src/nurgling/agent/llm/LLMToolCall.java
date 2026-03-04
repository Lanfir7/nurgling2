package nurgling.agent.llm;

public class LLMToolCall {
    public final String id;
    public final String name;
    public final String argumentsJson;

    public LLMToolCall(String id, String name, String argumentsJson) {
        this.id = id;
        this.name = name;
        this.argumentsJson = argumentsJson;
    }
}
