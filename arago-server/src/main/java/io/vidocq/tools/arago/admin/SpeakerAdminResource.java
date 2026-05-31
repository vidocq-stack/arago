package io.vidocq.tools.arago.admin;

import io.vidocq.tools.arago.persistence.Role;
import io.vidocq.tools.arago.persistence.Speaker;
import io.vidocq.tools.arago.persistence.SpeakerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Superadmin management of the speaker allowlist (cf. arago-spec §4.8, §8). Mounted under {@code /api}
 * by Cassini → {@code /api/admin/speakers}. Every method requires a valid superadmin token
 * ({@link AdminAuthenticator}); without it the call is {@code 401}.
 */
@ApplicationScoped
@Path("/admin/speakers")
public class SpeakerAdminResource {

    @Inject
    SpeakerRepository speakers;

    @Inject
    AdminAuthenticator auth;

    @Inject
    AdminAuditService audit;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@HeaderParam(HttpHeaders.AUTHORIZATION) String authz) {
        if (auth.authenticate(authz).isEmpty()) {
            return unauthorized();
        }
        List<SpeakerView> views = speakers.findAll().map(SpeakerAdminResource::view).toList();
        return Response.ok(views).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@HeaderParam(HttpHeaders.AUTHORIZATION) String authz,
                           @HeaderParam("X-Forwarded-For") String forwardedFor,
                           CreateSpeakerRequest request) {
        if (auth.authenticate(authz).isEmpty()) {
            return unauthorized();
        }
        if (request == null || request.email() == null || request.email().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        String email = request.email().trim().toLowerCase();
        if (speakers.findByEmail(email).isPresent()) {
            return Response.status(Response.Status.CONFLICT).build();
        }
        Role role = request.role() == null ? Role.SPEAKER : request.role();
        Speaker speaker = new Speaker(UUID.randomUUID().toString(), email, role, true,
                request.displayName(), "superadmin", Instant.now());
        Speaker saved = speakers.save(speaker);
        audit.record("speaker.create", saved.getId(), forwardedFor);
        return Response.status(Response.Status.CREATED).entity(view(saved)).build();
    }

    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@HeaderParam(HttpHeaders.AUTHORIZATION) String authz,
                           @HeaderParam("X-Forwarded-For") String forwardedFor,
                           @PathParam("id") String id, UpdateSpeakerRequest request) {
        if (auth.authenticate(authz).isEmpty()) {
            return unauthorized();
        }
        return speakers.findById(id).map(speaker -> {
            if (request != null) {
                if (request.role() != null) {
                    speaker.setRole(request.role());
                }
                if (request.enabled() != null) {
                    speaker.setEnabled(request.enabled());
                }
                if (request.displayName() != null) {
                    speaker.setDisplayName(request.displayName());
                }
            }
            Response ok = Response.ok(view(speakers.save(speaker))).build();
            audit.record("speaker.update", id, forwardedFor);
            return ok;
        }).orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@HeaderParam(HttpHeaders.AUTHORIZATION) String authz,
                           @HeaderParam("X-Forwarded-For") String forwardedFor,
                           @PathParam("id") String id) {
        if (auth.authenticate(authz).isEmpty()) {
            return unauthorized();
        }
        if (speakers.findById(id).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        speakers.deleteById(id);
        audit.record("speaker.delete", id, forwardedFor);
        return Response.noContent().build();
    }

    private static Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build();
    }

    private static SpeakerView view(Speaker s) {
        return new SpeakerView(s.getId(), s.getEmail(), s.getRole(), s.isEnabled(),
                s.getDisplayName(), s.getOidcSub());
    }
}
