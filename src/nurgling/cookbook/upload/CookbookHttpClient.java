package nurgling.cookbook.upload;

import org.json.JSONArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class CookbookHttpClient {
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public CookbookHttpClient(int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public void post(String endpoint, JSONArray payload) throws IOException {
        URI uri;
        try {
            uri = new URI(endpoint == null ? "" : endpoint.trim());
        } catch (URISyntaxException e) {
            throw new IOException("Invalid cookbook endpoint", e);
        }
        String protocol = uri.getScheme();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol))
            throw new IOException("Cookbook endpoint must use HTTP or HTTPS");
        URL url = uri.toURL();

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int status = connection.getResponseCode();
            consume(status >= 200 && status < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            if (status < 200 || status >= 300)
                throw new IOException("Cookbook server returned HTTP " + status);
        } finally {
            connection.disconnect();
        }
    }

    private static void consume(InputStream input) throws IOException {
        if (input == null)
            return;
        try (InputStream stream = input) {
            byte[] buffer = new byte[1024];
            while (stream.read(buffer) >= 0) {
                // Drain the response so the HTTP connection can be released.
            }
        }
    }
}
