# Build Plan
Source task: **T3** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.2 backfill; gaps G3, G17; risks K5, K15).

## Acceptance Criteria
- [x] An opt-in runtime command marks as SCHEDULED matches with no game result, no winner_team_id and null or 0-0 games won
- [x] The command is scoped by source and season and supports report and write modes; report mode performs no writes
- [x] Tests cover legacy empty actas, decided 0-0 matches, and a real played match that must stay PLAYED
- [x] The runtime README documents the command, its arguments and modes

## Backfill rule (the contract every step implements)

A `match_record` row is a **backfill candidate** when all of these hold:

- `source = :source` and `season = :season` (both required; never unscoped);
- `status = 'PLAYED'` (so a rerun finds nothing new; the command is idempotent);
- `winner_team_id IS NULL`;
- `home_games_won` and `away_games_won` are both null or both `0`;
- `home_sets_won` and `away_sets_won` are both null or both `0` (a stricter guard than the analysis, per K5:
  a header with sets won is not an empty acta);
- no `game` row of the match **has a result**, where a game has a result when `winner IS NOT NULL`, or
  `home_sets_won > 0`, or `away_sets_won > 0`, or it has at least one `set_score` row. A `not_played` game that
  still names a winner (a walkover) counts as a result, so its match stays `PLAYED`.

This covers the legacy RFETM empty actas and the G17 "decided 0-0" actas. It never selects a real played match:
a played match has at least one game with a winner or a set score.

Marking a candidate `SCHEDULED` must leave it satisfying the FEAT-00077 invariant, or the row can no longer be read
back (`Match.of(...)` rejects a `SCHEDULED` match with a winner or games/sets won, and the writers own the "no
children" half). In one transaction, write mode therefore:

1. deletes the match's `doubles_pair` rows, then its `set_score` rows (none by the rule; the delete is defensive and
   ordered for the FKs), then its `game` rows, then its `lineup` rows;
2. sets `home_games_won`, `away_games_won`, `home_sets_won` and `away_sets_won` to null;
3. sets `status = 'SCHEDULED'`.

The only children deleted are placeholder games that carry no result, plus the lineups of a fixture that was not
played. The source actas are unchanged, and FEAT-00081 recreates them when the acta is published.

## Steps

1. **Domain candidate value.** Add `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/match/model/ScheduledMatchBackfillCandidate.java`,
   an immutable record `(UUID matchId, String competition, Integer groupNumber, int round, String phase,
   LocalDate matchDate, UUID homeTeamId, UUID awayTeamId, int gameCount, int lineupCount)`. It is used for report
   output and the write summary. It is framework-free and validates `matchId`, `homeTeamId` and `awayTeamId` as
   non-null.

2. **Domain port.** Add `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/match/repository/ScheduledMatchBackfillRepository.java`.
   It is a focused port, kept separate from `MatchRepository` so that FEAT-00080's `replaceMatchContent` /
   `updateSchedule` additions do not collide with it, and so that every existing `MatchRepository` implementation does
   not need a new method:
   ```java
   public interface ScheduledMatchBackfillRepository {
       /** Candidates per the backfill rule, ordered by competition, group, round, match id. Read-only. */
       List<ScheduledMatchBackfillCandidate> findScheduledBackfillCandidates(ImportSource source, Season season);

       /**
        * Marks the given candidates SCHEDULED in one transaction: deletes their doubles pairs, set scores,
        * games and lineups, nulls games/sets won, sets status. Re-checks the rule, the source and the season
        * for every id and fails (IllegalStateException, nothing written) when any id is no longer a candidate.
        */
       ScheduledMatchBackfillWriteResult markScheduled(ImportSource source, Season season, Collection<UUID> matchIds);
   }
   ```
   Add the record `ScheduledMatchBackfillWriteResult(int matchesUpdated, int gamesDeleted, int lineupsDeleted,
   int setScoresDeleted, int doublesPairsDeleted)` next to the candidate value. Both arguments are required. Null
   `source`/`season` → `NullPointerException`; an empty id collection → a zero result with no writes.

3. **JPA adapter.** Add `tt-data-league-core-repository-jpa/src/main/java/org/cttelsamicsterrassa/data/core/repository/jpa/match/impl/ScheduledMatchBackfillRepositoryJpa.java`
   (`@Component`, `@Transactional`, `@AllArgsConstructor`, following `MatchRepositoryJpa`):
   - Add a JPQL candidate query to `MatchRepositoryHelper`, bound to the JPA `Source` and `MatchStatus.PLAYED`,
     using `not exists (select 1 from GameJPA g where g.match = m and (g.winner is not null or coalesce(g.homeSetsWon,0) > 0
     or coalesce(g.awaySetsWon,0) > 0 or exists (select 1 from SetScoreJPA s where s.game = g)))` and the header
     conditions of the rule. Add projection counts of games and lineups (correlated `count` subqueries or a second
     grouped query keyed by match id; choose whichever H2 and PostgreSQL both accept, and test it on H2).
   - `markScheduled`: re-run the candidate query restricted to `matchIds` and compare the sets. If they differ, throw
     `IllegalStateException` listing the offending ids. Then bulk-delete in FK order with `@Modifying` JPQL on the
     existing helpers: `DoublesPairRepositoryHelper` / `SetScoreRepositoryHelper` (`where x.game.match.id in :ids`),
     `GameRepositoryHelper` and `LineupRepositoryHelper` (`where x.match.id in :ids`). Then run one `@Modifying`
     update on `MatchJPA` that sets the four score columns to null and `status = SCHEDULED`, with a `where id in :ids
     and status = PLAYED` guard. Use the returned row counts for the result. If the updated count differs from the id
     count, throw so that the transaction rolls back. Process ids in chunks of at most 500 inside the single
     transaction to stay under bind-parameter limits.
   - Keep all SQL source- and season-scoped. Do not resolve anything by name.

4. **In-memory implementation.** In `tt-data-league-import/src/test/java/org/cttelsamicsterrassa/data/load/process/InMemoryRepositories.java`,
   add `ScheduledMatchBackfill implements ScheduledMatchBackfillRepository` over the existing `Matches`, `Games`,
   `Lineups`, `SetScores` and `DoublesPairs` stores. It applies the same rule and the same all-or-nothing re-check,
   and rebuilds each updated match through `Match.builder()...status(SCHEDULED)...createExisting()` with null scores.
   Add the removal helpers these stores need (package-private, test-only). This mirrors the JPA contract for the
   service tests.

5. **Backfill service (import module).** Add the package `org.cttelsamicsterrassa.data.load.shared.match.backfill` in
   `tt-data-league-import` with:
   - `ScheduledMatchBackfillMode { WRITE, REPORT }`. It is a dedicated enum rather than a reuse of
     `ConsolidationMode`, because the command is not a consolidation.
   - `ScheduledMatchBackfillSummary` record `(ImportSource source, Season season, ScheduledMatchBackfillMode mode,
     List<ScheduledMatchBackfillCandidate> candidates, ScheduledMatchBackfillWriteResult written)`. `written` is
     `ScheduledMatchBackfillWriteResult` with all counts at zero in report mode.
   - `ScheduledMatchBackfillService` (`@Component`, constructor-injected port) with
     `ScheduledMatchBackfillSummary run(ImportSource source, Season season, ScheduledMatchBackfillMode mode)`:
     find candidates, log one line per candidate (match id, competition, group, round, date, team ids, game and lineup
     counts) and a total. `REPORT` returns without calling `markScheduled`. `WRITE` calls
     `markScheduled(source, season, candidateIds)` once and logs the result. There is no catch-and-continue: failures
     propagate.

6. **Service tests.** Add `tt-data-league-import/src/test/java/org/cttelsamicsterrassa/data/load/shared/match/backfill/ScheduledMatchBackfillServiceTest.java`
   (JUnit 5, in-memory repositories). Seed one source/season with:
   - (a) a legacy empty acta: null scores, no winner, no games, no lineups;
   - (b) a G17 "decided 0-0": header 0-0, no winner, lineups present, every game `notPlayed` with reason
     "Victoria decidida (0-0)", no winner and no sets;
   - (c) a real played match with a winner and games with set scores;
   - (d) a played draw (tie-eligible, no header winner, games with winners);
   - (e) a walkover-style `notPlayed` game that has a winner.

   Also seed a candidate-shaped match in another season and another in another source.

   Assert:
   - `REPORT` lists exactly (a) and (b), and leaves every match, game and lineup unchanged, so report mode performs
     no writes.
   - `WRITE` marks (a) and (b) `SCHEDULED` with null scores and no games or lineups, and leaves (c), (d), (e) and the
     out-of-scope matches `PLAYED` and untouched.
   - A second `WRITE` finds zero candidates.
   - `markScheduled` with a non-candidate id fails and writes nothing.
   - Null source or season fails.

   Where practical, load (a) and (b) from the FEAT-00075 RFETM fixtures through the existing RFETM processor test
   harness (`ImportProcessorsTest` style) rather than hand-built rows, so the test covers the shape the processors
   actually store.

7. **JPA tests.** Add `tt-data-league-core-repository-jpa/src/test/java/org/cttelsamicsterrassa/data/core/repository/jpa/match/ScheduledMatchBackfillRepositoryJpaTest.java`
   (`@SpringBootTest` on `JpaTestApplication`, `@Import(JpaTestSupportConfiguration.class)`, H2, following
   `ImportSchemaTest` setup). Use the same scenarios (a)–(e), plus scope isolation, persisted through the real
   repositories. Assert:
   - the candidate query result and its game/lineup counts;
   - write mode's row counts;
   - after write, `MatchRepository.findMatchById` reads the rows back as `SCHEDULED` with null scores (this proves
     the mapper and invariant accept them), and `GameRepository.findGamesByMatchId` /
     `LineupRepository.findLineupsByMatchId` are empty;
   - (c)–(e) are unchanged;
   - rollback: `markScheduled` with one stale id throws and leaves every child row and status intact (assert in a
     fresh transaction).

8. **Runtime CLI.** In `tt-data-league-import-runtime`:
   - `ImportRuntimeCliContract`: add `BACKFILL_SCHEDULED_MATCHES_ARGUMENT = "--backfill-scheduled-matches"` and add a
     second usage line for the backfill command:
     `--source=... --season=<YYYY-YYYY> --backfill-scheduled-matches[=write|report]`.
   - `ImportRuntimeArguments`: add `boolean backfillScheduledMatches` and `ScheduledMatchBackfillMode
     backfillMode`. Parse them with the same bare/`=write`/`=report`/unsupported-value rules as consolidation by
     generalising `parseConsolidationSelection` into a mode-agnostic helper. Keep the existing error text for the
     consolidation flags.
   - `App.run`: when the backfill flag is present, run the backfill **instead of** an import:
     - require `--season` and fail with `IllegalArgumentException` when it is missing;
     - validate `YYYY-YYYY` with consecutive years instead of the current unchecked `substring` parse (apply the
       same stricter parse to the import path only if it does not change accepted inputs; otherwise leave it);
     - use `--source` (default `rfetm`, as today);
     - reject a combination with `--actas-folder`, `--rfetm-teams-folder`, `--consolidate-clubs*` or
       `--consolidate-players*` with a clear error, so a run is either an import or a backfill;
     - call `ScheduledMatchBackfillService.run` and log the summary.

     Inject the service through the `@Autowired` constructor. Update the navigator-compatibility constructor so it
     still compiles: pass a service that throws `IllegalStateException("backfill not wired")`.

9. **Runtime tests.** Extend `ImportRuntimeArgumentsTest` to cover bare/`=write`/`=report`/unsupported backfill
   values and to check that the flag is absent by default. Extend `AppTest` with a stub or mocked service to check:
   - report and write dispatch with the parsed source and season;
   - a missing season is rejected;
   - a malformed season is rejected;
   - a combination with `--actas-folder` or `--consolidate-clubs` is rejected;
   - no import execution happens when the backfill runs.

10. **Documentation.**
    - `tt-data-league-import-runtime/README.md`: add the three flag forms to the command-line parameters table. Add a
      "Backfill legacy empty and decided 0-0 matches" section with a report-then-write example (PowerShell style,
      matching the existing examples). It must state the rule, that report mode performs no writes, that write mode
      deletes the placeholder games and lineups of the matched rows, that the command is idempotent and requires
      `--season`, and that it does not traverse actas. Update "Execution order and failure behavior".
    - `tt-data-league-import-runtime/AGENTS.md`: add one runtime-contract bullet: the backfill is opt-in, needs an
      explicit source and season, has report and write modes, and is exclusive with import arguments.
    - `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md`: under `match_record`, replace the "stay
      `PLAYED` until the FEAT-00078 backfill runs" sentence with the backfill rule, its write effects (child
      deletion, score nulling) and its source/season scope. Add the new port to the "Repository lookup behavior"
      table.

11. **Validation.** Run `mvn -pl tt-data-league-core-repository-jpa -am test`, `mvn -pl tt-data-league-import -am test`,
    `mvn -pl tt-data-league-import-runtime -am test`, then the full `mvn test`. Report any pre-existing failures
    noted in FEAT-00077 separately from new ones. Then, against a copy of a populated PostgreSQL database, run
    `--source=rfetm --season=2025-2026 --backfill-scheduled-matches=report` and check that the reported set
    includes the 19 G17 "decided 0-0" actas (for example `divisio-honor/1/femenino/acta_27810.json`) plus the legacy
    empty actas, and contains no match with a game result. Then run write, rerun report (expect 0), and record the
    counts under `# Notes`.

# Implementation Guidelines

- Affected modules: domain (port and values), JPA (adapter and queries), import (service and in-memory test
  repositories), import runtime (CLI and wiring), and documentation (runtime README and AGENTS.md,
  `rfetm-datamodel.md`).
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- Dependency direction: the import service depends only on the domain port. The JPA adapter implements it. The
  runtime only parses arguments and delegates. There is no business rule in the runtime and no SQL in the import
  module.
- Opt-in and explicit: the command never runs as a side effect of an import, `--season` is mandatory, and there is
  no "all seasons" or "all sources" shortcut. Report mode runs the same candidate query and never calls the write
  port.
- Write mode is all-or-nothing per run (one transaction). It re-checks every id against the rule inside the
  transaction and fails loudly on drift. There are no partial successes and no broad catches.
- No schema change: the feature uses the existing `status` column from FEAT-00077 and adds no index. The
  `(source, season, competition, status)` index belongs to FEAT-00079.
- Out of scope:
  - BCNESA 4-4 placeholders (G14) and FCTT 6-0 / no-team placeholders (G18). They carry a winner or non-zero
    scores, so the rule does not select them. Repairing them needs source-aware classification (FEAT-00081 /
    FEAT-00076), not this header-level rule.
  - Read-side filtering of `SCHEDULED` rows (FEAT-00079).
  - Upgrading `SCHEDULED` rows to `PLAYED` on re-import (FEAT-00080/00081).
  - An import-API or MCP trigger for the backfill.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size S, Slice 1: lifecycle foundation. Depends on: FEAT-00077 (T2).
- 2026-09-27: Build plan written; status `idea` → `planned`. Decisions:
  - **Child rows are deleted in write mode.** FEAT-00077 made `SCHEDULED` imply no lineups, games, set scores or
    doubles pairs and null header scores, and `Match.of(...)` rejects a `SCHEDULED` row that still has games/sets
    won. G17 "decided 0-0" matches are stored with `not_played` games and lineups, so marking them `SCHEDULED`
    without removing those rows would break the invariant. Nulling the scores (the FEAT-00077 note) is not enough on
    its own. Only result-less placeholder games and the lineups of an unplayed fixture are removed; the source actas
    still hold the data, and FEAT-00081 recreates it when the acta is published. The README must state this
    explicitly. If stakeholders prefer a non-deleting variant, the alternative is to restrict candidates to matches
    with no child rows at all and only report the rest. That alternative would leave G17 unrepaired, so it was not
    chosen.
  - The rule is stricter than the analysis on two points (K5): header sets won must also be null or 0-0, and a
    `not_played` game that names a winner counts as a result.
  - The backfill gets a dedicated domain port (`ScheduledMatchBackfillRepository`) instead of new
    `MatchRepository` methods. This avoids churn in every `MatchRepository` implementation and collisions with
    FEAT-00080's `replaceMatchContent`/`updateSchedule`.
  - The CLI flag is `--backfill-scheduled-matches[=write|report]`, following the consolidation flag conventions
    (bare = write). The backfill is exclusive with import arguments, and `--season` is mandatory, per the
    acceptance criterion "scoped by source and season". A dedicated `ScheduledMatchBackfillMode` enum is used
    instead of `ConsolidationMode`.
  - Ordering vs. FEAT-00079: running the backfill before read-side filtering ships does not make reads worse. The
    rows are already reported as draws today, and after the backfill they have no games or lineups to inflate
    player statistics. Match counts still include them until FEAT-00079.
- 2026-09-28: Implemented (steps 1-10). Deviations and findings:
  - `ScheduledMatchBackfillServiceTest` lives in `org.cttelsamicsterrassa.data.load.process`, not `...shared.match.backfill`, because the `InMemoryRepositories` nested stores are package-private.
  - Scenario (a) loads `acta_rfetm_2026_unpublished.json`; `acta_rfetm_legacy_empty.json` actually carries an administrative 6-0 winner, so it is not a candidate. (b) loads `acta_rfetm_2025_decided_0_0.json`; (c) `acta_doubles.json`; (d) `acta_singles.json`; (e) is the (b) fixture with game 1 given a winner. Out-of-scope rows are hand-built.
  - The `@Modifying` bulk queries use `clearAutomatically = true`; without it the persistence context returned stale `PLAYED` matches after the update.
  - `markScheduled` validates every id before any write, so a stale id writes nothing; the JPA test asserts this in the same test transaction rather than a fresh one.
  - `AppTest.app(...)` helper recursed into itself (StackOverflowError, broken since commit 2e53ea3); fixed to `new App(rfetm, bcnesa, fctt)` so the runtime tests could run.
  - Validation: domain, JPA (4 new tests), import (5 new tests) and runtime (AppTest 7, ImportRuntimeArgumentsTest 11) pass. Pre-existing failures on unmodified HEAD, not from this feature: `InitialUserProvisioningServiceTest` (4, core-domain) and 8 import tests (`BcnesaImportProcessorsTest`, `FcttImportProcessorsTest`, `ImportProcessorsTest`, `TeamToClubConsolidationProcessorTest`) expecting club/team counts that are 0.
  - Step 11 PostgreSQL run (`--season=2025-2026 --backfill-scheduled-matches=report`, then write, then report expecting 0) is NOT done: no populated database is available here. Record its counts here when run.
