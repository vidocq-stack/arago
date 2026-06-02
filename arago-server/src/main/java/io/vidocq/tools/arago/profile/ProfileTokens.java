package io.vidocq.tools.arago.profile;

import io.vidocq.tools.arago.auth.AragoJwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Mints and verifies the short-lived HS256 <em>magic-link</em> token for the attendee RGPD self-service
 * (cf. arago-spec §4.7). Same hand-rolled signer as the attendee/superadmin tokens (zero-dep), but with
 * {@code role=profile} and {@code aud=arago-profile} so it can never be confused with an attendee or
 * superadmin token. Subject = the {@code AttendeeProfile} id; TTL from {@code arago.magic-link.ttl-minutes}
 * (default 15). Mirrors {@code AttendeeTokens}: lazy-init the signer from {@code arago.attendee.hmac-secret}.
 */
@ApplicationScoped
public class ProfileTokens {

    private static final int DEFAULT_TTL_MINUTES = 15;

    private volatile AragoJwt jwt;

    /** Issues a magic-link token for the given attendee profile id. */
    public String issue(String profileId) {
        AragoJwt signer = jwt();
        if (signer == null) {
            throw new IllegalStateException("arago.attendee.hmac-secret is not configured");
        }
        return signer.issue(profileId, "profile", AragoJwt.AUDIENCE_PROFILE, ttl(),
                Map.of("profileId", profileId));
    }

    /**
     * Verifies a magic-link token (signature, alg, issuer, {@code aud=arago-profile}, expiry) and returns
     * its claims (subject = profile id). Throws {@link AragoJwt.InvalidTokenException} on any failure.
     */
    public AragoJwt.Claims verify(String token) {
        AragoJwt signer = jwt();
        if (signer == null) {
            throw new IllegalStateException("arago.attendee.hmac-secret is not configured");
        }
        return signer.verify(token, AragoJwt.AUDIENCE_PROFILE);
    }

    private static Duration ttl() {
        int minutes = ConfigProvider.getConfig()
                .getOptionalValue("arago.magic-link.ttl-minutes", Integer.class).orElse(DEFAULT_TTL_MINUTES);
        return Duration.ofMinutes(minutes);
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
