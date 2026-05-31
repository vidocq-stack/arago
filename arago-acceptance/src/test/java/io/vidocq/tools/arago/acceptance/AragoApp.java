package io.vidocq.tools.arago.acceptance;

import io.vidocq.runtime.core.VidocqBootstrap;
import io.vidocq.tools.arago.auth.PasswordHasher;
import org.testcontainers.containers.PostgreSQLContainer;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Boots the Arago runtime once, in-process, against a fresh PostgreSQL Testcontainer, and exposes
 * its base URL to the steps. Idempotent and process-wide: the first scenario starts it, a JVM
 * shutdown hook (and an explicit {@link #stop()}) tears it down.
 *
 * <p>The runtime is booted on the test class path (no module path) via {@link VidocqBootstrap}.
 * DB coordinates and the HTTP port are injected as system properties, which override
 * {@code vidocq.properties} (MP Config system-properties source, ordinal 400 &gt; 105). Flyway
 * migrations run at boot, so the container starts with the full schema.</p>
 */
public final class AragoApp {

    /** Superadmin test credentials injected at boot (referenced verbatim by admin.feature). */
    public static final String SUPERADMIN_USER = "root";
    public static final String SUPERADMIN_PASSWORD = "correct-horse-battery";
    private static final String HMAC_SECRET = "0123456789abcdef0123456789abcdef"; // 32 bytes

    private static PostgreSQLContainer<?> postgres;
    private static VidocqBootstrap runtime;
    private static String baseUrl;

    private AragoApp() {}

    public static synchronized String start() {
        if (runtime != null) {
            return baseUrl;
        }
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("arago")
                .withUsername("arago")
                .withPassword("arago");
        postgres.start();

        int port = freePort();
        baseUrl = "http://localhost:" + port;

        System.setProperty("vidocq.pool.url", postgres.getJdbcUrl());
        System.setProperty("vidocq.pool.username", postgres.getUsername());
        System.setProperty("vidocq.pool.password", postgres.getPassword());
        System.setProperty("vidocq.chappe.listener.default.port", String.valueOf(port));

        // Superadmin break-glass account (spec §4.8): credentials only via config — here as system
        // properties (ordinal 400, override vidocq.properties). The hash is computed from the known
        // test password so admin.feature can log in.
        System.setProperty("arago.superadmin.username", SUPERADMIN_USER);
        System.setProperty("arago.superadmin.password-hash",
                new PasswordHasher().hash(SUPERADMIN_PASSWORD.toCharArray()));
        System.setProperty("arago.attendee.hmac-secret", HMAC_SECRET);

        runtime = VidocqBootstrap.create().configure().start();
        awaitHealthy(baseUrl);

        Runtime.getRuntime().addShutdownHook(new Thread(AragoApp::stop, "arago-acceptance-shutdown"));
        return baseUrl;
    }

    public static String baseUrl() {
        if (baseUrl == null) {
            throw new IllegalStateException("AragoApp.start() must be called first");
        }
        return baseUrl;
    }

    public static synchronized void stop() {
        try {
            if (runtime != null) {
                runtime.shutdown();
                runtime = null;
            }
        } finally {
            if (postgres != null) {
                postgres.stop();
                postgres = null;
            }
        }
    }

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot allocate a free port", e);
        }
    }

    private static void awaitHealthy(String base) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/health"))
                .timeout(Duration.ofSeconds(2)).GET().build();
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            try {
                HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
                // server not up yet
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Arago", e);
            }
        }
        throw new IllegalStateException("Arago did not become healthy within 60s at " + base);
    }
}
