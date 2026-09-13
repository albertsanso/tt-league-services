# tt-data-league-api-mcp

Read-only Model Context Protocol (MCP) tool layer over the table-tennis league
data. This module contains no runnable application by itself: its `@Tool`
beans are picked up by `tt-data-league-api-runtime`, which hosts the MCP
server alongside the existing REST API.

## Exposed tools

Every tool is read-only and delegates to the existing `QueryBus` application
queries — no new domain behavior is introduced.

| Bean | Tools | Backing queries |
|---|---|---|
| `ClubMcpTools` | `findClubById`, `findClubDetailsById`, `findClubCompetitionDetails`, `findClubsByStringInName` | `FindFederatedClubByIdQuery`, `FindClubDetailsQuery`, `FindFederatedClubCompetitionDetailsQuery`, `FindClubsByStringInNameQuery` |
| `PlayerMcpTools` | `findPlayersByStringInName`, `findPlayerDetailsById` | `FindFederatedPlayersByStringInNameQuery`, `FindPlayerDetailsQuery` |
| `MatchMcpTools` | `searchMatches`, `details` | `SearchMatchesQuery`, `FindMatchDetailsQuery` |
| `StatsMcpTools` | `getCommunityStatistics` | `FindCommunityStatisticsQuery` |
| `SearchMcpTools` | `searchGlobal` (grouped by entity type) | `FindFederatedPlayersByStringInNameQuery`, `FindClubsByStringInNameQuery`, `FindMatchesByStringInNameQuery` |

## Running and connecting

The MCP server runs inside the `tt-data-league-api-runtime` Spring Boot
process (see that module's `application.yml`), using Spring AI's SSE-based
WebMvc transport: clients open `GET /mcp/sse` to get a session-scoped
message endpoint, then `POST` JSON-RPC messages to
`/mcp/message?sessionId=...`. The `/mcp/**` path is unauthenticated
(`permitAll` in `tt-data-league-api-rest`'s `SecurityConfig`) — access is
controlled instead by whether the server is enabled at all, via the
`spring.ai.mcp.server.enabled` property.

That property is profile-gated:

- `dev` profile (`application-dev.yml`): `enabled: true`.
- `prod` profile (`application-prod.yml`): `enabled: false`.
- No profile active: defaults to `false` (see `application.yml`'s
  `MCP_SERVER_ENABLED` env var override), so the server stays off unless a
  profile or the env var turns it on.

To connect an MCP client (e.g. Claude Desktop or Claude Code) for local
development, start the runtime with `SPRING_PROFILES_ACTIVE=dev` and point
the client at the SSE endpoint, for example:

```json
{
  "mcpServers": {
    "tt-data-league": {
      "url": "http://localhost:8080/mcp/sse"
    }
  }
}
```
