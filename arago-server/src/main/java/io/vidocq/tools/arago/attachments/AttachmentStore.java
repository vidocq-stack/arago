package io.vidocq.tools.arago.attachments;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * Stores chat/pin attachments as PostgreSQL blobs (cf. arago-spec §4.3/§4.4). Kept in the database so
 * the runtime container stays stateless; RGPD purge ({@link #deleteExpired}) reuses {@code purge_after}.
 *
 * <p>Plain JDBC over the pooled {@link DataSource} (contributed by mansart-pool at runtime, hence the
 * {@code Instance<>} indirection like {@code FlywayMigrator}): Mansart's APT is for entity repositories,
 * not raw {@code BYTEA}, so byte payloads go through SQL directly.</p>
 */
@ApplicationScoped
public class AttachmentStore {

    @Inject
    Instance<DataSource> dataSource;

    public void save(String id, String roomId, String kind, String contentType, String filename,
                     byte[] data, Instant createdAt, Instant purgeAfter) {
        String sql = "INSERT INTO attachments "
                + "(id, room_id, kind, content_type, filename, size_bytes, data, created_at, purge_after) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = dataSource.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, roomId);
            ps.setString(3, kind);
            ps.setString(4, contentType);
            ps.setString(5, filename);
            ps.setInt(6, data.length);
            ps.setBytes(7, data);
            ps.setTimestamp(8, Timestamp.from(createdAt));
            ps.setTimestamp(9, purgeAfter == null ? null : Timestamp.from(purgeAfter));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("attachment save failed", e);
        }
    }

    public Optional<Loaded> load(String id) {
        String sql = "SELECT kind, content_type, filename, data FROM attachments WHERE id = ?";
        try (Connection c = dataSource.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Loaded(rs.getString("kind"), rs.getString("content_type"),
                        rs.getString("filename"), rs.getBytes("data")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("attachment load failed", e);
        }
    }

    /** Deletes every attachment of a room (cascade when the room is deleted, §17.2). */
    public int deleteByRoom(String roomId) {
        try (Connection c = dataSource.get().getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM attachments WHERE room_id = ?")) {
            ps.setString(1, roomId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("attachment delete-by-room failed", e);
        }
    }

    /** Deletes attachments past their {@code purge_after} (RGPD retention); returns how many were removed. */
    public int deleteExpired(Instant now) {
        String sql = "DELETE FROM attachments WHERE purge_after IS NOT NULL AND purge_after < ?";
        try (Connection c = dataSource.get().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(now));
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("attachment purge failed", e);
        }
    }

    /** A loaded attachment ready to serve. */
    public record Loaded(String kind, String contentType, String filename, byte[] data) {}
}
