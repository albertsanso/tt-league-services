# FEATURES.md — Feature Registry & Build Plans

This file is the single source of truth for planned, in-progress, and completed features.

**For humans:** Add new features under `## Backlog` using the template in [`task-management.md`](./task-management.md).
**For agents:** Only work on features marked `status: ready`. Update status as you progress. Never modify features marked `status: done` or `status: in-progress` unless explicitly asked.

---

## Status Legend

| Status | Meaning |
|-|-|
| `idea` | Captured but not planned yet — no build plan written |
| `planned` | Build plan written, not yet ready to implement |
| `ready` | Build plan approved, agent can start |
| `in-progress` | Currently being implemented |
| `in-review` | Implementation finalized and awaiting user review |
| `done` | Shipped after explicit user approval |
| `blocked` | Waiting on a dependency or decision |

---

## Main index

- [FEAT-00116: Container packaging for orchestrator and ingest services](### [FEAT-00116] Container packaging for orchestrator and ingest services)
- [FEAT-00115: Orchestrator metrics and run-correlated logging](### [FEAT-00115] Orchestrator metrics and run-correlated logging)
- [FEAT-00114: Replay import from retained run artifacts](### [FEAT-00114] Replay import from retained run artifacts)
- [FEAT-00113: Pipeline history statistics and dashboard](### [FEAT-00113] Pipeline history statistics and dashboard)
- [FEAT-00112: Orchestrator notifications and alerts](### [FEAT-00112] Orchestrator notifications and alerts)
- [FEAT-00111: Pipeline calendar and match-day detail views](### [FEAT-00111] Pipeline calendar and match-day detail views)
- [FEAT-00110: Orchestrator runs view with live logs and Run now dialog](### [FEAT-00110] Orchestrator runs view with live logs and Run now dialog)
- [FEAT-00109: Orchestrator frontend shell, authentication and API client](### [FEAT-00109] Orchestrator frontend shell, authentication and API client)
- [FEAT-00108: Scoped runs and adaptive polling](### [FEAT-00108] Scoped runs and adaptive polling)
- [FEAT-00107: Match-day tracker for open match days and pending matches](### [FEAT-00107] Match-day tracker for open match days and pending matches)
- [FEAT-00106: Orchestrator fixed-schedule trigger](### [FEAT-00106] Orchestrator fixed-schedule trigger)
- [FEAT-00105: Orchestrator runs API with manual trigger and live events](### [FEAT-00105] Orchestrator runs API with manual trigger and live events)
- [FEAT-00104: Orchestrator run executor with ingest and import gateways](### [FEAT-00104] Orchestrator run executor with ingest and import gateways)
- [FEAT-00103: Orchestrator pipeline run model and persistence](### [FEAT-00103] Orchestrator pipeline run model and persistence)
- [FEAT-00102: Round progress and open match-day query endpoint](### [FEAT-00102] Round progress and open match-day query endpoint)
- [FEAT-00101: Service credentials for platform-to-platform API calls](### [FEAT-00101] Service credentials for platform-to-platform API calls)
- [FEAT-00100: Machine-friendly asynchronous import jobs API](### [FEAT-00100] Machine-friendly asynchronous import jobs API)
- [FEAT-00099: Upload ZIP provenance manifest and package retrieval](### [FEAT-00099] Upload ZIP provenance manifest and package retrieval)
- [FEAT-00098: Ingest multi-scope runs and match-day status endpoint](### [FEAT-00098] Ingest multi-scope runs and match-day status endpoint)
- [FEAT-00097: Ingest run outcome classification for unattended runs](### [FEAT-00097] Ingest run outcome classification for unattended runs)
- [FEAT-00096: Pipeline orchestrator architecture baseline and module skeleton](### [FEAT-00096] Pipeline orchestrator architecture baseline and module skeleton)
- [FEAT-00095: New Workspace module for results and matches data ingestion](### [FEAT-00095] New Workspace module for results and matches data ingestion)

## In Progress

No features currently in progress.
## In Review

No features currently in review.
## Backlog

### [FEAT-00096] Pipeline orchestrator architecture baseline and module skeleton
- **Status:** idea
- **Priority:** high
- **Effort:** large
- **Depends on:** —

#### Goal
Establish the pipeline orchestrator's module layout, technology decisions and integration contracts so the other orchestrator features build on one agreed baseline.

#### Acceptance Criteria
- [ ] Maven modules `tt-league-pipeline-orchestrator-core`, `tt-league-pipeline-orchestrator-runtime` and `tt-league-pipeline-orchestrator-frontend` are added to the root reactor and `mvn test` builds them
- [ ] `tt-league-pipeline-orchestrator-core` has no Spring, JPA or HTTP-client dependency and no dependency on `tt-data-league-*` modules, and a test enforces this
- [ ] `tt-league-pipeline-orchestrator-runtime` is a Spring Boot 3 (Java 21) application with health and Actuator endpoints that fails at startup when required configuration is missing
- [ ] `tt-league-pipeline-orchestrator-frontend` is a React + TypeScript + Vite application built and tested (Vitest + React Testing Library) through `frontend-maven-plugin`
- [ ] Each new module has an `AGENTS.md` and README, and the root `AGENTS.md` module list and dependency rules include them
- [ ] The architecture decisions and the proposal gap analysis are recorded in this feature's details and reflected in the dependent backlog items

#### Feature Details
→ See [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00097] Ingest run outcome classification for unattended runs
- **Status:** idea
- **Priority:** high
- **Effort:** medium
- **Depends on:** —

#### Goal
Let an unattended caller tell a no-change run, a transient source outage and a parse failure apart from the ingest run report instead of a single FAILED status.

#### Acceptance Criteria
- [ ] The run report has an `outcome` of `SUCCEEDED`, `NO_CHANGES`, `COMPLETED_WITH_ISSUES`, `SOURCE_UNAVAILABLE` or `FAILED`, plus a `retryable` flag; the existing `status` stays for compatibility
- [ ] When download and parse change no JSON file, `package` and `upload` are skipped and the outcome is `NO_CHANGES` instead of a delta packaging failure
- [ ] HTTP connection errors, timeouts and 5xx answers from the federation site after the legacy retries give `SOURCE_UNAVAILABLE` with `retryable=true`
- [ ] Parse and schema-validation failures give `COMPLETED_WITH_ISSUES` or `FAILED` with `retryable=false`
- [ ] The CLI adds exit code 3 for `NO_CHANGES` and 4 for `SOURCE_UNAVAILABLE`, keeping 0/1/2, and the REST run DTO exposes `outcome` and `retryable`
- [ ] Tests cover each outcome with fake transports and fixtures; README documents outcomes and exit codes

#### Feature Details
→ See [FEAT-00097-DETAILS.md](./FEAT-00097-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00098] Ingest multi-scope runs and match-day status endpoint
- **Status:** idea
- **Priority:** high
- **Effort:** medium
- **Depends on:** —

#### Goal
Let the orchestrator refresh only the open groups of several competitions in one ingest run and read each source's match-day status without scraping.

#### Acceptance Criteria
- [ ] `POST /api/v1/ingest/runs` accepts `scopes: [{category, group, phase, territory, gender, matchDays}]`, and a run downloads, parses and packages only the union of the scopes
- [ ] The existing single `filters` body stays valid; sending both `filters` and `scopes` is a 400
- [ ] Unsupported scope fields for a source fail the run before any network call, as filters do today
- [ ] `GET /api/v1/ingest/sources/{source}/match-days-status?season=` returns the current `match-days-status.json` (404 when none exists)
- [ ] The CLI `run` accepts a `--scope-file` JSON with the same shape
- [ ] Tests cover scope union, validation and the status endpoint; README documents both

#### Feature Details
→ See [FEAT-00098-DETAILS.md](./FEAT-00098-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00099] Upload ZIP provenance manifest and package retrieval
- **Status:** idea
- **Priority:** high
- **Effort:** medium
- **Depends on:** —

#### Goal
Make every upload ZIP self-describing (run id, generator, content hash, match counts) and retrievable from the ingest service, so imports can be deduplicated, traced and replayed.

#### Acceptance Criteria
- [ ] `manifest.json` gains optional `runId`, `generator`, `generatorVersion`, `contentSha256` and `matchCounts` (`expected`, `withResult`, `pending`) fields
- [ ] `contentSha256` is computed deterministically over the sorted ZIP entries (excluding the manifest), so identical content gives the same hash
- [ ] `ResourceZipService` accepts the new optional fields, validates their types, and still accepts manifests without them
- [ ] `GET /api/v1/ingest/runs/{runId}/package` streams the run's ZIP with its SHA-256 in a response header (404 when the run produced none)
- [ ] The upload manifest section of `tt-data-league-api-runtime/README.md` and `tt-league-ingest/README.md` describe the new fields
- [ ] Python and Java tests cover hash stability, optional-field parsing and rejection of malformed values

#### Feature Details
→ See [FEAT-00099-DETAILS.md](./FEAT-00099-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00100] Machine-friendly asynchronous import jobs API
- **Status:** idea
- **Priority:** high
- **Effort:** large
- **Depends on:** FEAT-00099

#### Goal
Let an automated client submit an upload ZIP and follow the resulting import to completion through one job id, with idempotency and a structured change report.

#### Acceptance Criteria
- [ ] `POST /api/v1/administration/import/jobs` (multipart ZIP, optional `runId`, `allowPublishedShrink`) stores the ZIP and starts the import of every affected season, returning `202 {importJobId}`
- [ ] `GET /api/v1/administration/import/jobs/{id}` returns status `QUEUED`, `STORING`, `IMPORTING`, `SUCCEEDED`, `PARTIAL` or `FAILED` with the `ImportProcessResult` counters, lifecycle counters and round progress per season
- [ ] `GET /api/v1/administration/import/jobs?source=&from=&to=` lists job history, most recent first
- [ ] Submitting a ZIP whose manifest `contentSha256` matches a succeeded job returns that job (200) without re-importing
- [ ] At most one job per source runs at a time; a second submission for the same source is queued and runs in order
- [ ] Jobs are persisted, survive a restart (an interrupted job ends `FAILED` with a clear reason), and the datamodel document is updated
- [ ] Shrink-check (409) and invalid-ZIP (400) behaviour matches the existing upload endpoint, which stays unchanged

#### Feature Details
→ See [FEAT-00100-DETAILS.md](./FEAT-00100-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00101] Service credentials for platform-to-platform API calls
- **Status:** idea
- **Priority:** high
- **Effort:** medium
- **Depends on:** —

#### Goal
Let the orchestrator and the ingest service call the platform import and read APIs with a dedicated, scoped service credential instead of a user's JWT.

#### Acceptance Criteria
- [ ] A service credential (API key presented as `Authorization: ApiKey <key>` or `X-API-Key`) is configured from the environment, with an explicit permission set (`imports:write`, `matches:read`)
- [ ] Requests with a valid service credential reach the import jobs and calendar/round-progress endpoints and nothing outside their permissions
- [ ] Keys are compared in constant time, stored only as configuration (never logged), and an invalid or missing key is a 401
- [ ] User JWT authentication is unchanged; startup fails clearly when a configured service credential is malformed
- [ ] Security tests cover allowed, forbidden and invalid-key cases; the api-runtime README documents the configuration

#### Feature Details
→ See [FEAT-00101-DETAILS.md](./FEAT-00101-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00102] Round progress and open match-day query endpoint
- **Status:** idea
- **Priority:** high
- **Effort:** medium
- **Depends on:** —

#### Goal
Expose per source and season which match days are open, with their date window and match status counts, so the orchestrator can decide what to refresh from the domain model.

#### Acceptance Criteria
- [ ] `GET /api/v1/match/round-progress?source=&season=` returns, per competition/group/phase and jornada, the counts of scheduled, played, derived postponed and overdue matches, and the first and last scheduled dates
- [ ] Counts reuse `MatchRepository.findRoundProgress` and the calendar's derived postponed/overdue rules; no state is stored
- [ ] Optional `competition` and `onlyOpen=true` filters narrow the result; `onlyOpen` keeps jornadas with any non-played match or a window overlapping today plus the grace period
- [ ] The endpoint needs `matches:read` and is available to the service credential
- [ ] Domain, JPA and controller tests cover the FCTT 2026-2027 shape and an overdue match

#### Feature Details
→ See [FEAT-00102-DETAILS.md](./FEAT-00102-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00103] Orchestrator pipeline run model and persistence
- **Status:** idea
- **Priority:** high
- **Effort:** large
- **Depends on:** FEAT-00096

#### Goal
Record every orchestrated run, its steps, artifacts and import report durably, with at most one active run per source enforced by the database.

#### Acceptance Criteria
- [ ] `tt-league-pipeline-orchestrator-core` models `PipelineRun` and `PipelineStep` with states `QUEUED`, `RUNNING_INGEST`, `NO_CHANGES`, `PACKED`, `IMPORTING`, `SUCCEEDED`, `PARTIAL`, `FAILED`, triggers `SCHEDULED`/`MANUAL`/`RETRY` and `requestedBy`, and rejects illegal transitions
- [ ] Flyway migrations in `tt-league-pipeline-orchestrator-runtime` create schema `pipeline` with `pipeline_run`, `pipeline_step`, `run_artifact` and `import_report`
- [ ] A partial unique index guarantees at most one `QUEUED`/`RUNNING_*`/`PACKED`/`IMPORTING` run per source
- [ ] JPA adapters implement the core repository ports; Testcontainers PostgreSQL tests cover the migrations, the uniqueness rule and state persistence
- [ ] The module documents its tables in a datamodel document next to the module README

#### Feature Details
→ See [FEAT-00103-DETAILS.md](./FEAT-00103-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00104] Orchestrator run executor with ingest and import gateways
- **Status:** idea
- **Priority:** high
- **Effort:** large
- **Depends on:** FEAT-00097, FEAT-00099, FEAT-00100, FEAT-00101, FEAT-00103

#### Goal
Drive one pipeline run end to end (ingest, package retrieval, import, report) without user assistance, with timeouts and bounded retries for transient failures.

#### Acceptance Criteria
- [ ] `IngestServiceJobRunner` starts a `tt-league-ingest-rest` run (`download`, `parse`, `package`) for the run's source and scope, polls it and maps its `outcome` to `NO_CHANGES`, `PACKED` or `FAILED`
- [ ] On `PACKED`, the ZIP is fetched, its SHA-256 verified and stored in a configured artifact directory (`run_artifact` row), then submitted to the platform import jobs API
- [ ] The import job is polled to completion; its counters are stored in `import_report` and the run ends `SUCCEEDED`, `PARTIAL` or `FAILED`
- [ ] Every step has a configurable timeout; `SOURCE_UNAVAILABLE` and HTTP 5xx are retried up to 3 times with exponential back-off, other failures are not
- [ ] An ingest run that disappears (ingest restarted, 404) fails the step with a clear, retryable error
- [ ] Tests use stubbed HTTP servers (no network) for success, no-change, retry, timeout and import-failure paths

#### Feature Details
→ See [FEAT-00104-DETAILS.md](./FEAT-00104-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00105] Orchestrator runs API with manual trigger and live events
- **Status:** idea
- **Priority:** high
- **Effort:** medium
- **Depends on:** FEAT-00103, FEAT-00104

#### Goal
Let operators start a run on demand and follow runs and their steps live, through the same path the scheduler uses.

#### Acceptance Criteria
- [ ] `POST /api/pipeline/runs` (`source` or `ALL`, scope `OPEN_MATCH_DAYS`/`GROUP`/`FULL_SEASON`, `force`) creates `MANUAL` runs recording the user id
- [ ] A trigger for a source with an active run is rejected with 409 and a clear message (configurable to queue instead)
- [ ] `GET /api/pipeline/runs` (filters: source, status, from/to; paged) and `GET /api/pipeline/runs/{id}` return runs with steps, durations, issues and import report
- [ ] `GET /api/pipeline/events` streams run and step transitions as Server-Sent Events
- [ ] Platform JWTs are validated; viewing needs authentication and triggering needs `matches:write`
- [ ] Controller and security tests cover validation, 409, permissions and the event stream

#### Feature Details
→ See [FEAT-00105-DETAILS.md](./FEAT-00105-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00106] Orchestrator fixed-schedule trigger
- **Status:** idea
- **Priority:** high
- **Effort:** medium
- **Depends on:** FEAT-00104

#### Goal
Remove manual uploads by triggering full-scope runs per source on an explicitly configured schedule.

#### Acceptance Criteria
- [ ] Each source has an explicitly configured cron expression; a source without one is never scheduled, and an invalid expression fails startup
- [ ] Scheduled ticks create `SCHEDULED` runs through the same trigger path as manual runs and skip a source with an active run
- [ ] ShedLock (JDBC, `pipeline` schema) ensures only one orchestrator instance fires a tick
- [ ] Tests cover tick handling, skip-when-active and lock behaviour; README documents the configuration

#### Feature Details
→ See [FEAT-00106-DETAILS.md](./FEAT-00106-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00107] Match-day tracker for open match days and pending matches
- **Status:** idea
- **Priority:** medium
- **Effort:** large
- **Depends on:** FEAT-00102, FEAT-00104

#### Goal
Keep an up-to-date set of open match days per source with each match's reporting status, so the orchestrator knows what is still pending and when a match day is complete.

#### Acceptance Criteria
- [ ] After every final run state and on a periodic recompute, the tracker reads round progress and calendar data from the platform and upserts `match_day` and `match_tracking` rows
- [ ] Match statuses `SCHEDULED`, `AWAITING_RESULT`, `REPORTED`, `POSTPONED` and `OVERDUE` follow the platform's derived states and grace period; `reported_at` records the first run that saw the result
- [ ] A match day closes when every match is reported, ignored or postponed out of its window; several match days can be open at once
- [ ] Operators with `matches:write` can close a match day manually, mark a match ignored and add a note, and each action records who and when
- [ ] Tests cover window calculation, postponed matches, closing rules and manual actions

#### Feature Details
→ See [FEAT-00107-DETAILS.md](./FEAT-00107-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00108] Scoped runs and adaptive polling
- **Status:** idea
- **Priority:** medium
- **Effort:** large
- **Depends on:** FEAT-00098, FEAT-00106, FEAT-00107

#### Goal
Poll each source only for its open groups, as often as their matches require, and stop by itself when match days are complete.

#### Acceptance Criteria
- [ ] A scope builder turns a source's open match days into ingest `scopes` (territory/category/group/phase/match days)
- [ ] `poll_schedule` stores the next run, interval, consecutive no-change count and policy level per source and scope hash
- [ ] Poll intervals follow the proposal's policy table (configurable per source), double after 3 consecutive `NO_CHANGES` up to the next level, and stop with an alert for matches overdue beyond 21 days
- [ ] A weekly full-scope run (and one at season start) refreshes fixtures, phases and re-draws
- [ ] Admins can change the policy settings through an API; tests cover each policy level and the back-off

#### Feature Details
→ See [FEAT-00108-DETAILS.md](./FEAT-00108-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00109] Orchestrator frontend shell, authentication and API client
- **Status:** idea
- **Priority:** medium
- **Effort:** medium
- **Depends on:** FEAT-00096, FEAT-00105

#### Goal
Give operators a signed-in pipeline control centre shell that the runs, calendar and statistics views plug into.

#### Acceptance Criteria
- [ ] Users sign in through the platform login endpoint; the JWT is kept for the session and expiry sends the user back to sign in
- [ ] The layout has navigation for Calendar, Runs and Statistics, with routes lazy-loaded
- [ ] A typed API client covers the orchestrator endpoints, and a hook subscribes to the SSE event stream with reconnection
- [ ] Controls that need `matches:write` or `ADMIN` are hidden or disabled for other users
- [ ] Vitest + React Testing Library tests cover login, routing, the client and the SSE hook

#### Feature Details
→ See [FEAT-00109-DETAILS.md](./FEAT-00109-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00110] Orchestrator runs view with live logs and Run now dialog
- **Status:** idea
- **Priority:** medium
- **Effort:** medium
- **Depends on:** FEAT-00109

#### Goal
Let operators see every run, follow the running one live and start a run on demand from the UI.

#### Acceptance Criteria
- [ ] A runs table (newest first) shows trigger, scope, duration, step badges and outcome, with filters by source, status and date
- [ ] A run detail shows steps, issues, artifacts and the import report, and updates live while the run is active
- [ ] A Run now dialog (source or all, scope type, force) creates a manual run and shows the 409 message when one is active
- [ ] Tests cover table rendering, live updates and the dialog

#### Feature Details
→ See [FEAT-00110-DETAILS.md](./FEAT-00110-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00111] Pipeline calendar and match-day detail views
- **Status:** idea
- **Priority:** medium
- **Effort:** large
- **Depends on:** FEAT-00107, FEAT-00109

#### Goal
Show reported and pending matches per match day on a calendar and let operators act on a match day from its detail view.

#### Acceptance Criteria
- [ ] A month/week calendar shows one entry per match day and group, filterable by source, season, category and phase
- [ ] Entries are coloured by completion (all reported, in progress, has overdue, future) and show `reported / total`
- [ ] The match-day detail lists matches with status, result and reported-at, and a timeline of the runs that touched it
- [ ] Operators can refresh just that group, close the match day, mark a match ignored and add a note
- [ ] Tests cover colouring rules, filters and each action

#### Feature Details
→ See [FEAT-00111-DETAILS.md](./FEAT-00111-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00112] Orchestrator notifications and alerts
- **Status:** idea
- **Priority:** low
- **Effort:** medium
- **Depends on:** FEAT-00104, FEAT-00107

#### Goal
Tell operators when a match day closes or when the pipeline needs attention, without watching the UI.

#### Acceptance Criteria
- [ ] A `Notifier` port has one adapter for the chosen channel, configured from the environment and disabled when unconfigured
- [ ] Alerts fire for: match day closed, two consecutive failed runs for a source, a match unreported past a configured threshold, and no successful run in 24 h during an open match day
- [ ] Each alert is sent once per condition until it clears; tests use a fake notifier

#### Feature Details
→ See [FEAT-00112-DETAILS.md](./FEAT-00112-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00113] Pipeline history statistics and dashboard
- **Status:** idea
- **Priority:** low
- **Effort:** large
- **Depends on:** FEAT-00107, FEAT-00108, FEAT-00109

#### Goal
Report reporting timeliness, pending matches, run outcomes and source health over the season.

#### Acceptance Criteria
- [ ] A daily job aggregates `daily_stats` (runs, failures, matches reported, average time to report, pending at end of day) per source
- [ ] Statistics endpoints return reporting progress per match day, time to report (median, p90) per source and category, pending by age, corrections after first report and runs by outcome
- [ ] A dashboard page charts these figures and a source-health panel (HTTP errors, timeouts, parse errors per source)
- [ ] Tests cover the aggregation and the endpoints

#### Feature Details
→ See [FEAT-00113-DETAILS.md](./FEAT-00113-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00114] Replay import from retained run artifacts
- **Status:** idea
- **Priority:** low
- **Effort:** medium
- **Depends on:** FEAT-00104, FEAT-00110

#### Goal
Re-import any past run's ZIP after a fix, without downloading from the federation again.

#### Acceptance Criteria
- [ ] Operators can replay the import of a past run from the API and the runs view; it creates a `RETRY` run linked to the original
- [ ] Artifacts follow a configurable retention policy (for example ZIPs for the season, raw files 90 days), enforced by a cleanup job
- [ ] Replaying an unchanged ZIP returns the existing import job (idempotency) and the run records that
- [ ] Tests cover replay, retention and the idempotent case

#### Feature Details
→ See [FEAT-00114-DETAILS.md](./FEAT-00114-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00115] Orchestrator metrics and run-correlated logging
- **Status:** idea
- **Priority:** low
- **Effort:** medium
- **Depends on:** FEAT-00104

#### Goal
Make the pipeline observable with metrics and logs that trace one run across the orchestrator and ingest.

#### Acceptance Criteria
- [ ] Micrometer metrics (runs by outcome, step durations, pending matches, open match days) are exposed through Actuator in Prometheus format
- [ ] The orchestrator logs in structured JSON with `runId`, and passes its `runId` to the ingest service, which includes it on every log line of that run
- [ ] Tests check metric registration and run-id propagation

#### Feature Details
→ See [FEAT-00115-DETAILS.md](./FEAT-00115-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00116] Container packaging for orchestrator and ingest services
- **Status:** idea
- **Priority:** medium
- **Effort:** medium
- **Depends on:** FEAT-00096, FEAT-00104

#### Goal
Run the orchestrator, its frontend and the ingest service together on a single VM with Docker Compose.

#### Acceptance Criteria
- [ ] Container images exist for `tt-league-ingest-rest` and the orchestrator runtime (serving or alongside the built frontend)
- [ ] A Docker Compose setup for a single VM runs the platform, orchestrator runtime and frontend, `tt-league-ingest-rest` and PostgreSQL, wired through environment variables and a reverse proxy, with no committed secrets
- [ ] Images run as non-root, expose health checks, and keep data and artifact directories on volumes
- [ ] READMEs document build and run commands

#### Feature Details
→ See [FEAT-00116-DETAILS.md](./FEAT-00116-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---
## Done

### [FEAT-00095] New Workspace module for results and matches data ingestion
- **Status:** done
- **Priority:** medium
- **Effort:** large
- **Depends on:** —

#### Goal
Provide a new Workspace python module that ingests league results and match data from source files into the platform, complementing the Java import pipeline.

#### Description
The Workspace module is named `tt-league-ingest` with the related `pyproject.toml` and `uv.lock` files at repository root level and is intended to be used as a standalone ingestion module for league results and match data.
It is a standalone module that can be installed and run independently of the platform, but it is designed to be used in conjunction with the platform's ingestion pipeline.

This `tt-league-ingest` module contains:
- Workspace submodules for each Python based federation source extraction (e.g., `tt-league-ingest-rfetm`, `tt-league-ingest-bcnesa`, `tt-league-ingest-fctt`) that handle the specific data formats and ingestion logic for each federation.
- Workspace submodules for Python based common ingestion logic and utilities (e.g., `tt-league-ingest-common`) that can be shared across federation modules. JSON schema artifact.
- Workspace submodule for Python based runtime CLI (e.g., `tt-league-ingest-cli`) that provides a command-line interface for running the ingestion process.
- Workspace submodule for Python based runtime Rest API (e.g., `tt-league-ingest-rest`) that provides a RESTful API for triggering ingestion and monitoring progress.

The folder structure of the `tt-league-ingest` module is as follows:
```
tt-league-ingest/
├── pyproject.toml
├── uv.lock
├── README.md
└── packages/
    └── tt-league-ingest-rfetm/
        ├── pyproject.toml
        └── src/ingest_rfetm/__init__.py
    └── tt-league-ingest-bcnesa/
        ├── pyproject.toml
        └── src/ingest_bcnesa/__init__.py
    └── tt-league-ingest-fctt/
        ├── pyproject.toml
        └── src/ingest_fctt/__init__.py
    └── tt-league-ingest-common/
        ├── pyproject.toml
        └── src/ingest_common/__init__.py
    └── tt-league-ingest-cli/
        ├── pyproject.toml
        └── src/ingest_cli/__init__.py
    └── tt-league-ingest-rest/
        ├── pyproject.toml
        └── src/ingest_rest/__init__.py
```

The root pyproject.toml declares the modules:

```toml
[tool.uv.workspace]
members = ["packages/*"]
```

A submodule that depends on another one, like packages/tt-league-ingest-rest/pyproject.toml:

```toml
[project]
name = "api"
version = "0.1.0"
dependencies = ["ingest_rest"]

[tool.uv.sources]
core = { workspace = true }
```

#### Acceptance Criteria
- [x] The `tt-league-ingest` module is created with the specified folder structure and submodules.
- [x] Each submodule has its own `pyproject.toml` and `__init__.py` files.
- [x] The root `pyproject.toml` correctly declares the workspace members.
- [x] The ingestion logic for each federation is implemented in the respective submodules.
- [x] The common ingestion logic and utilities are implemented in the `tt-league-ingest-common` submodule.
- [x] The CLI and REST API for triggering ingestion and monitoring progress are implemented in the respective submodules.
- [x] All submodules are correctly integrated and can be built and run using the workspace tooling.
- [x] The workspace tooling correctly resolves dependencies and allows running commands in specific submodules.
- [x] All submodules have been tested and verified to work as expected.

#### Feature Details
→ See [FEAT-00095-DETAILS.md](./FEAT-00095-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---
