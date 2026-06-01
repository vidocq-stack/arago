package io.vidocq.tools.arago.rooms;

import io.vidocq.tools.arago.persistence.Pin;

/**
 * JSON projection of a {@link Pin} (cf. arago-spec §4.4) — returned by the pin endpoints and
 * broadcast over the room WebSocket.
 */
public record PinView(String id, String type, String content, String lang, int orderIndex) {

    public static PinView of(Pin p) {
        return new PinView(
                p.getId(),
                p.getType() == null ? null : p.getType().name(),
                p.getContent(),
                p.getLang(),
                p.getOrderIndex());
    }
}
