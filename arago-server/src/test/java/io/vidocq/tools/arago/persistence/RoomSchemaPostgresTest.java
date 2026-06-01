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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 0 acceptance: the {@code V1__init.sql} migration is valid PostgreSQL DDL, the {@code rooms}
 * table matches the {@link Room} entity, and the live-PIN uniqueness partial index behaves as
 * specified (DRAFT/ACTIVE unique, ENDED excluded).
 *
 * <p>The migration script is applied with plain JDBC — the very same resource Flyway runs at
 * startup ({@code FlywayMigrator}) — so this test exercises the schema against the production
 * backend (Testcontainers PostgreSQL, test scope only; see CLAUDE.md) without dragging the
 * module-path-only Flyway runtime onto the test class path.
 */
@Testcontainers
class RoomSchemaPostgresTest {

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
            // The container is shared across tests; start each from a clean slate, then apply the
            // rooms migrations in order (V1 create + V3 Phase 1 columns), as Flyway does at startup.
            s.execute("DROP TABLE IF EXISTS rooms");
            s.execute(readMigration("/db/migration/V1__init.sql"));
            s.execute(readMigration("/db/migration/V3__rooms_phase1.sql"));
        }
        return c;
    }

    private static String readMigration(String resource) throws Exception {
        try (InputStream in = RoomSchemaPostgresTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void migration_creates_rooms_table_and_row_roundtrips() throws Exception {
        try (Connection c = migratedConnection()) {
            insertRoom(c, "r1", "123456", "Mon lab", RoomStatus.ACTIVE);

            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT pin, title, status FROM rooms WHERE id = 'r1'")) {
                assertTrue(rs.next(), "the inserted room should be readable back");
                assertEquals("123456", rs.getString("pin"));
                assertEquals("Mon lab", rs.getString("title"));
                assertEquals("ACTIVE", rs.getString("status"));
            }
        }
    }

    @Test
    void live_pin_uniqueness_excludes_ended_rooms() throws Exception {
        try (Connection c = migratedConnection()) {
            insertRoom(c, "live1", "999000", "Room A", RoomStatus.ACTIVE);

            // Same PIN on an ACTIVE/DRAFT room must be rejected by the partial unique index.
            assertThrows(Exception.class,
                    () -> insertRoom(c, "live2", "999000", "Room B", RoomStatus.DRAFT));

            // …but an ENDED room may reuse the PIN (released after close).
            insertRoom(c, "ended1", "999000", "Room C", RoomStatus.ENDED);
        }
    }

    /** Mirrors the columns declared by {@link Room} — fails fast if the entity/schema drift apart. */
    private static void insertRoom(Connection c, String id, String pin, String title, RoomStatus status)
            throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO rooms (id, pin, title, status, owner_sub, mode, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, pin);
            ps.setString(3, title);
            ps.setString(4, status.name());
            ps.setString(5, "owner-" + id);
            ps.setString(6, RoomMode.CONF.name());
            ps.setObject(7, Instant.now().atOffset(ZoneOffset.UTC));
            ps.executeUpdate();
        }
    }
}
