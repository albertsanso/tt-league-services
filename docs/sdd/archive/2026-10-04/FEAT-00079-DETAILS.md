# Build Plan
Source task: **T4** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.5; gap G3; risks K1, K9).

## Acceptance Criteria
- [x] MatchOutcome returns empty for SCHEDULED matches and its javadoc states that a winner-less PLAYED match is a tie
- [x] Match, player, club, federated-club, competition and club-search query handlers compute stats, win rates, form and streaks over PLAYED only
- [x] MatchRepositoryHelper countBySeason, countAllMatches and findAllSeasons count PLAYED only; searchMatches/countMatches and fragment search default to PLAYED
- [x] REST and MCP MatchDto/MatchDetailDto expose an additive status field
- [x] findAllMatchesByTeamIds used by consolidation is not filtered by status
- [x] Each affected handler has a test with a mixed SCHEDULED/PLAYED fixture
- [x] Shipped with or before the processor lifecycle feature (T6)

## Filtering contract (the rule every step implements)

Split by who owns the decision:

- **Handler-level (in-memory-friendly) filtering** for the `findAllMatchesByTeamIds*` collections,
  because they feed both statistics (PLAYED only) and potential future list views (FEAT-00092).
  The JPA queries `findAllByTeamIds`, `findAllByTeamIdsAndSource` and
  `findAllByTeamIdsAndSourceAndSeasonAndCompetition` stay **unfiltered**; every handler that
  computes statistics, form, win rates, streaks or competition summaries applies
  `Match.isPlayed()` (`status == MatchStatus.PLAYED`) before aggregating.
- **Query-level filtering** for whole-corpus reads where the row set itself is the leak:
  `search`/`countSearch` (via the criteria status), `searchByFragmentsInName`, `findAllSeasons`,
  `countBySeason` and the new `countAllMatches` query only ever see `PLAYED` rows.
- **Consolidation exception (K9/R12):** `findAllMatchesByTeamIds` stays unfiltered at every
  layer, so re-pointing clubs never misses scheduled fixtures.
- **MatchOutcome is the central safety net:** `teamOutcome` and `playerOutcome` return
  `Optional.empty()` for a SCHEDULED match before any tie-eligibility logic runs.

## Steps

1. **Domain: `Match.isPlayed()` and `MatchOutcome` guard.**
   - In `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/match/model/Match.java`,
     add `public boolean isPlayed() { return status == MatchStatus.PLAYED; }`.
   - In `.../domain/shared/model/MatchOutcome.java`, make `teamOutcome(Match, UUID)` and
     `playerOutcome(Match, UUID)` return `Optional.empty()` as the first check when
     `match.getStatus() == MatchStatus.SCHEDULED`, before the `TieEligibleCompetitions` branch
     (this closes G3: a SCHEDULED match in a tie-eligible competition must not read as DRAW).
   - Rewrite the class javadoc invariant: imports can store unplayed fixtures as SCHEDULED;
     a winner-less **PLAYED** match is a tie (tie-eligible competitions) or has no outcome.

2. **Domain: search criteria status.** In `.../domain/match/model/MatchSearchCriteria.java` add a
   `MatchStatus status` component that defaults to `MatchStatus.PLAYED` in the builder/factory so
   every existing caller keeps its behavior without edits. `null` is rejected (`requireNonNull`) â€”
   there is deliberately no "all statuses" option in this feature.

3. **Domain: read models carry status.** Add a `MatchStatus status` component **at the end** of the
   records `.../application/match/find/dto/MatchSearchReadModel.java` and
   `.../application/match/find/dto/MatchDetailReadModel.java`. Fill it in
   `SearchMatchesQueryHandler.toReadModel` and `FindMatchDetailsQueryHandler.compose` from
   `match.getStatus()`. Update all constructions (handlers, tests).

4. **Domain: handler stat filtering.** Apply `Match.isPlayed()` predicates (no new abstractions):
   - `.../application/match/find/FindMatchDetailsQueryHandler.java`: filter the lists returned by
     `teamMatchesExcludingCurrent` / `visibleTeamMatches` so `teamForm`, `playerForm`,
     `alignmentStability`, `teamWinRate`/`winRate` and streak loops see PLAYED only. The
     currently-viewed match itself is returned regardless of status (it carries the new status
     field). Keep `findMatchById` unfiltered.
   - `.../application/player/find/FindPlayerDetailsQueryHandler.java`: in `compose`, extend the
     existing `winnerTeam != null` predicate at the `allPlayerMatches` stream (around lines
     112â€“118) with `match.isPlayed()`; do not change the winner semantics (SCHEDULED matches have
     no lineups per the FEAT-00077 invariant, so this is defence in depth, per K1).
   - `.../application/club/find/FindClubDetailsQueryHandler.java`,
     `.../application/club/find/FindFederatedClubDetailsQueryHandler.java` and
     `.../application/club/find/FindClubsByStringInNameQueryHandler.java`: filter the
     `findAllMatchesByTeamIdsAndSource` results with `isPlayed()` at the point they are collected
     (before `summarizeCompetitions` / player-result summaries), so win/loss/draw counts and
     season lists exclude scheduled fixtures.
   - `.../application/club/find/FindFederatedClubCompetitionDetailsQueryHandler.java`: apply
     `isPlayed()` in `isVisibleMatch` (or where the match list is assembled) so the competition
     detail list and its summaries exclude SCHEDULED rows. Decision: scheduled fixtures are not
     shown yet (analysis 4.5 says lists *may* include them; showing them belongs to FEAT-00092's
     calendar). Record the decision in `# Notes`.
   - `.../application/stats/find/FindCommunityStatisticsQueryHandler.java`: no code change if the
     counts/seasons it consumes are already PLAYED-filtered (step 5); verify its
     `countAllMatches`/`findAllSeasons` wiring.

5. **JPA: query-level PLAYED filters.** In
   `tt-data-league-core-repository-jpa/src/main/java/org/cttelsamicsterrassa/data/core/repository/jpa/match/impl/MatchRepositoryHelper.java`,
   follow the existing status JPQL pattern used by `findScheduledBackfillCandidates`
   (`m.status = org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus.PLAYED`):
   - `search` and `countSearch`: add `and m.status = :status` bound from
     `criteria.getStatus()` (defaults PLAYED via step 2).
   - `searchByFragmentsInName`: add the PLAYED predicate.
   - `findAllSeasons` and `countBySeason`: add the PLAYED predicate.
   - `findAllBySource`: add the PLAYED predicate (its port method `findAllMatchesBySource` has no
     production caller today; filter it so the read-side contract is uniform).
   - Leave `findAllByTeamIds`, `findAllByTeamIdsAndSource` and
     `findAllByTeamIdsAndSourceAndSeasonAndCompetition` unfiltered (handler-level filtering and
     the K9 consolidation exception).
   - `findAllSeasonsBySource` and `findAllCompetitionsBySourceAndSeason` (REST `MatchController.options`)
     stay unfiltered in this feature so season/competition dropdowns still list a season whose
     actas are all pending; record this as an explicit decision in `# Notes`.
   - In `.../match/impl/MatchRepositoryJpa.java`, replace the inherited
     `countAllMatches()` (`JpaRepository.count()`) with a dedicated helper query
     `select count(m) from MatchJPA m where m.status = PLAYED`.

6. **JPA: read-side index.** Add the `(source, season, competition, status)` index (deferred from
   FEAT-00077/00078) to the `@Table` index list of `.../jpa/match/model/MatchJPA.java`, named
   consistently with the existing indexes. It is created by `ddl-auto: update`; no other schema
   change.

7. **REST and MCP DTOs.** Add an additive `String status` component **at the end** of the records
   and map it from the read model's status (`name()`):
   - `tt-data-league-api-rest/src/main/java/.../api/rest/match/MatchDto.java` and `MatchDetailDto.java`.
   - `tt-data-league-api-mcp/src/main/java/.../api/mcp/match/MatchDto.java` and `MatchDetailDto.java`.
   No controller/tool signature changes; `MatchController`/`MatchMcpTools` mappings pick the new
   component up through `from(...)`. GraphQL module has no sources; no change.

8. **Domain tests.** JUnit 5 + Mockito, one mixed SCHEDULED/PLAYED fixture per affected handler:
   - `MatchOutcomeTest`: SCHEDULED match (tie-eligible and non-tie-eligible competition) yields
     empty for both `teamOutcome` and `playerOutcome`; existing winner-less PLAYED tie tests keep
     passing (builder default is PLAYED; set `.status(...)` explicitly in new fixtures).
   - `MatchTest`/criteria tests: `isPlayed()`; `MatchSearchCriteria` defaults to PLAYED and
     rejects null.
   - `FindMatchDetailsQueryHandlerTest`: a SCHEDULED fixture in the team list is excluded from
     form/win-rate/streak and appears as the current match with status SCHEDULED.
   - `FindPlayerDetailsQueryHandlerTest`, `FindClubDetailsQueryHandlerTest`,
     `FindFederatedClubDetailsQueryHandlerTest` (add one if missing),
     `FindFederatedClubCompetitionDetailsQueryHandlerTest`,
     `FindClubsByStringInNameQueryHandlerTest` (add if missing) and
     `FindCommunityStatisticsQueryHandlerTest`: stub a mix and assert scheduled rows do not
     affect stats, counts or seasons.
   - Read-model/DTO tests: REST `MatchDtoTest`/MCP equivalents (extend existing suites) assert the
     `status` string for both values.

9. **JPA tests.** Extend `ImportSchemaTest` or add
   `tt-data-league-core-repository-jpa/src/test/java/.../match/MatchStatusReadFilteringJpaTest.java`
   (`@SpringBootTest` on `JpaTestApplication`, H2, like `ScheduledMatchBackfillRepositoryJpaTest`):
   persist one SCHEDULED and one PLAYED match per shape and assert:
   - `searchMatches`/`countMatches` default to PLAYED and honour an explicit criteria status;
   - `findAllMatchesByFragmentsInName` excludes SCHEDULED;
   - `findAllSeasons`, `countMatchesBySeason` and `countAllMatches` exclude SCHEDULED (a season
     with only scheduled matches disappears);
   - `findAllMatchesBySource` excludes SCHEDULED;
   - `findAllMatchesByTeamIds`, `findAllMatchesByTeamIdsAndSource` and
     `...AndSourceAndSeasonAndCompetition` **include** SCHEDULED (consolidation/K9 guard);
   - the new index exists on H2 only if the existing test style already asserts indexes;
     otherwise skip.

10. **Documentation.**
    - `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md`: update the `match_record`
      section with the new `(source, season, competition, status)` index and extend the
      "Repository lookup behavior" table with the PLAYED-filtered queries and the deliberately
      unfiltered team-id lookups.
    - If `tt-data-league-api-rest`/`tt-data-league-api-mcp` READMEs document the match DTO shape,
      add the new `status` field; otherwise note the additive API change there.

11. **Validation.** Run `mvn -pl tt-data-league-core-domain -am test`,
    `mvn -pl tt-data-league-core-repository-jpa -am test`, `mvn -pl tt-data-league-import -am test`,
    `mvn -pl tt-data-league-api-rest -am test`, `mvn -pl tt-data-league-api-mcp -am test`, then the
    full `mvn test`. Confirm `InMemoryRepositories.Matches` still compiles and behaves (it stores
    domain objects; handler-level filtering makes its status-blindness safe, but add coverage if
    consolidation tests rely on it). Record results under `# Notes`.

# Implementation Guidelines

- Affected modules: domain (application + shared model), JPA repository, api-rest, api-mcp;
  documentation (`rfetm-datamodel.md`, module READMEs).
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add
  external ids to `FederatedClub` or `FederatedPlayer`. No SQL or JPQL outside the JPA module.
- No schema/column change: `match_record.status` comes from FEAT-00077; only the deferred
  `(source, season, competition, status)` index is added, created by `ddl-auto: update`.
- Preserve behavior for existing consumers: `Match.builder()` defaults to `PLAYED` and the column
  defaults to `'PLAYED'`, so existing tests and reads keep passing; new SCHEDULED fixtures must
  set `.status(MatchStatus.SCHEDULED)` explicitly.
- Keep the split contract of "Filtering contract" intact: no status predicates on the
  `findAllByTeamIds*` queries (K9 consolidation exception); handler streams use `Match.isPlayed()`.
- DTO/read-model record components are added **at the end only** (additive API); do not reorder
  existing components.
- MatchOutcome is the central safety net (K1): new statistics route outcome decisions through it
  or an explicit `isPlayed()` predicate; never rely on `winnerTeam == null` meaning "played draw".
- Out of scope:
  - Showing SCHEDULED fixtures in lists or the season calendar UI (FEAT-00092).
  - `source_fixture_id` (FEAT-00083), jornada progress (FEAT-00084), processor lifecycle (FEAT-00081).
  - GraphQL module (no sources today).
  - Deriving postponed/overdue states.
- Ship with or before FEAT-00081 (T6); otherwise SCHEDULED rows created by the processors leak
  into statistics (risk K1).
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for the index and lookup
  behavior, and module READMEs if they document the match DTO shape.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size M, Slice 1: lifecycle foundation. Depends on: FEAT-00077 (T2).
- Must ship with or before FEAT-00081 (T6): otherwise scheduled rows leak into statistics (risk K1).
- 2026-09-28: Build plan written; status `idea` â†’ `planned`. Decisions:
  - **Handler-level filtering for team-scoped collections, query-level for corpus reads.**
    `findAllByTeamIds*` JPA queries stay unfiltered so consolidation (K9/R12) and future FEAT-00092
    list views can see SCHEDULED rows; handlers apply `Match.isPlayed()` before aggregating.
    `search`/`countSearch`/fragment search/`findAllSeasons`/`countBySeason`/`findAllBySource` and
    the new PLAYED `countAllMatches` query filter in JPQL.
  - **`MatchSearchCriteria` gains a `status` component defaulting to `MatchStatus.PLAYED`** so
    search keeps today's behavior without caller edits; there is deliberately no "all statuses"
    option yet.
  - **SCHEDULED fixtures are not shown in the federated-club competition list** in this feature;
    analysis 4.5 only says lists *may* include them, and showing them belongs to FEAT-00092.
  - **`findAllSeasonsBySource` / `findAllCompetitionsBySourceAndSeason` (REST `MatchController.options`)
    stay unfiltered**, so a season whose actas are all pending still appears in the dropdown while
    its search results exclude scheduled rows.
  - The `(source, season, competition, status)` index deferred by FEAT-00077/00078 is delivered here.
  - `findAllMatchesBySource` has no production caller; it is still PLAYED-filtered for a uniform
    read-side contract.
- 2026-09-28: Implemented and validated (status `idea` → `planned` → `ready` → `in-progress`).
  Delivered exactly per plan: `Match.isPlayed()`, `MatchOutcome` SCHEDULED guard + javadoc,
  `MatchSearchCriteria.status` (default PLAYED, null rejected), `status` components on
  `MatchSearchReadModel`/`MatchDetailReadModel` and additive `String status` on REST/MCP
  `MatchDto`/`MatchDetailDto`, handler-level `isPlayed()` filtering in match/player/club/
  federated-club/competition/club-search handlers, JPA PLAYED predicates on `search`/`countSearch`
  (`:status` param)/`searchByFragmentsInName`/`findAllSeasons`/`countBySeason`/`findAllBySource`,
  new `countAllPlayed` replacing `JpaRepository.count()` in `countAllMatches`, and the
  `idx_match_source_season_competition_status` index on `match_record`.
  - Deviations: `FindCommunityStatisticsQueryHandler` has no own mixed-status test — it consumes
    only the counts/seasons from the now-filtered JPA queries, which `MatchStatusReadFilteringJpaTest`
    covers; the player handler test seeds a defensive lineup on a SCHEDULED match (storage cannot
    produce one) to prove the guard.
  - Validation: `mvn test` full reactor — core-domain 142 tests (only the 4 pre-existing
    `InitialUserProvisioningServiceTest` failures), JPA 65 green (5 new
    `MatchStatusReadFilteringJpaTest`), import 232 (only the 8 pre-existing processor failures),
    import-runtime 18, api-rest 65, api-mcp 15, api-runtime 16, graphql and frontend green.
    No new failures vs the documented FEAT-00075/00077/00078 baseline.
