# Arago — Phase 5 (Historique & exports §11)

## Livré 2026-06-02
Vue « événement passé » : replay du chat, stats, et exports Markdown (chat) / CSV (help requests).
Endpoints REST owner-only (`requireProvisionedSpeaker` + `ownedRoomOrAbort`), aucun nouveau schéma
(la donnée existe : `ChatMessage`, `HelpRequest`). Construction Markdown/CSV via util **pur**
`io.vidocq.tools.arago.history.Exports` (unit-testable).

- **Backend** (`RoomResource`, `@Inject ChatMessageRepository messages`) :
  - `GET /api/rooms/{id}/chat` → `List<ChatView>(author, body, persistent, validated, at)` (replay).
  - `GET /api/rooms/{id}/stats` → `RoomStats(messages, persistentMessages, helpTotal, helpResolved, attendees)`.
  - `GET /api/rooms/{id}/chat/export.md` → `text/markdown`, `Content-Disposition: attachment; filename="chat-{pin}.md"`.
  - `GET /api/rooms/{id}/help/export.csv` → `text/csv`, attachment `help-{pin}.csv`
    (en-tête `id,attendee,position,status,createdAt,updatedAt,message` ; échappement RFC4180 :
    guillemets doublés, champ entre guillemets).
- **Util** `Exports` : `chatMarkdown(title, pin, msgs)`, `helpCsv(helps)`, `csvCell(s)`.
- **Console** (`Speaker.svelte`) : panneau « Historique & export » — `room-stats` (compteurs chargés au
  `openRoom` via `GET …/stats`) ; boutons `export-chat` / `export-help` → `download(path, filename)`
  (fetch Bearer → blob → `<a download>` synthétique : téléchargement authentifié, pas un `<a href>`).

## Tests
- **Unit** `ExportsTest` (3) : `csvCell` (échappe `"`/`,`/retour ligne) ; `helpCsv` (en-tête + ligne) ;
  `chatMarkdown` (body + pseudo). Vert : arago-server **65** unit.
- **Acceptance** `history.feature` (non-@ui, user realm `sam`) : room CONF + attendee message persistant (WS)
  + help raise/claim/resolve, puis `GET …/chat` (contient le message), `GET …/stats`
  (`messages`=1, `helpResolved`=1), `GET …/chat/export.md` (`# titre` + message),
  `GET …/help/export.csv` (en-tête `status` + `RESOLVED`), et owner-only `GET …/stats` sans token → 401.
  Vert : non-@ui **48/48** (était 46), @ui **9/9** inchangé.

## Hors périmètre (notés)
Rétention **par tenant/event** (Arago mono-tenant ; rétention globale `arago.retention.*` + `PurgeScheduler`
en place) ; replay chat visuel riche côté UI (on expose l'historique JSON + les exports) ; stats avancées.
Transverses déjà notés : vue reveal complète attendee, WS speaker RS256 natif, modération superadmin,
persistance modération, durcissement TOCTOU preview, `SmtpMailer`, spec concurrency `@Scheduled`.
