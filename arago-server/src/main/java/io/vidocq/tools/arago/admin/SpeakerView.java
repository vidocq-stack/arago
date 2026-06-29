package io.vidocq.tools.arago.admin;

import io.vidocq.tools.arago.persistence.Role;

/** Read model of a speaker account returned by the admin API (top-level record for JSON-B). */
public record SpeakerView(String id, String email, Role role, boolean enabled,
                          String displayName, String pseudo, boolean hasPassword) {
}
