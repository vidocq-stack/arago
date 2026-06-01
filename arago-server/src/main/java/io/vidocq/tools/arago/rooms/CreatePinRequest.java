package io.vidocq.tools.arago.rooms;

import io.vidocq.tools.arago.persistence.PinType;

/**
 * Body of {@code POST /api/rooms/{id}/pins} (cf. arago-spec §4.4). {@code lang} is only meaningful
 * for {@code CODE} pins.
 */
public record CreatePinRequest(PinType type, String content, String lang) {}
