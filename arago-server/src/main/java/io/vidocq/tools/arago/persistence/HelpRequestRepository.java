package io.vidocq.tools.arago.persistence;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Jakarta Data repository for {@link HelpRequest} (Mansart-generated impl).
 * Active requests (PENDING/CLAIMED) drive the speaker LAB panel and the per-attendee anti-spam rule (§4.5).
 */
@Transactional
@Repository
public interface HelpRequestRepository extends BasicRepository<HelpRequest, String> {

    List<HelpRequest> findByRoomIdOrderByCreatedAtAsc(String roomId);

    List<HelpRequest> findByRoomIdAndStatus(String roomId, HelpStatus status);

    List<HelpRequest> findByRoomIdAndAttendeePseudo(String roomId, String attendeePseudo);
}
