package io.vidocq.tools.arago.oidc;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code GET /api/oidc/login} must fail soft when no identity provider is configured (e.g. the local
 * dev/demo compose without Keycloak): redirect back to the SPA with {@code ?oidc_error=...} instead of
 * letting {@code arago.oidc.issuer}'s absence surface as a 500.
 */
class OidcLoginResourceTest {

    /** A client that reports OIDC as unconfigured without touching MicroProfile Config. */
    private static OidcLoginResource resourceWithUnconfiguredOidc() {
        var resource = new OidcLoginResource();
        resource.keycloak = new KeycloakOidcClient() {
            @Override
            public boolean isConfigured() {
                return false;
            }
        };
        return resource;
    }

    @Test
    void loginWithoutOidcConfigRedirectsWithError() {
        Response response = resourceWithUnconfiguredOidc().login("/speaker");
        assertEquals(Response.Status.FOUND.getStatusCode(), response.getStatus());
        assertEquals("/speaker?oidc_error=oidc_not_configured",
                response.getHeaderString(HttpHeaders.LOCATION));
    }

    @Test
    void loginSanitizesUnsafeReturnBeforeFailingSoft() {
        Response response = resourceWithUnconfiguredOidc().login("//evil.example/x");
        assertEquals("/?oidc_error=oidc_not_configured",
                response.getHeaderString(HttpHeaders.LOCATION));
    }
}
