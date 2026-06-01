package io.vidocq.tools.arago.oidc;

import io.vidocq.tools.arago.persistence.Speaker;
import io.vidocq.tools.arago.persistence.SpeakerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Optional;

/**
 * Authorization of an OIDC-authenticated identity against the local speaker allowlist
 * (cf. arago-spec §4.2/§4.8). Authentication is Keycloak's job (the token is already validated by
 * cervantes); this decides whether that identity is a provisioned speaker.
 *
 * <p>Returns the {@link Speaker} when the email is on the allowlist AND enabled — binding the OIDC
 * {@code sub} on first login and refreshing {@code lastSeenAt}. Returns empty otherwise (the resource
 * then replies {@code 403 speaker_not_provisioned}). The effective role is {@link Speaker#getRole()},
 * not the Keycloak realm roles.</p>
 */
@ApplicationScoped
public class SpeakerAllowlist {

    @Inject
    SpeakerRepository speakers;

    public Optional<Speaker> authorize(String email, String oidcSub) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        Optional<Speaker> found = speakers.findByEmail(email.trim().toLowerCase());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Speaker speaker = found.get();
        if (!speaker.isEnabled()) {
            return Optional.empty();
        }
        if (speaker.getOidcSub() == null && oidcSub != null && !oidcSub.isBlank()) {
            speaker.setOidcSub(oidcSub);          // bind the Keycloak identity on first login
            speaker.setFirstLoginAt(Instant.now());
        }
        speaker.setLastSeenAt(Instant.now());
        speakers.save(speaker);
        return Optional.of(speaker);
    }
}
