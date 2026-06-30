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
 *   <li>{@link #findByProfileId(String)} — every message authored by a given attendee profile; the
 *       RGPD self-service uses it to list (export) and to anonymise on erasure (§4.7).</li>
 * </ul>
 */
@Transactional
@Repository
public interface ChatMessageRepository extends BasicRepository<ChatMessage, String> {

    long count();

    List<ChatMessage> findByRoomIdOrderByAtAsc(String roomId);

    List<ChatMessage> findByPersistentFalseAndPurgeAfterLessThan(Instant cutoff);

    List<ChatMessage> findByProfileId(String profileId);

    /**
     * Every speaker-authored message carrying {@code authorPseudo}. Used to rewrite the denormalised
     * author name when a speaker changes pseudo (§17.3) — reliable because speaker pseudos are globally
     * unique, so the old pseudo identifies exactly that speaker's messages across all rooms.
     */
    List<ChatMessage> findByAuthorPseudoAndFromSpeakerTrue(String authorPseudo);

    /** One private (DM) thread: all messages of a room belonging to {@code dmAttendee}, oldest first. */
    List<ChatMessage> findByRoomIdAndDmAttendeeOrderByAtAsc(String roomId, String dmAttendee);

    /** Every private (DM) message of a room (any thread), oldest first — grouped per attendee in Java. */
    List<ChatMessage> findByRoomIdAndDmAttendeeNotNullOrderByAtAsc(String roomId);
}
