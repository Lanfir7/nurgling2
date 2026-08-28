package nurgling.cookbook.upload;

import com.sun.net.httpserver.HttpServer;
import nurgling.cookbook.Recipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookbookUploadServiceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null)
            server.stop(0);
    }

    @Test
    void sharingCheckboxControlsWhetherRecipesReachTheServer() throws Exception {
        AtomicInteger requests = startServer(200);
        AtomicBoolean enabled = new AtomicBoolean(false);
        AtomicReference<String> endpoint = new AtomicReference<>(endpoint());
        CookbookUploadService service = new CookbookUploadService(
                enabled::get, endpoint::get, () -> "w17",
                new CookbookHttpClient(2000, 2000), Runnable::run);

        assertFalse(service.submit(recipe("disabled")));
        service.tick(10_000);
        assertEquals(0, requests.get());

        enabled.set(true);
        assertTrue(service.submit(recipe("enabled")));
        service.tick(20_000);
        assertEquals(1, requests.get());

        enabled.set(false);
        service.settingsChanged();
        service.submit(recipe("disabled-again"));
        enabled.set(true);
        service.tick(30_000);
        assertEquals(1, requests.get());
    }

    @Test
    void failedUploadIsRetriedOnTheNextFlush() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/client/token/food", exchange -> {
            int count = requests.incrementAndGet();
            while (exchange.getRequestBody().read() >= 0) {
            }
            exchange.sendResponseHeaders(count == 1 ? 500 : 200, 0);
            exchange.getResponseBody().close();
        });
        server.start();
        CookbookUploadService service = new CookbookUploadService(
                () -> true, this::endpoint, () -> "w17",
                new CookbookHttpClient(2000, 2000), Runnable::run, ignored -> { });

        service.submit(recipe("retry"));
        service.tick(10_000);
        service.tick(20_000);

        assertEquals(2, requests.get());
    }

    @Test
    void disablingSharingAtomicallyDiscardsConcurrentSubmission() throws Exception {
        AtomicInteger requests = startServer(200);
        AtomicBoolean enabled = new AtomicBoolean(true);
        CountDownLatch genusRequested = new CountDownLatch(1);
        CountDownLatch allowSubmission = new CountDownLatch(1);
        CookbookUploadService service = new CookbookUploadService(
                enabled::get, this::endpoint, () -> {
                    genusRequested.countDown();
                    try {
                        if (!allowSubmission.await(2, TimeUnit.SECONDS))
                            throw new IllegalStateException("submission was not released");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return "w17";
                }, new CookbookHttpClient(2000, 2000), Runnable::run);

        Thread submitter = new Thread(() -> service.submit(recipe("concurrent")));
        submitter.start();
        assertTrue(genusRequested.await(2, TimeUnit.SECONDS));

        enabled.set(false);
        Thread disable = new Thread(service::settingsChanged);
        disable.start();
        disable.join();
        allowSubmission.countDown();
        submitter.join();

        enabled.set(true);
        service.tick(10_000);
        assertEquals(0, requests.get());
    }

    @Test
    void acceptedCallbackRunsOnlyInsideSuccessfulQueueCommit() {
        AtomicBoolean enabled = new AtomicBoolean(false);
        AtomicInteger accepted = new AtomicInteger();
        CookbookUploadService service = new CookbookUploadService(
                enabled::get, () -> "http://localhost/client/token/food", () -> "w17",
                new CookbookHttpClient(2000, 2000), Runnable::run);

        assertFalse(service.submit(recipe("disabled"), accepted::incrementAndGet));
        enabled.set(true);
        assertTrue(service.submit(recipe("enabled"), accepted::incrementAndGet));

        assertEquals(1, accepted.get());
    }

    private AtomicInteger startServer(int status) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/client/token/food", exchange -> {
            requests.incrementAndGet();
            while (exchange.getRequestBody().read() >= 0) {
            }
            exchange.sendResponseHeaders(status, 0);
            exchange.getResponseBody().close();
        });
        server.start();
        return requests;
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/client/token/food";
    }

    private static Recipe recipe(String hash) {
        return new Recipe(hash, hash, "gfx/invobjs/" + hash,
                1.0, 50, Collections.emptyMap(), Collections.emptyMap());
    }
}
