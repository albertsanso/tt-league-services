# Build Plan
Read-only query that combines the existing whole-season `RoundProgress` (FEAT-00084) with the
calendar's derived states (FEAT-00092) per jornada. No schema change, no new stored state.
Implementation order follows the inward dependency direction: domain → JPA → handler wiring → REST → docs.

## Response contract

`GET /api/v1/match/round-progress?source=FCTT&season=2026-2027[&competition=...][&onlyOpen=true]`

```json
{
  "source": "FCTT",
  "season": "2026-2027",
  "competition": null,
  "onlyOpen": true,
  "today": "2026-10-04",
  "overdueGraceDays": 7,
  "groups": [
    {
      "competition": "TERCERA",
      "groupNumber": 2,
      "phase": "1a Fase",
      "currentRound": 3,
      "lastCompleteRound": 2,
      "rounds": [
        {
          "round": 3,
          "firstDate": "2026-09-26",
          "lastDate": "2026-09-27",
          "scheduledMatches": 2,
          "playedMatches": 4,
          "postponedMatches": 0,
          "overdueMatches": 1,
          "awaitingResultMatches": 1,
          "undatedMatches": 0,
          "complete": false,
          "current": true,
          "open": true
        }
      ]
    }
  ]
}
```

- `scheduledMatches + playedMatches` is every stored match of the jornada. `postponedMatches`, `overdueMatches`,
  `awaitingResultMatches` and `undatedMatches` are disjoint subsets of `scheduledMatches`, each resolved by
  `CalendarStateResolver` (the remainder is `UPCOMING`). `awaitingResultMatches`/`undatedMatches` are additive
  fields beyond the acceptance criteria so FEAT-00107 can map `AWAITING_RESULT` without a second call.
- `firstDate`/`lastDate` are the earliest/latest match dates of the jornada in Europe/Madrid (same definition as
  `CalendarRoundReadModel`), `null` when every match is undated.
- `currentRound`/`lastCompleteRound` come unchanged from `MatchRepository.findRoundProgress`; filters never change
  them.
- `open` is `true` when the jornada has any non-played match (`scheduledMatches > 0`) or its window
  `[firstDate, lastDate + overdueGraceDays]` contains `today`. With a 7-day grace, a jornada played on the 3rd stays
  open through the 10th (the last `AWAITING_RESULT` day) and closes on the 11th, mirroring the resolver.
- With `onlyOpen=true` only `open` jornadas are returned and groups left with no jornada are dropped. Without it
  every stored jornada is returned. Groups are sorted by competition, group and phase (nulls last), rounds ascending.
- An unknown competition or a source/season without matches returns `200` with empty `groups`.

## Steps

1. **Domain: slim calendar entry** (`tt-data-league-core-domain`, `domain/match/model/MatchCalendarEntry.java`).
   Immutable record `(UUID matchId, String competition, Integer groupNumber, String phase, int round,
   MatchStatus status, LocalDate matchDate)`; `matchId`/`status` required, `round >= 1`, `matchDate` nullable and
   already expressed in `Match.COMPETITION_ZONE`. Javadoc: calendar read only, never for statistics/search/PLAYED-only
   views (FEAT-00079), same wording as the other calendar reads.

2. **Domain: single state rule** (`CalendarStateResolver`). Add
   `resolve(MatchStatus status, int round, LocalDate matchDate, Integer currentRound, boolean overdueMarked,
   LocalDate today, OverdueGracePeriod grace)` holding the existing ordered rule, and make the current
   `resolve(Match, ...)` delegate to it after converting `getDateTime()` to the Europe/Madrid local date.
   Existing `CalendarStateResolver` behaviour and the two calendar handlers stay unchanged; there remains exactly
   one rule implementation.

3. **Domain: jornada read model and calculator** (`domain/match/model/`).
   - `JornadaProgress` record: competition, groupNumber, phase, round, firstDate, lastDate, scheduledMatches,
     playedMatches, postponedMatches, overdueMatches, awaitingResultMatches, undatedMatches, `complete`
     (`scheduledMatches == 0`), `current` (`round == currentRound`); constructor validates non-negative counts,
     subset sums `<= scheduledMatches`, `firstDate <= lastDate`, both dates null or both set. Method
     `boolean isOpen(LocalDate today, OverdueGracePeriod grace)` implements the `open` rule above.
   - `JornadaProgressCalculator.compute(List<RoundProgress> progress, Collection<MatchCalendarEntry> entries,
     Set<UUID> overdueMarkedMatchIds, LocalDate today, OverdueGracePeriod grace)` → `Map`/list of
     `JornadaProgress` per `(competition, groupNumber, phase)` key, using each group's `currentRound` from
     `progress` and `CalendarStateResolver.resolve(...)` per entry (marks only count for `SCHEDULED` entries, as in
     `CalendarMatchAssembler`). Null-safe grouping identical to `RoundProgressCalculator`'s `RoundKey`.
     An entry whose group is missing from `progress` is a consistency error → `IllegalStateException`
     (both reads come from the same table; never silently drop it).

4. **Domain: repository port** (`MatchRepository`). Add
   `List<MatchCalendarEntry> findCalendarEntries(ImportSource source, Season season)`: every stored match of the
   source and season regardless of status, projected to entries, source-scoped and read-only; `NullPointerException`
   on null arguments. Javadoc ties it to the round-progress handler only.

5. **Application: query and handler** (`application/match/roundprogress/`).
   - `FindRoundProgressQuery(ImportSource source, Season season, String competition, boolean onlyOpen)`:
     source/season mandatory, competition optional but non-blank when present (`IllegalArgumentException`
     otherwise), mirroring `FindSeasonCalendarQuery`.
   - `dto/RoundProgressReadModel` (source, season, competition, onlyOpen, today, overdueGraceDays, groups),
     `dto/RoundProgressGroupReadModel` (competition, groupNumber, phase, currentRound, lastCompleteRound, rounds),
     `dto/JornadaProgressReadModel` (the jornada fields plus `open`).
   - `FindRoundProgressQueryHandler(MatchRepository, MatchOverdueMarkRepository, OverdueGracePeriod, Clock)`
     extends `DomainQueryHandler`: one `findRoundProgress`, one `findCalendarEntries`, one
     `MatchOverdueMarkRepository.findByMatchIds` restricted to `SCHEDULED` entry ids, then
     `JornadaProgressCalculator`; applies the competition filter and `onlyOpen` after computing, so group headers
     are never affected by filters. `today = LocalDate.now(clock.withZone(Match.COMPETITION_ZONE))`.
     Failure handling follows the calendar handlers (`IllegalArgumentException` → `failResponse`); no broad catch.
     Not `@Named` (declared as a `@Bean`, see step 8), so the import runtime never needs the grace configuration.

6. **JPA adapter** (`tt-data-league-core-repository-jpa`, `repository/jpa/match/impl/`).
   - `MatchCalendarEntryProjection` constructor-expression class (same style as `RoundStatusCountProjection`).
   - `MatchRepositoryHelper.findCalendarEntries(Source, String season)`:
     `select new ...MatchCalendarEntryProjection(m.id, m.competition, m.groupNumber, m.phase, m.round, m.status,
     m.matchDate) from MatchJPA m where m.source = :source and m.season = :season`, no joins/fetches; served by the
     leading columns of `idx_match_source_season_competition_status`.
   - `MatchRepositoryJpa.findCalendarEntries` maps JPA `Source`/`MatchStatus` to the domain enums
     (`matchDate` is a plain `LocalDate`, no zone conversion, matching `MatchJPAToMatchMapper`).

7. **Other `MatchRepository` implementations** (`tt-data-league-import` tests): implement `findCalendarEntries` in
   `InMemoryRepositories` by projecting stored matches (date via `getDateTime()` in Europe/Madrid), and follow each
   file's existing convention for unused methods in the stubs inside `IncrementalPreviewServiceTest` and
   `SnapshotReconcilerTest`. Add a case to `InMemoryMatchRepositoryTest`.

8. **API runtime wiring** (`tt-data-league-api-runtime`, `config/SeasonCalendarConfiguration.java`): add a
   `FindRoundProgressQueryHandler` `@Bean` reusing the existing `OverdueGracePeriod` bean and
   `Clock.systemDefaultZone()`. No new configuration property.

9. **REST endpoint** (`tt-data-league-api-rest`, `match/`).
   - `MatchController.roundProgress`: `@GetMapping("/round-progress")`,
     `@PreAuthorize("hasAuthority('matches:read')")`, `@Operation`/`@ApiResponses` (200/400/401/403);
     params `source`, `season` (required), `competition`, `onlyOpen` (optional strings). `onlyOpen` accepts only
     `true`/`false` (case-insensitive, absent = `false`); anything else, a bad source/season or a blank competition
     → `400 "Invalid round progress filters"`. Handler failure → `500 "Round progress failed"`.
   - `RoundProgressDto` record tree with `from(RoundProgressReadModel)`, same style as `SeasonCalendarDto`.
   - Security needs no rule change: `GET /api/v1/match/**` already requires `matches:read`, which service
     credentials may hold (FEAT-00101).

10. **Tests** (JUnit 5 + Mockito, existing patterns):
    - Domain: `CalendarStateResolverTest` cases for the new overload (equivalence with the `Match` overload,
      grace boundary); `JornadaProgressTest` (validation, `isOpen` on the grace boundary day and the day after,
      undated jornada, open because of a postponed match); `JornadaProgressCalculatorTest` with the FCTT 2026-2027
      shape (`TERCERA`, groups 1–2, phase `1a Fase`, rounds 1–3: a postponed round-1 match, an `AWAITING_RESULT`
      and an `OVERDUE` round-3 match, a manually marked match, an ungrouped/phaseless group), and the missing-group
      `IllegalStateException`.
    - Application: `FindRoundProgressQueryHandlerTest` — competition filter keeps headers, `onlyOpen` drops closed
      jornadas and empty groups, marks queried once with SCHEDULED ids only, nothing written, empty source is a
      success with no groups, repository `IllegalArgumentException` → failed response.
    - JPA: `MatchCalendarEntriesJpaTest` (next to `MatchRoundProgressJpaTest`) — FCTT 2026-2027 vs 2025-2026 and
      another source are excluded, every status returned, null group/phase/date preserved; plus an end-to-end
      check that an overdue SCHEDULED match counts as overdue through `findRoundProgress` + `findCalendarEntries`.
    - REST: `MatchControllerTest` — DTO mapping, `onlyOpen`/competition passed to the query, `400` cases (bad
      `onlyOpen`, blank competition, unknown source), `500` on failed response.
      `ServiceCredentialSecurityIntegrationTest` — a service key with `matches:read` gets `200` on
      `/api/v1/match/round-progress` and one without it gets `403` (register the real `MatchController` with
      mocked `QueryBus`/`MatchRepository` in the test configuration, replacing or alongside the match probe).

11. **Documentation**:
    - `tt-data-league-api-runtime/README.md`: new "Round progress (FEAT-00102)" subsection under the season
      calendar section — endpoint, parameters, open rule, response fields, error responses, grace-period source,
      and that it is available to service credentials holding `matches:read`.
    - `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md`: add the `findCalendarEntries` read to the
      `MatchRepositoryHelper` row (read-only projection, index used; no column/constraint change).

12. **Validation**: `mvn -pl tt-data-league-api-runtime -am test`, then the full `mvn test`; review the diff for
    `target/` content and out-of-scope changes.

## Acceptance Criteria

- [x] `GET /api/v1/match/round-progress?source=&season=` returns, per competition/group/phase and jornada, the counts of scheduled, played, derived postponed and overdue matches, and the first and last scheduled dates
- [x] Counts reuse `MatchRepository.findRoundProgress` and the calendar's derived postponed/overdue rules; no state is stored
- [x] Optional `competition` and `onlyOpen=true` filters narrow the result; `onlyOpen` keeps jornadas with any non-played match or a window overlapping today plus the grace period
- [x] The endpoint needs `matches:read` and is available to the service credential
- [x] Domain, JPA and controller tests cover the FCTT 2026-2027 shape and an overdue match

# Implementation Guidelines

- Read-only. The domain stays the single source of truth for "reported"; the orchestrator never re-derives statuses from raw tables.
- No schema change and no stored state: counts and the `open` flag are derived on every request. Do not add a
  column, table or cache for round progress.
- Exactly one implementation of each rule: jornada progress stays in `RoundProgressCalculator`, the calendar state in
  `CalendarStateResolver`. The new calculator only composes them; the handler never re-implements either.
- `findCalendarEntries` is a calendar read like `findMatchesBySourceSeasonAndCompetition`: it must not feed
  statistics, search, community counts or any PLAYED-only view (FEAT-00079).
- Every lookup is source- and season-scoped; never read matches by competition name alone.
- The grace period comes only from the existing `OverdueGracePeriod` bean (`CALENDAR_OVERDUE_GRACE_DAYS`); never fall
  back to `OverdueGracePeriod.DEFAULT` outside tests.
- Response fields are a contract consumed by the orchestrator (FEAT-00107): add fields only additively and document
  them in the API runtime README.
- Out of scope: per-match rows (FEAT-00107 uses `GET /api/v1/match/calendar/range` for those), `CANCELLED`/`WALKOVER`
  states, writes of any kind, MCP tools, frontend views and orchestrator-side clients.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Builds on FEAT-00084 (round progress) and FEAT-00092 (calendar, derived states, 7-day grace).

## 2026-10-04 — Plan built (`idea` → `planned`)

- The existing `RoundProgress` is per competition/group/phase, not per jornada, so a new `JornadaProgress` read model
  is added; `RoundProgress` and its calculator are reused unchanged for the group headers (`currentRound`,
  `lastCompleteRound`) that drive the derived `POSTPONED` state.
- Derived states need per-match data, but loading full `Match` aggregates (teams, clubs) for a whole season on every
  orchestrator poll is wasteful. Decision: a slim `MatchCalendarEntry` projection port plus a primitive-argument
  overload of `CalendarStateResolver.resolve` that the `Match` overload delegates to, keeping one rule.
- The `onlyOpen` window is defined as `[firstDate, lastDate + grace]` so a fully played jornada stays open exactly
  while its last match could still be `AWAITING_RESULT`.
- Service-credential access needs no security change: `GET /api/v1/match/**` already requires `matches:read`
  (FEAT-00101); only a test is added.
- Open question: the manual-mark lookup passes every SCHEDULED match id of the season to `findByMatchIds` in one
  `IN` list. Fine for current FCTT/RFETM/BCNESA volumes; if a season ever approaches the PostgreSQL bind-parameter
  limit, switch to a source/season-scoped mark query instead of chunking silently.

## 2026-10-04 — Approved (`planned` → `ready`)

- Plan approved for implementation as written, including the single `IN`-list mark lookup.

## 2026-10-04 — Implemented (`in-progress` → `in-review`)

- Delivered as planned: `MatchCalendarEntry`, `JornadaProgress`/`JornadaProgressCalculator`, the primitive
  `CalendarStateResolver.resolve` overload (the `Match` overload delegates to it), `MatchRepository.findCalendarEntries`
  (JPA, in-memory and test stubs), `FindRoundProgressQueryHandler` wired as a `@Bean`, and
  `GET /api/v1/match/round-progress` with README and datamodel updates.
- Validation: `mvn -pl tt-data-league-api-runtime -am test` passes except
  `BcnesaImportProcessorsTest.storesTheSetScoresOfEveryGameFromTheHtmlBasedActas`, which fails on the missing
  untracked fixture `actas/acta_bcnesa_2026_published.json` and is unrelated to this feature. The remaining
  reactor modules (`mvn test` at the root) were not run.
