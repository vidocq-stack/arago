package io.vidocq.tools.arago.server;

import io.vidocq.tools.arago.persistence.Role;
import io.vidocq.tools.arago.persistence.Speaker;
import io.vidocq.tools.arago.persistence.SpeakerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Optional dev/demo convenience: provision a single speaker into the allowlist at startup so a Keycloak
 * login lands as a fully-authorized speaker with no manual {@code POST /api/admin/speakers} step. Driven
 * by {@code arago.dev.seed-speaker} (the email); unset (the prod default) makes this a no-op.
 *
 * <p>Authentication still goes through Keycloak — this only short-circuits the <em>authorization</em>
 * side (the allowlist the superadmin would otherwise manage). It runs as its own
 * {@code @Observes @Initialized(ApplicationScoped.class)} observer; the schema (incl. the
 * {@code speakers} table) is already migrated by the Vidocq Flyway extension during {@code beforeStart}
 * — before the CDI container boots — so there is no race with the migration.</p>
 *
 * <p>Config: {@code arago.dev.seed-speaker} — a comma-separated list of emails, each optionally carrying
 * its role as {@code email=ROLE} (e.g. {@code speakera@oidc.test=ADMIN,speakerb@oidc.test}). A bare email
 * uses {@code arago.dev.seed-speaker.role} ({@code SPEAKER}|{@code ADMIN}, default {@code SPEAKER}). The
 * display name is {@code arago.dev.seed-speaker.name} when set, otherwise the email's local part. Each
 * entry is idempotent: an existing email is left untouched.</p>
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

    /**
     * Seeds at container start. Migrations have already run in the Flyway extension's
     * {@code beforeStart} (before the container boots), so the {@code speakers} table exists.
     */
    void onStart(@Observes @Initialized(ApplicationScoped.class) Object event) {
        seedIfConfigured();
    }

    /** Reads {@code arago.dev.seed-speaker} (+ role/name) and seeds each entry; a blank/absent value is a no-op. */
    public void seedIfConfigured() {
        var config = ConfigProvider.getConfig();
        String raw = config.getOptionalValue("arago.dev.seed-speaker", String.class)
                .filter(s -> !s.isBlank()).orElse(null);
        if (raw == null) {
            return;
        }
        Role defaultRole = config.getOptionalValue("arago.dev.seed-speaker.role", String.class)
                .filter(s -> !s.isBlank())
                .map(s -> Role.valueOf(s.trim().toUpperCase()))
                .orElse(Role.SPEAKER);
        String defaultName = config.getOptionalValue("arago.dev.seed-speaker.name", String.class)
                .filter(s -> !s.isBlank()).orElse(null);
        for (SeedEntry entry : parseSeedSpec(raw, defaultRole, defaultName)) {
            if (seedSpeaker(entry.email(), entry.role(), entry.displayName())) {
                LOG.warning(() -> "Dev seed: provisioned speaker " + entry.email()
                        + " (" + entry.role() + ") — DISABLE in production (arago.dev.seed-speaker)");
            }
        }
    }

    /** One parsed {@code arago.dev.seed-speaker} entry. */
    record SeedEntry(String email, Role role, String displayName) {}

    /**
     * Parses a comma-separated {@code arago.dev.seed-speaker} value into entries. Each token is an email,
     * optionally suffixed with {@code =ROLE}; a missing/blank role falls back to {@code defaultRole}. The
     * display name is {@code defaultName} when non-null, else the email's local part. Blank tokens are skipped.
     */
    static List<SeedEntry> parseSeedSpec(String raw, Role defaultRole, String defaultName) {
        List<SeedEntry> entries = new ArrayList<>();
        for (String token : raw.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            String email = t;
            Role role = defaultRole;
            int eq = t.indexOf('=');
            if (eq >= 0) {
                email = t.substring(0, eq).trim();
                String r = t.substring(eq + 1).trim();
                if (!r.isEmpty()) {
                    role = Role.valueOf(r.toUpperCase());
                }
            }
            if (email.isEmpty()) {
                continue;
            }
            email = email.toLowerCase();
            String name = defaultName != null ? defaultName : localPart(email);
            entries.add(new SeedEntry(email, role, name));
        }
        return entries;
    }

    private static String localPart(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
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
