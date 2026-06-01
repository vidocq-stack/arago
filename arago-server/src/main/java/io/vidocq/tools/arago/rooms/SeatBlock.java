package io.vidocq.tools.arago.rooms;

/**
 * One contiguous block of seats in a row of a LAB layout (cf. arago-spec §4.5). {@code size} is the
 * number of seats; {@code label} is a human name shown in the top-down view (e.g. "Left", "Center").
 */
public record SeatBlock(int size, String label) {}
