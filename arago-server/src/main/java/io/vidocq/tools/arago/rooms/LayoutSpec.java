package io.vidocq.tools.arago.rooms;

import java.util.List;

/**
 * The seating layout of a LAB (or HYBRID) room (cf. arago-spec §4.5). BLOCKS is the only layout type
 * in v1: {@code rows} rows, each split into the same ordered list of {@code blocks} (a block has a
 * size and a label), with optional {@code blockedSeats} marked unavailable. {@code stagePos} and
 * {@code rowLabels} are presentation hints for the top-down view.
 *
 * <p>Serialized as JSON and stored on the room ({@code rooms.layout_json}); bound from the
 * {@code POST /api/rooms} body and echoed in {@link RoomView}. A seat coordinate is 0-indexed
 * {@code (row, block, seatInBlock)}.</p>
 */
public record LayoutSpec(int rows, List<SeatBlock> blocks, String stagePos, String rowLabels,
                         List<BlockedSeat> blockedSeats) {

    /** Total seats across one row (sum of block sizes). */
    public int seatsPerRow() {
        if (blocks == null) {
            return 0;
        }
        return blocks.stream().mapToInt(SeatBlock::size).sum();
    }

    /** True if {@code (row, block, seat)} is a valid, non-blocked coordinate of this layout. */
    public boolean isValidSeat(int row, int block, int seat) {
        if (blocks == null || row < 0 || row >= rows || block < 0 || block >= blocks.size()) {
            return false;
        }
        SeatBlock b = blocks.get(block);
        if (seat < 0 || seat >= b.size()) {
            return false;
        }
        return !isBlocked(row, block, seat);
    }

    /** True if {@code (row, block, seat)} is explicitly marked unavailable. */
    public boolean isBlocked(int row, int block, int seat) {
        if (blockedSeats == null) {
            return false;
        }
        return blockedSeats.stream()
                .anyMatch(s -> s.row() == row && s.block() == block && s.seat() == seat);
    }

    /** Human label for a block, falling back to its index when unlabeled. */
    public String blockLabel(int block) {
        if (blocks != null && block >= 0 && block < blocks.size()) {
            SeatBlock b = blocks.get(block);
            if (b.label() != null && !b.label().isBlank()) {
                return b.label();
            }
        }
        return "B" + (block + 1);
    }
}
