package io.vidocq.tools.arago.rooms;

/**
 * Body of {@code PUT /api/rooms/{id}/pins/{pinId}} — edits a pin in place (its type is fixed).
 * {@code lang} is only meaningful for {@code CODE} pins.
 */
public record UpdatePinRequest(String content, String lang) {}
