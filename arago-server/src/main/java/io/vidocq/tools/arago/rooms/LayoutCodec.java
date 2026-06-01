package io.vidocq.tools.arago.rooms;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/**
 * Serializes a {@link LayoutSpec} to/from the JSON string stored on {@code rooms.layout_json}
 * (champollion JSON-B). The {@link Jsonb} is thread-safe and built once.
 */
public final class LayoutCodec {

    private static final Jsonb JSONB = JsonbBuilder.create();

    private LayoutCodec() {}

    public static String toJson(LayoutSpec layout) {
        return layout == null ? null : JSONB.toJson(layout);
    }

    public static LayoutSpec fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSONB.fromJson(json, LayoutSpec.class);
    }
}
