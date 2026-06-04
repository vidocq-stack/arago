package io.vidocq.tools.arago.oidc;

/** Identity of the authenticated, allowlisted speaker (top-level record for JSON-B). */
public record MeView(String email, String role, String oidcSub, String pseudo, String name) {
}
