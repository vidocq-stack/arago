package io.vidocq.tools.arago.speaker;

/** Speaker login response: the signed HS256 token + the resolved identity (top-level record for JSON-B). */
public record SpeakerTokenResponse(String token, MeView me) {}
