package io.vidocq.tools.arago.speaker;

import io.vidocq.tools.arago.admin.LoginRateLimiter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Local speaker login (cf. arago-spec §4.2) — replaces the former Keycloak/OIDC redirect. Mounted under
 * {@code /api} by Cassini → {@code POST /api/speaker/login}. On valid credentials returns an HS256 token
 * plus the resolved identity; on bad credentials {@code 401}; when no signing secret is configured the
 * endpoint is disabled and returns {@code 503}.
 *
 * <p>Brute force is throttled per client IP via the shared {@link LoginRateLimiter} (5/min/IP, §10.2).</p>
 */
@ApplicationScoped
@Path("/speaker")
public class SpeakerLoginResource {

    @Inject
    SpeakerAuth auth;

    @Inject
    LoginRateLimiter rateLimiter;

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(SpeakerLoginRequest request,
                          @HeaderParam("X-Forwarded-For") String forwardedFor) {
        String clientKey = (forwardedFor == null || forwardedFor.isBlank())
                ? "unknown" : forwardedFor.split(",")[0].trim();
        if (!rateLimiter.allow("speaker:" + clientKey)) {
            return Response.status(429).build();
        }
        if (!auth.enabled()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        String email = request == null ? null : request.email();
        String password = request == null ? null : request.password();
        return auth.login(email, password)
                .map(resp -> Response.ok(resp).build())
                .orElseGet(() -> Response.status(Response.Status.UNAUTHORIZED)
                        .entity(new ErrorView("invalid_credentials")).build());
    }
}
