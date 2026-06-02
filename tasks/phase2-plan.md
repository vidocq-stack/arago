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

## (b) Modération kick/mute — À FAIRE
Endpoints speaker (`POST /api/rooms/{id}/kick|mute {pseudo}`) + enforcement temps réel `RoomSocket`
(fermer la socket kické, drop les messages d'un muté, frame `moderation`). UI speaker ultérieure.

## (c) Pins riches — À FAIRE
Réordonnancement (`PUT …/pins/order`) + pins URL avec preview OpenGraph (fetch serveur best-effort +
cache ; **attention SSRF** : allowlist http(s), refuser IP privées). Prérequis utile : une surface pins
côté speaker (pas encore d'UI speaker « anime ma room »).

## Différés (hors Phase 2 / dépendances)
`SmtpMailer` réel (décision zéro-dép SMTP) + envoi async du mail de validation ; spec concurrency
`@Scheduled` (le `PurgeScheduler` est un stopgap maison) ; réponse speaker par email + vue questions
persistantes (déclenchera le gating `validated`).
