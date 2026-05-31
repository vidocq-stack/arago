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
 * Phase 1 (I1) acceptance: {@code V2__speakers.sql} is valid PostgreSQL DDL, the {@code speakers}
 * and {@code admin_audit} tables match the {@link Speaker}/{@link AdminAudit} entities, and the
 * allowlist constraints hold (unique email; {@code oidc_sub} nullable but unique once set).
 *
 * <p>Same JDBC approach as {@code RoomSchemaPostgresTest}: applies the production migration script
 * directly, no module-path Flyway on the test class path.</p>
 */
@Testcontainers
class SpeakerSchemaPostgresTest {

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
            s.execute("DROP TABLE IF EXISTS speakers");
            s.execute("DROP TABLE IF EXISTS admin_audit");
            s.execute(readMigration("/db/migration/V2__speakers.sql"));
        }
        return c;
    }

    private static String readMigration(String path) throws Exception {
        try (InputStream in = SpeakerSchemaPostgresTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void migration_creates_speakers_table_and_row_roundtrips() throws Exception {
        try (Connection c = migratedConnection()) {
            insertSpeaker(c, "s1", "ada@example.com", Role.SPEAKER, SpeakerStatus.ACTIVE);

            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT email, role, status, oidc_sub FROM speakers WHERE id = 's1'")) {
                assertTrue(rs.next());
                assertEquals("ada@example.com", rs.getString("email"));
                assertEquals("SPEAKER", rs.getString("role"));
                assertEquals("ACTIVE", rs.getString("status"));
                rs.getString("oidc_sub");
                assertTrue(rs.wasNull(), "oidc_sub starts NULL until first login");
            }
        }
    }

    @Test
    void email_is_unique() throws Exception {
        try (Connection c = migratedConnection()) {
            insertSpeaker(c, "s1", "dup@example.com", Role.SPEAKER, SpeakerStatus.ACTIVE);
            assertThrows(Exception.class,
                    () -> insertSpeaker(c, "s2", "dup@example.com", Role.ADMIN, SpeakerStatus.ACTIVE));
        }
    }

    @Test
    void multiple_speakers_may_have_null_oidc_sub() throws Exception {
        try (Connection c = migratedConnection()) {
            // Two not-yet-logged-in speakers both have NULL oidc_sub — must be allowed.
            insertSpeaker(c, "s1", "a@example.com", Role.SPEAKER, SpeakerStatus.ACTIVE);
            insertSpeaker(c, "s2", "b@example.com", Role.SPEAKER, SpeakerStatus.ACTIVE);
        }
    }

    @Test
    void oidc_sub_is_unique_once_set() throws Exception {
        try (Connection c = migratedConnection()) {
            insertSpeaker(c, "s1", "a@example.com", Role.SPEAKER, SpeakerStatus.ACTIVE);
            insertSpeaker(c, "s2", "b@example.com", Role.SPEAKER, SpeakerStatus.ACTIVE);
            bindSub(c, "s1", "kc-sub-1");
            assertThrows(Exception.class, () -> bindSub(c, "s2", "kc-sub-1"));
        }
    }

    @Test
    void admin_audit_row_roundtrips() throws Exception {
        try (Connection c = migratedConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO admin_audit (id, actor, action, target, ip_truncated, at) "
                             + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, "a1");
            ps.setString(2, "superadmin");
            ps.setString(3, "speaker.create");
            ps.setString(4, "s1");
            ps.setString(5, "203.0.113.0/24");
            ps.setObject(6, Instant.now().atOffset(ZoneOffset.UTC));
            ps.executeUpdate();

            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT action FROM admin_audit WHERE id = 'a1'")) {
                assertTrue(rs.next());
                assertEquals("speaker.create", rs.getString("action"));
            }
        }
    }

    private static void insertSpeaker(Connection c, String id, String email, Role role, SpeakerStatus status)
            throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO speakers (id, email, role, status, invited_by, invited_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, email);
            ps.setString(3, role.name());
            ps.setString(4, status.name());
            ps.setString(5, "superadmin");
            ps.setObject(6, Instant.now().atOffset(ZoneOffset.UTC));
            ps.executeUpdate();
        }
    }

    private static void bindSub(Connection c, String id, String sub) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE speakers SET oidc_sub = ? WHERE id = ?")) {
            ps.setString(1, sub);
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }
}
