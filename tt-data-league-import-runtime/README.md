# Table-tennis league import runtime

This module is the executable Spring Boot application that imports federation
exports into the league database. It wires the source-specific navigators,
import processors, JPA repositories, and optional club/player consolidation.
Parsing and import rules are implemented in `tt-data-league-import`; this
module is responsible for runtime configuration and sequencing.

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

The source value is case-insensitive. Consolidation flags are opt-in and can
be used independently or together. Unknown consolidation modes fail with an
error; valid modes are `write`, `true`, or an empty value for write mode, and
`report`. Only the parameters listed above are recognized; `--base-folder` is
not an alias for `--actas-folder`. RFETM club consolidation reads team-to-club
relationships from `--rfetm-teams-folder`.

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

## Administration import API

The administrator API exposes the same preview/validate/start/cancel/rollback
lifecycle used by the administration panel. All endpoints require the `ADMIN`
role. Supported source IDs are configured with `tt.league.import.sources`
(default `RFETM,BCNESA,FCTT`); arbitrary paths and URLs are never accepted.
Jobs are bounded to 100 history results and use mapping version `1`. The
current adapter keeps lifecycle state in memory; deployments requiring restart
recovery should provide a persistent `ImportJobsPort` adapter.

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
