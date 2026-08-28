package nurgling.agent.runtime;

import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.agent.memory.MemoryRecord;
import nurgling.agent.memory.MemoryRetriever;
import nurgling.agent.memory.MemoryStore;
import nurgling.agent.llm.LLMMessage;
import nurgling.agent.llm.LLMResponse;
import nurgling.agent.llm.LLMToolCall;
import nurgling.agent.llm.OpenAIChatClient;
import nurgling.llm.LocalLlmLifecycle;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AgentRuntime {
    private static final int MAX_TOOL_ROUNDS = 64;
    private static final int MAX_LOG_LINE_CHARS = 420;
    private static final int MAX_MEMORY_ROWS = 5000;
    private static final String SYSTEM_PROMPT = nurgling.agent.AgentInstructions.SYSTEM_PROMPT;

    private final NGameUI gui;
    private final OpenAIChatClient client = new OpenAIChatClient();
    private final ToolRouter tools;
    private final MemoryStore memoryStore;
    private final MemoryRetriever memoryRetriever;
    private final List<LLMMessage> history = new ArrayList<>();
    private volatile Thread worker;
    private volatile boolean running = false;
    private volatile AgentEventListener listener;
    private volatile long lastMemoryId = -1;
    private volatile String lastUserPrompt = "";
    private volatile String lastWorldStateSummary = "";
    private volatile int consecutiveToolErrors = 0;

    public AgentRuntime(NGameUI gui) {
        this.gui = gui;
        this.tools = new ToolRouter(gui);
        this.memoryStore = new MemoryStore();
        this.memoryRetriever = new MemoryRetriever(memoryStore);
        resetHistory();
    }

    public void setListener(AgentEventListener listener) {
        this.listener = listener;
    }

    public boolean isRunning() {
        return running;
    }

    public void startWithPrompt(String prompt) {
        if (running) {
            log("Agent already running");
            return;
        }
        running = true;
        worker = new Thread(() -> runLoop(prompt), "LLM-Agent-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running = false;
        Thread t = worker;
        if (t != null) {
            t.interrupt();
        }
        tools.execute("stop_all_actions", "{}");
        log("Agent stopped");
    }

    public void submitPrompt(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) return;
        lastUserPrompt = prompt.trim();
        if (!running) {
            startWithPrompt(prompt);
            return;
        }
        synchronized (history) {
            history.add(new LLMMessage("user", prompt));
        }
    }

    public void clearContext() {
        synchronized (history) {
            resetHistory();
        }
        log("Контекст очищен");
    }

    public void addManualFeedback(int delta) {
        if (lastMemoryId <= 0) {
            log("Нет недавнего эпизода для оценки");
            return;
        }
        double d = delta > 0 ? 1.0 : -1.0;
        memoryStore.addReward(lastMemoryId, d);
        log("Feedback: reward " + (d > 0 ? "+1" : "-1"));
    }

    public void resetSessionTokenCounters() {
        AgentSessionStats.resetTokenCounters();
        log("Счетчики токенов за сеанс сброшены");
    }

    private void runLoop(String initialPrompt) {
        try {
            synchronized (history) {
                history.add(new LLMMessage("user", initialPrompt));
            }
            lastUserPrompt = initialPrompt == null ? "" : initialPrompt.trim();
            log("Agent started");
            for (int round = 0; running && round < MAX_TOOL_ROUNDS; round++) {
                AgentSessionStats.recordToolRound();
                long llmStartedAt = System.currentTimeMillis();
                LLMResponse response = askModel();
                long llmDurationMs = System.currentTimeMillis() - llmStartedAt;
                AgentSessionStats.recordUsage(response.promptTokens, response.completionTokens, response.totalTokens);
                log(String.format(Locale.ROOT, "TOKENS req: prompt=%d, completion=%d, total=%d",
                        response.promptTokens, response.completionTokens, response.totalTokens));
                log(String.format(Locale.ROOT, "METRIC llm: ms=%d, finish=%s", llmDurationMs, response.finishReason));
                String assistantText = sanitizeAssistantContent(response.content);
                if (!assistantText.isEmpty()) {
                    log("ASSISTANT: " + assistantText);
                    synchronized (history) {
                        history.add(new LLMMessage("assistant", assistantText));
                    }
                }

                if (response.toolCalls.isEmpty()) {
                    break;
                }

                synchronized (history) {
                    history.add(new LLMMessage("assistant", assistantText, null, null, toToolCallsJson(response.toolCalls)));
                }
                for (LLMToolCall tc : response.toolCalls) {
                    if (!running) break;
                    long toolStartedAt = System.currentTimeMillis();
                    String toolResult = tools.execute(tc.name, tc.argumentsJson);
                    long toolDurationMs = System.currentTimeMillis() - toolStartedAt;
                    boolean ok = isToolOk(toolResult);
                    if (ok) {
                        consecutiveToolErrors = 0;
                    } else {
                        consecutiveToolErrors++;
                    }
                    AgentSessionStats.recordToolResult(ok);
                    log(formatToolLog(tc.name, toolResult, toolDurationMs));
                    synchronized (history) {
                        history.add(new LLMMessage("tool", toolResult, tc.name, tc.id));
                    }
                    if ("get_world_state".equals(tc.name)) {
                        lastWorldStateSummary = shortenForLog(toolResult);
                    }
                    lastMemoryId = memoryStore.addRecord(
                            lastUserPrompt,
                            lastWorldStateSummary,
                            tc.name,
                            toolResult,
                            ok ? 1.0 : -1.0
                    );
                    if (ok) {
                        maybePromoteRule(tc.name, lastUserPrompt);
                    }
                    if (consecutiveToolErrors >= 3) {
                        log("AGENT safety: слишком много ошибок tools подряд, остановка цикла");
                        break;
                    }
                }
                memoryStore.trimMemory(MAX_MEMORY_ROWS);
            }
        } catch (Exception e) {
            log("Agent error: " + e.getMessage());
        } finally {
            running = false;
            worker = null;
        }
    }

    private LLMResponse askModel() throws Exception {
        LocalLlmLifecycle localLlm = LocalLlmLifecycle.global();
        AgentLlmRoute.Target target = AgentLlmRoute.resolve(
                boolCfg(NConfig.Key.agentUseBuiltInLlm, true),
                localLlm.getStatus(),
                strCfg(NConfig.Key.agentBaseUrl, "http://127.0.0.1:1234"),
                strCfg(NConfig.Key.agentApiKey, ""),
                strCfg(NConfig.Key.agentModel, "gpt-4o-mini")
        );
        double temperature = numCfg(NConfig.Key.agentTemperature, 0.2);
        int maxTokens = (int) numCfg(NConfig.Key.agentMaxTokens, 1024);
        int timeoutMs = (int) numCfg(NConfig.Key.agentTimeoutMs, 120000);
        List<LLMMessage> messagesSnapshot;
        synchronized (history) {
            messagesSnapshot = new ArrayList<>(history);
        }
        String latestPrompt = latestUserPrompt(messagesSnapshot);
        if (latestPrompt != null && !latestPrompt.isEmpty()) {
            List<MemoryRecord> episodes = memoryRetriever.retrieve(latestPrompt, 4);
            List<String> rules = memoryRetriever.topRules(3);
            String memoryContext = buildMemoryContext(rules, episodes);
            if (!memoryContext.isEmpty()) {
                messagesSnapshot.add(new LLMMessage("system", memoryContext));
            }
        }
        return client.chat(target.baseUrl, target.apiKey, target.model, messagesSnapshot, tools.toolDefinitions(),
                temperature, maxTokens, timeoutMs);
    }

    private static boolean boolCfg(NConfig.Key key, boolean def) {
        Object v = NConfig.get(key);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    private static String strCfg(NConfig.Key key, String def) {
        Object v = NConfig.get(key);
        if (v instanceof String) {
            String s = (String) v;
            if (!s.trim().isEmpty()) return s;
        }
        return def;
    }

    private static double numCfg(NConfig.Key key, double def) {
        Object v = NConfig.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return def;
    }

    private void log(String line) {
        String safe = shortenForLog(line);
        AgentEventListener l = listener;
        if (l != null) {
            l.onLog(safe);
        }
        if (gui != null && isChatVisibleLine(safe)) {
            gui.msg(safe);
        }
    }

    private static boolean isChatVisibleLine(String line) {
        if (line == null) return false;
        return line.startsWith("YOU:") || line.startsWith("ASSISTANT:") || line.startsWith("TOOL ");
    }

    private static String shortenForLog(String line) {
        if (line == null) return "";
        if (line.length() <= MAX_LOG_LINE_CHARS) return line;
        return line.substring(0, MAX_LOG_LINE_CHARS) + "... [truncated " + (line.length() - MAX_LOG_LINE_CHARS) + " chars]";
    }

    private static String formatToolLog(String toolName, String toolResult, long durationMs) {
        try {
            JSONObject obj = new JSONObject(toolResult);
            boolean ok = obj.optBoolean("ok", false);
            if (ok) {
                return "TOOL " + toolName + ": ok, ms=" + durationMs;
            }
            String err = obj.optString("error", "ошибка");
            return "TOOL " + toolName + ": error=" + shortenForLog(err) + ", ms=" + durationMs;
        } catch (Exception ignored) {
            return "TOOL " + toolName + ": " + shortenForLog(toolResult) + ", ms=" + durationMs;
        }
    }

    private static JSONArray toToolCallsJson(List<LLMToolCall> calls) {
        JSONArray arr = new JSONArray();
        for (LLMToolCall call : calls) {
            arr.put(new JSONObject()
                    .put("id", call.id)
                    .put("type", "function")
                    .put("function", new JSONObject()
                            .put("name", call.name)
                            .put("arguments", call.argumentsJson == null ? "{}" : call.argumentsJson)));
        }
        return arr;
    }

    private void resetHistory() {
        history.clear();
        history.add(new LLMMessage("system", SYSTEM_PROMPT));
        consecutiveToolErrors = 0;
    }

    private static boolean isToolOk(String toolResult) {
        try {
            return new JSONObject(toolResult).optBoolean("ok", false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String latestUserPrompt(List<LLMMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            LLMMessage msg = messages.get(i);
            if ("user".equals(msg.role) && msg.content != null && !msg.content.trim().isEmpty()) {
                return msg.content.trim();
            }
        }
        return "";
    }

    private static String buildMemoryContext(List<String> rules, List<MemoryRecord> episodes) {
        StringBuilder sb = new StringBuilder();
        if (rules != null && !rules.isEmpty()) {
            sb.append("Проверенные правила (кратко):\n");
            for (String rule : rules) {
                sb.append("- ").append(rule).append("\n");
            }
        }
        if (episodes != null && !episodes.isEmpty()) {
            sb.append("Релевантные прошлые эпизоды:\n");
            for (MemoryRecord ep : episodes) {
                sb.append("- intent=").append(shortPart(ep.intent))
                        .append("; action=").append(shortPart(ep.action))
                        .append("; result=").append(shortPart(ep.result))
                        .append("; reward=").append(ep.reward)
                        .append("\n");
            }
        }
        if (sb.length() == 0) return "";
        sb.append("Используй это как подсказку, но проверяй tools по факту.");
        return sb.toString();
    }

    private static String shortPart(String s) {
        if (s == null) return "";
        if (s.length() <= 120) return s;
        return s.substring(0, 120) + "...";
    }

    private void maybePromoteRule(String action, String intent) {
        if (action == null || action.trim().isEmpty() || intent == null || intent.trim().isEmpty()) return;
        List<String> kws = MemoryRetriever.keywords(intent);
        if (kws.isEmpty()) return;
        String key = action + "|" + kws.get(0);
        StringBuilder kw = new StringBuilder();
        for (int i = 0; i < kws.size() && i < 3; i++) {
            if (i > 0) kw.append(", ");
            kw.append(kws.get(i));
        }
        String rule = "Если запрос содержит [" + kw + "], сначала пробуй tool " + action + ".";
        memoryStore.upsertRule(key, rule, 0.2);
    }

    private static String sanitizeAssistantContent(String text) {
        if (text == null) return "";
        String[] lines = text.split("\\r?\\n");
        StringBuilder out = new StringBuilder();
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;
            if (looksLikeToolCallLine(line)) continue;
            if (looksLikeJsonLine(line)) continue;
            line = line.replaceAll("\\[[a-zA-Z_][a-zA-Z0-9_]*\\s*\\([^\\]]*\\)\\]", "").trim();
            int jsonAt = line.indexOf("{\"");
            if (jsonAt >= 0) {
                line = line.substring(0, jsonAt).trim();
            }
            if (line.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        return out.toString().trim();
    }

    private static boolean looksLikeToolCallLine(String line) {
        if (line == null) return false;
        String s = line.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) return false;
        return s.contains("(") && s.contains(")");
    }

    private static boolean looksLikeJsonLine(String line) {
        if (line == null) return false;
        String s = line.trim();
        if (s.isEmpty()) return false;
        return (s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[{") && s.endsWith("}]"));
    }
}
