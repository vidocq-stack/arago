package io.vidocq.tools.arago.oidc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonReader;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Minimal, zero-dependency OIDC client for the Authorization Code + PKCE front-channel (cf. arago-spec
 * §7/§8). Talks to Keycloak with the JDK {@link HttpClient}; JSON parsed via JSON-P (champollion).
 *
 * <p>Two steps:
 * <ol>
 *   <li>{@link #authorizeUrl} — the browser-facing redirect to Keycloak's {@code /auth} endpoint
 *       (public client {@code arago-web}, {@code code_challenge_method=S256}).</li>
 *   <li>{@link #exchangeCode} — the back-channel POST to {@code /token} with the PKCE
 *       {@code code_verifier} (no client secret: {@code arago-web} is a public client).</li>
 * </ol>
 *
 * <p>Config is read programmatically via {@link ConfigProvider} (not {@code @ConfigProperty}, which the
 * build-time Vauban indexer does not resolve here): {@code arago.oidc.issuer}, {@code arago.oidc.web-client-id}
 * (default {@code arago-web}), {@code arago.public.url} (base for the {@code redirect_uri}).</p>
 */
@ApplicationScoped
public class KeycloakOidcClient {

    private static final String CALLBACK_PATH = "/api/oidc/callback";
    private static final String SCOPE = "openid email profile";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Authorization endpoint redirect URL for the browser (response_type=code, PKCE S256). */
    public String authorizeUrl(String state, String nonce, String codeChallenge) {
        return issuer() + "/protocol/openid-connect/auth"
                + "?response_type=code"
                + "&client_id=" + enc(webClientId())
                + "&redirect_uri=" + enc(redirectUri())
                + "&scope=" + enc(SCOPE)
                + "&state=" + enc(state)
                + "&nonce=" + enc(nonce)
                + "&code_challenge=" + enc(codeChallenge)
                + "&code_challenge_method=S256";
    }

    /**
     * Exchanges an authorization {@code code} for an access token at Keycloak's token endpoint, proving
     * possession of the PKCE {@code code_verifier}. Returns the raw access token (a Keycloak RS256 JWT).
     *
     * @throws OidcExchangeException if Keycloak rejects the exchange or the response has no access token
     */
    public String exchangeCode(String code, String codeVerifier) {
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri())
                + "&client_id=" + enc(webClientId())
                + "&code_verifier=" + enc(codeVerifier);
        HttpRequest req = HttpRequest.newBuilder(URI.create(issuer() + "/protocol/openid-connect/token"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new OidcExchangeException("token endpoint unreachable", e);
        }
        if (resp.statusCode() != 200) {
            throw new OidcExchangeException("token endpoint returned " + resp.statusCode());
        }
        try (JsonReader reader = Json.createReader(new StringReader(resp.body()))) {
            String token = reader.readObject().getString("access_token", null);
            if (token == null || token.isBlank()) {
                throw new OidcExchangeException("token response had no access_token");
            }
            return token;
        }
    }

    /** Public base URL of this Arago instance (e.g. {@code https://arago.vidocq.dev}). */
    public String publicBaseUrl() {
        return trimTrailingSlash(config("arago.public.url", "http://localhost:8080"));
    }

    private String redirectUri() {
        return publicBaseUrl() + CALLBACK_PATH;
    }

    private String issuer() {
        return trimTrailingSlash(config("arago.oidc.issuer", null));
    }

    private String webClientId() {
        return config("arago.oidc.web-client-id", "arago-web");
    }

    private static String config(String key, String defaultValue) {
        String value = ConfigProvider.getConfig().getOptionalValue(key, String.class).orElse(defaultValue);
        if (value == null) {
            throw new OidcExchangeException("missing required config: " + key);
        }
        return value;
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Thrown when the OIDC code-for-token exchange (or its configuration) fails. */
    public static final class OidcExchangeException extends RuntimeException {
        public OidcExchangeException(String message) {
            super(message);
        }

        public OidcExchangeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
