package nurgling.agent.runtime;

import java.util.concurrent.atomic.AtomicLong;

public class AgentSessionStats {
    private static final AtomicLong sessionPromptTokens = new AtomicLong(0);
    private static final AtomicLong sessionCompletionTokens = new AtomicLong(0);
    private static final AtomicLong sessionTotalTokens = new AtomicLong(0);
    private static final AtomicLong llmRequests = new AtomicLong(0);
    private static final AtomicLong toolCalls = new AtomicLong(0);
    private static final AtomicLong toolSuccess = new AtomicLong(0);
    private static final AtomicLong toolErrors = new AtomicLong(0);
    private static final AtomicLong toolRounds = new AtomicLong(0);

    private AgentSessionStats() {
    }

    public static void recordUsage(int promptTokens, int completionTokens, int totalTokens) {
        sessionPromptTokens.addAndGet(Math.max(0, promptTokens));
        sessionCompletionTokens.addAndGet(Math.max(0, completionTokens));
        sessionTotalTokens.addAndGet(Math.max(0, totalTokens));
        llmRequests.incrementAndGet();
    }

    public static void recordToolResult(boolean ok) {
        toolCalls.incrementAndGet();
        if (ok) {
            toolSuccess.incrementAndGet();
        } else {
            toolErrors.incrementAndGet();
        }
    }

    public static void recordToolRound() {
        toolRounds.incrementAndGet();
    }

    public static long getSessionPromptTokens() {
        return sessionPromptTokens.get();
    }

    public static long getSessionCompletionTokens() {
        return sessionCompletionTokens.get();
    }

    public static long getSessionTotalTokens() {
        return sessionTotalTokens.get();
    }

    public static long getLlmRequests() {
        return llmRequests.get();
    }

    public static long getToolCalls() {
        return toolCalls.get();
    }

    public static long getToolSuccess() {
        return toolSuccess.get();
    }

    public static long getToolErrors() {
        return toolErrors.get();
    }

    public static long getToolRounds() {
        return toolRounds.get();
    }

    public static double getToolSuccessRate() {
        long total = toolCalls.get();
        if (total <= 0) return 0.0;
        return (double) toolSuccess.get() / (double) total;
    }

    public static void resetTokenCounters() {
        sessionPromptTokens.set(0);
        sessionCompletionTokens.set(0);
        sessionTotalTokens.set(0);
    }

    public static void resetAll() {
        resetTokenCounters();
        llmRequests.set(0);
        toolCalls.set(0);
        toolSuccess.set(0);
        toolErrors.set(0);
        toolRounds.set(0);
    }
}
