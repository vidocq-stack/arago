# BUG.md — Arago

Bugs reproductibles et points ouverts (cf. CLAUDE.md workspace). Format : id court, date,
symptôme, repro, hypothèse, statut.

## Ouverts

### ARAGO-004 — Enforcement OIDC bloqué → 3 défauts cervantes/stack surfacés (décisions upstream)
- **Date** : 2026-06-01 (diagnostic affiné après investigation complète)
- **Contexte** : Phase 1, I1-O1. `/api/oidc/me` valide un Bearer Keycloak (cervantes/MP-JWT) puis applique
  l'allowlist (`SpeakerAllowlist`). Câblage + cas **401 sans token** livrés/verts (commit `6ae4f29`).
  Harnais Keycloak Testcontainers **fonctionnel** (token RS256 OK ; piège réglé : KC 24+ exige
  `firstName`/`lastName`). En poussant le diagnostic jusqu'au bout, **3 défauts en cascade** sont apparus —
  tous **dans cervantes / la stack**, pas dans Arago (c'est le rôle d'Arago de les révéler) :
  1. **Packaging — cervantes ne publie pas son index vauban.** `cervantes-cdi-vauban` + `cervantes-cassini`
     ne lancent **pas** `vauban-maven-plugin:generate` (contrairement à knock) → pas de `META-INF/vauban-beans.list`
     → le `scanClasspath()` du runtime Vidocq **ne découvre ni le `@Produces JsonWebToken` ni les `@Provider`**
     (`JwtAuthenticationFilter`, `RolesAllowedDynamicFeature`) → **le filtre JWT ne tourne jamais** → `@Context
     SecurityContext.getUserPrincipal()` = null → 401. (Le `@Context` de Cassini est CORRECT — `Invoker` injecte
     bien les champs `@Context` sur l'instance CDI ; ma 1re hypothèse « bug @Context Cassini » était fausse.)
     **Fix vérifié** : ajouter `vauban-maven-plugin:generate` + `opens … to io.vidocq.vauban.core` aux 2 modules
     cervantes (Weld-safe, comme knock) → les jars embarquent l'index, le filtre devient découvrable. *(Idem ravel-cdi-vauban.)*
  2. **Bug cervantes — NPE quand `mp.jwt.*` non configuré.** Une fois le filtre découvert (fix 1), s'il n'y a
     **pas** de config `mp.jwt.verify.*`, `JwtValidator` est null et le filtre **NPE → 500** dès qu'un Bearer est
     présent (`Cannot invoke JwtValidator.validate(...) because "this.validator" is null`). Il devrait être
     **inerte** (anonyme) sans config. → casse tout endpoint Bearer d'une app qui embarque cervantes sans OIDC complet.
  3. **Design — conflit dual-issuer Bearer.** Une fois configuré (fix 1 + mp.jwt), le filtre `@PreMatching`
     **rejette en 401 TOUT Bearer qu'il ne peut vérifier**, y compris le **token HS256 local du superadmin**
     (2e émetteur légitime). → les endpoints `/api/admin/*` d'Arago tombent en 401. MP-JWT ne connaît qu'un issuer.
- **Décisions upstream nécessaires (mainteneur)** :
  - **(1)** Activer `vauban-maven-plugin:generate` dans `cervantes-cdi-vauban`/`cervantes-cassini` (+ ravel, + audit
    stack des autres `*-cdi-vauban`). Win clair, Weld-safe. *(NB : un balayage aveugle de « 44 modules » est faux —
    beaucoup sont câblés via la route B `*-extension-codegen`, ou ne sont pas des beans runtime ; appliquer du jugement.)*
  - **(2)** Corriger `JwtAuthenticationFilter` : si `validator == null` (pas de config) → inerte/anonyme, pas de NPE. + rejouer le TCK MP-JWT.
  - **(3)** Trancher la coexistence dual-Bearer : soit cervantes devient **lenient** (Bearer invérifiable → anonyme,
    enforcement seulement sur `@RolesAllowed`), soit Arago passe le token superadmin sur un **schéma/header distinct**
    (ex. `X-Arago-Admin`) que cervantes ignore.
- **MAJ 2026-06-01 — (1) et (2) CORRIGÉS + poussés upstream :**
  - **(1) FAIT** : `vauban-maven-plugin:generate` ajouté à `cervantes-cdi-vauban` + `cervantes-jaxrs` (et au lot
    `ravel/cyrano/heisenberg/dirac/humboldt-cdi` + `cassini-cdi-vauban`) → jars embarquent `META-INF/vauban-beans.list`,
    filtre/producteurs découverts. **Sans opens** (règle corrigée : la BCE est dans un package exporté → vauban-core y
    accède en public ; l'`opens to vauban.core` n'est requis QUE si la BCE est dans un package non-exporté, cas knock).
    Au passage : `cervantes-cassini` **renommé `cervantes-jaxrs`** (impl-agnostique JAX-RS). Commits : cervantes `6bfbb8b`,
    vidocq `02d88b5`, + 6 modules (`ravel 36105c6`, `cassini 6f050a6`, `cyrano 1e0a0fc`, `heisenberg e1132b1`, `dirac aeb9ba4`, `humboldt c2c9380`).
  - **(2) FAIT** : `JwtAuthenticationFilter` est inerte si `validator == null` (pas de NPE). **TCK MP-JWT 2.1 : 206/206 PASS.**
  - **Vérifié** : arago **reste vert** (filtre cervantes désormais découvert mais inerte sans `mp.jwt.*` → endpoints superadmin OK).
- **Reste (3) — design dual-issuer** : seul blocage restant pour l'enforcement OIDC. Quand `mp.jwt.*` EST configuré (chemin
  OIDC), le filtre rejette en 401 le Bearer HS256 superadmin. Trancher : cervantes *lenient* (Bearer invérifiable → anonyme,
  enforcement seulement sur `@RolesAllowed`) OU superadmin sur header distinct (`X-Arago-Admin`). Puis **ré-activer** le harnais
  Keycloak + scénarios `/api/oidc/me` 200/403 (prêts, hors repo ; `SpeakerAllowlist` livré).

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
