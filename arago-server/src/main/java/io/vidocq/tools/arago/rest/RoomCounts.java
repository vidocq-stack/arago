package io.vidocq.tools.arago.rest;

/**
 * Tiny JSON-B projection returned by {@code GET /api/rooms/count} — proves the
 * REST + Mansart read path end to end. {@code active} also feeds the future
 * "active rooms" metric (cf. arago-spec §12).
 */
public record RoomCounts(long total, long active) {}
