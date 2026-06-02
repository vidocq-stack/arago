package io.vidocq.tools.arago.oidc;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Reads claims from the <em>payload</em> of a compact JWS (header.payload.signature) <b>without</b>
 * verifying the signature. This is only used on the access token Arago receives directly from
 * Keycloak's token endpoint over the trusted back-channel — to pull {@code email}/{@code sub} for the
 * allowlist lookup. Every subsequent {@code /api/*} call still goes through cervantes/MP-JWT, which
 * performs the real signature + issuer validation, so this decode introduces no trust shortcut.
 */
final class JwtPayload {

    private JwtPayload() {}

    /** Decodes the JWT payload segment into a JSON object. */
    static JsonObject of(String compactJwt) {
        if (compactJwt == null) {
            throw new IllegalArgumentException("null JWT");
        }
        String[] parts = compactJwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("not a compact JWT");
        }
        byte[] json = Base64.getUrlDecoder().decode(parts[1]);
        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(json))) {
            return reader.readObject();
        }
    }

    /** {@code email} claim (or null). */
    static String email(JsonObject payload) {
        return payload.getString("email", null);
    }

    /** {@code sub} claim (or null). */
    static String subject(JsonObject payload) {
        return payload.getString("sub", null);
    }
}
