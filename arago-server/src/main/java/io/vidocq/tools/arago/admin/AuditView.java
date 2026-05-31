package io.vidocq.tools.arago.admin;

/**
 * Read model of an {@code AdminAudit} entry (top-level record for JSON-B). {@code at} is an
 * ISO-8601 instant string to avoid any temporal-binding ambiguity in the JSON layer.
 */
public record AuditView(String id, String actor, String action, String target,
                        String ipTruncated, String at) {
}
