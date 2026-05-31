package io.vidocq.tools.arago.acceptance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * De-risking smoke test: proves the Arago runtime boots in-process on the class path (against a
 * PostgreSQL Testcontainer) and answers HTTP, before the Cucumber harness is layered on top.
 */
class BootSmokeTest {

    private static String base;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void boot() {
        // Process-wide singleton; teardown via AragoApp's JVM shutdown hook (shared with Cucumber).
        base = AragoApp.start();
    }

    @Test
    void health_is_up() throws Exception {
        HttpResponse<String> r = get("/health");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"status\""), r.body());
        assertTrue(r.body().contains("UP"), r.body());
    }

    @Test
    void rooms_count_is_zero_on_empty_db() throws Exception {
        HttpResponse<String> r = get("/api/rooms/count");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"total\""), r.body());
        assertTrue(r.body().contains("\"active\""), r.body());
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
