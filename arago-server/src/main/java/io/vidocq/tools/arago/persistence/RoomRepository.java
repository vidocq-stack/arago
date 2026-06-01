package io.vidocq.tools.arago.persistence;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Jakarta Data repository for {@link Room}. Mansart generates the implementation at compile-time
 * (APT) and the {@code mansart-data-cdi} BCE wires it into Vauban as a singleton bean.
 */
@Transactional
@Repository
public interface RoomRepository extends BasicRepository<Room, String> {

    long count();

    long countByStatus(RoomStatus status);

    Optional<Room> findByPin(String pin);

    /** Rooms owned by a speaker (their OIDC subject), most recent first. */
    List<Room> findByOwnerSubOrderByCreatedAtDesc(String ownerSub);
}
