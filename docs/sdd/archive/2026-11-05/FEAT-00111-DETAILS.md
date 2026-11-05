# Build Plan
Paths abbreviate `tt-league-pipeline-orchestrator-core/src/main/java/org/cttelsamicsterrassa/data/pipeline/core` as
`<core>`, `tt-league-pipeline-orchestrator-runtime/src/main/java/org/cttelsamicsterrassa/data/pipeline/runtime` as
`<rt>` and `tt-league-pipeline-orchestrator-frontend/src` as `<fe>`. Test sources mirror them. Steps 1-5 are core,
6-12 runtime, 13-21 frontend, 22 documentation and validation. No platform module, platform REST contract,
`tt-league-ingest` code, root POM or frontend `package.json` change (no new dependency).

**Contracts used (read from the code on 2026-10-05).**
- `GET /api/pipeline/match-days?source&season&state&from&to&page&size` (FEAT-00107): `Page<MatchDaySummaryDto>` sorted
  by `firstDate` (undated last), competition, group, phase, round; `size` 1-200; `from`/`to` overlap the
  first-to-last match dates and exclude undated days. There is **no** competition or phase filter and no facet list.
- `MatchDaySummaryDto`: key (`source`, `season`, `competition`, `groupNumber`, `phase`, `round`), `firstDate`,
  `lastDate`, `windowEnd`, `graceDays`, `state` (`UPCOMING`/`OPEN`/`CLOSED`), close reason/by/at, `openedAt`,
  `lastRecomputedAt`, `matchCounts` per `TrackedMatchStatus` (ignored matches included) and `ignoredMatches`.
  `JpaMatchDayRepository.query` already reads the ignored count **per status** (`countByDayAndStatus`) and sums it.
- `GET /{id}` -> `MatchDayDetailDto(matchDay, matches, events)`; matches carry status, date, teams, `reportedAt`,
  `reportedRunId`, ignored by/at, but **no result** (the tracker never stores results). Events carry `kind`, `actor`,
  `occurredAt`, optional `runId`, `matchId` and `note`.
- Actions `POST /{id}/close`, `POST /{id}/reopen`, `PUT|DELETE /{id}/matches/{matchId}/ignore`, `POST /{id}/notes`
  return the updated detail; `409` `ILLEGAL_TRANSITION` / `STALE_MATCH_DAY`, `404` `MATCH_DAY_NOT_FOUND`. Every
  `POST`/`PUT`/`DELETE` under `/api/pipeline/match-days/**` needs `matches:write`. The frontend client
  `<fe>/api/matchDays.ts` already wraps all of them.
- There is **no** "refresh this group" endpoint. A `GROUP` run needs filters in the ingest vocabulary
  (`category`, `group`, `phase`, `territory`, `gender`, `matchDays`), which differs per source from the tracker key;
  the only mapping is the core `ScopeBuilder` + `SourceVocabulary` join with the ingest `match-days-status` report
  (FEAT-00108). RFETM units carry only the category, so an RFETM refresh covers the whole category.
- Platform `GET /api/v1/match/calendar` match items already expose `homeGamesWon`, `awayGamesWon` and
  `winnerTeamName`; the orchestrator's `PlatformCalendarMatch` reads only id, key, date, teams, `status` and
  `calendarState` (two constructor call sites: `HttpPlatformMatchGateway`, `ScriptedPlatformMatchGateway`).
- The SSE stream sends `run`, `step` and `pending-trigger` events; nothing is sent when the tracker recomputes or an
  operator acts on a match day.
- Frontend: `<fe>/pages/CalendarPage.tsx` is a placeholder routed at `/calendar`; capabilities `trigger-runs` and
  `operate-match-days` (both `matches:write`) exist in `<fe>/auth/permissions.ts`; `triggerRun` resolves for
  `201`/`202` and rejects `409`/`422` with an `ApiError` read by `triggerResults`.

**Completion colouring (decided here, computed on the server).** Counts exclude ignored matches ("active" counts).
- `FUTURE`: state `UPCOMING` (window not started, or undated).
- `HAS_OVERDUE`: otherwise, at least one active `OVERDUE` match (also on a manually closed day: the result is still
  missing; the operator ignores the match to clear it).
- `COMPLETE` ("all reported"): otherwise, every active match is `REPORTED` (a day whose matches are all ignored, or a
  closed day without matches, is complete).
- `IN_PROGRESS`: everything else (awaiting results, scheduled, or a closed day that still holds a postponed match).
- Entry text `reported / total` = active `REPORTED` / active matches.

1. **Core completion (`<core>/tracker/`).**
   - Enum `MatchDayCompletion {COMPLETE, IN_PROGRESS, HAS_OVERDUE, FUTURE}` and
     `TrackerRules.completion(MatchDayState state, Map<TrackedMatchStatus, Integer> activeCounts)` implementing the
     rules above (the single place for them).
   - `MatchDaySummary` gains `Map<TrackedMatchStatus, Integer> ignoredByStatus` (validated: never above the status
     count) and derived `activeCounts()`, `reportedCount()`, `totalCount()`, `completion()`; `ignoredCount()` stays
     (sum). Update its constructors in `JpaMatchDayRepository`, `InMemoryMatchDayRepository` (core test-jar) and
     `MatchDayQueryService.detail`.

2. **Core list filters and facets (`<core>/tracker/`, `<core>/tracker/port/`).**
   - `MatchDayQuery` adds `String competition`, `String phase` (exact match, optional, non-blank, max 255) and
     `boolean undated` (only undated days; `from`/`to` must then be null, otherwise `IllegalArgumentException`).
     Update every constructor call (controller, in-memory repository, tests).
   - `MatchDayRepository.facets(PipelineSource source, String season)` -> `MatchDayFacets(List<String> seasons,
     List<String> competitions, List<String> phases)`: distinct, sorted values; `seasons` honours only `source`,
     `competitions` and `phases` honour both (null = all); null phases are left out.

3. **Core refresh event (`<core>/tracker/`).** `MatchDayEventKind.REFRESH_REQUESTED` and
   `MatchDayActions.recordRefresh(UUID matchDayId, String actor, UUID runId /*nullable*/, String note)`: appends the
   event only (no state change, any state allowed); unknown id -> `MatchDayNotFoundException`.

4. **Core refresh service (`<core>/polling/MatchDayRefresh`).** Constructor: `MatchDayRepository`,
   `IngestStatusGateway`, `ScopeBuilder`, `TriggerRun`, `MatchDayActions`.
   `List<TriggerRun.Outcome> refresh(UUID matchDayId, boolean force, String actor, ConflictMode conflictMode)`:
   1. Load the day (`MatchDayNotFoundException`) and its non-ignored matches.
   2. `status.matchDayStatus(source, season)`; empty -> return `Outcome.Unavailable(source, NO_INGEST_STATUS, ...)`
      (same code and message as `TrackerOpenMatchDayScopeResolver`).
   3. `builder.build(source, season, List.of(new OpenMatchDay(day.key(), matches)), report)`; a
      `ScopeBuildException` -> `Outcome.Unavailable(source, e.code(), e.getMessage())` (`SCOPE_UNMATCHED`).
   4. `triggerRun.trigger(new Command(List.of(source), season, ScopeType.GROUP, build.runScope().filters(), force,
      RunTrigger.MANUAL, actor, conflictMode))` — new runs only through `TriggerRun`; the scope is this match day's
      round of its group (the whole category for RFETM).
   5. `Created` -> `recordRefresh(day, actor, run.id(), null)`; `Queued` -> `recordRefresh(day, actor, null,
      "Queued behind active run <id>")`; `Rejected`/`Unavailable` record nothing. Return the outcomes.
   Works for any match-day state (an operator may re-ingest a closed day). Wire it in `PollingConfiguration`, next
   to `TrackerOpenMatchDayScopeResolver`, reusing its `ScopeBuilder` bean.

5. **Core tests.** `TrackerRulesTest` completion table (every state x overdue/awaiting/scheduled/postponed/reported
   mix, ignored overdue does not count, empty open/closed day, manual close with overdue); `MatchDaySummaryTest`
   (active/reported/total counts, invalid ignored map); `MatchDayActionsTest` (`recordRefresh` with and without run,
   unknown id); `MatchDayRefreshTest` with `InMemoryMatchDayRepository`, a fake `IngestStatusGateway`, the real
   `ScopeBuilder` and a `TriggerRun` over the existing in-memory fakes: FCTT and BCNESA filters equal the
   `OPEN_MATCH_DAYS` filters for the same day, RFETM yields the category filter, created/queued record the event,
   rejected/no-status/unmatched record nothing; `InMemoryMatchDayRepository` gains the new query fields and facets.

6. **Migration `V6__match_day_refresh_event.sql`.** Replace the `match_day_event.kind` CHECK with one that adds
   `REFRESH_REQUESTED` (`ALTER TABLE ... DROP CONSTRAINT <name>` / `ADD CONSTRAINT`; take the generated name from
   V4 — PostgreSQL names the inline check `match_day_event_kind_check` — and assert it in the migration test). No
   other schema change; nothing references platform tables.

7. **Persistence (`<rt>/persistence/JpaMatchDayRepository`).** Predicates for `competition`, `phase` and
   `undated` (`firstDate IS NULL`); keep the ignored count per status from `countByDayAndStatus` instead of summing
   it; `facets` through three `select distinct` queries on `MatchDayJpaRepository` (sorted in SQL).

8. **Gateway results.** `PlatformCalendarMatch` gains nullable `Integer homeGamesWon`, `Integer awayGamesWon`,
   `String winnerTeamName`; `HttpPlatformMatchGateway.CalendarMatchBody` maps them (absent stays null, never a
   protocol error); `ScriptedPlatformMatchGateway` updated. `MatchDayTracker` ignores them (no result is stored).

9. **Read API (`<rt>/api/MatchDaysController`, `MatchDayQueryService`, DTOs).**
   - List: new params `competition`, `phase`, `undated` (`true` with `from`/`to` -> 400 `field: "undated"`).
   - `MatchDaySummaryDto` adds `completion`, `reportedMatches`, `totalMatches` (additive; `matchCounts` and
     `ignoredMatches` unchanged).
   - `GET /facets?source=&season=` -> `MatchDayFacetsDto(seasons, competitions, phases)`.
   - `MatchDayDetailDto` adds `runs: List<RunSummaryDto>` (no `steps`): the distinct run ids of its events and of its
     matches' `reportedRunId`, newest `createdAt` first, loaded with one new `PipelineRunRepository.findByIds(
     Collection<UUID>)` (JPA + in-memory) through `RunQueryService`/`RunDtoMapper`. These are "the runs that touched
     the match day": runs whose recompute changed it, that first reported one of its matches, or that an operator
     launched from it.
   - `GET /{id}/results` -> `MatchDayResultsDto(UUID matchDayId, LocalDate platformToday,
     List<MatchResultDto(matchId, platformStatus, homeGamesWon, awayGamesWon, winnerTeamName)>)`: a read-through of
     `PlatformMatchGateway.competitionCalendar(source, season, competition)` kept to the day's tracked match ids;
     never stored or cached. `GatewayException` -> `502` `ProblemDetail` with code `PLATFORM_UNAVAILABLE` (the
     gateway message already names a rejected API key). Unknown id -> 404.

10. **Refresh API.** `POST /api/pipeline/match-days/{id}/refresh` body `{ "force": boolean }` (optional, default
    `false`) -> same contract as `POST /api/pipeline/runs`: `201` (with `Location` of the run) / `202` / `409` /
    `422` with `TriggerResponse` results. Extract the outcome-to-response mapping of `RunsController.trigger` into a
    package-private `TriggerResponses` used by both controllers (no behaviour change for `/runs`). Conflict mode from
    `PipelineOrchestratorProperties.Triggers.conflictMode()`; actor from `CurrentUser.name`. Already covered by the
    `matches:write` rule for `POST /api/pipeline/match-days/**`.

11. **Match-day SSE event.** Event name `match-days`, payload `{source, season, matchDayId /*null after a
    recompute*/, cause: "RECOMPUTED" | "ACTION"}`.
    - Runtime interface `<rt>/events/MatchDayChangeListener` implemented by `RunEventBroadcaster` (publishes on its
      private pool like the other events).
    - `TrackerRecomputeDispatcher` calls it after a successful recompute whose `RecomputeOutcome` changed anything
      (add `RecomputeOutcome.hasChanges()`); failed or no-op recomputes send nothing.
    - `MatchDaysController` calls it after every successful action and after a refresh that created or queued a run.
    - Listener failures are logged, never thrown into the dispatcher or the request.

12. **Runtime tests.** `MatchDaysApiWebTest`: new filters and `undated` validation, facets, `completion`/counts in
    the summary, `runs` in the detail, results mapping and `502`, refresh `201`/`202`/`409`/`422`/`403`/`404`;
    `RunsApiWebTest` unchanged and still passing after the `TriggerResponses` extraction; `HttpPlatformMatchGatewayTest`
    result fields present/absent; `JpaMatchDayRepositoryTest` filters, undated, facets, ignored per status;
    `MatchDayTrackerMigrationTest` V6 accepts `REFRESH_REQUESTED` and still rejects unknown kinds;
    `TrackerRecomputeDispatcherTest` listener called only on changes; `RunEventBroadcasterTest` `match-days` payload.

13. **Frontend API (`<fe>/api/types.ts`, `matchDays.ts`, events).** `MatchDayCompletion`, the new summary fields,
    `MatchDayFacets`, `MatchDayResults`, `MatchResult`, detail `runs: RunSummary[]`, `REFRESH_REQUESTED` in the event
    kinds; `listMatchDays` gains `competition`, `phase`, `undated`; new `getMatchDayFacets`, `getMatchDayResults`,
    `refreshMatchDay(client, id, force)` (same `TriggerRunOutcome` contract as `triggerRun`). Add the
    `match-days` event (`MatchDaysEvent`) to `PipelineEvent`, the provider and `parseSse` handling. Extend
    `endpoints.test.ts`.

14. **Pure calendar modules (`<fe>/calendar/`).**
    - `calendarDates.ts`: ISO date strings (`YYYY-MM-DD`) handled as calendar dates (no time-zone shift), weeks start
      on Monday; `monthGrid(anchor)` (whole weeks covering the month), `weekDays(anchor)`,
      `visibleRange(view, anchor)` -> `{from, to}`, `shift(view, anchor, ±1)`, `todayIso()` (browser local date).
    - `completion.ts`: `completionColor` (`COMPLETE` success, `IN_PROGRESS` info, `HAS_OVERDUE` error, `FUTURE`
      default), labels ("All reported", "In progress", "Has overdue", "Future"), `entryLabel(summary)`
      (`TERCERA-masculino · G2 · 1a Fase · J3`, only set parts), `progressText` (`4 / 6`), postponed/ignored
      suffixes. It maps the server's `completion`; it never derives it.
    - `calendarFilters.ts`: URL state `?view=month|week&date=YYYY-MM-DD&source&season&competition&phase&state`;
      invalid values are dropped and reported (warning `Alert`, never replaced); missing `view`/`date` default to
      month and today (navigation only — no data default: no source or season is preselected).
    - `placeEntries(summaries, days)`: entries on their `firstDate` cell, API order kept.
    - Tests: `calendarDates.test.ts` (month starting Sunday/Monday, leap February, week across months/years),
      `completion.test.ts` (every completion colour/label, label parts, progress text), `calendarFilters.test.ts`
      (round trip, invalid values reported, defaults).

15. **Calendar data hooks (`<fe>/calendar/`).**
    - `useMatchDayCalendar(filters)` -> `{days, undated, loading, error, truncated, refetch}`: loads every page
      (`size` 200) of the visible range with the filters, sequentially, aborting on filter change or unmount; stops
      after 10 pages and sets `truncated` (warning "More than 2000 match days — narrow the filters"); loads the
      undated days (`undated=true`) with the same filters. Live: a `match-days` event whose source/season match the
      filters -> one refetch debounced by 500 ms; `reconnected` -> refetch; an event during a request triggers one
      follow-up (same rule as `useRunList`).
    - `useMatchDayFacets(source, season)` for the filter options.
    - Tests (`useMatchDayCalendar.test.tsx`, fake timers, `TestApiProvider`, `FakeEvents`): paging until total,
      truncation, abort on change, debounced refetch on matching events only, reconnect refetch.

16. **Calendar components (`<fe>/calendar/`).**
    - `CalendarToolbar`: previous / today / next, month-week toggle, period title.
    - `CalendarFilterBar`: source, season (facets), competition (facets, labelled "Category"), phase (facets), state;
      Clear filters. Changes write the URL (replace) and keep `view`/`date`.
    - `MatchDayCalendar`: month grid (`role="grid"`, weekday headers, outside-month days dimmed, today marked) with up
      to 4 entries per cell and "+N more" opening a dialog with the day's full list; week view: seven columns
      listing every entry.
    - `MatchDayEntry`: a link button coloured by completion that always shows text (`entryLabel` + `reported /
      total`) and icons for closed and postponed; `aria-label` with the completion label, state and counts; links to
      the detail with `state.backTo` = current search.
    - `CompletionLegend` and `UndatedMatchDays` (collapsible list under the calendar, hidden when empty).

17. **Calendar page (`<fe>/pages/CalendarPage.tsx`, replaces the placeholder).** Heading "Calendar", toolbar, filter
    bar, legend, calendar, undated list; load errors as error `Alert` with Retry; URL errors as warning `Alert`;
    empty range "No tracked match days in this period." (with Clear filters when filtered).
    - Tests (`CalendarPage.test.tsx`): filters from the URL reach `listMatchDays` (`from`/`to` of the visible month
      and week, source, season, competition, phase, state); changing a filter updates the URL; entries render
      label, `reported / total` and the completion label for each completion; "+N more" dialog; prev/next/today;
      week view; undated list; a `match-days` event refetches; entry click navigates with `backTo`.

18. **Match-day detail data (`<fe>/calendar/useMatchDayDetail.ts`, `useMatchDayResults.ts`).**
    - `useMatchDayDetail(id)` -> `{detail, loading, error, notFound, refetch, replace}`: `404`/`400` -> `notFound`;
      a `match-days` event for this id, or for its source/season with `cause: "RECOMPUTED"`, refetches; a `run` event
      for a run in `detail.runs` merges with `mergeRunEvent`; `reconnected` refetches; `replace(detail)` applies an
      action response.
    - `useMatchDayResults(id)` loads `/results` separately and again when `reportedMatches` changes; a failure shows
      a warning ("Results could not be read from the platform: …", Retry) and never blocks the page.
    - `timeline.ts` (pure): merges `events` and `runs` into one list newest first (runs at `createdAt`; events with
      a `runId` link to it), with a text per event kind (`REFRESH_REQUESTED` "Refresh requested by ana").

19. **Match-day detail page (`<fe>/pages/MatchDayDetailPage.tsx`, route `calendar/match-days/:matchDayId`, lazy).**
    - Back link "Calendar" to `/calendar` + `state.backTo`; header with the key, source, season, window
      (`firstDate – lastDate`, grace end), state chip (close reason, by, at), completion chip, `reported / total`,
      counts per status and ignored.
    - Matches table: date-time, home, away, status chip, result (`3 – 1` from `/results`, `—` when not played,
      "unavailable" when the read failed), reported at (link to the reporting run), ignored by/at, row actions
      Ignore / Stop ignoring and Add note.
    - Timeline from `timeline.ts`: kind, actor, time, note, run link with `RunStatusChip`.
    - Actions bar: "Refresh group" (`<Can capability="trigger-runs" mode="disable">`), Close / Reopen (by state),
      Add note (`<Can capability="operate-match-days" mode="disable">`).
    - `MatchDayActionDialog`: one labelled dialog for close, reopen, ignore, unignore (optional note, max 2000) and
      note (required text, "Applies to" the day or one match); submit disabled while pending; success replaces the
      detail; `409` (`ILLEGAL_TRANSITION`, `STALE_MATCH_DAY`) shows the server detail and refetches; `400` maps to the
      note field; `403`/`404`/network errors show an alert; nothing is retried.
    - `RefreshGroupDialog`: explains the scope ("this round of the group"; "the whole category for RFETM"), Force
      checkbox; `201`/`202` close and show a page alert with the per-source result and a link to the run; `409`/`422`
      keep the dialog open with the results list (extract the result list of `RunNowDialog` into a shared
      `runs/TriggerResultList.tsx` and use it in both).
    - Not found: "Match day not found" with the back link.
    - Tests (`MatchDayDetailPage.test.tsx`, `MatchDayActionDialog.test.tsx`, `timeline.test.ts`): matches with status,
      result and reported-at; results failure warning; timeline order and run links; each action sends the right
      request (close, reopen, ignore, unignore, note with and without match, refresh with force) and renders the
      returned detail; `409` message and refetch; refresh `202`/`409`/`422`; buttons disabled without
      `matches:write`; live `match-days` and `run` events; not found.

20. **App wiring.** Add the detail route in `<fe>/App.tsx`; `<fe>/test/renderApp.tsx` stubs answer the match-day list,
    facets, detail and results; `App.test.tsx` keeps passing (`/calendar` now renders the calendar).

21. **Frontend fixtures.** `<fe>/test/matchDayFixtures.ts` with summaries for every completion, a detail with
    reported, postponed, overdue and ignored matches, events of every kind and two runs.

22. **Documentation and validation.**
    - Runtime `README.md` "Match-day tracker": new list filters, facets, `completion`/counts, detail `runs`, results
      read-through and `502`, refresh endpoint and its scope per source, `match-days` SSE event ("Event stream").
    - `docs/pipeline-datamodel.md`: `REFRESH_REQUESTED` in `match_day_event`, V6 in the migration history.
    - Runtime `AGENTS.md`: controllers may also call `MatchDayRefresh` (runs still only through `TriggerRun`); the
      completion rule lives only in `TrackerRules`; results are read through, never stored.
    - Frontend `README.md`: Calendar screen (URL parameters, colours, live updates) and match-day detail (actions and
      their permissions); frontend `AGENTS.md`: completion comes from the server, calendar state follows the event
      stream through the `src/calendar/` hooks.
    - Run `mvn -pl tt-league-pipeline-orchestrator-core -am test`,
      `mvn -pl tt-league-pipeline-orchestrator-runtime -am test` with Docker running (V6, JPA and integration tests),
      in the frontend `npm run typecheck`, `lint`, `test`, `build` (Calendar and detail stay lazy chunks), then
      `mvn -pl tt-league-pipeline-orchestrator-frontend -am test` and the full `mvn test`. Manual smoke with platform
      and orchestrator running: month and week views, filters, open a detail, refresh a group and watch the run, close
      and reopen, ignore and note. Review the diff for `dist/`, `node_modules/`, `target/` or `.env` files.

## Acceptance Criteria

- [ ] A month/week calendar shows one entry per match day and group, filterable by source, season, category and phase
- [ ] Entries are coloured by completion (all reported, in progress, has overdue, future) and show `reported / total`
- [ ] The match-day detail lists matches with status, result and reported-at, and a timeline of the runs that touched it
- [ ] Operators can refresh just that group, close the match day, mark a match ignored and add a note
- [ ] Tests cover colouring rules, filters and each action

# Implementation Guidelines

Follow the root, core, runtime and frontend `AGENTS.md` files.

- The platform matches calendar (FEAT-00092/93) shows matches; this view shows pipeline completion. Do not duplicate
  overdue rules in the UI: statuses come from the tracker (which maps the platform), the completion category from
  `TrackerRules.completion`; the frontend only maps it to colours and labels.
- Results are a read-through of the platform calendar for the detail view only; never store, cache or log them in the
  orchestrator, and never add them to `match_tracking`.
- "Refresh group" creates runs only through `TriggerRun` with a scope built by `ScopeBuilder`/`SourceVocabulary`; the
  browser never builds ingest filters from a tracker key. No new `ScopeType`.
- Category in the UI is the tracker `competition` (for RFETM/FCTT `<category>-<gender>`); filters are exact matches on
  the stored values offered by the facets endpoint.
- No defaults for data: the calendar opens on the current month (navigation), but no source, season or category is
  preselected.
- One event connection per tab; calendar and detail subscribe only through `useRunEvents`.
- Explicit failures: no swallowed errors, no automatic retries of actions or refreshes, no success message unless
  the response was successful; a failed results read is shown, not hidden.
- Frontend: no new dependency (MUI month/week grid instead of FullCalendar), pure logic in `.ts` modules with unit
  tests, components and hooks in separate files; entries and chips carry text, not colour only.
- Out of scope: editing schedules or dates, marking matches overdue (platform), a manual recompute endpoint, drag
  and drop, notifications (FEAT-00112), statistics (FEAT-00113), replay (FEAT-00114) and any platform change.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal UI views 1 and 2.

## 2026-10-05 — Plan built (`idea` → `planned`)

The draft assumed a frontend-only feature with optional tracker read endpoints. FEAT-00107 delivered the list,
detail and action endpoints, but four acceptance criteria need backend additions; the plan adds them in the
orchestrator only (no platform change). Decisions to confirm before `ready`:

- **Category/phase filters on the server.** The list has no competition/phase filter; filtering in the browser would
  need every page of a range. Added `competition`, `phase` (and `undated`) to the list and a facets endpoint for the
  options.
- **Completion computed in core.** Colours need active (non-ignored) counts per status, which the summary does not
  expose, and the guideline forbids rule duplication in the UI; `TrackerRules.completion` owns the four categories.
  A manually closed day that still has an active overdue match stays `HAS_OVERDUE`; a closed day holding a postponed
  match is `IN_PROGRESS`.
- **Result is a platform read-through.** The tracker never stores results; `GET /match-days/{id}/results` reads the
  platform calendar (which already carries games won) on demand and fails visibly with `502`.
- **"Runs that touched it"** = runs referenced by its events or by `reportedRunId`, plus refreshes launched from it
  (new `REFRESH_REQUESTED` event, migration V6). Runs whose recompute changed nothing for the day are not listed.
- **Refresh group** reuses the FEAT-00108 `ScopeBuilder` so the filters match `OPEN_MATCH_DAYS` exactly; the scope is
  this round of the group (whole category for RFETM, whose scopes carry only the category).
- **Live calendar** through a new `match-days` SSE event after recomputes that changed something and after actions,
  instead of polling.
- **MUI calendar instead of FullCalendar**: no new dependency, testable pure grid logic; entries sit on their first
  date with "+N more" per cell; undated match days are listed under the calendar.
- Dependencies widened to FEAT-00108 (scope builder, ingest status gateway) and FEAT-00110 (run chips, trigger result
  list, merge helpers).

Open questions before `ready`: confirm the backend additions above (especially the result read-through and the V6
event kind), the colouring rules for manually closed and postponed days, and the MUI grid over FullCalendar.

## 2026-10-05 — Approved (`planned` → `ready`)

- User confirmed every decision above: the orchestrator backend additions stay in this feature (list filters and
  facets, server-side completion, results read-through, refresh endpoint, `REFRESH_REQUESTED` with migration V6,
  `match-days` SSE event), the colouring rules for manually closed and postponed days, the whole-category RFETM
  refresh, and the MUI grid instead of FullCalendar. Plan approved for implementation as written.

## 2026-10-05 — Implemented (`in-progress` → `in-review`)

Delivered as planned, in the orchestrator modules and its frontend only (no platform, ingest, root POM or
`package.json` change). Deviations and discoveries:

- `RecomputeOutcome` gained a `changed` count (window, status, date, team or membership differences) so that
  `hasChanges()` is false for a recompute that only refreshed timestamps; the `updated` counter alone counts every
  tracked day on every recompute and could not drive the `match-days` event.
- `PipelineRunRepository.findByIds` was added (JPA and in-memory) for the detail `runs`.
- Facets use the JPA Criteria API in `JpaMatchDayRepository` (an `EntityManager` is injected) instead of derived
  `@Query` methods, to avoid `:param is null` binding problems with enum parameters on PostgreSQL.
- A match day that started before the first visible calendar day is placed on the first visible day, so entries whose
  window overlaps the period are not hidden.
- Results, `PlatformUnavailableException` (`502`, `PLATFORM_UNAVAILABLE`) and `MatchDayResultsService` live in the
  runtime `api/` package; the tracker is read before the platform call so no transaction is held during the HTTP call.

Validation (2026-10-05):

- `mvn -pl tt-league-pipeline-orchestrator-core -am test`: passing.
- `mvn -pl tt-league-pipeline-orchestrator-runtime -am test`: 291 tests, 0 failures, **98 skipped because Docker is
  not available in this environment**. That includes the new `JpaMatchDayRepositoryTest` cases (competition, phase,
  undated, facets, ignored per status, `findByIds`) and the V6 case of `MatchDayTrackerMigrationTest`; they still
  need a run with Docker.
- Frontend `npm run typecheck`, `lint`, `test` (347 tests) and `build` (Calendar and match-day detail stay lazy
  chunks) pass; `mvn -pl tt-league-pipeline-orchestrator-frontend test` passes.
- The full `mvn test` currently stops in `tt-data-league-import`:
  `BcnesaImportProcessorsTest.storesTheSetScoresOfEveryGameFromTheHtmlBasedActas` fails with "Missing test fixture
  acta_bcnesa_2026_published.json" (the fixture is not in the repository). This is unrelated to this feature (no file
  of that module was touched) and hides the modules after it in the reactor, so the orchestrator modules were run
  with `-pl`.
- Not done: the manual smoke test with the platform and the orchestrator running (month and week views, filters,
  detail, refresh, close/reopen, ignore, note).
