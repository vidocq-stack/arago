package io.vidocq.tools.arago.speaker;

/** Minimal error payload (top-level record for JSON-B), e.g. {@code {"error":"invalid_credentials"}}. */
public record ErrorView(String error) {
}
