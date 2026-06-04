package io.vidocq.tools.arago.rooms;

import io.vidocq.tools.arago.auth.AragoJwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mints the short-lived HS256 token an attendee carries on the room WebSocket (cf. arago-spec §4.2).
 * Signed by Arago with the same secret as the superadmin token ({@code arago.attendee.hmac-secret}),
 * but with {@code role=attendee} and {@code aud=arago-attendee} — so cervantes/OIDC never confuses it
 * with a speaker Bearer, and the superadmin path never accepts it. Claims: {@code roomId}, {@code pseudo},
 * optional {@code profileId}. TTL = room TTL + 1h (the close grace window, §4.1).
 */
@ApplicationScoped
public class AttendeeTokens {

    private volatile AragoJwt jwt;

    /** Issues an attendee token for a room; {@code profileId} may be null (pseudo-only attendee). */
    public String issue(String roomId, String pseudo, String profileId) {
        return issue(roomId, pseudo, profileId, false);
    }

    /**
     * Issues a room token. When {@code speaker} is true the token carries the {@code spk} marker, so the
     * WebSocket flags this peer as the speaker (its chat is {@code fromSpeaker}, and it is left out of the
     * public attendee headcount). Used by the speaker console's observer connection.
     */
    public String issue(String roomId, String pseudo, String profileId, boolean speaker) {
        AragoJwt verifier = jwt();
        if (verifier == null) {
            throw new IllegalStateException("arago.attendee.hmac-secret is not configured");
        }
        Map<String, String> extra = new HashMap<>();
        extra.put("roomId", roomId);
        extra.put("pseudo", pseudo);
        if (profileId != null) {
            extra.put("profileId", profileId);
        }
        if (speaker) {
            extra.put("spk", "1");
        }
        String subject = profileId != null ? profileId : UUID.randomUUID().toString();
        return verifier.issue(subject, "attendee", AragoJwt.AUDIENCE_ATTENDEE, attendeeTtl(), extra);
    }

    /**
     * Verifies an attendee token (signature, alg, issuer, {@code aud=arago-attendee}, expiry) and
     * returns its claims. Throws {@link AragoJwt.InvalidTokenException} on any failure.
     */
    public AragoJwt.Claims verify(String token) {
        AragoJwt verifier = jwt();
        if (verifier == null) {
            throw new IllegalStateException("arago.attendee.hmac-secret is not configured");
        }
        return verifier.verify(token, AragoJwt.AUDIENCE_ATTENDEE);
    }

    private static Duration attendeeTtl() {
        int roomTtlHours = ConfigProvider.getConfig()
                .getOptionalValue("arago.room.ttl-hours", Integer.class).orElse(12);
        return Duration.ofHours(roomTtlHours + 1L);
    }

    private AragoJwt jwt() {
        AragoJwt j = jwt;
        if (j == null) {
            synchronized (this) {
                j = jwt;
                if (j == null) {
                    String secret = ConfigProvider.getConfig()
                            .getOptionalValue("arago.attendee.hmac-secret", String.class).orElse(null);
                    if (secret != null && !secret.isBlank()) {
                        jwt = j = new AragoJwt(secret.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return j;
    }
}
