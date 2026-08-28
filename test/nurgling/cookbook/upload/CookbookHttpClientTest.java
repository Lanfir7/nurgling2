package nurgling.cookbook.upload;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CookbookHttpClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null)
            server.stop(0);
    }

    @Test
    void postsJsonArrayToConfiguredCookbookEndpoint() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/client/token/food", exchange -> {
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();

        JSONArray payload = new JSONArray().put(new JSONObject().put("itemName", "Bread"));
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/client/token/food";

        new CookbookHttpClient(2000, 2000).post(endpoint, payload);

        assertEquals("POST", method.get());
        assertEquals("application/json; charset=UTF-8", contentType.get());
        assertEquals("Bread", new JSONArray(body.get()).getJSONObject(0).getString("itemName"));
    }

    @Test
    void rejectsNonHttpEndpointBeforeConnecting() {
        JSONArray payload = new JSONArray();

        assertThrows(IOException.class,
                () -> new CookbookHttpClient(2000, 2000).post("file:///tmp/food", payload));
    }

    private static byte[] readAll(java.io.InputStream input) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) >= 0)
            out.write(buffer, 0, read);
        return out.toByteArray();
    }
}
