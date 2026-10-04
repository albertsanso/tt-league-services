# Build Plan
Paths below are relative to `tt-data-league-api-rest/src/main/java/org/cttelsamicsterrassa/data/api/rest/` (shown as
`rest/`) and `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/` (shown as `domain/`)
unless a module is named.

1. **Permission (`domain/auth/user/model/Permission.java`, `UserRole.java`).** Add `IMPORTS_WRITE("imports:write")`
   to `Permission` and add it to `UserRole.ADMIN`'s set only. `User.getPermissions()` derives permissions from roles,
   and `MyUserDetailsService` reloads authorities on every request, so ADMIN users get `imports:write` without a data
   migration or re-login. There is no permission table, so `rfetm-datamodel.md` does not change.
2. **Catalog (`rest/config/security/RbacCatalog.java`).** Add `IMPORTS_WRITE = "imports:write"` next to the other
   permission constants, plus `public static Optional<Permission> permission(String value)`, which looks up a
   `Permission` by its `value()` and sits next to the existing `role(String)` helper. Step 3 uses it to validate
   configured permissions.
3. **Properties (`rest/config/security/ServiceCredentialProperties.java`, new).**
   `@ConfigurationProperties("security")` record `ServiceCredentialProperties(List<Entry> serviceCredentials)` with
   nested record `Entry(String name, String keySha256, Set<String> permissions)`. It binds next to the existing
   `security.jwt.*` and `security.password-recovery.*` keys, which it ignores. The compact constructors:
   - turn a missing list into `List.of()`, so no credential is configured by default;
   - reject a name that does not match `[a-z0-9-]{1,40}` (this covers a blank name);
   - reject a `keySha256` that is not exactly 64 hex characters, normalise it to lower case, and expose the decoded
     bytes through `byte[] keyHash()` (a defensive copy, built with `HexFormat.of().parseHex`);
   - reject an empty or missing `permissions` set and any value for which `RbacCatalog.permission` is empty;
   - reject duplicate names and duplicate hashes across entries.
   Each failure throws `IllegalArgumentException` with the entry index and name (never the hash), for example
   `security.service-credentials[0] (orchestrator): unknown permission 'imports:wrte'`. Spring wraps it in a binding
   failure, so the application does not start. Register the record with
   `@EnableConfigurationProperties(ServiceCredentialProperties.class)` on `SecurityConfig`.
4. **Filter (`rest/config/security/ServiceCredentialAuthenticationFilter.java`, new).** `OncePerRequestFilter` built
   from `ServiceCredentialProperties` through its constructor. It is not a `@Component` or `@Bean`, because Spring Boot
   would also register a filter bean as a servlet filter outside the security chain.
   - `OPTIONS` requests and requests without an `X-API-Key` header continue unchanged.
   - `X-API-Key` together with `Authorization` -> `response.sendError(400, "Use either a bearer token or an API key,
     not both")`; the chain stops.
   - Otherwise, hash the UTF-8 bytes of the header value with SHA-256 and compare the digest with every configured
     `keyHash()` using `MessageDigest.isEqual`, checking every entry instead of stopping at the first match, so the
     timing does not reveal which entry matched. Then:
     - on a match, set a `PreAuthenticatedAuthenticationToken` whose principal is `service:<name>` and whose
       authorities are one `SimpleGrantedAuthority` per configured permission, with no `ROLE_*` authority, and
       continue;
     - with no match, clear the context and `sendError(401, "Invalid API key")`. This includes the case where no
       credentials are configured.
   - Never log the presented key, its digest or the configured hashes. A debug log line may include the principal
     name.
5. **Wiring (`rest/config/security/SecurityConfig.java`).** Inject `ServiceCredentialProperties`. In
   `filterChain`, keep `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)` and then add
   `addFilterBefore(new ServiceCredentialAuthenticationFilter(properties), JwtAuthenticationFilter.class)`, so the
   ambiguity check runs before the JWT filter parses `Authorization`. `JwtAuthenticationFilter` stays unchanged: with
   no `Authorization` header it finds no username and passes the request on. Leave the URL rules as they are:
   `GET /api/v1/match/**` already requires `matches:read`, and `/api/v1/administration/settings/**` keeps
   `hasRole('ADMIN')`, which a service principal never has.
6. **Authorization (`rest/importjob/ImportJobController.java`).** Change the class-level
   `@PreAuthorize("hasRole('ADMIN')")` to `@PreAuthorize("hasAuthority('imports:write')")`. ADMIN users still pass
   through the permission granted in step 1. `submit` already passes `authentication.getName()` as `requestedBy`, so
   service-submitted jobs record `service:<name>`, and nothing else changes. `ImportResourceController` (`/upload`,
   preview, start) and `SettingsController` stay `hasRole('ADMIN')`.
7. **Frontend labels (`tt-data-league-frontend/src/i18n/{ca,en,es}.js`).** Add an `imports:write` entry next to
   `analytics:read` in each file's `usersAdmin.permission` map, so `UsersRolesPage` shows a translated label for the
   ADMIN role:
   `Importar dades` / `Write imports` / `Importar datos`. The frontend has no other logic change.
8. **Tests (`tt-data-league-api-rest/src/test/java/org/cttelsamicsterrassa/data/api/rest/config/security/`).**
   No new test dependency: `spring-boot-starter-test` already brings `spring-test`/MockMvc and Mockito. Test keys
   are generated in the test (a random string plus its SHA-256 computed in the test), never committed literals of
   real keys.
   - `ServiceCredentialPropertiesTest`: valid entry (upper-case hash normalised, `keyHash()` decoded); missing list
     -> empty; blank name, invalid name characters, a 63-character hash, a non-hex hash, empty permissions, unknown
     permission, duplicate name and duplicate hash each fail with a message naming the entry and without the hash.
   - `ServiceCredentialAuthenticationFilterTest` (`MockHttpServletRequest`/`MockHttpServletResponse`/`MockFilterChain`):
     no header -> chain called and context untouched; valid key -> chain called with principal `service:orchestrator`
     and exactly the configured authorities, none starting with `ROLE_`; wrong key -> 401 and chain not called;
     no configured credentials with a key -> 401; `X-API-Key` + `Authorization` -> 400 and chain not called;
     `OPTIONS` with a wrong key -> chain called.
   - `ServiceCredentialSecurityIntegrationTest` (`@SpringJUnitWebConfig` with a nested `@Configuration
     @EnableWebMvc` importing `SecurityConfig`, the real `ImportJobController`, and a test-only `@RestController` with
     `GET /api/v1/match/probe` and `GET /api/v1/administration/settings/probe`). Use Mockito beans for `QueryBus`,
     `CommandBus`, `JwtService`, `UserDetailsService` and `TokenBlacklistService`, define `ServiceCredentialProperties`
     as a bean, and build MockMvc with `webAppContextSetup(context).addFilters(springSecurityFilterChain)`. Cases:
     - key with `imports:write` -> `GET /api/v1/administration/import/jobs` is 200;
     - the same key -> `GET .../settings/probe` is 403;
     - key with only `imports:write` -> `GET /api/v1/match/probe` is 403;
     - key with `matches:read` -> `GET /api/v1/match/probe` is 200;
     - key with only `matches:read` -> jobs endpoint is 403;
     - wrong key -> 401;
     - key + bearer -> 400;
     - mocked ADMIN JWT (authorities from `UserRole.ADMIN`) -> jobs endpoint is 200;
     - mocked CLUB_MANAGER JWT -> jobs endpoint is 403;
     - no credentials at all -> jobs endpoint is 403, unchanged from today's default entry point.
   - Update `ImportJobControllerTest` only if its fixtures need the new authority. It calls the controller directly,
     so it should not need to change.
9. **Docs.**
   - `tt-data-league-api-runtime/README.md`:
     - In the "Import jobs API (FEAT-00100)" section, replace "requires the `ADMIN` role" with "requires the
       `imports:write` permission (ADMIN users or a service credential)".
     - Add a "Service credentials (FEAT-00101)" section covering:
       - generating a key, for example `openssl rand -base64 32`, and computing its hash with
         `printf %s "$KEY" | sha256sum`;
       - the YAML form under `security.service-credentials`;
       - the environment-variable form. Spring relaxed binding removes dashes, so the names are
         `SECURITY_SERVICECREDENTIALS_0_NAME`, `SECURITY_SERVICECREDENTIALS_0_KEYSHA256` and
         `SECURITY_SERVICECREDENTIALS_0_PERMISSIONS=imports:write,matches:read`;
       - sending the key as `X-API-Key`, and the 400/401/403 behaviour;
       - the fact that service principals never hold roles, so ADMIN-role endpoints stay closed to them;
       - rotation: add a second entry, switch the client, then remove the old entry.
     - Add `imports:write` to the "Applying permissions" guidance where the role/permission split is described.
   - `tt-data-league-api-runtime/src/main/resources/application.yml`: add no entry, only a comment under `security:`
     that points to the README section.
   - `tt-league-ingest/README.md`: in the `TT_LEAGUE_API_TOKEN` row, note that `/import/upload` still needs an ADMIN
     JWT and that service credentials apply to the jobs API.
10. **Validation.** `mvn -pl tt-data-league-api-runtime -am test`, then the full `mvn test` from the repository root.
    `tt-data-league-frontend` is in the reactor, so `mvn test` also runs its tests after the label change. Review the
    diff for committed keys or hashes.

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
- Keep the mechanism inside `tt-data-league-api-rest` security config. Domain change is limited to the new
  `Permission` value and its ADMIN grant. No persistence, no key table, no admin UI for credentials.
- Do not register the filter as a Spring bean; construct it inside `SecurityConfig` so it runs only in the security
  chain.
- Error responses go through `response.sendError`, like `JwtAuthenticationFilter`. Do not echo the presented key.
- Out of scope: `/import/upload`, preview and start endpoints (still ADMIN role), GraphQL/MCP access, the
  orchestrator-side client (FEAT-00104), and the `round-progress` endpoint itself (FEAT-00102, which only needs
  `matches:read`).

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

## Plan rebuild (2026-10-04)

- Rebuilt after FEAT-00100 landed (`44b36f5`). `ImportJobController` exists and is class-level
  `hasRole('ADMIN')`; step 6 now names the exact annotation change and drops the "if FEAT-00100 is not merged"
  branch. `Depends on` is set to FEAT-00100.
- Fixed the environment variable names. Spring relaxed binding removes dashes, so the names are
  `SECURITY_SERVICECREDENTIALS_0_KEYSHA256` (not `SECURITY_SERVICE_CREDENTIALS_0_KEY_SHA256`).
- Fixed the plan's assumption that `@WebMvcTest` applies: `tt-data-league-api-rest` has no
  `@SpringBootConfiguration` and no `spring-security-test` dependency. The integration test now uses
  `@SpringJUnitWebConfig` with MockMvc plus the security filter chain, so no new dependency is needed.
- Decisions: the filter is constructed inside `SecurityConfig` rather than declared as a bean, to avoid servlet
  auto-registration. An entry without permissions and duplicate hashes are also rejected at startup. Permission lookup
  goes through a new `RbacCatalog.permission(String)` helper. The `imports:write` label is added to the three
  `tt-data-league-frontend` i18n files because `UsersRolesPage` lists ADMIN permissions.
- Checked: `/api/v1/user/me` and `/api/v1/auth/me` return 401 for a principal without a user row, so the
  `authenticated()` user routes do not leak data to a service principal.
- Acceptance criteria unchanged.

## Implementation notes (2026-10-04)

- Implemented per the rebuilt plan. `Permission.IMPORTS_WRITE` is granted to `ADMIN` only; `ImportJobController` now
  requires `hasAuthority('imports:write')`. `ServiceCredentialProperties` and `ServiceCredentialAuthenticationFilter`
  are new in `tt-data-league-api-rest`, wired in `SecurityConfig` ahead of `JwtAuthenticationFilter`.
- Validation: new tests `ServiceCredentialPropertiesTest` (includes binding from the documented
  `SECURITY_SERVICECREDENTIALS_0_*` environment variables), `ServiceCredentialAuthenticationFilterTest`,
  `ServiceCredentialSecurityIntegrationTest` (real filter chain; allowed, forbidden, invalid-key, ambiguous-header,
  anonymous, ADMIN JWT and CLUB_MANAGER JWT cases) and `RbacCatalogTest` all pass. `mvn test` from the repository root
  passes in every module except one test.
- Known unrelated failure at HEAD: `BcnesaImportProcessorsTest.storesTheSetScoresOfEveryGameFromTheHtmlBasedActas`
  (`tt-data-league-import`) needs `acta_bcnesa_2026_published.json`, which is not tracked in git (only
  `acta_bcnesa_2026_placeholder_4_4.json` and `acta_bcnesa_2026_unpublished.json` are). Not touched by this feature.
- Error messages for an invalid name or hash identify the entry by name (the name may itself be the invalid value);
  they never include the hash.
