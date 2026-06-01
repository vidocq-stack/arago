# BUG.md — Arago

Bugs reproductibles et points ouverts (cf. CLAUDE.md workspace). Format : id court, date,
symptôme, repro, hypothèse, statut.

## Ouverts

_(aucun)_

## Résolus

### ARAGO-005 — Phase 1 chat WebSocket : 2 défauts stack révélés + corrigés upstream
- **Date** : 2026-06-01 → résolu 2026-06-01
- **Contexte** : tranche 4 (chat over WebSocket, `RoomSocket` monté en `vidocq.http.mount.roomws.type=websocket`).
  Le chat ne fonctionnait pas ; investigation jusqu'à la cause racine → **2 vrais défauts de la stack**
  (pas dans Arago — c'est son rôle de les révéler) :
  1. **Vauban (`VAU-PRX-003`)** — le proxy `_ClientProxy` (APT) émettait un descripteur invalide pour un
     type de retour **imbriqué** : `AttendeeTokens.verify()` retourne le record imbriqué `AragoJwt.Claims`
     → proxy `…AragoJwt/Claims` (slash au lieu de `$`) → `NoClassDefFoundError` à l'injection →
     `RoomResource` (qui injecte `AttendeeTokens`) tombait en 500. Cause : `ElementScanner` utilisait
     `getQualifiedName()` (canonique) au lieu de `getBinaryName()`. **Fixé dans vauban** + régression.
  2. **Chappe (`CHAPPE-002`)** — les routes `webSocket(\"/ws/rooms/{pin}\", …)` ne peuplaient pas
     `pathParams()` dans la `Request` de handshake → `pin` null dans `onOpen` → socket fermé. **Fixé dans
     chappe** (`WebSocketUpgrade` porte la Request matchée) + régression `WebSocketEchoTest`.
- **Règle respectée** : **aucun workaround dans Arago**. Les fixes sont dans les composants stack
  (chappe, vauban). Migration de version : `vauban.version` 0.1.0 → **0.2.0-SNAPSHOT** (0.2.0 = 0.1.0 +
  fix proxy, sans changement fonctionnel vauban-core) dans vidocq + arago (reste de la stack à migrer).
- **Vérifié** : acceptance **24/24 verte, sans aucun workaround** (chat WS de bout en bout : handshake
  token attendee → broadcast + persistance `ChatMessage`). + régressions chappe (6/6 WS) et vauban (APT).

### ARAGO-001 — `/metrics` câblé via dirac (MP Metrics 5.1) — extension runtime créée
- **Date** : 2026-05-30 → résolu 2026-06-01
- **Symptôme** : la spec (§5, §12) demande `/metrics` (MicroProfile Metrics). Aucune extension runtime
  Vidocq « dirac » (MP Metrics) n'était packagée (seule `humboldt`/OTLP existait, sans scrape `/metrics` simple).
- **Résolution** (modèle knock / ARAGO-003, **approche A — extension runtime réutilisable**) :
  - **dirac** : `dirac-rest` (module JAX-RS pur, comme knock-jaxrs) embarque désormais son index Vauban
    (`vauban-maven-plugin:generate`) → `MetricsResource` (@Path("/metrics")) découvrable par
    `CassiniExtension` (qui interroge `VaubanBeanProvider.getResourceClasses()`). + `opens io.vidocq.dirac.rest
    to … io.vidocq.vauban.core` (instanciation réflexive Vauban ; qualifié → Weld-safe, inerte au TCK).
  - **vidocq** : nouveau module `vidocq-runtime-dirac-extension` (wrapper, zéro classe — mirror knock-extension) :
    tire `dirac-rest` + `dirac-cdi-vauban` + `champollion-jsonp` + `cassini-rest-extension` ; `module-info`
    re-exporte le graphe dirac. Enregistré dans le reactor core-extensions + dependencyManagement.
  - **arago** : dépendance sur `vidocq-runtime-dirac-extension` ; `dirac-rest` ajouté au repackage Cassini
    (adapter tissé pour le jar Docker/AOT) + swap ; mount `/metrics` (`strip-prefix=false`, scopé
    `io.vidocq.dirac.rest`), `dirac.rest` exclu du mount `/api`.
- **Vérifié** : acceptance `metrics.feature` → `GET /metrics` **200** + corps OpenMetrics (`# EOF`), in-process
  avec OIDC actif (cervantes laisse la requête sans token anonyme). 18/18 scénarios verts, zéro régression.
  TCK dirac MP-Metrics 5.1 rejoué après la modif dirac-rest → 127/127.
- **Note** : endpoint non authentifié (scrape Prometheus ; restreint réseau en prod, cf. spec §4.8/§10.2).
  L'observabilité « détaillée superadmin » (§4.8) reste un raffinement ultérieur.
- **MAJ 2026-06-01 — §12 fermé** : `@Gauge arago_active_rooms` (`RoomMetrics`, MP Metrics) découvert par
  dirac au démarrage et exposé sur `/metrics`. Acceptance : le corps `/metrics` contient `arago_active_rooms`.

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
- **MAJ 2026-06-01 — (3) RÉSOLU côté Arago (header distinct, PAS cervantes lenient) :**
  - **Piste *lenient* écartée** : rendre cervantes tolérant (Bearer invérifiable → anonyme) **casse le TCK MP-JWT 2.1**
    (`EmptyTokenTest.invalidToken` exige un `401` dur sur token invalide, même endpoint non protégé). cervantes **reste strict**.
  - **Option retenue — superadmin sur `X-Arago-Admin`** : le token HS256 superadmin quitte `Authorization` pour l'en-tête
    `X-Arago-Admin`, que le filtre cervantes ignore. `AdminAuthenticator` lit ce seul en-tête (token brut, `Bearer ` toléré) ;
    `SpeakerAdminResource` + `AdminAuditResource` passent leur `@HeaderParam` à `X-Arago-Admin`. Les deux émetteurs Bearer
    coexistent : cervantes valide le Bearer Keycloak (OIDC), le superadmin vit hors de son champ.
  - **OIDC ré-activé** : harnais **Keycloak Testcontainers** (`quay.io/keycloak/keycloak:26.0`, realm `arago` importé,
    users `ada`/`bob`) reconstruit dans `AragoApp` ; `mp.jwt.verify.issuer`/`publickey.location` injectés au boot ; helper
    `keycloakToken(user)` (password grant). `oidc.feature` : **401 sans token, 200 speaker provisionné (`ada@oidc.test`),
    403 non provisionné (`bob`)**.
  - **Vérifié end-to-end** : `mvn -Pacceptance -pl arago-acceptance -am test -Dcucumber.filter.tags='not @ui'` → **vert**
    (18 scénarios + 2 smoke), avec OIDC actif (mp.jwt configuré) ET admin via `X-Arago-Admin` dans le **même** boot.
  - **Statut : RÉSOLU** (les 3 défauts traités). Spec mise à jour : §4.2 (transport `X-Arago-Admin`) + §4.8 (sécurité).

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
