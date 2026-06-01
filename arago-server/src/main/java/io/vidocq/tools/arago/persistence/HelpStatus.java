package io.vidocq.tools.arago.persistence;

/**
 * Lifecycle of a help request in a LAB room (cf. arago-spec §4.5).
 *
 * <ul>
 *   <li>{@code PENDING}   — raised by an attendee, awaiting a speaker.</li>
 *   <li>{@code CLAIMED}   — a speaker is on the way (claimed it).</li>
 *   <li>{@code RESOLVED}  — handled.</li>
 *   <li>{@code CANCELLED} — withdrawn by the attendee while still pending.</li>
 * </ul>
 */
public enum HelpStatus {
    PENDING,
    CLAIMED,
    RESOLVED,
    CANCELLED
}
