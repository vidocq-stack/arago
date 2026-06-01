-- Arago — Phase 1: LAB seating layout + seat locking (cf. arago-spec §4.5).
-- A LAB/HYBRID room carries a BLOCKS layout (stored as JSON). Attendees lock a seat first-come-first-
-- serve; help requests snapshot the seat coordinates so the speaker knows where to go.

-- The room's seating layout (BLOCKS), serialized as JSON; null for CONF rooms.
ALTER TABLE rooms ADD COLUMN layout_json TEXT;

-- One row per seat an attendee holds (or held). released = false is the live hold.
CREATE TABLE seats (
    id               VARCHAR(36) NOT NULL PRIMARY KEY,
    room_id          VARCHAR(36) NOT NULL,
    attendee_pseudo  VARCHAR(80) NOT NULL,
    seat_row         INTEGER     NOT NULL,
    seat_block_index INTEGER     NOT NULL,
    seat_in_block    INTEGER     NOT NULL,
    released         BOOLEAN     NOT NULL,
    taken_at         TIMESTAMPTZ NOT NULL,
    released_at      TIMESTAMPTZ
);

-- First-come-first-serve: a coordinate can be held by at most one attendee at a time. The partial
-- index ignores released seats, so a freed seat can be re-taken (and history is preserved).
CREATE UNIQUE INDEX ux_seats_active
    ON seats (room_id, seat_row, seat_block_index, seat_in_block)
    WHERE released = false;

-- Fast lookup of a room's live seats (top-down view) and an attendee's current seat (move/release).
CREATE INDEX ix_seats_room_live ON seats (room_id) WHERE released = false;

-- Help requests snapshot the requester's seat coordinates at raise time (§4.5); null when unseated.
ALTER TABLE help_requests ADD COLUMN seat_row         INTEGER;
ALTER TABLE help_requests ADD COLUMN seat_block_index INTEGER;
ALTER TABLE help_requests ADD COLUMN seat_in_block    INTEGER;
