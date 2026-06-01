-- Arago — Phase 1: LAB help requests (cf. arago-spec §4.5).
-- An attendee raises a "need help" request (real-time over the room WebSocket); a speaker claims and
-- resolves it. position is an optional free-form seat hint (full seat-assignment layout lands later).

CREATE TABLE help_requests (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    room_id         VARCHAR(36)  NOT NULL,
    attendee_pseudo VARCHAR(80)  NOT NULL,
    position        VARCHAR(80),
    message         VARCHAR(140),
    status          VARCHAR(16)  NOT NULL,
    claimed_by      VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

-- Speaker panel reads a room's requests oldest-first; anti-spam checks a room's active set.
CREATE INDEX ix_help_room_created ON help_requests (room_id, created_at);
CREATE INDEX ix_help_room_status ON help_requests (room_id, status);
