package nurgling.agent.llm;

import java.util.ArrayList;
import java.util.List;

public class LLMResponse {
    public String content = "";
    public final List<LLMToolCall> toolCalls = new ArrayList<>();
    public String finishReason = "";
    public int promptTokens = 0;
    public int completionTokens = 0;
    public int totalTokens = 0;
}
