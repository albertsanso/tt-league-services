# TT League API runtime

This service provides the runtime API for the TT League application. 
It is built using Spring Boot and connects to a PostgreSQL database. 
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
- The run registry backing the async import endpoints is in-memory per JVM; it does not survive a restart or a multi-instance deployment. Run a single instance, or provide a persistent `ImportJobsPort`/run-registry adapter before scaling out.
- Run the process as a long-lived service (for example, a Windows service via NSSM, or a systemd unit on Linux) so it restarts on failure and on host reboot; there is no bundled service unit in this repository.
- Monitor `http://<host>:9090/actuator/health` for liveness once deployed.

ZIP import uploads accept files up to 100 MB by default. Override
`IMPORT_UPLOAD_MAX_FILE_SIZE` and `IMPORT_UPLOAD_MAX_REQUEST_SIZE` when a
different deployment limit is required; the request limit must be at least as
large as the file limit.

Import execution is configured server-side under `tt.league.import.execution`.
Club and player consolidation run in `WRITE` mode by default; use
`IMPORT_EXECUTION_CLUB_CONSOLIDATION` or
`IMPORT_EXECUTION_PLAYER_CONSOLIDATION` (`WRITE`, `REPORT`, or `disabled`) to
override them. The API start endpoint accepts only the stored import-resource
ID and never a client-supplied path.

The API start endpoint (`POST /api/v1/administration/import/start`) runs the import
asynchronously: it returns `202 Accepted` immediately with a run id and initial (`queued`)
status instead of waiting for the traversal to finish. Poll
`GET /api/v1/administration/import/process_status?runId=<uuid>` for progress (processed/total
counts, percentage when a reliable total is available, skipped/error counts) and the terminal
result. The run registry is in-memory per JVM (`InMemoryImportRunRegistry`); it prevents two
accepted runs for the same import resource but does not persist run history across restarts.

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
