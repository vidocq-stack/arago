package io.vidocq.tools.arago.server;

import io.vidocq.tools.arago.persistence.Role;
import io.vidocq.tools.arago.persistence.Speaker;
import io.vidocq.tools.arago.persistence.SpeakerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Optional dev/demo convenience: provision a single speaker into the allowlist at startup so a Keycloak
 * login lands as a fully-authorized speaker with no manual {@code POST /api/admin/speakers} step. Driven
 * by {@code arago.dev.seed-speaker} (the email); unset (the prod default) makes this a no-op.
 *
 * <p>Authentication still goes through Keycloak — this only short-circuits the <em>authorization</em>
 * side (the allowlist the superadmin would otherwise manage). It is invoked by {@link FlywayMigrator}
 * <em>after</em> migrations so the {@code speakers} table exists; running it as an independent
 * {@code @Initialized(ApplicationScoped.class)} observer would race the migrator.</p>
 *
 * <p>Config: {@code arago.dev.seed-speaker} (email, required to act), {@code arago.dev.seed-speaker.role}
 * ({@code SPEAKER}|{@code ADMIN}, default {@code SPEAKER}), {@code arago.dev.seed-speaker.name} (display
 * name, optional). Idempotent: an existing email is left untouched.</p>
 */
@ApplicationScoped
public class DevSpeakerSeeder {

    private static final Logger LOG = Logger.getLogger(DevSpeakerSeeder.class.getName());

    @Inject
    SpeakerRepository speakers;

    DevSpeakerSeeder() {
        // CDI
    }

    DevSpeakerSeeder(SpeakerRepository speakers) {
        this.speakers = speakers;
    }

    /** Reads {@code arago.dev.seed-speaker} (+ role/name) and seeds it; a blank/absent email is a no-op. */
    public void seedIfConfigured() {
        var config = ConfigProvider.getConfig();
        String email = config.getOptionalValue("arago.dev.seed-speaker", String.class)
                .filter(s -> !s.isBlank()).orElse(null);
        if (email == null) {
            return;
        }
        Role role = config.getOptionalValue("arago.dev.seed-speaker.role", String.class)
                .filter(s -> !s.isBlank())
                .map(s -> Role.valueOf(s.trim().toUpperCase()))
                .orElse(Role.SPEAKER);
        String name = config.getOptionalValue("arago.dev.seed-speaker.name", String.class)
                .filter(s -> !s.isBlank()).orElse(null);
        boolean created = seedSpeaker(email, role, name);
        if (created) {
            LOG.warning(() -> "Dev seed: provisioned speaker " + email.trim().toLowerCase()
                    + " (" + role + ") — DISABLE in production (arago.dev.seed-speaker)");
        }
    }

    /**
     * Provisions {@code email} (trimmed + lowercased, matching {@code SpeakerAllowlist}) as an enabled
     * speaker if absent. Returns {@code true} when a row was created, {@code false} if it already existed.
     */
    boolean seedSpeaker(String email, Role role, String displayName) {
        String key = email.trim().toLowerCase();
        if (speakers.findByEmail(key).isPresent()) {
            return false;
        }
        speakers.save(new Speaker(
                UUID.randomUUID().toString(), key, role, true, displayName, "dev-seed", Instant.now()));
        return true;
    }
}
