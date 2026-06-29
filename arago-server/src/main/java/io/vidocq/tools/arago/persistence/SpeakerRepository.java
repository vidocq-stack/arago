package io.vidocq.tools.arago.persistence;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.util.Optional;

/**
 * Jakarta Data repository for the speaker allowlist ({@link Speaker}). Mansart generates the
 * implementation at compile-time (APT); the {@code mansart-data-cdi} BCE wires it into Vauban.
 *
 * <p>{@link #findByEmail(String)} resolves the account at login; {@link #findByPseudo(String)} resolves
 * a co-speaker invite handle. The owner/co-speaker subject is the entity id, so {@code findById} (from
 * {@link BasicRepository}) resolves it. CRUD and {@code findAll()} (as a {@code Stream}) come from
 * {@link BasicRepository}.</p>
 */
@Transactional
@Repository
public interface SpeakerRepository extends BasicRepository<Speaker, String> {

    Optional<Speaker> findByEmail(String email);

    Optional<Speaker> findByPseudo(String pseudo);

    long count();
}
