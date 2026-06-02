package io.vidocq.tools.arago.oidc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The front-channel (browser-facing) issuer and the back-channel (server-to-server) issuer may differ:
 * the classic "Keycloak behind a reverse proxy" topology, and the local dev compose where the browser
 * reaches Keycloak at {@code localhost:<port>} while Arago reaches it at {@code keycloak:8080}.
 *
 * <p>{@link KeycloakOidcClient#authorizeUrl} must build on the public issuer (the browser navigates
 * there); {@link KeycloakOidcClient#tokenEndpoint()} must build on the internal issuer when one is set,
 * and fall back to the public issuer otherwise (single-host deployments + acceptance, unchanged).</p>
 */
class KeycloakOidcClientTest {

    private static final String PUBLIC = "http://localhost:8081/realms/arago";
    private static final String INTERNAL = "http://keycloak:8080/realms/arago";

    @Test
    void authorizeUrlUsesThePublicIssuer() {
        var client = new KeycloakOidcClient() {
            @Override String issuer() { return PUBLIC; }
            @Override String internalIssuer() { return INTERNAL; }
        };
        String url = client.authorizeUrl("st", "no", "ch");
        assertTrue(url.startsWith(PUBLIC + "/protocol/openid-connect/auth?"),
                "authorize URL must target the public issuer, was: " + url);
    }

    @Test
    void tokenEndpointUsesTheInternalIssuerWhenSet() {
        var client = new KeycloakOidcClient() {
            @Override String issuer() { return PUBLIC; }
            @Override String internalIssuer() { return INTERNAL; }
        };
        assertEquals(INTERNAL + "/protocol/openid-connect/token", client.tokenEndpoint());
    }

    @Test
    void tokenEndpointFallsBackToThePublicIssuerWhenNoInternalSet() {
        // internalIssuer() is NOT overridden: its default reads config (absent here) and must fall back
        // to issuer() — the single-host behaviour that acceptance and prod rely on.
        var client = new KeycloakOidcClient() {
            @Override String issuer() { return PUBLIC; }
        };
        assertEquals(PUBLIC + "/protocol/openid-connect/token", client.tokenEndpoint());
    }
}
