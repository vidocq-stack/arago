package io.vidocq.tools.arago.admin;

import io.vidocq.tools.arago.persistence.Role;

/**
 * Body of {@code POST /api/admin/speakers}: create a speaker by email + role + initial password
 * (top-level record for JSON-B). The password is stored as a PBKDF2 hash, never in clear.
 */
public record CreateSpeakerRequest(String email, Role role, String displayName, String password) {
}
