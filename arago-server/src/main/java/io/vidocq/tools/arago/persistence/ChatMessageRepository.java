package io.vidocq.tools.arago.persistence;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Jakarta Data repository for {@link ChatMessage} (Mansart-generated impl).
 *
 * <ul>
 *   <li>{@link #findByRoomIdOrderByAtAsc(String)} — room history, oldest first (replayed to a
 *       joining WebSocket client).</li>
 *   <li>{@link #findByPersistentFalseAndPurgeAfterLessThan(Instant)} — selects ephemeral messages
 *       past their purge instant; the daily purge job deletes whatever this returns (§4.7).</li>
 * </ul>
 */
@Transactional
@Repository
public interface ChatMessageRepository extends BasicRepository<ChatMessage, String> {

    long count();

    List<ChatMessage> findByRoomIdOrderByAtAsc(String roomId);

    List<ChatMessage> findByPersistentFalseAndPurgeAfterLessThan(Instant cutoff);
}
