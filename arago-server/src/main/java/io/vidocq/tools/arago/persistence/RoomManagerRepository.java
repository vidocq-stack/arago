package io.vidocq.tools.arago.persistence;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Jakarta Data repository for {@link RoomManager} (co-speakers, cf. arago-spec §17.3). Mansart
 * generates the implementation at compile-time; the {@code mansart-data-cdi} BCE wires it into Vauban.
 */
@Transactional
@Repository
public interface RoomManagerRepository extends BasicRepository<RoomManager, String> {

    /** Co-speakers of a room. */
    List<RoomManager> findByRoomId(String roomId);

    /** Rooms a speaker co-manages (matched by their allowlist email). */
    List<RoomManager> findBySpeakerEmail(String speakerEmail);

    /** The entry for a (room, email) pair, if any — used to authorize and to exclude. */
    List<RoomManager> findByRoomIdAndSpeakerEmail(String roomId, String speakerEmail);
}
