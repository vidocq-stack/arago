package io.vidocq.tools.arago.persistence;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.util.Optional;

/**
 * Jakarta Data repository for the speaker allowlist ({@link Speaker}). Mansart generates the
 * implementation at compile-time (APT); the {@code mansart-data-cdi} BCE wires it into Vauban.
 *
 * <p>{@link #findByEmail(String)} resolves the allowlist at OIDC login; {@link #findByOidcSub(String)}
 * resolves an already-bound identity. CRUD and {@code findAll()} (as a {@code Stream}) come from
 * {@link BasicRepository}.</p>
 */
@Transactional
@Repository
public interface SpeakerRepository extends BasicRepository<Speaker, String> {

    Optional<Speaker> findByEmail(String email);

    Optional<Speaker> findByOidcSub(String oidcSub);

    long count();
}
