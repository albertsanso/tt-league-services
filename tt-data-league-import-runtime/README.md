# Table-tennis league import runtime

This module is the executable Spring Boot application that imports federation
exports into the league database. It wires the source-specific navigators,
import processors, JPA repositories, and optional club/player consolidation.
Parsing and import rules are implemented in `tt-data-league-import`; this
module is responsible for runtime configuration and sequencing.

The `actas-json` / `equipos-json` trees this runtime reads are produced by the
`tt-league-ingest` workspace (see `tt-league-ingest/README.md`).

## Requirements

- Java 21
- Maven
- PostgreSQL
- A database compatible with the schema managed by
  `tt-data-league-core-repository-jpa`
- An `actas-json` export directory for the selected source

## Database configuration

The application reads its PostgreSQL connection settings from environment
variables. The defaults are shown below for local development:

| Environment variable | Default |
| --- | --- |
| `DB_TTLEAGUEDATA_JDBC_URL` | `jdbc:postgresql://localhost:5432/ttleaguedata` |
| `DB_TTLEAGUEDATA_CREDENTIAL_USERNAME` | `postgres` |
| `DB_TTLEAGUEDATA_CREDENTIAL_PASSWORD` | `admin` |

Set these variables before launching in shared or production environments;
do not commit credentials or environment-specific configuration.

The application uses Hibernate with `ddl-auto: update`, PostgreSQL dialect,
and JDBC batching with a batch size of 50. The configuration is in
`src/main/resources/application.yml`. Actuator endpoints are exposed on port
`9090`, including:

```text
http://localhost:9090/actuator/health
```

`ddl-auto: update` does not rename legacy `club` or `player` tables, nor the
`team.club_id` or `player_season.player_id` columns. Existing databases require
the reviewed legacy deployment migration to `federated_club`,
`team.federated_club_id`, `federated_player`, and
`player_season.federated_player_id` before launch. The
`lineup.player_id`, `game.home_player_id`, `game.away_player_id`, and
`doubles_pair.player_id` columns remain linked to `player_season`.
Apply the manually owned PostgreSQL migration
`docs/migrations/FEAT-00008-canonical-club.sql` before launch. It creates the
canonical `club` table, adds the nullable `federated_club.club_id` link, and
performs exact-name backfill and preservation checks. Do not use
`ddl-auto: update` as a substitute for this migration.

Apply `docs/migrations/FEAT-00009-canonical-player.sql` after FEAT-00008 and before
launching the updated runtime. It creates the canonical `player` table, adds
the nullable `federated_player.player_id` link, and performs exact-name
backfill and preservation checks.

## Build

Run the module tests and build all required reactor dependencies from the
repository root:

```powershell
mvn -pl tt-data-league-import-runtime -am test
```

Build the executable Spring Boot jar:

```powershell
mvn -pl tt-data-league-import-runtime -am package
```

The packaged jar is created under:

```text
tt-data-league-import-runtime/target/tt-data-league-import-runtime-0.0.1-SNAPSHOT.jar
```

Run all repository tests from the root when validating changes that affect
shared modules:

```powershell
mvn test
```

## Command-line parameters

The application is launched with `--key=value` parameters:

| Parameter                      | Required | Values / behavior |
|--------------------------------| --- | --- |
| `--source=<source>`            | No | `rfetm`, `bcnesa`, or `fctt`; defaults to `rfetm`. |
| `--actas-folder=<path>`        | Yes | Root directory containing the source `actas-json` export. |
| `--rfetm-teams-folder=<path>`  | No | Required only when `--source=rfetm` and `--consolidate-clubs*` are used; points to the RFETM `equipos-json` export. |
| `--season=<YYYY-YYYY>`         | No | Imports only the specified season. When omitted, imports all available seasons. |
| `--consolidate-clubs`          | No | Runs club consolidation in write mode after import. |
| `--consolidate-clubs=write`    | No | Explicitly runs club consolidation in write mode. |
| `--consolidate-clubs=report`   | No | Runs the same club matching path without saving changes. |
| `--consolidate-players`        | No | Runs player consolidation in write mode after import. |
| `--consolidate-players=write`  | No | Explicitly runs player consolidation in write mode. |
| `--consolidate-players=report` | No | Runs the same player matching path without saving changes. |
| `--backfill-scheduled-matches`          | No | Runs the scheduled-match backfill (see below) in write mode instead of an import. |
| `--backfill-scheduled-matches=write`    | No | Explicitly runs the backfill in write mode. |
| `--backfill-scheduled-matches=report`   | No | Runs the same backfill candidate query without saving changes. |
| `--detect-amended-actas`                | No | Detects amended actas and re-applies them in write mode (see below). |
| `--detect-amended-actas=write`          | No | Explicitly runs amended-acta detection in write mode. |
| `--detect-amended-actas=report`         | No | Runs the same detection without saving changes. |

The source value is case-insensitive. Consolidation flags are opt-in and can
be used independently or together. Unknown consolidation modes fail with an
error; valid modes are `write`, `true`, or an empty value for write mode, and
`report`. Only the parameters listed above are recognized; `--base-folder` is
not an alias for `--actas-folder`. RFETM club consolidation reads team-to-club
relationships from `--rfetm-teams-folder`.

### BCNESA export layout

The BCNESA `actas-json` export is organized as
`<season>/<competition>/<G<n>|Other>/<phase>/<report>.json`. Report files are
named `acta*.json` (legacy, up to 2025-2026), or
`jornada_<NN>_local_team_<localId>_away_team_<awayId>.json` (2026-2027
onward). The team ids in the name are not used: teams come from the payload.

- The 2026-2027 competition folders are `rtb-*` slugs. They are stored under the
  legacy competition names through an explicit table (for example
  `rtb-preferent` is stored as `Preferent` and `rtb-veterans-2aa` as
  `Vet 2a _A_`); legacy folder names are stored unchanged.
- An `rtb-*` folder with no mapping is not imported: its files are counted as
  skipped, an issue names the folder, and the run ends as `FAILURE`. Add the
  folder to `BcnesaCompetitionNames`.
- A `.json` file under a phase folder with an unsupported name is counted as
  skipped and reported as an issue, which also ends the run as `FAILURE`.

### FCTT folder layout

The FCTT `actas-json` export is organized as
`<season>/<male|female>/<competition>/[<group>/]jornada-<day>-partido-<match>.json`:

```
actas-json/
  2026-2027/
    male/
      tercera-nacional/
        G1/
          jornada-1-partido-1234.json
    female/
      copa-catalana-femenina-1a/
        jornada-1-partido-5678.json
```

- The gender folder (`male` or `female`) is required and is folded into the
  stored competition name, following the RFETM convention (for example
  `tercera-nacional-masculino`, `copa-catalana-femenina-1a-femenino`). Any
  other folder at that level is logged and skipped.
- A competition folder may or may not have a group subfolder. Report files
  placed directly under the competition folder are imported with no group
  number; this is expected for competitions such as the women's cup groups
  that have not been split into `G<n>` groups.
- An acta whose `acta_publicada` field is `false` is an unpublished/scheduled
  fixture. Its `partidos` and `alineaciones` are empty and its
  `resultado_final` is a placeholder generated by the source, not a real
  result. The import never reads that placeholder: the fixture is stored as a
  `SCHEDULED` match with no games, lineups, or winner, and its clubs are
  registered from `equipos` (FEAT-00081). When the published acta for the same
  fixture arrives in a later run, the stored match is upgraded in place to
  `PLAYED` keeping its id; an already `PLAYED` match is never downgraded or
  rewritten by a pending acta, which is reported as a regression instead.
  Pending fixtures without team names (FCTT placeholders) cannot be attributed
   and are reported and skipped, never stored. Partial and invalid actas are
   likewise stored or kept as `SCHEDULED` and reported, not written as results.

### Run status and counters

Every run reports six lifecycle counters (in the traversal summary, the
execution metrics, the final log line, and the administration API result):
`scheduledCreated`, `upgradedToPlayed`, `rescheduled`, `partialActas`,
`invalidActas` and `unresolvedPendingFixtures`. The counters mirror the
per-acta outcomes and are exclusive: a partial or invalid acta that created or
rescheduled a `SCHEDULED` match is counted only under `partialActas` /
`invalidActas`, never also under `scheduledCreated` / `rescheduled`, so no acta
is ever counted twice. Regressions have no counter; they surface as warnings.

Run status is computed by one rule. Processor failures or traversal issues
give `FAILURE`. Otherwise a run is `EMPTY_RESULT` only when nothing was
dispatched *and* no unresolved pending fixture was recognised: no actas found,
or no file could be read as an acta. Everything else is `SUCCESS`, including
re-imports where every acta was already stored unchanged and pre-season runs
whose only actas are no-team placeholders. `SUCCESS` ends the administration
resource as `PROCESSED`; `FAILURE` and `EMPTY_RESULT` end it as `ERROR`. Note
that some runs that previously ended `ERROR` (for example re-importing an
unchanged pending snapshot) now end `PROCESSED`.

Reported outcomes (partial, invalid, regression, unresolved placeholders) are
warnings, not issues: they never fail the run, never skip consolidation, and
are surfaced to the administration API as `warning` findings on the terminal
result. `lastProcessedDate` on the import resource is set at the end of every
run, including failures and rejected submissions.

### Jornada progress

Along with the counters, a run reports the jornada progress of the imported
season: per source, season, competition, group and phase it states the current
round, the last complete round and the scheduled/played match counts. Both
rounds are derived from what is already stored:

- the *current round* is the highest round holding at least one `PLAYED` match,
  and is `-` while nothing of that competition, group and phase has been played;
- the *last complete round* is the highest stored round with no `SCHEDULED`
  match at or below it, and is `-` while the lowest stored round is still
  pending. Only stored rounds count: a gap in round numbers is not a pending
  match, and no total number of rounds is inferred because a source export is a
  sliding window over the season;
- *scheduled* and *played* are the per-status match counts.

A postponed early fixture therefore keeps the last complete round low while the
current round advances, which is what an operator needs to see. For the FCTT
2026-2027 `male/tercera-nacional/G1` shape (jornada 1 with three published and
three pending actas, jornada 2 with six pending ones) the run logs one line per
competition, group and phase:

```text
FCTT/2026-2027 progress tercera-nacional-masculino G1 1a Fase: current round 1, last complete round -, scheduled 9, played 3
```

`--season` is required for progress. A run without it logs
`<source> round progress not computed: run without --season` instead of silently
choosing a season, and a season without stored matches logs
`<source>/<season> round progress: no stored matches`. The administration API
exposes the same rows as `roundProgress` in the `process_status` terminal result
and in the `list_by_source` rows.

The progress is informational only. It is computed after the traversal from the
stored state and never decides which files are read, so a complete round does
not shorten a later import: every run still traverses the whole requested
season. If the progress query itself fails, the run ends `FAILURE` with a
`round-progress` issue rather than reporting success with no progress.

### Snapshot reconciliation

Every run is a snapshot: the season folder is traversed as a whole, so a stored
`SCHEDULED` fixture the snapshot no longer carries has probably been cancelled
or moved by the federation. After a `SUCCESS` traversal of a run with
`--season`, the run compares the stored `SCHEDULED` matches of that source and
season against the fixtures it just saw and reports each absent one. A stored
match counts as seen when its `id_partido` (source fixture id) or its natural
key (competition, group, phase, round and both clubs) appears in the snapshot,
so a fixture whose club is not yet registered, or that the identity guard
rejected, is still seen and never reported.

Rounds beyond the snapshot's highest round are not reported: a source export is
a sliding window over the season, so an upcoming jornada the window has not
reached yet is not a vanished fixture. The window is tracked per competition,
group and phase; a group that disappeared entirely is measured against the
snapshot-wide highest round, so it is still reported unless all its rounds lie
beyond that frontier.

Reconciliation is report-only. It never deletes, re-keys, re-statuses or
reschedules a match; deleting a vanished fixture stays a manual decision. Its
findings surface as warnings in the run log and as `warning` findings in the
administration API, and never change a `SUCCESS` run. A run without `--season`,
or one whose traversal did not end `SUCCESS`, skips reconciliation entirely so
an incomplete traversal cannot report present fixtures as absent. If the
reconciliation read itself fails, the run ends `FAILURE` with a
`snapshot-reconciliation` issue.

## Launch modes

### Backfill legacy empty and decided 0-0 matches

`--backfill-scheduled-matches[=write|report]` marks as `SCHEDULED` the
`PLAYED` matches of a source and season that carry no result at all: no
winner, no non-zero header games or sets won, and no game of their own with a
result (a winner, a non-zero set count, or a set-score row). This covers
legacy empty RFETM actas and the "decided 0-0" administrative placeholders; it
never selects a match that has a real winner or a game with a result.

The command is opt-in, requires an explicit `--season` in `YYYY-YYYY` form
with consecutive years, and is exclusive with every import argument
(`--actas-folder`, `--rfetm-teams-folder`, `--consolidate-clubs*`,
`--consolidate-players*`); combining them fails with an error instead of
running either one. It does not traverse actas.

Report mode runs the same candidate query and performs no writes. Write mode
marks every candidate `SCHEDULED` in one transaction, deleting the match's
placeholder games, lineups, set scores, and doubles pairs, and nulling its
header games/sets won and winner. Only result-less placeholder rows are
removed this way; the source actas are unaffected, and a later import can
recreate the child rows once the acta is actually published. Because write
mode leaves no candidates behind, a second write run finds zero matches: the
command is idempotent.

Report-then-write example (PowerShell):

```powershell
java -jar target\tt-data-league-import-runtime.jar `
  --source=rfetm --season=2025-2026 --backfill-scheduled-matches=report

java -jar target\tt-data-league-import-runtime.jar `
  --source=rfetm --season=2025-2026 --backfill-scheduled-matches
```

### Detect amended actas

`--detect-amended-actas[=write|report]` makes the import detect corrections to
already published actas. Every PLAYED match created or upgraded by an import
stores a content checksum of what was written (`v1:` SHA-256 of the canonical
header, lineups, games, set scores, and doubles pairs, independent of generated
ids and child order). When detection is enabled and a PLAYED acta arrives for a
stored PLAYED match whose checksum differs, the match is re-applied in place
through the same content replacement path used for upgrades, keeping its id, its
natural key, and its stored `source_fixture_id`.

The flag is opt-in and disabled by default; bare `--detect-amended-actas` uses
write mode and `=report` runs the same detection without any persistence write.
A stored PLAYED match with no checksum yet (legacy rows, or a value from another
version) adopts the incoming checksum as its baseline instead of being
rewritten, so enabling detection never mass-rewrites legacy seasons. A pending,
partial, or invalid acta for a stored PLAYED match is still reported as a
regression/invalid outcome in every mode: a PLAYED match is never downgraded.

Detected amendments surface as reportable warnings (`amended acta re-applied` /
`amended acta detected (report mode)`) and as one INFO line on the dedicated
logger `org.cttelsamicsterrassa.data.load.audit.AmendedActa`
(`amended-acta mode=… source=… … checksum=<old> -> <new>`); baseline adoption is
logged at DEBUG only. The flag is an import argument and cannot be combined with
`--backfill-scheduled-matches`.

```powershell
java -jar target\tt-data-league-import-runtime.jar `
  --source=rfetm --season=2025-2026 --actas-folder=C:\data\actas-json `
  --detect-amended-actas=report
```

## Administration import API

The administrator API exposes the same preview/validate/start/cancel/rollback
lifecycle used by the administration panel. All endpoints require the `ADMIN`
role. Supported source IDs are configured with `tt.league.import.sources`
(default `RFETM,BCNESA,FCTT`); arbitrary paths and URLs are never accepted.
Jobs are bounded to 100 history results and use mapping version `1`. The
current adapter keeps lifecycle state in memory; deployments requiring restart
recovery should provide a persistent `ImportJobsPort` adapter.

### Preview classification

The `preview` endpoint response carries a `classification` block (FEAT-00088)
that projects what an incremental upload of the stored season folder would
change, without writing anything. It is purely informational: it never changes
the preview `status`, never skips a file, and never feeds the real import run.
The block holds:

- `actas`: how the snapshot's actas bucket by completeness — `published`
  (PLAYED), `unpublished` (PENDING with resolvable teams), `partial`, `invalid`
  and `unresolved` (pending fixtures without team names, which no natural key
  can identify). Buckets follow the classifier, so legacy actas without
  `acta_publicada` are covered.
- `changes`: one row per competition, group and phase with a count per planned
  change — `newScheduled`, `newPlayed`, `upgrades`, `reschedules`, `unchanged`,
  `playedKept`, `regressions`, `invalidOnPlayed`, `identityConflicts` and
  `notStored`. The counts come from the same lifecycle decision the import run
  applies, so a preview and the run cannot disagree.
- `teamsPendingRegistration`: how many fixtures reference a team that is not
  registered for the season yet; the import run registers teams before storing
  matches, so these project as creations.
- `currentProgress` and `projectedProgress`: jornada-progress rows (the same
  shape as the import run's `roundProgress`) before and after the projected
  changes. A fixture repeated in the snapshot contributes its delta once, so a
  duplicated `id_partido` never inflates the projection; a projected count that
  would go negative is reported as an error rather than silently clamped.
- `duplicateFixtureIds`: every `id_partido` seen on more than one file of the
  snapshot, with the file locations. Each duplicate is also surfaced as a
  `warning` validation finding, as is every fixture identity conflict.

### Import only

Imports every available season for FCTT without running consolidation:

```powershell
java -jar tt-data-league-import-runtime\target\tt-data-league-import-runtime-0.0.1-SNAPSHOT.jar `
  --source=fctt `
  --actas-folder=C:\data\fctt
```

### Import one season

Imports only the selected season:

```powershell
java -jar tt-data-league-import-runtime\target\tt-data-league-import-runtime-0.0.1-SNAPSHOT.jar `
  --source=bcnesa `
  --actas-folder=C:\data\bcnesa `
  --season=2023-2024
```

### Import and write club consolidation

After the source traversal succeeds, consolidates the complete source-scoped
team inventory and persists canonical club associations:

```powershell
java -jar tt-data-league-import-runtime\target\tt-data-league-import-runtime-0.0.1-SNAPSHOT.jar `
  --source=fctt `
  --actas-folder=C:\data\fctt `
  --consolidate-clubs
```

When `--season` is also supplied, only that season is imported, but the
consolidation step still examines the complete existing inventory for the
selected source.

### Import and report club consolidation

Runs club matching and logs the proposed summary without creating, renaming,
or reassociating database records:

```powershell
java -jar tt-data-league-import-runtime\target\tt-data-league-import-runtime-0.0.1-SNAPSHOT.jar `
  --source=bcnesa `
  --actas-folder=C:\data\bcnesa `
  --consolidate-clubs=report
```

Report mode uses the same matching and counting path as write mode. It is
intended for reviewing the result before running a write-mode operation.

### Import and write both consolidations

Runs club consolidation first and player consolidation second:

```powershell
java -jar tt-data-league-import-runtime\target\tt-data-league-import-runtime-0.0.1-SNAPSHOT.jar `
  --source=fctt `
  --actas-folder=C:\data\fctt `
  --consolidate-clubs=write `
  --consolidate-players=write
```

### Import with independent report modes

Club and player consolidation modes are independent:

```powershell
java -jar tt-data-league-import-runtime\target\tt-data-league-import-runtime-0.0.1-SNAPSHOT.jar `
  --source=rfetm `
  --actas-folder=C:\data\rfetm `
  --rfetm-teams-folder=C:\data\rfetm\equipos-json `
  --consolidate-clubs=report `
  --consolidate-players=report
```

## Execution order and failure behavior

The runtime:

1. Parses and validates the command-line arguments.
2. Traverses the selected source, importing all seasons or the requested season.
3. Runs source-specific federated-club consolidation, then resolves the complete
   source-scoped federated-club inventory to canonical clubs.
4. Runs requested player consolidation after club consolidation.
5. Logs the import and consolidation summaries.

The required `--actas-folder` argument, unknown sources, invalid consolidation
modes, and traversal failures stop the run with an error. Consolidation is not
run after an unsuccessful source traversal.

Club and player consolidation are source-scoped. They are not enabled by
default, and report mode performs no persistence writes.

`--backfill-scheduled-matches` runs instead of steps 2-4 above, not alongside
them; it is rejected together with `--actas-folder`, `--rfetm-teams-folder`,
or either consolidation flag.
