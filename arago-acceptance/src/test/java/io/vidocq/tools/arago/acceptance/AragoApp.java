package io.vidocq.tools.arago.acceptance;

import io.vidocq.runtime.core.VidocqBootstrap;
import io.vidocq.tools.arago.auth.PasswordHasher;
import jakarta.json.Json;
import jakarta.json.JsonReader;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.StringReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

    /** Realm imported into the Keycloak container; see {@code keycloak/arago-realm.json}. */
    public static final String KEYCLOAK_REALM = "arago";
    private static final String KEYCLOAK_CLIENT = "arago-test";

    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> keycloak;
    private static String keycloakBaseUrl; // http://localhost:<mappedPort>, also the token issuer host
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

        // Keycloak (OIDC issuer) — started BEFORE the runtime boot so its issuer/JWKS URLs can be
        // injected as mp.jwt.verify.* system properties. With OIDC enabled, cervantes/MP-JWT owns the
        // Authorization: Bearer scheme; the superadmin token rides X-Arago-Admin instead (see ARAGO-004).
        keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:26.0")
                .withExposedPorts(8080)
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("keycloak/arago-realm.json"),
                        "/opt/keycloak/data/import/arago-realm.json")
                .withCommand("start-dev", "--import-realm")
                .waitingFor(Wait.forHttp("/realms/" + KEYCLOAK_REALM).forPort(8080).forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        keycloak.start();
        // Use the Testcontainers-reported host (not a hardcoded localhost — on some Docker setups the
        // mapped port is only reachable via getHost(), e.g. not ::1). Keycloak derives its token `iss`
        // from this request host, so issuer + JWKS + token all agree on the same origin.
        keycloakBaseUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);

        int port = freePort();
        baseUrl = "http://localhost:" + port;

        System.setProperty("vidocq.pool.url", postgres.getJdbcUrl());
        System.setProperty("vidocq.pool.username", postgres.getUsername());
        System.setProperty("vidocq.pool.password", postgres.getPassword());
        System.setProperty("vidocq.chappe.listener.default.port", String.valueOf(port));

        // OIDC: cervantes validates Keycloak Bearer tokens against this issuer + JWKS. Keycloak derives
        // its `iss` from the request host (localhost:<mappedPort>), so issuer and JWKS share that origin.
        String issuer = keycloakBaseUrl + "/realms/" + KEYCLOAK_REALM;
        System.setProperty("mp.jwt.verify.issuer", issuer);
        System.setProperty("mp.jwt.verify.publickey.location",
                issuer + "/protocol/openid-connect/certs");

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

    /**
     * Obtains a Keycloak access token for a realm user via the OAuth2 password (direct access) grant.
     * The returned token is a Keycloak-signed RS256 JWT to put on {@code Authorization: Bearer} — the
     * Bearer scheme cervantes validates (distinct from the superadmin's {@code X-Arago-Admin} token).
     */
    public static String keycloakToken(String username) {
        if (keycloakBaseUrl == null) {
            throw new IllegalStateException("AragoApp.start() must be called first");
        }
        String form = "grant_type=password"
                + "&client_id=" + KEYCLOAK_CLIENT
                + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=pw"
                + "&scope=" + URLEncoder.encode("openid email profile", StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(keycloakBaseUrl + "/realms/" + KEYCLOAK_REALM
                                + "/protocol/openid-connect/token"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> r = HttpClient.newHttpClient()
                    .send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new IllegalStateException("Keycloak token request failed (" + r.statusCode()
                        + ") for " + username + ": " + r.body());
            }
            try (JsonReader reader = Json.createReader(new StringReader(r.body()))) {
                return reader.readObject().getString("access_token");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot obtain Keycloak token for " + username, e);
        }
    }

    public static synchronized void stop() {
        try {
            if (runtime != null) {
                runtime.shutdown();
                runtime = null;
            }
        } finally {
            try {
                if (keycloak != null) {
                    keycloak.stop();
                    keycloak = null;
                }
            } finally {
                if (postgres != null) {
                    postgres.stop();
                    postgres = null;
                }
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
