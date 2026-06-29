package io.vidocq.tools.arago.speaker;

/** Body of {@code PUT /api/speaker/me/pseudo}: the speaker's chosen handle (server re-suffixes {@code #nnn}). */
public record PseudoRequest(String pseudo) {}
