package io.vidocq.tools.arago.admin;

import io.vidocq.tools.arago.auth.SuperadminAuth;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Superadmin local login (cf. arago-spec §4.2/§4.8). Mounted under {@code /api} by Cassini, so the
 * effective path is {@code POST /api/admin/login}. On valid credentials returns a short HS256 token
 * ({@code role=superadmin}); on bad credentials {@code 401}; when no password hash/secret is
 * configured the endpoint is disabled and returns {@code 503} (no implicit default account).
 *
 * <p>Credentials live only in configuration (env vars / system properties), never in the database.
 * Config is read programmatically via {@link ConfigProvider} (resolved by ravel) rather than
 * {@code @ConfigProperty} injection, which would require the ravel-cdi-vauban build integration.
 * Rate-limiting and audit logging (spec §10.2) are layered in a following increment.</p>
 */
@ApplicationScoped
@Path("/admin")
public class AdminLoginResource {

    private volatile SuperadminAuth auth;

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(LoginRequest request) {
        SuperadminAuth a = auth();
        if (!a.enabled()) {
            // No hash/secret configured: feature off, no implicit credentials (spec §4.8/§10.2).
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        String user = request == null ? null : request.username();
        String pass = request == null ? null : request.password();
        return a.login(user, pass)
                .map(token -> Response.ok(new LoginResponse(token)).build())
                .orElseGet(() -> Response.status(Response.Status.UNAUTHORIZED).build());
    }

    /** Lazily builds the auth helper from MP Config (read once; values are static after boot). */
    private SuperadminAuth auth() {
        SuperadminAuth a = auth;
        if (a == null) {
            synchronized (this) {
                a = auth;
                if (a == null) {
                    Config cfg = ConfigProvider.getConfig();
                    auth = a = new SuperadminAuth(
                            cfg.getOptionalValue("arago.superadmin.username", String.class).orElse(null),
                            cfg.getOptionalValue("arago.superadmin.password-hash", String.class).orElse(null),
                            cfg.getOptionalValue("arago.superadmin.token-ttl-minutes", Integer.class).orElse(30),
                            cfg.getOptionalValue("arago.attendee.hmac-secret", String.class).orElse(null));
                }
            }
        }
        return a;
    }
}
