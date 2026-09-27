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

- [FEAT-00093: Automated per-jornada fetch and upload](### [FEAT-00093] Automated per-jornada fetch and upload)

- [FEAT-00092: Season calendar and matchday management](### [FEAT-00092] Season calendar and matchday management)

- [FEAT-00091: BCNESA navigator cleanup for new file naming](### [FEAT-00091] BCNESA navigator cleanup for new file naming)

- [FEAT-00090: Delta upload mode](### [FEAT-00090] Delta upload mode)

- [FEAT-00089: Amended acta detection](### [FEAT-00089] Amended acta detection)

- [FEAT-00088: Preview classification for incremental uploads](### [FEAT-00088] Preview classification for incremental uploads)

- [FEAT-00087: Snapshot upload contract and shrink check](### [FEAT-00087] Snapshot upload contract and shrink check)

- [FEAT-00086: Snapshot reconciliation report](### [FEAT-00086] Snapshot reconciliation report)

- [FEAT-00085: Natural-key stability guard](### [FEAT-00085] Natural-key stability guard)

- [FEAT-00084: Jornada progress query and exposure](### [FEAT-00084] Jornada progress query and exposure)

- [FEAT-00083: Persist source fixture id (id_partido)](### [FEAT-00083] Persist source fixture id (id_partido))

- [FEAT-00082: Incremental import run status and metrics](### [FEAT-00082] Incremental import run status and metrics)

- [FEAT-00081: Match processor lifecycle for incremental actas](### [FEAT-00081] Match processor lifecycle for incremental actas)

- [FEAT-00080: Match upgrade and reschedule repository ports](### [FEAT-00080] Match upgrade and reschedule repository ports)

- [FEAT-00079: Read-side match status filtering](### [FEAT-00079] Read-side match status filtering)

- [FEAT-00078: Backfill legacy empty and decided 0-0 matches to SCHEDULED](### [FEAT-00078] Backfill legacy empty and decided 0-0 matches to SCHEDULED)

- [FEAT-00077: MatchStatus in domain and JPA](### [FEAT-00077] MatchStatus in domain and JPA)

- [FEAT-00076: Acta completeness classifier](### [FEAT-00076] Acta completeness classifier)

- [FEAT-00075: Incremental actas: reference fixtures and schema contract text](### [FEAT-00075] Incremental actas: reference fixtures and schema contract text)

- [FEAT-00074: FCTT import resource format modified](### [FEAT-00074] FCTT import resource format modified)

## In Progress

No features currently in progress.
## In Review

No features currently in review.
## Backlog

### [FEAT-00075] Incremental actas: reference fixtures and schema contract text
- **Status:** idea
- **Priority:** high
- **Effort:** small
- **Depends on:** —

#### Goal
Provide anonymised JUnit fixtures from the 2026-2027 example exports and restore the source-neutral acta schema descriptions so the incremental-import work is tested against real acta shapes.

#### Acceptance Criteria
- [ ] Anonymised fixtures exist for RFETM 2026-2027 published and unpublished actas, RFETM 2025-2026 "decided 0-0" (G17) and a legacy empty acta
- [ ] Anonymised fixtures exist for BCNESA 2026-2027 unpublished actas, including the 4-4 placeholder score (G14)
- [ ] Anonymised fixtures exist for an FCTT 2026-2027 published/unpublished pair, the no-team placeholder (G18), and the 2025-2026 6-0 placeholder
- [ ] docs/acta-model-definition.json restores the id_partido stability and unpublished resultado_final placeholder descriptions and keeps a source-neutral title
- [ ] All fixtures parse with the existing Acta parser in a JUnit test

#### Feature Details
→ See [FEAT-00075-DETAILS.md](./FEAT-00075-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00076] Acta completeness classifier
- **Status:** idea
- **Priority:** high
- **Effort:** small
- **Depends on:** FEAT-00075

#### Goal
Classify every acta/fixture as PLAYED, PENDING, PARTIAL or INVALID with acta_publicada deciding first, so no placeholder result is ever treated as a played match.

#### Acceptance Criteria
- [ ] ActaCompleteness { PLAYED, PENDING, PARTIAL, INVALID } and a shared classifier over Acta exist in tt-data-league-import
- [ ] Rules are evaluated in the order of analysis section 4.1 and resultado_final is never read to decide the class
- [ ] Games with no_disputado: true count as complete; legacy acta without acta_publicada needs at least one game with a result to be PLAYED
- [ ] A PENDING acta without both teams is identified as an unresolved pending fixture
- [ ] A JUnit test covers each rule and each T0 fixture; the FCTT 6-0, BCNESA 4-4 and RFETM decided 0-0 fixtures classify as PENDING

#### Feature Details
→ See [FEAT-00076-DETAILS.md](./FEAT-00076-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00077] MatchStatus in domain and JPA
- **Status:** idea
- **Priority:** high
- **Effort:** small
- **Depends on:** —

#### Goal
Give every stored match an explicit SCHEDULED or PLAYED lifecycle status so scheduled fixtures can be told apart from played matches.

#### Acceptance Criteria
- [ ] MatchStatus { SCHEDULED, PLAYED } exists in the domain as a Match field and builder property
- [ ] match_record.status is VARCHAR(20) NOT NULL DEFAULT 'PLAYED' mapped with @Enumerated(STRING) and works under ddl-auto: update on a populated table
- [ ] Mappers and in-memory repositories carry the status
- [ ] rfetm-datamodel.md documents the column, default and the invariant SCHEDULED => no lineups, games, set scores, doubles pairs or winner
- [ ] JPA tests cover the default value and the invariant

#### Feature Details
→ See [FEAT-00077-DETAILS.md](./FEAT-00077-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00078] Backfill legacy empty and decided 0-0 matches to SCHEDULED
- **Status:** idea
- **Priority:** high
- **Effort:** small
- **Depends on:** FEAT-00077

#### Goal
Repair existing phantom draws by marking legacy empty and "decided 0-0" matches as SCHEDULED through an opt-in, source- and season-scoped runtime command.

#### Acceptance Criteria
- [ ] An opt-in runtime command marks as SCHEDULED matches with no game result, no winner_team_id and null or 0-0 games won
- [ ] The command is scoped by source and season and supports report and write modes; report mode performs no writes
- [ ] Tests cover legacy empty actas, decided 0-0 matches, and a real played match that must stay PLAYED
- [ ] The runtime README documents the command, its arguments and modes

#### Feature Details
→ See [FEAT-00078-DETAILS.md](./FEAT-00078-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00079] Read-side match status filtering
- **Status:** idea
- **Priority:** high
- **Effort:** medium
- **Depends on:** FEAT-00077

#### Goal
Make every outcome, statistic and count read path consider only PLAYED matches and expose the status in match DTOs, so scheduled fixtures never leak into statistics.

#### Acceptance Criteria
- [ ] MatchOutcome returns empty for SCHEDULED matches and its javadoc states that a winner-less PLAYED match is a tie
- [ ] Match, player, club, federated-club, competition and club-search query handlers compute stats, win rates, form and streaks over PLAYED only
- [ ] MatchRepositoryHelper countBySeason, countAllMatches and findAllSeasons count PLAYED only; searchMatches/countMatches and fragment search default to PLAYED
- [ ] REST and MCP MatchDto/MatchDetailDto expose an additive status field
- [ ] findAllMatchesByTeamIds used by consolidation is not filtered by status
- [ ] Each affected handler has a test with a mixed SCHEDULED/PLAYED fixture
- [ ] Shipped with or before the processor lifecycle feature (T6)

#### Feature Details
→ See [FEAT-00079-DETAILS.md](./FEAT-00079-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00080] Match upgrade and reschedule repository ports
- **Status:** idea
- **Priority:** high
- **Effort:** medium
- **Depends on:** FEAT-00077

#### Goal
Provide transactional domain ports to upgrade a SCHEDULED match in place to PLAYED and to reschedule it, preserving the match UUID.

#### Acceptance Criteria
- [ ] MatchRepository.replaceMatchContent replaces header and children (lineups, games, set scores, doubles pairs) of an existing match in one transaction, preserving its id
- [ ] MatchRepository.updateSchedule (or a verified createExisting/saveMatch merge) updates date, time, city, venue and referee
- [ ] JPA and in-memory implementations exist
- [ ] Rollback tests prove a failed replace leaves no half-written match

#### Feature Details
→ See [FEAT-00080-DETAILS.md](./FEAT-00080-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00081] Match processor lifecycle for incremental actas
- **Status:** idea
- **Priority:** high
- **Effort:** medium
- **Depends on:** FEAT-00076, FEAT-00079, FEAT-00080

#### Goal
Store pending actas of all three sources as SCHEDULED matches and upgrade them in place to PLAYED when their published acta arrives.

#### Acceptance Criteria
- [ ] RFETM, FCTT and BCNESA match processors implement the shared algorithm of analysis section 4.3 (create, upgrade, reschedule, skip, regression issue)
- [ ] RFETM classifies actas before buildMatch and never stores unpublished actas as played
- [ ] FCTT replaces the FEAT-00074 unpublished skip with the SCHEDULED branch and skips no-team placeholders before the team processor
- [ ] BcnesaMatchdaySplitter yields one fixture named by equipos when partidos is empty
- [ ] The SCHEDULED branch never copies resultado_final or a winner; a PLAYED match is never downgraded
- [ ] The doubles path handles jugadores: [] without creating a DoublesPair
- [ ] FCTT preview wording reflects the new behaviour
- [ ] Tests cover create, upgrade, reschedule, idempotent re-import and regression for each source

#### Feature Details
→ See [FEAT-00081-DETAILS.md](./FEAT-00081-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00082] Incremental import run status and metrics
- **Status:** idea
- **Priority:** high
- **Effort:** small
- **Depends on:** FEAT-00081

#### Goal
Report incremental-import outcomes with explicit counters and make no-change runs succeed, so operators get correct run statuses.

#### Acceptance Criteria
- [ ] Traversal summaries, ImportExecutionMetrics and ImportProcessResult carry scheduledCreated, upgradedToPlayed, rescheduled, partialActas, invalidActas and unresolvedPendingFixtures
- [ ] A run with no changes (for example all actas pending and already stored) ends SUCCESS/PROCESSED; EMPTY_RESULT is kept for no actas found
- [ ] ImportResource.lastProcessedDate is set when a run finishes
- [ ] README documents the status change and counters

#### Feature Details
→ See [FEAT-00082-DETAILS.md](./FEAT-00082-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00083] Persist source fixture id (id_partido)
- **Status:** idea
- **Priority:** medium
- **Effort:** small
- **Depends on:** FEAT-00077, FEAT-00081

#### Goal
Store the source-supplied id_partido on each match so a fixture can be identified independently of its natural key across publication.

#### Acceptance Criteria
- [ ] match_record.source_fixture_id is a nullable VARCHAR(100) with a unique (source, source_fixture_id) constraint; external_id is not reused
- [ ] Match has a sourceFixtureId field supported by mappers and in-memory repositories
- [ ] MatchRepository.findBySourceFixtureId(ImportSource, String) exists
- [ ] All three sources fill it from id_partido on create and on upgrade; legacy rows stay null
- [ ] rfetm-datamodel.md documents the column and constraint

#### Feature Details
→ See [FEAT-00083-DETAILS.md](./FEAT-00083-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00084] Jornada progress query and exposure
- **Status:** idea
- **Priority:** medium
- **Effort:** small
- **Depends on:** FEAT-00077, FEAT-00081

#### Goal
Track the current and last complete jornada per source, season, competition, group and phase derived from match statuses.

#### Acceptance Criteria
- [ ] MatchRepository.findRoundProgress(source, season) returns current round, last complete round and scheduled/played counts per competition/group/phase
- [ ] Progress is exposed in the import run result, the import-resource read model and the CLI summary
- [ ] Progress is informational and never used to skip files
- [ ] Test with the FCTT 2026-2027 tercera-nacional/G1 shape yields current jornada 1 and no last complete jornada
- [ ] README documents the progress output

#### Feature Details
→ See [FEAT-00084-DETAILS.md](./FEAT-00084-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00085] Natural-key stability guard
- **Status:** idea
- **Priority:** medium
- **Effort:** small
- **Depends on:** FEAT-00081, FEAT-00083

#### Goal
Prevent duplicate fixtures caused by jornada drift by warning on the RFETM day-folder fallback and cross-checking the natural key against id_partido.

#### Acceptance Criteria
- [ ] A warning is reported when payload jornada is missing and the RFETM day-folder fallback is used
- [ ] When a stored match has the same id_partido but a different natural key, an issue is raised and no duplicate match is created
- [ ] Tests cover both cases

#### Feature Details
→ See [FEAT-00085-DETAILS.md](./FEAT-00085-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00086] Snapshot reconciliation report
- **Status:** idea
- **Priority:** medium
- **Effort:** small
- **Depends on:** FEAT-00081, FEAT-00083

#### Goal
Report stored SCHEDULED matches that are absent from a snapshot, without deleting anything.

#### Acceptance Criteria
- [ ] After a snapshot run, stored SCHEDULED matches of the season not seen in the run are reported, matched by id_partido or natural key
- [ ] Rounds beyond the snapshot's highest round are not flagged (FCTT sliding window)
- [ ] No match is deleted or modified by reconciliation
- [ ] Tests cover a vanished fixture and the FCTT window case

#### Feature Details
→ See [FEAT-00086-DETAILS.md](./FEAT-00086-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00087] Snapshot upload contract and shrink check
- **Status:** idea
- **Priority:** medium
- **Effort:** small
- **Depends on:** —

#### Goal
Formalise the full-season snapshot upload contract and reject truncated snapshots that would wipe good acta files.

#### Acceptance Criteria
- [ ] Snapshot mode (season folder replaced on upload) is documented as the default contract
- [ ] An upload with fewer published actas than the stored season folder is rejected unless an explicit override is given
- [ ] A moving FCTT window with at least as many published actas is still accepted
- [ ] Tests cover accepted, rejected and overridden uploads

#### Feature Details
→ See [FEAT-00087-DETAILS.md](./FEAT-00087-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00088] Preview classification for incremental uploads
- **Status:** idea
- **Priority:** medium
- **Effort:** small
- **Depends on:** FEAT-00076, FEAT-00084

#### Goal
Let operators see what an incremental upload will change before importing it, for all three sources.

#### Acceptance Criteria
- [ ] Preview reports counts of published, unpublished, invalid, partial and unresolved actas for RFETM, BCNESA and FCTT
- [ ] Preview reports new scheduled matches, upgrades, reschedules and regressions per competition/group
- [ ] Preview reports the resulting jornada progress
- [ ] Preview flags duplicate id_partido within a snapshot

#### Feature Details
→ See [FEAT-00088-DETAILS.md](./FEAT-00088-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00089] Amended acta detection
- **Status:** idea
- **Priority:** low
- **Effort:** medium
- **Depends on:** FEAT-00080

#### Goal
Detect and re-apply corrections to already published actas via a stored source checksum, as an opt-in behaviour.

#### Acceptance Criteria
- [ ] match_record.source_checksum stores a checksum of the applied acta and is documented in rfetm-datamodel.md
- [ ] When enabled and a PLAYED acta's checksum changes, the match is re-applied via replaceMatchContent and an audit line is logged
- [ ] The behaviour is opt-in and disabled by default

#### Feature Details
→ See [FEAT-00089-DETAILS.md](./FEAT-00089-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00090] Delta upload mode
- **Status:** idea
- **Priority:** low
- **Effort:** medium
- **Depends on:** FEAT-00087

#### Goal
Allow uploading only new acta files for a season without deleting previously received ones.

#### Acceptance Criteria
- [ ] The manifest accepts an optional mode of snapshot or delta, defaulting to snapshot
- [ ] Delta mode merges files into the season folder without deleting existing ones
- [ ] A rollback copy of the season folder is kept for delta uploads
- [ ] README documents the manifest field

#### Feature Details
→ See [FEAT-00090-DETAILS.md](./FEAT-00090-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00091] BCNESA navigator cleanup for new file naming
- **Status:** idea
- **Priority:** low
- **Effort:** small
- **Depends on:** FEAT-00075, FEAT-00081

#### Goal
Align the BCNESA navigator with the 2026-2027 file naming and remove obsolete fallbacks.

#### Acceptance Criteria
- [ ] The Other-group round fallback supports acta_<home>-<away>_<jornada>.json or is replaced by payload jornada with a reported issue instead of a guess
- [ ] The need for multi-fixture splitting and BcnesaClubIndex is measured and the outcome recorded
- [ ] Tests cover legacy and new file names

#### Feature Details
→ See [FEAT-00091-DETAILS.md](./FEAT-00091-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00092] Season calendar and matchday management
- **Status:** idea
- **Priority:** low
- **Effort:** large
- **Depends on:** FEAT-00079, FEAT-00084

#### Goal
Provide an API and UI to manage the season calendar over SCHEDULED and PLAYED matches.

#### Acceptance Criteria
- [ ] API lists a season calendar per competition/group/jornada with match status
- [ ] Derived postponed/overdue states are shown without being stored
- [ ] UI presents the calendar and jornada progress

#### Feature Details
→ See [FEAT-00092-DETAILS.md](./FEAT-00092-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00093] Automated per-jornada fetch and upload
- **Status:** idea
- **Priority:** low
- **Effort:** medium
- **Depends on:** FEAT-00087

#### Goal
Automate fetching extractor snapshots and uploading them each jornada.

#### Acceptance Criteria
- [ ] A scheduled job fetches and uploads a snapshot per source and season
- [ ] Configuration is explicit and environment-driven with no committed secrets
- [ ] Failures are reported clearly without silent fallback

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
