-- Arago — Phase 1: room lifecycle fields (cf. arago-spec §4.1, §7).
-- Adds the owning speaker (OIDC subject), the presentation mode, and the ended-at timestamp.
-- The table is empty on a fresh deployment; defaults keep the migration safe if rows exist.

ALTER TABLE rooms ADD COLUMN owner_sub VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE rooms ADD COLUMN mode      VARCHAR(16)  NOT NULL DEFAULT 'CONF';
ALTER TABLE rooms ADD COLUMN ended_at  TIMESTAMPTZ;

-- Drop the transitional defaults: every room created from Phase 1 on sets these explicitly.
ALTER TABLE rooms ALTER COLUMN owner_sub DROP DEFAULT;
ALTER TABLE rooms ALTER COLUMN mode      DROP DEFAULT;

-- Owner lookups (list my rooms) hit owner_sub.
CREATE INDEX ix_rooms_owner_sub ON rooms (owner_sub);
