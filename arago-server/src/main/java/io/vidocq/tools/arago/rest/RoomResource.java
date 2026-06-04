package io.vidocq.tools.arago.rest;

import io.vidocq.tools.arago.attachments.AttachmentStore;
import io.vidocq.tools.arago.auth.AragoJwt;
import io.vidocq.tools.arago.oidc.SpeakerAllowlist;
import io.vidocq.tools.arago.persistence.AttendeeProfile;
import io.vidocq.tools.arago.persistence.AttendeeProfileRepository;
import io.vidocq.tools.arago.history.Exports;
import io.vidocq.tools.arago.persistence.ChatMessage;
import io.vidocq.tools.arago.persistence.HelpRequest;
import io.vidocq.tools.arago.persistence.HelpRequestRepository;
import io.vidocq.tools.arago.persistence.HelpStatus;
import io.vidocq.tools.arago.persistence.Pin;
import io.vidocq.tools.arago.persistence.PinRepository;
import io.vidocq.tools.arago.persistence.PinType;
import io.vidocq.tools.arago.persistence.Room;
import io.vidocq.tools.arago.persistence.RoomMode;
import io.vidocq.tools.arago.persistence.RoomManager;
import io.vidocq.tools.arago.persistence.RoomManagerRepository;
import io.vidocq.tools.arago.persistence.RoomRepository;
import io.vidocq.tools.arago.persistence.RoomStatus;
import io.vidocq.tools.arago.persistence.Seat;
import io.vidocq.tools.arago.persistence.SeatRepository;
import io.vidocq.tools.arago.persistence.Speaker;
import io.vidocq.tools.arago.persistence.SpeakerRepository;
import io.vidocq.tools.arago.rooms.AttendeeTokens;
import io.vidocq.tools.arago.rooms.CreatePinRequest;
import io.vidocq.tools.arago.rooms.CreateRoomRequest;
import io.vidocq.tools.arago.rooms.HelpView;
import io.vidocq.tools.arago.rooms.JoinRequest;
import io.vidocq.tools.arago.rooms.LayoutCodec;
import io.vidocq.tools.arago.rooms.LayoutSpec;
import io.vidocq.tools.arago.rooms.SeatBlock;
import io.vidocq.tools.arago.rooms.JoinResponse;
import io.vidocq.tools.arago.rooms.PinGenerator;
import io.vidocq.tools.arago.rooms.PinView;
import io.vidocq.tools.arago.rooms.RoomView;
import io.vidocq.tools.arago.ws.RoomSocket;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.time.Duration;
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

    @Inject
    io.vidocq.tools.arago.pins.OgPreviewFetcher ogFetcher;

    @Inject
    io.vidocq.tools.arago.persistence.ChatMessageRepository messages;

    @Inject
    AttachmentStore attachmentStore;

    @Inject
    SeatRepository seatRepo;

    @Inject
    RoomManagerRepository managers;

    @Inject
    SpeakerRepository speakers;

    /** Max upload size (5 MiB) — images/QR/small files; the rest is rejected with 413. */
    private static final int MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(CreateRoomRequest request) {
        String ownerSub = requireProvisionedSpeaker();
        if (request == null || request.title() == null || request.title().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        RoomMode mode = request.mode() == null ? RoomMode.CONF : request.mode();
        boolean needsLayout = mode == RoomMode.LAB || mode == RoomMode.HYBRID;
        if (needsLayout && !isValidLayout(request.layout())) {
            return Response.status(Response.Status.BAD_REQUEST).build(); // §4.5: LAB/HYBRID needs a BLOCKS layout
        }
        Room room = new Room(UUID.randomUUID().toString(), pins.next(), request.title().trim(),
                RoomStatus.ACTIVE, mode, ownerSub, Instant.now());
        if (needsLayout) {
            room.setLayoutJson(LayoutCodec.toJson(request.layout()));
        }
        Room saved = rooms.save(room);
        return Response.status(Response.Status.CREATED).entity(RoomView.of(saved)).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listMine() {
        String ownerSub = requireProvisionedSpeaker();
        java.util.LinkedHashMap<String, RoomView> byId = new java.util.LinkedHashMap<>();
        // Owned rooms first (most recent first).
        for (Room r : rooms.findByOwnerSubOrderByCreatedAtDesc(ownerSub)) {
            byId.put(r.getId(), RoomView.of(r, true, null));
        }
        // Then rooms the caller co-manages (matched by their allowlist email), with the owner's name.
        String email = speakers.findByOidcSub(ownerSub).map(Speaker::getEmail).orElse(null);
        if (email != null) {
            for (RoomManager rm : managers.findBySpeakerEmail(email)) {
                if (byId.containsKey(rm.getRoomId())) {
                    continue;
                }
                rooms.findById(rm.getRoomId())
                        .ifPresent(r -> byId.put(r.getId(), RoomView.of(r, false, ownerName(r))));
            }
        }
        return Response.ok(java.util.List.copyOf(byId.values())).build();
    }

    /** Display name (or email) of a room's owner, for showing on co-managed rooms. */
    private String ownerName(Room r) {
        return speakers.findByOidcSub(r.getOwnerSub())
                .map(s -> s.getDisplayName() != null && !s.getDisplayName().isBlank()
                        ? s.getDisplayName() : s.getEmail())
                .orElse(null);
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response detail(@PathParam("id") String id) {
        String sub = requireProvisionedSpeaker();
        Room room = manageableRoomOrAbort(id, sub); // owner or co-manager
        return Response.ok(RoomView.of(room, sub.equals(room.getOwnerSub()), ownerName(room))).build();
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
     * Deletes a room and all its content (§17.2) — owner (primary admin) only. Cascade: chat messages,
     * pins, help requests, seats and attachments. {@code 404} if unknown, {@code 403} if not the owner.
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        String ownerSub = requireProvisionedSpeaker();
        Room room = rooms.findById(id)
                .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND));
        if (!ownerSub.equals(room.getOwnerSub())) {
            throw new WebApplicationException(Response.Status.FORBIDDEN); // delete is primary-admin only
        }
        for (ChatMessage m : messages.findByRoomIdOrderByAtAsc(id)) {
            messages.deleteById(m.getId());
        }
        for (Pin p : pinRepo.findByRoomIdOrderByOrderIndexAsc(id)) {
            pinRepo.deleteById(p.getId());
        }
        for (HelpRequest h : helpRepo.findByRoomIdOrderByCreatedAtAsc(id)) {
            helpRepo.deleteById(h.getId());
        }
        for (boolean released : new boolean[] {false, true}) {
            for (Seat s : seatRepo.findByRoomIdAndReleased(id, released)) {
                seatRepo.deleteById(s.getId());
            }
        }
        attachmentStore.deleteByRoom(id);
        for (RoomManager rm : managers.findByRoomId(id)) {
            managers.deleteById(rm.getId());
        }
        rooms.deleteById(id);
        return Response.noContent().build();
    }

    // --- Co-speakers (§17.3): the owner (primary admin) invites/excludes co-managers. ---

    /** Lists a room's co-speakers (owner or a co-speaker may read). */
    @GET
    @Path("/{id}/managers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listManagers(@PathParam("id") String id) {
        manageableRoomOrAbort(id, requireProvisionedSpeaker());
        List<ManagerView> views = managers.findByRoomId(id).stream()
                .map(rm -> new ManagerView(rm.getSpeakerEmail(),
                        speakers.findByEmail(rm.getSpeakerEmail()).map(Speaker::getPseudo)
                                .orElse(rm.getSpeakerEmail())))
                .toList();
        return Response.ok(views).build();
    }

    /**
     * Invites a provisioned speaker (by email) to co-manage the room — owner only. {@code 400} if the
     * email is missing or not on the speaker allowlist; idempotent if already a co-speaker.
     */
    @POST
    @Path("/{id}/managers")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addManager(@PathParam("id") String id, ManagerRequest request) {
        String sub = requireProvisionedSpeaker();
        Room room = ownerRoomOrAbort(id, sub);
        if (request == null || request.pseudo() == null || request.pseudo().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        Speaker speaker = speakers.findByPseudo(request.pseudo().trim()).orElse(null);
        if (speaker == null) {
            return Response.status(Response.Status.BAD_REQUEST).build(); // no speaker with that pseudo
        }
        String email = speaker.getEmail();
        if (email.equalsIgnoreCase(displayEmailOfOwner(room))) {
            return Response.status(Response.Status.CONFLICT).build(); // the owner is already admin
        }
        if (managers.findByRoomIdAndSpeakerEmail(id, email).isEmpty()) {
            managers.save(new RoomManager(UUID.randomUUID().toString(), id, email,
                    speaker.getOidcSub(), Instant.now()));
        }
        return Response.status(Response.Status.CREATED)
                .entity(new ManagerView(email, speaker.getPseudo())).build();
    }

    /** Excludes a co-speaker (by email) — owner only. {@code 204} whether or not they were a co-speaker. */
    @DELETE
    @Path("/{id}/managers/{email}")
    public Response removeManager(@PathParam("id") String id, @PathParam("email") String email) {
        ownerRoomOrAbort(id, requireProvisionedSpeaker());
        for (RoomManager rm : managers.findByRoomIdAndSpeakerEmail(id, email.trim().toLowerCase())) {
            managers.deleteById(rm.getId());
        }
        return Response.noContent().build();
    }

    /** Co-speaker view: their pseudo (for display) + email (the identifier used to exclude). */
    public record ManagerView(String email, String pseudo) {}

    /** Invite body: the pseudo (#nnn) of the speaker to add as a co-manager (§17.3). */
    public record ManagerRequest(String pseudo) {}

    /** The room if owned by {@code sub}; aborts {@code 404}/{@code 403} — for owner-only operations. */
    private Room ownerRoomOrAbort(String id, String sub) {
        Room room = rooms.findById(id)
                .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND));
        if (!sub.equals(room.getOwnerSub())) {
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }
        return room;
    }

    private String displayEmailOfOwner(Room room) {
        return speakers.findByOidcSub(room.getOwnerSub()).map(Speaker::getEmail).orElse(null);
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

        // Suffix #nnn (3 random digits) to avoid collisions between homonymous attendees (§17.1).
        String finalPseudo = pseudo + "#" + String.format("%03d",
                java.util.concurrent.ThreadLocalRandom.current().nextInt(1000));
        String token = attendeeTokens.issue(room.getId(), finalPseudo, profileId);
        return Response.ok(
                new JoinResponse(room.getId(), room.getMode().name(), token, profileId, finalPseudo)).build();
    }

    /**
     * Public room lobby for the projected attendee screen (cf. arago-spec §4.1): the room title + PIN to
     * join, plus a live headcount the display page polls. No authentication — the PIN is the public join
     * credential anyone in the room is shown, and the title is the public talk name; whoever sees the PIN
     * can already join. {@code 404} if the PIN matches no room.
     */
    @GET
    @Path("/lobby/{pin}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response lobby(@PathParam("pin") String pin) {
        Room room = rooms.findByPin(pin == null ? "" : pin.trim()).orElse(null);
        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new LobbyView(room.getTitle(), room.getPin(), room.getMode().name(),
                room.getStatus().name(), roomSocket.liveAttendeeCount(room.getId()))).build();
    }

    /** Public lobby view for the projected attendee screen: room title + PIN + live headcount. */
    public record LobbyView(String title, String pin, String mode, String status, int attendees) {}

    /**
     * Uploads a chat/pin attachment (cf. arago-spec §4.3/§4.4) stored as a PostgreSQL blob. Authorized as
     * a room participant: an attendee/observer token for this room ({@code ?token=}) or the owning
     * speaker's Bearer. Body = raw bytes; {@code kind} = {@code image} (served inline, no SVG) or
     * {@code file}. Capped at {@value #MAX_ATTACHMENT_BYTES} bytes (413 beyond). Returns the attachment id
     * the client then references from a chat message or a pin.
     */
    @POST
    @Path("/{id}/attachments")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadAttachment(@PathParam("id") String id,
            @QueryParam("token") String attendeeToken,
            @QueryParam("kind") String kind,
            @QueryParam("filename") String filename,
            @HeaderParam("Content-Type") String contentType,
            InputStream body) {
        Room room = authorizeRoomParticipant(id, attendeeToken);
        if (contentType == null || contentType.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        String ct = contentType.split(";")[0].trim().toLowerCase();
        String k = "image".equalsIgnoreCase(kind) ? "image" : "file";
        if (k.equals("image") && (!ct.startsWith("image/") || ct.equals("image/svg+xml"))) {
            return Response.status(Response.Status.BAD_REQUEST).build(); // SVG blocked (XSS); raster images only
        }
        byte[] data;
        try {
            data = body.readNBytes(MAX_ATTACHMENT_BYTES + 1);
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (data.length == 0) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (data.length > MAX_ATTACHMENT_BYTES) {
            return Response.status(413).build();
        }
        String aid = UUID.randomUUID().toString();
        String safeName = filename == null ? null : filename.replaceAll("[\\r\\n\"\\\\]", "").trim();
        if (safeName != null && safeName.length() > 200) {
            safeName = safeName.substring(0, 200);
        }
        Instant now = Instant.now();
        attachmentStore.save(aid, room.getId(), k, ct, safeName, data, now, now.plus(attachmentRetention()));
        return Response.status(Response.Status.CREATED)
                .entity(new AttachmentView(aid, k, ct, safeName, data.length)).build();
    }

    /** Result of an upload: the id to reference from a chat message or pin. */
    public record AttachmentView(String id, String kind, String contentType, String filename, int size) {}

    /**
     * Resolves a room participant: a valid attendee/observer token for this room ({@code token}), else the
     * owning provisioned speaker. Aborts {@code 401}/{@code 403}/{@code 404} otherwise.
     */
    private Room authorizeRoomParticipant(String id, String attendeeToken) {
        if (attendeeToken != null && !attendeeToken.isBlank()) {
            AragoJwt.Claims claims;
            try {
                claims = attendeeTokens.verify(attendeeToken);
            } catch (RuntimeException e) {
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            }
            if (!id.equals(claims.extra().get("roomId"))) {
                throw new WebApplicationException(Response.Status.FORBIDDEN);
            }
            return rooms.findById(id)
                    .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND));
        }
        return manageableRoomOrAbort(id, requireProvisionedSpeaker());
    }

    private static Duration attachmentRetention() {
        int hours = ConfigProvider.getConfig()
                .getOptionalValue("arago.room.ttl-hours", Integer.class).orElse(12);
        return Duration.ofHours(hours + 1L);
    }

    /** A BLOCKS layout is valid when it has at least one row and one non-empty, positively-sized block. */
    private static boolean isValidLayout(LayoutSpec layout) {
        if (layout == null || layout.rows() <= 0 || layout.blocks() == null || layout.blocks().isEmpty()) {
            return false;
        }
        for (SeatBlock b : layout.blocks()) {
            if (b == null || b.size() <= 0) {
                return false;
            }
        }
        return true;
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
        manageableRoomOrAbort(id, ownerSub);
        if (request == null || request.type() == null
                || request.content() == null || request.content().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        int order = (int) pinRepo.countByRoomId(id);
        String lang = request.type() == PinType.CODE ? request.lang() : null;
        Pin pin = new Pin(UUID.randomUUID().toString(), id, request.type(),
                request.content(), lang, order, Instant.now());
        // URL pins get a best-effort, SSRF-hardened OpenGraph preview fetched at creation (§4.4).
        if (request.type() == PinType.URL) {
            ogFetcher.fetch(request.content()).ifPresent(p -> {
                pin.setPreviewTitle(p.title());
                pin.setPreviewImage(p.image());
                pin.setPreviewDescription(p.description());
            });
        }
        Pin saved = pinRepo.save(pin);
        roomSocket.broadcast(id, RoomSocket.pinEvent("add", saved));
        return Response.status(Response.Status.CREATED).entity(PinView.of(saved)).build();
    }

    /** Lists a room's pins in display order (owner only). */
    @GET
    @Path("/{id}/pins")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listPins(@PathParam("id") String id) {
        String ownerSub = requireProvisionedSpeaker();
        manageableRoomOrAbort(id, ownerSub);
        List<PinView> views = pinRepo.findByRoomIdOrderByOrderIndexAsc(id)
                .stream().map(PinView::of).toList();
        return Response.ok(views).build();
    }

    /**
     * Reorders a room's pins (owner only, §11 Phase 2). The body lists pin ids in the desired order;
     * each is reassigned its {@code orderIndex} by position. Ids not in the list are appended after,
     * keeping their prior relative order (tolerates a partial list); an id foreign to the room is a 400.
     */
    @PUT
    @Path("/{id}/pins/order")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response reorderPins(@PathParam("id") String id, ReorderRequest request) {
        String ownerSub = requireProvisionedSpeaker();
        manageableRoomOrAbort(id, ownerSub);
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        List<Pin> roomPins = pinRepo.findByRoomIdOrderByOrderIndexAsc(id);
        java.util.Map<String, Pin> byId = new java.util.LinkedHashMap<>();
        for (Pin p : roomPins) {
            byId.put(p.getId(), p);
        }
        for (String pinId : request.ids()) {
            if (!byId.containsKey(pinId)) {
                return Response.status(Response.Status.BAD_REQUEST).build(); // id not in this room
            }
        }
        int order = 0;
        java.util.Set<String> placed = new java.util.LinkedHashSet<>();
        for (String pinId : request.ids()) {
            Pin p = byId.get(pinId);
            p.setOrderIndex(order++);
            pinRepo.save(p);
            placed.add(pinId);
        }
        for (Pin p : roomPins) {
            if (!placed.contains(p.getId())) {
                p.setOrderIndex(order++);
                pinRepo.save(p);
            }
        }
        List<Pin> reordered = pinRepo.findByRoomIdOrderByOrderIndexAsc(id);
        roomSocket.broadcast(id, RoomSocket.pinReorderEvent(reordered.stream().map(Pin::getId).toList()));
        return Response.ok(reordered.stream().map(PinView::of).toList()).build();
    }

    /** Reorder request body: pin ids in the desired display order. */
    public record ReorderRequest(java.util.List<String> ids) {}

    /**
     * Updates a LAB/HYBRID room's seating layout (owner only, §4.5) — pre-config or live. Re-validates,
     * persists, and broadcasts the new layout so connected top-down views refresh. Editing
     * {@code blockedSeats} is how a speaker toggles seats unavailable. A CONF room has no layout (409).
     * Note: a seat already taken that becomes blocked stays held — blocking only prevents new claims.
     */
    @PUT
    @Path("/{id}/layout")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateLayout(@PathParam("id") String id, LayoutSpec layout) {
        String ownerSub = requireProvisionedSpeaker();
        Room room = manageableRoomOrAbort(id, ownerSub);
        if (room.getMode() != RoomMode.LAB && room.getMode() != RoomMode.HYBRID) {
            return Response.status(Response.Status.CONFLICT).build();
        }
        if (!isValidLayout(layout)) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        room.setLayoutJson(LayoutCodec.toJson(layout));
        Room saved = rooms.save(room);
        roomSocket.broadcast(id, RoomSocket.layoutEvent(layout));
        return Response.ok(RoomView.of(saved)).build();
    }

    /**
     * Speaker force-releases an occupied seat (§17.4): the seat is freed and the change broadcast so it
     * disappears from every view. Owner or co-manager. {@code freed} reports whether a seat was actually
     * occupied there.
     */
    @POST
    @Path("/{id}/seats/release")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response releaseSeat(@PathParam("id") String id, SeatRef request) {
        manageableRoomOrAbort(id, requireProvisionedSpeaker());
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        boolean freed = roomSocket.releaseSeatAt(id, request.row(), request.block(), request.seat());
        return Response.ok(new SeatReleaseResult(freed)).build();
    }

    /** A seat coordinate (row, block index, seat index in block). */
    public record SeatRef(int row, int block, int seat) {}

    /** Whether the force-release actually freed an occupied seat. */
    public record SeatReleaseResult(boolean freed) {}

    /**
     * Lists a room's help requests oldest-first (cf. arago-spec §4.5), owner only. Feeds the speaker
     * LAB panel; attendees raise/cancel theirs over the WebSocket ({@link RoomSocket}).
     */
    @GET
    @Path("/{id}/help")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listHelp(@PathParam("id") String id) {
        String ownerSub = requireProvisionedSpeaker();
        manageableRoomOrAbort(id, ownerSub);
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
        manageableRoomOrAbort(id, ownerSub);
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
        manageableRoomOrAbort(id, ownerSub);
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

    // --- Moderation (§7): the owner-speaker mutes/kicks an attendee (by pseudo). State lives in RoomSocket. ---

    @POST
    @Path("/{id}/moderation/mute")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response mute(@PathParam("id") String id, ModerationRequest request) {
        return moderate(id, request, Action.MUTE);
    }

    @POST
    @Path("/{id}/moderation/unmute")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response unmute(@PathParam("id") String id, ModerationRequest request) {
        return moderate(id, request, Action.UNMUTE);
    }

    @POST
    @Path("/{id}/moderation/kick")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response kick(@PathParam("id") String id, ModerationRequest request) {
        return moderate(id, request, Action.KICK);
    }

    private enum Action { MUTE, UNMUTE, KICK }

    private Response moderate(String id, ModerationRequest request, Action action) {
        String ownerSub = requireProvisionedSpeaker();
        manageableRoomOrAbort(id, ownerSub);
        if (request == null || request.pseudo() == null || request.pseudo().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        String pseudo = request.pseudo().trim();
        int affected = switch (action) {
            case MUTE -> roomSocket.mute(id, pseudo);
            case UNMUTE -> roomSocket.unmute(id, pseudo);
            case KICK -> roomSocket.kick(id, pseudo);
        };
        return Response.ok(new ModerationResult(pseudo, affected)).build();
    }

    /** Moderation request body: the target attendee's pseudo. */
    public record ModerationRequest(String pseudo) {}

    /** Moderation outcome: the target pseudo + how many of their open sockets were affected. */
    public record ModerationResult(String pseudo, int affected) {}

    /**
     * Mints a short-lived observer token (owner only) so the speaker console can watch its own room's
     * WebSocket (layout/seats/pins/help) live. Reuses the attendee token scheme ({@code aud=arago-attendee})
     * — the console connects read-only and never claims a seat; the native speaker (RS256) WS is a later
     * refinement. Returns the token and the room PIN to build the {@code /ws/rooms/{pin}} URL.
     */
    @POST
    @Path("/{id}/observer-token")
    @Produces(MediaType.APPLICATION_JSON)
    public Response observerToken(@PathParam("id") String id, @QueryParam("name") String name) {
        String ownerSub = requireProvisionedSpeaker();
        Room room = manageableRoomOrAbort(id, ownerSub);
        // The display name the speaker's chat appears under (default "speaker"); capped, never blank.
        String pseudo = (name == null || name.isBlank()) ? RoomSocket.OBSERVER_PSEUDO : name.trim();
        if (pseudo.length() > 40) {
            pseudo = pseudo.substring(0, 40);
        }
        String token = attendeeTokens.issue(room.getId(), pseudo, null, true);
        return Response.ok(new ObserverToken(token, room.getPin())).build();
    }

    /** Observer token + room PIN for the speaker console's live WebSocket. */
    public record ObserverToken(String token, String pin) {}

    // --- Reveal remote control (§4.6): commands are owner-only REST; slide state flows over the WS. ---

    private static final java.util.Set<String> REVEAL_CMDS =
            java.util.Set.of("next", "prev", "goto", "togglePause");

    /** Enables (idempotently) the reveal session and returns its secret + room PIN for the deck plugin. */
    @POST
    @Path("/{id}/reveal/enable")
    @Produces(MediaType.APPLICATION_JSON)
    public Response revealEnable(@PathParam("id") String id) {
        String ownerSub = requireProvisionedSpeaker();
        Room room = manageableRoomOrAbort(id, ownerSub);
        if (room.getRevealSecret() == null || room.getRevealSecret().isBlank()) {
            room.setRevealSecret(UUID.randomUUID().toString());
            room = rooms.save(room);
        }
        return Response.ok(new RevealEnabled(room.getRevealSecret(), room.getPin())).build();
    }

    /** Owner sends a reveal command; broadcast as {@code reveal.cmd} to the deck plugin (§4.6). */
    @POST
    @Path("/{id}/reveal/cmd")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response revealCmd(@PathParam("id") String id, RevealCmd request) {
        String ownerSub = requireProvisionedSpeaker();
        manageableRoomOrAbort(id, ownerSub);
        if (request == null || request.cmd() == null || !REVEAL_CMDS.contains(request.cmd())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        roomSocket.broadcast(id, RoomSocket.revealCmdEvent(request.cmd(), request.slide()));
        return Response.ok().build();
    }

    /** Reveal session secret + room PIN (deck URL = {public}/reveal-demo?aragoRoom={pin}&aragoSecret={secret}). */
    public record RevealEnabled(String secret, String pin) {}

    /** Reveal command body: {@code cmd} ∈ next/prev/goto/togglePause, optional target {@code slide}. */
    public record RevealCmd(String cmd, Integer slide) {}

    // --- Past-event history & exports (§11 Phase 5), owner-only. ---

    @GET
    @Path("/{id}/chat")
    @Produces(MediaType.APPLICATION_JSON)
    public Response chatHistory(@PathParam("id") String id) {
        String ownerSub = requireProvisionedSpeaker();
        manageableRoomOrAbort(id, ownerSub);
        List<ChatView> views = messages.findByRoomIdOrderByAtAsc(id).stream()
                .map(m -> new ChatView(m.getAuthorPseudo(), m.getBody(), m.isPersistent(), m.isValidated(),
                        m.getAt() == null ? null : m.getAt().toString()))
                .toList();
        return Response.ok(views).build();
    }

    @GET
    @Path("/{id}/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stats(@PathParam("id") String id) {
        String ownerSub = requireProvisionedSpeaker();
        manageableRoomOrAbort(id, ownerSub);
        List<ChatMessage> msgs = messages.findByRoomIdOrderByAtAsc(id);
        List<HelpRequest> helps = helpRepo.findByRoomIdOrderByCreatedAtAsc(id);
        int persistent = (int) msgs.stream().filter(ChatMessage::isPersistent).count();
        int resolved = (int) helps.stream().filter(h -> h.getStatus() == HelpStatus.RESOLVED).count();
        int attendees = (int) msgs.stream().map(ChatMessage::getAuthorPseudo).distinct().count();
        return Response.ok(new RoomStats(msgs.size(), persistent, helps.size(), resolved, attendees)).build();
    }

    @GET
    @Path("/{id}/chat/export.md")
    @Produces("text/markdown")
    public Response exportChat(@PathParam("id") String id) {
        String ownerSub = requireProvisionedSpeaker();
        Room room = manageableRoomOrAbort(id, ownerSub);
        String md = Exports.chatMarkdown(room.getTitle(), room.getPin(), messages.findByRoomIdOrderByAtAsc(id));
        return Response.ok(md)
                .header("Content-Disposition", "attachment; filename=\"chat-" + room.getPin() + ".md\"")
                .build();
    }

    @GET
    @Path("/{id}/help/export.csv")
    @Produces("text/csv")
    public Response exportHelp(@PathParam("id") String id) {
        String ownerSub = requireProvisionedSpeaker();
        Room room = manageableRoomOrAbort(id, ownerSub);
        String csv = Exports.helpCsv(helpRepo.findByRoomIdOrderByCreatedAtAsc(id));
        return Response.ok(csv)
                .header("Content-Disposition", "attachment; filename=\"help-" + room.getPin() + ".csv\"")
                .build();
    }

    /** A chat message in the history view. */
    public record ChatView(String author, String body, boolean persistent, boolean validated, String at) {}

    /** Past-event counters. */
    public record RoomStats(int messages, int persistentMessages, int helpTotal, int helpResolved, int attendees) {}

    /**
     * The room with {@code id} if the caller ({@code sub}) may MANAGE it — i.e. is the owner OR an
     * invited co-speaker (§17.3); aborts {@code 404}/{@code 403} otherwise. End/delete/invite use the
     * stricter owner-only checks instead.
     */
    Room manageableRoomOrAbort(String id, String sub) {
        Room room = rooms.findById(id)
                .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND));
        if (sub.equals(room.getOwnerSub()) || isCoManager(id, sub)) {
            return room;
        }
        throw new WebApplicationException(Response.Status.FORBIDDEN);
    }

    /** Whether {@code sub} is an invited co-speaker of the room (matched by the speaker's allowlist email). */
    private boolean isCoManager(String roomId, String sub) {
        return speakers.findByOidcSub(sub)
                .map(s -> !managers.findByRoomIdAndSpeakerEmail(roomId, s.getEmail()).isEmpty())
                .orElse(false);
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
