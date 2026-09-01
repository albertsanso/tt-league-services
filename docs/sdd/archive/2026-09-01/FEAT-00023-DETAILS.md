# Build Plan

1. **Confirm contracts and boundaries.** Keep traversal, parsing, consolidation,
   validation, and change-set generation in `tt-data-league-import`. Add an
   injectable application service (for example `ImportJobService`) with request,
   result, validation, preview, progress, and rollback DTOs. Reuse
   `rfetm/*ActasDirectoryNavigator.java`, `bcnesa/*ActasDirectoryNavigator.java`,
   `fctt/*ActasDirectoryNavigator.java`, their processors/consolidation services,
   and `TraversalSummary`/`BcnesaTraversalSummary`; REST and React must not
   implement filesystem traversal or parser rules.
2. **Define persistence.** Add import job, source/configuration, row/result error,
   progress, audit event, and reversible change-set models in the import/domain
   layer; add repositories and JPA adapters/migrations in
   `tt-data-league-core-repository-jpa`. Persist immutable source metadata,
   findings, status (`PREVIEW`, `VALIDATING`, `READY`, `RUNNING`, `SUCCEEDED`,
   `FAILED`, `CANCELLED`, `ROLLED_BACK`), timestamps, actor, and correlation/job
   IDs. Keep secrets out of persistence and make rollback idempotent.
3. **Unify CLI and API orchestration.** Refactor
   `../../../../tt-data-league-import-runtime/src/main/java/org/cttelsamicsterrassa/data/load/runtime/App.java`
   behind the application service while preserving `ImportRuntimeArguments`,
   `ImportRuntimeCliContract`, and the README operational contract. CLI and API
   must share preview, validate, run, cancel, and rollback commands and expose
   the common summary/progress model.
4. **Expose secured REST endpoints.** In `tt-data-league-api-rest`, add
   controllers, DTOs, mappers, OpenAPI, and error handling for supported
   sources/schema, preview, validation, create/start, job detail/progress,
   cancel, rollback, paginated history, and search/filter. Wire through
   `../../../../tt-data-league-api-runtime/src/main/java/org/cttelsamicsterrassa/data/api/runtime/APIApplication.java`
   and existing api-rest/domain/JPA dependencies. Enforce administrator
   authorization on every endpoint and mutation, consistent 400/403/404/409/422/
   500 responses, request-size/time limits, and tenant/ownership scoping where
   applicable.
5. **Implement safe source and mapping handling.** Use allow-listed source
   adapters (initially the existing federation directory navigators), versioned
   mapping/transform definitions, schema validation, duplicate/referential
   checks, warnings versus blocking errors, bounded previews, and deterministic
   dry-runs. Resolve configured source IDs only: reject arbitrary paths, path
   traversal, symlinks, unrestricted filesystem/network access, and unsafe
   filenames.
6. **Build the administration UI.** Replace the placeholder in
   `../../../../tt-data-league-frontend/src/pages/AdministrationPage.jsx` (or a routed child)
   for protected `/administration/import`. Add API functions in a module adjacent
   to `src/api/settings.js`, hooks under `src/hooks`, and admin components for
   source selection, mapping, preview, validation findings, confirmation,
   progress/run/cancel, completion, rollback, and searchable/filterable history.
   Follow `auth/apiRequest`, existing admin layouts, route guards, accessible
   responsive controls, loading/empty/error/forbidden/conflict states, and
   polling cleanup.
7. **Add localization and notifications.** Add every label, status, validation
   message, confirmation, progress/history string, and alert to the Catalan,
   Spanish, and English catalogs. Follow existing notification and navigation
   patterns; never expose stack traces, paths, credentials, or parser internals.
8. **Test every boundary.** Add unit tests for mapping, validation, source
   allow-listing, progress aggregation, idempotent cancellation/rollback, and
   navigator/processor adapters; repository/JPA tests for persistence and
   retention; REST contract, pagination, conflict, error, and authorization tests;
   runtime/CLI compatibility tests; and frontend component, hook, route-guard,
   localization, and API-mock tests for preview-to-run and failure/retry flows.
9. **Document and validate operations.** Update the import runtime README and API
   documentation with configuration, source onboarding, permissions, limits,
   lifecycle, recovery, observability, and examples. Run `mvn test` plus
   module-focused import/API/JPA tests and the frontend's existing `npm test`/
   `npm run build` scripts. Verify migrations, an end-to-end admin import, unsafe-input
   rejection, progress/history, and rollback in a representative environment.

# Implementation Guidelines

- Preview and validation are side-effect free. Require an authorized,
  versioned confirmation token to run and reject stale mappings/source metadata.
- Use durable asynchronous jobs, bounded concurrency, idempotency keys/locks,
  cancellation semantics, and observable progress.
- Apply least privilege, redact secrets, validate all identifiers and filenames,
  and use configured directories only. Preserve existing API, CLI, auth,
  translation, and persistence conventions.

# Notes

## Acceptance criteria (synchronized with `../../FEATURES.md`)

- [ ] Administrators can import data from supported sources into the system.
- [ ] Administrators can map and transform source data during the import process.
- [ ] The panel validates imported data and provides clear error and warning states.
- [ ] Administrators can preview imported data before finalizing an import.
- [ ] Administrators can monitor import progress and completion status.
- [x] Administrators can view a history or log of previous imports.
- [ ] Administrators can schedule or automate recurring data imports.
- [ ] Administrators can roll back an import when necessary.
- [x] Administrators can search and filter import tasks or data sources.
- [x] The panel provides a clear and organized layout for import options and settings.
- [ ] The panel provides notifications or alerts for import errors, warnings, and completion.
- [x] The panel is accessible only to users with administrator privileges.

## Unresolved design decisions

- **Input source policy:** configured directories/files versus uploads or remote
  object storage; source registration and credential ownership.
- **Mapping scope:** fixed per-source mappings versus editable, versioned,
  reusable mappings and the supported transformation language.
- **Scheduling:** whether recurring imports are in scope, and scheduler,
  timezone, retry, and authorization ownership.
- **Rollback:** change-set granularity, downstream references, concurrent edits,
  and maximum rollback window.
- **Async/concurrency/retention:** queue/executor, cancellation guarantees,
  parallel-job and preview limits, polling versus events, and audit/history purge.



## Delivered in this slice

A first vertical slice was implemented on 2026-08-30 across domain, REST API, and frontend:

- **tt-data-league-core-domain**: Added ImportSourcesPort interface
  (org.cttelsamicsterrassa.data.core.domain.shared.port) allowing the REST layer to query
  supported import sources without depending on the import module.
- **tt-data-league-api-rest**: Added ImportJobController
  (GET /api/v1/administration/import/sources) protected with hasRole('ADMIN'),
  ImportSourceDto, ImportJobOpenAPIv1Controller, and ImportJobConfig (default bean
  returning all three federation sources: RFETM, BCNESA, FCTT).
- **tt-data-league-frontend**: Replaced the placeholder in AdministrationPage.jsx for
  the /administration/import route with ImportPanel.jsx. Added src/api/importJobs.js
  and src/hooks/useImportSources.js. Added importPanel translations to all three
  catalogs (Catalan, Spanish, English).
- **Tests**: ImportJobControllerTest with 6 focused unit tests.

## Decisions and implementation boundaries

The implementation uses configured, allow-listed federation source identifiers
(`tt.league.import.sources`); clients cannot submit filesystem paths or remote
URLs. Mapping is fixed and versioned (`mappingVersion=1`) until a reviewed
transformation language is introduced. Jobs use a thread-safe in-memory store
with explicit lifecycle transitions, bounded history queries, idempotent cancel
and rollback, and are deliberately replaceable by a persistence adapter through
`ImportJobsPort`. The API is administrator-only and the UI uses bounded polling
free status refreshes to avoid unbounded requests. Recurring scheduling is not
implemented in this feature and remains the sole open acceptance criterion.