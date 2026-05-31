-- Arago — speaker allowlist + admin audit (Phase 1, I1: auth foundation).
-- Speakers authenticate via Keycloak (OIDC); this table is the authorization
-- allowlist managed by the superadmin (cf. arago-spec §4.2/§4.8). The superadmin
-- itself is NOT stored here — its credentials live only in env vars.

CREATE TABLE speakers (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    email          VARCHAR(320) NOT NULL,
    oidc_sub       VARCHAR(255),
    role           VARCHAR(16)  NOT NULL,
    enabled        BOOLEAN      NOT NULL,
    display_name   VARCHAR(200),
    invited_by     VARCHAR(64)  NOT NULL,
    invited_at     TIMESTAMPTZ  NOT NULL,
    first_login_at TIMESTAMPTZ,
    last_seen_at   TIMESTAMPTZ
);

-- Email is the invitation/OIDC-matching key — globally unique.
CREATE UNIQUE INDEX ux_speakers_email ON speakers (email);

-- One Keycloak identity maps to at most one allowlist entry. oidc_sub is NULL
-- until the first login; PostgreSQL allows multiple NULLs under a unique index.
CREATE UNIQUE INDEX ux_speakers_oidc_sub ON speakers (oidc_sub);

-- Audit trail of superadmin actions (cf. §4.8/§10.2). No secrets/passwords/emails.
CREATE TABLE admin_audit (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    actor        VARCHAR(64)  NOT NULL,
    action       VARCHAR(64)  NOT NULL,
    target       VARCHAR(128),
    ip_truncated VARCHAR(64),
    at           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_admin_audit_at ON admin_audit (at);
