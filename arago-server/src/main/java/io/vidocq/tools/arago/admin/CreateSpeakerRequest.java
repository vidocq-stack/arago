package io.vidocq.tools.arago.admin;

import io.vidocq.tools.arago.persistence.Role;

/** Body of {@code POST /api/admin/speakers}: invite a speaker by email + role (top-level record for JSON-B). */
public record CreateSpeakerRequest(String email, Role role, String displayName) {
}
