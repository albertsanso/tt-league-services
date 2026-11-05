# Build Plan
All changes are in `tt-data-league-frontend`. No backend or API contract change: the existing per-source endpoints are called once per source and merged at the API boundary (see Notes for the decision).

1. **Shared source list.** Create `src/api/calendarSources.js` exporting `CALENDAR_SOURCES = ['RFETM', 'FCTT', 'BCNESA']`, and import it in `CalendarFilters.jsx` in place of the local `SOURCES` constant. Leave `MatchesSearchPage.jsx` and `useImportSourceStatus.js` alone.
2. **Options across sources.** In `calendarSources.js`, add `getCalendarOptions(source, season, token, signal, onUnauthorized)`:
   - With a source, call `getMatchOptions` once and return `{ seasons, competitions }` with the same array guards the page applies today.
   - Without a source, call `getMatchOptions` for every source through `Promise.all`, then return `seasons` as a de-duplicated list sorted descending and `competitions` as a de-duplicated list sorted ascending.
3. **Calendar range across sources.** In `calendarSources.js`, add `getCalendarRangeAcrossSources(filters, token, signal, onUnauthorized)`, which calls `getCalendarRange` once per source with the same filters and returns a single range-shaped object:
   - `source: ''`, with `season`, `from`, `to`, `today` and `overdueGraceDays` taken from the first response.
   - `matches`: concatenated, each tagged with its `source`, ordered by `dateTime` then `id` so the result does not depend on response order.
   - `facets.competitions`: de-duplicated and sorted. `facets.groups`: de-duplicated, sorted, `null` last. `facets.teams`: concatenated, each tagged with its `source`, sorted by name then `teamId`.
4. **Hook.** In `src/hooks/useMatches.js`, change `useCalendarRange` so that `enabled` is `Boolean(season && from && to)`, and call `getCalendarRange` when a source is set or `getCalendarRangeAcrossSources` when it is empty. The jornada view stays disabled for this hook because `rangeFor` gives no range there, so `from`/`to` are empty. `useSeasonCalendar` does not change: it still needs a source.
5. **Page defaults.** In `src/pages/SeasonCalendarPage.jsx`:
   - Derive `effectiveSeason = filters.season || options.seasons[0] || ''`, and do not write it to the URL. Use it for both calendar hooks, the options request, `requestedReady` and the `filters` passed to `CalendarFilters`.
   - Remove the `if (!filters.source) return undefined` guard and load options through `getCalendarOptions(filters.source, effectiveSeason, ...)`. The request runs twice on a first visit: once for the seasons, then again for the competitions of the derived season.
   - Start `loadingOptions` as `true`, because the effect now always runs, and clear `optionsError` when a request starts.
   - `requestedReady` becomes `Boolean(effectiveSeason)` for range views and `Boolean(filters.source && effectiveSeason && filters.competition)` for jornada.
6. **Filters.** In `src/components/calendar/CalendarFilters.jsx`:
   - The season select is disabled only while `loadingOptions` is true, and its blank placeholder renders only when there is no selected season.
   - Competition and team also become disabled when `!optionalScope && !filters.source`, so jornada asks for a source first.
   - The team label gains the source when the facet carries one: `${name} · ${competition} · ${source}`.
7. **Row source.** In `src/components/calendar/CalendarDetailPanel.jsx`, add `match.source` to the competition/group line when present, so rows from different federations can be told apart. Rows from a single-source request are unchanged.
8. **Status messages and translations.** In `SeasonCalendarPage.jsx` and `src/i18n/ca.js`, `es.js`, `en.js`:
   - Replace the `calendarPage.selectSeason` message with `calendarPage.noSeasons`, shown when options have loaded without error and there is no season.
   - Add `calendarPage.selectSourceForJornada`, shown in the jornada view when no source is selected.
   - Show `calendarPage.loading` while options load and no season is known yet.
   - Remove the unused `calendarPage.selectSeason` key from all three files. Leave the unrelated `selectSeason` key used elsewhere in those files untouched.
9. **Tests.**
   - New `src/api/calendarSources.test.js` (mocking `./matches.js`): one call per source, season and competition merge order, match order and `source` tagging, facet merge including `null` group, a single source failure rejecting the whole call, single-source passthrough.
   - `src/pages/SeasonCalendarPage.test.jsx`: replace `asks for a source and season before loading anything` with a test that `/calendari` requests all three sources for the newest season, and that the source select shows all sources and the season select that season without changing the URL.
   - Same file, add: explicit `?source=FCTT` requests only FCTT and defaults to its newest season; explicit `?season=` is respected; selecting a source from the default narrows the requests; competition/group/team filters reach every source; jornada without a source shows the new message and calls neither calendar API; no seasons shows `noSeasons`; a failing source shows the error state.
   - `src/components/calendar/calendarComponents.test.jsx`: cover the season select enabled with no source, and the source shown in a detail row and team option.
10. **Documentation.** In `tt-data-league-frontend/README.md`, section "Calendari de la temporada": `source` and `season` are no longer mandatory; document the defaults (all sources, newest season across them), that all-sources requests one calendar per source, and that the jornada view still needs a single source.
11. **Validation.** From `tt-data-league-frontend`, run `npm run lint`, `npm test` and `npm run build`. From the repository root, run `mvn -pl tt-data-league-frontend -am test`. Check in the running app that `/calendari` opens populated, that narrowing to one source and back works, and that the mark/clear overdue action refreshes an all-sources view.

# Implementation Guidelines
- Follow `tt-data-league-frontend/AGENTS.md`: merge and normalize at the API/hook boundary, and keep components display-only. The frontend still does not compute calendar states, deadlines or the current round; it only shows what each source's response contains.
- An empty `source` in the URL now means all sources. Keep the default derived: do not write `source` or `season` to the URL when they were not chosen, so the back link and "Today" behave as they do now and a shared `/calendari` link keeps following the newest season.
- All-or-nothing on failure: if any source's request fails, show the existing error state. Never render a partial all-sources calendar as if it were complete.
- Requests stay abortable: pass the same `signal` to every per-source call.
- Keep the existing behavior of picking a source (season, competition, group, team and round are cleared), which now falls back to that source's newest season.
- Out of scope: backend, REST, GraphQL or MCP changes; a jornada view across sources; `MatchesSearchPage`; removing the other duplicated source lists; a season-from-today-date rule.

# Notes
- Captured as `idea` (no build plan yet). Context: `SeasonCalendarPage.jsx` currently starts with `source=''`, and the season select stays disabled until a source is chosen (`CalendarFilters.jsx`). The `allSources` option only clears the source; it does not load a season across sources.
- Open questions before planning: how "current season" is determined (frontend constant, backend endpoint, or the latest season returned per source); whether the backend options/calendar endpoints must accept an all-sources query or the frontend must fan out per source and merge.
- Decision (current season): the newest season returned by `GET /api/v1/match/options` across all sources. This is the rule `FindCommunityStatisticsQueryHandler` already uses ("the most recent season with imported matches, across every source"), so no constant or new endpoint is needed. With an explicit source, the default is that source's newest season. A source that has not imported the newest season yet contributes no matches until it does.
- Decision (fan-out): call the existing per-source endpoints from the frontend and merge. `FindCalendarRangeQuery` requires a source and its handler's `GroupKey` has no source, so an all-sources backend query would have to change the query, handler, REST contract and tests. `src/api/clubs.js` already fans out with `Promise.all`. Cost: up to 6 options requests on a first visit and 3 range requests per view change.
- Decision (jornada view): it stays single-source. `GET /api/v1/match/calendar` needs one source and competition, and its rounds and group progress have no meaningful merged form. Without a source the view asks for one. Open for review: this narrows acceptance criterion 3 ("filters keep working") to the day, week and month views when all sources are selected.
- Known limitation: competition names and group numbers are not unique across sources. Selecting a competition name used by two federations in all-sources mode shows both, and each row shows its source so they can be told apart. Picking a source narrows it.
- Planned without changing acceptance criteria. The status stays `planned` until the plan is approved as `ready`.
- Plan approved and moved to `ready` on 2026-11-05, including the single-source jornada view: acceptance criterion 3 applies to the day, week and month views when all sources are selected.
- Implemented in `tt-data-league-frontend` (`calendarSources.js`, `useMatches.js`, `SeasonCalendarPage.jsx`, `CalendarFilters.jsx`, `CalendarDetailPanel.jsx`, i18n, README). Validation: `npm run lint`, `npm test` (421 tests) and `npm run build` pass. `mvn -pl tt-data-league-frontend -am test` could not be run in this environment because `mvn` is not installed; it is still to be run. The running-app check in build step 11 has not been done.
