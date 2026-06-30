package io.vidocq.tools.arago.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.logging.Logger;

/**
 * Dev-only convenience: prints the ready-to-use login credentials to the console at startup, so a
 * developer running {@code vidocq:dev} can copy/paste them without digging through config.
 *
 * <p><strong>Strictly gated to dev.</strong> It logs nothing unless {@code arago.dev.superadmin.password}
 * is set — a key wired ONLY by the {@code vidocq:dev} Maven goal (see arago-server/pom.xml), never in
 * production. That key carries the superadmin's clear-text dev password purely for this banner (the app
 * itself authenticates against the PBKDF2 hash, not this value). In prod the key is absent → no-op, so the
 * "no secrets in clear in the logs" rule holds where it matters.</p>
 */
@ApplicationScoped
public class DevCredentialsLogger {

    private static final Logger LOG = Logger.getLogger(DevCredentialsLogger.class.getName());

    void onStart(@Observes @Initialized(ApplicationScoped.class) Object event) {
        var cfg = ConfigProvider.getConfig();
        String adminPassword = cfg.getOptionalValue("arago.dev.superadmin.password", String.class)
                .filter(s -> !s.isBlank()).orElse(null);
        if (adminPassword == null) {
            return; // not a dev run — log nothing
        }
        String adminUser = cfg.getOptionalValue("arago.superadmin.username", String.class).orElse("root");
        String seedSpeakers = cfg.getOptionalValue("arago.dev.seed-speaker", String.class)
                .filter(s -> !s.isBlank()).orElse(null);
        String seedPassword = cfg.getOptionalValue("arago.dev.seed-speaker.password", String.class)
                .filter(s -> !s.isBlank()).orElse("pw");

        StringBuilder sb = new StringBuilder("\n");
        sb.append("===== DEV credentials (NOT for production) =====\n");
        sb.append("  Superadmin  → /admin    ").append(adminUser).append(" / ").append(adminPassword).append('\n');
        if (seedSpeakers != null) {
            sb.append("  Speakers    → /speaker  password: ").append(seedPassword).append('\n');
            for (String token : seedSpeakers.split(",")) {
                String t = token.trim();
                if (t.isEmpty()) {
                    continue;
                }
                int eq = t.indexOf('=');
                String email = (eq >= 0 ? t.substring(0, eq) : t).trim();
                String role = eq >= 0 ? t.substring(eq + 1).trim().toUpperCase() : "SPEAKER";
                sb.append("                          ").append(email)
                        .append(" / ").append(seedPassword).append("  (").append(role).append(")\n");
            }
        }
        sb.append("================================================");
        LOG.warning(sb::toString);
    }
}
