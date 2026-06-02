# Arago — Phase 6 (Polish : i18n FR/EN, thèmes clair/sombre, a11y §11)

## Livré 2026-06-02 (v1)
Polish front, zéro dépendance externe. Les tests E2E Playwright de la spec sont déjà couverts par le
module `arago-acceptance` (cf. spec §832), donc Phase 6 = **i18n FR/EN + thème clair/sombre + a11y AA**.

Périmètre v1 retenu : page **attendee** (publique, multi-public) **100% bilingue** ; barre
**Préférences** (langue FR/EN + bascule clair/sombre) sur les **4 pages** ; **thème sombre partout** via
variables CSS ; **a11y AA**. Les consoles opérateur (admin/speaker/mes-données) héritent du thème + de la
barre ; leur texte dense reste FR (i18n exhaustive **notée en reliquat**).

- **Infra** (`arago-web/src/lib/`, Svelte 5 runes en module `.svelte.js`) :
  - `i18n.svelte.js` — dictionnaire `fr`/`en` plat + `t(key, params)` (interpolation `{name}`), `$state`
    réactif universel (un `setLang()` re-rend tous les libellés), persistance `localStorage`, miroir
    `<html lang>`. Détection initiale : `localStorage` puis `navigator.language` (défaut `fr`).
  - `theme.svelte.js` — `light`/`dark`, miroir `<html data-theme>`, persistance, défaut
    `prefers-color-scheme`. `toggleTheme()`.
  - `Prefs.svelte` — barre partagée (boutons `lang-fr`/`lang-en` `aria-pressed`, `theme-toggle`
    `aria-label`), applique `lang`/`data-theme` au montage.
- **Attendee** (`App.svelte`) : toutes les chaînes via `t()` (join, speaker/OIDC, room/plan/légende/aide,
  footer) ; `joinError`/`notice`/`oidcError` stockent désormais une **clé** (re-traduite au changement de
  langue). Barre `Prefs` dans le header.
- **Thème** (`app.css`) : variables sous `:root,[data-theme=light]` + override `[data-theme=dark]` ;
  neutres « surface/line/muted » introduits pour que le sombre s'adapte (fin des `rgba` codés en dur dans
  `App.svelte`). `:focus-visible` global (focus clavier visible — a11y AA), y compris sièges SVG.
- **Consoles** : `Prefs` ajoutée aux headers de `Admin.svelte`, `MyData.svelte`, `Speaker.svelte`.

## Tests
- **@ui** `prefs.feature` (2 scénarios) : bascule langue (`lang-en`→`<html lang>`=en + « Join » ;
  `lang-fr`→fr + « Rejoindre ») ; bascule thème (`theme-toggle`→`<html data-theme>` dark↔light).
  Nouveau step `UiSteps` : `the html "{attr}" attribute is "{value}"`.
- **Déterminisme CI** : `UiSteps.i_open_the_spa_at` épingle le navigateur Playwright en `locale fr-FR` +
  `colorScheme LIGHT` (Chromium headless démarrait en `en-US` → la page rendait l'anglais et cassait les
  assertions FR existantes). Choix explicite de langue/thème dans le scénario prefs.
- Vert : Vite 126 modules ; acceptance non-@ui **48/48** (inchangé), @ui **11/11** (était 9).

## Hors périmètre (notés)
i18n **exhaustive** des consoles admin/speaker/mes-données (texte opérateur dense) ; thème par
composant fin ; sélecteur de langue côté Keycloak. Transverses déjà notés : vue reveal complète attendee,
WS speaker RS256 natif, modération superadmin/persistée, durcissement TOCTOU preview, `SmtpMailer`,
spec concurrency `@Scheduled` (remplacement de `PurgeScheduler`).
