# Build Plan
Source task: **T15** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Gap G21).

## Acceptance Criteria
- [x] The Other-group round fallback supports acta_<home>-<away>_<jornada>.json or is replaced by payload jornada with a reported issue instead of a guess
- [x] The need for multi-fixture splitting and BcnesaClubIndex is measured and the outcome recorded
- [x] Tests cover legacy and new file names

## Measurement baseline (2026-09-29)

Local export `C:\git\bcnesa-extract-2\resources\actas-json`, all `acta*.json` files under
`<season>/<competition>/<group>/<phase>/`. A file counts as multi-fixture when a later game's
`cruce` repeats the first game's `cruce` (the `BcnesaMatchdaySplitter` rule).

| Season | Files | `acta_<jornada>_page_<n>.json` | `acta_<home>-<away>_<jornada>.json` | Under Veterans `Other` |
| --- | --- | --- | --- | --- |
| 2020-2021 | 866 | 858 | 0 | 43 |
| 2021-2022 | 1,857 | 1,843 | 0 | 191 |
| 2022-2023 | 3,074 | 3,066 | 0 | 564 |
| 2023-2024 | 3,246 | 3,231 | 0 | 519 |
| 2024-2025 | 3,629 | 3,614 | 0 | 620 |
| 2025-2026 | 3,715 | 3,698 | 0 | 649 |
| 2026-2027 | 2,882 | 0 | 2,882 | 0 |

File-name counts are from a name-only census on 2026-09-29. Content facts are carried over from
the existing measurements in the code Javadoc and the analysis (section on source layouts): all
16,387 legacy files hold exactly one fixture and carry `jornada` (including all 2,586 files under
"Other" groups); all 2,882 files for 2026-2027 are unpublished, with no games, so they cannot split.
Result: there are no multi-fixture files anywhere in the export today, and the file-name fallback
has never been needed. Step 7 re-checks the content facts with a streaming script before closing.

## Decisions

- **G21 fallback:** keep a file-name fallback for Veterans "Other" groups, but make it accept both
  names and make every use visible. The payload `jornada` stays the primary and authoritative source.
  A file-name round is deterministic (not a guess), but it is reported through the existing
  `ImportRunContext.recordRoundFallback(...)` warning channel (FEAT-00085), so an operator sees it.
  A file whose payload has no `jornada` and whose name fits neither pattern is still skipped and
  counted in `filesSkipped`, exactly as today.
- **Splitting / club index:** keep `BcnesaMatchdaySplitter` (it also yields the single no-games
  fixture for pending actas, FEAT-00081) and keep `BcnesaClubIndex` as the resolver for a
  multi-fixture file, but stop pre-reading every group folder: the index is built lazily, once per
  group, only when a file actually splits into more than one fixture. The measured outcome is
  recorded in the Javadoc of the three traversal classes and under `# Notes`.

## Steps

1. **Fallback pattern (`BcnesaActasDirectoryNavigator`,
   `tt-data-league-import/src/main/java/org/cttelsamicsterrassa/data/load/bcnesa/traverse/`).**
   Replace `OTHER_GROUP_ROUND_FROM_FILE_NAME` with two case-insensitive patterns:
   - legacy `acta_(\d+)_page_.*\.json` → group 1 is the jornada;
   - current `acta_\d+-\d+_(\d+)\.json` → group 1 is the jornada (the trailing segment of
     `acta_<homeId>-<awayId>_<jornada>.json`).

   `parseRoundFromFileName(Path)` tries both and returns `null` when neither matches. Keep the rest
   of the flow unchanged: the fallback runs only when `acta.round() == null` **and**
   `BcnesaVeteransPhases.isOtherGroup(leagueCompetition, group)`.
2. **Report the fallback.** When `parseRoundFromFileName` returns a round, call
   `runContext.recordRoundFallback("BcnesaActasDirectoryNavigator", reportFile, "payload carries no
   jornada; round <n> taken from the file name")` before splitting. Pass `runContext` into
   `traverseReportFolder` (already a parameter). No new counters, no run-status change - same
   warning-only semantics as the RFETM day-folder fallback.
3. **Lazy club index.** In `traverseSeasonFolder`, stop calling `BcnesaClubIndex.build` eagerly.
   Pass a per-group memoizing supplier instead (e.g. a small private `LazyClubIndex` holder in the
   navigator, or a `Supplier<BcnesaClubIndex>` that builds once and caches; `build` throws
   `IOException`, so wrap it in `UncheckedIOException` inside the supplier and unwrap at the call
   site so the traversal's `throws IOException` contract is unchanged).
   Change `BcnesaMatchdaySplitter.split(Acta, BcnesaClubIndex)` to
   `split(Acta, Supplier<BcnesaClubIndex>)` and call the supplier only for fixture index `>= 1`.
   Keep a `split(Acta, BcnesaClubIndex)` convenience overload delegating with `() -> index` so
   existing tests and callers compile unchanged. `BcnesaClubIndex` itself is unchanged.
4. **Documentation in code.** Update the class Javadoc of `BcnesaActasDirectoryNavigator`
   ("Report file names are not parsed", the G21 fallback, "every group folder is read twice"),
   `BcnesaMatchdaySplitter`, `BcnesaClubIndex` ("built for every group but not consulted"), and
   `BcnesaTraversalSummary` with the measured numbers above and the new naming
   `acta_<homeId>-<awayId>_<jornada>.json` (2026-2027 onward) next to the legacy
   `acta_<jornada>_page_<n>.json` (up to 2025-2026). Do not change `MATCH_REPORT_FILE_PATTERN`
   (`acta.*\.json` already accepts both names).
5. **Tests (`tt-data-league-import/src/test/java/org/cttelsamicsterrassa/data/load/traverse/`).**
   In `BcnesaActasDirectoryNavigatorTest`:
   - keep `veteransOtherGroupFixtureWithNoJornadaTakesTheRoundFromTheFileName` (legacy name) and
     additionally assert one reported issue from `BcnesaActasDirectoryNavigator` in the run context;
   - add `...NewFileNameTakesTheRoundFromTheFileName`: `acta_151-247_7.json` under a Veterans
     `Other` group with `"jornada": null` → round 7, not skipped, one reported issue;
   - add a test that an Other-group file with `"jornada": null` and an unrecognised name
     (e.g. `acta_x.json`) is skipped (`filesSkipped == 1`) with no dispatch;
   - add a test that a numbered group (`G1`) file with the new name and `"jornada": null` is skipped
     (the fallback stays limited to "Other" groups);
   - add a test that a new-name file whose payload `jornada` differs from the name's segment uses
     the payload value and records no fallback issue;
   - add a test that a single-fixture group never builds the club index - e.g. a group containing an
     additional unparseable `acta_broken.json` still yields the same summary, and a counting
     `ActaParser` test double shows each file parsed exactly once when no file splits.
   In `BcnesaMatchdaySplitterTest`: add a test that the supplier is not invoked for a
   single-fixture acta or a no-games acta, and is invoked once for a two-fixture acta.
   Existing multi-fixture tests
   (`splitsAMatchdayIntoOneContextPerFixtureAndAttributesTheSecondFromTheClubIndex`) must still pass.
6. **Analysis doc.** In `docs/analysis/analysis-incremental-actas-for-current-jornada-import.md`,
   mark G21 as addressed by FEAT-00091 (gap table row) - wording only, no new files.
7. **Re-measure content (records AC 2).** Run a one-off script (scratch only, not committed)
   over the local export that prints progress per season and reports, per season: files with
   `jornada` null, files under `Other` with `jornada` null, and files whose `partidos` split into
   more than one fixture by the `cruce` rule. Record the numbers under `# Notes`; if any file is
   multi-fixture, record which ones and keep the lazy index as the resolver.
8. **Validation.** Run `mvn -pl tt-data-league-import -am test`, then the full `mvn test` from the
   repository root. Optionally run a BCNESA report-mode import for `--season=2026-2027` and for one
   legacy season against the local export and confirm `filesSkipped`, `fixturesSeen` and
   `fixturesDispatched` are unchanged versus `main` (the change must not alter dispatch results).

# Implementation Guidelines

- Affected modules: import.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- No schema, CLI, configuration or README changes are expected; `rfetm-datamodel.md` stays untouched.
- Dispatch results must not change: payload `jornada` is always authoritative, and the file-name round is used only for a Veterans "Other" group whose payload has no `jornada`.
- Report every file-name fallback through `ImportRunContext.recordRoundFallback`. Do not add counters, change the run status, or skip silently.
- Keep public signatures source-compatible: `BcnesaMatchdaySplitter.split(Acta, BcnesaClubIndex)` stays as an overload, and `BcnesaClubIndex.build/of/resolve` are unchanged.
- Out of scope: removing `BcnesaMatchdaySplitter` or `BcnesaClubIndex`, parsing home/away ids from the new file name (teams come from `equipos`), and any RFETM or FCTT navigator change.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P2, size S, Slice 4: hardening. Depends on: FEAT-00075 (T0), FEAT-00081 (T6).
- 2026-09-29: Plan built; status moved to `planned`. Decision: extend the "Other"-group file-name fallback to both names and report each use (instead of dropping it). Keep splitting, and build `BcnesaClubIndex` lazily per group, only for multi-fixture files, because the export has none today. A full content scan over ~22k files took more than 5 minutes in one unbuffered pass and was stopped. It is now step 7, run with per-season progress output.
- 2026-09-29: Step 7 re-measured content with a streaming per-season script (scratch only) over the local export `C:\git\bcnesa-extract-2\resources\actas-json`. Result per season (files / `jornada` null / `Other` `jornada` null / multi-fixture by the `cruce` rule / parse failures): 2020-2021 866/0/0/0/0; 2021-2022 1,857/0/0/0/0; 2022-2023 3,074/0/0/0/0; 2023-2024 3,246/0/0/0/0; 2024-2025 3,629/0/0/0/0; 2025-2026 3,715/0/0/0/0; 2026-2027 2,882/0/0/0/0. Total 19,269 files, 0 null `jornada`, 0 multi-fixture, 0 parse failures. This confirms the baseline: the file-name fallback and `BcnesaClubIndex` are never needed in the export, so the lazy index changes no dispatch result. AC 1-3 verified: the fallback accepts both names and reports each use, the multi-fixture need is measured and recorded, and tests cover legacy (`acta_5_page_1.json`) and new (`acta_151-247_7.json`) names.
- 2026-09-29: Implementation finalized; status moved to `in-review`. `mvn -pl tt-data-league-import -am test` passes (navigator 26, splitter 9, club index 2). Full reactor `mvn test` from the repository root passes (`BUILD SUCCESS`, all 10 modules).
- 2026-09-29: Closed on explicit user request; status moved to `done`. All three acceptance criteria checked.
