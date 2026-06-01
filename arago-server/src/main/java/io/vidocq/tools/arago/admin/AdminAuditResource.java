package io.vidocq.tools.arago.admin;

import io.vidocq.tools.arago.persistence.AdminAuditRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Read-only access to the superadmin audit trail (cf. arago-spec §8 {@code GET /api/admin/audit}).
 * Superadmin token required.
 */
@ApplicationScoped
@Path("/admin/audit")
public class AdminAuditResource {

    @Inject
    AdminAuditRepository audit;

    @Inject
    AdminAuthenticator auth;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@HeaderParam("X-Arago-Admin") String authz) {
        if (auth.authenticate(authz).isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build();
        }
        List<AuditView> views = audit.findAll()
                .map(a -> new AuditView(a.getId(), a.getActor(), a.getAction(), a.getTarget(),
                        a.getIpTruncated(), a.getAt() == null ? null : a.getAt().toString()))
                .toList();
        return Response.ok(views).build();
    }
}
