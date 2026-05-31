package io.vidocq.tools.arago.admin;

/** Superadmin login response: the signed HS256 token (cf. arago-spec §4.2). Top-level record for JSON-B. */
public record LoginResponse(String token) {
}
