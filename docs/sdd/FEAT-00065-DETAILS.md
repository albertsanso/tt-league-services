# Build Plan
1. Add a new Maven module `tt-data-league-api-mcp` (sibling of
   `tt-data-league-api-rest`), registered in the root `pom.xml` `<modules>`
   list and given a `dependencyManagement` entry alongside the other
   `tt-data-league-*` artifacts. It depends only on `tt-data-league-core-domain`
   (for `QueryBus`/`DomainQueryResponse` and the existing `Find*Query` /
   `SearchMatchesQuery` classes) plus `commons-core` and
   `querybus-synchronous-inmemory` — not on `tt-data-league-api-rest`, so the
   MCP tool layer stays independent of REST/web-security concerns and cannot
   depend outward on an API-specific module.

2. Add the Spring AI MCP server starter to the root `dependencyManagement`
   (`org.springframework.ai:spring-ai-starter-mcp-server-webmvc`, using the
   Spring AI BOM version compatible with Spring Boot 3.5.8) and as a
   dependency of `tt-data-league-api-mcp`.

3. In `tt-data-league-api-mcp`, add one `@Component` "tool" class per domain
   area, each exposing read-only `@Tool`-annotated methods that push the
   existing application queries through `QueryBus` and map the returned read
   models to small local record DTOs (mirroring the `ClubDto`/`MatchDto`/
   `PlayerDto`/`CommunityStatsDto` mapping style already used in
   `tt-data-league-api-rest`, duplicated here rather than shared, since the
   two modules must not depend on each other):
   - `ClubMcpTools`: club lookup by id (`FindFederatedClubByIdQuery`), club
     details (`FindClubDetailsQuery`), club competition details
     (`FindFederatedClubCompetitionDetailsQuery`), and name search
     (`FindClubsByStringInNameQuery`).
   - `PlayerMcpTools`: player details (`FindPlayerDetailsQuery`) and name
     search (`FindFederatedPlayersByStringInNameQuery`).
   - `MatchMcpTools`: match details (`FindMatchDetailsQuery`) and match search
     (`SearchMatchesQuery`, reusing its existing criteria object).
   - `StatsMcpTools`: community statistics (`FindCommunityStatisticsQuery`).
   - `SearchMcpTools`: global search across players/clubs/matches, matching
     `GlobalSearchController`'s three underlying queries.
   Every tool method must be read-only (query-side only); no `CommandBus`
   usage or write-capable tools in this module.

4. Register the tool beans with the MCP server using Spring AI's
   `MethodToolCallbackProvider` (one `@Bean` in a
   `McpToolsConfiguration` class in `tt-data-league-api-mcp`), so the
   auto-configured MCP server picks up all `@Tool` methods on the beans
   listed above.

5. Wire the module into the existing runtime instead of creating a second
   deployable: add `tt-data-league-api-mcp` as a dependency of
   `tt-data-league-api-runtime`, alongside `tt-data-league-api-rest`, so a
   single Spring Boot process serves both the REST API and the MCP endpoint.
   Configure the MCP server transport (`spring.ai.mcp.server.*` in
   `application.yml`) to expose the streamable-HTTP/SSE endpoint at a
   distinct base path (e.g. `/mcp`) so it does not collide with existing
   REST routes.

6. Extend `SecurityConfig` (in `tt-data-league-api-rest`, already wired into
   `tt-data-league-api-runtime`) to require the same authentication as the
   REST API for the `/mcp/**` path (reuse the existing JWT filter), keeping
   authorization consistent with the `clubs:read` / `players:read` /
   `matches:read` authorities already enforced on the equivalent REST
   endpoints. Do not expose the MCP endpoint unauthenticated.

7. Add focused unit tests in `tt-data-league-api-mcp/src/test` for each tool
   class, asserting the correct query is pushed on `QueryBus` and the
   response is mapped to the expected DTO shape, and an integration test in
   `tt-data-league-api-runtime` (or `tt-data-league-api-mcp` with a minimal
   Spring context) verifying the MCP server starts and lists the expected
   tool names.

8. Add a `README.md` to `tt-data-league-api-mcp` documenting the exposed
   tools, the transport/endpoint path, and how to connect an MCP client
   (e.g. Claude Desktop/Code) to the running runtime for local development.
   Update `tt-data-league-api-runtime/README.md` if it documents the set of
   exposed endpoints/capabilities.

# Implementation Guidelines

- Keep the MCP tool layer read-only; do not add `@Tool` methods backed by
  `CommandBus` commands (club consolidation, name updates, user/settings
  writes) as part of this feature.
- Do not let `tt-data-league-api-mcp` depend on `tt-data-league-api-rest`;
  duplicate the minimal DTO mapping instead of introducing a shared module,
  per the existing inward-dependency rule in the root `AGENTS.md`.
- Reuse existing `Find*Query`/`SearchMatchesQuery` classes and their handlers
  unchanged; this feature only adds a new delivery mechanism, not new
  application/domain behavior.
- The MCP endpoint (`/mcp/**`) is unauthenticated (`permitAll` in
  `SecurityConfig`) by explicit user decision, superseding the original
  authenticated design. Access control is via
  `spring.ai.mcp.server.enabled` instead: keep it off by default and only
  on under the `dev` profile — never make it default-on outside `dev`.
- Every `@Tool` method name must be unique across every tool bean registered
  with the same `MethodToolCallbackProvider` (Spring AI fails startup with
  "Multiple tools with the same name" otherwise) — set an explicit `@Tool(name
  = "...")` whenever two tool classes would otherwise share a method name
  (e.g. `MatchMcpTools.search` vs `SearchMcpTools.search`).
- Use Java 21, four-space indentation, and the existing package root
  (`org.cttelsamicsterrassa.data.core` for domain reuse,
  `org.cttelsamicsterrassa.data.api.mcp` for the new module's own classes).

# Notes

- Implemented as planned: `tt-data-league-api-mcp` depends only on
  `tt-data-league-core-domain` plus the query bus and Spring AI's
  `spring-ai-starter-mcp-server-webmvc` (pinned via `spring-ai-bom:1.0.0` in
  the root `dependencyManagement`); it does not depend on
  `tt-data-league-api-rest`.
- Resolved the two open questions from planning:
  - Detail level: tools reuse the same full detail level as the REST DTOs
    (including `MatchDetailDto`'s lineups/games/sets/form), duplicated
    verbatim into the `mcp` package rather than trimmed, for consistency
    with the rest of the module.
  - Spring AI version: pinned to `1.0.0` (GA), compatible with Spring Boot
    3.5.8; resolves cleanly from Maven Central.
- Tool method names must be unique across all registered tool beans:
  `MatchMcpTools.search` and `SearchMcpTools.search` collided at startup
  ("Multiple tools with the same name (search)"); renamed to `searchMatches`
  and `searchGlobal` via explicit `@Tool(name = ...)`.
- The root `tt-data-league-services` parent POM had a stale copy installed in
  the local `.m2` repository (pre-dating this feature) that other modules'
  dependency resolution fell back to when built outside the full reactor;
  `mvn -N install` at the repository root refreshed it. Anyone building a
  single module standalone (e.g. `cd tt-data-league-api-runtime && mvn ...`)
  after pulling this change should do the same if they hit a "version is
  missing" error for `tt-data-league-api-mcp`'s dependencies.
- Verified locally: `mvn -pl tt-data-league-api-mcp -am test` (15 new unit
  tests, all passing) and `mvn -pl tt-data-league-api-rest test` (unaffected
  by the `SecurityConfig` change). Booted
  `tt-data-league-api-runtime` with `spring-boot:run` against a local
  Postgres — logs confirm `Registered tools: 10` and an unauthenticated
  `POST /mcp` correctly returns `403 Forbidden`.
- Pre-existing, unrelated: `tt-data-league-core-repository-jpa` and
  `tt-data-league-import` have local test failures on this machine
  independent of this change (reproduced identically on `main` before this
  feature's commits), likely due to missing local Testcontainers/Docker
  support in this environment.

## Follow-up: unauthenticated access + profile-gated enable/disable

- Per explicit user request, reversed the authentication decision: `/mcp/**`
  is now `permitAll` in `SecurityConfig` (the earlier `AuthorizationManagers
  .allOf(...)` rule requiring `clubs:read`/`players:read`/`matches:read` was
  removed). Access is controlled instead by whether the server is enabled at
  all.
- Added `spring.ai.mcp.server.enabled` to `tt-data-league-api-runtime`'s
  `application.yml`, defaulting to `${MCP_SERVER_ENABLED:false}` when no
  profile is active. Added `application-dev.yml` (`enabled: true`) and
  `application-prod.yml` (`enabled: false`) — these are the first Spring
  profile files in this module; there was no prior profile convention to
  align with.
- Discovered while wiring this up: the pinned `spring-ai` version (`1.0.0`
  GA) does **not** have the `base-path`/`streamable-http.mcp-endpoint`
  properties used in the original `application.yml` — those exist only in
  later Spring AI releases. On 1.0.0 the WebMvc transport is SSE-based, with
  its own property names: `spring.ai.mcp.server.sse-endpoint` (default
  `/sse`) and `sse-message-endpoint` (default `/mcp/message`). Fixed
  `application.yml` to set `sse-endpoint: /mcp/sse` and
  `sse-message-endpoint: /mcp/message` so both live under `/mcp`. The earlier
  "verified `Registered tools: 10`" note above only checked tool
  registration and the security 403, not that the transport endpoint was
  actually reachable — the original property names were silently ignored by
  Spring Boot's relaxed binding (unknown properties, not a name that maps to
  anything), which masked the mismatch. A future rebase onto a Spring AI
  version with streamable-HTTP support would need to switch these properties
  again.
- Re-verified end-to-end: under `dev` profile, `GET /mcp/sse` returns `200`
  with a session-scoped `event: endpoint` pointing at `/mcp/message
  ?sessionId=...`, and `POST /mcp/message` (no auth header) reaches the MCP
  transport (protocol-level 400 "Session ID missing" when called without a
  session, never a 403). Under `prod` profile, both endpoints return `404`
  and no `McpServerAutoConfiguration` log lines appear at startup — the
  server is fully disabled, not just hidden behind auth.
