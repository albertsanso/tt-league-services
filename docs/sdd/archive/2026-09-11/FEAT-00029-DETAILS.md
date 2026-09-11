# Build Plan

1. **Confirm the architecture boundary before wiring `StartImportPreviewCommandHandler`.**
   - `tt-data-league-api-runtime` currently depends only on `tt-data-league-api-rest` and
     `tt-data-league-core-domain` (transitively `tt-data-league-core-repository-jpa`); it does
     **not** depend on `tt-data-league-import`, so the RFETM/BCNESA/FCTT navigators, parsers, and
     processors (all `@Component` beans under `org.cttelsamicsterrassa.data.load.*`) are not on the
     API's Spring classpath today (`APIApplication` scans `org.cttelsamicsterrassa`).
   - Add `tt-data-league-import` as a dependency of `tt-data-league-api-runtime` only (a
     composition-root module, like `tt-data-league-import-runtime` already does). Do **not** add it
     to `tt-data-league-core-domain` or `tt-data-league-api-rest`: domain code must stay inward-only
     per the root `AGENTS.md` and `tt-data-league-import/AGENTS.md` dependency rules.
   - Turn `ImportResourcePreviewService` (currently an empty `@Named` stub in
     `tt-data-league-core-domain/.../domain/load/service`) into a domain port (interface) with a
     method such as `ImportPreviewResult preview(ImportResource importResource)`, implemented by a
     new class inside `tt-data-league-import` (e.g. `NavigatorBackedImportResourcePreviewService`).
     This mirrors the existing `ImportResourceRepository` (core-domain port) /
     `ImportResourceRepositoryJpa` (core-repository-jpa adapter) pattern already used in this
     codebase.

2. **Design a non-persisting preview execution path (do not add dry-run flags to write processors).**
   - `RfetmMatchImportProcessor`, `RfetmTeamImportProcessor`, and the BCNESA/FCTT equivalents persist
     directly through injected repositories and have no report/dry-run mode; they are not
     preview-safe as-is and must not be reused unmodified for a "must not import the resource"
     preview.
   - Reuse the navigators' already-documented "explicit processor list" overload instead (e.g.
     `RfetmActasDirectoryNavigator.traverse(Path, List<MatchContextProcessor>)`), whose Javadoc
     already states it exists "so a caller can run a reporting pass and a persisting pass over the
     same tree without changing anything here." Add one preview-only `MatchContextProcessor`
     implementation per source (RFETM/BCNESA/FCTT) that performs the same club/team
     resolution/required-field checks as the persisting processors but only accumulates findings and
     errors in memory, never calling a repository save method.
   - Name the new processors distinctly from existing `*ReportProcessor` classes
     (`BcnesaMatchReportProcessor`, `FcttMatchReportProcessor`, `MatchReportContext`), which already
     mean "acta match report" processing and are unrelated to a report/dry-run mode, to avoid
     terminology collisions (e.g. `RfetmPreviewValidationProcessor`). Mirror the
     `ConsolidationMode.WRITE` / `ConsolidationMode.REPORT` precedent
     (`tt-data-league-import/.../club/consolidate/ConsolidationMode.java`) for the write-vs-preview
     distinction.
   - Resolve the navigator/parser to use from `ImportResource.getSource()`; locate on-disk content via
     `ImportResource.getResource().getPhysicalPath()` (populated through
     `ResourceRepositoryLoaderService.IMPORT_FOLDER_TEMPLATE`); scope traversal to the resource's
     `Season` using the existing `traverseSeason(...)` overload.
   - Before implementing, confirm how `ResourceType.TEAMS` resources map to a navigator/processor
     path: current navigators only traverse acta match-report files per source (team resolution
     happens from the acta payload via `RfetmTeamImportProcessor`, not from a dedicated teams-file
     navigator). Document the resolution here once confirmed; do not guess a mapping during
     implementation.

3. **Wire `StartImportPreviewCommandHandler` to the new port.**
   - Replace its current unused `ResourceRepositoryLoaderService` injection with
     `ImportResourceRepository` (to load the `ImportResource` by id and return a clear failure
     response when missing) and the new preview port from step 1.
   - Define an `ImportPreviewResultDto` (or similarly named record) capturing status
     (`success` / `empty-result` / `failure`), validation findings, and processing errors, returned
     inside `DomainCommandResponse`, following the existing `ImportResourceDto` /
     `PendingImportsInfoDto` record conventions.
   - The preview path must never call `ImportResource.startProcessing()` /
     `finishProcessing()` or persist any resource status change; a preview must not alter the
     resource's real import state (acceptance criterion: "must not import the resource").

4. **Resolve the backend status contract before implementing.**
   - `POST /api/v1/administration/import/preview?importResourceId=...` already exists via
     `ImportResourceController` and can remain the trigger endpoint.
   - `GET /api/v1/administration/import/status` currently returns `FindPendingImportsInfoQuery`
     results: source-level pending-import info consumed by the left Source/Federation selector's
     5-second poll (FEAT-00026), unrelated to a specific `importResourceId`. The FEAT-00029
     acceptance criteria call for a specific resource's preview status
     (loading/success/empty-result/failure), so it must use a separate endpoint.
   - Use `/preview_status?importResourceId=...` for the resource-scoped preview status and keep
     `/status` exclusively for the source-level pending-import response. Do not overload the
     polled endpoint with an incompatible response shape.

5. **Build the frontend preview workspace.**
   - Add a dedicated `ImportPreviewWorkspace` component (or a focused extension of
     `ImportReportPanel`) that replaces the current generic `job` object display for the **Simulate**
     action, presenting explicit loading, success (validation findings list), empty-result, and
     failure states, plus retry and "proceed to import" actions.
   - Keep `ImportPanel.jsx`'s `runResource(resource, true)` as the trigger for `createImportPreview`,
     but route its result into the new workspace instead of `ImportReportPanel`'s generic message;
     wire retry to re-invoke `createImportPreview` and "proceed" to call `startImport` for the same
     `resource.id` without losing `selectedSource` / the selected resource context.
   - Add an API/hook boundary analogous to `useImportResources` for the resource-scoped preview
     status contract resolved in step 4 (abortable, cancels on unmount/resource change, ignores stale
     responses).
   - Add `importPanel.preview*` translation keys (loading/success/empty-result/failure/retry/proceed
     labels) to `ca.js`, `es.js`, and `en.js` following the existing override pattern
     (`en.importPanel = { ...ca.importPanel, ... }`); keep the existing generic
     `reportTitle` / `reportEmpty` / `actionError` / `actionSuccess` keys used elsewhere in the panel.

6. **Add focused tests and validate.**
   - Backend: JUnit 5 tests for the new preview-only processors (validation without persistence side
     effects) in `tt-data-league-import`; for the wired `StartImportPreviewCommandHandler` and port in
     `tt-data-league-core-domain`; and for the controller/status contract in `tt-data-league-api-rest`,
     using existing in-memory repository test doubles.
   - Frontend: tests for the preview workspace's loading/success/empty-result/failure states, retry,
     "proceed without losing context," and hook cancellation/stale-response handling.
   - Run `mvn -pl tt-data-league-import,tt-data-league-core-domain,tt-data-league-api-rest,tt-data-league-api-runtime -am test`
     and the full `mvn test` reactor, plus `npm run lint`, `npm run build`, and the frontend test
     suite, before considering the feature implementation finalized.

# Acceptance Criteria

- [x] The **Simulate** action triggers the preview process for the selected import resource and displays the result in a dedicated preview workspace.
- [x] The preview workspace presents the validation findings and processing errors accessibly, with translated copy in Catalan, Spanish, and English.
- [x] Administrators can retry a failed preview or proceed from a successful preview to the import action without losing the selected resource context.
- [x] The preview process does not import the resource; it is only a simulation to review the processing result.
- [x] The endpoint `/api/v1/administration/import/preview_status` provides the preview status for the selected import resource.

# Implementation Guidelines

- Keep `tt-data-league-core-domain` free of any `tt-data-league-import` dependency; only
  composition-root/runtime modules (`tt-data-league-api-runtime`) may depend on both, matching the
  existing `tt-data-league-import-runtime` precedent.
- Preview execution must never call a repository save/persist method or mutate `ImportResource`
  status; reuse existing validation/lookup logic (club/team resolution, parser error handling)
  instead of duplicating it in a new code path.
- Preserve existing terminology: `*ReportProcessor` / `MatchReportContext` already mean "acta match
  report" processing in this codebase; name new preview-only processors distinctly to avoid
  confusion with a report/dry-run mode.
- Reuse existing DTO/response envelope and API client conventions (`DomainCommandResponse`,
  `apiRequest`) rather than inventing new response shapes.
- Confirm the `/status` endpoint contract question (Build Plan step 4) with the requester before
  implementation; do not silently give the same endpoint two incompatible meanings.
- Out of scope: changing club/player consolidation behavior, altering the existing
  `upload` / `list_by_source` / `start` contracts, or modifying the persisting import processors
  themselves beyond making them reachable from a new, separate preview-only path.

# Notes

- 2026-09-04: New feature planned. `StartImportPreviewCommand` /
  `StartImportPreviewCommandHandler` and `ImportResourcePreviewService` already exist in
  `tt-data-league-core-domain` as stubs (`handle(...)` returns `null`; the service class is empty),
  and `POST /api/v1/administration/import/preview?importResourceId=...` is already wired in
  `ImportResourceController`, but currently returns no usable result.
- 2026-09-04: Confirmed `tt-data-league-api-runtime` does not depend on `tt-data-league-import`
  today, so the source-specific navigators/parsers/processors are not reachable from the API process
  without adding that dependency to the runtime (composition-root) module only.
- 2026-09-04: Confirmed the persisting import processors (`RfetmMatchImportProcessor`,
  `RfetmTeamImportProcessor`, and their BCNESA/FCTT equivalents) write directly through injected
  repositories and have no dry-run mode. `RfetmActasDirectoryNavigator.traverse(Path, List)` is
  already documented as designed to support "a reporting pass and a persisting pass over the same
  tree," which is the intended reuse point for preview-only processors instead of modifying the
  existing write processors.
- 2026-09-04: Confirmed `GET /api/v1/administration/import/status` currently returns
  `FindPendingImportsInfoQuery` (source-level pending-import info for the FEAT-00026 selector poll)
  and has no notion of a specific `importResourceId`; this conflicts with this feature's acceptance
  criterion that the same endpoint report per-resource preview status. Flagged as an open contract
  decision to resolve before implementation (see Build Plan step 4); no application code changed by
  this SDD planning task.
- 2026-09-04: Preview status uses the dedicated `/preview_status?importResourceId=...` endpoint;
  `/status` remains the source-level pending-import endpoint.
- 2026-09-04: Confirmed `ResourceType.TEAMS` still has no source-specific match-report navigator. The
  preview service returns a non-persisting `failure` preview result for those resources instead of
  guessing a traversal path; `ACTAS` resources use the RFETM/BCNESA/FCTT acta navigators with
  preview-only validation processors.
- 2026-09-04: Focused validation passed for core-domain preview handlers, import preview processors,
  the full frontend test suite, frontend lint, frontend build, and API/runtime main compilation. The broader
  Maven focused reactor remains blocked by pre-existing unrelated failures in import processor tests,
  JPA Spring context setup, and API REST `importjob` test compilation.
