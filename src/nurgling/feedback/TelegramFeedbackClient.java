package nurgling.feedback;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TelegramFeedbackClient implements FeedbackSender {
    private static final String TELEGRAM_API_ROOT = "https://api.telegram.org/bot";

    private final FeedbackConfig config;
    private final String apiRoot;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public TelegramFeedbackClient(FeedbackConfig config, int connectTimeoutMs, int readTimeoutMs) {
        this(config, TELEGRAM_API_ROOT, connectTimeoutMs, readTimeoutMs);
    }

    TelegramFeedbackClient(FeedbackConfig config, String apiRoot, int connectTimeoutMs, int readTimeoutMs) {
        if(config == null || !config.configured())
            throw new IllegalArgumentException("Feedback is not configured");
        this.config = config;
        this.apiRoot = apiRoot;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public void sendMessage(String text) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("chat_id", config.chatId());
        fields.put("text", text);
        byte[] body = encodeForm(fields).getBytes(StandardCharsets.UTF_8);
        post("sendMessage", "application/x-www-form-urlencoded; charset=UTF-8", body);
    }

    @Override
    public void sendPhoto(byte[] png, String filename, String caption) throws IOException {
        String boundary = "NurglingBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeField(body, boundary, "chat_id", config.chatId());
        writeField(body, boundary, "caption", caption);
        writeAscii(body, "--" + boundary + "\r\n");
        writeAscii(body, "Content-Disposition: form-data; name=\"photo\"; filename=\""
                + safeFilename(filename) + "\"\r\n");
        writeAscii(body, "Content-Type: image/png\r\n\r\n");
        body.write(png);
        writeAscii(body, "\r\n--" + boundary + "--\r\n");
        post("sendPhoto", "multipart/form-data; boundary=" + boundary, body.toByteArray());
    }

    private void post(String method, String contentType, byte[] body) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(apiRoot + config.botToken() + "/" + method).openConnection();
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType);
            connection.setFixedLengthStreamingMode(body.length);
            try(OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            drain(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
            if(status < 200 || status >= 300)
                throw new TelegramStatusException(status);
        } catch(TelegramStatusException failure) {
            throw new IOException("Telegram returned HTTP " + failure.status);
        } catch(IOException | RuntimeException failure) {
            throw new IOException("Telegram request failed");
        } finally {
            if(connection != null)
                connection.disconnect();
        }
    }

    private static String encodeForm(Map<String, String> fields) {
        StringBuilder encoded = new StringBuilder();
        for(Map.Entry<String, String> field : fields.entrySet()) {
            if(encoded.length() > 0)
                encoded.append('&');
            encoded.append(urlEncode(field.getKey())).append('=').append(urlEncode(field.getValue()));
        }
        return encoded.toString();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch(java.io.UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void writeField(ByteArrayOutputStream output, String boundary, String name, String value)
            throws IOException {
        writeAscii(output, "--" + boundary + "\r\n");
        writeAscii(output, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        output.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        writeAscii(output, "\r\n");
    }

    private static void writeAscii(ByteArrayOutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.US_ASCII));
    }

    private static String safeFilename(String filename) {
        return filename == null ? "screenshot.png" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void drain(InputStream input) throws IOException {
        if(input == null)
            return;
        try(InputStream stream = input) {
            byte[] buffer = new byte[2048];
            while(stream.read(buffer) >= 0) {
                // The response body is intentionally discarded so it cannot leak the token.
            }
        }
    }

    private static final class TelegramStatusException extends IOException {
        final int status;
        TelegramStatusException(int status) { this.status = status; }
    }
}
