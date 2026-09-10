# Build Plan
1. In `RfetmMatchImportProcessor.process`
   (`tt-data-league-import/src/main/java/org/cttelsamicsterrassa/data/load/rfetm/process/RfetmMatchImportProcessor.java:101`),
   replace `int round = context.round();` with a value taken from the payload
   first: `int round = acta.round() != null ? acta.round() : context.round();`,
   mirroring the existing `groupNumber` pattern immediately above it
   (`acta.group() != null ? acta.group() : 0`). When falling back, log a
   warning consistent with the class's existing style (e.g.
   `LOGGER.warn("No jornada in payload for {}; using the day folder {}",
   context.matchReportFile(), context.day())`) so silent path/payload
   mismatches remain visible in import logs.
2. Do not modify `MatchReportContext.round()`
   (`tt-data-league-import/.../shared/process/MatchReportContext.java:72`) or
   its Javadoc's path-vs-payload rationale for `season`/competition — that
   method is shared with `FcttMatchImportProcessor` and
   `BcnesaMatchImportProcessor`, both of which still call `context.round()`
   directly and are out of scope for this feature. It remains the fallback
   value and the source for directory traversal; only RFETM's own call site
   changes.
3. Update the class Javadoc/comment context around
   `RfetmMatchImportProcessor.process` (or a short inline comment at the new
   line) to state that round is read from the payload's `jornada` field with
   the day-folder value as fallback, so a future reader doesn't have to
   re-derive this from the diff.
4. Extend `ImportProcessorsTest`
   (`tt-data-league-import/src/test/java/org/cttelsamicsterrassa/data/load/process/ImportProcessorsTest.java`):
   - Add a test proving the payload wins when it disagrees with the path: call
     the existing private `context(competition, day, sex, localTeamId,
     visitorTeamId, fixture)` helper (~line 240) with `day = "2"` and fixture
     `acta_singles.json` (whose `jornada` is `1`, per
     `tt-data-league-import/src/test/resources/actas/acta_singles.json:6`),
     then assert `match.getRound() == 1` (the JSON value, not the path's `2`).
   - Add a test proving the path fallback still works when `jornada` is
     absent: build a modified `Acta` with `round(null)` using the same
     reconstruction pattern as the existing `doublesActaWithUnlistedHomePlayer`
     helper (~line 221, which rebuilds an `Acta` field-by-field to vary one
     value), wrap it in a `MatchReportContext` with a known `day` (e.g. `"5"`),
     run the processor, and assert `match.getRound() == 5`.
   - Keep `storesTheMatchWithIdentityTakenFromThePath` (~line 97) passing
     unchanged; its fixture's `jornada` (`1`) already equals its path day
     (`"1"`), so the assertion `assertEquals(1, match.getRound())` continues
     to hold under the new precedence and should be left as-is other than,
     optionally, a comment noting it no longer proves path-precedence by
     itself now that the two new tests exist.
5. Run the focused test class
   (`mvn -Dtest=ImportProcessorsTest test` from `tt-data-league-import`), then
   the full `tt-data-league-import` Maven test suite to check for regressions
   in FCTT/BCNESA processors (unchanged, but sharing `MatchReportContext`).
   Review the final diff for unrelated SDD or generated-file changes.

# Implementation Guidelines

- Scope is RFETM only. Do not change `FcttMatchImportProcessor`,
  `BcnesaMatchImportProcessor`, `MatchReportContext`, or any FCTT/BCNESA
  directory navigator or fixture.
- Do not change how the day folder is discovered, validated
  (`DAY_FOLDER_PATTERN`), or used for traversal — `context.day()`/
  `context.round()` remain necessary as the fallback and for natural-key
  lookups elsewhere that still call `context.round()` if any exist outside
  this processor's own `round` computation.
- Keep `int round` typing at the `Match`/`MatchDto` boundary as-is (`Acta.round()`
  is `Integer`/nullable; unwrap only after the null check, never pass a boxed
  `null` into `Match.builder().round(...)`, which takes a primitive `int`).
- No new dependency, no new fixture file needed — reuse
  `acta_singles.json`/`acta_doubles.json` and the existing `Acta`
  reconstruction helper pattern already used in `ImportProcessorsTest`.
- Preserve existing duplicate-detection behavior
  (`matchRepository.findMatchByNaturalKey(...)` still uses whichever `round`
  value was resolved, unchanged in shape).

# Notes

- `Acta` (`tt-data-league-import/.../shared/parse/acta/Acta.java:29`) already
  deserializes the JSON `jornada` field into `Integer round` — it is parsed
  but currently unused.
- `RfetmMatchImportProcessor.process`
  (`tt-data-league-import/.../rfetm/process/RfetmMatchImportProcessor.java:101`)
  currently reads `int round = context.round();`, i.e.
  `MatchReportContext.round()`
  (`tt-data-league-import/.../shared/process/MatchReportContext.java:72`),
  which does `Integer.parseInt(day)` on the day-folder path segment
  (`[season]/[league-competition]/[day]/[sex]/acta.json`). This is the
  path-derived value the feature title refers to.
- `RfetmActasDirectoryNavigator` validates the day folder against
  `DAY_FOLDER_PATTERN = \d+` while traversing
  (`tt-data-league-import/.../rfetm/traverse/RfetmActasDirectoryNavigator.java:70,192-194`).
  That traversal/validation is unrelated to round derivation and should stay
  as-is per the acceptance criteria; only where `round` comes from for the
  `Match` entity should change.
- `Acta.round()` is `Integer` (nullable); `MatchReportContext.round()` /
  `Match.round` are primitive `int` (never null). A design decision is needed
  for reports where `acta.round()` is `null` — likely falling back to the
  path-derived value with a warning log, to preserve current behavior for
  the "several hundred reports" class of payload gaps the `MatchReportContext`
  Javadoc already documents for `season`.
- Existing coverage: `ImportProcessorsTest.storesTheMatchWithIdentityTakenFromThePath`
  (`tt-data-league-import/src/test/java/.../process/ImportProcessorsTest.java:97-114`)
  asserts `match.getRound()` equals `1`, sourced from `singlesContext()`
  (day folder `"1"`). The fixture `acta_singles.json` (and `acta_doubles.json`)
  both already have `"jornada": 1` in their JSON, matching the path value, so
  today's fixtures don't distinguish the two sources — a build plan will need
  a fixture/context where they differ (or `jornada` is missing) to prove the
  new behavior.
- The `MatchReportContext` Javadoc (lines 15-20) currently explains that path
  identity is preferred over the payload for competition and season because
  the payload is unreliable there; this feature narrows that stance
  specifically for round/jornada and should update that Javadoc accordingly
  once implemented, so the documented rationale doesn't contradict the code.
- Implemented: `RfetmMatchImportProcessor.process` now computes `round` via a
  new private `resolveRound(Acta, MatchReportContext)` helper — returns
  `acta.round()` when non-null, otherwise logs
  `LOGGER.warn("No jornada in payload for {}; using the day folder {}", ...)`
  and falls back to `context.round()` (the day-folder value). Diff is a
  4-line call-site change plus a 12-line helper method; `MatchReportContext`,
  `FcttMatchImportProcessor`, and `BcnesaMatchImportProcessor` are untouched.
- Tests: added `storesTheMatchRoundFromThePayloadWhenItDisagreesWithTheDayFolder`
  (day folder `"2"`, fixture `jornada: 1` → asserts round `1`) and
  `fallsBackToTheDayFolderRoundWhenThePayloadHasNoJornada` (Acta rebuilt with
  `round(null)` via a new `withRound(Acta, Integer)` test helper, day folder
  `"5"` → asserts round `5`), plus a clarifying comment on the pre-existing
  `storesTheMatchWithIdentityTakenFromThePath` noting its fixture doesn't
  distinguish the two sources. All three pass; `resolveRound`'s warning log
  was observed firing correctly for the null-jornada case.
- **Pre-existing, unrelated test failures found in this module** (not caused
  by this feature; confirmed present on `main` before this change, via
  `git stash` + `mvn clean test` in isolation): 8 tests across
  `ImportProcessorsTest`, `BcnesaImportProcessorsTest`,
  `FcttImportProcessorsTest`, and `TeamToClubConsolidationProcessorTest` all
  fail with the same "expected N clubs/players, got 0" pattern — clubs/teams
  never get stored during those runs. This spans all three import sources,
  so it looks systemic (e.g. a shared `InMemoryRepositories`/club-resolution
  regression from an unrelated recent change), not something introduced
  here. Out of scope for this feature; flagged to the user rather than fixed
  silently. `mvn test -Dtest=ImportProcessorsTest` reports
  `Tests run: 11, Failures: 3` both before and after this change (same 3
  pre-existing failures; the 2 new round tests and all previously-passing
  tests are unaffected).
- Scope broadened during implementation, per explicit user decision, to
  check FCTT and BCNESA too (the registry Goal had been edited to say "all
  sources/federations"). Investigation found both already compliant, so no
  code changes were needed for them:
  - **FCTT**: `FcttActasDirectoryNavigator` (Javadoc lines 32-34: "`jornada`
    in the parsed payload is the sole source of the round. Filename suffixes
    are opaque.") builds `round` as `acta.round()` directly when
    constructing `FcttMatchReportContext` (`FcttActasDirectoryNavigator.java:191-199`),
    and **skips** the report with `LOGGER.warn("Skipping {}: payload carries
    no match day", ...)` when `acta.round() == null`, rather than falling
    back to any path/filename value. `FcttMatchImportProcessor` just reads
    `context.round()`, which is that already-payload-sourced `int`.
  - **BCNESA**: `BcnesaActasDirectoryNavigator.traverseReportFolder`
    (`BcnesaActasDirectoryNavigator.java:256-265`) reads `Integer round =
    acta.round()` from the payload first; only for the special "veterans
    other group" case does it fall back to parsing the round out of the
    *file name* (`parseRoundFromFileName`, not a directory path segment),
    and skips the report entirely (same warning pattern as FCTT) if still
    null. `BcnesaMatchImportProcessor` reads `context.round()` from that
    already-resolved value.
  - Net effect: **RFETM was the only source deriving round from a directory
    path segment.** That is now fixed with a fallback (not skip) to stay
    conservative and avoid dropping previously-imported RFETM matches whose
    reports lack `jornada`. FCTT/BCNESA's stricter skip-on-missing behavior
    was left as-is — changing RFETM to skip-on-missing instead of
    fallback-to-path would be a larger, separate behavioral change
    (potentially reducing imported RFETM match counts) and was not part of
    this feature's scope.
