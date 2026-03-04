package nurgling.agent.llm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class OpenAIChatClient {
    private final HttpClient http;
    private static final ProxySelector NO_PROXY = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return Collections.singletonList(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // no-op
        }
    };

    public OpenAIChatClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(NO_PROXY)
                .build();
    }

    public LLMResponse chat(String baseUrl,
                            String apiKey,
                            String model,
                            List<LLMMessage> messages,
                            JSONArray tools,
                            double temperature,
                            int maxTokens,
                            int timeoutMs) throws IOException, InterruptedException {
        String normalized = normalizeBaseUrl(baseUrl);
        URI base = URI.create(normalized);
        String host = base.getHost();
        int port = (base.getPort() > 0) ? base.getPort() : ("https".equalsIgnoreCase(base.getScheme()) ? 443 : 80);
        if (!checkTcp(host, port, 3000)) {
            throw new IOException("TCP connect failed to " + host + ":" + port + " (request not sent)");
        }
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);
        payload.put("messages", toJsonMessages(messages));
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
            payload.put("tool_choice", "auto");
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(normalized + "/v1/chat/completions"))
                .timeout(Duration.ofMillis(Math.max(timeoutMs, 1000)))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            rb.header("Authorization", "Bearer " + apiKey.trim());
        }

        HttpResponse<String> response;
        try {
            response = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new IOException("LLM timeout: url=" + normalized + "/v1/chat/completions, model=" + model + ", timeoutMs=" + timeoutMs, e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("LLM HTTP " + response.statusCode() + ": " + response.body());
        }
        return parseResponse(response.body());
    }

    private static JSONArray toJsonMessages(List<LLMMessage> messages) {
        JSONArray arr = new JSONArray();
        for (LLMMessage msg : messages) {
            JSONObject o = new JSONObject();
            o.put("role", msg.role);
            o.put("content", msg.content == null ? "" : msg.content);
            if (msg.name != null && !msg.name.trim().isEmpty()) {
                o.put("name", msg.name);
            }
            if (msg.toolCallId != null && !msg.toolCallId.trim().isEmpty()) {
                o.put("tool_call_id", msg.toolCallId);
            }
            if (msg.toolCalls != null && !msg.toolCalls.isEmpty()) {
                o.put("tool_calls", msg.toolCalls);
            }
            arr.put(o);
        }
        return arr;
    }

    private static LLMResponse parseResponse(String body) {
        JSONObject root = new JSONObject(body);
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("LLM response has no choices");
        }
        JSONObject first = choices.getJSONObject(0);
        JSONObject message = first.optJSONObject("message");
        if (message == null) {
            throw new IllegalStateException("LLM response missing message");
        }

        LLMResponse out = new LLMResponse();
        out.content = message.optString("content", "");
        out.finishReason = first.optString("finish_reason", "");
        JSONObject usage = root.optJSONObject("usage");
        if (usage != null) {
            out.promptTokens = usage.optInt("prompt_tokens", 0);
            out.completionTokens = usage.optInt("completion_tokens", 0);
            out.totalTokens = usage.optInt("total_tokens", 0);
        }

        JSONArray toolCalls = message.optJSONArray("tool_calls");
        if (toolCalls != null) {
            for (int i = 0; i < toolCalls.length(); i++) {
                JSONObject tc = toolCalls.getJSONObject(i);
                JSONObject fn = tc.optJSONObject("function");
                if (fn == null) continue;
                out.toolCalls.add(new LLMToolCall(
                        tc.optString("id", "tc_" + i),
                        fn.optString("name", ""),
                        fn.optString("arguments", "{}")
                ));
            }
        }
        return out;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String url = (baseUrl == null || baseUrl.trim().isEmpty()) ? "http://127.0.0.1:1234" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/v1")) {
            url = url.substring(0, url.length() - 3);
        }
        return url;
    }

    private static boolean checkTcp(String host, int port, int timeoutMs) {
        if (host == null || host.isEmpty()) return false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
