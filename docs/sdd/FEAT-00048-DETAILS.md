# Build Plan

1. **`tt-data-league-frontend/src/hooks/useImportResources.js`**
   - No change to the fetch/normalize logic. `normalizeImportResources` already
     keeps `resource.status` (raw backend value: `PENDING`, `PROCESSING`,
     `PROCESSED`, `ERROR`) and `resource.season` on each normalized item —
     both are needed for grouping/sorting.

2. **`tt-data-league-frontend/src/components/import/ImportResourceList.jsx`**
   - Add a small grouping helper (co-located in this file, no new module
     needed):
     - `isImported(resource)` → `resource.status?.toUpperCase() === 'PROCESSED'`.
     - Everything else (`PENDING`, `PROCESSING`, `ERROR`, missing/unknown
       status) is treated as pending, matching acceptance criteria wording
       ("yet to be imported or are in progress"). `ERROR` stays in Pending
       since it was not successfully imported and typically needs a retry.
     - `isProcessing(resource)` → `resource.status?.toUpperCase() === 'PROCESSING'`.
   - Build the two groups with `useMemo`, each sorted by `season` descending
     (numeric compare with a string fallback, since `season` may be a
     4-digit year or a "2024/2025"-style string):
     ```js
     const bySeasonDesc = (a, b) => String(b.season ?? '').localeCompare(String(a.season ?? ''), undefined, { numeric: true })
     ```
   - Replace the single flat `resources.map(...)` render with two sections,
     each reusing the existing `Card`/`Badge`/`dl` markup already in the file
     (only the wrapping section changes, not the per-item structure):
     - `import-resource-group import-resource-group--imported` with heading
       `t('importPanel.resourcesImportedTitle')`, rendered only when the
       imported group is non-empty.
     - `import-resource-group import-resource-group--pending` with heading
       `t('importPanel.resourcesPendingTitle')`, rendered only when the
       pending group is non-empty.
     - Keep the outer `role="list"` / `role="listitem"` structure per group
       so existing a11y semantics still hold; wrap each group's items in its
       own `<div role="list">`.
   - Add a "processing" indicator on cards where `isProcessing(resource)` is
     true: a small spinner/dot element with `aria-hidden="true"` plus visually
     hidden text (`t('importPanel.resourceProcessing')`) next to the status
     `Badge`, e.g. `<span className="import-resource-processing-indicator" role="status" aria-live="polite">{t('importPanel.resourceProcessing')}</span>`.
     Keep the existing `tone()`/`displayValue()` badge showing the raw status
     text as today — the indicator is additive, not a replacement.
   - No prop-shape changes: `ImportResourceList` still receives
     `{ resources, onSimulate, onImport, disabled }` from `ImportPanel.jsx`;
     grouping/sorting stays internal to this component.

3. **`tt-data-league-frontend/src/app.css`**
   - Add styles for `.import-resource-group` (heading + `.import-resource-items`
     spacing, reusing the existing `.import-resource-items` flex-column rules)
     and `.import-resource-group h3` (small, muted, same scale as the existing
     `.import-resource-list h2`).
   - Add `.import-resource-processing-indicator` (small pulsing/spinning dot,
     `color: var(--...)` consistent with existing warning/info tones already
     used by `Badge`'s `warning` tone).

4. **i18n — `ca.js` (base), `es.js`, `en.js`** under `importPanel`:
   - Add `resourcesImportedTitle` ("Importats" / "Importados" / "Imported").
   - Add `resourcesPendingTitle` ("Pendents" / "Pendientes" / "Pending").
   - Add `resourceProcessing` ("Important…" / "Importando…" / "Importing…") for
     the in-progress indicator's accessible text.
   - Follow the existing pattern: `ca.js` holds the base strings, `es.js` and
     `en.js` spread `...ca.importPanel` and only override changed/added keys
     (see how `en.js:101-102` already does this for the `importPanel` block).

5. **`tt-data-league-frontend/src/components/import/ImportPanel.jsx`** (new
   requirement: auto-refresh the resource list once the uploaded file's
   import resource shows up as `PENDING`)
   - `loadFile()` already calls `resources.refresh()` immediately after
     `uploadImportFile` resolves (existing behavior, keep as-is) — but that
     refresh can race the backend, which registers the new
     `ImportResource` (and its `PENDING` status) asynchronously after the
     upload request completes, so the new entry may not exist yet on that
     first refresh.
   - Before calling `uploadImportFile`, capture the current resource IDs for
     the selected source in a ref, e.g.
     `const idsBeforeUpload = new Set(resources.data.map((r) => r.id))`.
   - After the existing post-upload `resources.refresh()` call, start a
     short poll (mirror the `useImportProcessStatus` pattern: recursive
     `window.setTimeout`, ~2000ms interval, cleared on unmount/source change/
     next upload) that calls `resources.refresh()` again and checks, once
     `resources.data` has reloaded (`!resources.loading`), whether it now
     contains an id absent from `idsBeforeUpload` with
     `status?.toUpperCase() === 'PENDING'`. Stop polling as soon as that is
     true; also stop after a bounded number of attempts (e.g. 10, ~20s
     total) so a slow/failed backend registration doesn't poll forever.
   - Guard against races: cancel/ignore the poll if the user switches
     `selectedSource` or starts a new upload while it is in flight (track an
     `active` flag / effect cleanup like the other hooks in this file do).
   - No new component props; this stays internal to `ImportPanel.jsx`
     (`resources` and `uploadState` are already local state/hook results
     there).

6. **Tests**
   - `ImportPanel.test.jsx`: add a case that mocks `useImportResources` to
     first return the pre-upload list, then (after `refresh` is called
     again) a list including the new resource with `status: 'PENDING'`;
     assert `refresh`/the resulting resource card render happens without
     requiring any other user action, and that polling stops (no further
     unexpected `refresh` calls) once the pending resource appears. Use
     fake timers (`vi.useFakeTimers()`) to advance past the poll interval
     deterministically.

7. **Tests — grouping/sorting/indicator (implemented; kept for reference)**
   - `tt-data-league-frontend/src/components/import/ImportResourceList.test.jsx`
     (new file, mirroring the style of `ImportPanel.test.jsx`): render with a
     mixed list of `PENDING`, `PROCESSING`, `PROCESSED`, `ERROR` resources
     across multiple seasons and assert:
     - Two group headings are present when both groups are non-empty.
     - Only `PROCESSED` resources render under the "Imported" heading; the
       rest render under "Pending".
     - Within each group, items appear in descending `season` order.
     - The processing indicator text/role is present only for the
       `PROCESSING` resource's card.
     - A group heading is omitted when its group is empty (e.g. all
       resources pending).
   - Update `ImportPanel.test.jsx` only if any existing assertion relies on
     resource ordering that grouping now changes (scan current assertions
     around line 66-92 for the `PROCESSED` resource item; adjust selectors if
     they assumed a flat list order).

# Implementation Guidelines

- Grouping and sorting are pure frontend/display concerns; no API or DTO
  changes (`ImportResourceDto`, `ImportResourceController`, etc. are
  unaffected).
- Status comparison must be case-insensitive and tolerate an unset status
  (treat as pending), matching the existing `tone()` helper's defensive
  style in this file.
- Do not introduce a new sorting/grouping utility module for a single
  consumer — keep the helpers local to `ImportResourceList.jsx` unless a
  second consumer appears later.
- Preserve the current per-card markup (`Card`, `Badge`, action buttons,
  `dl` details) exactly; only the surrounding grouping/heading/indicator is
  new, to minimize risk to existing snapshot/DOM-query based tests.
- Out of scope: sorting by anything other than season within a group,
  persisting group collapse/expand state, and changing how `ERROR` resources
  are actioned (they keep today's simulate/import buttons and disabled
  behavior).
- Post-upload polling (new requirement) only targets the single most-recent
  upload's resulting resource; it does not need to track multiple concurrent
  uploads. It must not run indefinitely — bound the attempts/timeout — and
  must not fight with the existing source-status-driven refresh effect
  (lines ~123-143 of `ImportPanel.jsx`); both may call `resources.refresh()`
  independently, which is safe since `useImportResources.refresh()` is
  idempotent.

# Notes
- Backend `ImportResourceStatus` enum (`PENDING`, `PROCESSING`, `PROCESSED`,
  `ERROR`) is the authoritative status vocabulary; frontend already receives
  it as a free-text `status` string via `normalizeImportResources`.
- Open question for follow-up (not blocking this plan): should `ERROR`
  resources get their own third section/visual treatment instead of sharing
  "Pending"? Current acceptance criteria only require Imported vs Pending, so
  `ERROR` stays folded into Pending for now.
- Implemented per plan: `ImportResourceList.jsx` now groups resources into
  Imported/Pending sections (each sorted by season DESC), with a pulsing
  `.import-resource-processing-indicator` shown on `PROCESSING` cards. Added
  `resourcesImportedTitle`, `resourcesPendingTitle`, `resourceProcessing`
  i18n keys to `ca.js`/`es.js`/`en.js`. Added
  `ImportResourceList.test.jsx` covering grouping, sorting, the processing
  indicator, and empty-group heading omission; `ImportPanel.test.jsx`
  required no changes since its resource fixtures don't exercise ordering
  across groups. Full frontend suite (195 tests) and lint pass.
- 2026-09-11: added new requirement — after a file upload, once the new
  import resource becomes visible in `PENDING` state, the resources list
  must auto-refresh (not yet implemented; see Build Plan step 5/6 above).
- 2026-09-11: implemented the post-upload polling in `ImportPanel.jsx`.
  Deviated slightly from the original plan: implemented as a plain
  `useEffect` reacting to the `useImportResources()` hook's own
  `data`/`loading`/`refresh` (not a hand-rolled `getImportResourcesBySource`
  poll loop), gated by a new `uploadPollActive` state flag and
  `preUploadResourceIds`/`uploadPollAttempts` refs, since that reuses the
  existing hook instead of duplicating its fetch/normalize logic. Selecting
  a different source (`handleSelectSource`) resets the poll instead of a
  separate effect, to avoid a `react-hooks/set-state-in-effect` lint error
  from setting state synchronously inside an effect keyed on
  `selectedSource`.
- 2026-09-11 (continued): Polls every 2s, capped at 10 attempts (~20s). Root
  cause confirmed by the user report ("the import resources list is not
  updating and adding the just uploaded new import resource"): the
  pre-existing immediate `resources.refresh()` right after upload could
  race the backend's asynchronous registration of the new
  `ImportResource`. Added
  `ImportPanel.test.jsx: 'polls for the newly uploaded resource until it
  shows up as PENDING, then stops refreshing'` using fake timers. Full
  suite (196 tests) and lint pass.
- 2026-09-11: fixed a related bug reported by the user — "when importing an
  import resource card, the PROCESSING status is not displayed
  automatically, it needs a manual refresh of the screen or manual
  navigation to be shown, with the import resource progress icon". Root
  cause: the run-status effect in `ImportPanel.jsx` only called
  `resources.refresh()` once the import run reached a *terminal* status
  (`if (!runId || !status || isActiveImportRunStatus(status)) return`
  skipped `queued`/`running`), so the resource card kept showing its
  pre-import status (e.g. `PENDING`) — and thus never rendered the
  processing indicator from this feature — until something unrelated
  triggered a refetch (the 5s source-status poll, or navigating away and
  back). Fix: the effect now refreshes resources on every distinct
  `runId:status` transition, including `queued`/`running`;
  `refreshHistory()` still only fires at the terminal transition,
  unchanged. Added `ImportPanel.test.jsx: 'refreshes the resource list as
  soon as the run becomes active, before it finishes'`. Full suite (197
  tests) and lint pass.
