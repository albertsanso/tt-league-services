# Incremental actas import for the current jornada: feasibility analysis and plan

Date: 2026-09-27 (revision 3; first version 2026-09-26)
Scope: `tt-data-league-core-domain`, `tt-data-league-core-repository-jpa`, `tt-data-league-import`,
`tt-data-league-import-runtime`, and the upload and read paths in `tt-data-league-api-rest` /
`tt-data-league-api-mcp`.
Status: analysis only. The match lifecycle has not been implemented. FEAT-00074 (FCTT format change)
already delivered the parser side of the new acta schema and, as an interim measure, makes FCTT skip
unpublished actas instead of storing them.

> **Revision note 3 (2026-09-27): measured example actas for all three sources.** The extractor
> outputs for RFETM (`C:\git\rfetm-extract-2\resources\actas-json`), BCNESA
> (`C:\git\bcnesa-extract-2\resources\actas-json`) and FCTT (`C:\git\fctt-extract\resources\actas-json`)
> were scanned file by file (section 2.5). The main changes to the plan:
>
> - **All three sources now send `acta_publicada` and `id_partido` for 2026-2027.** `acta_publicada`
>   is the primary classification signal everywhere, not just for FCTT. Content rules are only a
>   fallback for legacy RFETM files (up to 2025-2026), which have no flag.
> - **The 2026-2027 exports are real incremental snapshots.** RFETM has 76 published and 2,970
>   unpublished actas. FCTT has jornada 1 half published and jornada 2 unpublished. BCNESA has the
>   full calendar (2,882 fixtures), all unpublished.
> - **BCNESA pending actas are now confirmed and are silently dropped today.** The splitter returns no
>   fixture when `partidos` is empty (G4). BCNESA 2026-2027 also adds team ids and a new file name
>   pattern.
> - **New hazards:** legacy RFETM "0-0 decided" actas that look played but are not (G17). FCTT
>   placeholder fixtures with no teams (G18). A BCNESA placeholder score on an unpublished acta (G14).
>   RFETM competition-name casing that changes on publication (G19, harmless today).
> - **Schema working copy.** The latest edits to `docs/acta-model-definition.json` drop the warning
>   that an unpublished `resultado_final` is a placeholder. The data shows that the warning is still
>   needed (section 2.6).

> **Revision note 2 (2026-09-26).** The acta schema models publication state explicitly: an optional
> `acta_publicada` (missing means published), a stable `id_partido`, a nullable `abc_es_local`, and
> conditional rules. An unpublished acta carries no games or lineups. A published acta has at least
> one game and at least three players per side.

> **Revision note 1 (2026-09-26).** Every fixture in the export is stored as a `Match`, pending ones
> included, so that a future feature can manage the whole season calendar. A match's lifecycle is
> explicit (`MatchStatus`: `SCHEDULED` → `PLAYED`). A scheduled match is upgraded in place when its
> results arrive, and every statistic counts only `PLAYED` matches.

## 1. Executive summary

Moving actas import from whole-season historical loads to incremental loads, one current jornada at
a time, is **feasible**. The 2026-2027 example exports show the exact scenario: for each source,
a snapshot of the season mixes published actas (results up to the last jornada played) with
unpublished ones (fixtures planned but not played). Every source marks the difference with
`acta_publicada` and names each fixture with a stable `id_partido`.

Three current behaviours block the change:

1. **Skip-if-exists never upgrades a match.** Every match processor treats "a match with this natural
   key already exists" as "done, skip it". Once a fixture is stored while it is still empty, its
   played version is **never** written, and its result is lost.
2. **A stored match cannot say whether it was played.** `match_record` has no status. Every read path
   assumes a stored match was played. RFETM already stores empty legacy actas, 63 files in the example
   seasons, as winner-less matches, and these show up as **DRAW** in tie-eligible competitions.
3. **Pending actas are handled differently by each source.** For RFETM 2026-2027, the processor would
   store 2,970 empty matches as phantom draws. For FCTT, the processor skips them (FEAT-00074). For
   BCNESA, the splitter drops all 2,882 of them. None of these is the required behaviour.

Recommended approach, in priority order:

1. Classify each acta as `PLAYED`, `PENDING`, `PARTIAL`, or `INVALID`. `acta_publicada` decides first,
   and a placeholder `resultado_final` is never read. Content rules apply only to legacy files with
   no flag.
2. Introduce `MatchStatus { SCHEDULED, PLAYED }` in the domain and the schema, and backfill existing
   rows.
3. Make every outcome, statistic, and count read path consider only `PLAYED` matches. This fixes the
   existing phantom draws and must ship before more scheduled rows are written.
4. Persist a pending acta as a `SCHEDULED` match. When its published acta arrives, **upgrade the same
   match in place** (same UUID) to `PLAYED`, in one transaction.
5. Store `id_partido` as a source fixture id and use it as the cross-check for the natural key.
6. Derive jornada progress per competition/group from `match_record` statuses.
7. Formalise the ZIP contract: a season snapshot re-uploaded each jornada is the default.

## 2. Current state

### 2.1 End-to-end flow

```text
REST upload (ImportResourceController.uploadZipFile)
  └─ ResourceUploadService → ResourceZipService.extractZipAndGetManifest
       manifest.json = { source, seasons[], assets{ ACTAS|TEAMS: { files[] } } }
  └─ ResourceRepositoryLoaderService.loadIntoRepository
       for each asset, for each season:
         deleteRecursively(<importFolder>/import-<source>/<asset>/<season>)   ← wipes the season
         move extracted files into it
       ACTAS → Resource + ImportResource(source, type, season) set to PENDING
StartImportProcessCommandHandler (one run system-wide)
  └─ NavigatorBackedImportResourceProcessService.process(ImportResource)
       └─ NavigatorImportExecutionService.execute(source, folder, season)
            └─ {Rfetm|Bcnesa|Fctt}ActasDirectoryNavigator.traverseSeason(...)
                 parse every acta file of the season → dispatch to processors
                 (team @Order 10 → player @Order 20 → match @Order 30)
            └─ optional club/player consolidation when traversal was SUCCESS
       ImportResource.finishProcessing(status == SUCCESS)  → PROCESSED | ERROR
```

The runtime CLI (`tt-data-league-import-runtime`) goes through the same `NavigatorImportExecutionService`
using `--actas-folder` and `--season`. Schema changes are applied by Hibernate `ddl-auto: update`
in both runtimes. There is no Flyway or Liquibase.

### 2.2 Source layouts

| Source | Layout (2026-2027 export) | Unit of dispatch | Competition identity | Round comes from |
| --- | --- | --- | --- | --- |
| RFETM | `<season>/<competition>/<day>/<sex>/acta_<homeId>_<awayId>.json` | one acta = one match | path: `<competition>-<sex>` (`MatchReportContext.competition`) | payload `jornada`, falling back to the `<day>` folder |
| FCTT | `<season>/<male\|female>/<competition>/[<group>/]jornada-<d>-partido-<matchNo \| homeId-awayId \| pendiente>.json` | one acta = one match | path | payload `jornada` |
| BCNESA | `<season>/<competition>/<group>/<phase>/acta_<homeId>-<awayId>_<jornada>.json` (was `acta_<jornada>_page_<n>.json` up to 2025-2026) | one fixture per file, via `BcnesaMatchdaySplitter` | path, verbatim | payload `jornada` |

### 2.3 What already works in favour of incremental import

- **Re-import is idempotent.** `MatchRepository.findMatchByNaturalKey(competition, season, group,
  round, phase, homeTeamId, awayTeamId)` is backed by the unique constraint
  `uk_competition_season_group_round_teams`. The same natural key identifies a fixture whether it is
  scheduled or played, so it is the right key for an in-place upgrade.
- **Competition identity comes from the path.** RFETM changes the payload `competicion` from upper
  case (unpublished) to mixed case (published) (G19). The match key is unaffected because it is built
  from folders.
- **Season scoping.** `traverseSeason` and `ImportResource` are per `(source, season)`.
- **Re-upload re-arms the resource.** A new upload of the same season moves a finished `ImportResource`
  back to `PENDING`.
- **Single-run guard.** `StartImportProcessCommandHandler` and `ImportRunRegistry` allow only one import
  at a time.
- **Teams and players.** Team processors (`@Order 10`) register the teams of a pending acta, which a
  scheduled match needs anyway. Player processors do nothing with empty lineups.
- **Cost.** Re-walking a whole season is cheap: RFETM 2026-2027 has 3,046 files.
- **The parser is ready.** Since FEAT-00074, `Acta` exposes `published()` / `isPublished()`, `matchId()`
  (`id_partido`), `phase()`, and `gender()`. It tolerates a null `abc_es_local` and empty `partidos` /
  `alineaciones`. `ActaGame` parses `no_disputado`. All parser records use
  `@JsonIgnoreProperties(ignoreUnknown = true)`, so legacy RFETM `incidencia` is ignored. A doubles
  `jugadores: []` (new in the schema) parses as an empty list.
- **Every pending acta carries a schedule.** Across all three sources, every unpublished acta that
  names its teams also has `fecha`, `hora`, and `lugar`. The only exceptions are the two FCTT
  no-team placeholders (G18).

### 2.4 Gaps and defects found

| # | Finding | Location | Impact |
| --- | --- | --- | --- |
| G1 | **Skip-if-exists never upgrades a match.** An existing natural key returns early. | `RfetmMatchImportProcessor.process` (l.103), `FcttMatchImportProcessor.process` (l.109), `BcnesaMatchImportProcessor.process` | Results of a fixture first seen as pending are lost. **Blocker.** |
| G2 | **No match lifecycle state.** `Match` / `match_record` cannot say whether a match was played. RFETM stores empty actas as matches with null or 0-0 scores, no winner, and no games. It would do this for all 2,970 unpublished 2026-2027 actas, because `RfetmMatchImportProcessor` never checks `isPublished()`. FCTT skips `acta_publicada: false` (l.86), so FCTT pending fixtures are **not stored at all**. | `Match`, `MatchJPA`, `buildMatch` in the processors | Scheduled and played matches cannot be told apart. **Blocker.** |
| G3 | **Read paths assume "stored = played".** `MatchOutcome.teamOutcome` returns **DRAW** for any winner-less match in a tie-eligible competition. Match counts and lists include every row. | `MatchOutcome`; `FindMatchDetailsQueryHandler`, `FindPlayerDetailsQueryHandler`, `FindClubDetailsQueryHandler`, `FindFederatedClubDetailsQueryHandler`, `FindFederatedClubCompetitionDetailsQueryHandler`, `FindClubsByStringInNameQueryHandler`, `SearchMatchesQueryHandler`; `MatchRepositoryHelper` (`countBySeason`, `findAllSeasons`, `findAllBySource`, fragment search, `searchMatches` / `countMatches`); community statistics; REST and MCP match DTOs | Phantom draws, inflated counts, and wrong win rates. Some rows already exist (legacy RFETM empty actas). |
| G4 | **BCNESA drops every pending acta.** `BcnesaMatchdaySplitter.split` returns `List.of()` when `partidos` is empty (l.46), so none of the 2,882 unpublished 2026-2027 fixtures is dispatched to the match processor. Each of these files is exactly one fixture, and its `equipos` names both teams with ids. | `BcnesaMatchdaySplitter`, `BcnesaMatchImportProcessor` | **Confirmed.** A pending acta must yield one fixture named by `equipos`. |
| G5 | **No jornada tracking.** `ImportResource` has no jornada information, and `finishProcessing` never sets `lastProcessedDate`. | `ImportResource`, `import_resource` | The requirement to "keep track of the last/current jornada imported" is not met. |
| G6 | **The upload wipes the season folder.** `loadIntoRepository` runs `deleteRecursively(seasonFolder)` before moving files. A ZIP holding only the current jornada deletes the earlier jornadas from disk. | `ResourceRepositoryLoaderService.loadIntoRepository` | Safe only when every ZIP is a full-season snapshot. |
| G7 | **No update or delete ports for match content.** `Lineup`, `Game`, `SetScore`, and `DoublesPair` repositories expose only `save*`. `Match` is immutable (builder with `createExisting`). | domain repository ports | An in-place upgrade, a reschedule, or a correction needs new ports. |
| G8 | **The natural key depends on `jornada` being stable.** RFETM takes the round from the payload and falls back to the day folder. In RFETM 2025-2026, payload `jornada` differs from the day folder in **3,761 of 4,017** files. In 2026-2027 the two agree in all 3,046 files. The payload is authoritative, so this is safe while the field is present. A file without `jornada` would silently get a different key. | `RfetmMatchImportProcessor.resolveRound` | Duplicate fixtures if the fallback is ever used on a mixed snapshot. `id_partido` embeds the round and gives an independent check (G16). |
| G9 | **Reschedules and postponements.** A fixture of jornada *N* can move date or venue, or be played after jornada *N+1*. Nothing today updates a stored match's date or venue. | domain semantics | A stale calendar. A "max round" watermark is not a safe file filter. |
| G10 | **An all-pending run fails.** When nothing is dispatched, the status is `EMPTY_RESULT` and the resource ends as `ERROR`. BCNESA 2026-2027 today is exactly this case (G4). | `NavigatorImportExecutionService.execute`, `StartImportProcessCommandHandler.runAsync` | Misleading status. |
| G11 | **Preview does not classify.** Only FCTT preview reports unpublished actas. RFETM and BCNESA preview do not report played, pending, or partial actas, or new versus upgraded matches. | `*PreviewValidationProcessor`, `ActaPreviewValidationSupport` | Operators cannot see what an incremental upload will change. |
| G12 | **The manifest is strict.** `validateManifest` requires exactly `source`, `seasons`, and `assets`. | `ResourceZipService.validateManifest` | Contract change needed for a delta mode. |
| G13 | **Schema migration relies on `ddl-auto: update`.** Hibernate can add a column, but a `NOT NULL` column without a default fails on a populated `match_record`, and Hibernate never backfills. | runtime `application.yml` | The status column needs a database default and an explicit backfill (T3). |
| G14 | **Unpublished actas can carry a fake result.** `resultado_final` stays required. Most unpublished actas have all-null values, but some do not. All 4 unpublished FCTT 2025-2026 actas name the home team as `ganador`, 6-0. BCNESA 2026-2027 `1a Comarcal/G1/1a Fase/acta_450-333_1.json` has `marcador_partidos` 4-4 with no winner. | extractor outputs, schema `resultado_final` | A content rule that reads `resultado_final` would store these as real wins or draws. The classifier must decide on `acta_publicada` first, and nothing may read `resultado_final` of an unpublished acta. **Blocker** for T1. |
| G15 | **The file name changes on publication (FCTT).** An unpublished FCTT acta is `jornada-<d>-partido-<homeId>-<awayId>.json`, and a published one is `jornada-<d>-partido-<matchNo>.json`. BCNESA and RFETM keep team ids in the name for both versions. | FCTT extractor | File names cannot link the two versions of an FCTT fixture. Identity must come from the payload (natural key or `id_partido`). |
| G16 | **The stable fixture id is not persisted.** `id_partido` is present and unique per season in every 2026-2027 file of all three sources. It is absent from legacy RFETM (≤ 2025-2026) and legacy BCNESA (≤ 2025-2026) files. It is built from payload fields present in both versions, so it is stable across publication. Its format differs by source (2.5). FEAT-00074 parses it but does not store it, and `match_record.external_id` is `VARCHAR(20)`, too short. | `Acta.matchId()`, `match_record` | No column holds a source-supplied fixture key. |
| G17 | **Legacy RFETM "decided 0-0" actas look played.** 19 RFETM 2025-2026 files (for example `divisio-honor/1/femenino/acta_27810.json`) have every game marked `no_disputado: true` with `motivo: "Victoria decidida (0-0)"`, no sets, and `resultado_final` 0-0 with no winner. They have no `acta_publicada`. A rule such as "every game is played or `no_disputado`" would classify them as `PLAYED`. | legacy RFETM export | Phantom 0-0 draws. The fallback content rule must require **at least one game with a result**. |
| G18 | **FCTT fixtures with no teams.** FCTT 2026-2027 `female/copa-catalana-femenina-{1a,2a}/jornada-1-partido-pendiente.json` are unpublished, with null team ids and names, null `fecha` / `hora` / `lugar`, `grupo: 0`, and `id_partido` `…_1aFase_pendiente_1`. The file name holds at most one such placeholder per competition and jornada. | FCTT extractor (cup draws not yet made) | Cannot be stored as a match: there are no teams for the natural key. Count them and report them as unresolved (R9). Never create a team called `null`. |
| G19 | **RFETM `competicion` casing changes on publication.** Unpublished: `DIVISIÓN DE HONOR FEMENINA`. Published: `División de Honor Femenina`. Four unpublished actas already use mixed case. | RFETM extractor | Harmless while competition identity comes from the path (2.3). Any future use of the payload name must normalise it. |
| G20 | **Games not played in a published acta.** 61 of 76 published RFETM 2026-2027 actas, and most RFETM 2025-2026 actas, contain games with `no_disputado: true` (the tie was decided before they were played). | RFETM payload | Normal, not `PARTIAL`. The classifier must treat `no_disputado` games as complete. Game storage already handles them today. |
| G21 | **BCNESA "Other" groups use the old file name for a fallback.** `OTHER_GROUP_ROUND_FROM_FILE_NAME` parses `acta_<jornada>_page_*.json`, which the new naming (`acta_<home>-<away>_<jornada>.json`) no longer matches. 2026-2027 has no `Other` group folder, and payload `jornada` is always present. | `BcnesaActasDirectoryNavigator` (l.80) | Latent. **Addressed by FEAT-00091 (T15):** the fallback now accepts both names and reports every use through `ImportRunContext.recordRoundFallback` instead of guessing; the payload `jornada` stays authoritative. FEAT-00094 adds the `jornada_<NN>_local_team_<id>_away_team_<id>.json` name. |

### 2.5 Measured example actas

Scanned 2026-09-27. "Empty" means `partidos` empty **and** both `alineaciones` sides empty. No file
failed to parse, and no season has a duplicate `id_partido`.

**RFETM** (`C:\git\rfetm-extract-2\resources\actas-json`)

| Season | Files | `acta_publicada` | `id_partido` | Empty | Notes |
| --- | --- | --- | --- | --- | --- |
| 2019-2020 | 2,601 | missing | missing | 7 | legacy `incidencia` field |
| 2020-2021 | 1,518 | missing | missing | 3 | |
| 2021-2022 | 808 | missing | missing | 7 | |
| 2022-2023 | 3,853 | missing | missing | 9 | |
| 2023-2024 | 3,958 | missing | missing | 18 | |
| 2024-2025 | 3,864 | missing | missing | 19 | |
| 2025-2026 | 4,017 | missing | missing | 0 | 19 "decided 0-0" actas (G17); `jornada` ≠ day folder in 3,761 (G8) |
| **2026-2027** | **3,046** | **76 true / 2,970 false** | all | 2,970 (all unpublished) | first incremental snapshot: jornada 1 partly published |

RFETM 2026-2027 unpublished acta (`divisio-honor/1/femenino/acta_20201878_1052.json`): teams with ids,
`fecha`, `hora`, `lugar`, and `grupo`/`jornada`. `abc_es_local: null`, `dobles: null`, and all
`resultado_final` values null. The **designated referee** is already filled in (`arbitros.principal`).
There is no `fase`. `id_partido` format: `<season>_<competition-folder>_G<group>_J<round>_<homeId>-<awayId>`
(for example `2026-2027_divisio-honor_G3_J1_20201878-1052`). Gender is not in the id. It is unique
anyway because team ids differ.

**BCNESA** (`C:\git\bcnesa-extract-2\resources\actas-json`)

| Season | Files | `acta_publicada` | `id_partido` | Empty | Notes |
| --- | --- | --- | --- | --- | --- |
| 2020-2021 → 2025-2026 | 866 / 1,857 / 3,074 / 3,246 / 3,629 / 3,715 | all true | missing | 0 | team `id` null; `acta_<jornada>_page_<n>.json` |
| **2026-2027** | **2,882** | **all false** | all | 2,882 | full calendar; team ids present; 1 placeholder score (G14) |

BCNESA 2026-2027 unpublished acta (`1a Comarcal/G1/1a Fase/acta_151-247_2.json`): teams **with ids**
(new for BCNESA), `fecha`, `hora`, `lugar`, and `fase`. `id_partido` format:
`<season>_<competition-no-spaces>_G<group>_<phase-no-spaces>_<homeId>-<awayId>_<round>`.

**FCTT** (`C:\git\fctt-extract\resources\actas-json`)

| Season | Files | `acta_publicada` | `id_partido` | Empty | Notes |
| --- | --- | --- | --- | --- | --- |
| 2025-2026 | 510 (male and female) | 506 true / 4 false | all | 4 | all 4 unpublished carry a home-win 6-0 placeholder |
| **2026-2027** | **36** | **9 true / 27 false** | all | 27 | jornada 1: 3 published and 2–3 pending per group; jornada 2: all pending; 2 no-team placeholders (G18) |

The FCTT 2026-2027 export is a **sliding window** (jornadas 1–2), not the full calendar measured in
revision 2 (363 fixtures). This matters for reconciliation (T10, K8): a fixture that is absent from a
snapshot is not necessarily cancelled.

**Published-acta rules checked against the examples.** The expected shape of a published acta is:
`acta_publicada` true, `partidos` non-empty with at least one game with a result, and `alineaciones`
non-empty.

| Rule | RFETM 2026-2027 (76) | BCNESA ≤ 2025-2026 (16,387) | FCTT (515) |
| --- | --- | --- | --- |
| `partidos` non-empty | 76 | all | all |
| ≥ 1 game with a result (sets, `ganador`, or `resultado_juegos`) | 76 | all | all |
| ≥ 3 players per `alineaciones` side | 76 | all | all |
| `abc_es_local` boolean | 76 | all | all |

No published acta violates the rules, so an `INVALID` class (4.1) is a safety net, not a known case.
Every unpublished acta has `partidos` empty, both lineup sides empty, and `abc_es_local` null, as the
schema's `then` branch requires.

### 2.6 Schema contract (`docs/acta-model-definition.json`)

| Schema element | Rule | Consequence for this plan |
| --- | --- | --- |
| `acta_publicada` (optional boolean) | `false` when the match has not been played or its acta is not published. **Missing means published.** | The authoritative `PENDING` signal. Present in all 2026-2027 files of every source. Missing only in legacy RFETM, where the content fallback applies. |
| `allOf` `if acta_publicada = false` | `partidos` `maxItems: 0`; lineup sides `maxProperties: 0`. | An unpublished acta is never `PARTIAL` and has no children to write. |
| `allOf` `else` | `abc_es_local` boolean, `partidos` `minItems: 1`, each lineup side at least 3 players. | A published acta that violates this is `INVALID`, not pending. |
| `resultado_final` | Required. | May hold a placeholder on unpublished actas (G14). |
| `id_partido` (optional) | Pattern `^[0-9]{4}-[0-9]{4}_[^_]+_[^_]+_[^_]+_.+$`. | Treat as an **opaque** source key: its segment layout differs by source (2.5). |
| `partidos[].local/visitante.jugadores` | Now `pareja` **or** an empty array (a doubles game not played). | The doubles path must not create a `DoublesPair` from an empty list. |

**Working-copy regressions to fix in the schema.** The uncommitted edits make three changes. They
drop the sentence that `id_partido` is the same in the published and unpublished versions of a
fixture. They drop the warning that `resultado_final` of an unpublished acta is a placeholder. And
they retitle the shared contract "…de la RFETM". The measurements contradict the placeholder removal
(G14), and the fixture-id stability is the basis for T18. Restore both descriptions, and keep the
title source-neutral, because the file is the contract for all three sources (T0).

## 3. Requirements

Functional requirements:

- **R1. Every resolvable fixture is stored.** A `PENDING` acta creates (or keeps) a `Match` with
  `status = SCHEDULED`. It carries teams, competition, season, group, phase, round, date, time,
  venue, and, where present, the designated referee. It has no lineups, games, set scores, doubles
  pairs, results, or winner. The placeholder `resultado_final` is never read.
- **R2. Upgrade in place.** When a `PLAYED` acta arrives for a stored `SCHEDULED` match, the same
  match (same UUID) becomes `PLAYED`. Results, winner, referee, lineups, games, sets, and pairs are
  written in one transaction.
- **R3. Never downgrade.** A `PENDING` or `PARTIAL` acta for a stored `PLAYED` match changes nothing
  and is reported as an issue.
- **R4. Reschedules.** For a match still `SCHEDULED`, changes to date, time, venue, city, or referee
  in a newer acta update the stored match.
- **R5. Statistics count only `PLAYED`.** Every outcome, win-rate, streak, count, and "matches played"
  read path excludes `SCHEDULED`. Match listings expose the status so that a calendar can show both.
- **R6. Idempotency.** Re-uploading the same snapshot changes nothing and succeeds.
- **R7. Jornada tracking.** Per `(source, season, competition, group, phase)`, provide the **last
  complete jornada** (every fixture `PLAYED`), the **current jornada** (the highest round with at
  least one `PLAYED` fixture), and scheduled and played counts. Expose them in the run result and the
  import read model. Example for FCTT 2026-2027 `tercera-nacional/G1`: current jornada 1, last
  complete jornada none (3 of 6 fixtures played).
- **R8. Upload safety.** An incremental upload never deletes previously received acta files unless
  it is declared a full snapshot.
- **R9. Unresolvable fixtures are reported, not guessed.** A pending acta without both teams (G18) is
  counted as unresolved and reported. No team, club, or match is created for it.

Non-functional requirements:

- **R10.** Keep module boundaries: classification in `tt-data-league-import`, status and ports in the
  domain, and schema in the JPA module with `rfetm-datamodel.md` updated.
- **R11.** Keep every lookup source-scoped. Do not add external ids to `FederatedClub` or
  `FederatedPlayer`. The BCNESA team ids that appear in 2026-2027 belong to import or
  season-registration identity, not to club state.
- **R12.** Club and player consolidation must re-point `SCHEDULED` matches exactly as it re-points
  `PLAYED` ones. It works through `findAllMatchesByTeamIds`, which must **not** be filtered by status.

## 4. Proposed design

### 4.1 Acta completeness classifier (import module)

Add a value type `ActaCompleteness { PLAYED, PENDING, PARTIAL, INVALID }` and one shared classifier
over `Acta`, applied per fixture (for BCNESA, after the split). Rules are evaluated in order; the
first that applies wins.

| Step | Condition | Class |
| --- | --- | --- |
| 1 | `acta_publicada` is `false`. | `PENDING`. Stop: `resultado_final`, `abc_es_local`, `partidos`, and `alineaciones` are not inspected (G14). |
| 2 | `acta_publicada` is `true` and the acta breaks the published rules: no games, no game with a result, fewer than 3 players on a side, or a null `abc_es_local`. | `INVALID`: report an issue and write nothing. A stored match keeps its state. |
| 3 | `acta_publicada` is `true`. | `PLAYED`. Games with `no_disputado: true` count as complete (G20). |
| 4 | `acta_publicada` is missing (legacy RFETM only) and the acta is empty, or no game has a result (this includes G17's "decided 0-0"). | `PENDING`. |
| 5 | `acta_publicada` is missing, at least one game has a result, and every game has a result or is `no_disputado`. | `PLAYED`. |
| 6 | `acta_publicada` is missing, and some games have results while others have neither a result nor `no_disputado`. | `PARTIAL`: keep or store as `SCHEDULED`, write no children, report an issue. |

A game "has a result" when it has at least one set, a `ganador`, or a non-null `resultado_juegos`.
`resultado_final` is **never** used to decide the class.

Separately, a `PENDING` acta without both team names (G18) is counted as `unresolvedPendingFixtures`
and is not dispatched to the team or match processors.

### 4.2 Match lifecycle model (domain and JPA)

```text
            PENDING / PARTIAL acta                PLAYED acta
 (absent) ───────────────────────► SCHEDULED ───────────────────► PLAYED
    │                                  │  ▲                          │
    │         PLAYED acta              │  └── reschedule: update     │ PENDING/PARTIAL acta
    └──────────────────────────────────┼───── date/time/venue        │ → issue, no change
                                       │                             │ changed PLAYED acta
                                       ▼                             │ → T13 (correction)
                               missing from snapshot → reported (T10)
```

- `MatchStatus { SCHEDULED, PLAYED }` in the domain, as a `Match` field and builder property.
  "Postponed" and "overdue" are **derived** in the read model, not stored.
- JPA: `match_record.status VARCHAR(20) NOT NULL DEFAULT 'PLAYED'` with `@Enumerated(STRING)`. The
  database default keeps `ddl-auto: update` working on existing data (G13).
- Backfill (T3): set `status = 'SCHEDULED'` where the match has no `game` rows with a result, no
  `winner_team_id`, and null or 0-0 games won. This covers both the empty legacy RFETM actas and the
  G17 "decided 0-0" rows. It is opt-in, in the runtime, with report and write modes.
- `rfetm-datamodel.md` documents the column, the default, the backfill rule, and the invariant
  "`SCHEDULED` ⇒ no lineups, games, set scores, doubles pairs, or winner".

### 4.3 Match write path (processors)

Shared algorithm for RFETM, FCTT, and BCNESA:

```text
classify acta/fixture                                   (4.1)
if INVALID or unresolved → report, stop
resolve teams (unchanged)
existing = findMatchByNaturalKey(...)
cross-check: findBySourceFixtureId(source, id_partido) when present   (T18)
if existing is empty:
    PENDING/PARTIAL → save Match(status=SCHEDULED, no children)
    PLAYED          → save Match(status=PLAYED) + children          (today's behaviour)
else if existing.status == SCHEDULED:
    PLAYED          → replaceMatchContent(existing.id, PLAYED match, children)   (upgrade)
    PENDING/PARTIAL → update schedule fields if changed (reschedule)
else (existing PLAYED):
    PLAYED          → skip (T13 later: detect correction by checksum)
    PENDING/PARTIAL → issue "played match regressed to pending", no change
```

The current `buildMatch` / `buildLineups` / `storeGames` code is reused for the `PLAYED` branch. The
`SCHEDULED` branch builds only header fields and never copies `resultado_final` or the winner.

Source specifics:

- **RFETM.** There is no publication check today (G2). Add the classifier before `buildMatch`.
  Without it, a 2026-2027 upload would write 2,970 phantom draws.
- **FCTT.** Replace the FEAT-00074 early return for `!acta.isPublished()` (l.86) with the `SCHEDULED`
  branch **in the same change**. Until then, that early return keeps placeholder results out of the
  database. Skip G18 placeholders before the team processor. Identity across versions must come from
  the payload, never the file name (G15).
- **BCNESA.** `BcnesaMatchdaySplitter.split` must return one fixture named by `equipos` (with no
  games) when `partidos` is empty (G4), so that team registration and the `SCHEDULED` branch run.
  Team matching stays name-based and source-scoped. The new team ids may be recorded in season
  registration only (R11).

### 4.4 Domain ports for upgrade and reschedule

- `MatchRepository.replaceMatchContent(Match match, List<Lineup>, List<Game>, List<SetScore>,
  List<DoublesPair>)`: one transaction. It deletes the existing children of `match.getId()`, updates
  the header, and inserts the new children. The id is preserved.
- `MatchRepository.updateSchedule(UUID matchId, ZonedDateTime, String city, String venue, referee)`,
  or reuse `saveMatch` with `createExisting` if the JPA mapping merges by id. Verify this in T5.
- `MatchRepository.findBySourceFixtureId(ImportSource, String)` (T18).
- `InMemoryRepositories` in the import tests implement all of them.

### 4.5 Read-side changes

| Consumer | Change |
| --- | --- |
| `MatchOutcome.teamOutcome` / `playerOutcome` | Return empty for `SCHEDULED`. Update the javadoc invariant to "a winner-less **PLAYED** match is a tie". |
| `FindMatchDetailsQueryHandler`, `FindPlayerDetailsQueryHandler`, club detail and competition handlers, `FindClubsByStringInNameQueryHandler` | Compute stats, win rates, form, and streaks over `PLAYED` only. Lists may include `SCHEDULED` with the status shown. |
| `MatchRepositoryHelper.countBySeason`, `countAllMatches`, `findAllSeasons` | `PLAYED` only. Otherwise 2026-2027 would appear as a "current season" with thousands of matches that were never played. |
| `searchMatches` / `countMatches`, fragment search | Status filter defaulting to `PLAYED`, so current API behaviour is unchanged. |
| REST and MCP `MatchDto` / `MatchDetailDto` | Additive `status` field. |
| Consolidation processors (`findAllMatchesByTeamIds*`) | **No filter** (R12). |

### 4.6 Jornada progress

Progress is **derived from `match_record`**:

```sql
-- per (source, season, competition, group_num, phase)
current_round       = max(round) where status = 'PLAYED'
last_complete_round = max(r) such that no match with round <= r has status = 'SCHEDULED'
scheduled / played  = counts by status
```

- Expose it through `MatchRepository.findRoundProgress(source, season)`, the import run result, and
  the import-resource read model. Set `ImportResource.lastProcessedDate` when a run finishes.
- It is informational and **never** used to skip files (G9). Traversal stays full-season.
- Because FCTT exports a sliding window (2.5), "total rounds" is not known from the snapshot. Report
  only what has been stored.

### 4.7 Upload contract

- **Snapshot mode (default, recommended).** Each ZIP holds the season as currently exported, with
  published and unpublished actas. This is what all three extractors produce. The existing
  delete-and-replace of the season folder is correct for this mode. Document it, and reject a
  snapshot that has fewer **published** actas than the stored folder unless an override is given.
  This catches truncated uploads while still allowing FCTT's window to move.
- **Delta mode (optional).** Add an optional manifest `"mode": "snapshot" | "delta"` (G12). In delta
  mode, merge the files into the season folder without deleting, and keep a rollback copy.
- A stored `SCHEDULED` match whose fixture is absent from a snapshot is **reported**, never deleted
  (T10).

### 4.8 Run status and metrics

- Add `scheduledCreated`, `upgradedToPlayed`, `rescheduled`, `partialActas`, `invalidActas`, and
  `unresolvedPendingFixtures` counters to the traversal summaries, `ImportExecutionMetrics`, and
  `ImportProcessResult`.
- "No changes" is `SUCCESS`. `EMPTY_RESULT` is kept for "no actas found at all".

## 5. Prioritised implementation plan

Priority: **P0** is a blocker or data-correctness issue, **P1** is required for the feature, **P2**
is important hardening, and **P3** is follow-up work.

| ID | Pri | Task | Modules | Depends on | Size |
| --- | --- | --- | --- | --- | --- |
| T0 | P0 | **Fixtures and schema text.** Add anonymised JUnit fixtures taken from the example exports: RFETM 2026-2027 published and unpublished; RFETM 2025-2026 "decided 0-0" (G17) and a legacy empty acta; BCNESA 2026-2027 unpublished, including the 4-4 placeholder (G14); FCTT 2026-2027 published and unpublished pair, the no-team placeholder (G18), and the 2025-2026 6-0 placeholder. Restore the schema descriptions listed in 2.6. | import (test resources), docs | — | S |
| T1 | P0 | **Acta completeness classifier.** `ActaCompleteness` and the ordered rules of 4.1, with a test per rule and per T0 fixture. The placeholder and G17 fixtures must classify as `PENDING`. | import | T0 | S |
| T2 | P0 | **`MatchStatus` in domain and JPA.** Enum, `Match` field and builder, `MatchJPA` column `NOT NULL DEFAULT 'PLAYED'`, mappers, and in-memory repositories. Update `rfetm-datamodel.md`. JPA tests for the default and the invariant. | domain, JPA, import (tests) | — | S |
| T3 | P0 | **Backfill legacy empty and "decided 0-0" matches to `SCHEDULED`.** Opt-in runtime command with report and write modes, scoped by source and season. | JPA or import, runtime, README | T2 | S |
| T4 | P0 | **Read-side status filtering.** Every consumer in 4.5, and `status` in the REST and MCP DTOs. Tests for each handler with a mixed `SCHEDULED`/`PLAYED` fixture. **Ship with or before T6.** | domain (application), JPA, api-rest, api-mcp | T2 | M |
| T5 | P0 | **Upgrade and reschedule ports.** `replaceMatchContent` (transactional) and `updateSchedule`, with JPA and in-memory implementations and rollback tests. | domain, JPA, import (tests) | T2 | M |
| T6 | P0 | **Processor lifecycle.** Implement 4.3 for RFETM (add classification), FCTT (replace the FEAT-00074 skip, skip G18 placeholders), and BCNESA (the splitter yields a pending fixture, G4). Handle `jugadores: []` in the doubles path. Update the FCTT preview wording. | import | T1, T5 | M |
| T7 | P0 | **Run status and metrics.** New counters; no-change runs succeed; set `lastProcessedDate`. | import, domain, runtime | T6 | S |
| T18 | P1 | **Persist the source fixture id (G16).** Nullable `match_record.source_fixture_id VARCHAR(100)`, unique on `(source, source_fixture_id)`, a `Match` field, mappers, in-memory support, and `findBySourceFixtureId`. All three sources fill it from `id_partido` on create and on upgrade. Legacy rows stay null. Do **not** reuse `external_id`. Document it in `rfetm-datamodel.md`. | domain, JPA, import | T2, T6 | S |
| T8 | P1 | **Jornada progress query and exposure.** `findRoundProgress`, run result, import-resource read model, CLI summary, and README. | domain, JPA, api-rest, runtime | T2, T6 | S |
| T9 | P1 | **Natural-key stability guard (G8).** Warn when payload `jornada` is missing and the RFETM day-folder fallback is used. When a stored match has the same `id_partido` but a different natural key, raise an issue instead of creating a duplicate. | import | T6, T18 | S |
| T10 | P1 | **Snapshot reconciliation.** After a snapshot run, report stored `SCHEDULED` matches of that season that were not seen, matched by `id_partido` or the natural key. Report only. Do not flag rounds beyond the snapshot's highest round (FCTT window, 2.5). | import, runtime | T6, T18 | S |
| T11 | P1 | **Upload contract.** Document snapshot mode; add the published-count shrink check. | domain (`ResourceRepositoryLoaderService`, `ResourceZipService`), README | — | S |
| T12 | P1 | **Preview classification.** For all sources: counts of published, unpublished, invalid, partial, and unresolved actas; new scheduled matches, upgrades, reschedules, and regressions per competition/group; and the resulting jornada progress. | import (preview processors) | T1, T8 | S |
| T13 | P2 | **Amended-acta detection.** `match_record.source_checksum`. When a `PLAYED` acta's checksum changes, re-apply it via `replaceMatchContent` and log an audit line. Opt-in. | domain, JPA, import | T5 | M |
| T14 | P2 | **Delta upload mode.** Optional manifest `mode`; merge without deleting; keep a rollback copy. | domain, api-rest, README | T11 | M |
| T15 | P2 | **BCNESA navigator cleanup.** Adapt the `Other`-group round fallback to the new file name or drop it in favour of payload `jornada` (G21). Measure whether multi-fixture splitting and `BcnesaClubIndex` are still needed for any file. | import | T0, T6 | S |
| T16 | P3 | **Season calendar / matchday management feature.** API and UI over `SCHEDULED` and `PLAYED` matches. | api, UI | T4, T8 | L |
| T17 | P3 | **Automation.** Scheduled fetch and upload per jornada. | runtime / ops | T11 | M |

### 5.1 Delivery slices

1. **Slice 1: lifecycle foundation (T0 to T4).** Status exists, legacy empty and "decided 0-0"
   matches are marked `SCHEDULED`, and every statistic ignores them. This fixes the existing draw and
   count bug. No import behaviour changes yet.
2. **Slice 2: incremental import (T5 to T7, T18).** Pending actas of all three sources become
   scheduled matches with their `id_partido`. Published actas upgrade them in place. Reschedules
   update them. Run statuses are correct.
3. **Slice 3: visibility and safety (T8 to T12).** Jornada progress, duplicate guard, reconciliation,
   upload checks, and preview.
4. **Slice 4: hardening (T13 to T15).**
5. **Slice 5: calendar feature (T16) and automation (T17).**

**Until slice 2 ships, do not import any 2026-2027 export.** RFETM would store phantom draws, FCTT
would drop its pending fixtures, and BCNESA would end as `ERROR` with nothing stored.

Dependency graph:

```text
T0 → T1 ─────────────┐
T2 → T3              ├→ T6 → T7
T2 → T4 (with T6)    │    ├→ T18 → T9, T10
T2 → T5 ─────────────┘    ├→ T8 → T12
                          └→ T15
T5 → T13
T11 → T14
T4, T8 → T16
```

## 6. Changes to data structures, validation, and error handling

| Area | Change |
| --- | --- |
| Parser records | None required: `Acta` already exposes `matchId`, `published` / `isPublished()`, `phase`, `gender`, and `ActaGame.notPlayed`. Classification lives in the new classifier. |
| Acta schema | Restore the placeholder and `id_partido` stability descriptions, and keep the title source-neutral (2.6). The `jugadores: []` change is compatible with the parser. |
| Domain | `MatchStatus` and `Match.status`; `Match.sourceFixtureId`. `replaceMatchContent`, `updateSchedule`, `findBySourceFixtureId`, and `findRoundProgress`. A status filter on `MatchSearchCriteria`. A status-aware `MatchOutcome`. New counters on `ImportProcessResult` / `ImportExecutionMetrics`. `lastProcessedDate` set on finish. |
| Schema | `match_record.status VARCHAR(20) NOT NULL DEFAULT 'PLAYED'`, indexed on `(source, season, competition, status)`. Nullable `match_record.source_fixture_id` with a unique `(source, source_fixture_id)` constraint. Optional `source_checksum` (T13). All documented in `rfetm-datamodel.md`. |
| Invariants | `SCHEDULED` ⇒ no lineups, games, set scores, doubles pairs, or winner, and null results. `PLAYED` is never downgraded by import. `acta_publicada: false` ⇒ never `PLAYED`, and its `resultado_final` is never persisted. |
| Validation | Classifier (`INVALID` for broken published actas); unresolved pending fixtures (G18); natural-key and `id_partido` consistency (T9); snapshot shrink check (T11); reconciliation (T10). |
| Error handling | Pending is not an error. Partial, invalid, unresolved, and regression cases are reported issues, with no write. Upgrade failures are processor failures, isolated as today, and the transactional port leaves no half-written match. No broad catches and no success-shaped fallbacks. |

## 7. Risk assessment

| # | Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- | --- |
| K1 | A read path misses the status filter, so scheduled matches leak into statistics. | **High** (about ten consumers, G3) | High | T4 enumerates every consumer; a mixed-status test per handler; `MatchOutcome` as a central safety net; search defaults to `PLAYED`. Ship T4 before T6. |
| K2 | The classifier misreads played as pending, or the reverse. | Low for 2026-2027 (explicit flag everywhere); medium for legacy RFETM | High | Flag first (4.1); `no_disputado` handling (G20); "at least one game with a result" (G17); T0 fixtures from real files; preview counts (T12) reviewed before the first production run. |
| K3 | A half-applied upgrade (children deleted, new ones not written). | Low | High | A single transactional `replaceMatchContent`; JPA rollback tests. |
| K4 | `jornada` drift creates a duplicate fixture (G8). | Low (2026-2027 payload and folder agree) | High for the calendar | T9 guard with `id_partido`; T10 surfaces orphans. |
| K5 | The backfill marks a legacy played match as `SCHEDULED`. | Low | Medium | Report mode first; the rule requires no result in any game, no winner, and 0-0 or null scores. |
| K6 | The schema change fails on a populated database under `ddl-auto: update` (G13). | Medium if done naively | High | Database default in `columnDefinition`; test against a copy of production data. |
| K7 | BCNESA team identity changes: 2026-2027 adds team ids and new team spellings (for example `CTT BAUHAUS Castelldefels`). Name-based matching could split registrations. | Medium | Medium | Keep source-scoped name matching; record ids only in season registration (R11); preview warns about new team names; opt-in consolidation. |
| K8 | Fixtures vanish from a snapshot: federation changes, or FCTT's window moves. | Medium | Medium | T10 reports only and ignores rounds beyond the snapshot's range; deletion stays manual. |
| K9 | Consolidation must handle scheduled matches. | Low | Medium | Unfiltered `findAllMatchesByTeamIds` (R12); a consolidation test with a scheduled match. |
| K10 | A truncated snapshot ZIP wipes good files from disk (G6). | Medium | Medium | T11 published-count check; T14 rollback copy; archive uploaded ZIPs. |
| K11 | Team-name drift mid-season (RFETM ≤ 2025 has no team ids). | Low to medium | Medium | Existing `RfetmClubKey`; 2026-2027 RFETM sends ids; preview warns. |
| K12 | Operators see different run statuses (`ERROR` becomes `PROCESSED` for no-change runs). | Certain | Low | README and release notes; explicit counters. |
| K13 | A placeholder `resultado_final` is persisted as a real result (FCTT 6-0, BCNESA 4-4). | High if content rules run first; low with 4.1 | High | Step 1 of 4.1; the `SCHEDULED` branch never copies results; T0/T1 fixtures for both shapes; keep the FCTT skip until T6. |
| K14 | A snapshot holds both the unpublished and the published file of one FCTT fixture (G15), which raises a false regression. | Low (not seen) | Low | Identity by `id_partido`; T12 flags duplicate `id_partido` within a snapshot; prefer the published file within a run. |
| K15 | A 2026-2027 export is imported before slice 2 and writes 2,970 RFETM phantom draws. | Medium (the data is already available) | High | Block 2026-2027 imports until T4 and T6 ship; if it has already happened, T3's backfill repairs it. |
| K16 | An FCTT no-team placeholder (G18) creates a `null` team or crashes team resolution. | Medium | Medium | Filter in T6 before the team processor; count as unresolved; T0 fixture. |

## 8. Open questions for stakeholders

1. **Upload shape.** Will each upload be a full-season snapshot (recommended, and what the extractors
   produce) or only the current jornada? This decides whether T14 is needed.
2. **FCTT window.** Will FCTT keep exporting only the next jornadas (2026-2027: jornadas 1–2), or the
   full calendar as the earlier export did (363 fixtures)? This affects reconciliation (T10).
3. **Corrections.** Do federations amend published actas often enough to bring T13 forward?
4. **Stored states.** Is `SCHEDULED` / `PLAYED`, with derived "overdue" and "postponed", enough for
   the season-management feature, or will it need manual states (for example `CANCELLED`)?
5. **Vanished fixtures.** Should a scheduled match absent from later snapshots eventually be deleted,
   and on whose confirmation?
6. **Legacy RFETM re-extraction.** Can the RFETM extractor re-emit 2019-2020 to 2025-2026 with
   `acta_publicada` and `id_partido`? That would remove the content fallback (4.1 steps 4 to 6) and
   the G17 ambiguity.
7. **Cup placeholders.** When an FCTT cup draw is made, will the `…_pendiente_<n>` file be replaced by
   a file with teams (and a new `id_partido`)? Then the placeholder never needs to be stored.

## 9. Feasibility verdict

**Feasible with moderate effort, and now testable on real data.** All three extractors already emit
the needed signals for 2026-2027: `acta_publicada`, a stable `id_partido`, and teams and schedule for
every resolvable pending fixture. The existing natural key identifies a fixture across its lifecycle,
so an in-place `SCHEDULED` → `PLAYED` upgrade fits the current model.

The effort is concentrated in three places:

- the status model, backfill, and fixture id (small);
- the read-side filtering across roughly ten consumers (medium, and the main risk);
- the transactional upgrade path in three processors, plus the BCNESA splitter change (medium).

The main new hazards found in the examples are placeholder results on unpublished actas (G14), legacy
"decided 0-0" RFETM actas (G17), and FCTT no-team placeholders (G18). The ordered classifier (4.1)
neutralises all three. Slice 1 is worth shipping on its own, because it fixes phantom draws that
historical imports have already caused. No 2026-2027 export should be imported until slice 2 ships.
