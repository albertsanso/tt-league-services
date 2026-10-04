# Build Plan
Source task: **T0** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Sections 2.5, 2.6; gaps G14, G15, G17, G18).

## Acceptance Criteria
- [x] Anonymised fixtures exist for RFETM 2026-2027 published and unpublished actas, RFETM 2025-2026 "decided 0-0" (G17) and a legacy empty acta
- [x] Anonymised fixtures exist for BCNESA 2026-2027 unpublished actas, including the 4-4 placeholder score (G14)
- [x] Anonymised fixtures exist for an FCTT 2026-2027 published/unpublished pair, the no-team placeholder (G18), and the 2025-2026 6-0 placeholder
- [x] docs/acta-model-definition.json restores the id_partido stability and unpublished resultado_final placeholder descriptions and keeps a source-neutral title
- [x] All fixtures parse with the existing Acta parser in a JUnit test

## Starting state (verified 2026-09-27)

- `tt-data-league-import/src/test/resources/actas/` holds six older fixtures (`acta_singles.json`, `acta_doubles.json`, `acta_doubles_matchday.json`, `acta_matchday.json`, `acta_bcnesa_xyz_home.json`, `acta_fctt_abc_away.json`).
- `ActaParserTest` (`tt-data-league-import/src/test/java/org/cttelsamicsterrassa/data/load/parse/ActaParserTest.java`) already loads `acta_fctt_female_groupless.json` and `acta_fctt_unpublished.json`, which are **not in the repository**. Those two tests fail today. This feature supplies both files.
- `docs/acta-model-definition.json`: the title is `"Modelo de acta de partido de la RFETM"`. The `id_partido` description does not say the id is the same in the published and unpublished versions of a fixture. The `resultado_final` description does not warn that it is a placeholder on an unpublished acta.

## Steps

1. **Copy the source actas.** Copy each source file below into `tt-data-league-import/src/test/resources/actas/` under its target name. Source roots: `C:\git\rfetm-extract-2\resources\actas-json`, `C:\git\bcnesa-extract-2\resources\actas-json`, `C:\git\fctt-extract\resources\actas-json`.

   | Target fixture | Source file | Case |
   | --- | --- | --- |
   | `acta_rfetm_2026_published.json` | `2026-2027/divisio-honor/1/femenino/acta_2017543_20222977.json` | RFETM `acta_publicada: true`, has `id_partido` |
   | `acta_rfetm_2026_unpublished.json` | `2026-2027/divisio-honor/1/femenino/acta_20201878_1052.json` | RFETM pending, designated referee filled, all-null `resultado_final` |
   | `acta_rfetm_2025_decided_0_0.json` | `2025-2026/divisio-honor/1/femenino/acta_27810.json` | G17: no `acta_publicada`, every game `no_disputado`, 0-0, no winner |
   | `acta_rfetm_legacy_empty.json` | `2024-2025/divisio-honor/3/femenino/acta_2018837_20233397.json` | legacy empty acta: no `acta_publicada`, empty `partidos` and `alineaciones` |
   | `acta_bcnesa_2026_unpublished.json` | `2026-2027/1a Comarcal/G1/1a Fase/acta_151-247_2.json` | BCNESA pending, team ids, `fase` |
   | `acta_bcnesa_2026_placeholder_4_4.json` | `2026-2027/1a Comarcal/G1/1a Fase/acta_450-333_1.json` | G14: unpublished with a 4-4 `marcador_partidos` and no winner |
   | `acta_fctt_2026_published.json` | `2026-2027/male/tercera-nacional/G1/jornada-1-partido-2993.json` | FCTT published; `id_partido` `2026-2027_tercera-nacional_G1_1aFase_151-123_1` |
   | `acta_fctt_unpublished.json` | derived from `acta_fctt_2026_published.json` (see step 3) | FCTT unpublished version of the **same** fixture (the pair, G15) |
   | `acta_fctt_2026_no_team_placeholder.json` | `2026-2027/female/copa-catalana-femenina-1a/jornada-1-partido-pendiente.json` | G18: null teams, `grupo: 0`, `…_pendiente_1` |
   | `acta_fctt_2025_placeholder_6_0.json` | `2025-2026/male/tercera-nacional/G3/jornada-10-partido-78-86.json` | FCTT unpublished with a home-win 6-0 placeholder (corrected source; the plan's original path is a 0-5 away win, see Notes) |
   | `acta_fctt_female_groupless.json` | `2025-2026/female/copa-catalana-femenina-1a/jornada-1-partido-2876.json` | FCTT female cup, published, `genero` and `fase` present |

2. **Anonymise personal data.** In every new fixture, replace player names with synthetic names (for example `PLAYER HOME X`, `PLAYER AWAY A`) and player licences with synthetic numeric strings. Apply the same mapping everywhere a player appears (`alineaciones`, `dobles`, `partidos[].local/visitante`, doubles `pareja`). Replace referee names (`arbitros`) with synthetic names but keep them non-null where the source had them. Keep team and club names and ids, competition, venue, dates, `id_partido`, `acta_publicada`, scores, and structure unchanged, because the lifecycle features test against those values. Pretty-print as UTF-8 and do not add fields.

3. **Build the FCTT pair.** There is only one snapshot, so no fixture appears both unpublished and published. Build `acta_fctt_unpublished.json` from the published fixture, following the shape of the real unpublished files in the same group (for example `jornada-1-partido-130-149.json`): `acta_publicada: false`; `partidos: []`; both `alineaciones` sides `{}`; `abc_es_local: null`; `dobles: null`; `resultado_final` values null; keep `equipos`, `fecha`, `hora`, `lugar`, `fase`, `grupo`, `jornada`, and the **same** `id_partido`. Do not add a comment field to the JSON (the schema sets `additionalProperties: false`). Say that the file is derived in a one-line comment in the test instead. In the published version FCTT names the file by match number, and in the unpublished version by team ids (G15). The test must show that the two files share `id_partido` but not their file names.

4. **Fix the schema text** in `docs/acta-model-definition.json`. Change only descriptions and the title. Do not change the validation rules.
   - `title`: source-neutral, for example `"Modelo de acta de partido (RFETM, BCNESA, FCTT)"`.
   - `id_partido.description`: add that the value is the same in the unpublished and published versions of a fixture, and that it is an opaque source key whose segment layout differs by source.
   - `resultado_final.description`: add that when `acta_publicada` is `false`, the value is a placeholder (null values, or a fake score such as 6-0 or 4-4) and must not be read as a result.
   - Check the file is still valid JSON (for example `python -m json.tool docs/acta-model-definition.json`).

5. **Align `ActaParserTest`** with the real fixtures. `parsesTheNewFcttPayloadFields` asserts `matchId` `2026-2027_CopaCatFem1a_G1_1aFase_301-402_1` and phase `1aFase`, which do not match the export. Change the assertions to the values of the copied file (`2025-2026_copa-catalana-femenina-1a_1aFase_120-112_1`, phase `1a Fase`, gender `femenino`, published). The parser stores `fase` verbatim, so do not normalise it. Keep `anUnpublishedActaParsesWithEmptyPartidosAndAlineacionesAndAPlaceholderResult` as it is. It now runs against the derived FCTT file.

6. **Add `IncrementalActaFixturesTest`** in `tt-data-league-import/src/test/java/org/cttelsamicsterrassa/data/load/parse/`. Use JUnit 5 and the same `fixture(name)` resource lookup as `ActaParserTest`.
   - A `@ParameterizedTest` over all eleven new fixture names checks that `ActaParser.parse` succeeds.
   - Add one focused test per case. Each checks only the facts that later features rely on:
     - RFETM published: `isPublished()`, `matchId()` not null, games not empty.
     - RFETM unpublished: not published, no games, empty lineups, `abcIsHome()` null, referee present.
     - Decided 0-0: `published()` null, every game `wasNotPlayed()`, no set scores.
     - Legacy empty: `published()` null, no games, empty lineups.
     - BCNESA unpublished: not published, `phase()` present, both team ids present.
     - BCNESA 4-4: not published, and `finalResult()` games are 4-4 with a null winner.
     - FCTT pair: same `matchId()`, one published and one not.
     - FCTT no-team: not published, both team ids and names null, `group()` 0.
     - FCTT 6-0: not published, and `finalResult()` names a winner with a 6-0 score.
   - Do not add classification logic. That belongs to FEAT-00076.

7. **Validate.** Run `mvn -pl tt-data-league-import -am test`, then `mvn test` from the root. Review the diff: only the new fixtures, the two test classes, and the schema file should change. Check it for real player or referee names and licences.

# Implementation Guidelines

- Affected modules: import (test resources), docs.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- Out of scope: production code, the completeness classifier (FEAT-00076), and schema validation rules. No JPA or persistence change, so `rfetm-datamodel.md` and the READMEs stay unchanged.
- Fixtures are test data only. They must not contain real player or referee names or licences.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size S, Slice 1: lifecycle foundation. Depends on: —.
- 2026-09-27: Build plan written and promoted to `ready` at the user's request. Source files verified. The two FCTT fixtures that `ActaParserTest` already references are missing, so those tests fail today, and this feature fixes them. The export holds a single snapshot, so the FCTT unpublished/published pair is derived from the published file (step 3). The expected values in `parsesTheNewFcttPayloadFields` do not match the real export and are aligned in step 5.
- 2026-09-27: Implemented. All eleven fixtures copied/derived and anonymised (player, referee, delegate, and coach names and licences replaced with synthetic values consistently across `alineaciones`, `dobles`, `partidos[]`, and doubles `jugadores`; team/club identity, dates, venue, `id_partido`, `acta_publicada`, and scores kept verbatim). The plan's step-1 source for the 2025-2026 "6-0 placeholder" (`copa-catalana-femenina-1a/jornada-2-partido-113-112.json`) was actually a 0-5 away win, not 6-0; used `2025-2026/male/tercera-nacional/G3/jornada-10-partido-78-86.json` instead, a genuine unpublished 6-0 home-win placeholder. `ActaParserTest.parsesTheNewFcttPayloadFields` updated per step 5; `IncrementalActaFixturesTest` added per step 6 with a parameterized parse-success check plus one focused test per case. `docs/acta-model-definition.json` title/`id_partido`/`resultado_final` descriptions updated and re-validated as JSON.
- 2026-09-27: Validation: `mvn -pl tt-data-league-import -am install -DskipTests` then `mvn -pl tt-data-league-import test` — all new/updated acta-parsing tests pass, and this change fixes the two previously-failing `ActaParserTest` cases. The full `mvn test` reactor still fails, but on pre-existing, unrelated breakage confirmed present without this change via `git stash`: 4 `InitialUserProvisioningServiceTest` failures in `tt-data-league-core-domain`, and 8 import-processor test failures (`BcnesaImportProcessorsTest`, `FcttImportProcessorsTest`, `ImportProcessorsTest`, `TeamToClubConsolidationProcessorTest`) expecting persisted rows that come back empty — out of this feature's scope (production import-processor code, not test fixtures/schema/parser).
- 2026-09-27: Closed `done` at the user's explicit request. All acceptance criteria verified checked; moved to **Done** via the fixed `feature_manager.py status` command.
