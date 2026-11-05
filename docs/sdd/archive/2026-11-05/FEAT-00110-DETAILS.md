# Build Plan
All paths are relative to `tt-league-pipeline-orchestrator-frontend/`. This feature is frontend-only: no change to
the orchestrator runtime, the platform, the root POM, the module `pom.xml` or `package.json` (no new dependency). It
replaces the FEAT-00109 placeholder `RunsPage` and `RunDetailPage` and builds on the existing client
(`src/api/runs.ts`, `src/api/types.ts`, `ApiError`/`triggerResults`), the event provider (`useRunEvents`,
`useEventConnection`) and permission gating (`<Can capability="trigger-runs">`).

**Contracts (read from the code on 2026-10-05).**

- `GET /api/pipeline/runs?source*&status*&from&to&page&size` → `Page<RunSummary>`, newest first (`createdAt` desc,
  then `id` desc). `from` is inclusive and `to` exclusive on `createdAt` (ISO instants); `size` 1-100; `from` after
  `to` is `400` with `field: "from"`; unknown enum values are `400`. List items carry `steps: StepStatusSummary[]`
  (latest attempt per `StepKind`, ordered `INGEST`, `FETCH_PACKAGE`, `IMPORT`).
- `GET /api/pipeline/runs/{id}` → `RunDetail` (summary fields + `steps: Step[]` with every attempt, `artifacts`,
  `importReport | null`, `issues: string[]`); `404` for an unknown id, `400` for a malformed UUID. Durations of an
  active run or step are measured up to the server's "now" at response time.
- `POST /api/pipeline/runs` `TriggerRunRequest {source: RFETM|BCNESA|FCTT|ALL, season, scopeType, filters?, force}`
  (needs `matches:write`): `201` when at least one run was created, `202` when nothing was created but a trigger was
  queued (`PIPELINE_TRIGGER_CONFLICT_MODE=QUEUE`), `409` when every source was rejected (active run or existing
  pending trigger; `REJECTED` results carry `code`, `message`, `activeRunId`), `422` when the scope is unavailable
  (`UNAVAILABLE` results, codes `NO_OPEN_MATCH_DAYS`, `NO_INGEST_STATUS`, `SCOPE_UNMATCHED`), `400` on validation
  (`field` is `season`, `filters`, `scopeType` or `source`), `403` without `matches:write`. Every non-`400` body has
  `results: TriggerResult[]`, one per source; with `ALL` a `201` can mix `CREATED` with `REJECTED`/`UNAVAILABLE`.
  Rules mirrored client-side: season matches `^(\d{4})-(\d{4})$` with consecutive years
  (`PipelineRun.requireValidSeason`); `GROUP` needs exactly one source and at least one filter; `OPEN_MATCH_DAYS` and
  `FULL_SEASON` reject filters; a filter needs at least one non-blank field; `matchDays` are positive integers.
  There is no default season, source or scope.
- Events (`useRunEvents`): `run` (`RunEvent`, a summary **without** `steps`, sent on every run change), `step`
  (`StepEvent`, every step save), `pending-trigger` (`QUEUED`/`LAUNCHED`/`DROPPED`) and the client-side
  `reconnected`. There is no replay and no log endpoint: the "live log" is built in the browser from these events.
- Active statuses (core `RunStatus.isActive`): `QUEUED`, `RUNNING_INGEST`, `PACKED`, `IMPORTING`; terminal:
  `NO_CHANGES`, `SUCCEEDED`, `PARTIAL`, `FAILED`.

**Build steps.**

1. **Run presentation helpers (`src/runs/format.ts`, `src/runs/runStatus.ts`).** Pure functions, no React:
   - `isActiveStatus(status)`, `ACTIVE_STATUSES`, `TERMINAL_STATUSES` (mirror of `RunStatus`), `STEP_ORDER`
     (`INGEST`, `FETCH_PACKAGE`, `IMPORT`) and display labels for statuses, triggers, step kinds and scope types.
   - `statusColor(status)` → MUI chip colour: `SUCCEEDED` success, `PARTIAL` warning, `FAILED` error, `NO_CHANGES`
     default, active statuses info.
   - `formatDuration(ms | null)` (`—`, `12 s`, `3 min 05 s`, `1 h 02 min`), `elapsedMs(startedAt, finishedAt, now)`,
     `formatInstant(iso)` (local date-time), `formatBytes(n)`.
   - `scopeLabel(run)`: `Full season` when `fullSeason`, otherwise one short text per filter
     (`category · group · phase · territory · gender · md 3, 4`, only the set fields) and `+N more` after two;
     `scopeDetails(run)` returns every filter for a tooltip.
   - `mergeRunEvent(row, event)` (event fields over the row, keeping the row's `steps`) and
     `mergeStepSummary(steps, stepEvent)` (replace the kind's entry when the attempt is the same or newer; keep
     `STEP_ORDER`).
   - Tests (`format.test.ts`, `runStatus.test.ts`): every status colour and active flag, duration boundaries,
     scope labels for full season / one filter / three filters / match days, merge rules (older attempt ignored,
     new kind inserted in order, row `steps` kept on a run event).

2. **Filters in the URL (`src/runs/runFilters.ts`).** The runs list state lives in the query string so a filtered
   view can be bookmarked and survives reload and the round trip to a detail page:
   `?source=RFETM&source=FCTT&status=FAILED&from=2026-10-01&to=2026-10-05&page=2` (page 1-based in the URL).
   - `parseRunFilters(URLSearchParams)` → `{sources, statuses, fromDate, toDate, page, errors}`: unknown source or
     status values and malformed dates or pages are dropped and reported in `errors` (shown as a warning, never
     silently replaced by another value); `fromDate` after `toDate` is an error and no request is sent.
   - `serializeRunFilters(filters)` and `toRunListQuery(filters)`: local dates become instants with the browser's
     zone, `from` = start of `fromDate`, `to` = start of the day **after** `toDate` (exclusive bound, so the "to" day
     is included); size fixed at 20; URL page `n` → API page `n - 1`. Changing any filter resets the page.
   - Tests (`runFilters.test.ts`): round trip, repeated params, invalid values reported, the inclusive "to" day,
     `from > to`, page conversion.

3. **Runs list data (`src/runs/useRunList.ts`).** `useRunList(query)` → `{page, loading, error, refetch}`:
   - Loads with `api.runs.listRuns(query, signal)`, aborting the previous request when the query changes or on
     unmount; keeps the previous page visible while a refetch is in flight (`loading` drives a `LinearProgress`).
   - Live updates through `useRunEvents`:
     - `run` for a row on the current page → `mergeRunEvent` in place (status, timestamps, error change at once).
     - `run` for an unknown id while on API page 0 → one trailing refetch debounced by 300 ms (a burst of events from
       an `ALL` trigger becomes one request). On later pages new runs are not pulled in (rows would shift); a
       "New runs available — go to first page" action appears instead.
     - `step` for a row on the page → `mergeStepSummary` (step badges move live without a request).
     - `reconnected` → refetch (no replay).
     - An event that arrives while a request is in flight marks the result stale and triggers one more refetch after
       it settles, so a response that predates the event never overwrites it.
   - Tests (`useRunList.test.tsx` through a small harness component, fake timers, `TestApiProvider`, a fake
     `RunEventsContext` as in `RunsPage.test.tsx`): initial load and abort on query change; run event patches a row
     without a request; unknown run on page 0 refetches once for a burst; unknown run on page 2 shows the
     "new runs" flag and does not refetch; step event updates badges; reconnect refetches; event during an in-flight
     request causes one follow-up request.

4. **Runs table (`src/runs/RunsTable.tsx`, `src/runs/StepBadges.tsx`, `src/runs/RunStatusChip.tsx`).**
   - Columns: Created (local date-time), Source, Trigger (`Manual · ana`, `Scheduled`, `Retry`; `requestedBy` for
     manual runs, `force` shown as a small "forced" chip), Scope (`scopeLabel`, full list in a `Tooltip`), Duration
     (active rows tick every second with `elapsedMs` from one shared `useNow(1000)` timer that runs only while an
     active row is on screen), Steps (`StepBadges`), Outcome (`RunStatusChip`; for `FAILED`/`PARTIAL` the error code
     next to it and the message in a tooltip).
   - `StepBadges`: one small chip per `STEP_ORDER` kind — outlined "not started" when absent, otherwise coloured by
     step status with a spinner icon for `RUNNING` and `×n` when `attempt > 1`; each chip has an `aria-label` such as
     "Import: failed, attempt 2".
   - A row is a link to `/runs/:id` (keyboard reachable: the Created cell is a `Link`; the whole row is clickable
     for the mouse) and passes `state.backTo` = the current `location.search`.
   - `TablePagination` (20 per page, rows-per-page selector hidden) bound to the URL page; empty states "No runs
     yet." (no filters) and "No runs match these filters." with a Clear filters button.
   - Tests in `RunsPage.test.tsx` (step 8).

5. **Filter bar (`src/runs/RunFilterBar.tsx`).** Source multi-select (`RFETM`, `BCNESA`, `FCTT`), status
   multi-select (statuses grouped under "Active" and "Finished"), From and To `TextField type="date"` (no date-picker
   dependency), Clear filters. Every change writes the URL with `setSearchParams` (replace, not push, for typing in
   date fields); invalid ranges show a field error and keep the last valid result on screen.

6. **Live activity log (`src/runs/useRunActivity.ts`, `src/runs/RunActivityLog.tsx`).** The "live log" for the
   running run(s), built from the event stream:
   - `useRunActivity({runId?})` subscribes with `useRunEvents` and keeps the last 200 entries (newest first) of
     `{receivedAt, runId?, source, severity, text}` in component state; with `runId` it keeps only that run's
     entries (pending-trigger events are kept when their `runId` matches).
   - Entry rules, so repeated `run` events do not flood the log: a `run` event is logged only when its `status`
     differs from the last status seen for that run in this log (first sight logs "RFETM run queued/started …");
     `FAILED` / `PARTIAL` include `error.code: error.message`. A `step` event logs
     "Ingest started (attempt 2)", "Fetch package succeeded — <outcome>", "Import failed — CODE: message" and is
     deduplicated on `(runId, kind, attempt, status)`. `pending-trigger` logs "Trigger queued by ana",
     "Queued trigger launched run …" (link) and "Queued trigger dropped — CODE". `reconnected` logs a warning entry
     "Connection restored; events sent while disconnected are not shown, the data was reloaded."
   - `RunActivityLog`: a bordered, scrollable (`max-height`), monospace `role="log"` list with `aria-live="polite"`,
     each line `HH:mm:ss SOURCE text`, run ids rendered as links to the run; a caption "Showing events received
     since this page was opened." (no replay); when `useEventConnection().state` is not `open`, an inline
     `Alert severity="warning"` "Live updates paused — reconnecting…" (or "stopped" after a `401`).
   - Tests (`useRunActivity.test.tsx`): status-change-only logging, step dedup, run filter, 200-entry cap,
     pending-trigger texts, reconnect entry, connection warning rendering.

7. **Run now dialog (`src/runs/runNowForm.ts`, `src/runs/RunNowDialog.tsx`).**
   - `runNowForm.ts` (pure): form state `{source: PipelineSource | 'ALL' | '', season, scopeType: ScopeType | '',
     filters: FilterDraft[], force}` with `FilterDraft` = the six fields as strings (`matchDays` as
     `"3, 4"`); `validateRunNow(state)` → field errors mirroring the server rules listed in Contracts (source and
     scope type required, no preselection; season pattern and consecutive years; `GROUP` → single source and at
     least one filter, each with at least one field; `matchDays` a comma list of positive integers; filters only for
     `GROUP`); `toTriggerRequest(state)` trims values, drops blank fields (sent as absent, not `""`), parses
     `matchDays`, omits `filters` unless `GROUP`.
   - `RunNowDialog` (MUI `Dialog`, `aria-labelledby` title "Run now"): Source radio group (RFETM, BCNESA, FCTT,
     All sources); Season free-text `Autocomplete` (`freeSolo`) whose options are the distinct seasons of the runs
     currently loaded — suggestions only, the field starts empty; Scope radio group (Open match days, Group, Full
     season) with "Group" disabled while "All sources" is selected (helper text says why); a filter editor shown only
     for Group (one row of six fields by default, Add filter / Remove buttons); Force checkbox "Ignore the ingest
     no-change check" (matches the API description of `force`). Cancel and Start run buttons; Start run is disabled
     and shows a progress indicator while submitting, and the dialog cannot be dismissed while submitting.
   - Submission (`api.runs.triggerRun`):
     - `201` / `202`: close the dialog and report through the page (step 8) with the per-source `results`.
     - `409` / `422`: keep the dialog open and show an error `Alert` whose title is the problem `detail` and whose
       body lists each result: `RFETM — Source RFETM already has an active run` with a link "Open active run" to
       `/runs/{activeRunId}` when present, and the code for `422` (`NO_OPEN_MATCH_DAYS`, …).
     - `400`: map `problem.field` (`season`, `filters`, `scopeType`, `source`) to that field's error text; other
       `400`s go to the dialog alert.
     - `403`: dialog alert with the server message (the button is already gated, but the server is authoritative).
     - Network error (`status 0`): dialog alert with `ApiError.message`; nothing is retried.
   - Tests (`runNowForm.test.ts`; `RunNowDialog.test.tsx` with `userEvent`): validation table (missing fields, bad
     season, non-consecutive years, Group + All, Group without filter, empty filter, bad match days); request built
     for each scope type with trimmed values and no blank fields; `201` closes and reports; `202` closes and reports
     queued; `409` shows the message and the active-run link and keeps the entered values; `422` shows the code;
     `400` with `field: "season"` marks the season field; submit disabled while pending.

8. **Runs page (`src/pages/RunsPage.tsx`, replaces the placeholder).** Heading "Runs", the Run now button in
   `<Can capability="trigger-runs" mode="disable">` opening `RunNowDialog`, `RunFilterBar`, a "Live activity"
   section (`RunActivityLog` without `runId`, expanded by default when an active run is on the page, collapsible),
   then `RunsTable`. After a `201`/`202` the page shows a dismissible `Alert` summarising every result
   (`success` when all created, `info` for queued, `warning` when `ALL` mixed created and rejected/unavailable), with
   links to created runs; the table updates through the event stream and is refetched once if on page 0. Load errors
   use the existing error `Alert`; URL filter errors show a warning `Alert`.
   - Tests (`RunsPage.test.tsx` rewritten; `TestApiProvider`, fake `RunEventsContext`, `MemoryRouter`):
     columns render trigger, scope, duration, step badges and outcome for representative runs (full season, group
     filters, failed with error code, active with a running step); newest-first order is the API order; filters from
     the URL reach `listRuns` (sources, statuses, `from`/`to` instants, page) and changing a filter updates the URL
     and resets the page; `from > to` sends no request; live `run`/`step` events update a row and the activity log;
     an active row's duration ticks with fake timers; Run now disabled without `matches:write`, opens the dialog with
     it; a successful trigger shows the results alert; a row click navigates to the detail with `backTo`.

9. **Run detail page (`src/runs/useRunDetail.ts`, `src/pages/RunDetailPage.tsx`, replaces the placeholder).**
   - `useRunDetail(runId)` → `{run, loading, error, notFound, refetch}`: loads `api.runs.getRun`; `404` (and `400`
     for a malformed id) sets `notFound`. Live: a `run` event for this id merges the summary fields; when it reports
     a terminal status the detail is refetched once (artifacts, import report and issues only come from the GET);
     a `step` event for this id upserts the step by `(kind, attempt)` in `STEP_ORDER` then attempt order;
     `reconnected` refetches; events received during a request trigger one follow-up refetch (same rule as step 3).
   - Page layout: back link "All runs" to `/runs` + `state.backTo` (falls back to `/runs`); header with source,
     season, `RunStatusChip`, trigger and requester, forced flag, created / started / finished, live duration while
     active; a links row for `retryOfRunId` (to that run), `ingestRunId` and `importJobId` (plain text with copy
     button — they are ids of other services); Scope (`Full season` or a filter table); Steps table (kind, attempt,
     status, started, finished, duration, external ref, outcome, retryable, error code and message) — every attempt,
     not only the latest; Issues (list, or "No issues"); Artifacts (kind, size, created, `sha256` in monospace with
     ellipsis and full value in a tooltip, or "No artifacts"); Import report (the ten counters as a definition grid
     with `status` and `receivedAt`; `null` shows "No import report yet." while active and "No import report." once
     terminal); and the "Live activity" `RunActivityLog` for this run while it is active or has entries. The Run now
     button is not repeated here.
   - Not found: "Run not found" with the back link; other errors: error `Alert` with a Retry button.
   - Tests (`RunDetailPage.test.tsx`): renders steps (two attempts), issues, artifacts and import report; null
     report texts for active and terminal runs; `404` shows not found; live `step` event adds a step row and live
     `run` event changes the status chip; terminal `run` event refetches and shows the import report; reconnect
     refetches; back link keeps the list filters.

10. **App wiring and existing tests.** Routes in `src/App.tsx` stay (`runs`, `runs/:runId`, both lazy). Update
    `src/test/renderApp.tsx` `stubBackends` to answer `GET /api/pipeline/runs/{id}` separately from the list, and
    keep `App.test.tsx` passing (its `/runs/abc` route expectation changes to the not-found or loaded detail text).

11. **Documentation.**
    - Module `README.md`: Runs screen (columns, URL filter parameters and the inclusive "to" day, live updates and
      their limits — no replay, events only since the page was opened, refetch on reconnect), run detail sections,
      and the Run now dialog (fields, client-side rules mirroring the API, how `201`/`202`/`409`/`422` are shown).
    - Module `AGENTS.md`: add "run list and detail state follow the event stream through `src/runs/` hooks; screens
      never open their own event connection" and "client-side trigger validation mirrors `TriggerRules` /
      `PipelineRun.requireValidSeason` and never replaces the server's".

12. **Validation.** In the module: `npm run typecheck`, `npm run lint`, `npm test`, `npm run build` (Runs and Run
    detail stay separate chunks). Then `mvn -pl tt-league-pipeline-orchestrator-frontend -am test` and the full
    `mvn test` (report the known unrelated `tt-data-league-import` failure if it still occurs). Manual smoke with
    platform (8080) and orchestrator (8095) running: trigger a run from the dialog, watch the row's step badges and
    the activity log move, open the detail while it runs, trigger again to see the `409` message with the active-run
    link, and filter by source/status/date. Review the diff for `dist/`, `node_modules/`, `target/` or `.env` files.

## Acceptance Criteria

- [x] A runs table (newest first) shows trigger, scope, duration, step badges and outcome, with filters by source, status and date
- [x] A run detail shows steps, issues, artifacts and the import report, and updates live while the run is active
- [x] A Run now dialog (source or all, scope type, force) creates a manual run and shows the 409 message when one is active
- [x] Tests cover table rendering, live updates and the dialog

# Implementation Guidelines

Follow the root and module `AGENTS.md` files.

- Frontend only. No backend, platform, POM or `package.json` change; no new runtime or dev dependency (no
  date-picker, data-fetching, table or state library). MUI, React Router and the FEAT-00109 client are enough.
- Use the existing client modules and types; if a DTO turns out to differ from `src/api/types.ts`, fix the type in the
  same change rather than casting.
- One event connection per tab: screens only subscribe through `useRunEvents`; never open a second stream.
- The browser never invents data: no default source, season or scope in the dialog; the season field only suggests
  seasons already seen. Durations of active rows are computed from `startedAt` and the local clock, everything else
  comes from the API or the event payloads.
- Client validation mirrors `TriggerRules` and `PipelineRun.requireValidSeason` for fast feedback only; the server's
  `400`/`409`/`422`/`403` answers are always shown and never treated as success.
- Explicit failure: no swallowed request errors, no automatic retries of a trigger, no success message unless the
  response was `201`/`202`. The only broad catch stays the event provider's per-listener isolation.
- Keep pure logic (formatting, filter parsing, merge rules, form validation) in plain `.ts` modules with unit tests;
  keep contexts, hooks and components in separate files (`react-refresh/only-export-components`).
- Accessibility: status and step badges carry text labels (not colour only), the activity log is `role="log"`, the
  dialog is labelled and keyboard operable, table rows are reachable by keyboard.
- Out of scope: the calendar and match-day detail (FEAT-00111), statistics (FEAT-00113), replaying an import from a
  run's artifacts (FEAT-00114), cancelling or retrying a run from the UI (no API exists), a server-side log stream,
  a pending-triggers management view, downloading artifacts (no API exists) and any backend change.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal UI view 3.

2026-10-05: plan built (status `idea` -> `planned`). Decisions made while planning, for the user to confirm before
`ready`:
- **"Live logs" are an in-browser activity log built from the SSE events.** The orchestrator has no log endpoint and
  the stream has no replay, so the log shows run status changes, step starts/ends with outcome or error, and
  pending-trigger changes received since the page was opened. A server-side log stream would need a backend feature.
- **Run detail is the existing `/runs/:runId` page**, not a drawer (the draft outline said "drawer"): it is deep
  linkable, already routed and lazy-loaded by FEAT-00109, and the list keeps its filters through `state.backTo`.
- **List filters live in the URL** (`source`, `status`, `from`, `to` as local dates, `page`); the "to" date is
  inclusive in the UI and sent as the next day's start because the API bound is exclusive.
- **Live list updates patch rows in place** from `run`/`step` events and refetch only for unknown runs on the first
  page (debounced) and after a reconnect; later pages show a "new runs available" action instead of shifting.
- **Run now season** is a required free-text field with suggestions from loaded runs and no default, because the API
  and AGENTS rules forbid a default season and no "current season" endpoint exists.
- **Group scope** gets a small filter editor (the six `ScopeFilter` fields), enabled only for a single source.
- A `201` from `ALL` can mix created and rejected sources, so results are always shown per source.

Open questions before `ready`: none blocking. Confirm the in-browser activity log is an acceptable reading of
"live logs", and that a detail page (instead of a drawer) is preferred.

2026-10-05: user confirmed the in-browser activity log, the detail page instead of a drawer and the season field
without a default; effort changed from medium to large; plan approved, status `planned` -> `ready`.

2026-10-05: implementation delivered (status `ready` -> `in-progress` -> `in-review`). Frontend only, no new dependency.
- `src/runs/` holds the pure modules (`runStatus`, `format`, `runFilters`, `runNowForm`), the hooks (`useRunList`,
  `useRunDetail`, `useRunActivity`, `useNow`) and the components (`RunsTable`, `StepBadges`, `RunStatusChip`,
  `RunFilterBar`, `RunActivityLog`, `RunNowDialog`); `RunsPage` and `RunDetailPage` replace the placeholders.
- `TriggerRunRequest.filters` is now `Partial<ScopeFilter>[]` so blank filter fields are sent as absent.
- Test support: `src/test/eventBus.ts`, `FakeEvents.tsx`, `runFixtures.ts`; `renderApp` stubs answer the detail GET
  separately. `vitest.config.ts` `testTimeout` raised to 20 s: MUI dialogs driven by userEvent exceeded 5 s under
  parallel load (also hit unrelated LoginPage/App tests).
- Validation: `npm run typecheck`, `lint`, `test` (230 tests), `build` (Runs and Run detail remain separate chunks) and
  `mvn -pl tt-league-pipeline-orchestrator-frontend -am test` pass. The manual smoke against running platform and
  orchestrator (step 12) was not performed; do it before `done`.

