package io.vidocq.tools.arago.persistence;

/**
 * Lifecycle of a room (cf. arago-spec §4.1).
 *
 * <ul>
 *   <li>{@code DRAFT}  — prepared ahead of time, not joinable by attendees.</li>
 *   <li>{@code ACTIVE} — opened by the speaker, PIN is joinable.</li>
 *   <li>{@code ENDED}  — closed; websockets shut down, PIN released after TTL.</li>
 * </ul>
 */
public enum RoomStatus {
    DRAFT,
    ACTIVE,
    ENDED
}
