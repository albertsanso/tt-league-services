# Build Plan

> Draft outline.

1. `tt-data-league-api-rest` security: a service-key authentication filter that builds an authentication with
   the configured permissions, registered in `SecurityConfig` beside `JwtAuthenticationFilter`.
2. Configuration properties in `tt-data-league-api-runtime` (`security.service-credentials[*]`), environment-driven.
3. Update `tt-league-ingest` upload client docs (`TT_LEAGUE_API_TOKEN` can carry the service key).
4. Tests and README.

## Acceptance Criteria

- [ ] A service credential (API key presented as `Authorization: ApiKey <key>` or `X-API-Key`) is configured from the environment, with an explicit permission set (`imports:write`, `matches:read`)
- [ ] Requests with a valid service credential reach the import jobs and calendar/round-progress endpoints and nothing outside their permissions
- [ ] Keys are compared in constant time, stored only as configuration (never logged), and an invalid or missing key is a 401
- [ ] User JWT authentication is unchanged; startup fails clearly when a configured service credential is malformed
- [ ] Security tests cover allowed, forbidden and invalid-key cases; the api-runtime README documents the configuration

# Implementation Guidelines

- Never commit keys. No default key. Do not weaken existing role checks on user endpoints.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal item 5. Today `TT_LEAGUE_API_TOKEN` must hold a user JWT, which expires after 30 hours by default.
