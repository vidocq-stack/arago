# BUG.md — Arago

Bugs reproductibles et points ouverts (cf. CLAUDE.md workspace). Format : id court, date,
symptôme, repro, hypothèse, statut.

## Ouverts

### ARAGO-001 — `/metrics` non câblé en Phase 0
- **Date** : 2026-05-30
- **Symptôme** : la spec (§5, §12) demande `/metrics` (MicroProfile Metrics, compteur de rooms
  actives). Aucune extension runtime Vidocq « dirac » (MP Metrics) n'est packagée ; seule
  `humboldt` (MP Telemetry/OTLP) existe, mais elle n'expose pas un scrape `/metrics` simple et
  tirerait un exporter OTLP au boot.
- **Statut** : différé. Décider de l'extension metrics (dirac à intégrer côté runtime, ou exposer
  un endpoint dédié) avant de cocher le critère §12. Phase 0 livre `/api/health` (knock) seul.

### ARAGO-002 — `/health` sous le préfixe `/api`
- **Date** : 2026-05-30
- **Symptôme** : la spec liste `/health` à la racine ; knock est auto-monté sous le préfixe Cassini
  → effectivement `/api/health` (comme `forge-dashboard` avec `/api/healthz`).
- **Statut** : assumé en Phase 0 (Dockerfile + compose + front pointent `/api/health`). À aliaser à
  la racine si besoin (mount static dédié ou route racine).

### ARAGO-003 — knock (MP Health) incompatible avec vauban 0.1.0 (release)
- **Date** : 2026-05-30
- **Symptôme** : au boot, `DeploymentException` — `BceProcessor (module io.vidocq.vauban.core)
  cannot access io.vidocq.knock.cdi.internal.HealthCheckCdiExtension ... module
  io.vidocq.knock.cdi.vauban does not export io.vidocq.knock.cdi.internal to io.vidocq.vauban.core`.
- **Repro** : `vidocq-runtime-knock-extension:0.1.0-SNAPSHOT` + `vauban-indexer:0.1.0` (release) →
  `docker compose up`. Le BCE de knock n'ouvre/exporte pas son package interne au `BceProcessor`
  de vauban 0.1.0 (mansart, lui, exporte son package cdi → passe).
- **Hypothèse** : knock SNAPSHOT a été bâti contre vauban 0.1.0-SNAPSHOT ; le contrat BCE
  (exports/opens requis) a changé entre le SNAPSHOT et la release 0.1.0 de vauban.
- **Statut** : contourné en Phase 0 — knock retiré, `HealthResource` fournit `/api/health`.
  À ré-armer quand la stack runtime sera réalignée sur vauban 0.1.0 (cf. consigne « les autres
  stabilisés après »). Vérifier alors aussi ravel/cassini/mansart sous vauban 0.1.0.

## Résolus

_(aucun pour l'instant)_
