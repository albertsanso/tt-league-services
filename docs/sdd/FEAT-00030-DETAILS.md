# Build Plan

1. **Confirm the reused architecture baseline (no new module dependency needed).**
   - `tt-data-league-api-runtime/pom.xml` already depends on `tt-data-league-import` (added for
     FEAT-00029), so the RFETM/BCNESA/FCTT navigators, parsers, and persisting processors are already
     on the API's Spring classpath; no `pom.xml` change is required for this feature.
   - `POST /api/v1/administration/import/start` already exists in `ImportResourceController` and
     already pushes `StartImportProcessCommand(importResourceId)` to the command bus. Keep this as
     the only trigger/status endpoint: the registry's own acceptance criterion scopes the contract to
     `/start` alone (unlike FEAT-00029, do **not** add a second `/process_status`-style GET endpoint
     for this feature).
   - `StartImportProcessCommand` and `StartImportProcessCommandHandler` already exist in
     `tt-data-league-core-domain/.../application/importresource/process` as stubs (`handle(...)`
     returns `null`); they must be replaced with a real implementation, not created from scratch.
   - `ImportResource.startProcessing()` / `finishProcessing(boolean)` / `setPending()` already exist on
     the domain entity and already encode the exact state machine this feature needs
     (`PENDING -> PROCESSING -> PROCESSED|ERROR`, with `setPending()` allowed only from a finished
     status). Reuse them as-is; do not add new transition methods.

2. **Add a domain port and result model for the real (persisting) import, mirroring the preview port.**
   - Add `ImportResourceProcessService` (interface) to
     `tt-data-league-core-domain/.../domain/load/service`, with
     `ImportProcessResult process(ImportResource importResource)`, mirroring
     `ImportResourcePreviewService.preview(...)`.
   - Add `ImportProcessStatus` (`LOADING`, `SUCCESS`, `EMPTY_RESULT`, `FAILURE` — same four values the
     acceptance criteria require) and `ImportProcessResult` (status, findings, processing errors,
     `filesSeen`, `itemsPersisted`, `skipped`, `processorFailures`, with `success`/`empty`/`failure`
     factories) to `tt-data-league-core-domain/.../domain/load/model`, mirroring
     `ImportPreviewStatus`/`ImportPreviewResult` exactly in shape.
   - Reuse the existing `ImportPreviewFinding` (`severity`, `message`, `location`) and
     `ImportPreviewProcessingError` (`message`, `location`) records for `ImportProcessResult`'s findings
     and errors instead of duplicating identical record shapes; these two records are already
     source/feature-agnostic. Do not rename them as part of this feature — they belong to the `done`
     FEAT-00029 and a rename is a separate, explicit decision.
   - Add a default `UnavailableImportResourceProcessService` (`@Named`) in core-domain mirroring
     `UnavailableImportResourcePreviewService`, returning a `FAILURE` result with message
     `"Import processing is not available in this runtime."`, so any runtime without
     `tt-data-league-import` on the classpath still gets a safe, explicit failure instead of a missing
     bean error.

3. **Add a persisting `ImportResourceProcessService` implementation in `tt-data-league-import` that
   reuses the existing write processors unmodified.**
   - Add `NavigatorBackedImportResourceProcessService` (`@Component @Primary`) to
     `tt-data-league-import/.../shared/process`, mirroring
     `NavigatorBackedImportResourcePreviewService`'s structure (ACTAS-only support check; same
     "unsupported type" failure message pattern for `ResourceType.TEAMS`; resolve
     `importResource.getResource().getPhysicalPath()` and `importResource.getSeason()` the same way).
   - Inject the three *existing* ordered lists of persisting processors directly
     (`List<MatchContextProcessor>` for RFETM, `List<BcnesaMatchReportProcessor>` for BCNESA,
     `List<FcttMatchReportProcessor>` for FCTT) — Spring supplies exactly the `@Component`-annotated
     write processors already ordered by `@Order` (Team=10, Player=20, Match=30 for every source;
     confirmed identical across `Rfetm*/Bcnesa*/Fctt*ImportProcessor`), because the `*PreviewValidationProcessor`
     classes are plain objects, not Spring beans, and are never in these lists.
   - Wrap each injected processor in a new, source-specific "recording decorator"
     (`RfetmProcessRecordingProcessor implements MatchContextProcessor`,
     `BcnesaProcessRecordingProcessor implements BcnesaMatchReportProcessor`,
     `FcttProcessRecordingProcessor implements FcttMatchReportProcessor`) that delegates to the wrapped
     processor and, on `RuntimeException`, records `(message, location-from-context)` into a new
     `ImportProcessCollector` (mirrors `ImportPreviewCollector`, but only needs an `error(...)` method
     plus a `toResult(...)` deriving status the same way: `FAILURE` if any error/`processorFailures`,
     else `EMPTY_RESULT` if nothing was persisted, else `SUCCESS`) **and then rethrows the same
     exception** so the navigator's own per-processor isolation and `processorFailures` counting keep
     working unchanged.
   - Call the navigator's **explicit-processor-list** overload
     (`traverseSeason(baseFolder, season, wrappedProcessors)`) with the wrapped, order-preserved list —
     the same overload preview already uses — so this feature never touches
     `RfetmMatchImportProcessor`, `RfetmTeamImportProcessor`, `RfetmPlayerImportProcessor`, or their
     BCNESA/FCTT equivalents.
   - `itemsPersisted` = the navigator's `dispatched`/`fixturesDispatched` count (files/fixtures whose
     context reached at least one processor); this counts dispatch, not confirmed persistence per
     processor, matching the same limitation already accepted by the preview's `itemsDispatched`.

4. **Rewrite `StartImportProcessCommandHandler` to run the real transition, mirroring
   `StartImportPreviewCommandHandler`.**
   - Inject `ImportResourceRepository` and `ImportResourceProcessService` (replacing the handler's
     current empty body).
   - `findById` miss -> `DomainCommandResponse.failResponse(missingResource(importResourceId))` (new
     `ImportProcessResultDtoMapper.missingResource(...)`, mirroring
     `ImportPreviewResultDtoMapper.missingResource(...)`).
   - If found and already `PROCESSING`, return a `failResponse` with a clear
     "already processing" message instead of letting `startProcessing()`'s `IllegalStateException`
     surface as an unhandled error — guards against a double-submitted `/start` call (e.g. a
     double-click), which the current preview handler does not need to guard against since preview
     never mutates state.
   - If found and in a finished status (`PROCESSED`/`ERROR`), call `importResource.setPending()` first
     — this is what makes "retry a failed import" (and "re-import a processed one") possible, reusing
     the entity's own allowed-transition rule instead of adding a new one.
   - `importResource.startProcessing()` -> `ImportProcessResult result =
     importResourceProcessService.process(importResource)` -> `boolean isValid = result.status() ==
     ImportProcessStatus.SUCCESS` -> `importResource.finishProcessing(isValid)` (so `EMPTY_RESULT` and
     `FAILURE` both map to `ERROR`) -> `importResourceRepository.save(importResource)` (single save
     call, mirroring the "no intermediate persisted state" simplicity of a synchronous handler) -> map
     to `ImportProcessResultDto` via a new `ImportProcessResultDtoMapper.toDto(...)` and return
     `DomainCommandResponse.successResponse(dto)` (the DTO's own `status` field carries the
     domain-level success/empty-result/failure signal, exactly like the preview command always returns
     a successful `DomainCommandResponse` for a resource that was found).
   - Add `ImportProcessResultDto` (`importResourceId`, `source`, `season`, `resourceType`, `status`,
     `findings`, `processingErrors`, `filesSeen`, `itemsPersisted`, `skipped`, `processorFailures`) to
     `tt-data-league-core-domain/.../application/importresource/process/dto`, mirroring
     `ImportPreviewResultDto`'s package layout and field naming.

5. **Frontend: add a dedicated import workspace and route both "Import" entry points into it.**
   - Add `ImportProcessWorkspace.jsx` next to `ImportPreviewWorkspace.jsx`, presenting explicit
     loading/success/empty-result/failure states, the same `filesSeen`/`itemsPersisted`/`skipped`
     summary shape, findings/processing-error lists, and a retry action — adapted copy/labels for
     "import" rather than "preview" (do not refactor or rename the `done` `ImportPreviewWorkspace`
     itself as part of this change).
   - Add `normalizeImportProcess(payload)` in a new `src/hooks/useImportProcessResult.js`, mirroring
     `normalizeImportPreview` in `useImportPreviewStatus.js`; no polling hook is needed because
     `/start` stays a single synchronous call (see step 1) — this file exists mainly so the normalizer
     is reusable/testable and to leave room for a future polling hook if long-running imports are ever
     made asynchronous.
   - In `ImportPanel.jsx`, add an `importState` (`{resource, loading, result, error}`, mirroring
     `previewState`) and a `startProcess(resource)` handler (mirrors `startPreview`): set
     `importState` to loading, call `startImport(...)` (already implemented in `src/api/importJobs.js`,
     unchanged), normalize the response, and store the result/error.
   - Route **both** existing "Import" entry points through `startProcess`:
     - `runResource(resource)` / `run(season)`'s non-simulate branches (the resource card's **Import**
       button and the legacy `SeasonImportList` path) — replacing their current raw `startImport(...)`
       call that stuffs the result into the generic `job`/`ImportReportPanel` state.
     - `proceedFromPreview(resource)` (the "proceed to import" button inside
       `ImportPreviewWorkspace`) — replacing its current ad hoc `previewState.importing` /
       `importError` / `importResult` fields (and the generic `actionSuccess`/`actionError` message
       they render inline) so a proceed-triggered import also lands in the same dedicated
       `ImportProcessWorkspace`, satisfying the acceptance criterion for *both* trigger paths.
   - Decide panel precedence explicitly: show `ImportProcessWorkspace` whenever `importState.resource`
     is set (an import is the most recent/relevant action), otherwise `ImportPreviewWorkspace` when
     `previewState.resource` is set, otherwise the existing `ImportReportPanel` fallback. Clear
     `importState` when a new preview starts (`startPreview`) so a stale import result is never shown
     next to a fresh simulation.
   - "Proceed" after a **successful** import has no defined next domain action today (import is the
     last step of Upload -> Simulate -> Import for a resource). Absent further product input, implement
     it as "back to resources" (clears `importState`/`selectedSeason`, keeps `selectedSource`) rather
     than inventing an undefined downstream step — flagged in Notes for confirmation.

6. **Translations.**
   - Add `importPanel.process*` keys to `ca.js` (source of truth): `processTitle`, `processEmpty`,
     `processLoading`, `processSuccess`, `processEmptyResult`, `processFailure`, `processRetry`,
     `processBackToResources`, `processFindings`, `processErrors`, `processNoFindings`,
     `processNoErrors`, `processFilesSeen`, `processItemsPersisted`, `processSkipped`, and a
     `processStatus.{loading,success,empty-result,failure}` map — mirroring the `preview*` /
     `previewStatus` keys already at `ca.js:~520-548`.
   - Override the same keys in `es.js` and `en.js` using the existing
     `en.importPanel = { ...ca.importPanel, ... }` pattern (`en.js:101-102`).

7. **Tests.**
   - `tt-data-league-import`: unit tests for each recording decorator (forwards to delegate; records
     and rethrows on `RuntimeException`; navigator's `processorFailures` still increments) and for
     `NavigatorBackedImportResourceProcessService` (ACTAS-only support, per-source dispatch, status
     derivation, `TEAMS` unsupported-type failure).
   - `tt-data-league-core-domain`: `StartImportProcessCommandHandlerTest` mirroring
     `StartImportPreviewCommandHandlerTest` — missing resource, successful run persists exactly once,
     retry from `PROCESSED`/`ERROR` via `setPending()`, rejecting a call while already `PROCESSING`,
     and `isValid` mapping (`SUCCESS` -> `PROCESSED`, `EMPTY_RESULT`/`FAILURE` -> `ERROR`).
   - `tt-data-league-api-rest`: extend `ImportResourceControllerTest` with a
     `startProcessRoutesTheResourceIdToTheCommandBus` test mirroring
     `previewRoutesTheResourceIdToTheCommandBus`.
   - Frontend: `ImportProcessWorkspace.test.jsx` (all four states + retry); `ImportPanel.test.jsx`
     additions covering the direct **Import** action and the preview "proceed" action both landing in
     `ImportProcessWorkspace`, and a fresh **Simulate** clearing a stale import result.
   - Run `mvn -pl tt-data-league-import,tt-data-league-core-domain,tt-data-league-api-rest,tt-data-league-api-runtime -am test`
     and the full `mvn test` reactor, plus `npm run lint`, `npm run build`, and the frontend test suite,
     before considering the feature implementation finalized.

# Acceptance Criteria

- [x] The **Import** action triggers the import process for the selected import resource and displays the result in a dedicated import workspace.
- [x] The import workspace presents the validation findings and processing errors accessibly, with translated copy in Catalan, Spanish, and English.
- [x] Administrators can retry a failed import or proceed from a successful import to the next action without losing the selected resource context.
- [x] The import process uses the authenticated backend contract and does not start a different or unscoped resource.
- [x] The endpoint `/api/v1/administration/import/start` provides the import status for the selected import resource.

# Implementation Guidelines

- Keep `tt-data-league-core-domain` free of any `tt-data-league-import` dependency; only
  `tt-data-league-api-runtime` (already updated for FEAT-00029) may depend on both.
- The recording decorators must never swallow a processor exception — always rethrow after recording,
  so the navigator's existing per-file isolation and `processorFailures` counting keep working
  unchanged. Do not modify `RfetmMatchImportProcessor`, `RfetmTeamImportProcessor`,
  `RfetmPlayerImportProcessor`, or their BCNESA/FCTT equivalents.
- Reuse `ImportResource.setPending()` / `startProcessing()` / `finishProcessing(boolean)` exactly as
  defined; guard the `PROCESSING` re-entrancy case explicitly instead of letting the entity's
  `IllegalStateException` reach the API layer unhandled.
- Reuse `ImportPreviewFinding` / `ImportPreviewProcessingError` for the process result's findings and
  errors instead of duplicating identical shapes; do not rename or move those `done`-feature classes
  as part of this change.
- Reuse existing DTO/response envelope and API client conventions (`DomainCommandResponse`,
  `apiRequest`) rather than inventing new response shapes.
- Do not add a second GET status-polling endpoint; this feature's contract is the single synchronous
  `POST /start` call per the registry's acceptance criteria. Treat asynchronous/long-running import
  execution as a separate future feature if it becomes necessary.
- Out of scope: club/player consolidation (remains CLI-only and opt-in via `tt-data-league-import-runtime`'s
  `App`), `ResourceType.TEAMS` resources (return the same unsupported-type failure pattern as preview),
  refactoring the `done` `ImportPreviewWorkspace`/preview domain classes beyond the minimal reuse
  described above, and changing the existing `upload` / `list_by_source` / `preview` / `preview_status`
  contracts.

# Notes

- 2026-09-05: Build plan drafted from repository inspection. `POST /start`,
  `StartImportProcessCommand`, and `StartImportProcessCommandHandler` already exist as stubs (handler
  returns `null`); the `tt-data-league-import` dependency needed to reach the RFETM/BCNESA/FCTT
  navigators/processors is already present on `tt-data-league-api-runtime` (added for FEAT-00029), so
  no architecture-boundary work is required this time.
- 2026-09-05: Confirmed `RfetmMatchImportProcessor`/`RfetmTeamImportProcessor`/`RfetmPlayerImportProcessor`
  and their BCNESA/FCTT equivalents are `@Component`-annotated `MatchContextProcessor` /
  `BcnesaMatchReportProcessor` / `FcttMatchReportProcessor` beans with identical `@Order` values across
  sources (Team=10, Player=20, Match=30), already used unmodified by `tt-data-league-import-runtime`'s
  `App` via the navigators' injected-processor-list overload. `MatchContextProcessor`'s Javadoc states
  implementations "should therefore be idempotent, so that re-running a season after fixing a failure
  is safe," which directly supports this feature's retry requirement.
- 2026-09-05: Decided to keep the single synchronous `POST /start` contract (matching the registry's
  own acceptance criterion) rather than adding a `/process_status`-style GET endpoint like FEAT-00029's
  `/preview_status` — preview needed a second endpoint only because the already-polled `/status`
  endpoint had an incompatible, source-level meaning; no such conflict exists here.
  If real import runs prove too slow for a synchronous request in practice, revisit as a follow-up.
- 2026-09-05: Decided to add source-specific "recording decorator" processors (wrap-record-rethrow)
  instead of modifying the existing persisting processors, so `processingErrors` can be reported with a
  message/location similar to the preview workspace without touching write logic that the root
  `AGENTS.md` and `tt-data-league-import/AGENTS.md` both treat as sensitive to unrelated changes.
- 2026-09-05: Open product question, not resolved by this planning pass: what "proceed from a
  successful import to the next action" means, since import is currently the last step in the
  Upload -> Simulate -> Import resource flow. Recommended default (pending confirmation): return to the
  resource list while keeping `selectedSource`, rather than inventing an undefined next screen.
- 2026-09-05: No application code changed by this planning task; only `docs/sdd/FEATURES.md` (status)
  and this details file were edited.
- 2026-09-05: Implemented the synchronous import process end-to-end. The domain handler now
  transitions and persists resources with retry and duplicate-processing guards; the import module
  reuses ordered write processors through recording decorators; and the frontend exposes a translated
  import workspace for direct and preview-proceed actions. Successful imports return to the resource
  list while retaining the selected source.
- 2026-09-05: Validation completed for the core-domain module (39 tests) and frontend (133 tests),
  lint, and build. The import-module suite still has eight existing processor/consolidation
  failures, and the REST test compilation remains blocked by missing pre-existing
  `ImportJobController` and `InMemoryImportJobsService` classes; neither blocker is caused by the
  FEAT-00030 changes.
- 2026-09-05: User-approved closure recorded; FEAT-00030 is shipped and moved to `done`.
