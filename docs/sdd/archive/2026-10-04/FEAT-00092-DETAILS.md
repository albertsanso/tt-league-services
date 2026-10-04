# Build Plan
Source task: **T16** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Revision note 1; section 4.2 "Postponed and overdue are derived in the read model, not stored"; open question 4).

## Acceptance Criteria
- [x] API lists a season calendar per competition/group/jornada with match status
- [x] Derived postponed/overdue states are computed on read and never stored; only an explicit manual overdue mark is persisted
- [x] A SCHEDULED match becomes OVERDUE only after a configurable grace period (default 7 days) past its date
- [x] Users with `matches:write` can force and clear OVERDUE on a SCHEDULED match from the UI, and the mark records who set it and when
- [x] UI presents the calendar and jornada progress

## Baseline (verified 2026-09-29, after commit a0dc2a0 / FEAT-00091)

- `Match` carries `source`, `season`, `competition`, `groupNumber` (nullable), `round`, `phase` (nullable),
  `dateTime` (`ZonedDateTime`, nullable, zone `Match.COMPETITION_ZONE` = Europe/Madrid), `city`, `venue`,
  home/away/winner `Team`, games/sets won and `status` (`SCHEDULED` / `PLAYED`, FEAT-00077).
- Read side (FEAT-00079): statistics and corpus reads are PLAYED-only; `MatchSearchCriteria.status` is
  mandatory (default PLAYED, no "all statuses"). `MatchRepository.findAllSeasonsBySource` and
  `findAllCompetitionsBySourceAndSeason` are deliberately **unfiltered**, so they already serve calendar
  dropdowns for a season whose actas are all pending. FEAT-00079 explicitly left "showing SCHEDULED
  fixtures in lists or the season calendar UI" to this feature.
- Jornada progress (FEAT-00084): `RoundStatusCount`, `RoundProgress` and `RoundProgressCalculator` in
  `domain/match/model` define current round / last complete round per `(competition, groupNumber, phase)`
  within `(source, season)`. The rule lives only in the calculator.
- Existing whole-season read: `MatchRepository.findMatchesBySourceSeasonAndStatus` (FEAT-00086, one
  status at a time). There is no all-status read scoped by competition.
- `MatchRepository` implementors: `MatchRepositoryJpa`, `InMemoryRepositories.Matches` (import tests), and
  anonymous stubs in `IncrementalPreviewServiceTest` and `SnapshotReconcilerTest` (import tests).
- Matches are never deleted: `MatchRepositoryJpa` and `ScheduledMatchBackfillRepositoryJpa` delete only
  child rows (lineups, games, set scores, doubles pairs); consolidation re-points teams but keeps match ids.
  A foreign key from a new table to `match_record.id` is therefore safe.
- RBAC: `Permission` enum (core-domain `domain/auth/user/model`) with `UserRole` permission sets and
  `RbacCatalog` constants (api-rest). There is `matches:read` but no `matches:write`.
- Buses: `SynchronousQueryBus` / command bus take the list of all `DomainQueryHandler` /
  `DomainCommandHandler` beans, so a handler declared as a `@Bean` in a runtime configuration is
  registered exactly like a `@Named` one. `tt-data-league-import-runtime` scans all of
  `org.cttelsamicsterrassa`, so any `@Named` handler must be constructible there too.
- Runtime configuration pattern: `tt-data-league-api-runtime/.../config/ImportExecutionProperties`
  (`@ConfigurationProperties(prefix = "tt.league.import.execution")`) turned into a value bean by
  `ImportExecutionConfiguration`; defaults in `application.yml` as `${ENV_VAR:default}`.
- Clock pattern: `StartImportProcessCommandHandler` takes a `java.time.Clock` in a secondary constructor
  and defaults to `Clock.systemDefaultZone()`.
- Frontend (React 19 + Vite, Catalan-first i18n `ca`/`en`/`es`): `src/api/matches.js`,
  `src/hooks/useMatches.js` (`useRequest` abort/retry helper), `src/pages/MatchesSearchPage.jsx`, routes in
  `src/config/routes.js`, navigation in `src/config/navigation.js`, lazy routes in `src/App.jsx`,
  `useAuth().hasPermission`, primitives `Badge`, `Button`, `ProgressBar`, `Card`, `EmptyState`,
  `ErrorState`, `LoadingState` in `src/components/ui`. Tests use Vitest + Testing Library. Permission labels
  live in the i18n files (`'matches:read': 'Llegir partits'`).

## Contracts

1. **Calendar state (domain, `domain/match/model/`).**
   - `enum CalendarMatchState { PLAYED, UPCOMING, AWAITING_RESULT, UNDATED, OVERDUE, POSTPONED }`.
   - `record OverdueGracePeriod(int days)`: `days >= 0` else `IllegalArgumentException`;
     `static OverdueGracePeriod DEFAULT = new OverdueGracePeriod(7)` for tests and documentation only (the
     runtime always binds it explicitly, contract 7).
   - `final class CalendarStateResolver` with
     `static CalendarMatchState resolve(Match match, Integer currentRound, boolean overdueMarked,
     LocalDate today, OverdueGracePeriod grace)`. `match`, `today`, `grace` required
     (`NullPointerException`); `currentRound` is the `RoundProgress.currentRound` of the match's
     `(competition, groupNumber, phase)` and may be `null`. Rules, first match wins:
     1. `status == PLAYED` → `PLAYED` (a leftover manual mark is ignored);
     2. SCHEDULED and `overdueMarked` → `OVERDUE` (manual; the operator's decision wins over derivation);
     3. SCHEDULED and `currentRound != null && match.round < currentRound` → `POSTPONED`;
     4. SCHEDULED and `dateTime == null` → `UNDATED`;
     5. SCHEDULED and `matchDate.plusDays(grace.days()).isBefore(today)` → `OVERDUE` (date in
        `Match.COMPETITION_ZONE`; with 7 days, a match of Saturday the 3rd becomes overdue on Sunday the
        11th);
     6. SCHEDULED and `matchDate.isBefore(today)` → `AWAITING_RESULT` (date passed, still inside the
        grace period);
     7. otherwise → `UPCOMING`.
   - The resolved state is computed on every read and never stored.
2. **Manual overdue mark (domain).**
   - `record MatchOverdueMark(UUID matchId, ZonedDateTime markedAt, String markedBy)` in
     `domain/match/model/`: all components required; `markedBy` non-blank.
   - Port `domain/match/repository/MatchOverdueMarkRepository`:
     `Optional<MatchOverdueMark> findByMatchId(UUID)`, `List<MatchOverdueMark> findByMatchIds(Collection<UUID>)`
     (empty input → empty list, no query), `void save(MatchOverdueMark)` (insert, or leave an existing
     row untouched), `boolean deleteByMatchId(UUID)` (true when a row was removed).
   - The mark is operator input, separate from import data: no import processor, reconciler or
     consolidation code reads or writes it.
3. **Match repository port.** `MatchRepository.findMatchesBySourceSeasonAndCompetition(ImportSource source,
   Season season, String competition)` → `List<Match>`: abstract, all arguments required
   (`NullPointerException`; blank competition → `IllegalArgumentException`), always source-scoped,
   returns matches of **every** status, read-only. Javadoc states it is the calendar read and must not
   feed statistics (FEAT-00079 contract).
4. **JPA.**
   - `MatchRepositoryHelper.findAllBySourceAndSeasonAndCompetition(Source, String, String)` ordered by
     group, phase, round, date, time (served by `idx_match_source_season_competition_status`) and
     `MatchRepositoryJpa.findMatchesBySourceSeasonAndCompetition` mapping through `matchJPAToMatchMapper`.
   - New table `match_overdue_mark` (package `repository/jpa/match/`, entity `MatchOverdueMarkJPA`,
     helper `MatchOverdueMarkRepositoryHelper`, adapter `MatchOverdueMarkRepositoryJpa implements
     MatchOverdueMarkRepository`): `match_id UUID` primary key and FK → `match_record(id)`
     (`fk_match_overdue_mark_match`), `marked_at TIMESTAMP WITH TIME ZONE NOT NULL`, `marked_by
     VARCHAR(255) NOT NULL`. No cascade from `match_record`. One row per match at most. Created by
     `ddl-auto: update`.
5. **Application (core-domain).**
   - Query `application/match/calendar/FindSeasonCalendarQuery(ImportSource source, Season season,
     String competition, Integer groupNumber, Integer round)`: source/season/competition required;
     `groupNumber` and `round` optional (`round >= 1`); invalid → `IllegalArgumentException`.
   - Read models (`application/match/calendar/dto/`):
     - `SeasonCalendarReadModel(ImportSource source, Season season, String competition, LocalDate today,
       int overdueGraceDays, List<CalendarGroupReadModel> groups)`;
     - `CalendarGroupReadModel(Integer groupNumber, String phase, Integer currentRound,
       Integer lastCompleteRound, long scheduledMatches, long playedMatches, long overdueMatches,
       long postponedMatches, List<CalendarRoundReadModel> rounds)`; `overdueMatches` counts derived and
       manual OVERDUE;
     - `CalendarRoundReadModel(int round, LocalDate firstDate, LocalDate lastDate, long scheduledMatches,
       long playedMatches, boolean complete, boolean current, List<CalendarMatchReadModel> matches)`;
     - `CalendarMatchReadModel(UUID id, ZonedDateTime dateTime, String city, String venue,
       String homeTeamName, String awayTeamName, String winnerTeamName, Integer homeGamesWon,
       Integer awayGamesWon, MatchStatus status, CalendarMatchState calendarState,
       boolean overdueMarked, ZonedDateTime overdueMarkedAt, String overdueMarkedBy)`; the three mark
       fields are filled only for a SCHEDULED match (false/null for PLAYED).
   - `FindSeasonCalendarQueryHandler` (**not** `@Named`; see contract 7), constructor
     `(MatchRepository, MatchOverdueMarkRepository, OverdueGracePeriod, Clock)`:
     1. `matches = findMatchesBySourceSeasonAndCompetition(...)`;
     2. `RoundStatusCount` rows built from `matches` → `RoundProgressCalculator.compute` (same snapshot,
        rule not duplicated);
     3. `marks = findByMatchIds(ids of SCHEDULED matches)` (one query);
     4. `today = LocalDate.now(clock.withZone(Match.COMPETITION_ZONE))`;
     5. `CalendarStateResolver.resolve` per match;
     6. group by `(groupNumber, phase)` (nulls last, calculator order), rounds ascending, matches by
        `dateTime` (nulls last), home team name, id;
     7. `groupNumber` / `round` filters applied **after** progress and counts, so the group header always
        describes the whole group;
     8. `complete` = no SCHEDULED match in the round; `current` = round equals `currentRound`;
        `firstDate`/`lastDate` = min/max match date in `COMPETITION_ZONE` (`null` if undated).
     Empty result → success with empty `groups`. `IllegalArgumentException` → `failResponse`.
   - Commands (`application/match/calendar/mark/`, `DomainCommandHandler`, `@Named` — they need only
     repositories that exist in both runtimes):
     - `MarkMatchOverdueCommand(UUID matchId, String markedBy)` → handler with
       `(MatchRepository, MatchOverdueMarkRepository, Clock)` (`@Inject` constructor defaults to
       `Clock.systemDefaultZone()`): unknown match → failure `NOT_FOUND`; PLAYED match → failure
       `NOT_SCHEDULED`; already marked → success, original `markedAt`/`markedBy` kept (idempotent);
       otherwise save `MatchOverdueMark(matchId, now in COMPETITION_ZONE, markedBy)`.
     - `ClearMatchOverdueMarkCommand(UUID matchId)` → unknown match → `NOT_FOUND`; otherwise
       `deleteByMatchId` and success whether or not a row existed (idempotent). Clearing is allowed on a
       PLAYED match so a leftover mark can be removed.
     The failure reason is carried the way existing command handlers report failures (check
     `ConsolidateClubsCommandHandler` / `DomainCommandResponse` and reuse that shape; do not throw through
     the bus).
6. **Permission.** `Permission.MATCHES_WRITE("matches:write")`, granted to `UserRole.ADMIN` only.
   `RbacCatalog.MATCHES_WRITE` constant. Frontend permission label `'matches:write'` in the three locales
   (users/roles page).
7. **API runtime wiring (`tt-data-league-api-runtime/.../config/`).**
   - `SeasonCalendarProperties` (`@ConfigurationProperties(prefix = "tt.league.calendar")`) with
     `overdueGraceDays`; `application.yml`: `tt.league.calendar.overdue-grace-days:
     ${CALENDAR_OVERDUE_GRACE_DAYS:7}`. A negative or non-numeric value fails startup with a clear
     message (no fallback to 7).
   - `SeasonCalendarConfiguration`: `@Bean OverdueGracePeriod` from the properties and
     `@Bean FindSeasonCalendarQueryHandler` with `Clock.systemDefaultZone()`. Declaring the handler here
     (not `@Named`) keeps the import runtime, which scans every package, free of calendar configuration.
8. **REST (`tt-data-league-api-rest/.../match/`).**
   - `GET /api/v1/match/calendar?source=&season=&competition=&group=&round=` on `MatchController`,
     `matches:read`. Missing/invalid filters → `400 "Invalid calendar filters"`; handler failure →
     `500 "Season calendar failed"`. Response `SeasonCalendarDto` (nested `CalendarGroupDto`,
     `CalendarRoundDto`, `CalendarMatchDto`, static `from(...)`), enums as `String`, `today` as ISO date.
   - `PUT /api/v1/match/{id}/overdue-mark` (`matches:write`): `markedBy` = authenticated user name
     (`Authentication.getName()`), never taken from the request body. `204` on success; `404` unknown
     match; `409 "Only scheduled matches can be marked overdue"` for PLAYED.
   - `DELETE /api/v1/match/{id}/overdue-mark` (`matches:write`): `204`; `404` unknown match.
9. **Frontend (`tt-data-league-frontend/`).**
   - `src/api/matches.js`: `getSeasonCalendar(filters, …)` with `normalizeCalendar` (validates nested
     arrays; invalid shape → `ApiError` 502); `markMatchOverdue(id, …)` (PUT) and
     `clearMatchOverdueMark(id, …)` (DELETE).
   - `src/hooks/useMatches.js`: `useSeasonCalendar(filters)` on `useRequest` (enabled when source, season
     and competition are set; exposes `retry` to refetch after a mark change).
   - `src/pages/SeasonCalendarPage.jsx` at `/calendari` (`matches:read`):
     - filters source → season → competition via `getMatchOptions`, optional group selector from the
       response, filters in the URL query (`source, season, competition, group, round`);
     - one card per group/phase: `ProgressBar` played/(played+scheduled), current jornada, last complete
       jornada (`—` when null), scheduled/played/overdue/postponed counts, and the grace period in the page
       legend ("Endarrerit: sense acta 7 dies després de la data");
     - jornadas as an accessible list (heading with date range, "completa"/"actual" badges); each match
       row: date/time, teams, result for PLAYED, venue, `Badge` for `calendarState`; a manual mark shows
       "Endarrerit (manual)" with "marcat per {user} el {date}"; PLAYED rows link to the match summary;
     - **mark button**: only when `hasPermission('matches:write')` and the match is SCHEDULED. Label
       "Marcar com a endarrerit" when not marked, "Treure la marca d'endarrerit" when marked; a real
       `<button>` whose accessible name includes both teams; disabled with a busy status while the request
       runs; on success the calendar is refetched (no optimistic state); on failure an inline
       `role="alert"` message (409 has its own copy). No confirmation dialog: the action is reversible.
     - loading, empty, error (401 vs other) and retry states, as in `MatchesSearchPage`.
   - `src/config/routes.js` (`routePaths.seasonCalendar`, route meta `permission: 'matches:read'`),
     `src/config/navigation.js` (item `calendari` after `partits`, `CalendarDays` icon), `src/App.jsx`
     (lazy route), i18n keys in `ca`/`en`/`es` (`navigation.calendar`, `routes.seasonCalendar`,
     `calendarPage.*`, one label per `CalendarMatchState`, the mark actions/messages, `'matches:write'`).
   - The frontend never derives states, grace periods or progress; it renders what the API returns.

## Implementation order

1. **Domain values and resolver.** `CalendarMatchState`, `OverdueGracePeriod`, `CalendarStateResolver`,
   `MatchOverdueMark`. `CalendarStateResolverTest`: PLAYED wins over a mark, a lower round and a past date;
   manual mark → OVERDUE even inside the grace period, for a future date, and over POSTPONED; POSTPONED when
   `round < currentRound` (also with a future date after a reschedule); UNDATED; grace boundaries with
   7 days (match date +6 and +7 days → AWAITING_RESULT, +8 → OVERDUE); grace 0 → OVERDUE the day after;
   dated today and tomorrow → UPCOMING; Europe/Madrid date comparison across midnight UTC; null
   arguments → NPE. `OverdueGracePeriodTest` (negative rejected), `MatchOverdueMarkTest` (required fields).
2. **Ports.** `MatchRepository.findMatchesBySourceSeasonAndCompetition`; implement it in
   `InMemoryRepositories.Matches` and in the anonymous stubs of `IncrementalPreviewServiceTest` and
   `SnapshotReconcilerTest` (following their existing style for unused methods). New
   `MatchOverdueMarkRepository` port.
3. **Permission.** `Permission.MATCHES_WRITE`, `UserRole.ADMIN`, `RbacCatalog.MATCHES_WRITE`. Update any
   test that enumerates role permissions.
4. **JPA.** Calendar read query plus `match_overdue_mark` entity/helper/adapter.
   `MatchSeasonCalendarJpaTest` (style of `MatchRoundProgressJpaTest`): the FCTT 2026-2027
   tercera-nacional G1 shape (round 1: 3 PLAYED + 3 SCHEDULED, round 2: 6 SCHEDULED) plus decoys (same
   competition under RFETM, same source in 2025-2026, another competition) → exactly the 12 G1 matches of
   both statuses, in order; empty scope; null/blank arguments rejected.
   `MatchOverdueMarkRepositoryJpaTest`: save/find/find-by-ids/delete round trip; saving twice keeps the
   first `markedAt`; delete of a missing row → false; an unknown match id is rejected by the FK; marks
   survive a `replaceMatchContent` upgrade of the match (import never touches them).
5. **Query handler.** Read models and `FindSeasonCalendarQueryHandler`. `FindSeasonCalendarQueryHandlerTest`
   (stub repositories, fixed `Clock`, grace 7):
   - G1 shape with today 3 days after jornada 1 → current 1, last complete `null`, scheduled 9, played 3;
     jornada-1 pending matches AWAITING_RESULT, overdue 0; the same data with today 10 days after →
     those 3 OVERDUE, overdue 3; jornada 2 UPCOMING;
   - a manual mark on a jornada-2 match → OVERDUE with `overdueMarked`, `markedBy`, `markedAt`, counted in
     `overdueMatches`; a mark left on a PLAYED match → PLAYED, mark fields empty, not counted;
   - postponed fixture (round 1 one SCHEDULED, round 2 all PLAYED) → POSTPONED, postponed 1, current 2;
   - null group ordering, BCNESA phases reusing round numbers as separate groups;
   - `groupNumber`/`round` filters narrow the lists but not the group header;
   - one `findByMatchIds` call, only with SCHEDULED ids; empty result → empty `groups`; nothing written.
6. **Command handlers.** `MarkMatchOverdueCommandHandlerTest` / `ClearMatchOverdueMarkCommandHandlerTest`:
   unknown match, PLAYED match rejected for mark, idempotent re-mark keeps the original author/time,
   `markedAt` from the fixed clock in Europe/Madrid, clear is idempotent and allowed on PLAYED.
7. **API runtime wiring.** `SeasonCalendarProperties`, `SeasonCalendarConfiguration`, `application.yml`.
   `SeasonCalendarPropertiesTest` (style of `ImportExecutionPropertiesTest`): default 7, env override,
   negative rejected. Check that the API runtime context test (if any) still starts and that the import
   runtime `AppTest` is unaffected (the handler is not `@Named`).
8. **REST.** `SeasonCalendarDto`, `MatchController.calendar`, `markOverdue`, `clearOverdueMark`. New
   `MatchControllerTest` (style of `ClubControllerTest`): calendar 200 shape with string states and mark
   fields, 400 missing competition / invalid source, 500 handler failure, empty `groups`; PUT 204 passing
   the authenticated user name, 404, 409; DELETE 204, 404; `matches:write` is required for PUT/DELETE
   (403 for a `matches:read`-only user, using the existing security test setup).
9. **Frontend API and hook.** `getSeasonCalendar`, `normalizeCalendar`, `markMatchOverdue`,
   `clearMatchOverdueMark`, `useSeasonCalendar`. `src/api/matches.test.js`: query string (optional
   group/round omitted), required filters, invalid shape → 502, PUT/DELETE method and URL encoding.
10. **Frontend page, routes, navigation, i18n.** `SeasonCalendarPage.jsx` and wiring.
    `SeasonCalendarPage.test.jsx` (mocked API and auth): group progress and counts; jornada headings;
    state badges including AWAITING_RESULT and manual OVERDUE with author/date; PLAYED rows link to the
    summary and SCHEDULED rows do not; the mark button appears only with `matches:write` and only on
    SCHEDULED rows; clicking it calls PUT, shows busy state and refetches; a marked row offers clear
    (DELETE); 409 shows its message; empty/error/loading states; group filter updates the URL. Update any
    navigation/routes/permission-label tests that enumerate entries.
11. **Documentation.**
    - `rfetm-datamodel.md`: new `match_overdue_mark` section (columns, PK/FK, no cascade, one row per
      match, operator data never written by imports, ignored once the match is PLAYED), a row in the
      entity relationship summary, and the calendar read in "Repository lookup behavior".
    - `tt-data-league-api-runtime/README.md`: `CALENDAR_OVERDUE_GRACE_DAYS` (default 7, validation), the
      three endpoints, state rules of contract 1, `matches:write` (ADMIN).
    - `tt-data-league-frontend/README.md`: the Calendari page if the README lists pages.
12. **Validation.** `mvn -pl tt-data-league-core-repository-jpa -am test`, `mvn -pl tt-data-league-import -am test`,
    `mvn -pl tt-data-league-api-runtime -am test`, `mvn -pl tt-data-league-import-runtime -am test`; in
    `tt-data-league-frontend`: `npm run lint`, `npm test`, `npm run build`; then the full `mvn test`.

# Implementation Guidelines

- Affected modules: core-domain (states, resolver, overdue mark, ports, query and command handlers, read
  models, `Permission`/`UserRole`), JPA (calendar read query, `match_overdue_mark` table), import tests
  (in-memory repository and port stubs only), api-runtime (grace-period property and handler wiring),
  api-rest (endpoints, DTOs, `RbacCatalog`), frontend (page, API client, hook, routes, navigation, i18n),
  `rfetm-datamodel.md` and READMEs.
- Follow the repository and module `AGENTS.md` files (including `tt-data-league-frontend/AGENTS.md`); keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- **Derived states are never stored:** `CalendarMatchState` is computed on every read by
  `CalendarStateResolver`. Do not add a column, a `MatchStatus` value, or a `Match` field for
  postponed/overdue/awaiting/undated. The **only** stored calendar data is the manual overdue mark in
  `match_overdue_mark`, which records an operator decision, not a computed state.
- **Keep the manual mark separate from import data:** no import processor, reconciler, backfill or
  consolidation code reads or writes `match_overdue_mark`, and `replaceMatchContent` / `updateSchedule`
  do not touch it. Once a match is PLAYED, the mark is ignored rather than deleted.
- **One rule per concept:** jornada progress comes only from `RoundProgressCalculator`; calendar states
  and the grace period only from `CalendarStateResolver`. The JPA adapter, the handler and the frontend
  must not re-implement either rule (no JPQL for states, no state or date logic in React).
- **Configuration is explicit:** the grace period comes from `tt.league.calendar.overdue-grace-days`
  (`CALENDAR_OVERDUE_GRACE_DAYS`, default 7 in `application.yml`). An invalid value fails startup; there is
  no silent fallback. `FindSeasonCalendarQueryHandler` is declared as a `@Bean` in the API runtime, not
  `@Named`, so the import runtime (which scans every package) needs no calendar configuration.
- **Authorization:** marking and clearing need the new `matches:write` permission (ADMIN only).
  `markedBy` always comes from the authenticated principal, never from the request. Reading the calendar
  needs only `matches:read`.
- **FEAT-00079 contract stays intact:** the new all-status read is used only by the calendar handler.
  It must not feed statistics, search, community counts, or any PLAYED-only view.
  `MatchSearchCriteria` keeps its mandatory PLAYED/SCHEDULED status; no "all statuses" option is added.
- Time: derive `today` and `markedAt` from an injected `Clock` in `Match.COMPETITION_ZONE`
  (Europe/Madrid); never call `LocalDate.now()` without the clock. Do not add a global `Clock` bean.
- The calendar never writes matches, schedules or statuses. Rescheduling stays the import's job
  (FEAT-00080/00081). The only write path is the overdue mark (`PUT`/`DELETE`).
- The UI shows states, marks and progress exactly as the API returns them, and refetches after a mark
  change (no optimistic state). The Catalan copy comes first; `en` and `es` are kept in sync.
- DTO and read-model components are new types; existing `MatchDto`/`MatchSearchReadModel` are not changed.
- Out of scope:
  - Other manual states (`CANCELLED`, manual postponement), reason/comment fields on the mark, mark
    history or audit trail beyond `markedBy`/`markedAt`, and any calendar editing (analysis open
    question 4).
  - Per-competition or per-source grace periods; editing the grace period from the Settings UI.
  - Deleting or flagging vanished fixtures (FEAT-00086 reconciliation owns reporting; open question 5).
  - Inferring the total number of jornadas (FCTT exports a sliding window); only stored rounds are shown.
  - MCP and GraphQL exposure, calendar export (iCal), notifications.
  - A match-detail page for SCHEDULED fixtures.
  - Automated per-jornada fetch (FEAT-00093).

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P3, size L, Slice 5: calendar feature and automation. Depends on: FEAT-00079 (T4), FEAT-00084 (T8).
- 2026-09-29: Build plan written against the code after FEAT-00091 (commit a0dc2a0); status `idea` →
  `planned`. Both dependencies are done. Decisions:
  - **State rules** (contract 1, first match wins): PLAYED; POSTPONED when SCHEDULED and a later round of
    the same group/phase already has a PLAYED match (`round < currentRound`); UNDATED when SCHEDULED with
    no date; OVERDUE when SCHEDULED and dated before today (Europe/Madrid); otherwise UPCOMING.
    POSTPONED is based on round order, not on dates, so a fixture moved to a later date by a reschedule
    still reads as postponed, and it wins over OVERDUE because it is the more specific operator signal.
  - **Competition is mandatory** on the calendar endpoint. This bounds the payload (FCTT 2026-2027 holds
    2,882 fixtures per season) and matches the existing options endpoint, which already lists
    competitions per source and season. Group and round are optional filters.
  - **Progress is recomputed from the fetched matches** with `RoundProgressCalculator`, instead of a
    second `findRoundProgress` call, so the header and the listed matches come from the same snapshot.
  - **A new all-status port method** (`findMatchesBySourceSeasonAndCompetition`) instead of relaxing
    `MatchSearchCriteria`, so the FEAT-00079 PLAYED-by-default search contract stays unchanged.
  - The acceptance criteria were unchanged at that point. The plan made them concrete (endpoint, state
    rules, page).
- Open question 1 (recommendation: keep as planned): should OVERDUE allow a grace period (for example
  one or two days before a missing acta counts as overdue)? The plan uses "date before today" with no
  grace period. A grace period would be one more resolver parameter, and it should be environment-driven
  rather than hard-coded.
- Open question 2 (recommendation: keep as planned): should the page be public to every `matches:read`
  user, or live under Administration? The plan adds it to the main navigation, because the calendar is
  read-only league information.
- Open question 3 (recommendation: not now): analysis open question 4 (manual states such as
  `CANCELLED`) is still open. The derived-only design does not block adding a stored state later.
- 2026-09-29: Open question 1 answered by the user: **use a 1-week grace period, and allow OVERDUE to be
  forced manually with a button in the UI.** The plan was rebuilt; status stays `planned`. Changes:
  - Goal and acceptance criteria updated. AC 2 now says derived states are never stored and only the
    manual mark is persisted. New AC 3 covers the grace period and new AC 4 covers the manual mark.
  - The state rules gained the manual mark (second, right after PLAYED, so it wins over POSTPONED) and
    `AWAITING_RESULT` (date passed, still inside the grace period). Without it, a match whose date has
    passed would have no honest label. With the 7-day default, a match of the 3rd becomes OVERDUE on the
    11th.
  - The grace period is configurable (`CALENDAR_OVERDUE_GRACE_DAYS`, default 7), following the AGENTS
    rule that configuration is explicit and environment-driven. The domain value `OverdueGracePeriod`
    rejects negative values.
  - The manual mark is stored in a new `match_overdue_mark` table (PK/FK `match_id`, `marked_at`,
    `marked_by`) behind a new `MatchOverdueMarkRepository` port, rather than a `match_record` column, so
    that import writes (`replaceMatchContent`, `updateSchedule`) can never overwrite operator input. An FK
    is safe because matches are never deleted, only their child rows.
  - New `matches:write` permission, ADMIN only. `PUT`/`DELETE /api/v1/match/{id}/overdue-mark`; the
    author comes from the authenticated principal. Marking is idempotent and keeps the first
    author/time; marking a PLAYED match is `409`; clearing is idempotent and allowed on PLAYED.
  - The UI button appears only for SCHEDULED matches and only for users with `matches:write`. It toggles
    between mark and clear, has no confirmation dialog (the action is reversible), and refetches the
    calendar after success.
- Open question 1 is closed (see the entry above).
- Open question 4 (recommendation: keep as planned): only ADMIN gets `matches:write`. `CLUB_MANAGER`
  could get it too if club managers should flag overdue matches; that would be a one-line change to
  `UserRole`.
- 2026-09-29: Status `planned` → `ready` on explicit user request. The remaining open questions were
  accepted as recommended: only ADMIN gets `matches:write` (open question 4), the page sits in the main
  navigation for every `matches:read` user (open question 2), and manual states such as `CANCELLED` stay
  out of scope (open question 3).
- 2026-09-29: Status `ready` → `in-progress`, then `in-review` after implementing the full plan. The
  implementation follows the build plan step by step — domain states/resolver/overdue-mark, the
  all-status `findMatchesBySourceSeasonAndCompetition` port, the `match_overdue_mark` table, the
  `FindSeasonCalendarQueryHandler` (wired as a `@Bean` in the API runtime), the mark/clear command
  handlers (`@Named`), the REST endpoints and DTO, the `matches:write` permission, the `Calendari`
  page and its API/hook/i18n/route/navigation wiring, and the datamodel/README updates. Validation:
  full `mvn test` (BUILD SUCCESS, including the new domain, JPA (`MatchSeasonCalendarJpaTest`,
  `MatchOverdueMarkRepositoryJpaTest`), REST (`MatchControllerTest`) and runtime
  (`SeasonCalendarPropertiesTest`) tests) and, in the frontend, `npm run lint`, `npm test` (360) and
  `npm run build`. All five acceptance criteria are satisfied: the all-status calendar read feeds
  only the calendar handler (never statistics); the derived states live only in
  `CalendarStateResolver` and are never stored, with only the manual mark persisted; the grace period
  is environment-driven (`CALENDAR_OVERDUE_GRACE_DAYS`, default 7, invalid values fail startup);
  `matches:write` (ADMIN only) gates `PUT`/`DELETE /api/v1/match/{id}/overdue-mark` and records
  `markedBy`/`markedAt` from the authenticated principal; and the UI renders group progress and state
  badges. The `match_id` marked-at zone returned by H2 loses the region on a fresh read (offset-only
  `+02:00`), which the JPA tests assert via `toInstant()`; production PostgreSQL stores
  `timestamp with time zone` the same way, so the instant is always preserved.
- 2026-09-29: Closed on explicit user request; status `in-review` → `done`. All five acceptance
  criteria checked in both the registry and this file; `feature_manager.py validate` passes.
