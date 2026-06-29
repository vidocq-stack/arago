# CLAUDE.md — Arago

Guidance pour Claude Code dans ce dépôt. Voir aussi `arago-spec.md` (spec complète) et le
`CLAUDE.md` du workspace Vidocq pour la philosophie transverse.

## Identité

- **Arago** : première application phare bâtie sur la stack Vidocq (showcase + dogfood).
  Outil temps réel pour speakers et animateurs de hands-on labs (rooms, chat, pins, mode LAB,
  slides reveal, RGPD).
- groupId : `io.vidocq.tools.arago` — version `0.1.0-SNAPSHOT`.
- Repo : <https://codeberg.org/VidocqTools/arago> (org `VidocqTools`, sibling de `forge-dashboard`).
- Application (≠ librairie) : Model Maven **4.0.0**, Maven **3.9.16**, Java **25**, aligné sur la
  convention du workspace Vidocq (le sibling `forge-dashboard` est resté en Maven 4 / Model 4.1.0).

## Modules

| Module | Rôle |
|---|---|
| `arago-server` | Runtime Vidocq : REST Cassini + Mansart Data (PostgreSQL) + Flyway + bootstrap |
| `arago-web` | Front Svelte 5 + Vite ; build statique empaqueté en jar `/static`, servi par Chappe |
| `arago-reveal-plugin` | Plugin client reveal.js — **stub** en Phase 0, implémenté en Phase 4 |

## Commandes

```bash
sdk env                      # Java 25 + Maven 3.9.16 (.sdkmanrc)
mvn -ntp install -DskipTests # build complet (front Vite inclus via frontend-maven-plugin)
mvn -pl arago-server test    # tests (Testcontainers → Docker requis)
docker compose up --build    # stack locale Arago + PostgreSQL 16
```

Dev front seul : `cd arago-web && npm install && npm run dev`.

## Stack imposée (arago-spec §5)

Vidocq runtime, Vauban (CDI 4.1 build-time, **épinglé release 0.1.0**), Cassini (REST), Champollion
(JSON-B — cible ; Yasson en transition Phase 0), Chappe (HTTP + WebSocket), Mansart (Jakarta Data,
PostgreSQL), MP Config (ravel), MP Health (knock). **Pas de Spring/Quarkus/Helidon — Vidocq pur.**

## Authentification (locale, sans IdP externe)

Pas de Keycloak/OIDC : toute l'auth est **locale** et signée par Arago (HS256 via `AragoJwt`,
secret `arago.attendee.hmac-secret`). Trois autorités, distinctes par audience/entête :

- **Speaker** — login email + mot de passe (`POST /api/speaker/login`), token `aud=arago-speaker` sur
  `Authorization: Bearer` (`SpeakerAuth`/`SpeakerAuthenticator`, package `…arago.speaker`). Mot de
  passe haché PBKDF2 (`PasswordHasher`) dans `speakers.password_hash` (V15). L'admin crée/modifie les
  speakers ; le speaker choisit son pseudo (propagé live + historique). `owner_sub` d'une room = `speaker.id`.
- **Superadmin** — `POST /api/admin/login`, token `aud=arago-admin` sur l'entête `X-Arago-Admin`.
- **Attendee** — token HS256 `aud=arago-attendee` (PIN + pseudo), porté en `?token=` sur le WebSocket.

Dev : `vidocq:dev` seede `speakerA`/`speakerB` (mdp `pw`) et fixe le port Postgres hôte à `65123`
(clé `vidocq.dev.postgres.port`). Sessions front en `sessionStorage` (par onglet, survit au reload).

## Dépendances hors stack §5 (justifications)

Toute dépendance hors specs Jakarta/MicroProfile + stack Vidocq doit être justifiée (spec §15) :

- **Flyway** (runtime) — migrations DB versionnées (spec §7), appliquées au boot par l'**extension
  Flyway de Vidocq** (`vidocq-runtime-flyway-migration-extension`, avant le conteneur CDI). Plus de
  `FlywayMigrator` manuel.
- **org.postgresql:postgresql** (runtime) — driver JDBC du backend Postgres (spec §5).
- **Testcontainers** (test) — tests d'intégration sur un vrai PostgreSQL jetable (fidélité prod
  vs H2). Jamais en runtime.
- **frontend-maven-plugin** (build) — pilote npm/Vite pendant le build ; aucun artefact runtime.
- **Yasson + Parsson** (runtime) — impl JSON-B/P transitoire, alignée sur l'exemple officiel ;
  cible = champollion-jsonb.

## Conventions

- **Commits** : conventional commits (`feat(arago-server): …`). **Jamais de `Co-Authored-By: Claude`**
  ni de mention IA (règle workspace).
- **Code/Javadoc/commentaires en anglais** ; français réservé aux docs et au pilotage.
- **TDD**, **Virtual Threads** pour l'I/O, **JPMS strict** (`module-info` à exports/opens minimaux).
- **Par phase** (spec §11/§15) : plan court → validation → code. Aucun secret en clair dans les logs
  (couvert par test à venir). Sanitization centralisée de toute string utilisateur affichée.
- **Traçabilité** : bugs reproductibles → `BUG.md` ; chiffres de perf → `BENCH.md`.

## État

**Phase 0 (squelette) en cours.** Phases 1–6 (auth locale/room/chat/WebSocket, RGPD, pins, LAB, reveal,
historique, polish) à planifier après validation. Points ouverts Phase 0 : voir `BUG.md`
(extension exposant `/metrics`, alias `/health` racine).
