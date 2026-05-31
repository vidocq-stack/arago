package io.vidocq.tools.arago.admin;

import io.vidocq.tools.arago.persistence.Role;

/**
 * Body of {@code PATCH /api/admin/speakers/{id}}: partial update — null fields are left unchanged
 * (top-level record for JSON-B).
 */
public record UpdateSpeakerRequest(Role role, Boolean enabled, String displayName) {
}
