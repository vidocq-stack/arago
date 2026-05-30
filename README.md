# Arago — Speaker & Lab Companion

> Première application phare de la suite **Vidocq** (MicroProfile Server). Outil temps réel pour
> speakers de conférences et animateurs de hands-on labs : rooms à PIN, chat, contenu épinglé,
> plan de salle + demandes d'aide (mode LAB), pilotage de slides reveal.js, le tout RGPD-by-design.

Spec produit complète : [`arago-spec.md`](./arago-spec.md). Le nom rend hommage à François Arago,
conférencier scientifique et promoteur du télégraphe Chappe.

## Stack

100 % stack Vidocq (zéro Spring/Quarkus/Helidon) :

- **Vidocq runtime** (orchestrateur + extensions) — Vauban (CDI 4.1 build-time, release `0.1.0`),
  Cassini (JAX-RS 4.0), Chappe (HTTP/WS), Mansart (Jakarta Data), ravel (MP Config), knock (MP Health).
- **PostgreSQL 16** + migrations **Flyway**.
- Front **Svelte 5 + Vite**, build statique servi par Chappe.

## Architecture

```
arago-server/         runtime Vidocq : REST Cassini + Mansart (PostgreSQL) + Flyway + bootstrap
arago-web/            front Svelte 5 + Vite → jar de ressources statiques (/static)
arago-reveal-plugin/  plugin client reveal.js (stub Phase 0)
```

`App.main()` délègue à `Vidocq.main()` qui découvre les extensions sur le module-path et démarre
Chappe sur `:8080`. Les migrations Flyway s'appliquent au boot (`FlywayMigrator`), une fois le pool
Mansart prêt.

## Prérequis

- **Java 25** + **Maven 3.9.16** (pinés via `.sdkmanrc`) : `sdk env`.
- **Docker** (pour les tests Testcontainers et la stack docker-compose).

## Build

```bash
sdk env
mvn -ntp clean install        # build complet : front Vite (via frontend-maven-plugin) + serveur
mvn -pl arago-server test     # tests d'intégration (Testcontainers PostgreSQL — Docker requis)
```

Le build empaquette le front dans `arago-web`, le serveur dans `arago-server-*.jar`, et copie
toutes les deps runtime dans `arago-server/target/dependency/` (consommées par le Dockerfile).

## Lancer en local

### Via docker-compose (Arago + PostgreSQL)

```bash
mvn -ntp install -DskipTests   # build préalable (l'image copie le jar + les deps)
docker compose up --build
```

Puis :

- <http://localhost:8080/> — front Svelte (palette Empire, champ PIN)
- <http://localhost:8080/api/health> — santé MicroProfile (knock) → `{"status":"UP", …}`
- <http://localhost:8080/api/rooms/count> — compteur de rooms (preuve REST + Mansart)

### Dev front avec hot-reload

```bash
cd arago-web && npm install && npm run dev   # Vite dev server (proxy /api à configurer si besoin)
```

## Déploiement

CI Forgejo (`.forgejo/workflows/build-deploy.yml`) : build Maven → image Docker poussée sur
`registry.vidocq.dev/vidocq-tools/arago` → webhook Portainer. Secrets requis côté repo Codeberg :
`DOCKER_REG_URL`, `DOCKER_REG_USER`, `DOCKER_REG_PASSWORD`, `PORTAINER_WEBHOOK_URL`. PostgreSQL
managé séparément ; credentials injectés par env (`VIDOCQ_POOL_URL/USERNAME/PASSWORD`), jamais en clair.

## Roadmap

Phase 0 (ce squelette) ✅ — puis Phases 1–6 (OIDC/room/chat/WebSocket, RGPD, pins, mode LAB, reveal,
historique, polish) ; cf. `arago-spec.md` §11. Bugs & points ouverts : `BUG.md`. Benchmarks : `BENCH.md`.

## Licence

Apache License 2.0 — voir [`LICENSE`](./LICENSE).
