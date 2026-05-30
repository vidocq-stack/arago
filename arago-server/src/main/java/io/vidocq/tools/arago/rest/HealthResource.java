package io.vidocq.tools.arago.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Minimal liveness endpoint, mounted under the Cassini {@code /api} prefix → {@code GET /api/health}.
 *
 * <p>Stop-gap for Phase 0: the real MicroProfile Health implementation (knock) is temporarily
 * disabled because its build-compatible extension is not yet aligned with the vauban 0.1.0 release
 * (see BUG.md ARAGO-003). Once the stack is realigned, this is replaced by knock's {@code /health}.
 */
@ApplicationScoped
@Path("/health")
public class HealthResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Health get() {
        return new Health("UP");
    }
}
