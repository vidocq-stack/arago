package io.vidocq.tools.arago.persistence;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Jakarta Data repository for {@link Pin} (Mansart-generated impl).
 * Pins are listed in display order; {@code countByRoomId} backs the soft 20-pin limit (§4.4).
 */
@Transactional
@Repository
public interface PinRepository extends BasicRepository<Pin, String> {

    List<Pin> findByRoomIdOrderByOrderIndexAsc(String roomId);

    long countByRoomId(String roomId);
}
