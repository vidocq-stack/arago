-- Local speaker authentication (replaces Keycloak/OIDC). The speaker now logs in with email +
-- password; the password is stored as a self-describing PBKDF2 PHC hash (cf. PasswordHasher), the
-- same scheme as the superadmin. The admin sets an initial password at creation; the speaker can
-- change it afterwards.
--
-- oidc_sub becomes vestigial: kept nullable for history, no longer read or written. The room owner
-- and co-speaker subject are now the speaker's primary key (speakers.id), not a Keycloak sub.
ALTER TABLE speakers ADD COLUMN password_hash VARCHAR(255);
