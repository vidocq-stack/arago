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
Chappe sur `:8080`. Les migrations Flyway s'appliquent au boot via l'extension Flyway de Vidocq
(`migration`), avant le démarrage du conteneur CDI.

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

Cette stack-là ne **seede aucun compte** : le superadmin n'a pas de credentials et aucun speaker n'est
créé. La page <http://localhost:8080/speaker> affiche le formulaire de connexion, mais sans compte rien
ne se connecte. Pour exercer les deux consoles, utiliser la stack dev complète ci-dessous.

### Stack dev complète avec comptes seedés (`docker-compose.localdev.yml`)

Auth **100 % locale** (plus de Keycloak) : provisionne le compte superadmin break-glass **et** deux
speakers (email + mot de passe) prêts à l'emploi.

```bash
mvn -ntp install -DskipTests
docker compose -f docker-compose.localdev.yml up --build
```

- **Speaker** — <http://localhost:8080/speaker> → login `speakera@oidc.test` ou `speakerb@oidc.test`,
  mot de passe `pw`. Les deux sont seedés dans l'allowlist au boot via `ARAGO_DEV_SEED_SPEAKER`
  (`speakerA` en rôle ADMIN, `speakerB` en SPEAKER) avec le mot de passe `ARAGO_DEV_SEED_SPEAKER_PASSWORD`
  (`pw`). La liste accepte plusieurs emails séparés par des virgules, chacun en `email` ou `email=ROLE`.
- **Superadmin** — <http://localhost:8080/admin> → `root` / `arago-dev`. C'est lui qui crée/modifie les
  speakers (`POST /api/admin/speakers`, avec email + rôle + mot de passe initial) et réinitialise les
  mots de passe.
- PostgreSQL est exposé sur le port hôte **`65123`** (parité avec `vidocq:dev`) pour s'y connecter avec un
  outil DB. **Réservé au dev** : secrets de démonstration, speakers seedés, DB exposée sur l'hôte.

### Dev depuis IntelliJ IDEA

Le dépôt versionne deux configurations de lancement dans `.run/` : IntelliJ les détecte
automatiquement à l'ouverture du projet (menu déroulant des Run/Debug Configurations).

**Prérequis** : SDK de projet sur **JDK 25** (`File → Project Structure → Project SDK`) et
**Docker** démarré (le mode dev provisionne PostgreSQL via les DevServices Vidocq).

1. **`Arago Dev (vidocq:dev)`** — config Maven qui lance le goal `vidocq:dev` sur
   `arago-server/pom.xml`. Elle démarre l'app, provisionne PostgreSQL (sur le port hôte **fixe `65123`**),
   injecte les secrets de dev (superadmin `root` / `arago-dev`), seede `speakerA`/`speakerB` (mot de passe
   `pw`), et fork la JVM avec un agent JDWP en écoute sur **`:5005`**. Lancer cette config (▶) suffit pour
   avoir l'appli sur <http://localhost:8080/>.

2. **`Attach Arago :5005`** — config *Remote JVM Debug* qui s'attache au process forké sur
   `localhost:5005`. La lancer en mode Debug (🐞) une fois que `Arago Dev` tourne permet de poser
   des breakpoints dans le code serveur. `AUTO_RESTART` est activé : l'attach se reconnecte tout
   seul après un redémarrage du process dev.

> Workflow type : ▶ `Arago Dev (vidocq:dev)`, attendre le démarrage, puis 🐞 `Attach Arago :5005`.
> Pour recréer les configs à la main : *Maven Run* avec le goal `vidocq:dev` (working dir = racine
> du projet, pom = `arago-server/pom.xml`) + une *Remote JVM Debug* `localhost:5005` (attach,
> socket transport).

**Base de données dans IntelliJ** : le dépôt versionne aussi `.idea/dataSources.xml` (datasource
« Arago Dev (65123) » → `jdbc:postgresql://localhost:65123/vidocq`, user/mot de passe `vidocq`). Comme
le port DB est fixe, la connexion fonctionne dès que `vidocq:dev` tourne — elle apparaît directement
dans la fenêtre *Database* d'IntelliJ, sans configuration manuelle.

#### Comptes de dev

Auth locale (email + mot de passe). Seuls `speakerA`/`speakerB` sont seedés au boot ; tout autre speaker
est créé par le superadmin. Identique en `vidocq:dev` (IntelliJ) et via `docker-compose.localdev.yml`.

| Rôle dans Arago | Login (email) | Mot de passe | Seedé au boot |
|---|---|---|---|
| **Speaker — ADMIN** | `speakera@oidc.test` | `pw` | ✅ rôle `ADMIN` |
| **Speaker — SPEAKER** | `speakerb@oidc.test` | `pw` | ✅ rôle `SPEAKER` |
| **Superadmin** (break-glass) | `root` | `arago-dev` | console `/admin` |

- Le seed est piloté par `arago.dev.seed-speaker` (emails + rôle) et `arago.dev.seed-speaker.password`
  (mot de passe initial, défaut `pw`), câblés dans la config du plugin `vidocq:dev`.
- **Créer d'autres speakers** : superadmin → console `/admin` → « Créer » (email + rôle + mot de passe),
  puis le speaker se connecte sur `/speaker`. Le superadmin peut aussi réinitialiser un mot de passe.
- Un login d'un email non provisionné renvoie « Identifiants invalides ».

> **Réservé au dev** : secrets de démonstration et speakers seedés. Jamais en production
> (`arago.dev.seed-speaker` doit rester absent).

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

Phase 0 (ce squelette) ✅ — puis Phases 1–6 (auth locale/room/chat/WebSocket, RGPD, pins, mode LAB,
reveal, historique, polish) ; cf. `arago-spec.md` §11. Bugs & points ouverts : `BUG.md`. Benchmarks : `BENCH.md`.

## Licence

Apache License 2.0 — voir [`LICENSE`](./LICENSE).
