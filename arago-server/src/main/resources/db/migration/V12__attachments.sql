-- Chat/pin attachments stored as PostgreSQL blobs (arago-spec §4.3/§4.4 — images, files, QR sources).
-- Kept in the DB so the runtime container stays stateless; RGPD purge reuses purge_after (§4.7).
CREATE TABLE attachments (
    id           VARCHAR(64)  PRIMARY KEY,
    room_id      VARCHAR(64)  NOT NULL,
    kind         VARCHAR(16)  NOT NULL,   -- image | file
    content_type VARCHAR(128) NOT NULL,
    filename     VARCHAR(256),
    size_bytes   INTEGER      NOT NULL,
    data         BYTEA        NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    purge_after  TIMESTAMP
);
CREATE INDEX idx_attachments_room ON attachments (room_id);
CREATE INDEX idx_attachments_purge ON attachments (purge_after);

-- A chat message may carry one attachment (image rendered inline, file as a download link).
ALTER TABLE chat_messages ADD COLUMN attachment_id   VARCHAR(64);
ALTER TABLE chat_messages ADD COLUMN attachment_kind VARCHAR(16);
ALTER TABLE chat_messages ADD COLUMN attachment_name VARCHAR(256);
