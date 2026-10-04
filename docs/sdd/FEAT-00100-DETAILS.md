# Build Plan

> Draft outline.

1. Domain (`tt-data-league-core-domain`): `ImportJob` aggregate and repository port (reuse or replace the unused
   `ImportJobsPort`/`ImportJob` records after checking their callers), plus a job service that chains
   `ResourceUploadService` storage with the existing `StartImportProcessCommand` per import resource.
2. JPA adapter (`tt-data-league-core-repository-jpa`): `import_job` table; update `docs/rfetm-datamodel.md`.
3. Per-source serial executor in `tt-data-league-api-runtime`, replacing the fire-and-forget background task for this path.
4. REST (`tt-data-league-api-rest`): controller, DTOs and OpenAPI docs; `PARTIAL` maps from completed-with-warnings results.
5. Tests: domain service, JPA repository, controller, idempotency and restart recovery.

## Acceptance Criteria

- [ ] `POST /api/v1/administration/import/jobs` (multipart ZIP, optional `runId`, `allowPublishedShrink`) stores the ZIP and starts the import of every affected season, returning `202 {importJobId}`
- [ ] `GET /api/v1/administration/import/jobs/{id}` returns status `QUEUED`, `STORING`, `IMPORTING`, `SUCCEEDED`, `PARTIAL` or `FAILED` with the `ImportProcessResult` counters, lifecycle counters and round progress per season
- [ ] `GET /api/v1/administration/import/jobs?source=&from=&to=` lists job history, most recent first
- [ ] Submitting a ZIP whose manifest `contentSha256` matches a succeeded job returns that job (200) without re-importing
- [ ] At most one job per source runs at a time; a second submission for the same source is queued and runs in order
- [ ] Jobs are persisted, survive a restart (an interrupted job ends `FAILED` with a clear reason), and the datamodel document is updated
- [ ] Shrink-check (409) and invalid-ZIP (400) behaviour matches the existing upload endpoint, which stays unchanged

# Implementation Guidelines

- Keep `POST /administration/import/upload` and the preview/start endpoints as they are for manual use.
- Reuse the existing import run machinery (`ImportRunRegistry`, `ImportProcessResult`); do not duplicate import logic.
- Natural-key upsert, `id_partido` and amended-acta behaviour are unchanged; this item only adds the job envelope.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Changes to the Java backend import" items 1-3. Today an upload returns 202 without an id and only
stores files and marks the import resource pending, so an automated client cannot follow it. "Results corrected"
in the report can reuse the amended-acta detection counters (FEAT-00089) when that option is on.
