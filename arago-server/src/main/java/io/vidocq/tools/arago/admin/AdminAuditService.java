package io.vidocq.tools.arago.admin;

import io.vidocq.tools.arago.persistence.AdminAudit;
import io.vidocq.tools.arago.persistence.AdminAuditRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;

/**
 * Records superadmin actions to the {@link AdminAudit} trail (cf. arago-spec §4.8/§10.2). Never stores
 * secrets, passwords or attendee emails — only the action, the acted-upon id, and a truncated client
 * IP (best-effort, from {@code X-Forwarded-For}: /24 for v4, /48 for v6; never the full address).
 */
@ApplicationScoped
public class AdminAuditService {

    @Inject
    AdminAuditRepository repository;

    public void record(String action, String target, String forwardedForHeader) {
        AdminAudit entry = new AdminAudit(UUID.randomUUID().toString(), "superadmin", action, target,
                truncateIp(forwardedForHeader), Instant.now());
        repository.save(entry);
    }

    /** Truncates the first hop of an {@code X-Forwarded-For} value to /24 (v4) or /48 (v6). */
    static String truncateIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        String ip = forwardedFor.split(",")[0].trim();
        if (ip.indexOf('.') > 0) {
            String[] octets = ip.split("\\.");
            if (octets.length == 4) {
                return octets[0] + "." + octets[1] + "." + octets[2] + ".0/24";
            }
        } else if (ip.indexOf(':') > 0) {
            String[] groups = ip.split(":");
            if (groups.length >= 3) {
                return groups[0] + ":" + groups[1] + ":" + groups[2] + "::/48";
            }
        }
        return null; // unparseable — never store a raw/unknown value
    }
}
