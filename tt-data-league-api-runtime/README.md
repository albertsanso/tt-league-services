# TT League API runtime

This service provides the runtime API for the TT League application. 
It is built using Spring Boot and connects to a PostgreSQL database. 
The import upload ZIPs (`POST /api/v1/administration/import/upload`) are produced by the
`tt-league-ingest` workspace (`package` / `upload`, see `tt-league-ingest/README.md`).
The configuration for the service, including database connection details, JWT settings, mail server settings, and multipart upload limits, can be found in the `application.yml` file.

## Requirements

- Java 21
- Maven
- PostgreSQL (see `.podman/podman-compose.yaml` for a local instance)

## Configuration

The application reads its configuration from environment variables at startup; defaults are for local development and must be overridden in shared or production environments. Do not commit credentials or environment-specific configuration.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `DB_TTLEAGUEDATA_JDBC_URL` | `jdbc:postgresql://localhost:5432/ttleaguedata` | PostgreSQL JDBC URL |
| `DB_TTLEAGUEDATA_CREDENTIAL_USERNAME` | `postgres` | Database username |
| `DB_TTLEAGUEDATA_CREDENTIAL_PASSWORD` | `admin` | Database password |
| `JWT_SIGNING_SECRET` | dev-only placeholder | JWT signing secret; required, at least 32 bytes, see Considerations below |
| `JWT_EXPIRATION_MILLIS` | `108000000` | JWT token expiration, in milliseconds |
| `PASSWORD_RECOVERY_FROM` | `no-reply@localhost` | From-address used on password recovery emails |
| `PASSWORD_RECOVERY_RESET_URL` | `http://localhost:5173/reset-password` | Frontend reset-password URL embedded in recovery emails |
| `MAIL_HOST` | `localhost` | SMTP host |
| `MAIL_PORT` | `25` | SMTP port |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | empty | SMTP credentials |
| `IMPORT_UPLOAD_MAX_FILE_SIZE` | `100MB` | Max ZIP import upload file size |
| `IMPORT_UPLOAD_MAX_REQUEST_SIZE` | `100MB` | Max import upload request size; must be `>=` the file size limit |
| `IMPORT_EXECUTION_BATCH_SIZE` | `50` | Import batch size |
| `IMPORT_EXECUTION_CLUB_CONSOLIDATION` | `write` | `write`, `report`, or `disabled` |
| `IMPORT_EXECUTION_PLAYER_CONSOLIDATION` | `write` | `write`, `report`, or `disabled` |
| `IMPORT_EXECUTION_AMENDED_ACTA_DETECTION` | `disabled` | `write`, `report`, or `disabled` (amended-acta detection, FEAT-00089) |
| `IMPORT_JOBS_BUSY_RETRY_INTERVAL` | `PT10S` | ISO-8601 duration an import job sleeps between checks while another import is active (FEAT-00100) |
| `IMPORT_JOBS_BUSY_TIMEOUT` | `PT2H` | ISO-8601 duration an import job may wait for another import before it fails; a zero, negative or malformed value fails startup |
| `CALENDAR_OVERDUE_GRACE_DAYS` | `7` | Days a scheduled match remains pending before it becomes overdue (FEAT-00092); a negative or non-numeric value fails startup |

The HTTP API listens on the default Spring Boot port (`8080`); a separate Actuator management port is exposed on `9090`, including `http://localhost:9090/actuator/health`.

## Build

Run the module tests and build all required reactor dependencies from the repository root:

```powershell
mvn -pl tt-data-league-api-runtime -am test
```

Run all repository tests from the root when validating changes that affect shared modules:

```powershell
mvn test
```

## Package

Build the executable Spring Boot jar:

```powershell
mvn -pl tt-data-league-api-runtime -am clean package -DskipTests
```

The packaged jar is created under:

```text
tt-data-league-api-runtime/target/tt-data-league-api-runtime-0.0.1-SNAPSHOT.jar
```

Skip tests when only a jar is needed (for example, when tests already passed in CI):

```powershell
mvn -pl tt-data-league-api-runtime -am package -DskipTests
```

## Deploy

Set the required environment variables — at minimum a production `JWT_SIGNING_SECRET` and the database credentials — then run the packaged jar:

```powershell
$env:JWT_SIGNING_SECRET = "<random value, at least 32 bytes>"
$env:DB_TTLEAGUEDATA_JDBC_URL = "jdbc:postgresql://<host>:5432/ttleaguedata"
$env:DB_TTLEAGUEDATA_CREDENTIAL_USERNAME = "<username>"
$env:DB_TTLEAGUEDATA_CREDENTIAL_PASSWORD = "<password>"

java -jar tt-data-league-api-runtime\target\tt-data-league-api-runtime-0.0.1-SNAPSHOT.jar
```

Notes for a deployment target:

- The target database must be reachable and match the schema managed by `tt-data-league-core-repository-jpa`; `ddl-auto: update` is not a substitute for the reviewed migrations under `docs/migrations/`. Apply any pending migration before starting the service.
- `ImportFolderSettingStartupInitializer` provisions the `IMPORT/repository-folder` administrator setting at startup if absent, defaulting to `c:\tt-repository`; a persistence failure here fails application boot. Ensure the configured import folder exists as a directory before launch, or set it afterwards through the System settings panel.
- `InitialUserStartupInitializer` seeds two fixed ADMIN accounts (`albert`/`albert`, `oscar`/`Oscar&1234`) at startup if an account with that username or email does not already exist; a persistence failure here fails application boot. These credentials are hardcoded in source (not env-configurable) and do not meet the normal password-strength rules enforced elsewhere. Change or remove these accounts before exposing any non-development environment.
- The run registry backing the async import endpoints is in-memory per JVM; it does not survive a restart or a multi-instance deployment. Import jobs (`import_job`, `import_job_season`) are persisted and recovered at startup, but they rely on that registry and on a per-JVM submission lock, so run a single instance.
- Run the process as a long-lived service (for example, a Windows service via NSSM, or a systemd unit on Linux) so it restarts on failure and on host reboot; there is no bundled service unit in this repository.
- Monitor `http://<host>:9090/actuator/health` for liveness once deployed.

ZIP import uploads accept files up to 100 MB by default. Override
`IMPORT_UPLOAD_MAX_FILE_SIZE` and `IMPORT_UPLOAD_MAX_REQUEST_SIZE` when a
different deployment limit is required; the request limit must be at least as
large as the file limit.

## ZIP import upload contract

`POST /api/v1/administration/import/upload` accepts a ZIP whose root contains a
`manifest.json` with three required keys — `source` (`RFETM`, `FCTT` or
`BCNESA`), `seasons` (non-empty `YYYY-YYYY` list) and `assets` (a map of asset
name, `ACTAS` or `TEAMS`, to an object holding only a `files` array) — and one
optional key, `mode`. Each asset supports two file layouts: an empty `files`
list moves the extracted `<season>/` folder wholesale, or explicit entries laid
out as `actas-json/<season>/...` for ACTAS and `equipos-json/...` for TEAMS. A
delta manifest looks like:

```json
{
  "source": "FCTT",
  "mode": "delta",
  "seasons": ["2026-2027"],
  "assets": {
    "ACTAS": {"files": ["actas-json/2026-2027/jornada-5/acta-1.json"]}
  }
}
```

`mode` accepts exactly the lowercase `snapshot` or `delta`; any other value,
including `DELTA` or an unknown key, is rejected with `400`. When `mode` is
absent the upload is a snapshot.

**Optional provenance keys.** A manifest may also carry the keys below, which
`tt-league-ingest` writes into every ZIP it packages. Each key is optional, and
manifests without them import exactly as before. A key that is present must be
valid (an explicit `null` is not); otherwise the upload is rejected with `400`:

| Key | Rule |
|---|---|
| `runId` | 1–64 characters among `A-Z`, `a-z`, `0-9`, `.`, `_`, `-` |
| `generator` | non-blank string of at most 100 characters |
| `generatorVersion` | non-blank string of at most 100 characters |
| `contentSha256` | 64 lowercase hexadecimal characters; must match the ZIP content (see below) |
| `matchCounts` | object with exactly the integers `expected`, `withResult`, `pending`, all non-negative, and `withResult + pending == expected` |

```json
{
  "source": "FCTT",
  "mode": "delta",
  "seasons": ["2026-2027"],
  "assets": {"ACTAS": {"files": ["actas-json/2026-2027/jornada-5/acta-1.json"]}},
  "runId": "3f2b9c0e8d7a4f1b9e6c5d4a3b2c1d0e",
  "generator": "tt-league-ingest",
  "generatorVersion": "0.1.0",
  "contentSha256": "<64 lowercase hex characters>",
  "matchCounts": {"expected": 1, "withResult": 1, "pending": 0}
}
```

`matchCounts` counts the actas packaged in the ZIP: `pending` are those with
`acta_publicada: false`, and `withResult` are the rest (a missing
`acta_publicada` means published).

**`contentSha256` algorithm.** Take every ZIP entry except directory entries
and the root `manifest.json`. Use each entry name exactly as stored in the ZIP
(UTF-8, `/` separators) and sort the names by their UTF-8 bytes. For each name,
append `<name>\n<lowercase hex SHA-256 of the entry's uncompressed bytes>\n` to
one buffer. `contentSha256` is the lowercase hex SHA-256 of that buffer, so it
does not depend on ZIP timestamps, compression or entry order. The upload
recomputes it from the received ZIP (`ContentHash`) and rejects a mismatch with
`400` (`manifest.json contentSha256 does not match the ZIP content`). A ZIP with
two entries of the same name is rejected as well. The upload endpoint treats
the provenance as informational; the [import jobs API](#import-jobs-api-feat-00100)
uses `contentSha256` as its deduplication key and records `runId`.

**Snapshot mode (default) replaces the stored season.** Every upload deletes the
stored `import-<source>/<asset>/<season>` folder first, then moves the extracted
content in. Each ZIP must therefore hold the complete season as currently
exported, with both published and unpublished actas; an upload that holds a
subset wipes the rest of the stored season.

**Delta mode merges into the stored season.** Files are moved into the season
folder with same-path files overwritten by the incoming copy; stored files that
the ZIP does not mention are kept, and nothing is deleted from the season
folder. Delta therefore only adds or replaces files and cannot remove a stored
acta — upload a complete snapshot to drop files. Before moving anything, a
delta keeps one rollback copy of the season folder at
`<import folder>/upload-rollback/<source>/<asset>/<season>/` (the season exactly
as it was before this delta), replacing the previous copy for the same source,
asset and season. This path is outside every folder an import reads. To restore
manually: stop imports, replace the season folder with the rollback copy, then
start an import for the resource. When no season folder is stored yet there is
nothing to roll back to and no copy is written.

To protect against truncated snapshots, the upload runs a synchronous,
read-only **shrink check** before the asynchronous load is scheduled, for the
ACTAS asset of every manifest season. A file counts as a *published acta* when
it is a `.json` file whose JSON root is an object and whose `acta_publicada`
field is absent or is not the boolean `false` (RFETM and BCNESA never send the
field, so all of their actas count). Unreadable or invalid JSON files count as
not published. If the incoming published count is at least the stored count,
the upload is accepted — file names do not have to match, so an FCTT window
that drops old jornadas is accepted as long as the published count does not
fall. If it is lower, the upload is rejected with `409 Conflict` and a message
naming each shrinking season, its stored and incoming counts, and the override.
Retry with `allowPublishedShrink=true` (form field on the same endpoint,
default `false`) to replace the stored season anyway; a warning naming the
counts is logged. In delta mode the check compares the stored count with the
**projected merged** count — published stored files the ZIP does not overwrite
plus the published incoming files — so a pure addition is never rejected; only
overwriting a published acta with an unpublished or invalid copy can be. The
check never touches the stored folder, and TEAMS-only manifests are never
checked. Malformed ZIPs keep returning `400`.

Import execution is configured server-side under `tt.league.import.execution`.
Club and player consolidation run in `WRITE` mode by default; use
`IMPORT_EXECUTION_CLUB_CONSOLIDATION` or
`IMPORT_EXECUTION_PLAYER_CONSOLIDATION` (`WRITE`, `REPORT`, or `disabled`) to
override them. Amended-acta detection (FEAT-00089) is `disabled` by default; set
`IMPORT_EXECUTION_AMENDED_ACTA_DETECTION` (`WRITE`, `REPORT`, or `disabled`) to
detect corrections to already published actas and re-apply them in place. The API
start endpoint accepts only the stored import-resource
ID and never a client-supplied path.

The API start endpoint (`POST /api/v1/administration/import/start`) runs the import
asynchronously: it returns `202 Accepted` immediately with a run id and initial (`queued`)
status instead of waiting for the traversal to finish. Poll
`GET /api/v1/administration/import/process_status?runId=<uuid>` for progress (processed/total
counts, percentage when a reliable total is available, skipped/error counts) and the terminal
result. The terminal result carries the six lifecycle counters (`scheduledCreated`,
`upgradedToPlayed`, `rescheduled`, `partialActas`, `invalidActas`,
`unresolvedPendingFixtures`) and reports partial, invalid, regression and
unresolved-placeholder outcomes as `warning` findings. The run registry is in-memory per JVM
(`InMemoryImportRunRegistry`); it prevents two
accepted runs for the same import resource but does not persist run history across restarts.

The terminal `process_status` result also carries `roundProgress`: one row per
competition, group and phase of the resource's source and season, with
`competition`, `groupNumber`, `phase`, `currentRound`, `lastCompleteRound`,
`scheduledMatches` and `playedMatches`. `currentRound` is the highest round with
at least one played match and `lastCompleteRound` the highest stored round with
no scheduled match at or below it; both are `null` when nothing is played and
when the lowest stored round is still pending. `GET
/api/v1/administration/import/list_by_source` returns the same rows per
resource, derived live from the stored matches. Progress is informational: it
never changes which files an import reads, and a season that holds no stored
match has an empty list.

The preview endpoint (`POST /api/v1/administration/import/preview`) additionally
returns a `classification` block (FEAT-00088): the acta buckets
(`published`/`unpublished`/`partial`/`invalid`/`unresolved`), the planned
changes per competition, group and phase (`newScheduled`, `newPlayed`,
`upgrades`, `reschedules`, `unchanged`, `playedKept`, `regressions`,
`invalidOnPlayed`, `identityConflicts`, `notStored`), the
`teamsPendingRegistration` count, the `currentProgress` and `projectedProgress`
jornada rows (same shape as `roundProgress`), and any `duplicateFixtureIds`.
Duplicated `id_partido`s and fixture identity conflicts are also surfaced as
`warning` findings. The block is informational: it never changes the preview
`status` and never writes. See the import-runtime README for the full field
reference.

At startup, `ImportFolderSettingStartupInitializer` ensures the `IMPORT/repository-folder`
administrator setting exists, creating it with the default value `c:\tt-repository`
only when it is absent. Provisioning is idempotent: it never overwrites an
administrator's configured value, and a persistence failure during startup
fails application boot instead of leaving the setting unconfigured. Import
workflows resolve this persisted setting when no explicit folder is supplied;
administrators can view and change it through the System settings panel, and
the configured path must exist as a directory at import execution time.

Similarly, `RfetmTeamsFolderSettingStartupInitializer` ensures the
`IMPORT/rfetm-teams-folder` administrator setting exists, creating it with the
default value `import-rfetm\teams` only when it is absent. RFETM club
consolidation resolves this setting on every run (via
`RfetmTeamsFolderPathResolver`), joined onto the `IMPORT/repository-folder`
setting to build the absolute teams folder path, so administrator changes to
either setting take effect without an application restart. This replaces the
previous `IMPORT_EXECUTION_RFETM_TEAMS_FOLDER` environment variable, which no
longer exists.

## Import jobs API (FEAT-00100)

The jobs API lets an automated client submit an upload ZIP and follow the
resulting import to completion through one id. It accepts the same ZIP and
manifest as `/upload` and requires the `ADMIN` role. The manual upload,
preview and start endpoints are unchanged.

| Endpoint | Result |
|---|---|
| `POST /api/v1/administration/import/jobs` (multipart `file`, optional `runId`, `allowPublishedShrink`, default `false`) | `202 {importJobId, status, created: true}` for a new job; `200` with the same shape and `created: false` for an existing job of the same content; `400` for an invalid file, ZIP, manifest, `contentSha256` or `runId`; `409` when the upload shrinks published actas (see the shrink check above) |
| `GET /api/v1/administration/import/jobs/{id}` | `200` with the job, or `404` |
| `GET /api/v1/administration/import/jobs?source=&from=&to=&limit=` | `200` with jobs, newest first; `from`/`to` are inclusive UTC dates (`YYYY-MM-DD`) on the creation time, `limit` defaults to 50 and must be 1–200; an unknown `source`, `from` after `to` or an out-of-range `limit` is `400` |

`runId` is the caller's own run id (1–64 characters among `A-Z`, `a-z`,
`0-9`, `.`, `_`, `-`) and is returned as `runId`; the manifest `runId` is
returned as `manifestRunId`.

**Lifecycle.** The submission is validated synchronously, the ZIP bytes are
staged at `<import folder>/import-jobs/<importJobId>.zip`, and the job is
`QUEUED`. Jobs then run one at a time on a dedicated thread: `STORING`
re-extracts the staged ZIP, repeats the shrink check against the data stored
now and stores the content exactly like `/upload`; `IMPORTING` runs one import
per ACTAS season of the manifest, in manifest order, through the same import
run machinery as `/start`. The staged ZIP is deleted once the job is terminal.

| Status | Meaning |
|---|---|
| `SUCCEEDED` | Every season ended `success` or `empty-result` with no processor failures or execution issues (also a TEAMS-only ZIP, which has no season to import) |
| `PARTIAL` | At least one season succeeded, but another failed or reported processor failures or execution issues |
| `FAILED` | No season succeeded, or the job failed before importing (`errorDetail` says why) |

`GET /jobs/{id}` returns `importJobId`, `status`, `source`, `seasons`, `mode`,
`contentSha256`, `runId`, `manifestRunId`, `allowPublishedShrink`,
`requestedBy`, `errorDetail`, `createdAt`, `startedAt`, `finishedAt` and
`seasonResults`. Each season result has `season`, `importResourceId`,
`importRunId`, `status` (the import run status values), `errorDetail` and
`result`: the same `ImportProcessResult` shape as the terminal
`process_status` result, with its counters, lifecycle counters and
`roundProgress`.

**Deduplication.** When the manifest carries `contentSha256` and a job of the
same source with that hash is `QUEUED`, `STORING`, `IMPORTING`, `SUCCEEDED` or
`PARTIAL`, the submission returns that job with `200` and starts nothing; this
check runs before the shrink check. After a `FAILED` job the same content can
be resubmitted. Manifests without `contentSha256` are never deduplicated.

**One import at a time.** Imports are single-run system-wide. A job waits
before storing, and again before each season's run, while another import (for
example one started manually with `/start`) is active, re-checking every
`IMPORT_JOBS_BUSY_RETRY_INTERVAL`. When a wait exceeds
`IMPORT_JOBS_BUSY_TIMEOUT`, the job (before storing) or that season (while
importing) fails with `Timed out after <timeout> waiting for another import to
finish`. The wait before storing is a check, not a reservation: a manual
`/start` in the moments between the check and the storing behaves as it does
with `/upload` today.

**Restarts.** Jobs are persisted in `import_job` and `import_job_season` (see
the JPA data model). At startup, before the web server accepts requests, every
`STORING` or `IMPORTING` job ends `FAILED` with `Interrupted by a platform restart`, the
import resource it left `PROCESSING` returns to `ERROR` so the season can be
imported again, and every `QUEUED` job is resumed in creation order. A job still
waiting to start storing when the application stops stays `QUEUED` and resumes.
A recovery failure fails startup. Run a single instance: the run registry and
the submission lock are per JVM.

## Season calendar (FEAT-00092)

The runtime exposes a per-competition season calendar that couples the stored
matches of every status with derived calendar states. The grace period is bound
from `tt.league.calendar.overdue-grace-days` (`CALENDAR_OVERDUE_GRACE_DAYS`,
default `7`); an invalid value fails startup with no silent fallback.

- `GET /api/v1/match/calendar?source=&season=&competition=&group=&round=`
  (`matches:read`) returns the calendar grouped by group/phase and jornada. The
  response summarizes each group's progress and lists each round's matches.
- `GET /api/v1/match/calendar/range?source=&season=&from=&to=&competition=&group=&team=`
  (`matches:read`, FEAT-00093) returns the matches of **every** competition of a
  source and season whose date is in `[from, to)` (`from`/`to` ISO dates, `to`
  exclusive), with the same calendar states as the jornada calendar. The optional
  `competition`, `group` (needs a competition) and `team` (a season-specific team
  UUID, home or away) parameters filter the matches; the response `facets`
  (competitions, groups of the selected competition, teams) are computed before
  filtering, so options never shrink when a filter is applied. The range is
  limited to 62 days (a six-week month grid); undated matches are never included.
  Invalid parameters (dates, `from >= to`, more than 62 days, bad team UUID,
  unknown source) return `400 "Invalid calendar range"`; a handler failure
  returns `500 "Calendar range failed"`. Every match row also carries
  `competition`, `groupNumber`, `phase`, `round`, `homeTeamId` and `awayTeamId`
  (additive fields, also present in the jornada calendar).
- `PUT /api/v1/match/{id}/overdue-mark` (`matches:write`) records a manual
  overdue mark on a `SCHEDULED` match; `markedBy` is the authenticated user,
  never the request body. Re-marking is idempotent (the first author/time are
  kept). Marking is allowed only from the day after the match date (Europe/Madrid);
  marking a `PLAYED` match, an undated match, or one dated today or later is
  rejected with `409` and the reason in `message`. Each calendar match row
  carries `overdueMarkable`, which the UI uses to show the mark action.
- `DELETE /api/v1/match/{id}/overdue-mark` (`matches:write`) clears the mark;
  the operation is idempotent and allowed on a `PLAYED` match.

A `SCHEDULED` match resolves, first rule wins, to: `PLAYED` (the match has been
played, a leftover mark ignored), `OVERDUE` (a manual mark exists), `POSTPONED`
(the match's round is below the group's current round), `UNDATED` (no date),
`OVERDUE` (the grace period, which starts counting the day after the match date,
has elapsed; with 7 days a match of the 3rd is overdue on the 11th; dates compared
in Europe/Madrid), `AWAITING_RESULT` (from the day after the match date while
still inside the grace period), or otherwise `UPCOMING`. All states are computed on read and
never stored; the only persisted calendar data is the manual overdue mark in
`match_overdue_mark`. The `matches:write` permission is granted to `ADMIN`
only.

# Considerations:

**JWT_SIGNING_SECRET**: The JWT signing secret is currently hardcoded in the `application.yml` file.
This is not secure for production environments. 
It is recommended to use environment variables or a secure secrets management system to store sensitive information like JWT secrets.

**User Roles**: The database table `app_user_role` is used to assign roles to users.

```sql

INSERT INTO app_user_role (user_id, role)
VALUES ('{existing user uuid}', 'ADMIN');

COMMIT;
```

The available roles are define in `UserRole` enum class in the backend code. The roles are:
`ADMIN`, `PRACTICIONER`, `CLUB_MANAGER`, `ANALYST`.

The existing users can be found in the `app_user` table.
```sql

SELECT * FROM app_user;

```

# Applying permissions

## Backend

Use Spring Security method authorization. `@EnableMethodSecurity` is already enabled, and 
`MyUserDetailsService` exposes permissions such as `clubs:write` as authorities:

```java
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorize("hasAuthority('clubs:write')")
public void updateClub(...) {
// protected functionality
}
```
For a role check:
```java
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(...) {
}
```
Prefer permissions for business capabilities and roles only for broad administrative checks. Backend checks are mandatory; frontend checks are only for UI convenience.
You can also protect URL patterns in `SecurityConfig`:
```java
.requestMatchers(HttpMethod.POST, "/api/v1/club/**")
    .hasAuthority("clubs:write")
```
## Frontend
The frontend already receives roles and derived permissions from `/api/v1/auth/me`. `AuthContext` exposes:
```typescript
const { hasPermission, hasRole } = useAuth()

if (!hasPermission('clubs:write')) {
return null
}
```
For routes, add the permission in `src/config/routes.js`:
```typescript
{ path: '/club-admin', auth: true, permission: 'clubs:write' }
```
`App.jsx` already applies `RequirePermission`, and the sidebar already hides items without the required permission. A user can still bypass frontend logic, so every protected operation must also be enforced on the backend.
