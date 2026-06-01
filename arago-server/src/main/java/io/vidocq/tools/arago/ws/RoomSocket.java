package io.vidocq.tools.arago.ws;

import io.vidocq.chappe.api.CloseCodes;
import io.vidocq.chappe.api.Request;
import io.vidocq.chappe.api.WebSocket;
import io.vidocq.chappe.api.WebSocketHandler;
import io.vidocq.tools.arago.auth.AragoJwt;
import io.vidocq.tools.arago.persistence.ChatMessage;
import io.vidocq.tools.arago.persistence.ChatMessageRepository;
import io.vidocq.tools.arago.persistence.Room;
import io.vidocq.tools.arago.persistence.RoomRepository;
import io.vidocq.tools.arago.persistence.RoomStatus;
import io.vidocq.tools.arago.rooms.AttendeeTokens;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
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
    AttendeeTokens attendeeTokens;

    /** roomId → open sockets, for broadcast. ConcurrentHashMap + key-set is virtual-thread-safe. */
    private final Map<String, Set<WebSocket>> peers = new ConcurrentHashMap<>();

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
        peers.computeIfAbsent(room.getId(), k -> ConcurrentHashMap.newKeySet()).add(ws);

        // Replay the room history (oldest first) to the freshly joined client.
        for (ChatMessage m : messages.findByRoomIdOrderByAtAsc(room.getId())) {
            trySend(ws, render(m));
        }
    }

    @Override
    public void onText(WebSocket ws, String message) {
        String roomId = (String) ws.attribute("roomId");
        if (roomId == null) {
            return; // not authenticated (handshake rejected) — ignore
        }
        String pseudo = (String) ws.attribute("pseudo");
        String profileId = (String) ws.attribute("profileId");

        String body = parseBody(message);
        if (body == null || body.isBlank()) {
            return;
        }
        if (body.length() > MAX_BODY) {
            body = body.substring(0, MAX_BODY);
        }
        // Only an email-bearing attendee (has a profile) may persist a message (§4.3/§4.7).
        boolean persistent = parsePersistent(message) && profileId != null;
        Instant now = Instant.now();
        Instant purgeAfter = persistent ? null : now.plus(ephemeralRetention());

        ChatMessage saved = messages.save(new ChatMessage(
                UUID.randomUUID().toString(), roomId, profileId, pseudo, false, persistent, body, now, purgeAfter));
        broadcast(roomId, render(saved));
    }

    @Override
    public void onClose(WebSocket ws, int code, String reason) {
        String roomId = (String) ws.attribute("roomId");
        if (roomId != null) {
            Set<WebSocket> set = peers.get(roomId);
            if (set != null) {
                set.remove(ws);
            }
        }
    }

    private void broadcast(String roomId, String payload) {
        Set<WebSocket> set = peers.get(roomId);
        if (set == null) {
            return;
        }
        for (WebSocket peer : set) {
            trySend(peer, payload);
        }
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

    private static String parseBody(String message) {
        JsonObject json = parse(message);
        return json == null ? null : json.getString("body", null);
    }

    private static boolean parsePersistent(String message) {
        JsonObject json = parse(message);
        return json != null && json.getBoolean("persistent", false);
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
