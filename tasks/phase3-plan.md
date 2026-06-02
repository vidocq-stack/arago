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

## Reste Phase 3 — console speaker (UI dédiée, différée)
Vue top-down **speaker** + file d'aide priorisée + claim/resolve depuis la carte + UI éditeur de layout
(toggle sièges) → nouvelle entrée Svelte « console speaker » (OIDC) + test @ui. Gros incrément UI qui
débloque aussi : pin reorder drag&drop, cartes preview OG, actions modération. Aucune console speaker
n'existe encore (seules SPA attendee + console superadmin + page « Mes données »).

## Différés transverses (rappel)
Modération superadmin, persistance état modération, durcissement TOCTOU preview, `SmtpMailer` réel,
spec concurrency `@Scheduled` (PurgeScheduler = stopgap).
