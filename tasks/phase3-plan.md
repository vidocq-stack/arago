# Arago — Phase 3 (Mode LAB, spec §4.5 / §11)

> Le gros du Mode LAB a été livré hors-ordre pendant la Phase 1 : layout BLOCKS à la création,
> verrouillage de siège first-come (index unique `ux_seats_active`), rejet des sièges bloqués au claim
> (`LayoutSpec.isValidSeat`), vue top-down **attendee** (`App.svelte`), help raise/claim/resolve + « 1
> active par attendee ». Migrations `V7__help_requests`, `V8__lab_layout`.

## Backend LAB complété — 2026-06-02
- **Éditeur de layout (pré-config + à chaud)** : `PUT /api/rooms/{id}/layout` (`RoomResource`, owner-only)
  → re-valide (`isValidLayout`), persiste (`LayoutCodec.toJson`), **diffuse** `RoomSocket.layoutEvent`
  (la vue top-down se met à jour live). Éditer `blockedSeats` = toggler des sièges indispo. CONF → 409 ;
  layout invalide → 400 ; non-owner → 403. (Siège déjà occupé qui devient bloqué : reste tenu, le blocage
  n'empêche que les nouveaux claims.)
- **Cooldown anti-spam 60 s** après résolution (`RoomSocket.raiseHelp` + helper pur `inResolveCooldown`,
  `arago.help.cooldown-seconds`, défaut 60 ; le CANCELLED ne pénalise pas).
- Tests : unit `HelpCooldownTest` ; acceptance `layout.feature` (PUT 200/400/409) + `help.feature`
  (re-raise post-resolve bloqué). Vert : unit + acceptance **43/43**. Pas de migration.

## Console speaker — LIVRÉE 2026-06-02 (complète)
Page Svelte `speaker.html` servie à **`/speaker`** (cleanUrls) : login OIDC (retour `/speaker`), « mes
rooms » + création (CONF/LAB) + terminer, et par room — **vue top-down live** (token observateur → WS
attendee read-only), **file d'aide** claim/resolve, **éditeur de layout** (toggle sièges → PUT), **pins**
(ajout/liste/**reorder DnD**/suppression), **modération** mute/kick.
- Enablers backend : retour OIDC paramétrable (`/api/oidc/login?return=/speaker`, sanitize local-path) ;
  `POST /api/rooms/{id}/observer-token` (owner-only → token aud=attendee + pin) pour le WS observateur.
- `Speaker.svelte` réutilise la géométrie top-down d'`App.svelte`.
- Tests : `observer-token` (acceptance rooms.feature, 200 + 401) ; **@ui `speaker-console.feature`**
  (login `/speaker` → créer room ; + room LAB REST → demande d'aide attendee → console resolve). Vert :
  non-@ui **44/44**, **@ui 8/8**.
- NB : le WS speaker RS256 natif reste différé (observateur via token attendee en attendant).

## Différés transverses (rappel)
Modération superadmin, persistance état modération, durcissement TOCTOU preview, `SmtpMailer` réel,
spec concurrency `@Scheduled` (PurgeScheduler = stopgap).
