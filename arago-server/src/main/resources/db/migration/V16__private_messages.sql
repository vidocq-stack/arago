-- Private messages (DM) between an attendee and the room's speakers — a shared speaker inbox: every
-- speaker/co-speaker of the room sees the thread and any of them may reply (cf. chat §4.3).
--
-- dm_attendee = the attendee pseudo that owns the private thread. NULL = a normal global room message,
-- so existing rows and the public chat are unaffected. Direction is the existing from_speaker flag.
ALTER TABLE chat_messages ADD COLUMN dm_attendee VARCHAR(64);
CREATE INDEX ix_chat_dm ON chat_messages (room_id, dm_attendee);
