package io.vidocq.tools.arago.oidc;

/** Body of {@code PUT /api/oidc/me/pseudo}: the speaker's chosen handle (server re-suffixes {@code #nnn}). */
public record PseudoRequest(String pseudo) {}
