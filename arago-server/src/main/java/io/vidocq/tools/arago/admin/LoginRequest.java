package io.vidocq.tools.arago.admin;

/** Superadmin login request body (cf. arago-spec §8 {@code POST /api/admin/login}). Top-level record for JSON-B. */
public record LoginRequest(String username, String password) {
}
