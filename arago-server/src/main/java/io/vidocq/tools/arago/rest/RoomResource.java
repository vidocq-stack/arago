package io.vidocq.tools.arago.rest;

import io.vidocq.tools.arago.oidc.SpeakerAllowlist;
import io.vidocq.tools.arago.persistence.AttendeeProfile;
import io.vidocq.tools.arago.persistence.AttendeeProfileRepository;
import io.vidocq.tools.arago.persistence.Room;
import io.vidocq.tools.arago.persistence.RoomMode;
import io.vidocq.tools.arago.persistence.RoomRepository;
import io.vidocq.tools.arago.persistence.RoomStatus;
import io.vidocq.tools.arago.rooms.AttendeeTokens;
import io.vidocq.tools.arago.rooms.CreateRoomRequest;
import io.vidocq.tools.arago.rooms.JoinRequest;
import io.vidocq.tools.arago.rooms.JoinResponse;
import io.vidocq.tools.arago.rooms.PinGenerator;
import io.vidocq.tools.arago.rooms.RoomView;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Room lifecycle for speakers (cf. arago-spec §4.1, §8). Mounted under {@code /api} by Cassini →
 * {@code /api/rooms}. Every mutating/owning endpoint requires a valid Keycloak Bearer (validated by
 * cervantes/MP-JWT, which sets the {@link SecurityContext} principal to the {@link JsonWebToken})
 * AND a provisioned speaker in the local allowlist; the room's owner is the speaker's OIDC subject.
 *
 * <ul>
 *   <li>{@code POST /api/rooms} — create an ACTIVE room with a fresh PIN;</li>
 *   <li>{@code GET  /api/rooms} — list the caller's rooms (most recent first);</li>
 *   <li>{@code GET  /api/rooms/{id}} — detail (owner only);</li>
 *   <li>{@code POST /api/rooms/{id}/end} — end the room (owner only);</li>
 *   <li>{@code GET  /api/rooms/count} — public counters (Phase 0, feeds the metrics gauge).</li>
 * </ul>
 *
 * <p>{@code @RequestScoped} because the per-request {@link SecurityContext} is read via
 * {@code @Context} (see {@code OidcResource} for the same pattern).</p>
 */
@RequestScoped
@Path("/rooms")
public class RoomResource {

    @Context
    SecurityContext securityContext;

    @Inject
    RoomRepository rooms;

    @Inject
    PinGenerator pins;

    @Inject
    SpeakerAllowlist allowlist;

    @Inject
    AttendeeProfileRepository attendees;

    @Inject
    AttendeeTokens attendeeTokens;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(CreateRoomRequest request) {
        String ownerSub = requireProvisionedSpeaker();
        if (request == null || request.title() == null || request.title().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        RoomMode mode = request.mode() == null ? RoomMode.CONF : request.mode();
        Room room = new Room(UUID.randomUUID().toString(), pins.next(), request.title().trim(),
                RoomStatus.ACTIVE, mode, ownerSub, Instant.now());
        Room saved = rooms.save(room);
        return Response.status(Response.Status.CREATED).entity(RoomView.of(saved)).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listMine() {
        String ownerSub = requireProvisionedSpeaker();
        List<RoomView> views = rooms.findByOwnerSubOrderByCreatedAtDesc(ownerSub)
                .stream().map(RoomView::of).toList();
        return Response.ok(views).build();
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response detail(@PathParam("id") String id) {
        String ownerSub = requireProvisionedSpeaker();
        return rooms.findById(id)
                .map(room -> ownerSub.equals(room.getOwnerSub())
                        ? Response.ok(RoomView.of(room)).build()
                        : Response.status(Response.Status.FORBIDDEN).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Path("/{id}/end")
    @Produces(MediaType.APPLICATION_JSON)
    public Response end(@PathParam("id") String id) {
        String ownerSub = requireProvisionedSpeaker();
        return rooms.findById(id).map(room -> {
            if (!ownerSub.equals(room.getOwnerSub())) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
            if (room.getStatus() != RoomStatus.ENDED) {
                room.setStatus(RoomStatus.ENDED);
                room.setEndedAt(Instant.now());
                rooms.save(room);
            }
            return Response.ok(RoomView.of(room)).build();
        }).orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Public room join (cf. arago-spec §4.2): no authentication. An attendee provides the PIN and a
     * pseudo (mandatory) and optionally an email (then consent is mandatory). Returns the attendee's
     * HS256 token + room id/mode. {@code 404} if the PIN matches no joinable (ACTIVE) room;
     * {@code 400} on a missing pseudo or an email without accepted consent.
     */
    @POST
    @Path("/join")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response join(JoinRequest request) {
        if (request == null || request.pin() == null || request.pin().isBlank()
                || request.pseudo() == null || request.pseudo().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        Room room = rooms.findByPin(request.pin().trim()).orElse(null);
        if (room == null || room.getStatus() != RoomStatus.ACTIVE) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String pseudo = request.pseudo().trim();

        String profileId = null;
        if (request.email() != null && !request.email().isBlank()) {
            if (!Boolean.TRUE.equals(request.consentAccepted())) {
                return Response.status(Response.Status.BAD_REQUEST).build(); // §4.7: consent required with email
            }
            profileId = upsertProfile(request.email().trim().toLowerCase(), pseudo,
                    request.consentTextVersion());
        }

        String token = attendeeTokens.issue(room.getId(), pseudo, profileId);
        return Response.ok(new JoinResponse(room.getId(), room.getMode().name(), token, profileId)).build();
    }

    /** Reuses an attendee profile by email, or creates one; records the consent version + timestamp. */
    private String upsertProfile(String email, String pseudo, String consentTextVersion) {
        AttendeeProfile profile = attendees.findByEmail(email).orElseGet(
                () -> new AttendeeProfile(UUID.randomUUID().toString(), email, pseudo,
                        null, null, Instant.now()));
        profile.setPseudo(pseudo);
        profile.setConsentTextVersion(consentTextVersion);
        profile.setConsentAt(Instant.now());
        return attendees.save(profile).getId();
    }

    @GET
    @Path("/count")
    @Produces(MediaType.APPLICATION_JSON)
    public RoomCounts count() {
        return new RoomCounts(rooms.count(), rooms.countByStatus(RoomStatus.ACTIVE));
    }

    /**
     * Resolves the authenticated, allowlisted speaker's OIDC subject, or aborts: {@code 401} if no
     * valid token (no JWT principal), {@code 403} if the identity is not provisioned/enabled.
     */
    private String requireProvisionedSpeaker() {
        Principal principal = securityContext == null ? null : securityContext.getUserPrincipal();
        if (!(principal instanceof JsonWebToken jwt)) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
        if (allowlist.authorize(emailClaim(jwt), jwt.getSubject()).isEmpty()) {
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }
        return jwt.getSubject();
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
