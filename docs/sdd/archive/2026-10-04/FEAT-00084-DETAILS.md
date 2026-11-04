# Build Plan
Source task: **T8** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.6; requirement R7; gap G5).

## Acceptance Criteria
- [x] MatchRepository.findRoundProgress(source, season) returns current round, last complete round and scheduled/played counts per competition/group/phase
- [x] Progress is exposed in the import run result, the import-resource read model and the CLI summary
- [x] Progress is informational and never used to skip files
- [x] Test with the FCTT 2026-2027 tercera-nacional/G1 shape yields current jornada 1 and no last complete jornada
- [x] README documents the progress output

## Baseline (verified 2026-09-28, after commit 4a457f8 / FEAT-00083)

- The G5 half about `lastProcessedDate` is already done by FEAT-00082 (`ImportResource.finishProcessing(boolean, ZonedDateTime)`).
  This feature only adds the jornada-progress half.
- `Match` carries `source`, `season`, `competition`, `groupNumber` (nullable), `round` (int, `NOT NULL`),
  `phase` (nullable) and `status` (`SCHEDULED` / `PLAYED`, FEAT-00077). There is no gender column; the
  natural key (`findMatchByNaturalKey`) is `(competition, season, groupNumber, round, phase, home, away)`,
  so progress is grouped by `(competition, groupNumber, phase)` within `(source, season)`.
- `match_record` already has `idx_match_source_season_competition_status (source, season, competition, status)`,
  which serves a `(source, season)` grouped count. No schema change is needed.
- `MatchRepository` implementors: `MatchRepositoryJpa` and `InMemoryRepositories.Matches` (import tests) only.
- `NavigatorImportExecutionService.execute` (import module) is the single execution path for the CLI
  (`App.run` → `ImportExecutionService.execute`) and the administration API
  (`NavigatorBackedImportResourceProcessService.process` → `ImportProcessResult` →
  `ImportProcessResultDtoMapper` / `ImportRunStatusDtoMapper` → `ImportProcessResultDto`). The request
  season is optional (`ImportExecutionRequest.season()`); the API always passes the resource's season,
  the CLI only with `--season`.
- The import-resource read model is `ImportResourceDto`, built only by
  `FindImportResourcesBySourceQueryHandler` and served by `GET .../import/list_by_source`
  (`ImportResourceController`). It has `lastProcessedDate` but no progress.
- `App.run` logs `"{} import finished: {}"` with the `ImportExecutionResult` `toString`; there is no
  dedicated summary block.
- Real shape (`C:\git\fctt-extract\resources\actas-json\2026-2027\male\tercera-nacional\G1`): jornada 1 has
  3 published and 3 unpublished actas; jornada 2 has 6 unpublished. Stored: round 1 = 3 PLAYED + 3 SCHEDULED,
  round 2 = 6 SCHEDULED → current round 1, last complete round none, scheduled 9, played 3.

## Contracts

1. **Domain values** (`tt-data-league-core-domain/.../domain/match/model/`):
   - `record RoundStatusCount(String competition, Integer groupNumber, String phase, int round,
     MatchStatus status, long matches)`: one grouped row read from storage. `status` required,
     `matches >= 1`.
   - `record RoundProgress(ImportSource source, Season season, String competition, Integer groupNumber,
     String phase, Integer currentRound, Integer lastCompleteRound, long scheduledMatches, long playedMatches)`.
     `source`/`season` required; counts non-negative and `scheduledMatches + playedMatches > 0`;
     `currentRound` is `null` when nothing is PLAYED; `lastCompleteRound` is `null` when the lowest stored
     round still has a SCHEDULED match; `lastCompleteRound != null` ⇒ `currentRound != null` and
     `lastCompleteRound <= currentRound`. Violations throw `IllegalArgumentException`.
   - `final class RoundProgressCalculator` with
     `static List<RoundProgress> compute(ImportSource source, Season season, Collection<RoundStatusCount> counts)`,
     the one place the rule lives (shared by JPA and in-memory). Per `(competition, groupNumber, phase)` key
     (null-safe equality):
     - `currentRound` = max round with at least one PLAYED match (R7), else `null`;
     - `lastCompleteRound` = highest stored round `r` such that no stored match with round `<= r` is
       SCHEDULED, else `null` (analysis 4.6). Only stored rounds count: a gap in round numbers is not a
       SCHEDULED match, and no "total rounds" is inferred (FCTT exports a sliding window);
     - `scheduledMatches` / `playedMatches` = sums by status.
     Output sorted by competition, groupNumber, phase (nulls last on each), so results are deterministic.
2. **Domain port** `MatchRepository.findRoundProgress(ImportSource source, Season season)` →
   `List<RoundProgress>`: abstract (no default), both arguments required (`NullPointerException`),
   always source-scoped, counts matches of every status, empty list when the source/season has no
   matches. Javadoc states the rule by referring to `RoundProgressCalculator` and that the result is
   informational (never an import-skip signal).
3. **JPA** (`tt-data-league-core-repository-jpa/.../match/impl/`):
   - `RoundStatusCountProjection(String competition, Integer groupNumber, String phase, Integer round,
     MatchStatus status, Long matches)` in the style of `ScheduledMatchBackfillCandidateProjection`.
   - `MatchRepositoryHelper.countByRoundAndStatus(Source source, String season)`:
     `select new ...RoundStatusCountProjection(m.competition, m.groupNumber, m.phase, m.round, m.status, count(m))
     from MatchJPA m where m.source = :source and m.season = :season
     group by m.competition, m.groupNumber, m.phase, m.round, m.status`.
   - `MatchRepositoryJpa.findRoundProgress`: `Source.valueOf(source.name())`, `season.toString()`, map the
     JPA `MatchStatus` to the domain enum by name, then `RoundProgressCalculator.compute`. Read-only.
4. **Import execution result** (`tt-data-league-import/.../shared/execution/`):
   - `ImportExecutionResult` gains a trailing `List<RoundProgress> roundProgress` (null → empty; the
     existing 6- and 7-arg constructors keep working with an empty list).
   - `NavigatorImportExecutionService` gains a `MatchRepository` dependency on the `@Autowired`
     constructor (Spring already provides `MatchRepositoryJpa` in both runtimes). The convenience
     constructors pass `null`, meaning "progress not computed" (used by navigator-only tests).
   - In `execute`, after traversal and post-processing: when the request has a season and the
     repository is present, `roundProgress = matchRepository.findRoundProgress(source, season)`; otherwise
     an empty list. It is computed for SUCCESS and FAILURE runs alike (it reflects what is stored), but not
     when the traversal itself threw (that early return keeps an empty list). A runtime exception from the
     query is recorded as an `ImportExecutionIssue("round-progress", "", message)` and makes the status
     `FAILURE` (no swallowed error, no success-shaped fallback). Progress never feeds
     `ImportRunStatusPolicy`, traversal, or file selection.
   - Without a season (CLI run with no `--season`) progress is **not** computed: no implicit season is
     selected (AGENTS: never silently default a season).
5. **Administration run result** (core-domain):
   - `ImportProcessResult` gains a trailing `List<RoundProgress> roundProgress` (null → empty); the
     existing 7-, 11- and 12-arg constructors and the `success/empty/failure` factories default to empty.
   - New `application/importresource/shared/dto/RoundProgressDto(String competition, Integer groupNumber,
     String phase, Integer currentRound, Integer lastCompleteRound, long scheduledMatches, long playedMatches)`
     and a small `RoundProgressDtoMapper.toDtos(List<RoundProgress>)` (package-public, used by both
     handlers below). Source and season are omitted from each row because the enclosing DTO carries them.
   - `ImportProcessResultDto` gains a trailing `List<RoundProgressDto> roundProgress` (null → empty); the
     existing convenience constructors pass an empty list. `ImportProcessResultDtoMapper.dto` and
     `ImportRunStatusDtoMapper.toResultDto` fill it.
   - `NavigatorBackedImportResourceProcessService.process` copies `result.roundProgress()` into
     `ImportProcessResult`. The API always runs with the resource's season, so the terminal
     `process_status` result always carries progress for that `(source, season)`.
6. **Import-resource read model** (core-domain):
   - `ImportResourceDto` gains a trailing `List<RoundProgressDto> roundProgress`.
   - `FindImportResourcesBySourceQueryHandler` injects `MatchRepository` and, for each `ACTAS` resource,
     sets progress from `findRoundProgress(resource.getSource(), resource.getSeason())`, memoised per season
     within one `handle` call (several resources of one season share one query). Non-`ACTAS` resources get
     an empty list. Progress is derived live from `match_record`; nothing is stored on `ImportResource`.
7. **CLI summary** (`tt-data-league-import-runtime/.../App.java`): after the existing
   `"{} import finished: {}"` line, log one line per progress row:
   `"{}/{} progress {} G{} {}: current round {}, last complete round {}, scheduled {}, played {}"`
   (`-` for null group/phase/rounds). When the run had no `--season`, log once:
   `"{} round progress not computed: run without --season"`. When the season has no stored matches, log
   `"{}/{} round progress: no stored matches"`. `ImportExecutionResult.toString` must not print the
   progress list twice: the dedicated lines are the summary. The compatibility constructor's
   `AppSupport.result` keeps an empty list.

## Implementation order

1. **Domain values and calculator.** Add `RoundStatusCount`, `RoundProgress`, `RoundProgressCalculator`.
   Tests `RoundProgressCalculatorTest` / `RoundProgressTest` (core-domain):
   - FCTT tercera-nacional/G1 shape (round 1: 3 PLAYED + 3 SCHEDULED, round 2: 6 SCHEDULED) →
     current 1, last complete `null`, scheduled 9, played 3;
   - all rounds PLAYED (legacy season) → current = last complete = max round;
   - rounds 1–2 PLAYED, round 3 mixed, round 4 SCHEDULED → current 3, last complete 2;
   - only SCHEDULED → current `null`, last complete `null`;
   - a PLAYED round after a SCHEDULED one (postponed fixture: round 1 has one SCHEDULED, round 2 all
     PLAYED) → current 2, last complete `null`;
   - gap in round numbers (1, 3 all PLAYED) → last complete 3;
   - null group and null phase keys are grouped separately from non-null ones and sort last;
     two phases reusing round numbers (BCNESA) are independent;
   - `RoundProgress` invariant violations throw.
2. **Domain port.** Add `findRoundProgress` to `MatchRepository` with javadoc.
3. **JPA.** Projection, helper query, `MatchRepositoryJpa.findRoundProgress`. Test
   `match/MatchRoundProgressJpaTest` in the style of `MatchStatusReadFilteringJpaTest`: persist the G1
   shape for FCTT 2026-2027 plus decoys (same competition under RFETM, same source in 2025-2026, another
   group) → exactly one G1 row with 1 / `null` / 9 / 3, and decoys do not leak; `null` group/phase rows;
   empty source/season → empty list; `null` arguments → `NullPointerException`.
4. **In-memory repository** (`tt-data-league-import/src/test/.../process/InMemoryRepositories.Matches`):
   `findRoundProgress` groups stored matches by `(competition, groupNumber, round, phase, status)` for the
   source/season and delegates to `RoundProgressCalculator`. Case in `InMemoryMatchRepositoryTest`.
5. **Execution service.** Contract 4: `ImportExecutionResult.roundProgress`, the `MatchRepository`
   constructor parameter, computation and failure handling in `execute`. Update the three constructor
   call sites in `NavigatorImportExecutionServiceTest` and `NavigatorImportExecutionLifecycleTest`.
   Tests (in `NavigatorImportExecutionLifecycleTest`, wired with the in-memory repositories):
   - FCTT 2026-2027 tercera-nacional/G1 snapshot built from the existing fixtures
     (`acta_fctt_2026_published.json`, `acta_fctt_unpublished.json` / FEAT-00075 `IncrementalActaFixturesTest`
     helpers) with 3 published + 3 pending jornada-1 actas and 6 pending jornada-2 actas, imported with
     season 2026-2027 → `roundProgress` has the G1 row current 1, last complete `null`, scheduled 9, played 3;
   - importing the jornada-1 published versions of the 3 pending fixtures afterwards → current 1, last
     complete 1, scheduled 6, played 6;
   - informational only: the same snapshot run twice gives identical `filesSeen` / `dispatched` / `skipped`
     both times, and a progress row with a complete round does not reduce `filesSeen` (no file is skipped
     because its round is complete);
   - run without season → empty `roundProgress`;
   - a repository whose `findRoundProgress` throws → status `FAILURE` with a `round-progress` issue.
6. **Administration result and DTOs.** Contract 5: `ImportProcessResult`, `RoundProgressDto`,
   `RoundProgressDtoMapper`, `ImportProcessResultDto`, both mappers, and
   `NavigatorBackedImportResourceProcessService`. Tests: `NavigatorBackedImportResourceProcessServiceTest`
   (progress copied), `FindImportRunStatusQueryHandlerTest` (progress reaches `ImportProcessResultDto`),
   and a `StartImportProcessCommandHandlerTest` case only if its fixtures construct results positionally.
7. **Import-resource read model.** Contract 6: `ImportResourceDto`, `FindImportResourcesBySourceQueryHandler`
   (constructor gains `MatchRepository`). New `FindImportResourcesBySourceQueryHandlerTest` (core-domain,
   stub `MatchRepository` / `ImportResourceRepository`): an ACTAS resource carries progress; two resources
   of one season trigger one `findRoundProgress` call; a non-ACTAS resource has an empty list;
   `lastProcessedDate` is unchanged. Check `ImportResourceControllerTest` still passes (JSON is additive).
8. **Runtime CLI.** Contract 7 in `App.run`. `AppTest`: a stubbed `ImportExecutionService` returning
   progress rows logs one line per row; a season-less run logs "not computed"; constructor updates for
   the compatibility path.
9. **Documentation.**
   - `tt-data-league-import-runtime/README.md`: a "Jornada progress" subsection after "Run status and
     counters": definition of current round and last complete round (per source, season, competition,
     group, phase), scheduled/played counts, the FCTT G1 example, the CLI log lines, "not computed without
     `--season`", derived from stored matches only (no total rounds; FCTT sliding window), and that it is
     informational and never skips files.
   - `tt-data-league-api-runtime/README.md`: the `process_status` terminal result and
     `GET .../import/list_by_source` rows carry `roundProgress` (fields listed).
   - `rfetm-datamodel.md`: no change (no columns, constraints or table behavior change); state this in the
     feature notes on completion.
10. **Validation.** `mvn -pl tt-data-league-core-repository-jpa -am test`, `mvn -pl tt-data-league-import-runtime -am test`,
    then the full `mvn test` from the root.

# Implementation Guidelines

- Affected modules: domain, JPA, import (execution service and in-memory test repository), import runtime
  (CLI summary), api-runtime README. api-rest needs no code change: its JSON grows additively through
  the core-domain DTOs.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- Progress is **derived** from `match_record` on every read; do not add columns or tables and do not
  store progress on `ImportResource`. No schema change is expected; if one becomes necessary, update
  `rfetm-datamodel.md` in the same change.
- The rule lives only in `RoundProgressCalculator` (domain). The JPA and in-memory repositories only
  produce `RoundStatusCount` rows; do not re-implement the rule in JPQL or in tests.
- Informational only: progress must never feed traversal, file selection, `--season` handling,
  `ImportRunStatusPolicy`, or consolidation. Traversal stays full-season (G9).
- Do not infer a season or a total number of rounds. Without a season, progress is "not computed", said
  explicitly; rounds beyond the stored snapshot are unknown.
- The progress query failing is a reported `round-progress` issue and a `FAILURE` status, never a
  swallowed error or an empty-list fallback.
- Out of scope: persisting progress history per run, a REST endpoint dedicated to progress, MCP/GraphQL
  exposure, "postponed/overdue" derivation and the season calendar (analysis T16), and preview classification
  counts (T12, which will reuse `findRoundProgress`).

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P1, size S, Slice 3: visibility and safety. Depends on: FEAT-00077 (T2), FEAT-00081 (T6).
- 2026-09-28: Build plan written against the code after FEAT-00083 (commit 4a457f8); status `idea` →
  `planned`. Decisions:
  - `lastProcessedDate` (the other half of G5) was already delivered by FEAT-00082; this feature covers
    jornada progress only.
  - Grouping key is `(competition, groupNumber, phase)` within `(source, season)`, mirroring the match
    natural key; there is no gender column, so competition must already disambiguate it.
  - Definitions: current round = highest round with at least one PLAYED match (R7); last complete round =
    highest stored round with no SCHEDULED match at or below it (analysis 4.6). A postponed early fixture
    therefore keeps "last complete" low while "current" advances — intended, it is what an operator needs
    to see.
  - Progress is computed once in `NavigatorImportExecutionService`, so the CLI and the API share it, and
    live in `FindImportResourcesBySourceQueryHandler` for the read model (memoised per season per call).
  - Verified real shape of FCTT 2026-2027 `male/tercera-nacional/G1`: round 1 = 3 published + 3 pending,
    round 2 = 6 pending → current 1, last complete none, scheduled 9, played 3. R7's "3 of 6" refers to
    round 1 only.
- Open question 1 (recommendation: keep as planned): a failing progress query makes the run `FAILURE`
  even though the import itself succeeded. The alternative is a warning finding with no status change;
  choose it only if operators prefer a successful run status over a strict failure.
- Open question 2 (recommendation: not now): CLI runs without `--season` report "not computed". Reporting
  every season of the source would be explicit but noisy for legacy seasons; revisit with T16 if needed.
- 2026-09-28: Executed the build plan; status `planned` → `in-progress`. Both open questions were
  confirmed by the user as "keep as planned": a failing progress query is a `round-progress` issue and a
  `FAILURE` run, and a season-less CLI run logs "not computed". Delivered, in plan order:
  - `RoundStatusCount`, `RoundProgress`, `RoundProgressCalculator` in the domain with
    `RoundProgressCalculatorTest` (11 cases, including the G1 shape, the postponed fixture, the round
    gap, null-group/null-phase grouping and BCNESA phase reuse) and `RoundProgressTest` (invariants).
  - `MatchRepository.findRoundProgress` as an abstract, source-scoped port.
  - `RoundStatusCountProjection`, `MatchRepositoryHelper.countByRoundAndStatus` and
    `MatchRepositoryJpa.findRoundProgress`, tested by `MatchRoundProgressJpaTest` (G1 shape with
    other-source, other-season and other-group decoys; null group/phase rows; empty scope; null
    arguments).
  - `InMemoryRepositories.Matches.findRoundProgress` grouping stored matches per round and status and
    delegating to the calculator; cases in `InMemoryMatchRepositoryTest`.
  - `ImportExecutionResult.roundProgress` plus the `MatchRepository` dependency of
    `NavigatorImportExecutionService`, computed after traversal and post-processing only when the request
    carries a season; cases in `NavigatorImportExecutionLifecycleTest` including the snapshot rebuilt from
    the FCTT published/unpublished fixtures and its pending→published upgrade.
  - `ImportProcessResult.roundProgress`, `RoundProgressDto`, `RoundProgressDtoMapper`,
    `ImportProcessResultDto.roundProgress`, both mappers, and the copy in
    `NavigatorBackedImportResourceProcessService`; cases in
    `NavigatorBackedImportResourceProcessServiceTest` and `FindImportRunStatusQueryHandlerTest`.
  - `ImportResourceDto.roundProgress` filled by `FindImportResourcesBySourceQueryHandler` (constructor now
    takes `MatchRepository`), memoised per season; new `FindImportResourcesBySourceQueryHandlerTest`.
    `ImportResourceControllerTest` still passes: the JSON only grows.
  - CLI summary lines in `App.run`; `AppTest` captures the log with a Logback `ListAppender`.
- 2026-09-28: Two small plan deviations, both deliberate:
  - The 10-arg `NavigatorImportExecutionService` constructor was kept as an overload passing
    `matchRepository = null` ("progress not computed"), so `NavigatorImportExecutionServiceTest` needed no
    constructor change; only the lifecycle test was rewired to the in-memory repository.
  - `ImportExecutionResult` overrides `toString` to leave `roundProgress` out, so the run log shows the
    dedicated progress lines once instead of also dumping them inside the result line (contract 7).
- 2026-09-28: `rfetm-datamodel.md` intentionally unchanged: progress is derived from `match_record` with
  the existing `idx_match_source_season_competition_status` index, and no column, relationship, cascade,
  constraint or table behavior changed.
- 2026-09-28: Validation. `mvn test` from the root: BUILD SUCCESS, all modules. Acceptance criteria
  verified against the delivered behavior: the port exists and returns per competition/group/phase rows;
  progress reaches the run result, the read model and the CLI; the G1 shape yields current round 1 with no
  last complete round in the calculator, JPA and end-to-end import tests; the import-runtime and
  api-runtime READMEs document it. Informational-only is pinned by
  `progressIsInformationalAndNeverSkipsFilesOfACompleteRound` and by the fact that progress is computed
  after `ImportRunStatusPolicy.statusOf` and never reaches the navigators. Status `in-progress` →
  `in-review`.
- 2026-09-28: Closed on explicit user request; status `in-review` → `done`. All five acceptance criteria
  are checked in both the registry and this file, each verified by a delivered test. Follow-ups left for
  other features: FEAT-00088 will reuse `findRoundProgress` for preview classification counts and
  FEAT-00092 (T16) owns the season calendar that could turn "no total rounds inferred" into a real
  round count. Nothing was archived.
