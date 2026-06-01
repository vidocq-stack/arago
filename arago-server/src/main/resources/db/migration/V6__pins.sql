-- Arago — Phase 1: pinned content (cf. arago-spec §4.4).
-- Speaker-pinned blocks (TEXT/URL/CODE/SECRET), visible to the whole room, ordered for display.
-- SECRET pins are purged when the room closes (handled in the app, not here).

CREATE TABLE pins (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    room_id     VARCHAR(36)   NOT NULL,
    type        VARCHAR(16)   NOT NULL,
    content     VARCHAR(8000) NOT NULL,
    lang        VARCHAR(40),
    order_index INTEGER       NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL
);

-- Pins are read in display order per room.
CREATE INDEX ix_pins_room_order ON pins (room_id, order_index);
