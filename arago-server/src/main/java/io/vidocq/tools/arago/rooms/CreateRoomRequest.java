package io.vidocq.tools.arago.rooms;

import io.vidocq.tools.arago.persistence.RoomMode;

/**
 * Body of {@code POST /api/rooms} (cf. arago-spec §8). {@code mode} is optional and defaults to
 * {@code CONF} when absent. {@code layout} is required for {@code LAB}/{@code HYBRID} rooms (the
 * BLOCKS seating plan, §4.5) and ignored for {@code CONF}.
 */
public record CreateRoomRequest(String title, RoomMode mode, LayoutSpec layout) {}
