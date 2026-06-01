package io.vidocq.tools.arago.persistence;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.util.Optional;

/**
 * Jakarta Data repository for {@link AttendeeProfile} (Mansart-generated impl). Lookup by email so a
 * returning attendee reuses their existing profile instead of creating a duplicate.
 */
@Transactional
@Repository
public interface AttendeeProfileRepository extends BasicRepository<AttendeeProfile, String> {

    Optional<AttendeeProfile> findByEmail(String email);
}
