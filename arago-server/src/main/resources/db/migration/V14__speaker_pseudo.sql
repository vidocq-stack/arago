-- Speaker self-chosen pseudo (arago-spec §17.3): unique handle (suffixed #nnn) used as the chat
-- author name and as the key to invite a co-speaker. Nullable until the speaker sets it.
ALTER TABLE speakers ADD COLUMN pseudo VARCHAR(64);
CREATE UNIQUE INDEX uq_speakers_pseudo ON speakers (pseudo);
