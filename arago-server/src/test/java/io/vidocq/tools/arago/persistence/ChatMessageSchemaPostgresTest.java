package io.vidocq.tools.arago.persistence;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 acceptance: the {@code V5__chat.sql} migration is valid PostgreSQL DDL, the
 * {@code chat_messages} table matches the {@link ChatMessage} entity, and the purge-selection
 * predicate (ephemeral + past purge instant) picks exactly the expired ephemeral rows — the set the
 * daily purge job (§4.7) deletes. Raw JDBC against the production backend (Testcontainers PostgreSQL).
 */
@Testcontainers
class ChatMessageSchemaPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("arago")
                    .withUsername("arago")
                    .withPassword("arago");

    private static Connection migratedConnection() throws Exception {
        Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try (Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS chat_messages");
            s.execute(readMigration());
        }
        return c;
    }

    private static String readMigration() throws Exception {
        try (InputStream in = ChatMessageSchemaPostgresTest.class
                .getResourceAsStream("/db/migration/V5__chat.sql")) {
            assertNotNull(in, "V5__chat.sql must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void migration_creates_chat_table_and_row_roundtrips() throws Exception {
        try (Connection c = migratedConnection()) {
            Instant at = Instant.now();
            insert(c, "m1", "room1", "p1", "Zoe", false, true, "hello **world**", at, null);

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT room_id, profile_id, author_pseudo, from_speaker, persistent, body, purge_after "
                            + "FROM chat_messages WHERE id = ?")) {
                ps.setString(1, "m1");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "row should exist");
                    assertEquals("room1", rs.getString("room_id"));
                    assertEquals("p1", rs.getString("profile_id"));
                    assertEquals("Zoe", rs.getString("author_pseudo"));
                    assertFalse(rs.getBoolean("from_speaker"));
                    assertTrue(rs.getBoolean("persistent"));
                    assertEquals("hello **world**", rs.getString("body"));
                    assertNull(rs.getObject("purge_after"));
                }
            }
        }
    }

    @Test
    void purge_selection_picks_only_expired_ephemeral() throws Exception {
        try (Connection c = migratedConnection()) {
            Instant now = Instant.now();
            // persistent (never purged) — purge_after null
            insert(c, "keep-persistent", "r", null, "Ada", true, true, "keep me", now, null);
            // ephemeral, still fresh — purge_after in the future
            insert(c, "keep-fresh", "r", null, "Bob", false, false, "not yet", now, now.plusSeconds(3600));
            // ephemeral, expired — purge_after in the past → the only one selected
            insert(c, "purge-me", "r", null, "Ced", false, false, "old", now, now.minusSeconds(3600));

            List<String> selected = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM chat_messages WHERE persistent = false AND purge_after < ?")) {
                ps.setObject(1, now.atOffset(ZoneOffset.UTC));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        selected.add(rs.getString("id"));
                    }
                }
            }
            assertEquals(List.of("purge-me"), selected,
                    "only the expired ephemeral message must be selected for purge");
        }
    }

    private static void insert(Connection c, String id, String roomId, String profileId,
                               String pseudo, boolean fromSpeaker, boolean persistent, String body,
                               Instant at, Instant purgeAfter) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO chat_messages (id, room_id, profile_id, author_pseudo, from_speaker, "
                        + "persistent, body, at, purge_after) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, roomId);
            ps.setString(3, profileId);
            ps.setString(4, pseudo);
            ps.setBoolean(5, fromSpeaker);
            ps.setBoolean(6, persistent);
            ps.setString(7, body);
            ps.setObject(8, at.atOffset(ZoneOffset.UTC));
            ps.setObject(9, purgeAfter == null ? null : purgeAfter.atOffset(ZoneOffset.UTC));
            ps.executeUpdate();
        }
    }
}
