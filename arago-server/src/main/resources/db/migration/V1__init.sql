-- Arago — initial schema (Phase 0).
-- Only the `rooms` aggregate is created here, enough to prove the
-- PostgreSQL + Mansart + Flyway pipeline. The rest of the model
-- (attendees, chat, pins, help requests, profiles…) lands in later phases.

CREATE TABLE rooms (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    pin        VARCHAR(6)   NOT NULL,
    title      VARCHAR(200) NOT NULL,
    status     VARCHAR(16)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

-- A PIN must be unique among joinable rooms (DRAFT + ACTIVE); ENDED rooms
-- release their PIN, so they are excluded from the uniqueness constraint.
CREATE UNIQUE INDEX ux_rooms_pin_live
    ON rooms (pin)
    WHERE status IN ('DRAFT', 'ACTIVE');
