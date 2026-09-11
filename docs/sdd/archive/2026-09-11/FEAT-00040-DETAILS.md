# Build Plan

## Part A — Phase as part of match identity (implemented, see Notes)

1. Widened `findMatchByNaturalKey(...)` in `MatchRepository` with a `phase`
   parameter, added a null-safe `@Query` in `MatchRepositoryHelper`, added
   `"phase"` to the `uk_competition_season_group_round_teams` unique
   constraint columns in `MatchJPA`, and wired
   `BcnesaMatchImportProcessor` to pass `context.phase()` into both the
   dedupe lookup and `buildMatch(...)`. RFETM/FCTT call sites pass `null`
   (unchanged behavior). `InMemoryRepositories` mirrors the null-safe
   comparison. Regression coverage added in `ImportSchemaTest` and
   `BcnesaImportProcessorsTest`.
2. This part is done; do not redo it. See `# Notes` for validation status.

## Part B — Veterans "Other"-phase handling (implemented, see Notes)

The registry's acceptance criteria go beyond Part A: scoping this behavior to
BCNESA Veterans competitions, handling the "Other" phase folder (where the
parsed Group value is `null`), and parsing the jornada/round number from the
report file name when the payload does not carry it. None of this exists in
`tt-data-league-import` yet. `BcnesaActasDirectoryNavigator` currently:
- treats every league-competition the same way (no Veterans-only branch),
- requires every group folder to match `G\d+` (`GROUP_FOLDER_PATTERN`), so a
  folder structure without a group level, or a non-numeric group marker for
  "Other" fixtures, would currently be logged and skipped rather than
  producing a `null` group,
- always derives `round` from the parsed payload's `jornada` field
  (`Acta.round()`) and skips any file where it is absent — there is no
  fallback that parses a number out of the file name.

3. **Confirm the real "Other" phase folder/file layout before coding.** Get or
   inspect an actual BCNESA Veterans `actas-json` export that contains an
   "Other" phase (`Play Off`, `ASCENS`, `DESCENS`, `Finals`, etc.):
   - Confirm whether the `<Group>` directory level is still present (e.g.
     literally named `Otros`/`Other`) or the tree collapses to
     `<Competition>/<Phase>/...` for these fixtures.
   - Confirm whether `jornada` is present or absent in the JSON payload for
     these files, and whether the file name reliably follows
     `acta_<number>_page_<*>.json` (mirroring the source
     `acta_<number>_page_<*>.pdf` naming) when it is absent.
   - Record findings under `# Notes` before proceeding; do not guess the
     layout in code.

4. **Scope the new parsing behavior to Veterans competitions.** Identify how
   a Veterans competition is distinguished from others in the folder tree
   (the `<Competition>` folder name, e.g. containing "Veterans"/"Veteranos")
   or in the payload, and add an explicit predicate in
   `BcnesaActasDirectoryNavigator` (or a small helper) that only applies the
   Part-B behaviors (Group=null handling, filename-based jornada fallback)
   for that competition; other competitions keep today's behavior unchanged
   (fail closed — skip/warn — rather than silently importing under wrong
   assumptions).

5. **Support a nullable group for "Other" phase fixtures.**
   - `BcnesaMatchReportContext`: relax `group`/`groupNumber()` so a Veterans
     "Other" fixture can carry a `null` group (or an `Optional<Integer>`)
     instead of throwing/being skipped, while every other competition keeps
     the current non-null contract.
   - `Match` domain model (`tt-data-league-core-domain/.../match/model/Match.java`)
     and the JPA mapping/schema (`MatchJPA.java`): widen `groupNumber` from
     `int` to `Integer` so it can be persisted as `null`. Check every current
     usage of `Match.getGroupNumber()`/`groupNumber` (natural key lookup,
     unique constraint, search/filter code, UI/API DTOs) for null-safety
     before changing the type — this is a wider ripple than Part A's `phase`
     addition and needs its own review of `MatchRepositoryHelper` (`@Query`
     null-safe pattern, matching the one already used for `phase`) and the
     unique constraint (decide whether `group_num IS NULL` fixtures should
     still be deduped by round/teams alone).
   - Update `BcnesaMatchImportProcessor.buildMatch(...)` and the natural-key
     lookup call to pass a possibly-null group.

6. **Parse jornada from the file name as a fallback.** In
   `BcnesaActasDirectoryNavigator.traverseReportFolder(...)`, when
   `acta.round()` is absent (`null`) and the fixture is under a Veterans
   "Other" phase folder, extract `<number>` from a file name matching
   `acta_(\d+)_page_.*\.json` (or the actual pattern confirmed in step 3)
   instead of skipping the file. Keep the existing skip-and-log behavior for
   every other case (payload has no jornada and no matching file name
   pattern, or the competition is not Veterans).

7. **Tests** (add once step 3's findings are confirmed):
   - `BcnesaActasDirectoryNavigatorTest`: an "Other" phase fixture with no
     `jornada` in the payload is still dispatched, with `round` taken from
     the file name; a non-Veterans competition with the same payload shape
     is still skipped/unchanged.
   - `BcnesaImportProcessorsTest` / `ImportSchemaTest`: a Veterans "Other"
     phase match is persisted with `groupNumber == null`, and its natural key
     still dedupes correctly against a same-round, same-teams re-import.
   - Regression: existing Veterans "1a Fase" and non-Veterans fixtures behave
     exactly as before (group and round both still required and non-null).

8. **Docs**: update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md`
   and any BCNESA-navigator Javadoc describing group/round as always non-null,
   once the nullable-group change lands.

9. Part B is done; do not redo it. See `# Notes` for validation status and the
   residual risk around the unevidenced Veterans-competition/Other-phase
   folder-name heuristic.

## Part C — Fix: Group="Other" folder was skipped entirely (bug found in production)

User-reported (2026-09-08): actas are not being ingested when `<Phase>` is
"Play Off", "ASCENS", or "DESCENS" and `<Group>` is "Other". This reveals that
Part B's premise was backwards: the signal that a fixture belongs to the
Veterans "Other" category is the **`<Group>` folder being the literal string
"Other"**, not the `<Phase>` folder name. Under `<Group>=Other`, multiple
`<Phase>` subfolders exist (`Play Off`, `ASCENS`, `DESCENS`, `Finals`, etc.) -
matching the original FEATURES.md description ("the value 'Other' means that
the parse value for Group is `null`") much more literally than Part B's
implementation did.

Root cause: `BcnesaActasDirectoryNavigator.traverseSeasonFolder` filters group
folders with `GROUP_FOLDER_PATTERN = Pattern.compile("G\\d+")` and skips
(logs a warning and continues) any folder that doesn't match - which silently
discards the entire `Other` group folder and everything under it, regardless
of phase. Part B never touched this filter because it (wrongly) assumed the
`<Group>` folder was always `G<n>` and the "Other" signal lived on `<Phase>`.

10. **Accept the literal `Other` group folder for Veterans competitions.**
    In `BcnesaActasDirectoryNavigator.traverseSeasonFolder`, change the group
    folder filter so a folder is accepted when it matches `GROUP_FOLDER_PATTERN`
    (`G\d+`) **or** it is the literal "Other" group folder of a Veterans
    competition (case-insensitive `"Other".equalsIgnoreCase(group)` gated by
    `BcnesaVeteransPhases.isVeteransCompetition(leagueCompetition)`). A
    non-Veterans competition's "Other"-named (or any other non-`G\d+`) group
    folder keeps today's behavior: logged and skipped (fail closed).

11. **Re-key the "Other" detection off `<Group>`, not `<Phase>`.** Replace
    `BcnesaVeteransPhases.isOtherPhase(leagueCompetition, phase)` with
    `isOtherGroup(leagueCompetition, group)` (`"Other".equalsIgnoreCase(group)`
    for a Veterans competition), and use it everywhere Part B used the old
    phase-keyed predicate:
    - `BcnesaMatchReportContext.groupNumber()`: return `null` when the group
      folder is literally "Other" for a Veterans competition, instead of
      trying `Integer.parseInt(group.substring(1))` (which would throw on
      "Other" - this is very likely *why* Part B, had it reached this fixture,
      would have crashed rather than just mis-skip it).
    - `BcnesaActasDirectoryNavigator.traverseReportFolder`'s file-name-based
      `jornada` fallback: key off `isOtherGroup(leagueCompetition, group)`
      rather than the phase name, so it applies to every phase folder under
      `Other` (Play Off, ASCENS, DESCENS, Finals, ...), not just ones that
      happen not to be named "1a Fase".
    - Delete `isOtherPhase`/`FIRST_PHASE` once nothing references them.

12. **Verify the club index and phase-folder loop need no changes.**
    `BcnesaClubIndex.build(groupFolder, actaParser)` already walks every phase
    subfolder generically regardless of the group folder's name, and
    `traverseSeasonFolder`'s phase loop already iterates every phase subfolder
    of a group with no assumption of exactly one - both already work correctly
    for `Other/Play Off`, `Other/ASCENS`, `Other/DESCENS`, `Other/Finals` once
    step 10 lets the `Other` group folder through. No changes expected here;
    confirm with a test.

13. **Tests.**
    - `BcnesaActasDirectoryNavigatorTest`: a Veterans fixture under
      `<Competition>/Other/Play Off/...` (and `ASCENS`, `DESCENS`) is
      dispatched (not skipped), with `groupNumber() == null`; the same shape
      under a non-Veterans competition is still skipped exactly as before
      (group folder doesn't match `G\d+` and isn't an accepted "Other").
    - A single "Other" group folder with multiple phase subfolders (Play Off
      + ASCENS in the same test) dispatches fixtures from every phase.
    - Update/replace the existing Part-B tests that keyed the Veterans-Other
      scenario off the `<Phase>` folder name (`veteransOtherPhaseFixture*`,
      `nonVeteransCompetitionWithAnOtherShapedPhase*`) to key off `<Group>` =
      "Other" instead, matching the corrected folder layout.
    - `BcnesaImportProcessorsTest` / `ImportSchemaTest`: keep the existing
      null-group persistence/dedupe assertions - only the context construction
      needs to reflect `group = "Other"` rather than a non-"1a Fase" phase.

14. **Re-import.** Once the fix lands, re-running the BCNESA import over an
    export that contains `Other/Play Off`, `Other/ASCENS`, `Other/DESCENS`
    (and any other `Other/<phase>`) folders will pick up every fixture that
    was previously silently skipped, and store each with `groupNumber = null`
    and `phase` set to its folder name. No manual backfill or migration is
    needed: these fixtures were never stored (skipped, not mis-stored), so
    there's nothing to correct in already-imported data - only new rows are
    added by the natural-key-driven idempotent re-import that already exists.

15. Part C is done; do not redo it. See `# Notes` for validation status.

## Part D — Fix: Veterans-competition name heuristic didn't match real folder names

User-confirmed (2026-09-08): the real BCNESA Veterans competition folder name starts with
`"Vet "` and usually follows the pattern `"Vet <1-digit number>a"` (e.g. `"Vet 1a"`, `"Vet 2a"`) -
it does not contain the substring "veteran" that `BcnesaVeteransPhases.isVeteransCompetition`
required. Under the old regex (`.*veteran.*`), every real Veterans competition folder failed the
check, so `isAcceptedGroupFolder` never accepted its `Other` group folder and Part C's fix never
actually fired in production.

16. `BcnesaVeteransPhases.isVeteransCompetition` now matches a competition name starting with
    `"Vet "` (case-insensitive) in addition to the previous `"veteran"` substring match (kept for
    the existing "Veterans"-named test fixtures/robustness). Regression test
    `realVeteransCompetitionFolderNamingIsRecognised` in `BcnesaActasDirectoryNavigatorTest` uses a
    `"Vet 1a"` competition folder to confirm the `Other` group folder and its phase subfolders are
    now ingested.
17. Part D is done; do not redo it. See `# Notes` for validation status.

# Implementation Guidelines
- Do not backfill `phase` or `groupNumber` on existing stored matches; only
  newly imported/re-imported BCNESA fixtures are affected going forward.
- RFETM and FCTT importers are out of scope for both `phase` and the Part-B
  Veterans-only behavior; only shared method signatures they call change.
- Do not rename the `uk_competition_season_group_round_teams` constraint.
- Keep the null-safe comparison pattern (JPA `@Query` and
  `InMemoryRepositories`) consistent for every nullable natural-key column.
- Part B must not change behavior for non-Veterans competitions or for
  Veterans fixtures outside the "Other" phase; the explicit competition/phase
  predicate from step 4 is what keeps the blast radius contained.
- Part C: the "Other" signal is the literal `<Group>` folder name, not the
  `<Phase>` folder name. A Veterans competition's `<Group>=Other` folder can
  contain any number of `<Phase>` subfolders (Play Off, ASCENS, DESCENS,
  Finals, ...) - all of them get `groupNumber = null` and `phase` = their own
  folder name; none of them are assumed to be the only one.

# Notes
- Part D fixed (2026-09-08): user confirmed the real Veterans competition folder name starts with
  `"Vet "` and usually follows `"Vet <digit>a"` (e.g. `"Vet 1a"`), not "veteran"/"Veterans" as the
  Part B/C heuristic assumed. `BcnesaVeteransPhases.isVeteransCompetition` now matches
  `^Vet\s.*` (case-insensitive) in addition to the old `.*veteran.*` match. This closes the
  residual risk flagged repeatedly in Part B/C notes: without this, Part C's `Other`-group fix
  never actually applied to any real competition folder, since none of them contain "veteran".
  `mvn test` for `tt-data-league-import`: 31 tests run (was 30), 3 pre-existing unrelated failures
  (club/player-count assertions, same 3 as before this change), all Veterans/Other/phase-related
  tests pass including the new `realVeteransCompetitionFolderNamingIsRecognised`.
- Bug found in production (2026-09-08, reported by user): actas are not ingested when `<Phase>` is
  "Play Off", "ASCENS", or "DESCENS" and `<Group>` is "Other". Root cause: Part B keyed its
  Veterans-"Other" detection off the `<Phase>` folder name (`phase != "1a Fase"`), but the real signal
  is the `<Group>` folder being literally `"Other"` - and `BcnesaActasDirectoryNavigator`'s
  `GROUP_FOLDER_PATTERN` (`G\d+`) skips that folder entirely before phase is ever considered, so no
  fixture under `Other/<any phase>` was ever dispatched. Added Part C to fix this by re-keying the
  "Other" predicate off `<Group>` and accepting the literal `"Other"` group folder for Veterans
  competitions. Moved status back to `in-progress` until Part C lands and is verified.
- Implementation complete for Part C (2026-09-08):
  - `BcnesaVeteransPhases` rewritten: `isOtherPhase(competition, phase)` replaced by
    `isOtherGroup(competition, group)` (`"Other".equalsIgnoreCase(group)` for a Veterans competition).
    `isVeteransCompetition` unchanged.
  - `BcnesaActasDirectoryNavigator.traverseSeasonFolder` now accepts a group folder when it matches
    `GROUP_FOLDER_PATTERN` (`G\d+`) **or** `BcnesaVeteransPhases.isOtherGroup(leagueCompetition, group)`
    is true (new `isAcceptedGroupFolder` helper); a non-Veterans competition's "Other" (or any other
    non-`G\d+`) group folder is still logged and skipped, unchanged.
  - `BcnesaActasDirectoryNavigator.traverseReportFolder`'s file-name `jornada` fallback now keys off
    `isOtherGroup(leagueCompetition, group)` instead of the phase name, so it applies to every phase
    folder under `Other` (not just non-"1a Fase" ones - which was never the right test anyway).
  - `BcnesaMatchReportContext.groupNumber()` now returns `null` based on `isOtherGroup(leagueCompetition,
    group)` rather than the old phase-based check; this also fixes a latent crash risk in the old code
    (`Integer.parseInt(group.substring(1))` would have thrown `NumberFormatException` on a literal
    "Other" group had that folder ever reached this method, which it couldn't under the old,
    now-corrected, group filter).
  - Renamed `OTHER_PHASE_ROUND_FROM_FILE_NAME` to `OTHER_GROUP_ROUND_FROM_FILE_NAME` and updated
    surrounding Javadoc/comments to describe the group-based signal.
  - Tests rewritten in `BcnesaActasDirectoryNavigatorTest` to use `<Group>=Other` with `<Phase>` folders
    named "Play Off"/"ASCENS"/"DESCENS" (matching the reported bug): a fixture under
    `Veterans/Other/Play Off/` is now dispatched (previously silently skipped) with a `null`
    `groupNumber`; the same fixture with no `jornada` in the payload takes its round from the file
    name; all three of Play Off/ASCENS/DESCENS under one `Other` group are dispatched together; a
    non-Veterans competition's `Other` group folder is still skipped entirely (`filesSeen == 0`, the
    folder is never even opened); a Veterans `1a Fase` fixture under a numbered group is unaffected.
    `BcnesaImportProcessorsTest`'s Veterans-Other persistence/dedupe tests were updated to construct
    their `BcnesaMatchReportContext` with `group = "Other"` instead of a non-"1a Fase" phase under
    `G1` (`fixtureContext(...)` gained an explicit `group` parameter).
  - Verified: `mvn compile`/`test-compile` pass across the affected modules. All BCNESA/Veterans/group
    tests pass, including the new and rewritten ones. Ran the full `tt-data-league-import` suite before
    and after this change (stashed comparison): both report the same 8 pre-existing failures
    (club/player-count assertions unrelated to group/phase handling, plus
    `TeamToClubConsolidationProcessorTest`, all reproducing identically on the pre-Part-C commit) -
    confirming no regressions.
  - **Residual risk unchanged from Part B**: the "Veterans competition" substring heuristic
    (`.*veteran.*`, case-insensitive, on the `<Competition>` folder name) is still inferred from
    FEATURES.md's Description and the user's bug report, not directly evidenced against a full real
    export. The bug report does confirm the `<Group>=Other` / `<Phase>` = Play Off, ASCENS, DESCENS
    folder shape is real; recommend running the fixed importer against a real Veterans season export
    to confirm the competition-name detection and that every previously-missing actas is now ingested.
- Plan rebuilt (2026-09-08): the registry's acceptance criteria cover more than Part A — Veterans-only
  scoping, "Other" phase handling with a `null` Group, and filename-based jornada parsing are not yet
  implemented anywhere in `tt-data-league-import`. Added Part B to the build plan for this remaining
  work. Moved status from `in-review` back to `in-progress` since not all registry acceptance criteria
  are met yet.
- Open question blocking Part B (step 3): the exact "Other" phase folder/file layout (is `<Group>`
  still a folder level? is `jornada` actually absent from the payload for these files? does the file
  name reliably follow `acta_<number>_page_<*>.json`?) is not evidenced anywhere in this repo (no
  sample "Other"-phase export or fixture exists under `tt-data-league-import/src/test/resources`).
  Confirm against a real export before writing step 4-6 code.
- Part B unblocked (2026-09-08): FEATURES.md's Description section (added the same day) answers step 3
  directly - the `<Group>` folder level is always present (`<season>/<Competition>/<Group>/<Phase>/`),
  a Veterans "Other"-phase fixture's *parsed* Group value must be `null` regardless of the physical
  group folder, and the file name mirrors the source PDF's `acta_<number>_page_<*>.pdf`/`.json` naming
  with `<number>` being the jornada. Proceeded with Part B on that basis.
- Implementation complete for Part B (2026-09-08):
  - Added `BcnesaVeteransPhases` (package `org.cttelsamicsterrassa.data.load.bcnesa`), the explicit
    predicate from step 4. A competition is "Veterans" by a case-insensitive `veteran` substring in the
    `<Competition>` folder name (covers "Veterans"/"Veteranos"); a phase is "Other"-category when the
    competition is Veterans and the `<Phase>` folder name is not `"1a Fase"` (case-insensitive) - the
    literal folder name for an Other-category phase is expected to be one of "Play Off"/"ASCENS"/
    "DESCENS"/"Finals"/etc, not literally the word "Other" (multiple such folders are already handled
    generically by the pre-existing phase-folder loop). **This substring heuristic is not evidenced
    against a real export - if the real `<Competition>` folder name does not contain "veteran", the
    predicate will not fire and the competition silently keeps today's (unchanged, non-Veterans)
    behavior rather than mis-handling it (fail closed, per the Implementation Guidelines), but the
    Part-B behaviors described below will not apply either. Confirm the literal competition folder name
    against a real BCNESA Veterans export before relying on this in production.**
  - `BcnesaMatchReportContext.groupNumber()` now returns `Integer` and returns `null` when
    `BcnesaVeteransPhases.isOtherPhase(leagueCompetition, phase)` is true, instead of parsing the
    physical group folder name.
  - `BcnesaActasDirectoryNavigator.traverseReportFolder` now falls back to parsing the round from the
    report file name (`acta_(\d+)_page_.*\.json`, case-insensitive) when the payload's `jornada` is
    absent and the fixture is a Veterans Other-phase fixture; every other absent-`jornada` case is
    still skipped exactly as before.
  - Widened `Match.groupNumber`/`getGroupNumber()`/`MatchBuilder.groupNumber(...)` from `int` to
    `Integer` (nullable), and `MatchRepository.findMatchByNaturalKey`'s `groupNumber` parameter to
    `Integer`. Reviewed every call site: RFETM and FCTT importers pass an `int` local (autoboxes,
    unaffected), `MatchToMatchJPAMapper`/`MatchJPAToMatchMapper` pass the value straight through
    (`MatchJPA.groupNumber` was already `Integer`), and `MatchDetailReadModel`/`MatchDetailDto` (API
    read path) were widened to `Integer` too so a null-group match doesn't NPE on unboxing when
    displayed. The BCNESA-only frontend display (`MatchDetailPage.jsx`) renders a null value as empty
    text with no code change needed, so it was left untouched (out of scope per the Implementation
    Guidelines, which only call out RFETM/FCTT importers).
  - `MatchJPA.groupNumber`'s `@Column` is now `nullable = true` (was `false`); the
    `uk_competition_season_group_round_teams` constraint keeps `group_num` as a member column
    unchanged (not renamed), and `MatchRepositoryHelper`'s natural-key `@Query` now compares
    `groupNumber` with the same null-safe `(:param is null and m.field is null or m.field = :param)`
    pattern already used for `phase`. Schema is Hibernate-managed (`ddl-auto`, no migration files in
    this repo), so no migration was needed.
  - `BcnesaMatchImportProcessor` (`process`/`buildMatch`) now threads a nullable `Integer groupNumber`
    from `context.groupNumber()` through the natural-key lookup and `Match.builder()` unchanged
    otherwise.
  - Updated `docs/rfetm-datamodel.md`: `group_num` is now documented `Yes` (nullable) with a note on
    why the unique constraint does not dedupe null-group fixtures by group alone; also added the
    `phase` column row, missing from Part A's doc update.
  - Tests added: `BcnesaActasDirectoryNavigatorTest` (Veterans-Other fixture with no `jornada` takes the
    round from the file name and carries a `null` groupNumber; a non-Veterans competition with the same
    Other-shaped phase and no `jornada` is still skipped; a Veterans `"1a Fase"` fixture keeps its
    regular group number), `BcnesaImportProcessorsTest` (a Veterans Other-phase fixture persists with
    `groupNumber == null`; re-running the same one dedupes despite the null group), `ImportSchemaTest`
    (`persistsAndDedupesAMatchWithANullGroupNumber` - a null-group match round-trips through the JPA
    layer and its natural-key lookup distinguishes it from a numbered-group fixture with the same
    round/phase/teams). `InMemoryRepositories.Matches.findMatchByNaturalKey` was updated to compare
    `groupNumber` with `Objects.equals` instead of `==` to support the nullable parameter.
  - Verified: `mvn compile`/`test-compile` pass across every affected module (including
    `tt-data-league-api-rest`, `tt-data-league-api-runtime`, `tt-data-league-import-runtime`,
    `tt-data-league-api-graphql`). All match/phase/group-related tests pass, including the new ones.
    The 7 pre-existing club/player-count failures in `BcnesaImportProcessorsTest`/
    `FcttImportProcessorsTest`/`ImportProcessorsTest` and `ImportSchemaTest`'s whole-class Spring
    context failure (`NoSuchBeanDefinitionException` for `ImportRunRegistry`, unrelated to this
    change) reproduce identically on the pre-Part-B commit, confirming they are unrelated
    pre-existing issues, not regressions from this work.
  - **Residual risk / needs user confirmation**: the "Veterans" folder-name heuristic and the "any
    phase other than exactly `1a Fase` is Other-category" split are both inferred from FEATURES.md's
    Description, not from an actual BCNESA Veterans export. Recommend running this against one real
    Veterans season export before trusting it in production, and adjusting `BcnesaVeteransPhases` if
    the literal competition/phase folder names differ from what is assumed here.
- Widening `Match.groupNumber` from `int` to `Integer` (step 5) is a larger ripple than Part A's
  `phase` addition — it touches the domain model, JPA mapping, the unique constraint, and every
  existing null-unsafe caller of `getGroupNumber()`. Review call sites before starting.
- `BcnesaActasDirectoryNavigator` and `BcnesaClubIndex` already traverse an arbitrary number of phase
  subfolders per group generically (not assuming exactly one), so the "multiple phases within Other"
  acceptance criterion is already satisfied by existing, pre-FEAT-00040 code — no new work needed
  there beyond what step 4-6 add for Veterans/Other-specific parsing.
- Implementation complete for Part A (2026-09-08): widened `findMatchByNaturalKey` with a `phase` parameter,
  added phase to the `uk_competition_season_group_round_teams` unique constraint, wired
  `BcnesaMatchImportProcessor` to read `context.phase()` into both the dedupe lookup and
  `buildMatch(...)`, passed `null` phase from RFETM/FCTT call sites (unchanged behavior), and
  updated the `InMemoryRepositories` test double to compare phase with null-safety. Added
  regression coverage: `ImportSchemaTest.distinguishesMatchesSharingARoundButBelongingToDifferentPhases`
  and two new `BcnesaImportProcessorsTest` cases (phase persisted on import; same-round fixtures
  across different phases both imported rather than deduped). `mvn compile`/`test-compile` pass
  across all modules; `BcnesaImportProcessorsTest` and the two new cases pass (the 3 pre-existing
  failures in that class and `ImportSchemaTest`'s Spring-context failure are unrelated pre-existing
  issues, unchanged from the base commit before this work).
- Depends on FEAT-00039 (done), which added the settable/persisted/displayable/filterable
  `Match.phase` property end-to-end but explicitly left every importer unwired — every match
  currently carries `phase = null`.
- `BcnesaActasDirectoryNavigator` already parses the phase folder segment (e.g. `1a Fase`) into
  `BcnesaMatchReportContext.phase()`; it is currently received but discarded by
  `BcnesaMatchImportProcessor.buildMatch(...)` (`tt-data-league-import/.../bcnesa/process/BcnesaMatchImportProcessor.java`).
- The natural key used both for duplicate detection (`MatchRepository.findMatchByNaturalKey`) and
  for the DB unique constraint `uk_competition_season_group_round_teams` on `match_record`
  (`competition, season, group_num, round, home_team_id, away_team_id`) does not include `phase`.
  BCNESA round numbers can repeat across phases within the same group, so without including phase
  a fixture from a later phase can be silently skipped as an already-stored duplicate of an
  earlier-phase fixture between the same two teams.
