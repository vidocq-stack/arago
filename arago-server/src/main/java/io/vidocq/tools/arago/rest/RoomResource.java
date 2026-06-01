package io.vidocq.tools.arago.rest;

import io.vidocq.tools.arago.oidc.SpeakerAllowlist;
import io.vidocq.tools.arago.persistence.AttendeeProfile;
import io.vidocq.tools.arago.persistence.AttendeeProfileRepository;
import io.vidocq.tools.arago.persistence.HelpRequest;
import io.vidocq.tools.arago.persistence.HelpRequestRepository;
import io.vidocq.tools.arago.persistence.HelpStatus;
import io.vidocq.tools.arago.persistence.Pin;
import io.vidocq.tools.arago.persistence.PinRepository;
import io.vidocq.tools.arago.persistence.PinType;
import io.vidocq.tools.arago.persistence.Room;
import io.vidocq.tools.arago.persistence.RoomMode;
import io.vidocq.tools.arago.persistence.RoomRepository;
import io.vidocq.tools.arago.persistence.RoomStatus;
import io.vidocq.tools.arago.rooms.AttendeeTokens;
import io.vidocq.tools.arago.rooms.CreatePinRequest;
import io.vidocq.tools.arago.rooms.CreateRoomRequest;
import io.vidocq.tools.arago.rooms.HelpView;
import io.vidocq.tools.arago.rooms.JoinRequest;
import io.vidocq.tools.arago.rooms.JoinResponse;
import io.vidocq.tools.arago.rooms.PinGenerator;
import io.vidocq.tools.arago.rooms.PinView;
import io.vidocq.tools.arago.rooms.RoomView;
import io.vidocq.tools.arago.ws.RoomSocket;
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

    @Inject
    PinRepository pinRepo;

    @Inject
    HelpRequestRepository helpRepo;

    @Inject
    RoomSocket roomSocket;

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
                // SECRET pins auto-expire at room close (§4.4); never log their content.
                for (Pin p : pinRepo.findByRoomIdOrderByOrderIndexAsc(room.getId())) {
                    if (p.getType() == PinType.SECRET) {
                        pinRepo.deleteById(p.getId());
                    }
                }
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

    /**
     * Pins a content block in a room (cf. arago-spec §4.4). Owner-only. The pin is appended (next
     * order index) and broadcast to the room's WebSocket peers; a {@code CODE} pin keeps its
     * {@code lang}. {@code 400} on a missing type/content.
     */
    @POST
    @Path("/{id}/pins")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createPin(@PathParam("id") String id, CreatePinRequest request) {
        String ownerSub = requireProvisionedSpeaker();
        ownedRoomOrAbort(id, ownerSub);
        if (request == null || request.type() == null
                || request.content() == null || request.content().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        int order = (int) pinRepo.countByRoomId(id);
        String lang = request.type() == PinType.CODE ? request.lang() : null;
        Pin saved = pinRepo.save(new Pin(UUID.randomUUID().toString(), id, request.type(),
                request.content(), lang, order, Instant.now()));
        roomSocket.broadcast(id, RoomSocket.pinEvent("add", saved));
        return Response.status(Response.Status.CREATED).entity(PinView.of(saved)).build();
    }

    /** Lists a room's pins in display order (owner only). */
    @GET
    @Path("/{id}/pins")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listPins(@PathParam("id") String id) {
        String ownerSub = requireProvisionedSpeaker();
        ownedRoomOrAbort(id, ownerSub);
        List<PinView> views = pinRepo.findByRoomIdOrderByOrderIndexAsc(id)
                .stream().map(PinView::of).toList();
        return Response.ok(views).build();
    }

    /**
     * Lists a room's help requests oldest-first (cf. arago-spec §4.5), owner only. Feeds the speaker
     * LAB panel; attendees raise/cancel theirs over the WebSocket ({@link RoomSocket}).
     */
    @GET
    @Path("/{id}/help")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listHelp(@PathParam("id") String id) {
        String ownerSub = requireProvisionedSpeaker();
        ownedRoomOrAbort(id, ownerSub);
        List<HelpView> views = helpRepo.findByRoomIdOrderByCreatedAtAsc(id)
                .stream().map(HelpView::of).toList();
        return Response.ok(views).build();
    }

    /**
     * Speaker claims a pending help request (PENDING → CLAIMED), recording the owner as handler and
     * broadcasting the new state to the room. Owner only; {@code 404} if the request is unknown or not
     * in this room; {@code 409} if it is no longer pending.
     */
    @POST
    @Path("/{id}/help/{helpId}/claim")
    @Produces(MediaType.APPLICATION_JSON)
    public Response claimHelp(@PathParam("id") String id, @PathParam("helpId") String helpId) {
        return transitionHelp(id, helpId, HelpStatus.PENDING, HelpStatus.CLAIMED, true);
    }

    /**
     * Speaker resolves a help request (PENDING/CLAIMED → RESOLVED) and broadcasts it. Owner only;
     * {@code 404} if unknown/not in this room; {@code 409} if already RESOLVED/CANCELLED.
     */
    @POST
    @Path("/{id}/help/{helpId}/resolve")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resolveHelp(@PathParam("id") String id, @PathParam("helpId") String helpId) {
        String ownerSub = requireProvisionedSpeaker();
        ownedRoomOrAbort(id, ownerSub);
        HelpRequest h = helpRepo.findById(helpId)
                .filter(r -> id.equals(r.getRoomId()))
                .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND));
        if (h.getStatus() != HelpStatus.PENDING && h.getStatus() != HelpStatus.CLAIMED) {
            return Response.status(Response.Status.CONFLICT).build();
        }
        h.setStatus(HelpStatus.RESOLVED);
        h.setClaimedBy(ownerSub);
        h.setUpdatedAt(Instant.now());
        HelpRequest saved = helpRepo.save(h);
        roomSocket.broadcast(id, RoomSocket.helpEvent(saved));
        return Response.ok(HelpView.of(saved)).build();
    }

    /** Owner-only state transition for a help request, expecting {@code from} and broadcasting {@code to}. */
    private Response transitionHelp(String id, String helpId, HelpStatus from, HelpStatus to,
                                    boolean recordHandler) {
        String ownerSub = requireProvisionedSpeaker();
        ownedRoomOrAbort(id, ownerSub);
        HelpRequest h = helpRepo.findById(helpId)
                .filter(r -> id.equals(r.getRoomId()))
                .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND));
        if (h.getStatus() != from) {
            return Response.status(Response.Status.CONFLICT).build();
        }
        h.setStatus(to);
        if (recordHandler) {
            h.setClaimedBy(ownerSub);
        }
        h.setUpdatedAt(Instant.now());
        HelpRequest saved = helpRepo.save(h);
        roomSocket.broadcast(id, RoomSocket.helpEvent(saved));
        return Response.ok(HelpView.of(saved)).build();
    }

    /** The room with {@code id} if owned by {@code ownerSub}; aborts {@code 404}/{@code 403} otherwise. */
    Room ownedRoomOrAbort(String id, String ownerSub) {
        Room room = rooms.findById(id)
                .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND));
        if (!ownerSub.equals(room.getOwnerSub())) {
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }
        return room;
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
