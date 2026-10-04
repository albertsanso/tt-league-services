# Build Plan

Redesign the season calendar page (`/calendari`, delivered by FEAT-00092) as a FullCalendar-based
component with day, week, month and jornada views, a Calendar Detail list, filtering by
competition/group/team and sorting by date/time/venue. Day/week/month views span every competition of a
source and season, so they are served by a new date-range calendar read; the jornada view keeps using the
existing per-competition `GET /api/v1/match/calendar`.

## Acceptance Criteria
- [ ] The calendar component should display matches in the selected timed view (day, week, jornada, month).
- [ ] Users should be able to navigate between different timed views and select specific dates or jornadas.
- [ ] The calendar should support filtering and sorting of matches based on competition, group, or team.
- [ ] The Calendar Detail view should display a list of matches for the selected date or jornada, with relevant match information.
- [ ] Users should be able to click on a match in the Calendar Detail view to view more details about the match and navigate to the match detail page.
- [ ] The calendar component should be responsive and adapt to different screen sizes, including mobile devices.
- [ ] The calendar component is implemented in the existing React JavaScript/JSX module, keeps its view/date/filter state in URL search params (no new global state library), and uses date-fns for date calculations.
- [ ] The calendar component should be built using the FullCalendar library and should leverage its features and API for implementing the required functionality.
- [ ] A source- and season-scoped date-range calendar API returns matches of every status in a bounded date range, with the same calendar states (FEAT-00092 rules) as the jornada calendar.
- [ ] The "mark as overdue" action is available and visible only from the day after a SCHEDULED match's date (Europe/Madrid), enforced by the backend and exposed as `overdueMarkable`; the overdue grace period starts counting on that same day after the match date.

## Baseline (verified 2026-09-29, after commit b2c2d12 / FEAT-00092)

- Frontend `tt-data-league-frontend`: React 19 + Vite, **JavaScript/JSX** (no TypeScript), Vitest +
  Testing Library, Tailwind 3 + `src/index.css`/`src/app.css`, i18n `ca`/`en`/`es`, `lucide-react`. No
  calendar, date or state-management library. Global UI state is `AppStateProvider`/`useAppState`
  (module `AGENTS.md` forbids a second global state mechanism); page state lives in URL search params.
- `src/pages/SeasonCalendarPage.jsx` (`/calendari`, `matches:read`): source → season → competition
  selectors (`getMatchOptions`), optional group/round in the URL, one card per group with progress, rounds
  as lists, state badges (`STATE_TONES`), overdue mark/clear buttons for `matches:write`, links to
  `routePaths.matchSummary(id, params)` for PLAYED rows.
- `src/api/matches.js`: `getSeasonCalendar` + `normalizeCalendar`, `markMatchOverdue`,
  `clearMatchOverdueMark`. `src/hooks/useMatches.js`: private `useRequest(request, enabled, identity)`
  (abort, stale-response guard, `retry`) and `useSeasonCalendar`.
- Backend: `GET /api/v1/match/calendar` (`MatchController.calendar`) → `FindSeasonCalendarQuery` /
  `FindSeasonCalendarQueryHandler` (bean in `SeasonCalendarConfiguration`, not `@Named`) →
  `SeasonCalendarReadModel` / `CalendarGroupReadModel` / `CalendarRoundReadModel` /
  `CalendarMatchReadModel`; `SeasonCalendarDto`. Competition is required.
- `CalendarStateResolver.resolve(match, currentRound, overdueMarked, today, grace)` is the only state
  rule; `currentRound` comes from `RoundProgressCalculator`. `MatchRepository.findRoundProgress(source,
  season)` already returns `RoundProgress(source, season, competition, groupNumber, phase, currentRound,
  lastCompleteRound, …)` for every competition of a season.
- `match_record` stores `match_date` (`LocalDate`) and `match_time` separately; indexes do not cover
  `(source, season, match_date)`.
- `MatchRepository` implementors: `MatchRepositoryJpa`, `InMemoryRepositories.Matches`, and anonymous
  stubs in `IncrementalPreviewServiceTest` and `SnapshotReconcilerTest` (import tests).
- `GET /api/v1/match/{id}` (`FindMatchDetailsQueryHandler`) returns details with `status`, so a
  SCHEDULED match can be opened on `MatchSummaryPage` (verify rendering in step 11).

## Contracts

1. **Match repository port.** `MatchRepository.findMatchesBySourceSeasonAndDateRange(ImportSource source,
   Season season, LocalDate fromInclusive, LocalDate toExclusive)` → `List<Match>`: abstract, all
   arguments required (`NullPointerException`), `fromInclusive` must be before `toExclusive`
   (`IllegalArgumentException`), always source-scoped, returns matches of **every** status whose
   `match_date` is in `[from, to)` (undated matches never included), read-only. Javadoc states it is a
   calendar read and must not feed statistics (FEAT-00079 contract), like
   `findMatchesBySourceSeasonAndCompetition`.
2. **JPA.** `MatchRepositoryHelper.findAllBySourceAndSeasonAndMatchDateRange(Source, String, LocalDate,
   LocalDate)` (`m.matchDate >= :from and m.matchDate < :to`, ordered by date, time, competition, group,
   round) and `MatchRepositoryJpa` mapping through `matchJPAToMatchMapper`. New index
   `idx_match_source_season_date` on `(source, season, match_date)` in `MatchJPA`'s `@Table` (created by
   `ddl-auto: update`). No column, FK or cascade change.
3. **Calendar match read model (additive).** Append to `CalendarMatchReadModel`: `String competition,
   Integer groupNumber, String phase, int round, UUID homeTeamId, UUID awayTeamId`. Filled by both
   handlers; `SeasonCalendarDto.CalendarMatchDto` exposes them (additive JSON fields, existing fields
   unchanged).
4. **Shared assembly.** New `application/match/calendar/CalendarMatchAssembler` (final, constructed with
   `MatchOverdueMarkRepository`, `OverdueGracePeriod`): `marksFor(List<Match>)` (one `findByMatchIds`
   call with SCHEDULED ids only), `state(match, currentRound, marks, today)` and
   `toReadModel(match, state, marks)`. `FindSeasonCalendarQueryHandler` is refactored to use it with no
   behavior change (its existing tests stay green unchanged, apart from the new fields).
5. **Range query (core-domain, `application/match/calendar/range/`).**
   - `FindCalendarRangeQuery(ImportSource source, Season season, LocalDate from, LocalDate to,
     String competition, Integer groupNumber, UUID teamId)`: source/season/from/to required;
     `from < to`; `to - from <= FindCalendarRangeQuery.MAX_RANGE_DAYS` (62, covers a 6-week month grid);
     `competition` null or non-blank; `groupNumber` only with a competition; otherwise
     `IllegalArgumentException`.
   - Read models (`range/dto/`): `CalendarRangeReadModel(ImportSource source, Season season,
     LocalDate from, LocalDate to, LocalDate today, int overdueGraceDays,
     List<CalendarMatchReadModel> matches, CalendarRangeFacets facets)`;
     `CalendarRangeFacets(List<String> competitions, List<Integer> groups, List<CalendarTeamFacet> teams)`;
     `CalendarTeamFacet(UUID teamId, String name, String competition)`.
   - `FindCalendarRangeQueryHandler(MatchRepository, MatchOverdueMarkRepository, OverdueGracePeriod,
     Clock)` (**not** `@Named`; bean in `SeasonCalendarConfiguration`):
     1. `matches = findMatchesBySourceSeasonAndDateRange(source, season, from, to)`;
     2. `currentRound` per `(competition, groupNumber, phase)` from `findRoundProgress(source, season)`
        (whole season, so POSTPONED is identical to the jornada view; rule not duplicated);
     3. facets from the range matches **before** filtering: all competitions (sorted); groups of the
        selected competition (nulls last); teams (distinct by id, sorted by name) of the selected
        competition or of all competitions — so options never shrink when a filter is applied;
     4. filters: competition equals, group equals, team = home or away team id;
     5. marks/state/read model via `CalendarMatchAssembler`, `today` in `Match.COMPETITION_ZONE`;
     6. order by `dateTime`, competition, group (nulls last), round, home team name, id.
     Empty result → success with empty `matches`. `IllegalArgumentException` → `failResponse`. Nothing
     written.
6. **REST.** `GET /api/v1/match/calendar/range?source=&season=&from=&to=&competition=&group=&team=` on
   `MatchController`, `matches:read`. `from`/`to` ISO dates (`to` exclusive), `team` a UUID. Parse or
   query validation failure → `400 "Invalid calendar range"`; handler failure → `500 "Calendar range
   failed"`. Response `CalendarRangeDto` (reuses `SeasonCalendarDto.CalendarMatchDto`), enums as strings,
   dates ISO.
7. **Frontend API/hook.**
   - `src/api/matches.js`: `getCalendarRange(filters, token, signal, onUnauthorized)` with
     `normalizeCalendarRange` (arrays `matches`, `facets.competitions`, `facets.groups`, `facets.teams`;
     invalid → `ApiError` 502). Optional params omitted when empty.
   - `src/hooks/useMatches.js`: `useCalendarRange(filters)` on `useRequest`, enabled when source, season,
     from and to are set.
8. **Frontend calendar component** (`src/components/calendar/`, reusable, no page coupling):
   - `calendarRange.js` (pure, date-fns, weeks start Monday): `rangeFor(view, anchorDate)` →
     `{ from, to }` (exclusive; month = FullCalendar's visible 6-week grid), `shift(view, anchorDate, ±1)`,
     `titleFor(view, anchorDate, locale)`; parses/validates `YYYY-MM-DD` (invalid → today).
   - `calendarEvents.js` (pure): `toEvents(matches)` → FullCalendar `EventInput` (`id`, `start` = match
     `dateTime`, `allDay` false, `title` "Home – Away", `classNames` `cal-state-<state>`,
     `extendedProps.match`); `sortMatches(matches, sort)` with `date` (dateTime, default), `time`
     (time of day in Europe/Madrid, then date) and `venue` (locale compare, nulls last, then dateTime).
   - `MatchCalendar.jsx`: wraps `@fullcalendar/react` with plugins `dayGrid`, `timeGrid`, `list`,
     `interaction`; props `view`, `date`, `events`, `visibleRange` (jornada), `compact`, `locale`,
     `onDateSelect(date)`, `onMatchSelect(match)`. `headerToolbar: false` (the toolbar is ours);
     keeps FullCalendar in sync via `getApi().changeView/gotoDate` in an effect. View mapping:
     day → `listDay`; week → `timeGridWeek` (`listWeek` when `compact`); month → `dayGridMonth` with
     `dayMaxEvents` and `moreLinkClick` → `onDateSelect`; jornada → custom `listJornada` (`type: 'list'`,
     `visibleRange` = round `firstDate`..`lastDate + 1`). `firstDay: 1`, `timeZone: 'Europe/Madrid'`,
     locale from i18n (`ca`/`es`/`en-gb`). Event content is a keyboard-focusable element with an
     accessible name (teams, time, state label).
   - `CalendarToolbar.jsx`: view switcher (real `<button>`s, `aria-pressed`), previous/today/next with
     accessible names, period title; jornada view shows "Jornada N" with previous/next over the group's
     rounds and a round `<select>`.
   - `CalendarFilters.jsx`: source, season, competition, group, team, sort selectors (`<label>`ed
     `<select>`s); options from `getMatchOptions` (source/season) and from the response facets (range
     views) or the season calendar response (jornada view).
   - `CalendarDetailPanel.jsx` (Calendar Detail view): heading for the selection (day, week, month,
     jornada), sorted list of matches with date/time, teams, result for PLAYED, venue/city, competition
     and group, state `Badge` (tones as today) and manual-overdue author/date; each row is a `Link` to
     `routePaths.matchSummary(id, params)` (every status); the FEAT-00092 mark/clear overdue buttons
     move here unchanged (same permission, busy, error and refetch behavior). Undated matches of a
     jornada are listed under a "Sense data" subheading.
9. **Page.** `SeasonCalendarPage.jsx` becomes the composition of the component, all state in URL params:
   `source, season, view (day|week|month|jornada; default week), date (anchor, default today),
   competition, group, team, sort (default date), round (jornada), selected (YYYY-MM-DD, optional)`.
   - Range views use `useCalendarRange` with `rangeFor(view, date)`; jornada view requires a competition
     (prompts for one otherwise), uses `useSeasonCalendar` without `round` and picks the round
     client-side (default: the group's `currentRound`, else first round), filtering by team client-side
     over `homeTeamId`/`awayTeamId`.
   - Detail selection: clicking a day/"+N" selects that day; clicking an event selects its day and focuses
     the row; with no selection the detail lists the whole visible period (or jornada). Switching view
     keeps `date` and filters and clears `selected`.
   - Group progress cards from FEAT-00092 remain in the jornada view (header above the calendar).
   - Responsive: `compact` = mobile/tablet flag from `useAppState` (verify the exact field name); on
     mobile the detail panel stacks below the calendar, on desktop it sits beside it.
   - Loading, empty, error (401 vs other) and retry states as in FEAT-00092.
10. **Styling.** FullCalendar v6 injects its CSS; theme it in `src/app.css` by setting `--fc-*` custom
    properties (border, page background, today background, event colors) from the design-contract
    tokens, plus `.cal-state-*` classes matching the `Badge` tones, in light and dark themes.
11. **Overdue mark window and grace start (added 2026-09-29).**
    - Domain: `CalendarStateResolver.canMarkOverdue(Match match, LocalDate today)` (both required,
      `NullPointerException`) is the single rule: true only for a SCHEDULED match with a date, and only
      when `today` is on or after the day after the match date in `Match.COMPETITION_ZONE`. Undated and
      PLAYED matches are never markable.
    - Grace period: `resolve` counts the grace period from the day after the match date
      (`graceStart = matchDate + 1`): `AWAITING_RESULT` from `graceStart`, `OVERDUE` from
      `graceStart + grace.days()` (with 7 days a match of the 3rd is overdue on the 11th; with 0 days,
      on the 4th). Same results as the FEAT-00092 formula; the start day is now explicit.
    - Read model (additive): `CalendarMatchReadModel.overdueMarkable` (last component), filled by
      `CalendarMatchAssembler.toReadModel(match, state, marks, today)` for both calendar handlers;
      `SeasonCalendarDto.CalendarMatchDto.overdueMarkable` exposes it in the jornada and range responses.
    - Command: `MarkMatchOverdueCommandHandler` rejects a match that is not markable today with
      `"A match can only be marked overdue from the day after its scheduled date"` (checked after the
      PLAYED rejection, before the idempotent re-mark). `MatchController.markOverdue` returns `409` with
      the handler's message instead of a fixed text.
    - Frontend: `CalendarDetailPanel` shows the mark button only for `matches:write` on a SCHEDULED row
      with `overdueMarkable`; the clear button stays visible whenever `overdueMarked`, so an existing
      mark can always be removed. `calendarPage.markConflict` (ca/en/es) mentions the day-after rule.

## Implementation order

1. **Port and implementors.** Add `findMatchesBySourceSeasonAndDateRange` to `MatchRepository`;
   implement in `InMemoryRepositories.Matches` (filter by source/season/date) and in the anonymous stubs of
   `IncrementalPreviewServiceTest` and `SnapshotReconcilerTest` (their existing style for unused methods).
2. **JPA.** Helper query, adapter method, `idx_match_source_season_date`. `MatchCalendarRangeJpaTest`
   (style of `MatchSeasonCalendarJpaTest`): matches of both statuses across two competitions inside the
   range; boundaries (`from` included, `to` excluded); decoys (other source, other season, outside range,
   undated) excluded; order; null args → NPE; `from >= to` → IAE.
3. **Read model + assembler.** Extend `CalendarMatchReadModel`, add `CalendarMatchAssembler`, refactor
   `FindSeasonCalendarQueryHandler`; update `FindSeasonCalendarQueryHandlerTest` for the new fields (all
   existing assertions unchanged) and add `CalendarMatchAssemblerTest` (single marks query with SCHEDULED
   ids only; mark ignored for PLAYED).
4. **Range query.** `FindCalendarRangeQuery` (+ validation test: from/to order, 62-day limit, blank
   competition, group without competition) and `FindCalendarRangeQueryHandler`.
   `FindCalendarRangeQueryHandlerTest` (stub repos, fixed clock, grace 7): two competitions in one week;
   state per match equals the jornada handler's state for the same data (POSTPONED uses whole-season
   progress even when the earlier jornada is outside the range; manual mark → OVERDUE with author);
   competition/group/team filters; facets unchanged by filters; ordering; empty range → empty list; one
   marks query; nothing written.
5. **Runtime wiring.** `@Bean FindCalendarRangeQueryHandler` in `SeasonCalendarConfiguration`; confirm
   the import runtime `AppTest` still starts (handler not `@Named`).
6. **REST.** `CalendarRangeDto`, `MatchController.calendarRange`; extend `SeasonCalendarDto` with the new
   match fields. `MatchControllerTest`: 200 shape (matches, facets, team ids), optional params, 400 for
   bad dates / `from >= to` / > 62 days / bad team UUID / invalid source, 500 on handler failure, 403
   without `matches:read`; existing calendar tests assert the new fields.
7. **Dependencies.** From `tt-data-league-frontend`: `npm install @fullcalendar/core @fullcalendar/react
   @fullcalendar/daygrid @fullcalendar/timegrid @fullcalendar/list @fullcalendar/interaction date-fns`
   (v6.1.x with React 19 peer support; standard MIT plugins only, no premium/scheduler plugins).
   `package-lock.json` updated by npm only; `npm run build` passes with the Node/npm pinned in `pom.xml`.
8. **Frontend API/hook.** `getCalendarRange`, `normalizeCalendarRange`, `useCalendarRange`.
   `src/api/matches.test.js`: query string, optional params omitted, required params, invalid shape → 502.
9. **Pure helpers.** `calendarRange.js` and `calendarEvents.js` with `calendarRange.test.js` (week starts
   Monday, month grid range, prev/next across year boundaries, invalid anchor → today) and
   `calendarEvents.test.js` (event mapping, state class, three sort orders incl. null venue and
   Europe/Madrid time of day).
10. **Components.** `MatchCalendar`, `CalendarToolbar`, `CalendarFilters`, `CalendarDetailPanel`.
    Tests: toolbar view switch/prev/next/today and accessible names; filters render facet options and
    report changes; detail panel sorting, links for PLAYED and SCHEDULED, overdue buttons only with
    `matches:write` on SCHEDULED rows, undated subsection. `MatchCalendar` gets a smoke test rendering
    events in jsdom (add a `ResizeObserver` stub to the existing test setup only if FullCalendar needs it).
11. **Page.** Rewrite `SeasonCalendarPage.jsx` and update `SeasonCalendarPage.test.jsx` (mock
    `MatchCalendar` to a simple list to keep tests deterministic): URL state round trip for every param;
    range views call `getCalendarRange` with the computed range; jornada view requires a competition,
    defaults to the current round and navigates previous/next; day selection narrows the detail list;
    team filter; overdue mark/clear behavior from FEAT-00092 preserved; loading/empty/error/retry. Check
    `MatchSummaryPage` renders a SCHEDULED match (no result) and add a test if missing.
12. **Styling and i18n.** `--fc-*` theme and `.cal-state-*` classes in `src/app.css` (light/dark); keys in
    `ca`/`en`/`es` for views, navigation buttons, filters, sort options, detail headings, "Sense data",
    jornada label and range-limit errors. Manual check with `npm run dev` at mobile (375px), tablet and
    desktop widths.
13. **Documentation.**
    - `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md`: `idx_match_source_season_date` and the
      date-range calendar read in "Repository lookup behavior".
    - `tt-data-league-api-runtime/README.md`: the range endpoint, parameters, 62-day limit, errors.
    - `tt-data-league-frontend/README.md`: calendar views, URL parameters, new dependencies.
14. **Validation.** `npm run lint`, `npm test`, `npm run build` in the frontend; `mvn test` from the root.
15. **Overdue mark window (contract 11).** `CalendarStateResolver.canMarkOverdue` and the explicit
    grace start; `overdueMarkable` in `CalendarMatchReadModel`, `CalendarMatchAssembler`, both handlers
    and `SeasonCalendarDto`; the too-early rejection in `MarkMatchOverdueCommandHandler` and the 409
    message pass-through in `MatchController`; `CalendarDetailPanel` visibility and `markConflict` copy.
    Tests: `CalendarStateResolverTest` (grace starts the day after with 1 grace day; mark window on the
    match day / day after; undated and PLAYED not markable; Europe/Madrid date; null args),
    `MarkMatchOverdueCommandHandlerTest` (match day and undated rejected, day after accepted),
    `CalendarMatchAssemblerTest` (`overdueMarkable`), `MatchControllerTest` (409 carries the reason),
    `calendarComponents.test.jsx` (button hidden while not markable) and `SeasonCalendarPage.test.jsx`
    fixtures. Docs: `tt-data-league-api-runtime/README.md` (mark window, `overdueMarkable`, grace start).

# Implementation Guidelines

- Affected modules: `tt-data-league-core-domain`, `tt-data-league-core-repository-jpa`,
  `tt-data-league-import` (test stubs only), `tt-data-league-api-rest`, `tt-data-league-api-runtime`,
  `tt-data-league-frontend`. Follow each module's `AGENTS.md`.
- Frontend stays JavaScript/JSX (two-space indentation, single quotes, no semicolons). No TypeScript,
  Redux or Zustand: view/date/filter/sort/selection state is URL search params; cross-cutting UI state
  stays in `AppStateProvider`.
- Use FullCalendar's API (views, `visibleRange`, `moreLinkClick`, `dateClick`, `eventClick`,
  `eventContent`, `getApi()`) rather than re-implementing grids. Use date-fns for range/shift/title math;
  do not mix in another date library.
- The frontend never derives calendar states, grace periods, current rounds or postponement; it renders
  `calendarState` from the API. Likewise it never computes the overdue mark window; it uses
  `overdueMarkable`. Sorting and the jornada-view team filter are presentation only.
- Every lookup stays source- and season-scoped. Team filtering uses team ids (season-specific `Team`),
  never names. No external ids on `FederatedClub`/`FederatedPlayer`.
- The range read is read-only and must never feed statistics, search or community counts.
- Out of scope: drag-and-drop rescheduling or any write besides the existing overdue mark; undated
  matches in day/week/month views (they appear only in the jornada view); cross-source or cross-season
  calendars; iCal export; FullCalendar premium plugins.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3) as "Automated per-jornada fetch
  and upload" (task T17, Slice 5, depends on FEAT-00087).
- 2026-09-29: Scope replaced by the user with "Calendar UI component redesign"; T17 (scheduled
  fetch/upload) is no longer tracked by this ID and needs its own feature if still wanted.
- 2026-09-29: Plan built. Decisions confirmed with the user: (1) keep the JSX module, URL-param state,
  FullCalendar + date-fns instead of TypeScript + Redux/Zustand — the related acceptance criterion was
  reworded accordingly; (2) add a source/season-scoped date-range endpoint so day/week/month views span
  all competitions, with competition/group/team as filters; jornada view reuses the FEAT-00092 API.
  Added an acceptance criterion for the range API. Dependency set to FEAT-00092; effort raised to large.
- 2026-09-29: Plan approved by the user; status moved to `ready`.
- 2026-09-29: Implementation started; status moved to `in-progress`.
- 2026-09-29: Implemented steps 1-14. Decisions and deviations found during implementation:
  (1) `@fullcalendar/react` `latest` is 7.x (different major, needs `temporal-polyfill`), so all FullCalendar
  packages are pinned to 6.1.21 (`@fullcalendar/react@6.1.21` supports React 19). (2) The `useAppState`
  viewport field is `viewport` (`mobile`/`tablet`/`desktop`); `compact` = `viewport !== 'desktop'`.
  (3) The month view uses `fixedWeekCount: false`, so the visible grid is exactly the weeks covering the
  month, which is what `rangeFor('month')` requests (max 42 days, within the 62-day limit).
  (4) The competition selector always uses the `getMatchOptions` competitions (not the range facets), so a
  selected competition never disappears from the options when the visited period has no matches for it;
  group and team options come from the range facets (jornada view: from the season calendar response).
  (5) Jornada view with several groups and no `group` param shows the first group (the group selector
  shows it selected); the group progress cards of FEAT-00092 are shown for the active group(s).
  (6) `STATE_TONES` and `fullCalendarView` live in `calendarEvents.js` (react-refresh lint rule).
  (7) `MatchSummaryPage` already rendered a SCHEDULED match; a regression test was added.
- 2026-09-29: Validation: `mvn test` from the root (416 frontend + all Java modules) passes; `npm run lint`,
  `npm test` (403 tests) and `npm run build` pass. Acceptance criteria verified by tests: views/navigation
  (`SeasonCalendarPage.test.jsx`, `calendarRange.test.js`), filters/sorting (`calendarComponents.test.jsx`,
  `calendarEvents.test.js`, `FindCalendarRangeQueryHandlerTest`), detail panel and match links
  (`calendarComponents.test.jsx`), range API (`FindCalendarRangeQueryHandlerTest`,
  `MatchCalendarRangeJpaTest`, `MatchControllerTest`). Not done: the manual `npm run dev` check at mobile
  (375px), tablet and desktop widths against a live backend (responsive behavior is implemented with the
  `compact` flag and CSS breakpoints but has only been verified through jsdom tests).
- 2026-09-29: Scope extended by the user during review: the "mark as overdue" action is available/visible
  only from the day after the match date, and the grace period starts counting that same day. Added
  contract 11, step 15 and an acceptance criterion. Decisions: (1) the window is enforced in the backend
  (`MarkMatchOverdueCommandHandler`, 409) and exposed as `overdueMarkable`, so the UI does not duplicate
  the date rule; (2) undated SCHEDULED matches can no longer be marked manually (no date to count from)
  — previously they could; (3) the clear action is unaffected; (4) the grace formula already counted from
  the day after the match, so state results are unchanged and only the code/docs make the start
  explicit. The FEAT-00092 details (done) were left untouched.
- 2026-09-29: Validation of step 15: `mvn test` passes for every Java module (domain, JPA, import,
  import runtime, REST, MCP, API runtime, GraphQL); calendar frontend tests (44) and eslint pass. The
  reactor's frontend step failed in `npm ci` with a Windows EPERM (-4048, locked `node_modules`) before
  running tests, so the full `mvn test` needs a re-run once the lock is released
  (`mvn test -rf :tt-data-league-frontend`).
