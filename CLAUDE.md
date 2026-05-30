# CLAUDE.md — Arago

Guidance pour Claude Code dans ce dépôt. Voir aussi `arago-spec.md` (spec complète) et le
`CLAUDE.md` du workspace Vidocq pour la philosophie transverse.

## Identité

- **Arago** : première application phare bâtie sur la stack Vidocq (showcase + dogfood).
  Outil temps réel pour speakers et animateurs de hands-on labs (rooms, chat, pins, mode LAB,
  slides reveal, RGPD).
- groupId : `io.vidocq.tools.arago` — version `0.1.0-SNAPSHOT`.
- Repo : <https://codeberg.org/VidocqTools/arago> (org `VidocqTools`, sibling de `forge-dashboard`).
- Application (≠ librairie) : on suit les conventions du workspace applicatif `vidocq-tools`
  (Model Maven **4.1.0**, Maven **4.0.0-rc-5**, Java **25** Liberica), pas celles des libs upstream.

## Modules

| Module | Rôle |
|---|---|
| `arago-server` | Runtime Vidocq : REST Cassini + Mansart Data (PostgreSQL) + Flyway + bootstrap |
| `arago-web` | Front Svelte 5 + Vite ; build statique empaqueté en jar `/static`, servi par Chappe |
| `arago-reveal-plugin` | Plugin client reveal.js — **stub** en Phase 0, implémenté en Phase 4 |

## Commandes

```bash
sdk env                      # Java 25 + Maven 4.0.0-rc-5 (.sdkmanrc)
mvn -ntp install -DskipTests # build complet (front Vite inclus via frontend-maven-plugin)
mvn -pl arago-server test    # tests (Testcontainers → Docker requis)
docker compose up --build    # stack locale Arago + PostgreSQL 16
```

Dev front seul : `cd arago-web && npm install && npm run dev`.

## Stack imposée (arago-spec §5)

Vidocq runtime, Vauban (CDI 4.1 build-time, **épinglé release 0.1.0**), Cassini (REST), Champollion
(JSON-B — cible ; Yasson en transition Phase 0), Chappe (HTTP + WebSocket), Mansart (Jakarta Data,
PostgreSQL), MP Config (ravel), MP Health (knock). **Pas de Spring/Quarkus/Helidon — Vidocq pur.**

## Dépendances hors stack §5 (justifications)

Toute dépendance hors specs Jakarta/MicroProfile + stack Vidocq doit être justifiée (spec §15) :

- **Flyway** (`flyway-core`, `flyway-database-postgresql`, runtime) — migrations DB versionnées,
  exigées par spec §7. Pas d'extension Flyway côté Vidocq → câblage manuel (`FlywayMigrator`,
  observer `@Initialized(ApplicationScoped.class)`).
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

**Phase 0 (squelette) en cours.** Phases 1–6 (OIDC/room/chat/WebSocket, RGPD, pins, LAB, reveal,
historique, polish) à planifier après validation. Points ouverts Phase 0 : voir `BUG.md`
(extension exposant `/metrics`, alias `/health` racine).
