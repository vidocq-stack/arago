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

## Résolus

### ARAGO-002 — `/health` au chemin racine standard (plus sous `/api`)
- **Date** : 2026-05-30 → résolu 2026-05-31
- **Symptôme** : MP Health impose `/health` à la racine ; on l'avait sous `/api/health`.
- **Cause** : le mount Vidocq **compose/strippe** son préfixe (le préfixe agit comme base-path, comme
  `@ApplicationPath`). Un mount `/health` strippé donnait `/health/health` ; un mount racine `/`
  masquait le static (Chappe ne fait pas de fallthrough sur un mount restful à `/`).
- **Résolution** : nouveau **mount non-strippant** dans Chappe — `Router.mount(prefix, handler, stripPrefix)`
  (chappe-api `DefaultRouterBuilder`), propagé par `ChappeMountPoint` + `ChappeMountConfigExtension`
  via la conf `vidocq.http.mount.<n>.strip-prefix` (défaut `true`). Arago : `health.path=/health` +
  `strip-prefix=false` → Cassini voit le path complet, la ressource garde son `@Path("/health")`
  **absolu**, `/health` reste un préfixe de routage distinct (static `/` et `/api` non masqués).
  Le scoping `include-packages`/`exclude-packages` de `CassiniMountHandlerProvider` confine la ressource
  au mount `/health`. Healthcheck Docker/compose → `/health`.

### ARAGO-003 — knock (MP Health) montable en runtime Vidocq modulaire, knock 100 % Cassini-free
- **Date** : 2026-05-30 → résolu 2026-05-31
- **Symptômes successifs** : `DeploymentException` (BCE inaccessible à vauban.core) → `/health` 404
  (ressource non montée) → `UnsatisfiedResolution` du `HealthCheckRegistry` → adapter non lié.
- **Résolution finale** (knock dépend de JAX-RS + Vauban, **jamais de Cassini**) :
  - **Rename `knock-cassini` → `knock-jaxrs`** (le module n'a jamais dépendu de Cassini ; nom honnête).
  - `knock-cdi-vauban` + `knock-jaxrs` : `vauban-maven-plugin:generate` → livrent leur propre
    `META-INF/vauban-beans.list` (registry + ressource découvrables au runtime). + `opens` qualifiés
    vers `io.vidocq.vauban.core` (BCE + instanciation de bean). Outillage Vauban only, **Weld-safe**.
  - L'**adapter Cassini** est tissé dans une **copie repackagée** de knock-jaxrs au build d'Arago
    (`cassini-maven-plugin`, `repackageModularDependencies=true`) — le jar publié de knock reste pur.
    **Fix du plugin** : le repackaging réécrit désormais le `module-info` du jar dérivé pour y ajouter
    `requires io.vidocq.cassini.api` (Class-File API), sinon l'adapter ne se lie pas.
  - **Toute la stack bumpée sur chappe 0.2.0-SNAPSHOT** (le mount non-strippant vit en chappe-api 0.2.0).
- **Validé end-to-end** : `/health`+`/health/live|ready|started` → MP Health 200, `/api/health` 404,
  `/api/rooms/count` + SPA intacts, boot ~460 ms.
- **À faire upstream avant merge** : commits/republish coordonnés (chappe, cassini, knock, vidocq-runtime) ;
  MAJ ADR-002 knock ; **rejouer les TCK** (MP Health knock, REST 2535 cassini — la modif Chappe + le
  rename touchent du socle) ; `jpms-guardian`/`dependency-gatekeeper` ; pré-générer aussi `$$CassiniRoutes`
  dans le repackaging pour l'AOT pur (aujourd'hui routes via scan réflexif, OK en JIT). Pas de `Co-Authored-By`.
