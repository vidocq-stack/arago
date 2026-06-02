package io.vidocq.tools.arago.admin;

import io.vidocq.tools.arago.profile.PurgeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Superadmin trigger for the RGPD retention purge (cf. arago-spec §4.7/§8). Mounted under {@code /api}
 * by Cassini → {@code POST /api/admin/purge/run}. Idempotent. Requires a valid superadmin token
 * ({@link AdminAuthenticator}, {@code X-Arago-Admin}); without it the call is {@code 401}.
 *
 * <p>Manual-only for now — the stack has no scheduler primitive, so the spec's daily run is deferred.</p>
 */
@ApplicationScoped
@Path("/admin/purge")
public class AdminPurgeResource {

    @Inject
    AdminAuthenticator auth;

    @Inject
    AdminAuditService audit;

    @Inject
    PurgeService purge;

    @POST
    @Path("/run")
    @Produces(MediaType.APPLICATION_JSON)
    public Response run(@HeaderParam("X-Arago-Admin") String authz,
            @HeaderParam("X-Forwarded-For") String forwardedFor) {
        if (auth.authenticate(authz).isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        PurgeService.PurgeResult result = purge.run();
        audit.record("purge.run", "ephemeralChat=" + result.ephemeralChatPurged(), forwardedFor);
        return Response.ok(result).build();
    }
}
