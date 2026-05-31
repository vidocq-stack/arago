package io.vidocq.tools.arago.persistence;

/**
 * Allowlist status of a {@link Speaker} (cf. arago-spec §4.8).
 *
 * <p>Modelled as an enum rather than a {@code boolean} because Mansart's metamodel generator maps
 * {@code boolean}/{@code Boolean} fields to a numeric attribute and fails to compile them (tracked
 * in {@code mansart/BUG.md}); an enum is stored as a string column, which Mansart handles like
 * {@link RoomStatus}.</p>
 *
 * <ul>
 *   <li>{@code ACTIVE}   — speaker may authenticate and act.</li>
 *   <li>{@code DISABLED} — access cut at the next token check; history preserved.</li>
 * </ul>
 */
public enum SpeakerStatus {
    ACTIVE,
    DISABLED
}
