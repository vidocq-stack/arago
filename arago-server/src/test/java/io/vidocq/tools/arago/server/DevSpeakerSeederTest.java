package io.vidocq.tools.arago.server;

import io.vidocq.tools.arago.persistence.Role;
import io.vidocq.tools.arago.persistence.Speaker;
import io.vidocq.tools.arago.persistence.SpeakerRepository;
import jakarta.data.Order;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dev/demo seed provisions a single speaker into the allowlist so a Keycloak login lands as a
 * speaker with zero manual steps. It must be idempotent (a restart re-seeds nothing) and must normalise
 * the email to the same lowercase key {@link io.vidocq.tools.arago.oidc.SpeakerAllowlist} matches on.
 */
class DevSpeakerSeederTest {

    @Test
    void seedsAnAbsentSpeakerEnabledWithTheGivenRole() {
        var repo = new FakeSpeakerRepository();
        var seeder = new DevSpeakerSeeder(repo);

        boolean created = seeder.seedSpeaker(" Ada@OIDC.test ", Role.SPEAKER, "Ada Lovelace");

        assertTrue(created);
        Speaker saved = repo.findByEmail("ada@oidc.test").orElseThrow();
        assertEquals("ada@oidc.test", saved.getEmail(), "email must be trimmed + lowercased");
        assertEquals(Role.SPEAKER, saved.getRole());
        assertTrue(saved.isEnabled());
        assertEquals("Ada Lovelace", saved.getDisplayName());
        assertEquals("dev-seed", saved.getInvitedBy());
    }

    @Test
    void isIdempotentWhenTheSpeakerAlreadyExists() {
        var repo = new FakeSpeakerRepository();
        var seeder = new DevSpeakerSeeder(repo);
        seeder.seedSpeaker("ada@oidc.test", Role.SPEAKER, "Ada");

        boolean createdAgain = seeder.seedSpeaker("ADA@oidc.test", Role.ADMIN, "Ada again");

        assertFalse(createdAgain, "a second seed of the same email must be a no-op");
        assertEquals(1, repo.count());
        assertEquals(Role.SPEAKER, repo.findByEmail("ada@oidc.test").orElseThrow().getRole());
    }

    /** In-memory {@link SpeakerRepository} keyed by email; only the seeder's two operations are real. */
    private static final class FakeSpeakerRepository implements SpeakerRepository {
        private final Map<String, Speaker> byEmail = new HashMap<>();

        @Override
        public Optional<Speaker> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public <S extends Speaker> S save(S entity) {
            byEmail.put(entity.getEmail(), entity);
            return entity;
        }

        @Override
        public long count() {
            return byEmail.size();
        }

        @Override public Optional<Speaker> findByOidcSub(String oidcSub) { throw new UnsupportedOperationException(); }
        @Override public <S extends Speaker> List<S> saveAll(List<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<Speaker> findById(String id) { throw new UnsupportedOperationException(); }
        @Override public Stream<Speaker> findAll() { throw new UnsupportedOperationException(); }
        @Override public Page<Speaker> findAll(PageRequest pageRequest, Order<Speaker> order) { throw new UnsupportedOperationException(); }
        @Override public void deleteById(String id) { throw new UnsupportedOperationException(); }
        @Override public void delete(Speaker entity) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(List<? extends Speaker> entities) { throw new UnsupportedOperationException(); }
    }
}
