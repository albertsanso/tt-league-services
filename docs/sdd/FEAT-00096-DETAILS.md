# Build Plan

> Draft outline. Make it concrete (file paths, POM snippets) before moving to `planned`.

1. Add the three modules to the root `pom.xml` `<modules>` list, without changing existing plugin or
   dependency management (root `AGENTS.md`: avoid unrelated parent-POM changes).
2. `tt-league-pipeline-orchestrator-core`: package root `org.cttelsamicsterrassa.data.pipeline`; plain Java 21 records/enums; JUnit 5 only.
   Add a dependency-direction test (reflection/classpath scan with plain JUnit, no new test framework) that
   fails when `org.springframework`, `jakarta.persistence` or `org.cttelsamicsterrassa.data.core` classes are referenced.
3. `tt-league-pipeline-orchestrator-runtime`: Spring Boot application (`spring-boot-starter-web`, `-actuator`, `-validation`), depends on
   `tt-league-pipeline-orchestrator-core`. Explicit, environment-driven configuration properties bound with `@Validated`; startup fails
   on missing platform URL, ingest URL, ingest API key, datasource or JWT secret. Testcontainers dependency
   declared for later persistence tests.
4. `tt-league-pipeline-orchestrator-frontend`: scaffold with Vite + React + TypeScript; Vitest + React Testing Library + jsdom; ESLint;
   Material UI. Wire `frontend-maven-plugin` the same way as `tt-data-league-frontend/pom.xml`
   (`npm ci`, `npm run lint`, `npm run build`, `npm test`).
5. Write `AGENTS.md` and `README.md` for each module; update the root `AGENTS.md` mission/architecture list,
   the dependency direction rules and the focused build commands.
6. Run `mvn test` from the root.

## Acceptance Criteria

- [ ] Maven modules `tt-league-pipeline-orchestrator-core`, `tt-league-pipeline-orchestrator-runtime` and `tt-league-pipeline-orchestrator-frontend` are added to the root reactor and `mvn test` builds them
- [ ] `tt-league-pipeline-orchestrator-core` has no Spring, JPA or HTTP-client dependency and no dependency on `tt-data-league-*` modules, and a test enforces this
- [ ] `tt-league-pipeline-orchestrator-runtime` is a Spring Boot 3 (Java 21) application with health and Actuator endpoints that fails at startup when required configuration is missing
- [ ] `tt-league-pipeline-orchestrator-frontend` is a React + TypeScript + Vite application built and tested (Vitest + React Testing Library) through `frontend-maven-plugin`
- [ ] Each new module has an `AGENTS.md` and README, and the root `AGENTS.md` module list and dependency rules include them
- [ ] The architecture decisions and the proposal gap analysis are recorded in this feature's details and reflected in the dependent backlog items

# Implementation Guidelines

- Out of scope: any orchestration behaviour, persistence tables or UI views; those are separate backlog items.
- Keep the orchestrator a separate deployable that talks to the platform (`tt-data-league-api-runtime`) and to
  `tt-league-ingest-rest` over HTTP only. Never read or write `tt-data-league-*` tables directly.
- No shared "common" module between orchestrator and platform: the platform REST contracts are the shared
  surface. Duplicating a few DTOs in the orchestrator is accepted to keep the dependency direction clean.

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
