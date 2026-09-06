# TT League API runtime

This service provides the runtime API for the TT League application. 
It is built using Spring Boot and connects to a PostgreSQL database. 
The configuration for the service, including database connection details, JWT settings, mail server settings, and multipart upload limits, can be found in the `application.yml` file.

ZIP import uploads accept files up to 100 MB by default. Override
`IMPORT_UPLOAD_MAX_FILE_SIZE` and `IMPORT_UPLOAD_MAX_REQUEST_SIZE` when a
different deployment limit is required; the request limit must be at least as
large as the file limit.

Import execution is configured server-side under `tt.league.import.execution`.
Club and player consolidation run in `WRITE` mode by default; use
`IMPORT_EXECUTION_CLUB_CONSOLIDATION` or
`IMPORT_EXECUTION_PLAYER_CONSOLIDATION` (`WRITE`, `REPORT`, or `disabled`) to
override them. RFETM team consolidation uses
`C:\tt-repository\import-rfetm\teams` by default; override it with
`IMPORT_EXECUTION_RFETM_TEAMS_FOLDER` when appropriate. The API start endpoint
accepts only the stored import-resource ID and never a client-supplied path.

The API start endpoint (`POST /api/v1/administration/import/start`) runs the import
asynchronously: it returns `202 Accepted` immediately with a run id and initial (`queued`)
status instead of waiting for the traversal to finish. Poll
`GET /api/v1/administration/import/process_status?runId=<uuid>` for progress (processed/total
counts, percentage when a reliable total is available, skipped/error counts) and the terminal
result. The run registry is in-memory per JVM (`InMemoryImportRunRegistry`); it prevents two
accepted runs for the same import resource but does not persist run history across restarts.

At startup, `ImportFolderSettingStartupInitializer` ensures the `IMPORT/import-folder`
administrator setting exists, creating it with the default value `c:\tt-repository`
only when it is absent. Provisioning is idempotent: it never overwrites an
administrator's configured value, and a persistence failure during startup
fails application boot instead of leaving the setting unconfigured. Import
workflows resolve this persisted setting when no explicit folder is supplied;
administrators can view and change it through the System settings panel, and
the configured path must exist as a directory at import execution time.

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
