# Build Plan
1. **Remove dead types.** Delete `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/shared/port/ImportJobsPort.java` and
   `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/shared/model/ImportJob.java`, `ImportJobRequest.java`, `ImportJobStatus.java` (no references in any
   module or the frontend; checked 2026-10-04).
2. **Extract the run body (`tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/application/importresource/process/`).** Move the body of
   `StartImportProcessCommandHandler.runAsync` (mark running, `ImportResourceProcessService.process`,
   `finishProcessing`, registry completion, failure handling) into a new `@Named ImportResourceRunService` with
   `ImportRunSnapshot run(UUID runId, ImportResource resource)` returning the terminal snapshot. The handler keeps its
   validation and `registerQueued` logic and submits `runService.run(...)` to its executor. Existing
   `StartImportProcessCommandHandlerTest` must pass unchanged; add `ImportResourceRunServiceTest`.
3. **Split upload validation from storage (`tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/load/service/`).**
   - `ResourceUploadService`: add `ImportManifest validateUpload(String filename, byte[] content, boolean allowShrink)`
     (validate file, extract, validate manifest, shrink check, then delete the temporary extraction folder) and keep
     `uploadAndTriggerAsyncLoad` built on it.
   - `ResourceRepositoryLoaderService.loadIntoRepository` returns `List<ImportResource>`: the ACTAS import resources
     it created or set pending (from `createResourcesAndStartProcessing`), empty for TEAMS-only manifests. Existing
     callers ignore the result.
4. **Job domain (`tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/load/job/`, new package).**
   - `ImportJobStatus` (`QUEUED`, `STORING`, `IMPORTING`, `SUCCEEDED`, `PARTIAL`, `FAILED`; `isTerminal()`, `isActive()`).
   - `ImportJob` aggregate: `id`, `source`, `seasons`, `mode`, `contentSha256` (optional), `runId` (optional, the
     caller's run id), `allowPublishedShrink`, `stagedZipPath`, `requestedBy`, `status`, `errorDetail`, `createdAt`,
     `startedAt`, `finishedAt`, `List<ImportJobSeason>`; transition methods that reject illegal moves.
   - `ImportJobSeason`: `season`, `importResourceId`, `importRunId`, `status` (`ImportRunStatus`), `resultJson`.
   - `ImportJobRepository` port: `save`, `findById`, `findActiveOrSucceededBySourceAndContentSha256`,
     `findBySourceAndCreatedBetween(source, from, to, limit)`, `findByStatusIn`.
   - `ImportJobService`: `submit(filename, bytes, runId, allowShrink, requestedBy)` (validate via `validateUpload`,
     dedupe by `contentSha256`, stage bytes to `<import folder>/import-jobs/<jobId>.zip`, persist `QUEUED`, hand the id
     to `ImportJobDispatcher`) and `execute(UUID jobId)` (re-extract the staged ZIP, re-run the shrink check, `STORING`
     -> `loadIntoRepository` -> `IMPORTING` -> for each returned import resource: `ImportRunRegistry.registerQueued`
     (retry every `busyRetryInterval` while another run is active, up to `busyTimeout`) -> `ImportResourceRunService.run`
     -> record the season result; final status `SUCCEEDED` when every season is `SUCCESS`/`EMPTY_RESULT` without
     processor failures, `PARTIAL` when a season succeeded with processor failures or execution issues, otherwise
     `FAILED`; always delete the staged ZIP at the end).
   - `ImportJobDispatcher` port (`dispatch(UUID jobId)`), implemented in the runtime.
5. **Application layer (`tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/application/importjob/`, new).** `SubmitImportJobCommand` + handler,
   `FindImportJobQuery` + handler, `FindImportJobHistoryQuery` + handler, DTOs `ImportJobDto` and
   `ImportJobSeasonDto` (season results reuse `ImportProcessResultDtoMapper`; `resultJson` is the serialized
   `ImportProcessResultDto`).
6. **JPA adapter (`tt-data-league-core-repository-jpa/src/main/java/org/cttelsamicsterrassa/data/core/repository/jpa/load/`).** `model/ImportJobJPA` (table `import_job`) and `model/ImportJobSeasonJPA` (table
   `import_job_season`, `@ManyToOne` to job, owned by a `@OneToMany(cascade = ALL, orphanRemoval = true)`),
   `impl/ImportJobRepositoryHelper` (Spring Data), `impl/ImportJobRepositoryJpa` implementing the port, and mappers in
   `mapper/` following the `ImportResource` pattern. Index `idx_import_job_source_sha` on `(source, content_sha256)`
   and `idx_import_job_created` on `created_at`. `resultJson` is a `TEXT` column.
7. **Runtime (`tt-data-league-api-runtime/src/main/java/org/cttelsamicsterrassa/data/api/runtime/config/`).** `ImportJobProperties` (`tt.league.import.jobs.busy-retry-interval`, default
   `PT10S`; `busy-timeout`, default `PT2H`; documented in `application.yml` with `IMPORT_JOBS_*` variables);
   `ImportJobConfiguration` with a single-thread `ThreadPoolTaskExecutor` bean `importJobExecutor`, the
   `ImportJobDispatcher` adapter submitting `ImportJobService.execute`, and an `ApplicationReadyEvent` listener that
   fails `STORING`/`IMPORTING` jobs ("interrupted by a restart") and re-dispatches `QUEUED` jobs in creation order.
8. **REST (`tt-data-league-api-rest/src/main/java/org/cttelsamicsterrassa/data/api/rest/importjob/ImportJobController.java`, new).** `@RequestMapping(API_BASE_PATH_V1 +
   "/administration/import/jobs")`, `@PreAuthorize("hasRole('ADMIN')")` (FEAT-00101 widens it to `imports:write`).
   `POST` (multipart) -> 202 new job / 200 existing job / 400 / 409; `GET /{id}` -> 200 / 404; `GET` with `source`,
   `from`, `to` (ISO-8601 dates on `createdAt`), `limit` (default 50, max 200). `requestedBy` is the authenticated
   principal name. OpenAPI annotations as in `ImportResourceController`.
9. **Tests.** Domain: job transitions, dedupe, `PARTIAL` mapping, busy wait timeout (fake clock/sleeper), staged ZIP
   cleanup. JPA: repository round trip and queries in `tt-data-league-core-repository-jpa` tests. Runtime: restart
   recovery listener. REST: `ImportJobControllerTest` for 202/200/400/409/404 and listing.
10. **Docs.** `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md`: `import_job` and `import_job_season`
    sections (columns, indexes, cascade, no FK to `import_resource` because season rows are snapshots of what ran);
    `tt-data-league-api-runtime/README.md`: the jobs API and configuration.
11. **Validation.** `mvn -pl tt-data-league-api-runtime -am test`, then the full `mvn test`.

## Acceptance Criteria

- [ ] `POST /api/v1/administration/import/jobs` (multipart `file`, optional `runId`, `allowPublishedShrink`) validates the ZIP synchronously (400 invalid, 409 published-acta shrink) and returns `202 {importJobId, status}`
- [ ] A job stores the ZIP content and then imports every ACTAS season of its manifest, moving through `QUEUED`, `STORING`, `IMPORTING` and ending `SUCCEEDED`, `PARTIAL` or `FAILED`
- [ ] `GET /api/v1/administration/import/jobs/{id}` returns the job with, per season, the import run id, status and `ImportProcessResult` (counters, lifecycle counters, round progress); `GET /api/v1/administration/import/jobs?source=&from=&to=&limit=` lists jobs, most recent first
- [ ] When the manifest has `contentSha256`, submitting the same source and hash as a `SUCCEEDED`/`PARTIAL` or active job returns that job with 200 and no new import; without `contentSha256` there is no deduplication
- [ ] Jobs run one at a time system-wide; a job waits (bounded, configurable) while a manually started import is active and fails with a clear reason after the timeout
- [ ] Jobs are persisted in `import_job` and `import_job_season`; after a restart `QUEUED` jobs resume and `STORING`/`IMPORTING` jobs end `FAILED` with an interruption reason; `rfetm-datamodel.md` documents both tables
- [ ] The existing upload, preview and start endpoints behave as before, and the unused `ImportJobsPort`/`shared.model.ImportJob*` types are removed

# Implementation Guidelines

- Keep `POST /administration/import/upload` and the preview/start endpoints unchanged for manual use.
- Reuse the import run machinery (`ImportRunRegistry`, `ImportResourceProcessService`, `ImportProcessResult`);
  the job only chains storage and runs. Do not duplicate import logic.
- Natural-key upsert, `id_partido` and amended-acta behaviour are unchanged.
- Domain code stays free of Spring: the executor and startup listener live in `tt-data-league-api-runtime`.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Changes to the Java backend import" items 1-3. Today an upload returns 202 without an id and only
stores files and marks the import resource pending, so an automated client cannot follow it. "Results corrected"
in the report can reuse the amended-acta detection counters (FEAT-00089) when that option is on.

## Planning notes (2026-10-04)

- Today `uploadAndTriggerAsyncLoad` stores files in a fire-and-forget task and only marks the import resource
  `PENDING`. The import itself needs `POST /start` per resource, and its status lives in
  `InMemoryImportRunRegistry`, lost on restart. The job envelope closes both gaps without changing those paths.
- Imports are single-run **system-wide** (`ImportRunRegistry.registerQueued` rejects while any run is active), so the
  "one job per source" idea from the backlog draft became "one job at a time system-wide" plus a bounded wait for
  manual imports. Acceptance criteria were updated accordingly.
- The job stages the uploaded bytes and re-extracts at execution because the extraction folder is a temporary
  directory that does not survive a restart.
- Deduplication relies on the platform-verified `contentSha256` from FEAT-00099; manifests without it are never
  deduplicated (explicit, no fallback hash).
- An `ImportResource` left `PROCESSING` by a crash is an existing issue of the manual path too; it is not addressed here.
- Removing `ImportJobsPort` and the `shared.model.ImportJob*` records is safe: they have no references anywhere and
  their names would clash with the new job model.
