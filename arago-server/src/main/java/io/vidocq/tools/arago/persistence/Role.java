package io.vidocq.tools.arago.persistence;

/**
 * Role of a provisioned speaker in the Arago allowlist (cf. arago-spec §2/§4.8).
 *
 * <p>The <em>superadmin</em> is intentionally absent here: it is a break-glass root account whose
 * credentials live only in environment variables (cf. §4.8), never in the database.</p>
 *
 * <ul>
 *   <li>{@code SPEAKER} — may create and run their own rooms.</li>
 *   <li>{@code ADMIN}   — speaker who can also see all rooms, moderate and force-close.</li>
 * </ul>
 */
public enum Role {
    SPEAKER,
    ADMIN
}
