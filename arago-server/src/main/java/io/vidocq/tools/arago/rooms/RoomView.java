package io.vidocq.tools.arago.rooms;

import io.vidocq.tools.arago.persistence.Room;

/**
 * JSON projection of a {@link Room} returned by the room endpoints (cf. arago-spec §8). Exposes the
 * speaker-facing view: identity, PIN, title, mode, status and timestamps. {@code ownerSub} is
 * intentionally omitted (internal identity).
 */
public record RoomView(String id, String pin, String title, String mode, String status,
                       String createdAt, String endedAt) {

    public static RoomView of(Room r) {
        return new RoomView(
                r.getId(),
                r.getPin(),
                r.getTitle(),
                r.getMode() == null ? null : r.getMode().name(),
                r.getStatus() == null ? null : r.getStatus().name(),
                r.getCreatedAt() == null ? null : r.getCreatedAt().toString(),
                r.getEndedAt() == null ? null : r.getEndedAt().toString());
    }
}
