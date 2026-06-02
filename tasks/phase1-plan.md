# Arago — Plan Phase 1

> Phase 1 (spec §11) = Room + chat + email opt-in, ~2 sprints. On la découpe en **4 incréments
> livrables** (chacun = binaire qui boote vert + tests). On attaque par le **socle auth** (I1),
> puis le cœur démo (I2 room, I3 chat/WS), puis le RGPD (I4).
>
> TDD obligatoire (rouge → vert → refactor). Commits atomiques, conventional commits, en anglais.
> Pas de dépendance hors stack §5 sans justification (Argon2id = la seule, cf. spec §5).

---

## I1 — Socle auth (superadmin + allowlist speakers + OIDC) ← on commence ici

**But :** un superadmin peut se loguer (creds env-vars/Argon2id) et provisionner des speakers ;
un speaker provisionné se logue en OIDC, un non-provisionné est refusé (403).

### Modules / packages touchés (`arago-server`)
- `io.vidocq.tools.arago.auth` — `PasswordHasher` (Argon2id, repli PBKDF2-JDK), `SuperadminAuth`,
  `JwtIssuer`/`JwtVerifier` (HS256, claims `role`/`aud`/`exp`), `SecurityContextFilter` (JAX-RS).
- `io.vidocq.tools.arago.persistence` — entités `Speaker`, `AdminAudit` + `SpeakerRepository`
  (Mansart, déjà câblé en Phase 0) + migration Flyway `V2__speakers.sql`.
- `io.vidocq.tools.arago.admin` — `AdminLoginResource` (`POST /api/admin/login`),
  `SpeakerAdminResource` (CRUD `/api/admin/speakers`), `AdminAuditResource`.
- `io.vidocq.tools.arago.oidc` — `OidcCallbackResource` : enforcement allowlist
  (résolution email → `Speaker`, 403 `speaker_not_provisioned`, liaison `sub` au 1er login).
- `arago hash-password` — petit goal/CLI (lecture stdin masqué, sortie PHC, jamais de clair).

### Endpoints exposés (cf. spec §8)
`POST /api/admin/login`, `GET/POST /api/admin/speakers`, `PATCH/DELETE /api/admin/speakers/{id}`,
`GET /api/admin/rooms` (stub jusqu'à I2), `GET /api/admin/audit`,
`GET /api/oidc/login`, `GET /api/oidc/callback`.

### Config (spec §14)
`ARAGO_SUPERADMIN_USER`, `ARAGO_SUPERADMIN_PASSWORD_HASH`, `arago.superadmin.token-ttl-minutes`,
`arago.attendee.hmac-secret` (réutilisée pour signer le JWT superadmin), `arago.oidc.*`.

### Tests (TDD, dans l'ordre rouge→vert)
1. `PasswordHasherTest` — verify OK / KO, constant-time, format PHC, repli PBKDF2.
2. `SuperadminAuthTest` — login OK → JWT `role=superadmin` ; mauvais mdp → 401 uniforme ;
   hash absent → endpoint désactivé (503) ; rate-limit/lockout.
3. `SpeakerAllowlistTest` — email absent → 403 `speaker_not_provisioned` ; présent+enabled →
   liaison `sub` au 1er login ; `enabled=false` → refusé.
4. `SpeakerAdminResourceTest` (JAX-RS) — CRUD complet, accès refusé sans JWT superadmin.
5. `JwtIssuerVerifierTest` — `aud`/`exp`/`role`, JWT superadmin refusé sur WS (audience).
6. Test « no-secret-in-logs » étendu : aucun mot de passe / hash dans les logs.

### Décisions à confirmer avant code
- **Lib Argon2id** : *Password4j* (pur Java, API simple) **ou** *BouncyCastle* **ou** repli
  PBKDF2-JDK zéro-dep ? (impacte le `pom` + la justification §15.3).
- **Console `/admin`** : page Svelte dans `arago-web` dès I1, ou juste les endpoints REST +
  tests en I1 et l'UI en I2 ? (proposé : endpoints d'abord, UI ensuite).
- **Stub Keycloak en test** : on teste l'enforcement allowlist avec un JWT OIDC forgé (clé de test)
  plutôt qu'un vrai Keycloak (Testcontainers Keycloak = lourd ; à réserver à un test d'intégration opt-in).

### État (MAJ 2026-06-02) — I1 livré, dont le front-channel OIDC ✅
- **Superadmin / allowlist / audit / rate-limit / OIDC back-channel** : livrés et verts (cf. `BUG.md`
  ARAGO-004 : cervantes valide le Bearer Keycloak, `/api/oidc/me` 200/403/401, superadmin sur `X-Arago-Admin`).
- **Front-channel OIDC (Authorization Code + PKCE)** — *reliquat livré 2026-06-02* :
  - Backend `io.vidocq.tools.arago.oidc` : `Pkce` (S256), `OidcFlowStore` (state + ticket, single-use+TTL,
    VT-safe), `KeycloakOidcClient` (`java.net.http`), `OidcLoginResource` (`GET /api/oidc/login` → 302
    Keycloak ; `GET /api/oidc/callback` → échange code + allowlist + cookie ticket one-time ; `POST
    /api/oidc/token` → consomme le ticket). Décision : **token livré à la SPA** (Bearer, pas de session
    serveur, pas de token dans l'URL). `requires java.net.http` ajouté au module-info.
  - Front `arago-web/App.svelte` : bouton « Se connecter (Keycloak) » + récupération one-time du token +
    `/api/oidc/me` → bannière « Connecté : … ». Hook minimal (pas de console speaker = I2).
  - Tests : `PkceTest`/`OidcFlowStoreTest` (10) ; realm de test + client public `arago-web` (standard
    flow) ; `oidc.feature` (302 PKCE) ; **@ui `oidc-login.feature`** (login Keycloak réel : judy → identité,
    bob → non provisionné). Vert : unit 10/10, acceptance non-@ui 33/33, @ui 5/5.
  - Décision « stub vs vrai Keycloak » tranchée côté **vrai Keycloak Testcontainers** (déjà en place).
- **Reste I1** : refresh token / logout / TTL long (l'access token ~600 s suffit pour l'instant) ; co-speaker.

---

## I2 — Room lifecycle + QR (DRAFT→ACTIVE, PIN unique)
`Room`/`RoomRepository` (déjà esquissés Phase 0), `RoomResource` (create/get/open/end),
génération PIN unique (DRAFT+ACTIVE), QR SVG (`/api/rooms/{id}/qrcode.svg`), page projection.
Supervision globale `/api/admin/rooms` câblée pour de vrai.

## I3 — Chat temps réel (WebSocket Chappe) ← driver de validation du WS Chappe
Hub `/ws/rooms/{pin}`, handshake JWT (speaker/attendee), `chat.send`/`chat.new`, persistance
`ChatMessage`, flag `persistent` (toggle disabled sans email), sanitization markdown centralisée.

## I4 — Email opt-in + RGPD
Join attendee (pseudo + email opt-in + **consentement non pré-coché**), `AttendeeProfile`/`Consent`,
`reply-email` speaker (sans voir l'email), magic link + page « Mes données » + droit à l'oubli,
job de purge quotidien idempotent, page `/privacy`, filtre logs (no email).

### État (MAJ 2026-06-02) — droits RGPD backend livrés ✅
- **Déjà là** : join attendee + email opt-in + consentement non-pré-coché, `AttendeeProfile`,
  `ChatMessage.persistent`, `privacy.html`.
- **Livré 2026-06-02 — droits de la personne (backend) §4.7** :
  - `io.vidocq.tools.arago.profile` : `ProfileTokens` (magic-link HS256, `aud=arago-profile`, TTL
    `arago.magic-link.ttl-minutes`=15) ; `ProfileDataService` (accès/portabilité + erasure =
    anonymise messages `profileId=null`/`authorPseudo="anonyme"` + supprime le profil) ;
    `PurgeService` (éphémères expirés) ; `ProfileResource` `/api/profile` (`POST /magic-link` 202
    anti-énumération + dev-expose, `GET /me`, `GET /me/export`, `DELETE /me`) ; `AdminPurgeResource`
    `POST /api/admin/purge/run` (superadmin + audit).
  - `io.vidocq.tools.arago.mail` : SPI `Mailer` + `LoggingMailer` (pas de SMTP ; `SmtpMailer` plus tard).
  - `ChatMessageRepository.findByProfileId` ; `opens` profile + mail ; `arago.mail.dev-expose-link`.
  - Tests : `ProfileDataServiceTest` (anonymisation) ; `profile.feature` E2E (room→attendee+email+consent
    →message persistant WS→magic-link→me/export→erasure→404 ; anti-énumération ; purge 401/200).
    Vert : unit 12/12, acceptance non-@ui **37/37**, @ui 5/5.
- **I4b — page « Mes données » livrée 2026-06-02** : entrée Vite `mes-donnees.html` + `src/MyData.svelte`
  servie à l'URL propre `/mes-donnees` (Chappe `cleanUrls`) ; landing du magic-link (lit `?token=`,
  `GET /api/profile/me`, affiche profil + messages persistants, boutons Exporter + Supprimer-avec-confirmation
  → `DELETE`). Le magic-link pointe désormais vers la page (`ProfileResource`). Test @ui `profile-ui.feature`.
  Vert : acceptance non-@ui 37/37, **@ui 6/6**.
- **Reste I4** (différé) : réponse speaker par email + `SmtpMailer` (décision zéro-dép SMTP) ; purge
  **programmée** quotidienne (pas de scheduler stack — manuel via `/api/admin/purge/run`) ; rétention
  help/pins par âge de room ENDED. (Purge des `SECRET` pins à la clôture : **déjà fait** dans `RoomResource.end`.)

---

## Ordre de commit (I1)
1. `V2__speakers.sql` + entités/repos (rouge: SpeakerAllowlistTest)
2. `PasswordHasher` + tests (vert)
3. `JwtIssuer`/`JwtVerifier` + tests
4. `AdminLoginResource` + rate-limit + audit
5. `SpeakerAdminResource` CRUD
6. `OidcCallbackResource` enforcement allowlist
7. `arago hash-password` CLI
8. doc README (section « bootstrap superadmin »)
