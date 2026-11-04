# Build Plan
## Acceptance Criteria
- [x] The BCNESA navigator accepts acta files named `jornada_<NN>_local_team_<localId>_away_team_<awayId>.json` and imports them; no file in the 2026-2027 export at `C:\tt-repository\import-bcnesa\actas` is ignored or skipped because of its name
- [x] Legacy names (`acta_<jornada>_page_<n>.json`, `acta_<n>.json` and `acta_<home>-<away>_<jornada>.json`) keep importing with unchanged results
- [x] The Veterans "Other" group round fallback recognises the jornada segment of the new name and is still reported through `ImportRunContext.recordRoundFallback`; the payload `jornada` stays authoritative
- [x] A `.json` file under a phase folder that matches no supported name is counted as skipped and reported as an issue instead of being silently ignored
- [x] New `rtb-*` competition folders are stored under the legacy competition names through an explicit mapping (for example `rtb-preferent` → `Preferent`, `rtb-veterans-2aa` → `Vet 2a _A_`); legacy folder names are stored unchanged
- [x] An `rtb-*` competition folder with no mapping is not imported: its files are counted as skipped and an issue is reported, so the run does not end as `SUCCESS`
- [x] Veterans competitions under the new folder names are still recognised as Veterans (including their "Other" group handling)
- [x] Tests cover the new, legacy and unrecognised file names, the competition mapping and the unmapped-folder case
- [ ] A BCNESA report-mode import over `C:\tt-repository\import-bcnesa\actas` for `--season=2026-2027` completes without errors, sees all 351 files, skips none, and reports matches only under legacy competition names

## Root cause (2026-10-01)

- `BcnesaActasDirectoryNavigator.MATCH_REPORT_FILE_PATTERN` (and the copy in `BcnesaClubIndex`) is
  `acta.*\.json`. None of the 351 `jornada_..._local_team_..._away_team_...json` files match, so
  the traversal lists no report files, dispatches nothing, and `ImportRunStatusPolicy` ends the run
  as `EMPTY_RESULT`. Files that do not match are dropped silently by `listJsonFiles`, so nothing in
  the summary says why.
- `BcnesaMatchReportContext.competition()` stores the competition folder name verbatim. The new
  export uses `rtb-*` slugs, so without a mapping 2026-2027 matches would be stored under names that
  differ from every earlier season (and from any 2026-2027 match imported from the previous export).

## Competition mapping

Legacy names are the folder names used in seasons 2020-2021 to 2025-2026 (from
`C:\git\bcnesa-extract-2\resources\actas-json`). Payload `competicion` is shown for reference only;
the folder stays the identity source.

| New folder | Payload `competicion` | Stored competition |
| --- | --- | --- |
| `rtb-preferent` | `RTB PREFERENT` | `Preferent` |
| `rtb-primera` | `RTB PRIMERA` | `Primera` |
| `rtb-segona-a` | `RTB SEGONA A` | `Segona _A_` |
| `rtb-segona-b` | `RTB SEGONA B` | `Segona _B_` |
| `rtb-tercera-a` | `RTB TERCERA A` | `Tercera _A_` |
| `rtb-tercera-b` | `RTB TERCERA B` | `Tercera _B_` |
| `rtb-1a-comarcal` | `RTB 1a COMARCAL` | `1a Comarcal` (new, no legacy equivalent) |
| `rtb-2a-comarcal` | `RTB 2a COMARCAL` | `2a Comarcal` (new, no legacy equivalent) |
| `rtb-veterans-1a` | `RTB VETERANS 1a` | `Vet 1a` |
| `rtb-veterans-2aa` | `RTB VETERANS 2aA` | `Vet 2a _A_` |
| `rtb-veterans-2ab` | `RTB VETERANS 2aB` | `Vet 2a _B_` |
| `rtb-veterans-3a-a` | `RTB VETERANS 3a A` | `Vet 3a _A_` |
| `rtb-veterans-3a-b` | `RTB VETERANS 3a B` | `Vet 3a _B_` |
| `rtb-veterans-4a-a` | `RTB VETERANS 4a A` | `Vet 4a _A_` |
| `rtb-veterans-4a-b` | `RTB VETERANS 4a B` | `Vet 4a _B_` |
| `rtb-veterans-4a-c` | `RTB VETERANS 4a C` | `Vet 4a _C_` (new group letter, legacy style) |

## Steps

All paths are under `tt-data-league-import/src/main/java/org/cttelsamicsterrassa/data/load/bcnesa/`
unless stated otherwise.

1. **Report file names (`traverse/BcnesaReportFileNames.java`, new public final class).**
   Move every BCNESA file-name rule into one place so the navigator and the club index cannot drift:
   - `CURRENT = jornada_(\d+)_local_team_\d+_away_team_\d+\.json` (case-insensitive);
   - `LEGACY_PAGE = acta_(\d+)_page_.*\.json` and `LEGACY_PAIR = acta_\d+-\d+_(\d+)\.json`
     (moved from the navigator, unchanged);
   - `LEGACY_ANY = acta.*\.json` (today's catch-all, so `acta_<n>.json` and `acta.json` keep
     importing);
   - `static boolean isMatchReport(String fileName)`: `CURRENT` or `LEGACY_ANY` matches;
   - `static Integer roundFromFileName(String fileName)`: tries `LEGACY_PAGE`, `LEGACY_PAIR`, then
     `CURRENT` (group 1 via `Integer.valueOf`, so `01` becomes 1); `null` when none match.
   The class Javadoc lists the three name generations with the season ranges and counts recorded in
   FEAT-00091 plus the new 351-file sample.
2. **Navigator file listing (`traverse/BcnesaActasDirectoryNavigator.java`).**
   - Delete `MATCH_REPORT_FILE_PATTERN`, `OTHER_GROUP_LEGACY_ROUND_FROM_FILE_NAME` and
     `OTHER_GROUP_ROUND_FROM_FILE_NAME`; `parseRoundFromFileName` delegates to
     `BcnesaReportFileNames.roundFromFileName`. The fallback still runs only when
     `acta.round() == null` and the group is a Veterans "Other" group, and still calls
     `runContext.recordRoundFallback(...)` with the same message.
   - Split the phase-folder listing into accepted report files (`isMatchReport`) and other regular
     `*.json` files (case-insensitive `.json` suffix). For each unrecognised `.json`: increment
     `filesSeen` and `filesSkipped`, log a warning, and add
     `new ImportExecutionIssue("BcnesaActasDirectoryNavigator", <file>, "unrecognised match report file name")`
     to `Counters.issues`. Non-JSON files stay ignored.
   - `countReportFiles` counts both lists so the progress total equals `filesSeen` at the end.
3. **Club index listing (`traverse/BcnesaClubIndex.java`).** Replace its private
   `MATCH_REPORT_FILE_PATTERN` with `BcnesaReportFileNames.isMatchReport`. Unrecognised names are
   not read by the index (the navigator already reports them). No public signature changes.
4. **Competition mapping (`BcnesaCompetitionNames.java`, new public final class next to
   `BcnesaVeteransPhases`).**
   - `private static final Map<String, String> BY_EXPORT_FOLDER` holding exactly the table above,
     keys lower-case.
   - `public static Optional<String> storedName(String competitionFolder)`: a folder starting with
     `rtb-` (case-insensitive) returns its mapped name, or empty when unmapped; any other folder
     returns the folder name unchanged (legacy exports).
   - No fuzzy matching, no derivation from the payload `competicion`, no default.
5. **Use the mapping in the navigator.** In `traverseSeasonFolder` and `countReportFiles`, resolve
   `BcnesaCompetitionNames.storedName(folderName)` once per competition folder:
   - mapped: pass the stored name as `leagueCompetition` to `isAcceptedGroupFolder`,
     `traverseReportFolder` and `BcnesaMatchReportContext`, so `competition()`, `groupNumber()`,
     Veterans detection and the "Other" group rule all see `Vet 2a _A_`, not the slug;
   - unmapped: do not traverse it. For every report file below it (counted with the same group and
     phase walk) increment `filesSeen` and `filesSkipped`, add one
     `ImportExecutionIssue("BcnesaActasDirectoryNavigator", <competition folder>, "unmapped BCNESA competition folder <name>; add it to BcnesaCompetitionNames")`
     per folder, and log an error.
   Update the Javadoc of `BcnesaMatchReportContext` (`leagueCompetition` is the stored competition
   name, mapped from `rtb-*` folders) and the "Report file names are not parsed" section of the
   navigator class Javadoc (both name generations, the mapping, unrecognised-name reporting).
6. **Veterans detection (`BcnesaVeteransPhases.java`).** No code change, because the navigator now
   passes the mapped `Vet ...` name; update the class Javadoc to say it receives the stored
   competition name. Step 7 proves `rtb-veterans-*` folders are treated as Veterans.
7. **Tests (`tt-data-league-import/src/test/java/org/cttelsamicsterrassa/data/load/`).**
   - `traverse/BcnesaReportFileNamesTest` (new): accepts the current name, `acta_1.json`,
     `acta_5_page_1.json`, `acta_151-247_7.json` and `acta.json`; rejects `manifest.json`,
     `jornada_01.json` and `jornada_01_local_team_x_away_team_2.json`; `roundFromFileName` returns 1
     for `jornada_01_local_team_439_away_team_438.json`, 5 and 7 for the legacy names, and `null`
     for an unrecognised name.
   - `BcnesaCompetitionNamesTest` (new, next to the existing BCNESA tests): every row of the mapping
     table; legacy names (`Preferent`, `Vet 1a`) are returned unchanged; `rtb-unknown` is empty; the
     `rtb-` lookup is case-insensitive.
   - `traverse/BcnesaActasDirectoryNavigatorTest`:
     - `2026-2027/rtb-preferent/G1/1a Fase/jornada_01_local_team_439_away_team_438.json` is
       dispatched with round 1, competition `Preferent`, group number 1 and `filesSkipped == 0`;
     - `rtb-veterans-2aa/Other/Final/jornada_03_local_team_1_away_team_2.json` with
       `"jornada": null` gets round 3 from the file name, one round-fallback issue, and
       `groupNumber() == null`;
     - the payload `jornada` wins over the new name's segment, with no fallback issue;
     - a `notes.json` next to a valid report: `filesSeen == 2`, `filesSkipped == 1`, one summary
       issue naming the file, and the valid report is still dispatched;
     - an `rtb-unknown/G1/1a Fase/` folder with two reports: no dispatch, `filesSeen == 2`,
       `filesSkipped == 2`, one summary issue naming the folder;
     - progress: the reported total equals the final `filesSeen` when unrecognised files and
       unmapped folders are present;
     - all existing legacy-name tests stay green unchanged.
   - `traverse/BcnesaClubIndexTest`: an index built over a group holding new-name files registers
     their header votes.
   - Existing `NavigatorImportExecutionService` tests (or the nearest BCNESA execution test): an
     unmapped competition folder makes the run `FAILURE`, not `EMPTY_RESULT`.
8. **Documentation.**
   - `tt-data-league-import-runtime/README.md`: add a short "BCNESA export layout" paragraph next to
     the FCTT layout description: `<season>/<competition>/<G<n>|Other>/<phase>/<report>.json`, the
     file-name generations, the `rtb-*` to stored-name mapping, and that unmapped folders or
     unrecognised file names fail the run.
   - `docs/analysis/analysis-incremental-actas-for-current-jornada-import.md`: append to the G21 row
     that FEAT-00094 adds the `jornada_..._local_team_..._away_team_...json` name (wording only).
   - `rfetm-datamodel.md` is not changed (no schema change).
9. **Stored-data check before any write run.** Query the BCNESA matches already stored for
   2026-2027 (competition, `source_fixture_id`). If any have a non-null `source_fixture_id` whose
   format differs from the new `id_partido` (`2026-2027_RTB1aCOMARCAL_G1_1aFase_439-438_1`),
   `MatchFixtureIdentityGuard` will report conflicts instead of updating them. Record the finding
   under `# Notes`; if conflicts are expected, stop and agree a follow-up with the user before a
   write run (this feature does not rewrite stored ids).
10. **Validation.**
    - `mvn -pl tt-data-league-import -am test`, then the full `mvn test` from the repository root.
    - Report-mode BCNESA import with `--source=bcnesa`,
      `--actas-folder=C:\tt-repository\import-bcnesa\actas` and `--season=2026-2027`: expect 351
      files seen, 0 skipped, 351 fixtures seen, no summary issues, and only mapped competition names
      in the snapshot and round-progress output.
    - Report-mode run of one legacy season from `C:\git\bcnesa-extract-2\resources\actas-json`:
      `filesSeen`, `filesSkipped`, `fixturesSeen` and `fixturesDispatched` equal the numbers on
      `main`.
    - Record the numbers under `# Notes`.

# Implementation Guidelines

- Affected module: `tt-data-league-import` (BCNESA traversal under
  `org.cttelsamicsterrassa.data.load.bcnesa.traverse`); runtime only if README/launch notes change.
- Follow the repository and module `AGENTS.md` files. Teams and clubs keep coming from the payload
  `equipos`; do not add external ids to `FederatedClub`/`FederatedPlayer` because the name now
  carries team ids.
- Affected files: the new `BcnesaReportFileNames` and `BcnesaCompetitionNames`, plus
  `BcnesaActasDirectoryNavigator`, `BcnesaClubIndex`, Javadoc in `BcnesaMatchReportContext` and
  `BcnesaVeteransPhases`, their tests, the import-runtime README and the analysis G21 row.
- The payload `jornada` stays authoritative; the file-name round is used only for a Veterans
  "Other" group whose payload has no `jornada`, exactly as in FEAT-00091.
- The home/away team ids in the new file name are not parsed or used: teams keep coming from
  `equipos`.
- The competition folder stays the identity source; the mapping is an explicit closed table.
  Unmapped `rtb-*` folders and unrecognised `.json` names fail clearly through summary issues,
  never through a guess or a silent skip.
- Keep public signatures source-compatible (`BcnesaActasDirectoryNavigator.traverse*`,
  `BcnesaClubIndex.build/of/resolve`, `BcnesaMatchReportContext` constructors).
- No schema, CLI or configuration change; `rfetm-datamodel.md` stays untouched.
- Out of scope: renaming competitions already stored for earlier seasons, rewriting stored
  `source_fixture_id` values, and any RFETM or FCTT navigator change.

# Notes

## Requirement (2026-10-01)

The BCNESA extractor now writes acta JSON files as
`jornada_<match day number>_local_team_<local team id>_away_team_<away team id>.json`
(match day zero-padded to two digits, e.g. `jornada_01_local_team_439_away_team_438.json`), and the
BCNESA import process fails on them. Example files: `C:\tt-repository\import-bcnesa\actas`.

## Observations from the sample export (not a plan)

- 351 files, all under `2026-2027/<competition>/G<n>/1a Fase/`; every file name matches
  `jornada_\d+_local_team_\d+_away_team_\d+\.json`.
- `BcnesaActasDirectoryNavigator.MATCH_REPORT_FILE_PATTERN` is `acta.*\.json`, so the new names
  are not recognised as match reports; the Other-group round fallbacks (FEAT-00091) only know the
  `acta_...` names.
- Competition folders are now slugs (`rtb-preferent`, `rtb-1a-comarcal`, `rtb-veterans-4a-c`, ...)
  instead of the legacy display names (`Preferent`, `Vet 1a`, ...). Planning should check whether
  competition naming, `BcnesaVeteransPhases` detection and any competition-name mapping still
  behave as intended with these folder names.
- Sample payloads carry `jornada`, `fase`, `grupo`, `competicion`, `id_partido`
  (e.g. `2026-2027_RTB1aCOMARCAL_G1_1aFase_439-438_1`) and `equipos.local/visitante.id`.

## Plan decisions (2026-10-01)

- The user chose to map the new `rtb-*` competition folders to the legacy competition names, so
  every season keeps one competition name (instead of storing the slugs or splitting the mapping
  into a separate feature). Effort raised to large and the acceptance criteria extended.
- `rtb-1a-comarcal`, `rtb-2a-comarcal` and `rtb-veterans-4a-c` have no legacy folder. The plan
  stores them as `1a Comarcal`, `2a Comarcal` and `Vet 4a _C_` (legacy naming style). Confirm these
  names before implementation; changing them later means renaming stored matches.
- Unrecognised `.json` file names and unmapped `rtb-*` folders are reported as summary issues,
  which makes the run `FAILURE` under `ImportRunStatusPolicy`. This follows the "fail clearly"
  rule; a silent skip is what hid the current problem (the run ended as `EMPTY_RESULT`).
- Legacy exports have no `id_partido`; the new one does. Step 9 checks for stored 2026-2027
  matches with a different `source_fixture_id` before any write run.

## Implementation and validation (2026-10-01)

- Implemented steps 1-8. Test locations differ slightly from the plan: the existing BCNESA tests live
  under `data/load/traverse`, so the navigator/club-index tests were extended there; the unmapped-folder
  `FAILURE` test is in `NavigatorImportExecutionLifecycleTest`. `mvn test` (full reactor) passes.
- Real-export check (throwaway no-op-processor traversal, since removed, not the CLI import):
  `C:\tt-repository\import-bcnesa\actas` 2026-2027 gives filesSeen 351, filesSkipped 0, fixturesSeen 351,
  fixturesDispatched 351, no issues; competitions seen are only the mapped legacy names (1a Comarcal 24,
  2a Comarcal 13, Preferent 30, Primera 30, Segona _A_/_B_ 30 each, Tercera _A_ 30, Tercera _B_ 28,
  Vet 1a 16, Vet 2a _A_/_B_ 16 each, Vet 3a _A_/_B_ 16 each, Vet 4a _A_/_B_ 16 each, Vet 4a _C_ 24).
  Legacy season 2024-2025 from `C:\git\bcnesa-extract-2\resources\actas-json`: seen 3577, skipped 0,
  dispatched 3577, no issues, legacy names only. These were not compared against `main` numbers.
- Open: step 9 (stored 2026-2027 `source_fixture_id` check) needs database access and was not done;
  do it before any write run. The last acceptance criterion stays unchecked until the CLI report-mode run
  is done by the user.
