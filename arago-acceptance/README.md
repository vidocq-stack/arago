# arago-acceptance

Black-box acceptance tests for Arago — **Cucumber + Testcontainers + Playwright** (cf. arago-spec §16).

The suite boots the Arago runtime **in-process** (`VidocqBootstrap`) against a throwaway **PostgreSQL
Testcontainer**, then exercises it as a black box over HTTP (REST) and through a headless browser
(the served Svelte SPA). DB coordinates and the HTTP port are injected as system properties (which
override `vidocq.properties`); Flyway migrations run at boot, so each run starts on a clean schema.

This module is **out of the fast reactor** — it builds only under the `acceptance` profile, so the
ordinary `mvn install` stays quick.

## Run

Prerequisites: a running **Docker** daemon (Testcontainers). On the first UI run, Playwright
downloads a Chromium build (~150 MB) into `~/.cache/ms-playwright`.

```bash
# whole suite (REST + UI)
mvn -Pacceptance -pl arago-acceptance -am test

# REST only (skip the browser scenarios)
mvn -Pacceptance -pl arago-acceptance -am test -Dcucumber.filter.tags='not @ui'
```

## Layout

- `src/test/resources/features/*.feature` — Gherkin scenarios (English). UI scenarios are tagged `@ui`.
- `AragoApp` — process-wide in-process boot harness (Postgres container + runtime + readiness wait).
- `RestSteps` — HTTP step definitions (status + JSON assertions).
- `UiSteps` — Playwright step definitions (`@ui`).
- `Hooks` — Cucumber `@BeforeAll` that boots the app once (teardown via JVM shutdown hook).
- `RunCucumberTest` — JUnit Platform suite entry point.

## Scope today

Baseline scenarios covering what already exists (Phase 0 + I1): `/health` (+ live/ready/started),
`/api/rooms/count`, and the SPA loading at `/`. The feature set grows with each phase — the v1
acceptance criteria (spec §12) become executable scenarios here.
