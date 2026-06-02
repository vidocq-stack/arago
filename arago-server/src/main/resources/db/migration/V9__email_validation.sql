-- Phase 2: attendee email validation by magic link (arago-spec §4.7/§10.1).
-- An attendee's email is unvalidated until they follow a validation magic link; a persistent message
-- from an unvalidated email is held (validated = false) until then. Existing rows + every ephemeral
-- message are "active" (validated = true by default).
ALTER TABLE attendee_profiles ADD COLUMN email_validated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_messages     ADD COLUMN validated       BOOLEAN NOT NULL DEFAULT TRUE;
