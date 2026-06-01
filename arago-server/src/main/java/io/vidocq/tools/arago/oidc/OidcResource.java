package io.vidocq.tools.arago.oidc;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;

/**
 * OIDC identity endpoint (cf. arago-spec §4.2/§4.8). Mounted under {@code /api} by Cassini →
 * {@code GET /api/oidc/me}. Requires a valid Keycloak Bearer token (validated by cervantes/MP-JWT,
 * which sets the JAX-RS {@link SecurityContext} principal to the {@link JsonWebToken}); then enforces
 * the local allowlist:
 * <ul>
 *   <li>no/invalid token → {@code 401};</li>
 *   <li>valid token whose email is not provisioned (or disabled) → {@code 403 speaker_not_provisioned};</li>
 *   <li>provisioned → {@code 200} with the resolved speaker identity (sub bound on first login).</li>
 * </ul>
 *
 * <p>The token is read via {@code @Context SecurityContext} (JAX-RS-native, handled by Cassini)
 * rather than {@code @Inject JsonWebToken} — the latter is a cervantes producer that the build-time
 * Vauban indexer does not see across the dependency boundary.</p>
 */
@RequestScoped
@Path("/oidc")
public class OidcResource {

    @Context
    SecurityContext securityContext;

    @Inject
    SpeakerAllowlist allowlist;

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    public Response me() {
        Principal principal = securityContext == null ? null : securityContext.getUserPrincipal();
        if (!(principal instanceof JsonWebToken jwt)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build();
        }
        String email = emailClaim(jwt);
        String sub = jwt.getSubject();
        return allowlist.authorize(email, sub)
                .map(s -> Response.ok(new MeView(s.getEmail(), s.getRole().name(), s.getOidcSub())).build())
                .orElseGet(() -> Response.status(Response.Status.FORBIDDEN)
                        .entity(new ErrorView("speaker_not_provisioned")).build());
    }

    /** Reads the {@code email} claim robustly (cervantes may expose it as a String or a JsonString). */
    private static String emailClaim(JsonWebToken token) {
        Object raw = token.getClaim("email");
        return switch (raw) {
            case null -> null;
            case String s -> s;
            case jakarta.json.JsonString js -> js.getString();
            default -> raw.toString();
        };
    }
}
