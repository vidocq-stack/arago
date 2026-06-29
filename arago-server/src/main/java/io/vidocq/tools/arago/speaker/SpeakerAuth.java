package io.vidocq.tools.arago.speaker;

import io.vidocq.tools.arago.auth.AragoJwt;
import io.vidocq.tools.arago.auth.PasswordHasher;
import io.vidocq.tools.arago.persistence.Speaker;
import io.vidocq.tools.arago.persistence.SpeakerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Local speaker authentication (cf. arago-spec §4.2) — replaces the former Keycloak/OIDC flow. The
 * speaker logs in with {@code email} + password; the password is verified against the PBKDF2 PHC hash
 * stored on the {@link Speaker} row ({@link PasswordHasher}). On success a short HS256 token is minted
 * ({@code aud=arago-speaker}, {@code sub=speaker.id}, {@code role=speaker.role}), carrying the email,
 * pseudo and display name as extra claims.
 *
 * <p>Signed with {@code arago.attendee.hmac-secret} (the shared Arago HMAC key, same as the superadmin
 * and attendee tokens). Failures are uniform — a missing account, a disabled account, a missing hash or
 * a wrong password all return empty, never leaking which.</p>
 */
@ApplicationScoped
public class SpeakerAuth {

    @Inject
    SpeakerRepository speakers;

    private final PasswordHasher hasher = new PasswordHasher();
    private volatile AragoJwt jwt;        // null until configured
    private volatile Duration ttl;

    /** Authentication is available only when a signing secret is configured. */
    public boolean enabled() {
        return jwt() != null;
    }

    /**
     * Verifies credentials and, on success, returns {@code (token, identity)}. Returns empty on any
     * failure (disabled feature, unknown email, disabled account, no password set, wrong password) —
     * the caller must not leak which.
     */
    public Optional<SpeakerTokenResponse> login(String email, String password) {
        AragoJwt verifier = jwt();
        if (verifier == null || email == null || email.isBlank()) {
            return Optional.empty();
        }
        Speaker speaker = speakers.findByEmail(email.trim().toLowerCase()).orElse(null);
        char[] pw = password == null ? new char[0] : password.toCharArray();
        boolean ok;
        if (speaker == null || !speaker.isEnabled() || speaker.getPasswordHash() == null) {
            ok = false; // still fall through with a uniform failure
        } else {
            try {
                ok = hasher.verify(pw, speaker.getPasswordHash());
            } catch (RuntimeException e) {
                ok = false; // malformed stored hash is a config error — fail closed
            }
        }
        if (!ok) {
            return Optional.empty();
        }
        speaker.setLastSeenAt(Instant.now());
        if (speaker.getFirstLoginAt() == null) {
            speaker.setFirstLoginAt(Instant.now());
        }
        speakers.save(speaker);

        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("email", speaker.getEmail());
        if (speaker.getPseudo() != null) {
            extra.put("pseudo", speaker.getPseudo());
        }
        if (speaker.getDisplayName() != null) {
            extra.put("name", speaker.getDisplayName());
        }
        String token = verifier.issue(speaker.getId(), speaker.getRole().name(),
                AragoJwt.AUDIENCE_SPEAKER, ttl, extra);
        return Optional.of(new SpeakerTokenResponse(token, SpeakerMeResource.view(speaker)));
    }

    private AragoJwt jwt() {
        AragoJwt j = jwt;
        if (j == null) {
            synchronized (this) {
                j = jwt;
                if (j == null) {
                    var cfg = ConfigProvider.getConfig();
                    String secret = cfg.getOptionalValue("arago.attendee.hmac-secret", String.class)
                            .orElse(null);
                    if (secret != null && !secret.isBlank()) {
                        ttl = Duration.ofMinutes(cfg
                                .getOptionalValue("arago.speaker.token-ttl-minutes", Integer.class)
                                .filter(m -> m > 0).orElse(720));
                        jwt = j = new AragoJwt(secret.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return j;
    }
}
