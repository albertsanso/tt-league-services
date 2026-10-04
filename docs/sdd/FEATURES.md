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

### [FEAT-00103] Orchestrator pipeline run model and persistence
- **Status:** done
- **Priority:** high
- **Effort:** large
- **Depends on:** FEAT-00096

#### Goal
Record every orchestrated run, its steps, artifacts and import report durably, with at most one active run per source enforced by the database.

#### Acceptance Criteria
- [x] `tt-league-pipeline-orchestrator-core` models `PipelineRun` and `PipelineStep` with states `QUEUED`, `RUNNING_INGEST`, `NO_CHANGES`, `PACKED`, `IMPORTING`, `SUCCEEDED`, `PARTIAL`, `FAILED`, triggers `SCHEDULED`/`MANUAL`/`RETRY` and `requestedBy`, and rejects illegal transitions
- [x] Flyway migrations in `tt-league-pipeline-orchestrator-runtime` create schema `pipeline` with `pipeline_run`, `pipeline_step`, `run_artifact` and `import_report`
- [x] A partial unique index guarantees at most one `QUEUED`/`RUNNING_*`/`PACKED`/`IMPORTING` run per source
- [x] JPA adapters implement the core repository ports; Testcontainers PostgreSQL tests cover the migrations, the uniqueness rule and state persistence
- [x] The module documents its tables in a datamodel document next to the module README

#### Feature Details
→ See [FEAT-00103-DETAILS.md](./FEAT-00103-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00102] Round progress and open match-day query endpoint
- **Status:** done
- **Priority:** high
- **Effort:** medium
- **Depends on:** —

#### Goal
Expose per source and season which match days are open, with their date window and match status counts, so the orchestrator can decide what to refresh from the domain model.

#### Acceptance Criteria
- [x] `GET /api/v1/match/round-progress?source=&season=` returns, per competition/group/phase and jornada, the counts of scheduled, played, derived postponed and overdue matches, and the first and last scheduled dates
- [x] Counts reuse `MatchRepository.findRoundProgress` and the calendar's derived postponed/overdue rules; no state is stored
- [x] Optional `competition` and `onlyOpen=true` filters narrow the result; `onlyOpen` keeps jornadas with any non-played match or a window overlapping today plus the grace period
- [x] The endpoint needs `matches:read` and is available to the service credential
- [x] Domain, JPA and controller tests cover the FCTT 2026-2027 shape and an overdue match

#### Feature Details
→ See [FEAT-00102-DETAILS.md](./FEAT-00102-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00101] Service credentials for platform-to-platform API calls
- **Status:** done
- **Priority:** high
- **Effort:** medium
- **Depends on:** FEAT-00100

#### Goal
Let the orchestrator and the ingest service call the platform import and read APIs with a dedicated, scoped service credential instead of a user's JWT.

#### Acceptance Criteria
- [x] Service credentials are configured from the environment as `security.service-credentials` entries (`name`, `key-sha256`, `permissions`); a presented `X-API-Key` is hashed and compared in constant time
- [x] A new `imports:write` permission is granted to the `ADMIN` role and to service credentials that list it; the import jobs endpoints accept `imports:write` and the match read endpoints keep `matches:read`
- [x] A valid service credential authenticates as `service:<name>` with only its configured permissions; an invalid key is a 401, and a request with both `Authorization` and `X-API-Key` is a 400
- [x] Startup fails clearly on a malformed entry (blank name, hash that is not 64 hex characters, unknown permission); no credential is configured by default
- [x] User JWT authentication and existing role checks are unchanged
- [x] Security tests cover allowed, forbidden, invalid-key and ambiguous-header cases; `tt-data-league-api-runtime/README.md` documents the configuration and how to generate a key and its hash

#### Feature Details
→ See [FEAT-00101-DETAILS.md](./FEAT-00101-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00100] Machine-friendly asynchronous import jobs API
- **Status:** done
- **Priority:** high
- **Effort:** large
- **Depends on:** FEAT-00099

#### Goal
Let an automated client submit an upload ZIP and follow the resulting import to completion through one job id, with idempotency and a structured change report.

#### Acceptance Criteria
- [x] `POST /api/v1/administration/import/jobs` (multipart `file`, optional `runId`, `allowPublishedShrink`) validates the ZIP synchronously (400 invalid, 409 published-acta shrink) and returns `202 {importJobId, status}`
- [x] A job stores the ZIP content and then imports every ACTAS season of its manifest, moving through `QUEUED`, `STORING`, `IMPORTING` and ending `SUCCEEDED`, `PARTIAL` or `FAILED`
- [x] `GET /api/v1/administration/import/jobs/{id}` returns the job with, per season, the import run id, status and `ImportProcessResult` (counters, lifecycle counters, round progress); `GET /api/v1/administration/import/jobs?source=&from=&to=&limit=` lists jobs, most recent first
- [x] When the manifest has `contentSha256`, submitting the same source and hash as a `SUCCEEDED`/`PARTIAL` or active job returns that job with 200 and no new import; without `contentSha256` there is no deduplication
- [x] Jobs run one at a time system-wide; a job waits (bounded, configurable) while a manually started import is active and fails with a clear reason after the timeout
- [x] Jobs are persisted in `import_job` and `import_job_season`; after a restart `QUEUED` jobs resume and `STORING`/`IMPORTING` jobs end `FAILED` with an interruption reason, returning the import resource they left `PROCESSING` to `ERROR`; `rfetm-datamodel.md` documents both tables
- [x] The existing upload, preview and start endpoints behave as before, and the unused `ImportJobsPort`/`shared.model.ImportJob*` types are removed

#### Feature Details
→ See [FEAT-00100-DETAILS.md](./FEAT-00100-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00099] Upload ZIP provenance manifest and package retrieval
- **Status:** done
- **Priority:** high
- **Effort:** medium
- **Depends on:** —

#### Goal
Make every upload ZIP self-describing (run id, generator, content hash, match counts) and retrievable from the ingest service, so imports can be deduplicated, traced and replayed.

#### Acceptance Criteria
- [x] `manifest.json` gains optional `runId`, `generator`, `generatorVersion`, `contentSha256` and `matchCounts` (`expected`, `withResult`, `pending`) fields
- [x] `contentSha256` follows one documented algorithm over the sorted ZIP entries other than `manifest.json`, so identical content always gives the same hash
- [x] `ResourceZipService` accepts the new optional fields, validates their format, recomputes `contentSha256` from the ZIP content and rejects a mismatch with 400, and still accepts manifests without them
- [x] The Java change ships before or with the Python change, because the platform rejects unknown manifest keys today
- [x] `GET /api/v1/ingest/runs/{runId}/package` streams the run's ZIP with its SHA-256 in an `X-Content-SHA256` header (404 when the run is unknown or produced no ZIP, 409 while the run is active)
- [x] Each REST run packages to its own ZIP, so a later run never replaces an earlier run's package; packages of runs outside the most recent `HISTORY_LIMIT` are deleted and answer 404
- [x] The manifest section of `tt-data-league-api-runtime/README.md` and `tt-league-ingest/README.md` describe the new fields and the hash algorithm
- [x] Python and Java tests cover hash stability, optional-field parsing, mismatch rejection and rejection of malformed values

#### Feature Details
→ See [FEAT-00099-DETAILS.md](./FEAT-00099-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00098] Ingest multi-scope runs and match-day status endpoint
- **Status:** done
- **Priority:** high
- **Effort:** medium
- **Depends on:** —

#### Goal
Let the orchestrator refresh only the open groups of several competitions in one ingest run and read each source's match-day status without scraping.

#### Acceptance Criteria
- [x] `POST /api/v1/ingest/runs` accepts `scopes: [{category, group, phase, territory, gender, matchDays}]`, and a run downloads, parses and packages only the union of the scopes
- [x] The existing single `filters` body stays valid; sending both `filters` and `scopes` is a 400
- [x] Unsupported scope fields for a source fail the run before any network call, as filters do today
- [x] `GET /api/v1/ingest/sources/{source}/match-days-status?season=` returns the current `match-days-status.json` (404 when none exists)
- [x] The CLI `run` accepts a `--scope-file` JSON with the same shape
- [x] Tests cover scope union, validation and the status endpoint; README documents both

#### Feature Details
→ See [FEAT-00098-DETAILS.md](./FEAT-00098-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00097] Ingest run outcome classification for unattended runs
- **Status:** done
- **Priority:** high
- **Effort:** medium
- **Depends on:** —

#### Goal
Let an unattended caller tell a no-change run, a transient source outage and a parse failure apart from the ingest run report instead of a single FAILED status.

#### Acceptance Criteria
- [x] The run report has an `outcome` of `SUCCEEDED`, `NO_CHANGES`, `COMPLETED_WITH_ISSUES`, `SOURCE_UNAVAILABLE` or `FAILED`, plus a `retryable` flag; the existing `status` stays unchanged for compatibility
- [x] The pipeline fingerprints the season's `actas-json` (SHA-256 per file) and `equipos-json` before the first stage and after `PARSE`/`TEAMS`, and reports `actasChanged` and `contentChanged` counts
- [x] When the run includes `PARSE`, no JSON changed and `force` is not set, `PACKAGE` and `UPLOAD` are recorded as skipped and the outcome is `NO_CHANGES` instead of a delta packaging failure
- [x] A `DOWNLOAD` stage whose legacy script reported failures (exit code 1) while no content file was written gives `SOURCE_UNAVAILABLE` with `retryable=true`
- [x] Parse issues, invalid actas and stage failures give `COMPLETED_WITH_ISSUES` or `FAILED` with `retryable=false`
- [x] The CLI keeps exit codes 0/1/2 and adds 3 for `NO_CHANGES` and 4 for `SOURCE_UNAVAILABLE`; `--json` and the REST run DTO expose `outcome`, `retryable` and `changes`
- [x] Tests cover each outcome with fake ingestors and fixtures (no network); the README documents outcomes, skip rules and exit codes

#### Feature Details
→ See [FEAT-00097-DETAILS.md](./FEAT-00097-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00096] Pipeline orchestrator architecture baseline and module skeleton
- **Status:** done
- **Priority:** high
- **Effort:** large
- **Depends on:** —

#### Goal
Establish the pipeline orchestrator's module layout, technology decisions and integration contracts so the other orchestrator features build on one agreed baseline.

#### Acceptance Criteria
- [x] Maven modules `tt-league-pipeline-orchestrator-core`, `tt-league-pipeline-orchestrator-runtime` and `tt-league-pipeline-orchestrator-frontend` are added to the root reactor and `mvn test` builds and tests them
- [x] `tt-league-pipeline-orchestrator-core` has no Spring, JPA or HTTP-client dependency and no dependency on `tt-data-league-*` modules, and a test enforces this
- [x] `tt-league-pipeline-orchestrator-runtime` is a Spring Boot 3.5 (Java 21) application exposing `/actuator/health` that fails at startup when a required `tt.pipeline.*` property is missing or invalid
- [x] `tt-league-pipeline-orchestrator-frontend` is a React + TypeScript + Material UI application built with Vite, type-checked, linted and tested (Vitest + React Testing Library) through `frontend-maven-plugin`
- [x] Each new module has an `AGENTS.md` and README, and the root `AGENTS.md` module list, dependency rules and build commands include them
- [x] The architecture decisions and the proposal gap analysis are recorded in this feature's details and reflected in the dependent backlog items

#### Feature Details
→ See [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

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
