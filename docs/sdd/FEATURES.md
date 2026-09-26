 dev# FEATURES.md — Feature Registry & Build Plans

This file is the single source of truth for planned, in-progress, and completed features.

**For humans:** Add new features under `## Backlog` using the template in [`task-management.md`](./task-management.md).
**For agents:** Only work on features marked `status: ready`. Update status as you progress. Never modify features marked `status: done` or `status: in-progress` unless explicitly asked.

---

## Status Legend

| Status | Meaning |
|-|-|
| `idea` | Captured but not planned yet — no build plan written |
| `planned` | Build plan written, not yet ready to implement |
| `ready` | Build plan approved, agent can start |
| `in-progress` | Currently being implemented |
| `in-review` | Implementation finalized and awaiting user review |
| `done` | Shipped after explicit user approval |
| `blocked` | Waiting on a dependency or decision |

---

## Main index

- [FEAT-00074: FCTT import resource format modified](### [FEAT-00074] FCTT import resource format modified)
- [FEAT-00075: Sample and specify pending, partial, and played actas](### [FEAT-00075] Sample and specify pending, partial, and played actas)
- [FEAT-00076: Acta completeness classifier](### [FEAT-00076] Acta completeness classifier)
- [FEAT-00077: MatchStatus in the domain and JPA model](### [FEAT-00077] MatchStatus in the domain and JPA model)
- [FEAT-00078: Backfill legacy empty matches to SCHEDULED](### [FEAT-00078] Backfill legacy empty matches to SCHEDULED)
- [FEAT-00079: Read-side MatchStatus filtering](### [FEAT-00079] Read-side MatchStatus filtering)
- [FEAT-00080: Match upgrade and reschedule repository ports](### [FEAT-00080] Match upgrade and reschedule repository ports)
- [FEAT-00081: Match processor lifecycle for scheduled and played actas](### [FEAT-00081] Match processor lifecycle for scheduled and played actas)
- [FEAT-00082: Incremental import run status and metrics](### [FEAT-00082] Incremental import run status and metrics)
- [FEAT-00083: Jornada progress query and exposure](### [FEAT-00083] Jornada progress query and exposure)
- [FEAT-00084: Natural-key stability guard for fixtures](### [FEAT-00084] Natural-key stability guard for fixtures)
- [FEAT-00085: Snapshot reconciliation of vanished scheduled fixtures](### [FEAT-00085] Snapshot reconciliation of vanished scheduled fixtures)
- [FEAT-00086: Snapshot upload contract and shrink check](### [FEAT-00086] Snapshot upload contract and shrink check)
- [FEAT-00087: Preview classification for incremental uploads](### [FEAT-00087] Preview classification for incremental uploads)
- [FEAT-00088: Persist the source fixture id on matches](### [FEAT-00088] Persist the source fixture id on matches)
- [FEAT-00089: Amended-acta detection by checksum](### [FEAT-00089] Amended-acta detection by checksum)
- [FEAT-00090: Delta upload mode for actas ZIPs](### [FEAT-00090] Delta upload mode for actas ZIPs)
- [FEAT-00091: BCNESA splitter cleanup](### [FEAT-00091] BCNESA splitter cleanup)
- [FEAT-00092: Season calendar and matchday management](### [FEAT-00092] Season calendar and matchday management)
- [FEAT-00093: Automated per-jornada actas fetch and upload](### [FEAT-00093] Automated per-jornada actas fetch and upload)

## In Progress

No features currently in progress.
## In Review

No features currently in review.
## Backlog

### [FEAT-00075] Sample and specify pending, partial, and played actas
- **Status:** idea
- **Priority:** high
- **Effort:** small
- **Depends on:** —

#### Goal
Real 2026-2027 RFETM, FCTT, and BCNESA acta samples pin down how pending, partial, played, walkover, and rescheduled actas look, so the completeness classifier is built on evidence rather than assumptions.

#### Acceptance Criteria
- [ ] Current-season (2026-2027) RFETM, FCTT, and BCNESA actas are collected for the pending, partial, played, walkover, and rescheduled cases where the source provides them, and the gaps are recorded.
- [ ] The payload shape of each case (`partidos`, `resultado_final`, `jornada`, `fecha`, `lugar`, `equipos`) is recorded in this feature's notes.
- [ ] Anonymised JUnit fixtures are added under the import module test resources, including an FCTT all-null 2026-2027 placeholder acta and an FCTT published/unpublished pair sharing the same `id_partido`.
- [ ] The content of an empty (pending) BCNESA matchday acta is confirmed, or its absence is documented as an open question (G4, K7).

#### Feature Details
→ See [FEAT-00075-DETAILS.md](./FEAT-00075-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00076] Acta completeness classifier
- **Status:** idea
- **Priority:** high
- **Effort:** small
- **Depends on:** FEAT-00075

#### Goal
Every acta (every fixture for BCNESA) is classified as PLAYED, PENDING, or PARTIAL by explicit, ordered rules, so placeholder results of unpublished actas can never be read as real results.

#### Acceptance Criteria
- [ ] An `ActaCompleteness { PLAYED, PENDING, PARTIAL }` value type and a per-source classifier exist in `tt-data-league-import` (per fixture for BCNESA, after splitting).
- [ ] An acta with `acta_publicada: false` is always PENDING, and its `resultado_final`, `abc_es_local`, `partidos`, and `alineaciones` are not inspected.
- [ ] An acta with `acta_publicada: true` that breaks the published-acta schema rules (no games, or null `abc_es_local`) is reported as an issue and not classified as pending.
- [ ] Content rules (PENDING / PLAYED / PARTIAL, including the `no_disputado` walkover case) apply only when `acta_publicada` is missing, and never promote an acta to PLAYED on `resultado_final` alone.
- [ ] JUnit tests cover both FCTT placeholder shapes (home win 6-0 and all nulls) and assert they classify as PENDING.

#### Feature Details
→ See [FEAT-00076-DETAILS.md](./FEAT-00076-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00077] MatchStatus in the domain and JPA model
- **Status:** idea
- **Priority:** high
- **Effort:** small
- **Depends on:** —

#### Goal
A stored match states whether it is SCHEDULED or PLAYED, so scheduled fixtures and played matches can be told apart in every layer.

#### Acceptance Criteria
- [ ] `MatchStatus { SCHEDULED, PLAYED }` exists in `tt-data-league-core-domain` as a `Match` field and builder property.
- [ ] `MatchJPA` maps `match_record.status VARCHAR(20) NOT NULL DEFAULT 'PLAYED'` with `@Enumerated(STRING)`, so `ddl-auto: update` works on a populated database.
- [ ] Domain/JPA mappers and the import-module in-memory repositories carry the status.
- [ ] JPA tests cover the column default and the invariant "SCHEDULED implies no lineups, games, set scores, doubles pairs, or winner".
- [ ] `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` documents the column, its default, and the invariant.

#### Feature Details
→ See [FEAT-00077-DETAILS.md](./FEAT-00077-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00078] Backfill legacy empty matches to SCHEDULED
- **Status:** idea
- **Priority:** high
- **Effort:** small
- **Depends on:** FEAT-00077

#### Goal
Empty matches stored by earlier historical imports are marked SCHEDULED, so they stop surfacing as phantom draws and inflated match counts.

#### Acceptance Criteria
- [ ] An opt-in runtime command marks as SCHEDULED the matches with no `game` rows, no `winner_team_id`, and null or 0-0 games won.
- [ ] The command supports report and write modes; report mode runs the same analysis without persistence writes and prints the counts to review.
- [ ] The command is scoped by source and season and fails clearly on missing or invalid arguments.
- [ ] The runtime README documents the command, its modes, and the backfill rule; `rfetm-datamodel.md` documents the rule.

#### Feature Details
→ See [FEAT-00078-DETAILS.md](./FEAT-00078-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00079] Read-side MatchStatus filtering
- **Status:** idea
- **Priority:** high
- **Effort:** medium
- **Depends on:** FEAT-00077

#### Goal
Every outcome, statistic, and count read path considers only PLAYED matches, fixing today's phantom draws and making it safe to store scheduled fixtures.

#### Acceptance Criteria
- [ ] `MatchOutcome.teamOutcome` / `playerOutcome` return empty for SCHEDULED matches, and the javadoc invariant reads "a winner-less PLAYED match is a tie".
- [ ] Match, player, club, federated-club, federated-club-competition, and club-name-search query handlers compute stats, win rates, form, and streaks over PLAYED matches only.
- [ ] `MatchRepositoryHelper` counts and season listings (`countBySeason`, `countAllMatches`, `findAllSeasons`) consider PLAYED only.
- [ ] Match search and fragment search accept a status filter that defaults to PLAYED, so current API behaviour is unchanged.
- [ ] REST and MCP `MatchDto` / `MatchDetailDto` expose an additive `status` field.
- [ ] Consolidation lookups (`findAllMatchesByTeamIds*`) are not filtered by status.
- [ ] Each affected handler has a test with a mixed SCHEDULED/PLAYED fixture.

#### Feature Details
→ See [FEAT-00079-DETAILS.md](./FEAT-00079-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00080] Match upgrade and reschedule repository ports
- **Status:** idea
- **Priority:** high
- **Effort:** medium
- **Depends on:** FEAT-00077

#### Goal
The domain exposes transactional ports to upgrade a scheduled match in place and to update its schedule, so the import can move a fixture from SCHEDULED to PLAYED without losing its identity.

#### Acceptance Criteria
- [ ] `MatchRepository.replaceMatchContent(Match, List<Lineup>, List<Game>, List<SetScore>, List<DoublesPair>)` deletes the match's existing children, updates the header, and inserts the new children in one transaction, preserving the match UUID.
- [ ] `MatchRepository.updateSchedule(UUID, ZonedDateTime, String city, String venue)` exists, or reuse of `saveMatch` with `createExisting` is verified to merge by id and documented.
- [ ] JPA and import-module in-memory implementations exist for both.
- [ ] JPA rollback tests prove a failed upgrade leaves no half-written match.

#### Feature Details
→ See [FEAT-00080-DETAILS.md](./FEAT-00080-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00081] Match processor lifecycle for scheduled and played actas
- **Status:** idea
- **Priority:** high
- **Effort:** medium
- **Depends on:** FEAT-00076, FEAT-00079, FEAT-00080

#### Goal
The RFETM, FCTT, and BCNESA match processors store pending actas as SCHEDULED matches, upgrade them in place when their results arrive, apply reschedules, and never downgrade a played match.

#### Acceptance Criteria
- [ ] With no existing match, a PENDING or PARTIAL acta creates a SCHEDULED match with header fields only (teams, competition, season, group, phase, round, date, time, venue) and never copies `resultado_final`, winner, or referee data.
- [ ] With no existing match, a PLAYED acta keeps today's behaviour.
- [ ] An existing SCHEDULED match is upgraded in place (same UUID) to PLAYED through `replaceMatchContent` when a PLAYED acta arrives.
- [ ] An existing SCHEDULED match has its date, time, venue, and city updated when a newer PENDING or PARTIAL acta changes them.
- [ ] A PENDING or PARTIAL acta for an existing PLAYED match changes nothing and is reported as an issue; an unchanged PLAYED acta is skipped.
- [ ] BCNESA dispatches pending fixtures named by `equipos` (bypassing or extending `BcnesaMatchdaySplitter`), and fixtures whose teams cannot be resolved are counted and reported, not guessed (R9).
- [ ] The FEAT-00074 early return for unpublished FCTT actas is replaced by the SCHEDULED branch in the same change, and the FCTT preview message reports a scheduled fixture.
- [ ] Re-importing the same snapshot changes nothing and succeeds (R6); tests cover create, upgrade, reschedule, and regression for each source.

#### Feature Details
→ See [FEAT-00081-DETAILS.md](./FEAT-00081-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00082] Incremental import run status and metrics
- **Status:** idea
- **Priority:** high
- **Effort:** small
- **Depends on:** FEAT-00081

#### Goal
Import runs report what an incremental load changed, and a run with no changes succeeds instead of ending in ERROR.

#### Acceptance Criteria
- [ ] `scheduledCreated`, `upgradedToPlayed`, `rescheduled`, `partialActas`, and `unresolvedPendingFixtures` counters are added to the traversal summaries, `ImportExecutionMetrics`, and `ImportProcessResult`.
- [ ] A run that finds actas but changes nothing ends as SUCCESS (or a new NO_CHANGES mapped to PROCESSED); `EMPTY_RESULT` is kept for "no actas found at all".
- [ ] `ImportResource.lastProcessedDate` is set when a run finishes.
- [ ] The runtime README documents the new counters and the changed status of no-change runs.

#### Feature Details
→ See [FEAT-00082-DETAILS.md](./FEAT-00082-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00083] Jornada progress query and exposure
- **Status:** idea
- **Priority:** medium
- **Effort:** small
- **Depends on:** FEAT-00077, FEAT-00081

#### Goal
Operators can see, per competition, group, and phase, the last complete jornada, the current jornada, and scheduled and played counts, derived from stored match statuses.

#### Acceptance Criteria
- [ ] `MatchRepository.findRoundProgress(source, season)` returns, per `(competition, group, phase)`, the last complete round, the current round (highest round with a PLAYED fixture), and scheduled and played counts.
- [ ] The progress is exposed in the import run result, the import-resource read model (REST), and the CLI summary.
- [ ] Progress is informational only and never used to skip files.
- [ ] The runtime README documents the progress output.

#### Feature Details
→ See [FEAT-00083-DETAILS.md](./FEAT-00083-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00084] Natural-key stability guard for fixtures
- **Status:** idea
- **Priority:** medium
- **Effort:** small
- **Depends on:** FEAT-00081

#### Goal
A payload `jornada` that drifts between the planned and played versions of a fixture raises an issue instead of creating a duplicate match.

#### Acceptance Criteria
- [ ] A payload `jornada` that is missing or disagrees with the RFETM day folder is detected and reported.
- [ ] Before creating a match, the processor looks for the same teams, competition, and season in another round and raises an issue instead of creating a duplicate.
- [ ] Once the source fixture id is persisted (FCTT `id_partido`), a stored match with the same fixture id but a different natural key is treated as the duplicate signal.
- [ ] Tests cover the drift and the duplicate-prevention cases.

#### Feature Details
→ See [FEAT-00084-DETAILS.md](./FEAT-00084-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00085] Snapshot reconciliation of vanished scheduled fixtures
- **Status:** idea
- **Priority:** medium
- **Effort:** small
- **Depends on:** FEAT-00081

#### Goal
After a snapshot run, stored SCHEDULED matches whose fixture no longer appears in the federation export are reported, so stale calendar entries are visible.

#### Acceptance Criteria
- [ ] After a snapshot run, stored SCHEDULED matches of that source and season whose fixture was not seen are reported in the run result and logs.
- [ ] Fixtures are matched as "seen" by natural key (or by `id_partido` for FCTT once persisted), never by file name.
- [ ] Nothing is deleted automatically.
- [ ] Tests cover a removed fixture and a fixture whose file name changed on publication.

#### Feature Details
→ See [FEAT-00085-DETAILS.md](./FEAT-00085-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00086] Snapshot upload contract and shrink check
- **Status:** idea
- **Priority:** medium
- **Effort:** small
- **Depends on:** —

#### Goal
The actas upload contract is a documented full-season snapshot, and a truncated snapshot cannot silently wipe previously received acta files.

#### Acceptance Criteria
- [ ] Snapshot mode (each ZIP holds the full season as currently published) is documented in the relevant README.
- [ ] `ResourceRepositoryLoaderService` rejects a snapshot that has fewer acta files than the stored season folder unless an explicit override is given.
- [ ] The rejection fails clearly and leaves the stored season folder untouched.
- [ ] Tests cover the shrink rejection and the override.

#### Feature Details
→ See [FEAT-00086-DETAILS.md](./FEAT-00086-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00087] Preview classification for incremental uploads
- **Status:** idea
- **Priority:** medium
- **Effort:** small
- **Depends on:** FEAT-00076, FEAT-00083

#### Goal
The import preview shows what an incremental upload will change before it is imported.

#### Acceptance Criteria
- [ ] Preview reports, per competition and group, new scheduled fixtures, upgrades, reschedules, regressions, partial actas, and unresolved fixtures.
- [ ] Preview reports the resulting jornada progress.
- [ ] Preview flags two files that carry the same `id_partido` in one snapshot.
- [ ] Tests cover the preview counters for each source.

#### Feature Details
→ See [FEAT-00087-DETAILS.md](./FEAT-00087-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00088] Persist the source fixture id on matches
- **Status:** idea
- **Priority:** medium
- **Effort:** small
- **Depends on:** FEAT-00077, FEAT-00081

#### Goal
Matches store the source-supplied fixture id (FCTT `id_partido`), giving a stable key across the unpublished and published versions of a fixture.

#### Acceptance Criteria
- [ ] A nullable `match_record.source_fixture_id VARCHAR(100)` with a unique constraint on `(source, source_fixture_id)` is added, with a `Match` field, mappers, and in-memory support.
- [ ] FCTT fills it from `id_partido` on create and on upgrade; other sources leave it null until they send one.
- [ ] `external_id` is not reused.
- [ ] `rfetm-datamodel.md` documents the column and constraint; tests cover create, upgrade, and uniqueness.

#### Feature Details
→ See [FEAT-00088-DETAILS.md](./FEAT-00088-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00089] Amended-acta detection by checksum
- **Status:** idea
- **Priority:** low
- **Effort:** medium
- **Depends on:** FEAT-00080

#### Goal
A federation correction to an already played acta is detected and re-applied instead of being silently ignored.

#### Acceptance Criteria
- [ ] A `match_record.source_checksum` column stores the checksum of the acta that produced a PLAYED match.
- [ ] When a PLAYED acta's checksum changes, the match is re-applied through `replaceMatchContent` and an audit line is logged.
- [ ] The behaviour is opt-in at first.
- [ ] `rfetm-datamodel.md` documents the column; tests cover unchanged, amended, and opt-out cases.

#### Feature Details
→ See [FEAT-00089-DETAILS.md](./FEAT-00089-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00090] Delta upload mode for actas ZIPs
- **Status:** idea
- **Priority:** low
- **Effort:** medium
- **Depends on:** FEAT-00086

#### Goal
An operator can upload only the current jornada without wiping earlier jornadas from the import folder.

#### Acceptance Criteria
- [ ] The manifest accepts an optional `"mode": "snapshot" | "delta"`; unknown keys are still rejected.
- [ ] In delta mode, files are merged into the season folder without deleting, and a rollback copy is kept.
- [ ] Snapshot remains the default when `mode` is absent.
- [ ] README documents the mode; tests cover both modes and the rollback copy.

#### Feature Details
→ See [FEAT-00090-DETAILS.md](./FEAT-00090-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00091] BCNESA splitter cleanup
- **Status:** idea
- **Priority:** low
- **Effort:** small
- **Depends on:** FEAT-00075, FEAT-00081

#### Goal
The BCNESA multi-fixture splitter and licence-based club index are kept only if real files still need them.

#### Acceptance Criteria
- [ ] The BCNESA export is measured for files that still contain several fixtures, and the result is recorded in the notes.
- [ ] If no file needs them, the multi-fixture split and licence-based club index are simplified; otherwise they are kept and the reason is documented.
- [ ] Existing BCNESA import tests pass unchanged in behaviour.

#### Feature Details
→ See [FEAT-00091-DETAILS.md](./FEAT-00091-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00092] Season calendar and matchday management
- **Status:** idea
- **Priority:** low
- **Effort:** large
- **Depends on:** FEAT-00079, FEAT-00083

#### Goal
Users can browse and manage the whole season calendar, including upcoming matchdays, over SCHEDULED and PLAYED matches.

#### Acceptance Criteria
- [ ] An API (and UI) lists a season's matchdays with SCHEDULED and PLAYED matches and their status.
- [ ] Derived "overdue" and "postponed" labels are shown without being stored.
- [ ] Any manual states (for example CANCELLED) are decided and, if added, are never overridden by the import.

#### Feature Details
→ See [FEAT-00092-DETAILS.md](./FEAT-00092-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00093] Automated per-jornada actas fetch and upload
- **Status:** idea
- **Priority:** low
- **Effort:** medium
- **Depends on:** FEAT-00086

#### Goal
The current-season actas are fetched and uploaded automatically each jornada without manual operator steps.

#### Acceptance Criteria
- [ ] A scheduled job fetches the current-season actas per source and uploads them as a snapshot ZIP.
- [ ] Failures are reported clearly and never fall back to another source, season, or mode.
- [ ] Operational configuration is environment-driven and documented in the runtime README; no credentials are committed.

#### Feature Details
→ See [FEAT-00093-DETAILS.md](./FEAT-00093-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---
## Done

### [FEAT-00074] FCTT import resource format modified
- **Status:** done
- **Priority:** medium
- **Effort:** large (> 8h)
- **Depends on:** —

#### Goal
Female leagues are included in the FCTT import process, and the import process should be updated to handle these changes.

#### Description
The FCCT ZIP resource file structure has been modified to include female leagues. 
The import process needs to be updated to accommodate `male`/`female` in the folder structure, and the system should be able to correctly identify and process both league types.

Current folder structure:
```
├── tt-repository
│   ├── import-fctt
│   │   ├── actas
│   │   │   ├── 2025-2026
│   │   │   │   ├── tercera nacional
│   │   │   │   │   ├── G1
│   │   │   │   │   ├── G2
│   │   │   │   │   ├── G3
```

Target folder structure:
```
├── tt-repository
│   ├── import-fctt
│   │   ├── actas
│   │   │   ├── 2026-2027
│   │   │   │   ├── male
│   │   │   │   │   ├── tercera nacional
│   │   │   │   │   │   ├── G1
│   │   │   │   │   │   ├── G2
│   │   │   │   │   │   ├── G3
│   │   │   │   ├── female
│   │   │   │   │   ├── copa-catalana-femenina-1a
│   │   │   │   │   ├── copa-catalana-femenina-2a
```

#### Acceptance Criteria
- [x] The import process correctly identifies and processes both `male` and `female` league types from the `<season>/<male|female>/<competition>/[<group>/]` layout; unknown gender folders are logged and skipped.
- [x] Imported data keeps league types distinct: FCTT competitions are stored as `<competition>-masculino` / `<competition>-femenino`, and group-less female competitions are stored with no group number.
- [x] New-format file names (`jornada-<d>-partido-<m>.json` and `jornada-<d>-partido-<home>-<away>.json`) are imported.
- [x] Unpublished actas (`acta_publicada: false`) are not stored as played matches, and their placeholder results are never persisted.
- [x] The payload `fase` is stored as the match phase and is part of the match natural key; re-importing stays idempotent.
- [x] Import and preview follow `docs/acta-model-definition.json`: optional `id_partido`/`acta_publicada`/`genero` are parsed, a nullable `abc_es_local` and empty `partidos`/`alineaciones` in unpublished actas are handled, and the schema documents that unpublished `resultado_final` is a placeholder.
- [x] The import process is tested with sample data for both male and female leagues.

#### Feature Details
→ See [FEAT-00074-DETAILS.md](./FEAT-00074-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---
