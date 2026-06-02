# Arago — Phase 2 (spec §11 : pins riches & modération & validation email)

> NB : le **Mode LAB (spec Phase 3)** — layout BLOCKS, sélection de siège, vue top-down, demandes
> d'aide — a été livré pendant la Phase 1 (hors ordre). Phase 2 = les 3 sous-features ci-dessous,
> indépendantes.

## (a) Validation email par magic-link — LIVRÉ 2026-06-02
Au 1er message persistant d'un attendee dont l'email n'est pas validé : le message est **retenu**
(`ChatMessage.validated=false`) et un magic-link de validation est envoyé (Mailer). Suivre le lien
(`GET /api/profile/validate?token=`) valide l'email (`AttendeeProfile.emailValidated`) et active les
messages persistants retenus. Réutilise `ProfileTokens` (`aud=arago-profile`) + `Mailer` (LoggingMailer,
pas de SMTP) + dev-expose pour les tests.
- Migration `V9__email_validation.sql` (`email_validated`, `validated`).
- `RoomSocket.handleChat` (held + envoi 1er persistant), `ProfileDataService.validateEmail`
  (+ `markValidatedInPlace`, `validated` dans `MessageView`), `ProfileResource GET /validate`.
- Tests : unit `markValidatedInPlace` ; acceptance `email-validation.feature` (persistant `validated:false`
  → validate `messagesActivated:1` → `validated:true`). Vert : unit + acceptance **38/38**.
- Gating de la vue speaker sur `validated` : à câbler quand la vue/réponse-speaker existera (différé).

## (b) Modération kick/mute — LIVRÉ 2026-06-02
Le speaker propriétaire mute/kick un attendee **par pseudo** (§7). Endpoints owner-only
`POST /api/rooms/{id}/moderation/{mute,unmute,kick} {pseudo}` (`RoomResource`, réutilise
`requireProvisionedSpeaker` + `ownedRoomOrAbort`). État **en mémoire** par room dans `RoomSocket`
(`mutedByRoom`/`bannedByRoom`) : mute = drop + frame `moderation` au muté ; kick = ferme les sockets +
ban rejoin (`onOpen` refuse un pseudo banni). Frame `{"type":"moderation","action":...,"pseudo":...}`.
- Tests : `moderation.feature` (mute → B ne reçoit pas ; unmute → B reçoit ; kick → socket A fermée ;
  401 sans token speaker). RestSteps : chat sur conn nommée + `WebSocket "X" is closed within N s`.
  Vert : acceptance **40/40**. (Pas de test unit : logique WS-stateful, couverte par l'acceptance réelle.)
- Différés : modération **superadmin** (§10.2) ; **persistance** de l'état (survie au restart) ; nettoyage
  à la clôture ; UI speaker (endpoints pilotables via REST en attendant).

## (c) Pins riches
### (c1) Réordonnancement — LIVRÉ 2026-06-02
`PUT /api/rooms/{id}/pins/order {ids:[...]}` (owner-only, réutilise `requireProvisionedSpeaker` +
`ownedRoomOrAbort`) → réassigne `orderIndex` par position (id étranger → 400 ; pins non listés appendés),
diffuse `RoomSocket.pinReorderEvent` (frame `{type:pin,action:reorder,ids:[...]}`), renvoie la liste
`PinView` réordonnée. Tests : scénario `pins.feature` (3 pins → reorder → GET ordre) ; `RestSteps` :
`I PUT … with body:` + `the response body lists "X" before "Y"`. Vert : acceptance **41/41**. Pas de
migration (réutilise `order_index`). UI drag&drop speaker = ultérieure (pas d'UI speaker).

### (c2) Pins URL + preview OpenGraph — LIVRÉ 2026-06-02
À la création d'un pin `type=URL`, fetch serveur **synchrone best-effort** de la page + parse
`og:title|image|description` (fallback `<title>`), stocké sur le pin (migration **V10**) + exposé dans
`PinView` + diffusé dans `pinEvent`. **Durci SSRF** (`SsrfGuard`) : http(s) only, refus
loopback/any/link/site-local/multicast + 100.64/10 + fc00::/7 + dé-map IPv4-mapped (hôte ET chaque
redirection, suivies manuellement ≤3) ; timeout ~3 s ; corps ≤256 KB ; `text/html` only ; cache mémoire
borné. Flag `arago.pins.preview.allow-private` (défaut false ; true en test pour le serveur local 127.0.0.1).
- `io.vidocq.tools.arago.pins` : `SsrfGuard`, `OpenGraph` (parser hand-rollé), `OgPreviewFetcher` (`java.net.http`).
- Tests : unit `SsrfGuardTest` (IPs bloquées/autorisées) + `OpenGraphParserTest` ; acceptance
  `pins-preview.feature` (serveur OG local JDK `com.sun.net.httpserver` → pin URL → preview title).
  Vert : unit 5/5, acceptance **42/42**.
- Limite assumée (notée) : fenêtre TOCTOU/DNS-rebinding (connect-par-IP-validée = durcissement ultérieur).

## Phase 2 — COMPLÈTE (a validation email, b modération, c1 reorder, c2 preview) ✅

## Différés (hors Phase 2 / dépendances)
`SmtpMailer` réel (décision zéro-dép SMTP) + envoi async du mail de validation ; spec concurrency
`@Scheduled` (le `PurgeScheduler` est un stopgap maison) ; réponse speaker par email + vue questions
persistantes (déclenchera le gating `validated`).
