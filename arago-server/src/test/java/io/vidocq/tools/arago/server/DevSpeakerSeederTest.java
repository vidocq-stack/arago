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

    @Test
    void parsesACommaSeparatedListWithPerEntryRoleOverride() {
        var entries = DevSpeakerSeeder.parseSeedSpec(
                " speakera@oidc.test=ADMIN , speakerb@oidc.test ", Role.SPEAKER, null);

        assertEquals(2, entries.size());
        assertEquals("speakera@oidc.test", entries.get(0).email());
        assertEquals(Role.ADMIN, entries.get(0).role(), "explicit =ADMIN overrides the default");
        assertEquals("speakera", entries.get(0).displayName(), "name derived from the email local part");
        assertEquals("speakerb@oidc.test", entries.get(1).email());
        assertEquals(Role.SPEAKER, entries.get(1).role(), "no override -> default role");
    }

    @Test
    void parseSeedSpecHonoursAnExplicitDefaultNameAndSkipsBlankTokens() {
        var entries = DevSpeakerSeeder.parseSeedSpec("a@x , , b@x ", Role.ADMIN, "Demo");

        assertEquals(2, entries.size());
        assertEquals(Role.ADMIN, entries.get(0).role(), "blank role falls back to the default");
        assertEquals("Demo", entries.get(0).displayName(), "explicit default name wins over derivation");
    }

    @Test
    void seedsEveryParsedEntryExactlyOnce() {
        var repo = new FakeSpeakerRepository();
        var seeder = new DevSpeakerSeeder(repo);

        for (var e : DevSpeakerSeeder.parseSeedSpec("a@x=ADMIN,b@x", Role.SPEAKER, null)) {
            seeder.seedSpeaker(e.email(), e.role(), e.displayName());
        }

        assertEquals(2, repo.count());
        assertEquals(Role.ADMIN, repo.findByEmail("a@x").orElseThrow().getRole());
        assertEquals(Role.SPEAKER, repo.findByEmail("b@x").orElseThrow().getRole());
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
