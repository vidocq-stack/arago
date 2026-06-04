package io.vidocq.tools.arago.rooms;

import io.vidocq.tools.arago.persistence.Room;

/**
 * JSON projection of a {@link Room} returned by the room endpoints (cf. arago-spec §8). Exposes the
 * speaker-facing view: identity, PIN, title, mode, status, timestamps and (for LAB/HYBRID) the
 * seating {@code layout}. {@code ownerSub} is intentionally omitted (internal identity).
 */
public record RoomView(String id, String pin, String title, String mode, String status,
                       String createdAt, String endedAt, LayoutSpec layout,
                       boolean owned, String ownerName) {

    /** View of a room the caller owns (the common case): {@code owned=true}, no owner name needed. */
    public static RoomView of(Room r) {
        return of(r, true, null);
    }

    /**
     * View flagging whether the caller {@code owned}s the room; for a co-managed room {@code ownerName}
     * is the primary admin's display name to show in the list (§17.3).
     */
    public static RoomView of(Room r, boolean owned, String ownerName) {
        return new RoomView(
                r.getId(),
                r.getPin(),
                r.getTitle(),
                r.getMode() == null ? null : r.getMode().name(),
                r.getStatus() == null ? null : r.getStatus().name(),
                r.getCreatedAt() == null ? null : r.getCreatedAt().toString(),
                r.getEndedAt() == null ? null : r.getEndedAt().toString(),
                LayoutCodec.fromJson(r.getLayoutJson()),
                owned,
                ownerName);
    }
}
