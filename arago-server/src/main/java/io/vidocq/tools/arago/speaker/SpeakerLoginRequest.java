package io.vidocq.tools.arago.speaker;

/** Speaker login body: email + password (top-level record for JSON-B). */
public record SpeakerLoginRequest(String email, String password) {}
