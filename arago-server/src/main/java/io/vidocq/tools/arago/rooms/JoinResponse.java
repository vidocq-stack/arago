package io.vidocq.tools.arago.rooms;

/**
 * Response of {@code POST /api/rooms/join}: the resolved room id + mode, the attendee's HS256 token
 * (to put on the room WebSocket handshake), and the {@code profileId} when an email was provided
 * (null otherwise). The SPA stores the token in browser memory (cf. arago-spec §4.2).
 */
public record JoinResponse(String roomId, String mode, String token, String profileId) {}
