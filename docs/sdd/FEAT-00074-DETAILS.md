# Build Plan

Source of truth for the new format: `C:\git\fctt-extract` commit `3cef9a8`
(2026-09-26) and its `resources/actas-json/model-definition.json`. Compared with
the layout the importer supports today, the export changed in five ways:

| # | Change | Old | New | Effect on today's importer |
|---|---|---|---|---|
| C1 | Gender folder level | `<season>/<competition>/<group>/` | `<season>/<male\|female>/<competition>/<group>/` | `male` is read as the competition and `tercera-nacional` as the group, so every report is skipped as an invalid group. |
| C2 | File name | `jornada_<d>_partido_<m>.json` | `jornada-<d>-partido-<m>.json` (published) and `jornada-<d>-partido-<homeId>-<awayId>.json` (unpublished) | `MATCH_REPORT_FILE_PATTERN` matches nothing, so 0 files are seen. |
| C3 | Competition folder name | `tercera nacional` | `tercera-nacional` (slug) | The `competition` natural-key value changes. |
| C4 | New payload fields | none | `id_partido`, `acta_publicada`, `fase` | Ignored today because of `@JsonIgnoreProperties`. |
| C5 | Unpublished actas | not exported | `acta_publicada: false`, empty `partidos`/`alineaciones`, but a **placeholder** `resultado_final` (for example `ganador` = home team, 6–0) | These would be stored as played matches with fake results. |

Female competitions (`copa-catalana-femenina-1a`, `copa-catalana-femenina-2a`)
exist only as HTML so far (`actas-html/2026-2027/female/<competition>/jornada-N.html`).
They have **no `G<n>` group folder**, and their JSON has not been produced yet.

## Schema contract impact (`docs/acta-model-definition.json`)

The shared acta schema (used for RFETM, BCNESA and FCTT) was updated to describe C4/C5. Impact of each
schema change on the importer:

| Schema change | Importer impact | Plan step |
|---|---|---|
| New optional `id_partido` (`<season>_<category>_<group>_<phase>_<teams>_<round>`) | Not parsed today. It is parsed for logs and preview but not persisted. It is the same for the published and unpublished versions of a fixture. | 1, Notes |
| New optional `acta_publicada` (missing means published) | Not parsed today. It is the switch for C5. | 1, 4, 6 |
| New optional `genero` (`masculino`/`femenino`, only for gender-aware sources) | Not parsed today. Used as a consistency check against the folder, only when present. | 1, 3, 6 |
| `abc_es_local` becomes `boolean \| null` (null when unpublished) | Safe. `Acta.abcIsHome` is already a `Boolean`, and no code branches on it. `FcttActaOrientation.isMirrored` returns false for empty `partidos`, so unpublished actas pass through unchanged. | Test in 7 |
| `partidos` `minItems: 1` removed (now `maxItems: 0` when unpublished) | Safe. `Acta` normalises to `List.of()`, and the match processor won't reach game storage for unpublished actas. | 4 |
| Lineup side `minProperties: 3` removed (now `maxProperties: 0` when unpublished) | Safe. `ActaLineups` normalises `{}` to `Map.of()`. | 4 |
| Published actas: `abc_es_local` boolean, ≥ 1 game, 3 players per side (`allOf` else-branch) | Nothing in the import enforces these today. Preview warns when a published acta violates them. | 6 |
| Not documented: unpublished actas carry a placeholder `resultado_final` | This is a gap in the schema contract and the main data-integrity risk. | 8 |

## Steps

1. **Parser: add the new payload fields** (`tt-data-league-import`, `shared/parse/acta/Acta.java`).
   - Add `@JsonProperty("id_partido") String matchId`, `@JsonProperty("acta_publicada") Boolean published`,
     `@JsonProperty("fase") String phase` and `@JsonProperty("genero") String gender` to the `Acta` record.
   - Add `isPublished()`, which returns `!Boolean.FALSE.equals(published)`. The model definition says a
     missing value means published, so RFETM and BCNESA behaviour is unchanged.
   - Update every `new Acta(...)` call site and test fixture for the new record components.
     `FcttActaOrientation` and any RFETM/BCNESA helpers that copy an `Acta` must carry the new fields through.

2. **Report context: add gender and optional group** (`fctt/process/FcttMatchReportContext.java`).
   - Add a `gender` component that holds the folder value (`male` or `female`).
   - Add `sex()`, which maps `male` to `masculino` and `female` to `femenino`, the same vocabulary as RFETM.
   - Change `competition()` to return `MatchReportContext.competitionOf(leagueCompetition, sex())`,
     for example `tercera-nacional-masculino`. This follows the RFETM convention of folding gender into
     the competition name, so no schema change is needed.
   - Make `group` nullable for competitions without group folders. `groupNumber()` returns
     `OptionalInt.empty()` for a null group. Add `hasGroupFolder()` so the navigator can tell "no group
     folder" apart from "unparseable group folder".
   - Add `phase()`, which returns the trimmed `acta.phase()` or `null`.
   - Update the compatibility constructor and every caller.

3. **Navigator: support the new layout** (`fctt/traverse/FcttActasDirectoryNavigator.java`).
   - New layout: `<base>/<season>/<male|female>/<competition>/[<group>/]jornada-<d>-partido-<x>.json`.
   - Set `MATCH_REPORT_FILE_PATTERN` to `jornada-\d+-partido-[\d-]+\.json`. Drop the underscore form
     (see the Implementation Guidelines).
   - Add `GENDER_FOLDERS = Set.of("male", "female")`. Warn and skip any other folder at that level,
     mirroring RFETM's `SEX_FOLDERS` handling.
   - At competition level, report files directly inside the competition folder are dispatched with a null
     group. Subfolders are treated as group folders, as today.
   - Skip a report with a warning when a present group folder is not `G<n>` or `<n>` (current behaviour).
   - Skip a report with a warning when the payload's `genero` is present and contradicts the folder.
   - Update `countReportFiles` to use exactly the same walk, so the progress totals stay correct.
   - Update the class Javadoc with the new layout.

4. **Match processor** (`fctt/process/FcttMatchImportProcessor.java`).
   - Unpublished actas (`!acta.isPublished()`): do **not** store a `Match`, lineups or games. Log at
     debug level with the file name. This avoids persisting the placeholder `resultado_final` (C5).
     Storing them as scheduled fixtures is deferred to a later feature (see Notes).
   - Pass `context.phase()` to `findMatchByNaturalKey` instead of `null`, and set `.phase(context.phase())`
     on the `Match` builder.
   - Pass the nullable group number (`Integer`) to both the natural-key lookup and the builder. Remove the
     early return for group-less reports; keep it only for present-but-invalid group folders.

5. **Team and player processors** (`FcttTeamImportProcessor`, `FcttPlayerImportProcessor`).
   - Check that they don't read the group folder or the competition folder directly. If they do, switch
     them to the context accessors from step 2.
   - Unpublished actas still register teams, because `equipos` is populated. Lineups are empty, so no
     player registrations are created. No other change is expected.

6. **Preview validation** (`fctt/process/FcttPreviewValidationProcessor.java`).
   - Unpublished actas: emit one info message ("FCTT acta not published; fixture will not be stored as a
     match") and skip the lineup, doubles and game checks. Today they would be reported as
     "ready to simulate: 0 game(s)", which is misleading.
   - Published actas: warn when `abc_es_local` is null, `partidos` is empty, or a lineup side doesn't have
     exactly 3 players, following the schema's published-acta rules.
   - Report the invalid-group error only for a group folder that is present but unparseable, not for
     group-less competitions.
   - Report a `genero`/gender-folder mismatch as an error.

7. **Tests** (JUnit 5, in-memory repositories).
   - `FcttActasDirectoryNavigatorTest`: rewrite the fixtures for the new layout. Cover:
     male and female dispatch; the resulting competition values (`tercera-nacional-masculino`,
     `copa-catalana-femenina-1a-femenino`); an unknown gender folder is skipped; a group-less female
     competition is dispatched with a null group; an invalid group folder is skipped; an old-style
     `jornada_1_partido_1.json` is not matched; `partido-<a>-<b>` unpublished names are matched; a
     `genero`/folder mismatch is skipped; the progress total equals the files seen.
   - `FcttImportProcessorsTest`: an unpublished acta stores no match; phase is stored and is part of the
     natural key (re-import stays idempotent); a group-less female report stores a match with a null
     `groupNumber`.
   - Parser test: the new fields are read from a sample based on `jornada-1-partido-2478.json`; a missing
     `acta_publicada` is treated as published; RFETM fixtures still parse.
   - Unpublished-acta fixture based on `jornada-10-partido-78-86.json` (`abc_es_local: null`, empty
     `partidos`/`alineaciones`, placeholder `resultado_final`). It parses, `FcttActaOrientation` leaves it
     unchanged, no match is stored, and the preview emits the not-published info message.
   - `FcttPreviewValidationProcessorTest` (new or extended): the published-acta warnings, the group-less
     competition case, and the `genero` mismatch error.
   - Keep test resources small and synthetic. Don't copy full extractor exports into the repository.

8. **Documentation**.
   - `docs/acta-model-definition.json`: include the pending working-tree update in this feature. Also:
     change the title to cover all sources ("Modelo de acta de partido", not "de la RFETM"); state in the
     `resultado_final` description that it is a placeholder when `acta_publicada` is false and must not be
     read as a result; state in the `id_partido` description that it is the same for a fixture's published
     and unpublished versions.
   - `tt-data-league-import-runtime/README.md`: describe the new FCTT folder layout, the gender level,
     group-less competitions, and that unpublished actas are not stored as matches.
   - `docs/analysis/analysis-incremental-actas-for-current-jornada-import.md`: update the FCTT layout row.
   - `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md`: document the FCTT `competition` value
     format (`<slug>-<masculino|femenino>`) and that FCTT now populates `match.phase`. There is no column change.

9. **Validation**.
   - Run `mvn -pl tt-data-league-import -am test`, then the full `mvn test`.
   - Run a manual report-mode import against `C:\git\fctt-extract\resources\actas-json` for `2025-2026`
     and `2026-2027`. Expect 759 files seen, 0 parse skips, and every unpublished acta excluded from the
     match count.

# Implementation Guidelines

- Keep the change inside `tt-data-league-import` plus documentation. No domain, JPA or schema changes:
  `Match.phase` and a nullable `groupNumber` already exist, and `findMatchByNaturalKey` already takes
  `phase` and an `Integer` group.
- Support only the new layout. The extractor migrated the 2025-2026 export as well, so no current data
  uses the old layout. Old-layout folders are skipped with warnings (an unknown gender folder), never
  silently reinterpreted.
- Gender is always explicit, from the folder. Never infer it from the competition name.
  The payload's `genero` is a consistency check only.
- Don't use the placeholder `resultado_final` of unpublished actas for anything. Don't derive winners,
  standings or statistics from it.
- `id_partido` is parsed and available in logs and preview output, but it is **not** persisted in this
  feature. It isn't needed for identity, because the natural key already covers the match.
- Don't add an external id to `FederatedClub` or `FederatedPlayer`. Team ids in file names and `equipos.*.id`
  stay import-only data.
- Out of scope: the match lifecycle (a `SCHEDULED`/`PLAYED` status, storing unpublished actas as
  scheduled fixtures, upgrading them in place), migrating existing FCTT rows, and any frontend change.

# Notes

- 2026-09-26: Plan written after comparing `fctt-extract` commits `3cef9a8~5` (old layout) and `3cef9a8`
  (new layout). The registry description covered C1 only; C2–C5 were found in the extractor diff.
- 2026-09-26: Reviewed the uncommitted `docs/acta-model-definition.json` update. Its changes are
  backward-compatible for RFETM and BCNESA, because all new fields are optional and the relaxed
  `partidos`/lineup cardinalities are already tolerated by the parser. Added the schema impact table,
  preview rules (step 6), unpublished-acta fixture tests (step 7) and schema documentation fixes (step 8).
- 2026-09-26 **Decision (user):** storing unpublished actas as `SCHEDULED` matches (the match lifecycle
  from the incremental-actas analysis, tasks T2/T4/T5/T6) will be implemented later in a separate
  feature. It was briefly added to this plan and then removed. Until then, the FCTT match processor
  skips unpublished actas, so no placeholder results are persisted.
- **Existing FCTT data:** C3 (competition slug + gender suffix) and the phase now in the natural key both
  change the match identity. Re-importing on top of existing FCTT rows would duplicate matches. The FCTT
  data must be cleared and fully re-imported after this change. This is an operational step, not a migration.
- **`id_partido` as a future key:** it encodes season, category, group, phase, team ids and round, and is
  the same before and after publication. That makes it the natural candidate for upgrading a scheduled
  fixture to played in place in the later lifecycle feature. It is not persisted here.
- **Open question — female JSON layout:** female JSON hasn't been produced yet. The plan assumes reports
  sit directly under the competition folder (no group). `id_partido` has a mandatory group segment, so
  female exports may still carry a group token even without a group folder. If the extractor emits a
  group folder, the existing group-folder path covers it unchanged.
- **Open question — female competition phases:** "Copa" competitions may use knockout phases. The
  `fase` value flows into `Match.phase` as-is; no normalization is planned.
- 2026-09-26 **Implementation finalized.** Steps 1-8 delivered in `tt-data-league-import` (parser,
  `FcttMatchReportContext`, navigator, match/preview processors) plus the schema and documentation
  updates listed in step 8. Added JUnit fixtures `acta_fctt_unpublished.json` and
  `acta_fctt_female_groupless.json`, a `fase` field on the shared `acta_doubles.json` fixture, and new
  tests in `FcttActasDirectoryNavigatorTest`, `FcttImportProcessorsTest`, `ActaParserTest`, and the new
  `FcttPreviewValidationProcessorTest`.
  - `mvn -pl tt-data-league-import -am test`: 161 tests run, 8 failures — all 8 pre-exist on
    unmodified `main` (`BcnesaImportProcessorsTest` x3, `FcttImportProcessorsTest.storesClubsAndPlayersUnderTheFcttSource`,
    `ImportProcessorsTest` x3, `TeamToClubConsolidationProcessorTest.groupsNormalizedSpellingsAndVerifiedAbbreviations`),
    verified by re-running the same command against a stash of this change. No new failures.
  - `tt-data-league-core-domain`: `InitialUserProvisioningServiceTest` (4 failures) also pre-exists on
    `main`, unrelated to this feature (auth module, not touched here).
  - `tt-data-league-core-repository-jpa` module tests error out with Spring context failures in this
    sandbox (likely missing Testcontainers/Docker access); no code in that module was changed by this
    feature, so this is an environment limitation, not a regression.
  - Step 9's manual report-mode import against `C:\git\fctt-extract\resources\actas-json` was not run
    (no access to that path from this session); the automated tests above cover the same behavior with
    synthetic fixtures.
  - All seven registry acceptance criteria are met and checked off.
