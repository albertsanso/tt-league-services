# Build Plan

1. **Backend — report a real total during traversal, not just at the end.**
   Today each navigator (`RfetmActasDirectoryNavigator`, `BcnesaActasDirectoryNavigator`,
   `FcttActasDirectoryNavigator`) reports progress via `ImportRunProgress.indeterminate(...)`
   while traversing (see each navigator's `reportProgress`/`Counters` helper), and
   `NavigatorImportExecutionService.execute` only emits a single `determinate` snapshot with the
   real total *after* traversal finishes. This is why the UI currently shows "processed" but never
   "total" while a run is active.
   - Add a fast pre-count pass to each navigator: before dispatching processors, walk the same
     source tree with the same file-matching predicate already used for real traversal, and count
     matching files without processing them.
   - Report an initial `ImportRunProgress.determinate(0, preCountedTotal, 0, 0)` via the
     `ImportProgressListener` before processing starts, then keep reporting `determinate(...)`
     with that fixed total as `processed`/`skipped`/`errorCount` change (replace the
     `indeterminate(...)` calls in each navigator's `reportProgress`).
   - `ImportRunProgress`/`ImportRunStatusDto` already support a nullable `total`/`percentage`; no
     DTO or contract change is required.
   - Files: `tt-data-league-import/.../rfetm/traverse/RfetmActasDirectoryNavigator.java`,
     `.../bcnesa/traverse/BcnesaActasDirectoryNavigator.java`,
     `.../fctt/traverse/FcttActasDirectoryNavigator.java`.
   - Tests: per-navigator unit test asserting the listener receives a determinate snapshot with the
     correct total before any file is dispatched, and that `total` stays constant while `processed`
     grows across subsequent callbacks.

2. **Backend — verify progress propagation end to end.**
   Confirm `InMemoryImportRunRegistry.updateProgress` stores the latest `ImportRunProgress`
   (including the new early determinate total) and that `FindImportRunStatusQueryHandler` /
   `ImportRunStatusDtoMapper.toDto` surface it unchanged. Add a
   `StartImportProcessCommandHandler` test asserting `process_status` polls show a stable,
   non-null `total` from the first running snapshot onward for a fixture with a known file count.

3. **Frontend — reset the ZIP file chooser after a successful upload.**
   `ImportPanel.loadFile()` sets `uploadState` to `success` but never clears `file`, and
   `ImportFileControls`'s `<Input type="file">` is an uncontrolled native element, so the browser
   keeps showing the previously selected filename even once `file` is cleared in React state.
   - In `ImportPanel.jsx`, call `setFile(null)` alongside the `success` `uploadState` update in
     `loadFile()`.
   - In `ImportFileControls.jsx`, force the native input to remount when `file` becomes `null`
     after a success (e.g. `key={file ? file.name : 'empty'}` on the `Input`), since resetting only
     the React prop does not clear an uncontrolled file input's displayed value.
   - Tests: extend `ImportFileControls.test.jsx` and `ImportPanel.test.jsx` to assert the input is
     visually and programmatically cleared after a successful upload.

4. **Frontend — synchronize refresh on source status transition.**
   The existing effect in `ImportPanel.jsx` (around the `previousSourceStatuses` ref) already
   detects when a source's status flips to `available`/`error`, but it only resets `uploadState`;
   it never refreshes seasons/report data or the resource list for that source, so those panels
   drift out of sync with the star indicator.
   - Extract the duplicated "fetch history and normalize into `{ data, loading, error }`" logic
     (currently inlined both in the mount `useEffect` and in `loadFile`) into one
     `refreshHistory()` callback.
   - In the `sourceCompletedAction` effect, when the transitioning source equals `selectedSource`,
     call `refreshHistory()` and `resources.refresh()` in addition to the existing `uploadState`
     reset.
   - Covers acceptance criterion 1.

5. **Frontend — refresh the resource list when an import run finishes.**
   `resources.refresh()` is currently only invoked after a successful upload
   (`ImportPanel.jsx loadFile`); nothing refreshes the resource list when
   `useImportProcessStatus` reaches a terminal status (`success`/`empty-result`/`failure`).
   - Add a `useEffect` keyed on `runStatus.data?.status` that, on a terminal transition for the
     currently tracked `runId`, calls `resources.refresh()` (and `refreshHistory()` from step 4) if
     the run's source matches `selectedSource`.
   - Guard with a ref tracking the last-handled `runId`/status pair so the refresh fires exactly
     once per terminal transition, not on every render.
   - Covers acceptance criterion 2.

6. **Frontend — persist active import progress across source switches and reloads.**
   `importState` already lives above `selectedSource` in `ImportPanel`, so switching
   `selectedSource` alone does not currently lose an in-flight run's UI — but the run tracking
   (`runId`) is plain component state, so a full page reload loses it even though the backend run
   keeps executing in the background.
   - Persist `{ source, resource, runId }` for the single active run to `sessionStorage` under a
     dedicated key (e.g. `import-panel:active-run`) whenever `importState` changes with a non-null
     `runId`; clear the entry when `clearImport()` runs or the run reaches a terminal status the
     user has acknowledged (navigating back to the resource list).
   - On mount, lazily initialize `importState` (and `selectedSource`, if unset) from the stored
     value via `useState(() => ...)` so `useImportProcessStatus(runId)` immediately resumes
     polling instead of starting from an empty state.
   - Only one active run is tracked/persisted at a time, matching the existing server-side
     constraint that a resource already `PROCESSING` cannot be started again
     (`StartImportProcessCommandHandler`); concurrent runs across multiple sources are out of
     scope for this feature.
   - Covers acceptance criterion 6.

7. **Backend and frontend — allow only one import process running at a time, system-wide.**
   `InMemoryImportRunRegistry.registerQueued` only ever reserved the "active run" slot per
   `importResourceId` (`activeRunByResource`), so two different resources - even across different
   sources - could run concurrently; `StartImportProcessCommandHandler` only rejects a start when
   *that same* resource is already `PROCESSING`.
   - Add a single system-wide lock (`AtomicReference<UUID> activeRun`) to
     `InMemoryImportRunRegistry.registerQueued`: reserve it with `compareAndSet(null, runId)` before
     the existing per-resource reservation, release it (`compareAndSet(runId, null)`) in
     `complete(...)` alongside the existing per-resource cleanup. Update the `ImportRunRegistry`
     interface javadoc to state the constraint is system-wide, not per-resource.
   - Add `ImportRunStatusDtoMapper.anotherRunActive(UUID)` with a message distinct from
     `alreadyProcessing(UUID)` (which stays for "this exact resource is already `PROCESSING`"), and
     have `StartImportProcessCommandHandler` use it when `registerQueued` returns empty.
   - Frontend: compute `importInProgress` in `ImportPanel.jsx` from `importState`/`runStatus`
     (`submitting`, or an active/unknown-status `runId`); guard `startProcess`/`startPreview` with
     it, and pass it down as `disabled` to `ImportResourceList` and `SeasonImportList` (→
     `SeasonImportListItem`) so every "Importa"/"Simula" action is disabled while any import is
     running - not just the one on the resource currently in progress. Simulate is disabled too,
     not because previewing counts as an "import process", but because `startPreview` resets
     `importState` (see `emptyImportState()`), which would otherwise silently drop the UI's
     tracking of the still-running backend run.
   - Covers the new "only one import process running" requirement.

8. **Frontend — display the source/federation name in the import result/progress status.**
   `ImportProcessWorkspace`'s heading (`resourceLabel`) only joins `resource.resourceType` and
   `resource.season`; the resource objects returned by `list_by_source`
   (`normalizeImportResources`) carry no `source` field, so the source/federation was never shown
   next to the progress/result view. The run-status payload already carries it though:
   `useImportProcessStatus`'s `normalizeImportRunStatus` maps `status.source` (the backend's
   `ImportRunStatusDto.source()`, populated from `ImportRunSnapshot.source()` at
   `registerQueued(...)` time) straight through as `run.source` - no backend change is needed.
   - Change `resourceLabel(resource, t)` to `resourceLabel(resource, source, t)` in
     `ImportProcessWorkspace.jsx`, prefixing the joined label with `source` when present, and pass
     `run.source` (or `run?.source` in the pre-run loading branch, where `run` can still be `null`)
     at each of the three call sites (loading, active/progress, terminal/result).
   - No prop changes needed in `ImportPanel.jsx` - the value already flows through `process.run`.
   - Covers the new "display source/federation name in import result/progress status" requirement.

9. **Tests and verification.**
   - Backend: run the affected navigator/unit tests and `StartImportProcessCommandHandler` tests
     (`mvn test` on the touched modules); add `InMemoryImportRunRegistryTest` cases for a second
     resource being rejected while one is active, accepted once the first reaches a terminal
     status, and exactly one submission winning under concurrent load across *different* resources.
   - Frontend: extend `ImportPanel.test.jsx`, `ImportFileControls.test.jsx`, and
     `useImportProcessStatus.test.jsx` for the new refresh, reset, persistence, and single-run-lock
     behavior; run the frontend test suite.
   - Manual pass on the import screen: upload a ZIP and confirm the file chooser clears, the star
     turns/stays green, and seasons/report/resource panels refresh once status flips to
     `available`; start an import and confirm the progress bar shows a growing total from the
     start; switch source mid-run and back, and reload the page mid-run, confirming the progress
     view resumes; while an import runs, confirm every other resource/season's "Importa" and
     "Simula" buttons are disabled, and they re-enable once the run reaches a terminal status;
     confirm the source/federation name is shown alongside the progress and result views.

# Implementation Guidelines

- The pre-count pass in each navigator must reuse the exact same file-matching predicate as the
  real traversal so the reported `total` always matches the number of files actually dispatched;
  do not introduce a second, divergent notion of "matching file".
- Accept the added IO cost of a pre-count walk per navigator; do not attempt to cache or share it
  across runs — out of scope for this feature.
- Keep `ImportRunProgress`/`ImportRunStatusDto` contracts unchanged; this feature is about when and
  how existing fields are populated, not new fields.
- `sessionStorage` persistence is per-tab and best-effort UI convenience; it does not replace the
  backend as the source of truth for run state, and multi-tab synchronization is out of scope.
- Only one import run may be active system-wide (see requirement 5); this is now an enforced
  invariant (`InMemoryImportRunRegistry`'s global `activeRun` lock plus the disabled frontend
  actions), not just a UI-tracking limitation. Do not add support for concurrently tracking or
  running multiple in-flight runs.

# Notes

- Root cause confirmed by reading the current implementation: `ImportPanel.jsx` already fetches
  history once at mount and refreshes it only from `loadFile`; the source-status-transition effect
  (`previousSourceStatuses` ref) only resets `uploadState` and never touches `resources`/`history`;
  `resources.refresh()` is never called from the import-run completion path; and
  `ImportFileControls`'s file `<Input>` is uncontrolled, so clearing `file` in state alone does not
  reset what the browser displays.
- Root cause for "no total during processing" confirmed in
  `NavigatorImportExecutionService.execute`: navigators report
  `ImportRunProgress.indeterminate(...)` while walking (per the javadoc on `ImportRunProgress`,
  "the navigators report file/fixture counters incrementally without a pre-computed total"), and a
  single `determinate(...)` snapshot is only emitted after traversal completes.

## Implementation notes (2026-09-09)

All six build-plan steps landed as designed; every acceptance criterion is met:

- Steps 1-2 (backend total): `RfetmActasDirectoryNavigator`, `BcnesaActasDirectoryNavigator`, and
  `FcttActasDirectoryNavigator` now pre-count matching report files (reusing each navigator's exact
  folder/file predicates) and emit an initial `ImportRunProgress.determinate(0, total, 0, 0)` before
  dispatching, then keep reporting `determinate(...)` with that fixed total per file. For BCNESA,
  the "processed" unit reported during a run was switched from `fixturesDispatched` to `filesSeen`
  so it shares units with the file-level `total` (the fixture count per file is only known after
  parsing, so it cannot be pre-counted cheaply); `fixturesDispatched` is unchanged in the final
  `BcnesaTraversalSummary`. `InMemoryImportRunRegistry`/`FindImportRunStatusQueryHandler` needed no
  changes — they already propagate the latest snapshot unmodified. Verified with per-navigator unit
  tests (`reportsMonotonicDeterminateProgressWithARealTotalWhileTraversing`) and the existing
  `StartImportProcessCommandHandlerTest`/`InMemoryImportRunRegistryTest` suites.
- Step 3 (ZIP reset): `ImportPanel.loadFile()` now clears `file` on upload success, and
  `ImportFileControls` keys its native `<Input type="file">` on the filename (remounting it when
  `file` becomes `null`) since the element is otherwise uncontrolled.
- Steps 4-5 (refresh sync): extracted a shared `refreshHistory()` callback; the source-status
  transition effect now also calls it plus `resources.refresh()` when the transitioning source is
  the one selected, and a new effect watches `useImportProcessStatus` for a terminal transition
  (guarded by a `lastHandledRunTransition` ref) to do the same.
- Step 6 (persistence): `importState` gained a `source` field; the active run
  (`{ source, resource, runId }`) is written to `sessionStorage` (`import-panel:active-run`) on
  every change and cleared when the run is cleared; `selectedSource`/`importState` are lazily
  rehydrated from it on mount so a page reload mid-run resumes polling immediately.
- Validation: full frontend suite (`npx vitest run`, 30 files / 164 tests) passes; the touched
  backend module (`tt-data-league-import`) compiles and its navigator/execution tests pass. Eight
  pre-existing `tt-data-league-import` test failures (`ImportProcessorsTest`,
  `BcnesaImportProcessorsTest`, `FcttImportProcessorsTest`, `TeamToClubConsolidationProcessorTest`)
  were confirmed present on `main` before this change (same count and assertions) and are unrelated
  — they look like a missing local DB/environment dependency, not a regression from this feature.

## Implementation notes (2026-09-09, requirement 5 addition)

Requirement 5 ("allow 1 import process running only") added after the first implementation pass;
build-plan step 7 landed as designed, acceptance criterion added and met:

- Backend: `InMemoryImportRunRegistry` gained a system-wide `AtomicReference<UUID> activeRun` lock
  in `registerQueued`/`complete`, on top of (not replacing) the existing per-resource
  `activeRunByResource` map. `ImportRunRegistry`'s javadoc now states the constraint is system-wide.
  `StartImportProcessCommandHandler` now returns the new
  `ImportRunStatusDtoMapper.anotherRunActive(UUID)` (distinct message from `alreadyProcessing`) when
  `registerQueued` rejects a start because a *different* resource's run is active.
- Frontend: `ImportPanel.jsx` computes `importInProgress` (submitting, or an active/not-yet-known
  run status) and uses it to short-circuit `startProcess`/`startPreview`, and to pass a `disabled`
  prop through `ImportResourceList`/`SeasonImportList`/`SeasonImportListItem` disabling every
  "Importa" and "Simula" button while any import runs. Simulate is included in the lock because
  `startPreview` unconditionally resets `importState` (`setImportState(emptyImportState())`) - if
  left enabled during an active run, clicking it would silently drop the frontend's tracking (and
  `sessionStorage` persistence) of a run that is still executing on the backend, even though the
  backend itself would correctly keep it running and reject any competing start.
- Validation: added `InMemoryImportRunRegistryTest` cases (cross-resource rejection while active,
  acceptance after the active run terminates, exactly-one-winner under concurrent submissions across
  *different* resources — 8/8 pass) and an `ImportPanel.test.jsx` case asserting every
  "Importa"/"Simula" button is disabled while a run is active and re-enabled once it completes. Full
  frontend suite: 30 files / 165 tests pass. `StartImportProcessCommandHandlerTest` (uses its own
  fake, per-resource-only registry, unaffected by the global lock) still passes 8/8.

## Implementation notes (2026-09-09, requirement 6 addition)

Requirement 6 ("display source/federation name in import result/progress status") added after the
single-run-lock pass; build-plan step 8 landed as designed, acceptance criterion added and met:

- Frontend only, no backend change: `ImportProcessWorkspace.jsx`'s `resourceLabel` now takes a
  `source` argument and prefixes the joined heading with it (`SOURCE · resourceType · season`); all
  three call sites (initial-submission loading, active/progress, terminal/result) pass `run?.source`
  / `run.source`. The value already existed end-to-end - `ImportRunSnapshot.source()` set at
  `registerQueued(...)`, mapped through `ImportRunStatusDtoMapper.toDto` and
  `normalizeImportRunStatus` - it just wasn't rendered.
- Validation: added `ImportProcessWorkspace.test.jsx` cases asserting the source appears in the
  heading for both the active/progress and terminal/result states (e.g. `"BCNESA · ACTAS ·
  2025-2026"`). Full frontend suite: 30 files / 167 tests pass.
