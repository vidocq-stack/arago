package io.vidocq.tools.arago.oidc;

/** Minimal error payload (top-level record for JSON-B), e.g. {@code {"error":"speaker_not_provisioned"}}. */
public record ErrorView(String error) {
}
