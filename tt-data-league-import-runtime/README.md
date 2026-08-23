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
`docs/migrations/FEAT-008-canonical-club.sql` before launch. It creates the
canonical `club` table, adds the nullable `federated_club.club_id` link, and
performs exact-name backfill and preservation checks. Do not use
`ddl-auto: update` as a substitute for this migration.

Apply `docs/migrations/FEAT-009-canonical-player.sql` after FEAT-008 and before
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
| `--season=<YYYY-YYYY>`         | No | Imports only the specified season. When omitted, imports all available seasons. |
| `--consolidate-clubs`          | No | Runs club consolidation in write mode after import. |
| `--consolidate-clubs=write`    | No | Explicitly runs club consolidation in write mode. |
| `--consolidate-clubs=report`   | No | Runs the same club matching path without saving changes. |
| `--consolidate-players`        | No | Runs player consolidation in write mode after import. |
| `--consolidate-players=write`  | No | Explicitly runs player consolidation in write mode. |
| `--consolidate-players=report` | No | Runs the same player matching path without saving changes. |

The source value is case-insensitive. Consolidation flags are opt-in and can
be used independently or together. Unknown consolidation modes fail with an
error; valid modes are `write`, `true`, or an empty value for write mode, and
`report`. Only the parameters listed above are recognized; `--base-folder` is
not an alias for `--actas-folder`.

## Launch modes

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
  --consolidate-clubs=report `
  --consolidate-players=report
```

## Execution order and failure behavior

The runtime:

1. Parses and validates the command-line arguments.
2. Traverses the selected source, importing all seasons or the requested season.
3. Runs requested club consolidation once after a successful traversal.
4. Runs requested player consolidation after club consolidation.
5. Logs the import and consolidation summaries.

The required `--actas-folder` argument, unknown sources, invalid consolidation
modes, and traversal failures stop the run with an error. Consolidation is not
run after an unsuccessful source traversal.

Club and player consolidation are source-scoped. They are not enabled by
default, and report mode performs no persistence writes.
