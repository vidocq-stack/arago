-- Phase 4: reveal.js remote-control session (arago-spec §4.6). The reveal deck plugin connects to the
-- room WebSocket with this secret; null until the speaker enables remote control on the room.
ALTER TABLE rooms ADD COLUMN reveal_secret VARCHAR(64);
