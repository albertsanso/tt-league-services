# Incremental actas import for the current jornada: feasibility analysis and plan

Date: 2026-09-26 (revised the same day)
Scope: `tt-data-league-core-domain`, `tt-data-league-core-repository-jpa`, `tt-data-league-import`,
`tt-data-league-import-runtime`, and the upload and read paths in `tt-data-league-api-rest` /
`tt-data-league-api-mcp`.
Status: analysis only. Nothing in this document has been implemented.

> **Revision note.** The first version recommended *not* persisting matches for pending (empty)
> actas. That is reversed here. A future feature will display and manage the whole season, including
> upcoming matchdays, so **every fixture in the acta export must be stored as a `Match`**, pending
> ones included. The design below therefore makes the match's lifecycle explicit with a
> `MatchStatus` (`SCHEDULED` → `PLAYED`). It upgrades a scheduled match in place when its results
> arrive, and it makes every statistic and outcome read path count only `PLAYED` matches. What was
> task T14 ("scheduled fixtures as first-class data", P3) is now part of the P0 core.

## 1. Executive summary

Moving actas import from whole-season historical loads to incremental loads, one current jornada at
a time, is **feasible**. Storing planned fixtures is also feasible, and it is what enables the
future season/calendar feature. The existing traversal, processor, and `ImportResource` machinery can
be reused.

Two current behaviours block the change:

1. **Skip-if-exists never upgrades a match.** Every match processor treats "a match with this natural
   key already exists" as "done, skip it". Once a fixture is stored while it is still empty, its
   played version is **never** written, and the result is lost.
2. **A stored match cannot say whether it was played.** `match_record` has no status. RFETM and FCTT
   already store empty actas as matches with no games and no winner. Every read path assumes a stored
   match was played: the `MatchOutcome` javadoc says "imports only ever create a Match for a report
   that was actually played". So these empty matches already surface as **DRAW** in tie-eligible
   competitions and inflate match counts.

Recommended approach, in priority order:

1. Classify each acta (for BCNESA, each fixture) as `PLAYED`, `PENDING`, or `PARTIAL`.
2. Introduce `MatchStatus { SCHEDULED, PLAYED }` in the domain and the schema. Backfill existing rows.
3. Make every outcome, statistic, and count read path consider only `PLAYED` matches. This fixes the
   phantom-draw bug and is a precondition for storing more scheduled rows.
4. Persist a pending acta as a `SCHEDULED` match: teams, round, date, and venue, with no lineups,
   games, results, or winner. When the played acta arrives, **upgrade the same match in place**
   (same UUID) to `PLAYED`, with its lineups, games, sets, and pairs written in one transaction.
5. Keep scheduled matches in step with the federation: update date, time, and venue on reschedule,
   and report fixtures that disappear from a newer snapshot.
6. Derive the jornada progress per competition/group (last complete and current jornada) from
   `match_record` statuses. This is now possible because pending fixtures are stored.
7. Formalise the ZIP contract. A season snapshot re-uploaded each jornada is the recommended default.
   An optional delta mode must not wipe earlier jornadas.

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

| Source | Layout | Unit of dispatch | Round comes from |
| --- | --- | --- | --- |
| RFETM | `<season>/<competition>/<day>/<sex>/acta*.json` | one acta = one match | payload `jornada`, falling back to the `<day>` folder |
| FCTT | `<season>/<competition>/<group>/jornada_<d>_partido_<m>.json` | one acta = one match | file name / context |
| BCNESA | one acta per matchday, several fixtures concatenated | fixture split by `BcnesaMatchdaySplitter` | context |

### 2.3 What already works in favour of incremental import

- **Re-import is idempotent.** `MatchRepository.findMatchByNaturalKey(competition, season, group,
  round, phase, homeTeamId, awayTeamId)` is backed by the unique constraint
  `uk_competition_season_group_round_teams`. The same natural key identifies a fixture whether it is
  scheduled or played, so it is the right key for an in-place upgrade.
- **Season scoping.** `traverseSeason` and `ImportResource` are per `(source, season)`.
- **Re-upload re-arms the resource.** A new upload of the same season moves a finished `ImportResource`
  back to `PENDING`.
- **Single-run guard.** `StartImportProcessCommandHandler` and `ImportRunRegistry` allow only one import
  at a time.
- **Teams and players.** Team processors (`@Order 10`) register the teams of a pending acta, which a
  scheduled match needs anyway. Player processors do nothing with empty lineups.
- **Cost.** Re-walking a whole season is cheap. RFETM 2025-2026 has about 4,000 files.

### 2.4 Gaps and defects found

| # | Finding | Location | Impact |
| --- | --- | --- | --- |
| G1 | **Skip-if-exists never upgrades a match.** An existing natural key returns early. | `RfetmMatchImportProcessor.process` (l.103), `FcttMatchImportProcessor.process` (l.105), `BcnesaMatchImportProcessor.process` (l.91) | Results of a fixture first seen as pending are lost. **Blocker.** |
| G2 | **No match lifecycle state.** `Match` / `match_record` cannot say whether a match was played. RFETM and FCTT already store empty actas as matches with null or 0-0 scores, no winner, and no games. | `Match`, `MatchJPA`, `buildMatch` in the processors | Scheduled and played matches cannot be told apart. **Blocker** for storing pending fixtures. |
| G3 | **Read paths assume "stored = played".** `MatchOutcome.teamOutcome` returns **DRAW** for any winner-less match in a tie-eligible competition. Match counts and lists include every row. | `MatchOutcome`; `FindMatchDetailsQueryHandler`, `FindPlayerDetailsQueryHandler`, `FindClubDetailsQueryHandler`, `FindFederatedClubDetailsQueryHandler`, `FindFederatedClubCompetitionDetailsQueryHandler`, `FindClubsByStringInNameQueryHandler`, `SearchMatchesQueryHandler`; `MatchRepositoryHelper` (`countBySeason`, `findAllSeasons`, `findAllBySource`, fragment search, `searchMatches` / `countMatches`); community statistics; REST and MCP match DTOs | Phantom draws, inflated counts, and wrong win rates as soon as scheduled rows exist. Some rows already exist. |
| G4 | **BCNESA drops empty actas.** `BcnesaMatchdaySplitter.split` returns no fixtures when `partidos` is empty, so a pending BCNESA acta is never dispatched, even though its `equipos` names the teams. The splitter and `BcnesaClubIndex` assume several fixtures per file. A scan of the local export (`C:\tt-repository\import-bcnesa\actas`, 16,387 files, 2020-2021 to 2025-2026) found **exactly one fixture per file** (`acta_<jornada>_page_<n>.json`), with `equipos` and `alineaciones` always present. So BCNESA can name every pending fixture from its own file, as RFETM and FCTT do. The export has **no** empty actas (every season is complete), so the shape of a pending BCNESA acta is still unverified. | `BcnesaMatchdaySplitter`, `BcnesaMatchImportProcessor` | The pending acta must bypass the splitter (or the splitter must yield one fixture named by `equipos` when `partidos` is empty). Confirm on a 2026-2027 sample (T0). |
| G5 | **No jornada tracking.** `ImportResource` has no jornada information, and `lastProcessedDate` is never set by `finishProcessing`. | `ImportResource`, `import_resource` | The requirement to "keep track of the last/current jornada imported" is not met. |
| G6 | **The upload wipes the season folder.** `loadIntoRepository` runs `deleteRecursively(seasonFolder)` before moving files. A ZIP holding only the current jornada deletes the earlier jornadas from disk. | `ResourceRepositoryLoaderService.loadIntoRepository` | Safe only when every ZIP is a full-season snapshot. |
| G7 | **No update or delete ports for match content.** `Lineup`, `Game`, `SetScore`, and `DoublesPair` repositories expose only `save*`. `Match` is immutable (builder with `createExisting`). | domain repository ports | An in-place upgrade from `SCHEDULED` to `PLAYED`, a reschedule, or a correction needs new ports. |
| G8 | **The natural key depends on `jornada` being stable.** RFETM takes the round from the payload and falls back to the day folder. If the planned and played versions disagree, they produce two keys: an orphan `SCHEDULED` match plus a separate `PLAYED` one. | `RfetmMatchImportProcessor.resolveRound` | Duplicate fixtures in the calendar. |
| G9 | **Reschedules and postponements.** A fixture of jornada *N* can move date or venue, or be played after jornada *N+1*. Nothing today updates a stored match's date or venue. | domain semantics | A stale calendar. A "max round" watermark is not a safe file filter. |
| G10 | **An all-pending run fails.** When nothing is dispatched, the status is `EMPTY_RESULT`, and the resource ends as `ERROR`. Once pending actas create scheduled matches this is less likely, but a no-change rerun must also count as success. | `NavigatorImportExecutionService.execute`, `StartImportProcessCommandHandler.runAsync` | Misleading status. |
| G11 | **Preview does not classify.** Preview validators do not report played, pending, or partial actas, or new versus upgraded matches. | `*PreviewValidationProcessor`, `ActaPreviewValidationSupport` | Operators cannot see what an incremental upload will change. |
| G12 | **The manifest is strict.** `validateManifest` requires exactly `source`, `seasons`, and `assets`. | `ResourceZipService.validateManifest` | Contract change needed for a delta mode. |
| G13 | **Schema migration relies on `ddl-auto: update`.** Hibernate can add a column, but a `NOT NULL` column without a default fails on a populated `match_record`, and Hibernate never backfills values. | runtime `application.yml` | The status column must be added with a database default and backfilled explicitly (T3). |

## 3. Requirements

Functional requirements:

- **R1. Every fixture is stored.** A `PENDING` acta creates (or keeps) a `Match` with
  `status = SCHEDULED`. It carries teams, competition, season, group, phase, round, date, time, and
  venue when present. It has no lineups, games, set scores, doubles pairs, results, or winner.
- **R2. Upgrade in place.** When a `PLAYED` acta arrives for a stored `SCHEDULED` match, the same
  match (same UUID) becomes `PLAYED`. Results, winner, referee, lineups, games, sets, and pairs are
  written in one transaction.
- **R3. Never downgrade.** A `PENDING` or `PARTIAL` acta for a stored `PLAYED` match changes nothing
  and is reported as an issue.
- **R4. Reschedules.** For a match still `SCHEDULED`, changes to date, time, venue, or city in a newer
  acta update the stored match.
- **R5. Statistics count only `PLAYED`.** Every outcome, win-rate, streak, count, and "matches played"
  read path excludes `SCHEDULED`. Match listings expose the status so that a calendar can show both.
- **R6. Idempotency.** Re-uploading the same snapshot changes nothing and succeeds.
- **R7. Jornada tracking.** Per `(source, season, competition, group, phase)`, provide the **last
  complete jornada** (every fixture `PLAYED`), the **current jornada** (the highest round with at
  least one `PLAYED` fixture), and scheduled and played counts. Expose them in the run result and the
  import read model.
- **R8. Upload safety.** An incremental upload never deletes previously received acta files unless
  it is declared a full snapshot.
- **R9. Source limits are explicit.** Where a source cannot identify a pending fixture's teams, the
  fixture is counted as unresolved and reported, not guessed. In the current exports every source
  names both teams in each file (G4), so this is a safety net.

Non-functional requirements:

- **R10.** Keep module boundaries: classification in `tt-data-league-import`, status and ports in the
  domain, and schema in the JPA module with `rfetm-datamodel.md` updated.
- **R11.** Keep every lookup source-scoped. Do not add external ids to `FederatedClub` or
  `FederatedPlayer`.
- **R12.** Club and player consolidation must re-point `SCHEDULED` matches exactly as it re-points
  `PLAYED` ones. It already works through `findAllMatchesByTeamIds`, which must **not** be filtered by
  status.

## 4. Proposed design

### 4.1 Acta completeness classifier (import module)

Add a value type `ActaCompleteness { PLAYED, PENDING, PARTIAL }` and a classifier. It is shared over
`Acta` and specialised per source where rules differ. For BCNESA it runs per fixture after splitting.

Draft rules, to be confirmed on real current-season samples (T0):

| Class | Rule (draft) |
| --- | --- |
| `PENDING` | `partidos` is empty, or no game has sets, a `ganador`, a `resultado_juegos`, or `no_disputado = true`. In addition, `resultado_final` is absent or 0-0 with no `ganador`. |
| `PLAYED` | `resultado_final` names a winner or has a non-zero games score, and every game is played or explicitly `no_disputado` (walkover and forfeit cases). |
| `PARTIAL` | Some games carry results and others are empty (an upload taken mid-match, or a data-entry lag). |

Treatment of `PARTIAL`: store or keep the match as `SCHEDULED`, write no children, and raise an
issue. It is upgraded when a later snapshot brings it complete. This keeps `PLAYED` meaning "the
result is final".

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
  "Postponed" and "overdue" (scheduled with a date in the past) are **derived** in the read model,
  not stored. The acta gives no reliable postponement signal. The enum can grow later if the calendar
  feature needs manual states.
- JPA: `match_record.status VARCHAR(20) NOT NULL DEFAULT 'PLAYED'` with `@Enumerated(STRING)`. The
  database default keeps `ddl-auto: update` working on existing data (G13).
- Backfill (T3): set `status = 'SCHEDULED'` where the match has no `game` rows, no `winner_team_id`,
  and null or 0-0 games won. The backfill has report and write modes. It lives in the runtime
  (opt-in), following the consolidation conventions.
- `rfetm-datamodel.md` documents the column, the default, the backfill rule, and the invariant
  "`SCHEDULED` ⇒ no lineups, games, set scores, doubles pairs, or winner".

### 4.3 Match write path (processors)

Processor algorithm, shared shape for RFETM, FCTT, and BCNESA:

```text
classify acta/fixture
resolve teams (unchanged)
existing = findMatchByNaturalKey(...)
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

The current RFETM `buildMatch` / `buildLineups` / `storeGames` code is reused for the `PLAYED`
branch. The `SCHEDULED` branch builds only the header fields.

### 4.4 Domain ports for upgrade and reschedule

- `MatchRepository.replaceMatchContent(Match match, List<Lineup>, List<Game>, List<SetScore>,
  List<DoublesPair>)`: one transaction. It deletes the existing children of `match.getId()`, updates
  the header, and inserts the new children. The id is preserved, so external references stay valid.
- `MatchRepository.updateSchedule(UUID matchId, ZonedDateTime, String city, String venue)`, or reuse
  `saveMatch` with `createExisting` if its JPA mapping merges by id. Verify this in T4.
- `InMemoryRepositories` in the import tests implement both.
- Read-side ports gain status-aware variants, or `MatchSearchCriteria` gains a `statuses` filter (see
  4.5).

### 4.5 Read-side changes

| Consumer | Change |
| --- | --- |
| `MatchOutcome.teamOutcome` / `playerOutcome` | Return empty for `SCHEDULED`. Update the javadoc invariant to "a winner-less **PLAYED** match is a tie". |
| `FindMatchDetailsQueryHandler`, `FindPlayerDetailsQueryHandler`, club detail and competition handlers, `FindClubsByStringInNameQueryHandler` | Compute stats, win rates, form, and streaks over `PLAYED` only. Lists may include `SCHEDULED` with status shown, depending on the screen. |
| `MatchRepositoryHelper.countBySeason`, `countAllMatches`, `findAllSeasons` | Count or consider `PLAYED` only, since "matches played" and "current season" are community statistics. |
| `searchMatches` / `countMatches`, fragment search | Add a status filter defaulting to `PLAYED`, so current API behaviour is unchanged. The calendar feature passes `SCHEDULED`. |
| REST `MatchDto` / `MatchDetailDto`, MCP `MatchDto` / `MatchDetailDto` | Add a `status` field. This is additive and non-breaking. |
| Consolidation processors (`findAllMatchesByTeamIds*`) | **No filter.** They must see and re-point scheduled matches too (R12). |

Doing this before any new scheduled rows are written also fixes the draws already being reported
today (G3).

### 4.6 Jornada progress

Since every fixture is now stored, progress can be **derived from `match_record`**, which becomes the
single source of truth:

```sql
-- per (source, season, competition, group_num, phase)
current_round       = max(round) where status = 'PLAYED'
last_complete_round = max(r) such that no match with round <= r has status = 'SCHEDULED'
scheduled / played  = counts by status
```

- Expose it through a domain query (`MatchRepository.findRoundProgress(source, season)`), the import
  run result, and the import-resource read model.
- A cache table (`import_round_progress`) is **optional**. Add it only if the query proves slow; it
  is not needed for correctness.
- The progress is informational. It is **never** used to skip files (G9). Traversal stays
  full-season and relies on natural-key idempotency.
- Set `ImportResource.lastProcessedDate` when a run finishes.

### 4.7 Upload contract

- **Snapshot mode (default, recommended).** Each ZIP holds the full season as currently published,
  with played and pending actas. The existing delete-and-replace of the season folder is correct for
  this mode. Document it, and reject a snapshot that shrinks against the stored folder unless an
  override is given, to catch truncated uploads.
- **Delta mode (optional).** Add an optional manifest `"mode": "snapshot" | "delta"` (G12). In delta
  mode, merge the files into the season folder without deleting, and keep a rollback copy.
- In snapshot mode, a stored `SCHEDULED` match whose fixture no longer appears is **reported**, never
  deleted automatically (T10).

### 4.8 Run status and metrics

- Add `scheduledCreated`, `upgradedToPlayed`, `rescheduled`, `partialActas`, and
  `unresolvedPendingFixtures` counters to the traversal summaries, `ImportExecutionMetrics`, and
  `ImportProcessResult`.
- "No changes" is `SUCCESS` (or a new `NO_CHANGES` mapped to `PROCESSED`). `EMPTY_RESULT` stays for
  "no actas found at all".

## 5. Prioritised implementation plan

Priority: **P0** is a blocker or data-correctness issue, **P1** is required for the feature, **P2**
is important hardening, and **P3** is follow-up work.

| ID | Pri | Task | Modules | Depends on | Size |
| --- | --- | --- | --- | --- | --- |
| T0 | P0 | **Sample and specify actas.** Collect current-season (2026-2027) RFETM, FCTT, and BCNESA actas that are pending, partial, played, walkover, and rescheduled. Record their payload shape (`partidos`, `resultado_final`, `jornada`, `fecha`, `lugar`, `equipos`). Add anonymised JUnit fixtures. Confirm what an empty BCNESA matchday contains (G4). | import (test resources) | — | S |
| T1 | P0 | **Acta completeness classifier.** `ActaCompleteness` plus a per-source classifier (per fixture for BCNESA), with tests. | import | T0 | S |
| T2 | P0 | **`MatchStatus` in domain and JPA.** Enum, `Match` field and builder, `MatchJPA` column `NOT NULL DEFAULT 'PLAYED'`, mappers, and in-memory repositories. Update `rfetm-datamodel.md`. Add JPA tests for the default and the invariant. | domain, JPA, import (tests) | — | S |
| T3 | P0 | **Backfill legacy empty matches to `SCHEDULED`.** Opt-in runtime command with report and write modes, scoped by source and season. It reports the counts to review before writing. | JPA or import, runtime, README | T2 | S |
| T4 | P0 | **Read-side status filtering.** `MatchOutcome`, every query handler in 4.5, the repository counts and seasons, a search status filter defaulting to `PLAYED`, and `status` in the REST and MCP DTOs. Add tests for each handler with a mixed `SCHEDULED`/`PLAYED` fixture. **Ship with or before T5.** | domain (application), JPA, api-rest, api-mcp | T2 | M |
| T5 | P0 | **Upgrade and reschedule ports.** `replaceMatchContent` (transactional) and `updateSchedule`, with JPA and in-memory implementations and rollback tests. | domain, JPA, import (tests) | T2 | M |
| T6 | P0 | **Processor lifecycle.** Implement 4.3 in the RFETM, FCTT, and BCNESA match processors: create `SCHEDULED`, upgrade to `PLAYED`, reschedule, and report regressions. BCNESA stores what it can resolve and counts the rest (R9). | import | T1, T5 | M |
| T7 | P0 | **Run status and metrics.** Add the new counters. No-change runs succeed. Set `lastProcessedDate`. | import, domain, runtime | T6 | S |
| T8 | P1 | **Jornada progress query and exposure.** `findRoundProgress`, run result, import-resource read model, CLI summary, and README. | domain, JPA, api-rest, runtime | T2, T6 | S |
| T9 | P1 | **Natural-key stability guard (G8).** Detect a payload `jornada` that disagrees with the folder or is missing. Before creating a match, look for the same teams, competition, and season in another round, and raise an issue instead of creating a duplicate. | import | T6 | S |
| T10 | P1 | **Snapshot reconciliation.** After a snapshot run, report stored `SCHEDULED` matches of that season whose fixture was not seen (federation removed or reassigned it). Report only. | import, runtime | T6 | S |
| T11 | P1 | **Upload contract.** Document snapshot mode; add the shrink check. | domain (`ResourceRepositoryLoaderService`, `ResourceZipService`), README | — | S |
| T12 | P1 | **Preview classification.** Preview reports new scheduled, upgrades, reschedules, regressions, partial actas, and unresolved fixtures per competition/group, plus the resulting jornada progress. | import (preview processors) | T1, T8 | S |
| T13 | P2 | **Amended-acta detection.** Add a `match_record.source_checksum` column. When a `PLAYED` acta's checksum changes, re-apply it via `replaceMatchContent` and log an audit line. Opt-in at first. | domain, JPA, import | T5 | M |
| T14 | P2 | **Delta upload mode.** Optional manifest `mode`; merge without deleting; keep a rollback copy. | domain, api-rest, README | T11 | M |
| T15 | P2 | **BCNESA splitter cleanup.** The local export has one fixture per file, so the multi-fixture split and the licence-based club index are only exercised for legacy-shaped files. Measure whether any file still needs them. If none does, simplify; otherwise keep them. (The javadocs were re-measured against the local export on 2026-09-26.) | import | T0, T6 | S |
| T16 | P3 | **Season calendar / matchday management feature.** API and UI over `SCHEDULED` and `PLAYED` matches, derived "overdue" and "postponed" labels, and optional manual states. This is the separate future feature, enabled by T2 to T8. | api, UI | T4, T8 | L |
| T17 | P3 | **Automation.** Scheduled fetch and upload per jornada. | runtime / ops | T11 | M |

### 5.1 Delivery slices

1. **Slice 1: lifecycle foundation (T0 to T4).** Status exists, legacy empty matches are marked
   `SCHEDULED`, and every statistic ignores them. This fixes the draw and count bug that exists today.
   No import behaviour changes yet.
2. **Slice 2: incremental import (T5 to T7).** Pending actas become scheduled matches, played actas
   upgrade them in place, reschedules update them, and run statuses are correct.
3. **Slice 3: visibility and safety (T8 to T12).** Jornada progress, duplicate guard, snapshot
   reconciliation, upload checks, and preview.
4. **Slice 4: hardening (T13 to T15).**
5. **Slice 5: the calendar feature itself (T16) and automation (T17).**

Dependency graph:

```text
T0 → T1 ─────────────┐
T2 → T3              ├→ T6 → T7
T2 → T4 (with T6)    │    ├→ T8 → T12
T2 → T5 ─────────────┘    ├→ T9
                          ├→ T10
                          └→ T15
T5 → T13
T11 → T14
T4, T8 → T16
```

## 6. Changes to data structures, validation, and error handling

| Area | Change |
| --- | --- |
| Parser records | No change to `Acta` and `ActaGame`. Classification lives in a new classifier. |
| Domain | `MatchStatus` plus a `Match.status` field. `replaceMatchContent` and `updateSchedule` ports. `findRoundProgress` query. A status filter on `MatchSearchCriteria`. `MatchOutcome` becomes status-aware. New counters on `ImportProcessResult` / `ImportExecutionMetrics`. `ImportResource.lastProcessedDate` set on finish. |
| Schema | `match_record.status VARCHAR(20) NOT NULL DEFAULT 'PLAYED'`, with an index on `(source, season, competition, status)` for progress and calendar queries. Optional `match_record.source_checksum` (T13). Optional `import_round_progress` cache. All documented in `rfetm-datamodel.md`. |
| Invariants | `SCHEDULED` ⇒ no lineups, games, set scores, doubles pairs, or winner, and null results. `PLAYED` is never downgraded by import. |
| API contract | Additive `status` field in the REST and MCP match DTOs. Default search behaviour unchanged (`PLAYED`). |
| Manifest | Optional `mode` (T14 only). Unknown keys are still rejected. |
| Validation | Classifier results; natural-key stability check (T9); snapshot shrink check (T11); snapshot reconciliation (T10). |
| Error handling | Pending is not an error. Partial and regression cases are issues, with no write. Upgrade failures are processor failures, isolated per processor as today, and the transactional port leaves no half-written match. No broad catches. |

## 7. Risk assessment

| # | Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- | --- |
| K1 | A read path misses the status filter, so scheduled matches leak into statistics (draws, counts, win rates). | **High** (many consumers, see G3) | High | T4 enumerates every consumer; a mixed-status test per handler; `MatchOutcome` returns empty for `SCHEDULED` as a central safety net; search defaults to `PLAYED`. Ship T4 before T6. |
| K2 | The classifier misreads played as pending, or the reverse (walkovers, forfeits, 0-0). | Medium | High | T0 real samples; explicit `no_disputado` rule; preview counts (T12) reviewed before the first production run. |
| K3 | A half-applied upgrade (children deleted, new ones not written). | Low | High | A single transactional `replaceMatchContent`; JPA rollback tests. |
| K4 | `jornada` drift between the planned and played acta creates a duplicate fixture (G8). | Low to medium | High for the calendar | T9 guard; T10 reconciliation surfaces orphaned scheduled matches. |
| K5 | The backfill misclassifies a legacy played match with lost games as `SCHEDULED`. | Low | Medium | Report mode first. The rule also requires no winner and null or 0-0 results. Review the counts per source and season before writing. |
| K6 | The schema change fails on a populated database under `ddl-auto: update` (G13). | Medium if done naively | High (startup failure) | Use a database default in `columnDefinition`; test against a copy of production data; document it in the README. |
| K7 | Pending BCNESA actas have a different shape than assumed (for example no `equipos`, or several fixtures per file as the splitter javadoc describes for an older export). | Low (the local export is one fixture per file) | Medium | T0 confirms on a 2026-2027 sample; the splitter path stays for multi-fixture files; R9 reports anything unresolved. |
| K8 | The federation removes or reassigns fixtures, leaving stale `SCHEDULED` rows. | Medium | Medium | T10 reports them; deletion stays a manual, explicit decision. |
| K9 | Consolidation must now also handle scheduled matches. | Low | Medium | It already works over all team matches (R12); add a consolidation test with a scheduled match. |
| K10 | A truncated snapshot ZIP wipes good files from disk (G6). | Medium | Medium | T11 shrink check; T14 rollback copy; archive uploaded ZIPs operationally. |
| K11 | Team-name drift mid-season: RFETM 2025+ has no team ids, so a rename splits registrations. A scheduled match then points to a stale team. | Low to medium | Medium | Existing `RfetmClubKey` rule; opt-in consolidation; preview warns about new team names in an in-progress season. |
| K12 | Operators see different run statuses (`ERROR` becomes `PROCESSED` for no-change runs). | Certain | Low | README and release notes; explicit counters in the DTOs. |

## 8. Open questions for stakeholders

1. **Upload shape.** Will each upload be a full-season snapshot (recommended) or only the current
   jornada? This decides whether T14 is needed.
2. **Pending acta samples.** Can we get one 2026-2027 export per source that contains pending
   (unplayed) actas? No local export contains any, so the classifier rules and the BCNESA pending
   shape are still assumptions.
3. **Corrections.** Do federations amend already-played actas often enough to bring T13 forward?
4. **Stored states.** Is `SCHEDULED` / `PLAYED`, with derived "overdue" and "postponed", enough for
   the planned season-management feature? Or will it need manually managed states (for example
   `CANCELLED`) that the import must not override?
5. **Vanished fixtures.** Should a scheduled match that disappears from a later snapshot eventually be
   deleted, and on whose confirmation?

## 9. Feasibility verdict

**Feasible with moderate effort.** Persisting pending fixtures is the right foundation for the future
season and matchday feature. The existing natural key already identifies a fixture across its
lifecycle, so an in-place `SCHEDULED` → `PLAYED` upgrade fits the current model.

The effort is concentrated in three places:

- the status model and backfill (small);
- the read-side filtering across roughly ten consumers (medium, and the main risk);
- the transactional upgrade path in the processors (medium).

Slice 1 is worth shipping on its own: it fixes the phantom draws that historical imports have already
caused.
