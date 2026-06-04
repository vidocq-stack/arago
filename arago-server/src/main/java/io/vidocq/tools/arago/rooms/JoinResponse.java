package io.vidocq.tools.arago.rooms;

/**
 * Response of {@code POST /api/rooms/join}: the resolved room id + mode, the attendee's HS256 token
 * (to put on the room WebSocket handshake), the {@code profileId} when an email was provided (null
 * otherwise), and the final {@code pseudo} (suffixed with {@code #nnn} to avoid collisions, §17.1).
 * The SPA stores the token in browser memory and shows the pseudo (cf. arago-spec §4.2/§17.1).
 */
public record JoinResponse(String roomId, String mode, String token, String profileId, String pseudo) {}
