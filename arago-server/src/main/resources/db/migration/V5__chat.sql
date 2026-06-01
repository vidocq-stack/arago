-- Arago — Phase 1: chat messages (cf. arago-spec §4.3, §4.7).
-- References (room_id, optional profile_id) are plain id columns, consistent with the rest of the
-- model. Ephemeral messages carry purge_after; persistent ones (null purge_after) survive closure.

CREATE TABLE chat_messages (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    room_id       VARCHAR(36)   NOT NULL,
    profile_id    VARCHAR(36),
    author_pseudo VARCHAR(80)   NOT NULL,
    from_speaker  BOOLEAN       NOT NULL,
    persistent    BOOLEAN       NOT NULL,
    body          VARCHAR(2000) NOT NULL,
    at            TIMESTAMPTZ   NOT NULL,
    purge_after   TIMESTAMPTZ
);

-- Room history is read oldest-first on join.
CREATE INDEX ix_chat_messages_room_at ON chat_messages (room_id, at);

-- The daily purge scans ephemeral messages past their purge instant.
CREATE INDEX ix_chat_messages_purge ON chat_messages (purge_after) WHERE persistent = false;
