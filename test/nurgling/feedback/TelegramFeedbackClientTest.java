package nurgling.feedback;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramFeedbackClientTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if(server != null)
            server.stop(0);
    }

    @Test
    void postsMessageAndPngAsMultipart() throws Exception {
        AtomicReference<Request> message = new AtomicReference<>();
        AtomicReference<Request> photo = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/botsecret/sendMessage", exchange -> respond(exchange, message, 200));
        server.createContext("/botsecret/sendPhoto", exchange -> respond(exchange, photo, 200));
        server.start();
        TelegramFeedbackClient client = new TelegramFeedbackClient(
                new FeedbackConfig("secret", "20871713"), apiRoot(), 2000, 2000);

        client.sendMessage("BUG R-1\nSubject & details");
        client.sendPhoto(new byte[] {(byte)0x89, 0x50, 0x4e, 0x47}, "R-1-1.png", "R-1 1/1");

        assertEquals("POST", message.get().method);
        assertTrue(message.get().contentType.startsWith("application/x-www-form-urlencoded"));
        assertTrue(message.get().body.contains("chat_id=20871713"));
        assertTrue(message.get().body.contains("Subject+%26+details"));
        assertEquals("POST", photo.get().method);
        assertTrue(photo.get().contentType.startsWith("multipart/form-data; boundary="));
        assertTrue(photo.get().body.contains("filename=\"R-1-1.png\""));
        assertTrue(photo.get().body.contains("Content-Type: image/png"));
        assertTrue(photo.get().body.contains("R-1 1/1"));
    }

    @Test
    void httpFailureDoesNotExposeTokenOrResponseBody() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/botsecret/sendMessage", exchange -> {
            byte[] response = "secret leaked by server".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        TelegramFeedbackClient client = new TelegramFeedbackClient(
                new FeedbackConfig("secret", "20871713"), apiRoot(), 2000, 2000);

        IOException failure = assertThrows(IOException.class, () -> client.sendMessage("hello"));

        assertEquals("Telegram returned HTTP 500", failure.getMessage());
        assertFalse(failure.toString().contains("secret"));
    }

    private String apiRoot() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/bot";
    }

    private static void respond(HttpExchange exchange, AtomicReference<Request> request, int status)
            throws IOException {
        request.set(new Request(exchange.getRequestMethod(),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                new String(readAll(exchange), StandardCharsets.ISO_8859_1)));
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static byte[] readAll(HttpExchange exchange) throws IOException {
        try(ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            while((read = exchange.getRequestBody().read(buffer)) >= 0)
                output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }

    private static final class Request {
        final String method;
        final String contentType;
        final String body;

        private Request(String method, String contentType, String body) {
            this.method = method;
            this.contentType = contentType;
            this.body = body;
        }
    }
}
