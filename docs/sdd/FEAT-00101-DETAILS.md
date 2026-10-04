# Build Plan
1. **Permission (`tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/auth/user/model/`).** Add `IMPORTS_WRITE("imports:write")` to `Permission` and to
   `UserRole.ADMIN`'s set. Add `RbacCatalog.IMPORTS_WRITE`. Check `tt-data-league-frontend` for exhaustive permission
   lists (settings/users views) and add the label if one exists.
2. **Properties (`tt-data-league-api-rest/src/main/java/org/cttelsamicsterrassa/data/api/rest/config/security/ServiceCredentialProperties.java`, new).**
   `@ConfigurationProperties("security")` record holding `List<ServiceCredential> serviceCredentials`, each
   `name` (`[a-z0-9-]{1,40}`), `keySha256` (64 hex), `Set<String> permissions` (must be `Permission` values). Validate
   in the compact constructor and fail with a message naming the entry. Registered with
   `@EnableConfigurationProperties` on `SecurityConfig`. Unique names enforced.
3. **Filter (`tt-data-league-api-rest/src/main/java/org/cttelsamicsterrassa/data/api/rest/config/security/ServiceCredentialAuthenticationFilter.java`, new).** `OncePerRequestFilter`:
   - no `X-API-Key` header -> continue;
   - `X-API-Key` and `Authorization` both present -> 400 "use either a bearer token or an API key";
   - SHA-256 of the presented key compared with each configured hash using `MessageDigest.isEqual`; a match sets a
     `PreAuthenticatedAuthenticationToken` with principal `service:<name>` and authorities = permissions (no roles);
     no match -> 401. The key and its hash are never logged.
   - Registered in `SecurityConfig` before `JwtAuthenticationFilter`; `JwtAuthenticationFilter` already skips when an
     authentication is present.
4. **Authorization.** `ImportJobController` (FEAT-00100) uses `@PreAuthorize("hasAuthority('imports:write')")`, which
   still admits ADMIN users through the role's new permission. If FEAT-00100 is not merged yet, apply the same rule
   when it lands (dependency noted). Match read endpoints already require `matches:read`. `ImportResourceController`
   stays `hasRole('ADMIN')`.
5. **Runtime config (`tt-data-league-api-runtime/src/main/resources/application.yml`).** Document the
   `security.service-credentials` list with an example in the README only. The YAML has no entry, so nothing is enabled
   by default; operators add entries through `SECURITY_SERVICE_CREDENTIALS_0_NAME`, `..._KEY_SHA256` and
   `..._PERMISSIONS` environment variables (Spring relaxed binding).
6. **Tests (`tt-data-league-api-rest/src/test/java/.../config/security/`).**
   `ServiceCredentialPropertiesTest` (each malformed entry, duplicate names); `ServiceCredentialAuthenticationFilterTest`
   (match, mismatch, both headers, no header); a `@WebMvcTest`-style security test over a jobs endpoint and a match read
   endpoint: service key with `imports:write` allowed on jobs and forbidden on `/api/v1/administration/settings/**`;
   key without `matches:read` forbidden on `/api/v1/match/**`; ADMIN JWT still allowed on jobs.
7. **Docs.** `tt-data-league-api-runtime/README.md`: service credential section (generate a random key, compute
   `sha256`, configure the variables, send `X-API-Key`). `tt-league-ingest/README.md`: note that `TT_LEAGUE_API_TOKEN`
   still needs an ADMIN JWT for `/import/upload`; service keys are for the jobs API.
8. **Validation.** `mvn -pl tt-data-league-api-runtime -am test`, then the full `mvn test`.

## Acceptance Criteria

- [ ] Service credentials are configured from the environment as `security.service-credentials` entries (`name`, `key-sha256`, `permissions`); a presented `X-API-Key` is hashed and compared in constant time
- [ ] A new `imports:write` permission is granted to the `ADMIN` role and to service credentials that list it; the import jobs endpoints accept `imports:write` and the match read endpoints keep `matches:read`
- [ ] A valid service credential authenticates as `service:<name>` with only its configured permissions; an invalid key is a 401, and a request with both `Authorization` and `X-API-Key` is a 400
- [ ] Startup fails clearly on a malformed entry (blank name, hash that is not 64 hex characters, unknown permission); no credential is configured by default
- [ ] User JWT authentication and existing role checks are unchanged
- [ ] Security tests cover allowed, forbidden, invalid-key and ambiguous-header cases; `tt-data-league-api-runtime/README.md` documents the configuration and how to generate a key and its hash

# Implementation Guidelines

- Never commit keys or hashes of real keys. No default credential.
- Do not weaken existing role checks on user endpoints. Service principals get permissions only, never roles.
- Storing only the SHA-256 of the key keeps the raw secret out of configuration files and process listings.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal item 5. Today `TT_LEAGUE_API_TOKEN` must hold a user JWT, which expires after 30 hours by default.

## Planning notes (2026-10-04)

- Platform JWTs carry `roles` and `permissions` claims but are validated against the user table and an in-memory
  blacklist, so a long-lived user token is not a good machine credential. That is why a separate key mechanism is used.
- `ImportResourceController` is class-level `hasRole('ADMIN')`; the service credential is deliberately limited to
  the new jobs API (FEAT-00100) and read endpoints.
- Follow-up (not in scope): let the ingest upload stage send `X-API-Key` and accept `imports:write` on
  `/import/upload` if unattended CLI uploads are still needed after the orchestrator exists.
