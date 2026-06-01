package io.vidocq.tools.arago.rooms;

import io.vidocq.tools.arago.persistence.RoomMode;

/**
 * Body of {@code POST /api/rooms} (cf. arago-spec §8). {@code mode} is optional and defaults to
 * {@code CONF} when absent.
 */
public record CreateRoomRequest(String title, RoomMode mode) {}
