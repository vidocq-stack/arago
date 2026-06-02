# Arago — Phase 4 (Mode CONF remote : pilotage reveal.js §4.6)

## Livré 2026-06-02 (v1 complet)
Pilotage à distance d'une deck reveal.js projetée sur place. Design : **état = WS** (le plugin connecté
en `?secret=` émet `reveal.state`, diffusé) ; **commandes = REST owner-only** (`reveal.cmd` diffusé au
plugin). Aucune auth-rôle sur le WS, aucune dépendance externe (pas de reveal.js embarqué).

- **Backend** : `Room.revealSecret` (V11) ; `POST /api/rooms/{id}/reveal/enable` (owner → `{secret,pin}`,
  idempotent) ; `POST /api/rooms/{id}/reveal/cmd {cmd,slide?}` (owner, cmd ∈ next/prev/goto/togglePause →
  diffuse `reveal.cmd`) ; `RoomSocket` accepte `?secret=` (rôle `reveal`), diffuse `reveal.state`
  (statics `revealCmdEvent`/`revealStateEvent`).
- **Plugin** : `arago-reveal-plugin.js` réel (servi à `/arago-reveal-plugin.js`) — lit
  `?aragoRoom&aragoSecret`, connecte le WS, émet `reveal.state` sur `slidechanged`/`fragmentshown`,
  exécute `reveal.cmd` (`Reveal.next/prev/slide/togglePause`), dispatch `arago-reveal-connected`.
- **Démo/fixture** : `arago-web/public/reveal-demo.html` (mini-`Reveal` autonome + plugin), servie à
  `/reveal-demo`.
- **Console** : panneau **Slides** (rooms ≠ LAB) — activer (secret + URL deck), Précédent/Suivant
  (`reveal/cmd`), slide courante (depuis `reveal.state`). **Attendee** : suivi « Slide H.V » (lecture seule).
- **Tests** : `reveal.feature` (enable + WS `?secret` deck + `reveal.cmd` REST→deck + `reveal.state`→viewer
  + 401) ; **@ui `reveal-ui.feature`** (deck démo → commande REST → slide avance). Vert : non-@ui **46/46**,
  **@ui 9/9**. RestSteps : WS avec reveal secret + report slide. User realm `rita`.

## Hors périmètre (notés)
Vue reveal.js **complète** synchronisée attendee (on a le n° de slide) ; miniatures `data:` ; gestion fine
des fragments ; reveal.js réel (CDN/embarqué). Transverses déjà notés : WS speaker RS256 natif, modération
superadmin, persistance modération, durcissement TOCTOU preview, `SmtpMailer`, spec concurrency `@Scheduled`.
