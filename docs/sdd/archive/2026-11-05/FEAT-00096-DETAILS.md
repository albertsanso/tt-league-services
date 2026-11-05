# Build Plan
1. **Root reactor (`pom.xml`).** Append `<module>tt-league-pipeline-orchestrator-core</module>`, `<module>tt-league-pipeline-orchestrator-runtime</module>` and
   `<module>tt-league-pipeline-orchestrator-frontend</module>` after `tt-data-league-frontend`. Add one `dependencyManagement` entry for
   `org.cttelsamicsterrassa:tt-league-pipeline-orchestrator-core:0.0.1-SNAPSHOT`, as for the existing modules. No other parent-POM change:
   versions for Spring Boot, JUnit, AssertJ and Mockito come from the imported `spring-boot-dependencies` 3.5.8 BOM.
   Add `tt-league-pipeline-orchestrator-frontend/node_modules/`, `tt-league-pipeline-orchestrator-frontend/dist/` and `tt-league-pipeline-orchestrator-frontend/target/` to the root `.gitignore`.
2. **`tt-league-pipeline-orchestrator-core` module.**
   - `pom.xml`: parent `tt-data-league-services`, packaging `jar`, no compile dependencies; test scope
     `org.junit.jupiter:junit-jupiter`, `org.assertj:assertj-core` (not `spring-boot-starter-test`, to keep Spring
     off the module's classpath).
   - `src/main/java/org/cttelsamicsterrassa/data/pipeline/core/package-info.java` documenting the module's role
     (state machine, tracker rules, polling policy, ports; filled by later features).
   - `src/test/java/org/cttelsamicsterrassa/data/pipeline/core/CoreDependencyRulesTest.java`: walks
     `src/main/java/**/*.java` and fails on any `import` of `org.springframework.`, `jakarta.persistence.`,
     `org.hibernate.`, `java.net.http.`, `org.cttelsamicsterrassa.data.core.`, `org.cttelsamicsterrassa.data.api.`
     or `org.cttelsamicsterrassa.data.load.`. It also parses the module `pom.xml` and fails on any
     non-test dependency (same idea as `tt-league-ingest/tests/test_workspace_layout.py`).
3. **`tt-league-pipeline-orchestrator-runtime` module.**
   - `pom.xml`: depends on `tt-league-pipeline-orchestrator-core`, `spring-boot-starter-web`, `spring-boot-starter-actuator`,
     `spring-boot-starter-validation`; test `spring-boot-starter-test`; `spring-boot-maven-plugin` with `repackage`
     as in `tt-data-league-api-runtime/pom.xml`. Persistence, security and Testcontainers are added by
     FEAT-00103/FEAT-00105, not here.
   - `org/cttelsamicsterrassa/data/pipeline/runtime/PipelineOrchestratorApplication.java` (`@SpringBootApplication`,
     `@ConfigurationPropertiesScan`).
   - `config/PipelineOrchestratorProperties.java`: `@ConfigurationProperties("tt.pipeline")` + `@Validated` record with
     nested records `platform(@NotNull URI baseUrl)`, `ingest(@NotNull URI baseUrl, @NotBlank String apiKey)`,
     `security(@NotBlank @Size(min = 32) String jwtSecret)`.
   - `src/main/resources/application.yml`: `server.port: ${PIPELINE_SERVER_PORT:8095}`;
     `tt.pipeline.platform.base-url: ${PIPELINE_PLATFORM_URL}`, `tt.pipeline.ingest.base-url: ${PIPELINE_INGEST_URL}`,
     `tt.pipeline.ingest.api-key: ${PIPELINE_INGEST_API_KEY}`, `tt.pipeline.security.jwt-secret: ${JWT_SIGNING_SECRET}`
     with **no defaults**, so a missing variable fails startup; `management.endpoints.web.exposure.include: health,info`.
   - Tests: `PipelineOrchestratorApplicationTest` (`@SpringBootTest` with test properties, context loads, health is UP
     via `TestRestTemplate`); `PipelineOrchestratorPropertiesTest` using `ApplicationContextRunner` to assert startup
     failure for each missing or invalid property (blank key, short secret, malformed URI).
4. **`tt-league-pipeline-orchestrator-frontend` module.**
   - `package.json` (`"type": "module"`): dependencies `react`, `react-dom`, `react-router-dom`, `@mui/material`,
     `@emotion/react`, `@emotion/styled`; dev dependencies `typescript`, `vite`, `@vitejs/plugin-react`, `vitest`,
     `jsdom`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`, `eslint`,
     `@eslint/js`, `typescript-eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`, `globals`.
     Use the same React/Vite/Vitest major versions as `tt-data-league-frontend/package.json`. Scripts: `dev`,
     `build` (`tsc -b && vite build`), `typecheck` (`tsc -b --noEmit`), `lint`, `test` (`vitest run`), `preview`.
     Commit `package-lock.json`.
   - `tsconfig.json` / `tsconfig.app.json` / `tsconfig.node.json` (strict), `vite.config.ts`, `vitest.config.ts`
     (`environment: 'jsdom'`, setup file importing `@testing-library/jest-dom/vitest`), `eslint.config.js`,
     `index.html`, `.gitignore`.
   - `src/main.tsx` (MUI `ThemeProvider` + `CssBaseline` + `BrowserRouter`), `src/App.tsx` rendering a placeholder
     "Pipeline control centre" app bar and empty route outlet, `src/theme.ts`, `src/App.test.tsx`.
   - `pom.xml`: copy the `frontend-maven-plugin` 1.12.0 setup of `tt-data-league-frontend/pom.xml` (Node v20.19.0,
     npm 10.2.4, local install under `target/node`) with executions `install-node-and-npm`, `npm ci`,
     `npm run lint` (phase `test`), `npm test` (phase `test`) and `npm run build` (phase `prepare-package`).
5. **Module guidance.** `AGENTS.md` and `README.md` for each new module: purpose, dependency direction
   (`tt-league-pipeline-orchestrator-runtime` -> `tt-league-pipeline-orchestrator-core`; no module depends on `tt-data-league-*`; platform and ingest are reached over HTTP only),
   configuration table (`PIPELINE_*`, `JWT_SIGNING_SECRET`), build and run commands
   (`mvn -pl tt-league-pipeline-orchestrator-runtime -am test`, `npm run dev`).
6. **Root `AGENTS.md`.** Add the three modules to "Mission and architecture" and to the "read the nearest `AGENTS.md`"
   list; add the rule "orchestrator modules integrate with the platform and `tt-league-ingest` only through their
   REST APIs and never read or write platform tables"; add the focused build commands.
7. **Validation.** `mvn -pl tt-league-pipeline-orchestrator-core,tt-league-pipeline-orchestrator-runtime,tt-league-pipeline-orchestrator-frontend -am test`, then the full `mvn test` from the root; review the diff for
   `target/`, `node_modules/` and `dist/` content.

## Acceptance Criteria

- [x] Maven modules `tt-league-pipeline-orchestrator-core`, `tt-league-pipeline-orchestrator-runtime` and `tt-league-pipeline-orchestrator-frontend` are added to the root reactor and `mvn test` builds and tests them
- [x] `tt-league-pipeline-orchestrator-core` has no Spring, JPA or HTTP-client dependency and no dependency on `tt-data-league-*` modules, and a test enforces this
- [x] `tt-league-pipeline-orchestrator-runtime` is a Spring Boot 3.5 (Java 21) application exposing `/actuator/health` that fails at startup when a required `tt.pipeline.*` property is missing or invalid
- [x] `tt-league-pipeline-orchestrator-frontend` is a React + TypeScript + Material UI application built with Vite, type-checked, linted and tested (Vitest + React Testing Library) through `frontend-maven-plugin`
- [x] Each new module has an `AGENTS.md` and README, and the root `AGENTS.md` module list, dependency rules and build commands include them
- [x] The architecture decisions and the proposal gap analysis are recorded in this feature's details and reflected in the dependent backlog items

# Implementation Guidelines

- Out of scope: run model, persistence, security, orchestration behaviour and UI views (FEAT-00103 onward).
- Keep the orchestrator a separate deployable that talks to the platform (`tt-data-league-api-runtime`) and to
  `tt-league-ingest-rest` over HTTP only. Never read or write `tt-data-league-*` tables directly.
- No shared "common" module between orchestrator and platform: the platform REST contracts are the shared
  surface. Duplicating a few DTOs in the orchestrator is accepted to keep the dependency direction clean.
- Do not add Testcontainers, Flyway or Spring Security here; their features add them with their first use.

# Notes

## Source

Proposal: [update-data-pipeline-orchestrator.md](../analysis/update-data-pipeline-orchestrator.md) (2026-10-03). Analysis made on 2026-10-04 against
the `tt-data-league-*` modules and `tt-league-ingest` (FEAT-00095).

## Gap analysis: proposal vs current implementation

| Proposal item | Current state (2026-10-04) | Backlog item |
| --- | --- | --- |
| Shared Python core library | Done: `ingest_common` in the `tt-league-ingest` uv workspace (FEAT-00095) | — |
| One CLI contract (`run`) | Done: `tt-league-ingest run`; plus a FastAPI REST service (`POST/GET /api/v1/ingest/runs`, `X-API-Key`, one run at a time, in-memory history) | Reused as the job runner |
| `--scope` with several groups | Partial: single-valued `category`/`group`/`phase`/`territory`/`gender` filters plus a match-day set | Ingest multi-scope runs |
| Standard exit codes (no changes, source unavailable, parse error) | Missing: `SUCCEEDED` / `COMPLETED_WITH_ISSUES` / `FAILED`; CLI exits 0/1/2; a delta package with nothing selected fails | Ingest run outcome |
| ETag / hash incremental downloads | Partial: match-day-status based skipping (complete and future jornadas are not re-requested) | Not planned now |
| `manifest.json` provenance (run id, hashes, counts) | Partial: `source`, `seasons`, `assets`, `mode` (`ResourceZipService` owns the contract) | ZIP provenance |
| Full fixture list | Done: pending actas (`acta_publicada=false`) are stored as `SCHEDULED` matches (FEAT-00081); `match-days-status.json` per source | Exposed by the ingest scopes item |
| Containers and object storage | Missing | Deployment packaging; artifacts kept by the orchestrator |
| Async import API returning an import id | Partial: `POST /administration/import/upload` returns 202 with no id and only stores files; the import needs `preview`/`start` by `importResourceId`; run registry is in memory | Import jobs API |
| ZIP-level idempotency | Partial: natural-key upsert, `id_partido` (FEAT-00083), natural-key guard (FEAT-00085), amended actas (FEAT-00089); no ZIP-level key | Import jobs API |
| Import report with a diff | Partial: `ImportProcessResult` with `ImportLifecycleCounters` (`scheduledCreated`, `upgradedToPlayed`, `rescheduled`, ...) and `roundProgress` | Import jobs API |
| Domain events | Missing | Deferred: the orchestrator is a separate process and recomputes after each run |
| Service-to-service auth | Missing: user JWT only (HS256, 30 h default expiry); ingest upload uses a bearer token from `TT_LEAGUE_API_TOKEN` | Service credentials |
| Match-day detection | Partial: `MatchRepository.findRoundProgress` (FEAT-00084), calendar API with derived postponed/overdue and a 7-day grace period, manual overdue mark (FEAT-00092) | Round-progress endpoint, Match-day tracker |
| Calendar UI | Matches calendar exists in `tt-data-league-frontend` (FullCalendar, FEAT-00092/93); no pipeline view | Pipeline calendar UI |
| Tracking schema and migrations | Missing; the platform uses `ddl-auto: update` | Orchestrator run model and persistence (Flyway in its own schema) |
| `CANCELLED` / `WALKOVER` statuses | Domain has only `SCHEDULED` / `PLAYED` | Open question |

## Decisions (2026-10-04, D1-D8 confirmed by the user on 2026-10-04)

- **D1 Build tool: Maven, not Gradle.** The command asks for "new maven modules", the repository is one Maven
  reactor, and the root `AGENTS.md` forbids a new build tool unless required.
- **D2 Separate service, REST integration.** The orchestrator runs as its own Spring Boot application, with no
  compile dependency on `tt-data-league-core-domain` or the JPA adapter. It reads match state through platform
  REST endpoints, so the domain model stays the single source of truth for "reported". It stores match UUIDs
  as plain references without foreign keys into platform tables.
- **D3 Helper module: `tt-league-pipeline-orchestrator-core`.** Framework-free run state machine, tracker rules, polling policy, scope
  builder and ports (`IngestGateway`, `ImportGateway`, `PlatformMatchGateway`, `ArtifactStore`, repositories,
  `Notifier`). Adapters (JPA/Flyway, HTTP clients, controllers, scheduler) live in `tt-league-pipeline-orchestrator-runtime`. Split a separate
  persistence-adapter module only if the runtime grows large.
- **D4 Job runner.** The first `JobRunner` adapter drives the existing `tt-league-ingest-rest` service. Docker and
  Kubernetes runners wait for the deployment decision.
- **D5 ZIP flow.** The orchestrator asks ingest for `download`, `parse` and `package` (not `upload`), fetches the
  ZIP, keeps it in its artifact store and submits it to the platform import jobs API. It then knows the import
  id and can replay the import.
- **D6 Frontend stack.** A separate module, as requested: React + TypeScript + Material UI + React Testing Library,
  built with Vite and tested with **Vitest instead of Jest**. That matches `tt-data-league-frontend` and avoids a
  second test framework.
- **D7 Auth.** Users: the orchestrator validates platform-issued JWTs (shared HS256 secret from the environment).
  Any authenticated user can view, `matches:write` is needed to trigger runs and close match days, and `ADMIN`
  to change policy settings. Orchestrator to platform: a dedicated service credential. Orchestrator to ingest:
  `X-API-Key`.
- **D8 Persistence.** PostgreSQL schema `pipeline`, managed by Flyway inside `tt-league-pipeline-orchestrator-runtime` only. The platform's
  `ddl-auto` setup stays unchanged.
- **D9 Deployment: single VM with Docker Compose** (user decision, 2026-10-04). Platform, orchestrator (runtime and
  frontend), `tt-league-ingest-rest` and PostgreSQL run as Compose services on one VM. This settles D4: the
  ingest-REST `JobRunner` is the only runner; a Kubernetes runner is not planned, and a Docker Engine runner is
  not needed because the ingest service already runs as a long-lived container. Artifacts and ingest data live
  on named volumes instead of object storage.
- **D10 Pipeline UI: separate application** (user decision, 2026-10-04). `tt-league-pipeline-orchestrator-frontend` stays its own app
  (D6), not a "Pipeline" area in `tt-data-league-frontend`. It signs in through the platform login endpoint and
  accepts the duplicated login and calendar components.

## Open questions

- ~~Deployment target: a single VM with Docker Compose, or an existing Kubernetes cluster?~~ Resolved
  2026-10-04: single VM with Docker Compose (D9).
- ~~Pipeline UI: keep a separate frontend (D6), or add a "Pipeline" area to `tt-data-league-frontend`?~~ Resolved
  2026-10-04: separate application (D10).
- Do all three sources publish full fixtures with dates for every category?
- Is the final team score enough for "reported", or are individual games required?
- Notification channel for alerts (e-mail, Telegram, Slack)?
- Is a 7-day grace period before OVERDUE right for all federations? (The platform already defaults to 7 days.)
- Do `CANCELLED` / `WALKOVER` need domain support, or does the orchestrator only offer a manual "ignore"?

## Backlog map (rollout phases from the proposal)

- Phase 1, standardise ingest: ingest run outcome, ingest multi-scope runs and match-day status, ZIP provenance.
- Phase 2, headless automation: import jobs API, service credentials, run model, run executor, runs API, fixed scheduler.
- Phase 3, match-day tracking: round-progress endpoint, match-day tracker, scoped runs and adaptive polling.
- Phase 4, control-centre UI: frontend shell, runs view, calendar and match-day detail.
- Phase 5, history and statistics: notifications, statistics, replay, observability; deployment packaging alongside.

## Planning notes (2026-10-04)

- The existing `tt-data-league-frontend` Maven build runs `lint` and `build` but not its Vitest suite. The new
  frontend also runs `npm test` in the Maven `test` phase so `mvn test` covers it.
- `mvn test` will download Node and npm packages for the new frontend, as it already does for
  `tt-data-league-frontend`.
- Placeholders without defaults make Spring fail with "Could not resolve placeholder" before binding. That is the
  intended clear startup failure; the properties test asserts the validation messages for present-but-invalid values.

## Implementation notes (2026-10-04)

- Delivered the three Maven modules, root reactor/`dependencyManagement`/`.gitignore` entries, per-module `AGENTS.md` and
  README, and the root `AGENTS.md` updates.
- Validation: `mvn test` from the root passes (core 2 tests, runtime 6 tests, frontend lint + Vitest + build).
- Deviations from the plan: the runtime properties test uses a blank ingest URL instead of a malformed one, because
  Spring's `String` -> `URI` conversion percent-encodes illegal characters instead of failing; a missing platform URL
  fails on the `platform` group. The frontend also declares `@testing-library/dom` (peer of `@testing-library/react`),
  `@types/node` and `yaml` as dev dependencies, which `npm install` needs to resolve cleanly.
- The lockfile was generated with `npm install --legacy-peer-deps` followed by `npm install`, because a fresh
  `npm install` hit npm's `Cannot read properties of null (reading 'edgesOut')` bug on npm 10.2.4 and 10.9.2.
