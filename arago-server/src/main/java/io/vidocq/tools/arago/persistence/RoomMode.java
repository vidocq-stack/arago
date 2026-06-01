package io.vidocq.tools.arago.persistence;

/**
 * Presentation mode of a room (cf. arago-spec §4.1).
 *
 * <ul>
 *   <li>{@code CONF}   — conference talk: chat + pins, no seating layout.</li>
 *   <li>{@code LAB}    — hands-on lab: adds a seating layout and per-seat help requests.</li>
 *   <li>{@code HYBRID} — both, e.g. a talk that switches to lab exercises.</li>
 * </ul>
 */
public enum RoomMode {
    CONF,
    LAB,
    HYBRID
}
