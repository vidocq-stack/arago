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

Cette stack-là ne configure **pas** d'IdP : le bouton « Se connecter (Keycloak) » affiche un message
« connexion non configurée » (pas de 500), et le superadmin n'a pas de credentials. Pour tester les
deux consoles, utiliser la stack dev complète ci-dessous.

### Stack dev complète : Arago + PostgreSQL + Keycloak (`docker-compose.localdev.yml`)

Ajoute un Keycloak 26 (realm `arago` pré-importé, 15 users `*@oidc.test` / mot de passe `pw`) **et**
le compte superadmin break-glass, pour exercer les deux parcours authentifiés en local :

```bash
mvn -ntp install -DskipTests
docker compose -f docker-compose.localdev.yml up --build
```

- **Speaker** — <http://localhost:8080/> → « Se connecter (Keycloak) », puis login Keycloak
  `speakera` ou `speakerb` / `pw`. Les deux sont auto-provisionnés dans l'allowlist au boot via
  `ARAGO_DEV_SEED_SPEAKER` (`speakerA` en rôle ADMIN, `speakerB` en SPEAKER), donc le login aboutit
  sans invitation manuelle. La liste accepte plusieurs emails séparés par des virgules, chacun en
  `email` ou `email=ROLE`. Les autres users du realm (`ada`, `bob`, … `pw`) ne sont pas seedés —
  parfaits pour tester le flux superadmin → inviter (`POST /api/admin/speakers`) → login.
- **Superadmin** — <http://localhost:8080/admin> → `root` / `arago-dev`.
- Console Keycloak admin — <http://localhost:8081/> (`admin` / `admin`).

Le navigateur joint Keycloak en `localhost:8081`, alors qu'Arago le joint en `keycloak:8080` sur le
réseau Docker : `KeycloakOidcClient` gère ce split via `ARAGO_OIDC_INTERNAL_ISSUER` (échange de code
back-channel + JWKS), `ARAGO_OIDC_ISSUER` restant l'issuer public (le `iss` du token). C'est le
scénario classique « Keycloak derrière un reverse-proxy ». **Réservé au dev** : secrets de
démonstration, realm en `redirectUris: ["*"]`, Keycloak en HTTP, speaker seedé.

### Dev depuis IntelliJ IDEA

Le dépôt versionne deux configurations de lancement dans `.run/` : IntelliJ les détecte
automatiquement à l'ouverture du projet (menu déroulant des Run/Debug Configurations).

**Prérequis** : SDK de projet sur **JDK 25** (`File → Project Structure → Project SDK`) et
**Docker** démarré (le mode dev provisionne PostgreSQL + Keycloak via les DevServices Vidocq).

1. **`Arago Dev (vidocq:dev)`** — config Maven qui lance le goal `vidocq:dev` sur
   `arago-server/pom.xml`. Elle démarre l'app, provisionne PostgreSQL + Keycloak (realm `arago`
   pré-importé), injecte les secrets de dev (superadmin `root` / `arago-dev`) et fork la JVM avec
   un agent JDWP en écoute sur **`:5005`**. Lancer cette config (▶) suffit pour avoir l'appli sur
   <http://localhost:8080/>.

2. **`Attach Arago :5005`** — config *Remote JVM Debug* qui s'attache au process forké sur
   `localhost:5005`. La lancer en mode Debug (🐞) une fois que `Arago Dev` tourne permet de poser
   des breakpoints dans le code serveur. `AUTO_RESTART` est activé : l'attach se reconnecte tout
   seul après un redémarrage du process dev.

> Workflow type : ▶ `Arago Dev (vidocq:dev)`, attendre le démarrage, puis 🐞 `Attach Arago :5005`.
> Pour recréer les configs à la main : *Maven Run* avec le goal `vidocq:dev` (working dir = racine
> du projet, pom = `arago-server/pom.xml`) + une *Remote JVM Debug* `localhost:5005` (attach,
> socket transport).

#### Comptes de dev

Le realm Keycloak `arago` (importé depuis `docker/keycloak/arago-realm.json` au démarrage du
DevService) provisionne ces comptes. **Tous les users Keycloak ont le mot de passe `pw`.** Identique
en `vidocq:dev` (IntelliJ) et via `docker-compose.localdev.yml`.

| Rôle dans Arago | Login Keycloak | Mot de passe | Allowlist au boot |
|---|---|---|---|
| **Speaker — ADMIN** | `speakera` | `pw` | ✅ seedé `ADMIN` |
| **Speaker — SPEAKER** | `speakerb` | `pw` | ✅ seedé `SPEAKER` |
| Users génériques (16) | `ada`, `bob`, `carol`, `dave`, `erin`, `grace`, `heidi`, `ivan`, `judy`, `karl`, `leo`, `mona`, `nina`, `owen`, `rita`, `sam` | `pw` | ❌ non seedés |
| **Superadmin** (break-glass) | `root` | `arago-dev` | n/a (console `/admin`, hors Keycloak) |

- Emails Keycloak : `<login>@oidc.test` (ex. `speakera@oidc.test`).
- **`speakera` / `speakerb`** sont auto-ajoutés à l'allowlist au boot (`arago.dev.seed-speaker`,
  câblé dans la config du plugin `vidocq:dev`), donc leur login OIDC aboutit **sans invitation
  manuelle**. `speakerA` est `ADMIN`, `speakerB` est `SPEAKER`.
- Les **16 users génériques** existent dans Keycloak mais ne sont **pas** dans l'allowlist : leur
  login renvoie « Compte non provisionné ». Parfaits pour tester le flux superadmin → inviter
  (`POST /api/admin/speakers`, console `/admin` en `root` / `arago-dev`) → login.
- Console admin Keycloak : <http://localhost:8081/> (`admin` / `admin`) **uniquement** avec
  `docker-compose.localdev.yml` ; en `vidocq:dev`, le port Keycloak est aléatoire (voir
  `target/vidocq-dev-services.properties` ou les logs `Keycloak dev service ready, issuer …`).

> **Réservé au dev** : secrets de démonstration, realm en `redirectUris: ["*"]`, Keycloak en HTTP,
> speakers seedés. Jamais en production (`arago.dev.seed-speaker` doit rester absent).

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
