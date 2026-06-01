-- Arago — Phase 1: attendee profiles (cf. arago-spec §4.2/§4.7).
-- An attendee who joins with an email gets a profile (cross-room identity, keyed by email).
-- Pseudo-only attendees have no row. Consent (the GDPR checkbox) is recorded inline.

CREATE TABLE attendee_profiles (
    id                    VARCHAR(36)  NOT NULL PRIMARY KEY,
    email                 VARCHAR(320) NOT NULL UNIQUE,
    pseudo                VARCHAR(80)  NOT NULL,
    consent_text_version  VARCHAR(40),
    consent_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL
);
