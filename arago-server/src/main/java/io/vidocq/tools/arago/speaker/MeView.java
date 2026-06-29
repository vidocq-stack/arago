package io.vidocq.tools.arago.speaker;

/** Identity of the authenticated speaker (top-level record for JSON-B). */
public record MeView(String email, String role, String id, String pseudo, String name) {
}
