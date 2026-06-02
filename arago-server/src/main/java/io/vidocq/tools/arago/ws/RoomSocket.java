package io.vidocq.tools.arago.ws;

import io.vidocq.chappe.api.CloseCodes;
import io.vidocq.chappe.api.Request;
import io.vidocq.chappe.api.WebSocket;
import io.vidocq.chappe.api.WebSocketHandler;
import io.vidocq.tools.arago.auth.AragoJwt;
import io.vidocq.tools.arago.mail.Mailer;
import io.vidocq.tools.arago.persistence.AttendeeProfile;
import io.vidocq.tools.arago.persistence.AttendeeProfileRepository;
import io.vidocq.tools.arago.persistence.ChatMessage;
import io.vidocq.tools.arago.persistence.ChatMessageRepository;
import io.vidocq.tools.arago.profile.ProfileTokens;
import io.vidocq.tools.arago.persistence.HelpRequest;
import io.vidocq.tools.arago.persistence.HelpRequestRepository;
import io.vidocq.tools.arago.persistence.HelpStatus;
import io.vidocq.tools.arago.persistence.Pin;
import io.vidocq.tools.arago.persistence.PinRepository;
import io.vidocq.tools.arago.persistence.Room;
import io.vidocq.tools.arago.persistence.RoomRepository;
import io.vidocq.tools.arago.persistence.RoomStatus;
import io.vidocq.tools.arago.persistence.Seat;
import io.vidocq.tools.arago.persistence.SeatRepository;
import io.vidocq.tools.arago.rooms.AttendeeTokens;
import io.vidocq.tools.arago.rooms.LayoutCodec;
import io.vidocq.tools.arago.rooms.LayoutSpec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Room chat WebSocket (cf. arago-spec §4.3/§4.6). Declaratively mounted at {@code /ws/rooms/{pin}}
 * by the runtime ({@code vidocq.http.mount.roomws.*}); the WS socle resolves this {@code @ApplicationScoped}
 * bean and registers it on the Chappe router.
 *
 * <p>Handshake: the attendee presents their HS256 token (minted by {@code POST /api/rooms/join}) on
 * the {@code Authorization: Bearer} header, or — for browsers, which cannot set handshake headers —
 * a {@code ?token=} query parameter. The token is verified ({@code aud=arago-attendee}) and its
 * {@code roomId} must match the room behind the PIN; otherwise the socket is closed
 * ({@link CloseCodes#POLICY_VIOLATION}).</p>
 *
 * <p>Each frame is a JSON message {@code {"body": "...", "persistent": false}}. The message is
 * persisted ({@link ChatMessage}) and broadcast to every open socket in the room (including the
 * sender). Only an attendee who provided an email (has a {@code profileId}) may flag a message
 * {@code persistent} (§4.3/§4.7); otherwise it is ephemeral with a purge instant.</p>
 *
 * <p>Speaker (Keycloak RS256) sockets are a later refinement; this first cut serves attendee tokens,
 * the path delivered by slice 2.</p>
 */
@ApplicationScoped
public class RoomSocket implements WebSocketHandler {

    private static final System.Logger LOG = System.getLogger(RoomSocket.class.getName());
    private static final String BEARER = "Bearer ";
    private static final int MAX_BODY = 2000;

    @Inject
    RoomRepository rooms;

    @Inject
    ChatMessageRepository messages;

    @Inject
    AttendeeProfileRepository profiles;

    @Inject
    AttendeeTokens attendeeTokens;

    @Inject
    ProfileTokens profileTokens;

    @Inject
    Mailer mailer;

    @Inject
    PinRepository pinRepo;

    @Inject
    HelpRequestRepository helpRepo;

    @Inject
    SeatRepository seatRepo;

    /** roomId → open sockets, for broadcast. ConcurrentHashMap + key-set is virtual-thread-safe. */
    private final Map<String, Set<WebSocket>> peers = new ConcurrentHashMap<>();

    /** Moderation state (§7), per room, in memory for the room's lifetime: muted and kicked (banned) pseudos. */
    private final Map<String, Set<String>> mutedByRoom = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> bannedByRoom = new ConcurrentHashMap<>();

    @Override
    public void onOpen(WebSocket ws, Request handshake) throws IOException {
        String pin = handshake.pathParams().get("pin");
        String token = extractToken(handshake);
        if (pin == null || pin.isBlank() || token == null) {
            ws.close(CloseCodes.POLICY_VIOLATION, "missing pin or token");
            return;
        }
        Room room = rooms.findByPin(pin).orElse(null);
        if (room == null || room.getStatus() != RoomStatus.ACTIVE) {
            ws.close(CloseCodes.POLICY_VIOLATION, "room not joinable");
            return;
        }
        AragoJwt.Claims claims;
        try {
            claims = attendeeTokens.verify(token);
        } catch (RuntimeException e) {
            ws.close(CloseCodes.POLICY_VIOLATION, "invalid token");
            return;
        }
        if (!room.getId().equals(claims.extra().get("roomId"))) {
            ws.close(CloseCodes.POLICY_VIOLATION, "token/room mismatch");
            return;
        }

        String pseudo = claims.extra().getOrDefault("pseudo", "anonymous");
        String profileId = claims.extra().get("profileId");
        ws.attribute("roomId", room.getId());
        ws.attribute("pseudo", pseudo);
        if (profileId != null && !profileId.isBlank()) {
            ws.attribute("profileId", profileId);
        }
        // A kicked attendee cannot rejoin the room (§7) — refuse the handshake outright.
        Set<String> banned = bannedByRoom.get(room.getId());
        if (banned != null && banned.contains(pseudo)) {
            ws.close(CloseCodes.POLICY_VIOLATION, "kicked");
            return;
        }
        peers.computeIfAbsent(room.getId(), k -> ConcurrentHashMap.newKeySet()).add(ws);

        // Replay the room history (oldest first) to the freshly joined client.
        for (ChatMessage m : messages.findByRoomIdOrderByAtAsc(room.getId())) {
            trySend(ws, render(m));
        }
        // Replay the current pins (display order) so a joining client sees pinned content (§4.4).
        for (Pin p : pinRepo.findByRoomIdOrderByOrderIndexAsc(room.getId())) {
            trySend(ws, pinEvent("add", p));
        }
        // Replay active help requests (PENDING/CLAIMED) so a joining speaker sees the LAB queue (§4.5).
        for (HelpRequest h : helpRepo.findByRoomIdOrderByCreatedAtAsc(room.getId())) {
            if (h.getStatus() == HelpStatus.PENDING || h.getStatus() == HelpStatus.CLAIMED) {
                trySend(ws, helpEvent(h));
            }
        }
        // LAB rooms: push the seating layout, then replay the seats already taken (§4.5 top-down view).
        LayoutSpec layout = LayoutCodec.fromJson(room.getLayoutJson());
        if (layout != null) {
            trySend(ws, layoutEvent(layout));
            for (Seat s : seatRepo.findByRoomIdAndReleased(room.getId(), false)) {
                trySend(ws, seatEvent("taken", s));
            }
        }
    }

    @Override
    public void onText(WebSocket ws, String message) {
        String roomId = (String) ws.attribute("roomId");
        if (roomId == null) {
            return; // not authenticated (handshake rejected) — ignore
        }
        JsonObject json = parse(message);
        if (json == null) {
            return; // malformed frame
        }
        String pseudo = (String) ws.attribute("pseudo");
        // Inbound frame kind: LAB seat/help control frames, else a chat message.
        switch (json.getString("type", "chat")) {
            case "help" -> raiseHelp(roomId, pseudo, json);
            case "help-cancel" -> cancelHelp(roomId, pseudo);
            case "seat" -> claimSeat(ws, roomId, pseudo, json);
            case "seat-release" -> releaseSeat(roomId, pseudo);
            default -> handleChat(ws, roomId, pseudo, json);
        }
    }

    private void handleChat(WebSocket ws, String roomId, String pseudo, JsonObject json) {
        // Muted attendee (§7): drop the message (no persist, no broadcast); tell only the sender.
        Set<String> muted = mutedByRoom.get(roomId);
        if (muted != null && muted.contains(pseudo)) {
            trySend(ws, moderationEvent("muted", pseudo));
            return;
        }
        String profileId = (String) ws.attribute("profileId");
        String body = json.getString("body", null);
        if (body == null || body.isBlank()) {
            return;
        }
        if (body.length() > MAX_BODY) {
            body = body.substring(0, MAX_BODY);
        }
        // Only an email-bearing attendee (has a profile) may persist a message (§4.3/§4.7).
        boolean persistent = json.getBoolean("persistent", false) && profileId != null;
        Instant now = Instant.now();
        Instant purgeAfter = persistent ? null : now.plus(ephemeralRetention());

        // A persistent message from an unvalidated email is held (validated=false) and, on the attendee's
        // FIRST persistent message, triggers a validation magic link (§4.7/§10.1). Ephemeral = active.
        boolean validated = true;
        if (persistent) {
            AttendeeProfile profile = profiles.findById(profileId).orElse(null);
            validated = profile != null && profile.isEmailValidated();
            if (profile != null && !validated) {
                maybeSendValidationLink(profile);
            }
        }

        ChatMessage saved = messages.save(new ChatMessage(UUID.randomUUID().toString(), roomId, profileId,
                pseudo, false, persistent, body, now, purgeAfter, validated));
        broadcast(roomId, render(saved));
    }

    /** Sends the validation magic link only on the attendee's first persistent message (anti-spam). */
    private void maybeSendValidationLink(AttendeeProfile profile) {
        boolean firstPersistent = messages.findByProfileId(profile.getId()).stream()
                .noneMatch(ChatMessage::isPersistent);
        if (!firstPersistent) {
            return;
        }
        String link = publicBaseUrl() + "/api/profile/validate?token="
                + URLEncoder.encode(profileTokens.issue(profile.getId()), StandardCharsets.UTF_8);
        mailer.sendValidationLink(profile.getEmail(), link);
    }

    private static String publicBaseUrl() {
        String url = ConfigProvider.getConfig()
                .getOptionalValue("arago.public.url", String.class).orElse("http://localhost:8080");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static long helpCooldownSeconds() {
        return ConfigProvider.getConfig()
                .getOptionalValue("arago.help.cooldown-seconds", Long.class).orElse(60L);
    }

    /**
     * True if the attendee resolved a help request within the cooldown window (§4.5) — a new request is
     * then refused. Only RESOLVED counts (a CANCELLED request is a voluntary withdrawal, no penalty).
     * Pure and package-visible for unit testing.
     */
    static boolean inResolveCooldown(List<HelpRequest> attendeeHelps, Instant now, long cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return false;
        }
        for (HelpRequest h : attendeeHelps) {
            if (h.getStatus() == HelpStatus.RESOLVED && h.getUpdatedAt() != null
                    && now.isBefore(h.getUpdatedAt().plusSeconds(cooldownSeconds))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Attendee raises a "need help" request (§4.5). One active (PENDING/CLAIMED) per attendee. In a
     * LAB room the request snapshots the attendee's current seat coordinates + a human label, so the
     * speaker knows where to go even after the attendee later moves or leaves.
     */
    private void raiseHelp(String roomId, String pseudo, JsonObject json) {
        List<HelpRequest> mine = helpRepo.findByRoomIdAndAttendeePseudo(roomId, pseudo);
        boolean alreadyActive = mine.stream()
                .anyMatch(h -> h.getStatus() == HelpStatus.PENDING || h.getStatus() == HelpStatus.CLAIMED);
        if (alreadyActive) {
            return; // anti-spam: one active request at a time
        }
        if (inResolveCooldown(mine, Instant.now(), helpCooldownSeconds())) {
            return; // anti-spam: cooldown window after a resolution (§4.5)
        }
        String message = json.getString("message", null);
        if (message != null && message.length() > 140) {
            message = message.substring(0, 140);
        }

        // Resolve the requester's current seat (if any) and render a position label from the layout.
        Seat seat = activeSeat(roomId, pseudo);
        String position = json.getString("position", null);
        if (seat != null) {
            LayoutSpec layout = roomLayout(roomId);
            position = seatLabel(layout, seat);
        }

        HelpRequest help = new HelpRequest(
                UUID.randomUUID().toString(), roomId, pseudo, position, message,
                HelpStatus.PENDING, Instant.now());
        if (seat != null) {
            help.setSeatRow(seat.getSeatRow());
            help.setSeatBlockIndex(seat.getSeatBlockIndex());
            help.setSeatInBlock(seat.getSeatInBlock());
        }
        broadcast(roomId, helpEvent(helpRepo.save(help)));
    }

    /** Attendee withdraws their own still-pending request (§4.5). */
    private void cancelHelp(String roomId, String pseudo) {
        for (HelpRequest h : helpRepo.findByRoomIdAndAttendeePseudo(roomId, pseudo)) {
            if (h.getStatus() == HelpStatus.PENDING) {
                h.setStatus(HelpStatus.CANCELLED);
                h.setUpdatedAt(Instant.now());
                broadcast(roomId, helpEvent(helpRepo.save(h)));
            }
        }
    }

    /**
     * Attendee locks a seat first-come-first-serve (§4.5). Validates the coordinate against the room's
     * layout, then inserts the hold — the partial unique index {@code ux_seats_active} is the source
     * of truth, so a lost race surfaces as an insert failure and the attendee is told {@code seat-taken}.
     * A successful claim releases the attendee's previous seat (a move) and broadcasts both events.
     */
    private void claimSeat(WebSocket ws, String roomId, String pseudo, JsonObject json) {
        LayoutSpec layout = roomLayout(roomId);
        if (layout == null) {
            return; // not a LAB room — ignore seat frames
        }
        int row = json.getInt("row", -1);
        int block = json.getInt("block", -1);
        int seat = json.getInt("seat", -1);
        if (!layout.isValidSeat(row, block, seat)) {
            trySend(ws, seatRejected(pseudo, row, block, seat, "invalid-seat"));
            return;
        }
        // App-level pre-check (clean message for the common case); the index is the real guard below.
        int fRow = row, fBlock = block, fSeat = seat;
        boolean takenByOther = seatRepo.findByRoomIdAndReleased(roomId, false).stream()
                .anyMatch(s -> s.getSeatRow() == fRow && s.getSeatBlockIndex() == fBlock
                        && s.getSeatInBlock() == fSeat && !pseudo.equals(s.getAttendeePseudo()));
        if (takenByOther) {
            trySend(ws, seatRejected(pseudo, row, block, seat, "seat-taken"));
            return;
        }
        Seat saved;
        try {
            saved = seatRepo.save(new Seat(
                    UUID.randomUUID().toString(), roomId, pseudo, row, block, seat, Instant.now()));
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.DEBUG, "seat claim lost the unique-index race", e);
            trySend(ws, seatRejected(pseudo, row, block, seat, "seat-taken"));
            return;
        }
        // Took the new seat: free any prior hold (a move) and broadcast the transitions.
        for (Seat prior : releaseActiveSeats(roomId, pseudo, saved.getId())) {
            broadcast(roomId, seatEvent("free", prior));
        }
        broadcast(roomId, seatEvent("taken", saved));
    }

    /** Attendee gives up their seat (§4.5); also called when they leave the room (see {@link #onClose}). */
    private void releaseSeat(String roomId, String pseudo) {
        for (Seat freed : releaseActiveSeats(roomId, pseudo, null)) {
            broadcast(roomId, seatEvent("free", freed));
        }
    }

    /** The attendee's current (active) seat in the room, or null if unseated. */
    private Seat activeSeat(String roomId, String pseudo) {
        return seatRepo.findByRoomIdAndReleased(roomId, false).stream()
                .filter(s -> pseudo.equals(s.getAttendeePseudo()))
                .findFirst().orElse(null);
    }

    /** Marks the attendee's active seats released (skipping {@code exceptId}); returns those freed. */
    private List<Seat> releaseActiveSeats(String roomId, String pseudo, String exceptId) {
        List<Seat> freed = new ArrayList<>();
        for (Seat s : seatRepo.findByRoomIdAndReleased(roomId, false)) {
            if (!pseudo.equals(s.getAttendeePseudo()) || (exceptId != null && exceptId.equals(s.getId()))) {
                continue;
            }
            s.setReleased(true);
            s.setReleasedAt(Instant.now());
            seatRepo.save(s);
            freed.add(s);
        }
        return freed;
    }

    private LayoutSpec roomLayout(String roomId) {
        return rooms.findById(roomId).map(r -> LayoutCodec.fromJson(r.getLayoutJson())).orElse(null);
    }

    /** Renders a 1-indexed, human-readable seat label, e.g. {@code "R1·Center·S3"}. */
    private static String seatLabel(LayoutSpec layout, Seat s) {
        String block = layout == null ? "B" + (s.getSeatBlockIndex() + 1) : layout.blockLabel(s.getSeatBlockIndex());
        return "R" + (s.getSeatRow() + 1) + "·" + block + "·S" + (s.getSeatInBlock() + 1);
    }

    @Override
    public void onClose(WebSocket ws, int code, String reason) {
        String roomId = (String) ws.attribute("roomId");
        if (roomId != null) {
            Set<WebSocket> set = peers.get(roomId);
            if (set != null) {
                set.remove(ws);
            }
            // Leaving the room frees the seat (§4.5), so the coordinate becomes available again.
            String pseudo = (String) ws.attribute("pseudo");
            if (pseudo != null) {
                releaseSeat(roomId, pseudo);
            }
        }
    }

    /**
     * Broadcasts a raw JSON payload to every open socket in a room. Public so the REST resources can
     * push pin add/remove events (see {@link #pinEvent}) to connected clients.
     */
    public void broadcast(String roomId, String payload) {
        Set<WebSocket> set = peers.get(roomId);
        if (set == null) {
            return;
        }
        for (WebSocket peer : set) {
            trySend(peer, payload);
        }
    }

    // --- Moderation (§7), invoked by the owner-speaker via RoomResource. State is in memory, per room. ---

    /** Mutes a pseudo: their further messages are dropped. Returns the number of their open sockets notified. */
    public int mute(String roomId, String pseudo) {
        mutedByRoom.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(pseudo);
        return notify(roomId, pseudo, "muted");
    }

    /** Lifts a mute. Returns the number of the pseudo's open sockets notified. */
    public int unmute(String roomId, String pseudo) {
        Set<String> muted = mutedByRoom.get(roomId);
        if (muted != null) {
            muted.remove(pseudo);
        }
        return notify(roomId, pseudo, "unmuted");
    }

    /** Kicks a pseudo: bans them for the room's lifetime and closes their open sockets. Returns sockets closed. */
    public int kick(String roomId, String pseudo) {
        bannedByRoom.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(pseudo);
        int closed = 0;
        for (WebSocket ws : socketsOf(roomId, pseudo)) {
            try {
                ws.close(CloseCodes.POLICY_VIOLATION, "kicked");
                closed++;
            } catch (IOException e) {
                LOG.log(System.Logger.Level.DEBUG, "kick close failed", e);
            }
        }
        broadcast(roomId, moderationEvent("kicked", pseudo));
        return closed;
    }

    /** Sends a moderation event to every open socket of {@code pseudo} in the room; returns how many. */
    private int notify(String roomId, String pseudo, String action) {
        int n = 0;
        for (WebSocket ws : socketsOf(roomId, pseudo)) {
            trySend(ws, moderationEvent(action, pseudo));
            n++;
        }
        return n;
    }

    private List<WebSocket> socketsOf(String roomId, String pseudo) {
        Set<WebSocket> set = peers.get(roomId);
        if (set == null) {
            return List.of();
        }
        List<WebSocket> result = new ArrayList<>();
        for (WebSocket ws : set) {
            if (pseudo.equals(ws.attribute("pseudo"))) {
                result.add(ws);
            }
        }
        return result;
    }

    /** Renders a moderation event ({@code {"type":"moderation","action":...,"pseudo":...}}). */
    public static String moderationEvent(String action, String pseudo) {
        return Json.createObjectBuilder()
                .add("type", "moderation")
                .add("action", action)
                .add("pseudo", pseudo)
                .build().toString();
    }

    /** Renders a pin reorder event ({@code {"type":"pin","action":"reorder","ids":[...]}}); clients re-sort locally. */
    public static String pinReorderEvent(List<String> ids) {
        var arr = Json.createArrayBuilder();
        for (String id : ids) {
            arr.add(id);
        }
        return Json.createObjectBuilder()
                .add("type", "pin")
                .add("action", "reorder")
                .add("ids", arr)
                .build().toString();
    }

    /** Renders a pin add/remove WebSocket event ({@code {"type":"pin","action":...,"pin":{...}}}). */
    public static String pinEvent(String action, Pin p) {
        return Json.createObjectBuilder()
                .add("type", "pin")
                .add("action", action)
                .add("pin", Json.createObjectBuilder()
                        .add("id", p.getId())
                        .add("pinType", p.getType() == null ? "" : p.getType().name())
                        .add("content", p.getContent() == null ? "" : p.getContent())
                        .add("lang", p.getLang() == null ? "" : p.getLang())
                        .add("orderIndex", p.getOrderIndex())
                        .add("previewTitle", p.getPreviewTitle() == null ? "" : p.getPreviewTitle())
                        .add("previewImage", p.getPreviewImage() == null ? "" : p.getPreviewImage())
                        .add("previewDescription",
                                p.getPreviewDescription() == null ? "" : p.getPreviewDescription()))
                .build().toString();
    }

    /** Renders a help-request WebSocket event ({@code {"type":"help","status":...,...}}). */
    public static String helpEvent(HelpRequest h) {
        var b = Json.createObjectBuilder()
                .add("type", "help")
                .add("id", h.getId())
                .add("attendee", h.getAttendeePseudo())
                .add("position", h.getPosition() == null ? "" : h.getPosition())
                .add("message", h.getMessage() == null ? "" : h.getMessage())
                .add("status", h.getStatus() == null ? "" : h.getStatus().name());
        if (h.getSeatRow() != null) {
            b.add("row", h.getSeatRow()).add("block", h.getSeatBlockIndex()).add("seat", h.getSeatInBlock());
        }
        return b.build().toString();
    }

    /** Renders the room's seating layout ({@code {"type":"layout","layout":{...}}}) for the top-down view. */
    public static String layoutEvent(LayoutSpec layout) {
        var blocks = Json.createArrayBuilder();
        if (layout.blocks() != null) {
            for (var block : layout.blocks()) {
                blocks.add(Json.createObjectBuilder()
                        .add("size", block.size())
                        .add("label", block.label() == null ? "" : block.label()));
            }
        }
        var blocked = Json.createArrayBuilder();
        if (layout.blockedSeats() != null) {
            for (var s : layout.blockedSeats()) {
                blocked.add(Json.createObjectBuilder()
                        .add("row", s.row()).add("block", s.block()).add("seat", s.seat()));
            }
        }
        return Json.createObjectBuilder()
                .add("type", "layout")
                .add("layout", Json.createObjectBuilder()
                        .add("rows", layout.rows())
                        .add("blocks", blocks)
                        .add("stagePos", layout.stagePos() == null ? "TOP" : layout.stagePos())
                        .add("rowLabels", layout.rowLabels() == null ? "NUMERIC" : layout.rowLabels())
                        .add("blockedSeats", blocked))
                .build().toString();
    }

    /** Renders a seat state change ({@code action} = {@code "taken"} | {@code "free"}). */
    public static String seatEvent(String action, Seat s) {
        return Json.createObjectBuilder()
                .add("type", "seat")
                .add("action", action)
                .add("attendee", s.getAttendeePseudo())
                .add("row", s.getSeatRow())
                .add("block", s.getSeatBlockIndex())
                .add("seat", s.getSeatInBlock())
                .build().toString();
    }

    /** Renders a rejected seat claim sent only to the requester ({@code reason} explains why). */
    private static String seatRejected(String pseudo, int row, int block, int seat, String reason) {
        return Json.createObjectBuilder()
                .add("type", "seat")
                .add("action", "rejected")
                .add("attendee", pseudo)
                .add("row", row)
                .add("block", block)
                .add("seat", seat)
                .add("reason", reason)
                .build().toString();
    }

    private void trySend(WebSocket ws, String payload) {
        try {
            if (ws.isOpen()) {
                ws.sendText(payload);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.DEBUG, "WebSocket send failed; dropping peer", e);
        }
    }

    private static String extractToken(Request handshake) {
        String auth = handshake.header("Authorization").orElse(null);
        if (auth != null && auth.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            String t = auth.substring(BEARER.length()).trim();
            return t.isEmpty() ? null : t;
        }
        return handshake.queryParam("token").filter(t -> !t.isBlank()).orElse(null);
    }

    private static JsonObject parse(String message) {
        try (JsonReader r = Json.createReader(new StringReader(message))) {
            return r.readObject();
        } catch (RuntimeException e) {
            return null; // malformed frame — ignore
        }
    }

    private static String render(ChatMessage m) {
        return Json.createObjectBuilder()
                .add("type", "chat")
                .add("id", m.getId())
                .add("author", m.getAuthorPseudo())
                .add("fromSpeaker", m.isFromSpeaker())
                .add("persistent", m.isPersistent())
                .add("body", m.getBody())
                .add("at", m.getAt().toString())
                .build().toString();
    }

    private static Duration ephemeralRetention() {
        int days = ConfigProvider.getConfig()
                .getOptionalValue("arago.retention.chat.ephemeral-days", Integer.class).orElse(7);
        return Duration.ofDays(days);
    }
}
