package io.vidocq.tools.arago.persistence;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Jakarta Data repository for {@link Seat} (Mansart-generated impl). "Active" means
 * {@code released = false}; the partial unique index guarantees one holder per coordinate, this
 * repository drives the top-down view (all active seats) and the per-attendee move/release rule.
 */
@Transactional
@Repository
public interface SeatRepository extends BasicRepository<Seat, String> {

    /**
     * All currently-held seats in a room (feeds the top-down view + on-join replay). Per-attendee and
     * per-coordinate lookups filter this small set in Java rather than relying on long derived
     * queries — the partial unique index, not a query, is the authority on who holds a coordinate.
     */
    List<Seat> findByRoomIdAndReleased(String roomId, boolean released);
}
