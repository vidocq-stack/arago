-- Co-speakers who may co-manage a room (arago-spec §17.3). The room's owner stays the primary admin
-- (only they can end/delete the room and invite/exclude co-speakers). Matched by allowlist email;
-- speaker_sub is filled opportunistically once known.
CREATE TABLE room_managers (
    id            VARCHAR(64)  PRIMARY KEY,
    room_id       VARCHAR(64)  NOT NULL,
    speaker_email VARCHAR(256) NOT NULL,
    speaker_sub   VARCHAR(128),
    added_at      TIMESTAMP    NOT NULL
);
CREATE UNIQUE INDEX uq_room_managers ON room_managers (room_id, speaker_email);
CREATE INDEX idx_room_managers_email ON room_managers (speaker_email);
