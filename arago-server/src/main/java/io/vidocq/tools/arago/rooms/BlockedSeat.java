package io.vidocq.tools.arago.rooms;

/**
 * A seat marked unavailable in a LAB layout (cf. arago-spec §4.5) — e.g. a broken chair or a reserved
 * spot. Coordinates are 0-indexed: {@code (row, block, seat)} within the room's {@link LayoutSpec}.
 */
public record BlockedSeat(int row, int block, int seat) {}
