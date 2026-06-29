package io.vidocq.tools.arago.speaker;

import io.vidocq.tools.arago.auth.AragoJwt;
import io.vidocq.tools.arago.persistence.Speaker;
import io.vidocq.tools.arago.persistence.SpeakerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Verifies the speaker token on protected endpoints (cf. arago-spec §4.2). The token is the HS256 token
 * minted by {@link SpeakerAuth} ({@code aud=arago-speaker}, {@code sub=speaker.id}), carried on the
 * standard {@code Authorization: Bearer} header (free now that cervantes/OIDC is gone).
 *
 * <p>Resolves the {@link Speaker} by id and re-checks {@code enabled} on every call, so disabling a
 * speaker cuts access at the next request without revoking the token. Returns empty on any failure
 * (missing/invalid/expired token, unknown or disabled speaker); never throws.</p>
 */
@ApplicationScoped
public class SpeakerAuthenticator {

    @Inject
    SpeakerRepository speakers;

    private volatile AragoJwt jwt;

    /** Resolves the authenticated, enabled speaker from an {@code Authorization} header; empty on any failure. */
    public Optional<Speaker> authenticate(String authorizationHeader) {
        AragoJwt verifier = jwt();
        if (verifier == null) {
            return Optional.empty();
        }
        String token = stripBearer(authorizationHeader);
        if (token == null) {
            return Optional.empty();
        }
        try {
            AragoJwt.Claims claims = verifier.verify(token, AragoJwt.AUDIENCE_SPEAKER);
            return speakers.findById(claims.subject()).filter(Speaker::isEnabled);
        } catch (AragoJwt.InvalidTokenException e) {
            return Optional.empty();
        }
    }

    private static String stripBearer(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String v = header.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            v = v.substring(7).trim();
        }
        return v.isEmpty() ? null : v;
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
