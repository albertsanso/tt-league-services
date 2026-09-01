# Build Plan

1. Confirm the overview statistics contract and product scope before
   implementation. Define whether the overview is source/season scoped or
   global/current-season only, which source and season selectors are exposed,
   whether deltas compare with the previous season or the selected scope, and
   which authentication permission protects the read operation.
2. Define immutable statistics read models and an application query in
   `tt-data-league-core-domain`, keeping source and season identity explicit
   wherever the selected scope requires it. The response must represent total
   players, clubs, matches, and the current season label/status without adding
   external identifiers to `FederatedClub` or `FederatedPlayer`.
3. Extend the existing repository ports and implement efficient aggregate
   queries in `tt-data-league-core-repository-jpa` for player, club, match, and
   season counts. Ensure counts use the documented relationships and
   source/season predicates, avoid unscoped name matching and N+1 loading, and
   update `docs/rfetm-datamodel.md` only if persistence mappings or query
   behavior change.
4. Add focused domain and JPA tests covering scope validation, source
   isolation, season selection, zero-result scopes, deterministic current
   season selection, delta calculation, and repository failures. Use the
   existing in-memory repository patterns where applicable.
5. Add an authenticated REST read endpoint matching the existing API versioning
   and controller conventions. Implement request/query validation, response
   DTOs and mappers, OpenAPI documentation, authorization, and explicit
   unauthorized or malformed-request errors. Keep the endpoint backed by the
   application query rather than duplicating counting logic in the controller.
6. Update
   `../../../../tt-data-league-frontend/src/hooks/useCommunityStats.js` to call the shared
   API client with the authenticated request and agreed scope parameters.
   Preserve normalization, abort handling, loading/error states, and the
   deterministic mock-data path controlled by `VITE_USE_MOCK_STATS`.
7. Update `../../../../tt-data-league-frontend/src/components/overview/CommunityStats.jsx`
   and the overview page to render backend values, selected-scope controls,
   current-season status, empty states, and authorization errors while
   preserving responsive layout, navigation, accessibility, and the existing
   Catalan/Spanish/English translation conventions.
8. Add focused REST and frontend tests for authenticated success, malformed and
   unauthorized requests, source/season scope changes, loading, empty, error,
   cancellation/stale responses, mock fallback, normalization, responsive
   rendering, and translation keys. Run the affected Maven module tests and
   the existing frontend test, lint, and production-build commands.

# Implementation Guidelines

- Keep dependencies directed inward: domain code must not depend on Spring or
  JPA, and runtime modules must wire the public query/API rather than duplicate
  statistics logic.
- Reuse existing repository ports, query-bus/application patterns, API client,
  authentication context, DTO mappers, error handling, translation resources,
  and shared loading/error components.
- Scope every aggregate by federation/source and season when those are part of
  the approved contract. Never infer source identity from an unscoped club or
  player name.
- Do not expose lazy JPA entities from the endpoint; return an immutable
  aggregate projection or mapped read model.
- Preserve the current mock behavior unless `VITE_USE_MOCK_STATS` is exactly
  `false`; real API failures must remain visible through the established error
  state and must not be silently treated as successful data.
- Do not change overview routing, authentication boundaries, or unrelated
  dashboard metrics.

# Notes

- The feature is intentionally `planned`, not `ready`, until the source/season
  scope, delta definition, endpoint version/path, and authorization permission
  are approved.
- The current frontend placeholder hook uses `GET /api/stats/community`,
  returns Catalan-shaped fields (`jugadors`, `clubs`, `partits`, `temporada`),
  and defaults to mock data unless `VITE_USE_MOCK_STATS=false`; the
  implementation should preserve compatibility or document an intentional
  contract migration.
- No GraphQL endpoint or new dependency is in scope unless explicitly added
  during scope approval.

## Implementation decisions (resolved during build)

- **Scope:** global/current-season only, aggregated across every `ImportSource`.
  The overview has no source or season selector today, so per-source/season
  filters were out of the minimal coherent contract; nothing prevents adding
  scoped variants later behind new query parameters.
- **Totals:** unique non-blank player and club names, compared case-insensitively
  across every source, plus the unique match row count.
- **Last imported season:** the most recent season string with at least one
  imported `Match`, across every source (`MatchRepository.findAllSeasons()`).
  When no match has a season, the season is `UNAVAILABLE`.
- **Endpoint:** `GET /api/v1/stats/community`, matching the existing
  versioned controller conventions (`StatsController`,
  `StatsOpenAPIv1Controller`), superseding the unversioned placeholder
  `GET /api/stats/community`. Response shape uses English field names
  (`players`, `clubs`, `matches`, `season`) for consistency with every other
  REST DTO in `tt-data-league-api-rest`; this is an intentional contract
  migration, not a compatibility shim.
- **Authorization:** `hasAuthority('clubs:read') and hasAuthority('players:read')
  and hasAuthority('matches:read')`, matching `RbacCatalog` permissions already
  granted to every role (`ADMIN`, `CLUB_MANAGER`, `ANALYST`, `PRACTITIONER`),
  since the statistics aggregate all three domains and the overview is a
  general landing page for any authenticated user. The existing
  `Permission.ANALYTICS_READ` was not reused because it is restricted to
  `ADMIN`/`ANALYST` and would have hidden the overview from `CLUB_MANAGER`
  and `PRACTITIONER` users.
- **Frontend:** `useCommunityStats` calls `getCommunityStatistics` (new
  `src/api/stats.js`) through the shared authenticated `apiRequest` client
  (token + `onUnauthorized` wired to `useAuth().clearSession`), uses the REST
  response by default, and only uses the explicit mock path when
  `VITE_USE_MOCK_STATS === 'true'`. The overview renders the backend totals
  and last imported season without placeholder deltas, and exposes an
  `unauthorized` flag (derived from a `401` `ApiError`) so
  `CommunityStats.jsx` can render `overview.statsUnauthorized` instead of the
  generic `overview.statsError`, plus a new `overview.statsEmpty` state when
  every total is zero. No new scope-selector UI was added (see Scope above).

## Implementation summary

- Domain: `FindCommunityStatisticsQuery`/`Handler` and
  `CommunityStatisticsReadModel` in
  `tt-data-league-core-domain/.../application/stats/find`; new default
  count/season methods on `FederatedPlayerRepository`, `FederatedClubRepository`,
  `MatchRepository`, `PlayerSeasonRepository`, `TeamRepository`.
- Persistence: JPA helper/query additions in `tt-data-league-core-repository-jpa`
  for the same repositories; no schema change, so `docs/rfetm-datamodel.md`
  was not updated.
- REST: `org.cttelsamicsterrassa.data.api.rest.stats` (`StatsController`,
  `StatsOpenAPIv1Controller`, `CommunityStatsDto`).
- Frontend: `src/api/stats.js`, updated `src/hooks/useCommunityStats.js` and
  `src/components/overview/CommunityStats.jsx`, new translation keys in
  `ca.js`/`en.js`/`es.js`.
- Tests added: `FindCommunityStatisticsQueryHandlerTest`,
  `CommunityStatisticsAggregateQueriesTest`, `StatsControllerTest`,
  `src/api/stats.test.js`, `src/hooks/useCommunityStats.test.jsx`.
- Validated: `mvn -pl tt-data-league-core-domain,tt-data-league-core-repository-jpa,tt-data-league-api-rest -am test`
  (all green), frontend `npm test`, `npm run lint`, `npm run build` (all
  green except one pre-existing, unrelated failure — see below).
- Known pre-existing, unrelated failures (present before this feature and
  left untouched per scope): `../../../../tt-data-league-frontend/src/config/routes.test.js`
  (`matches nested Club routes...`, caused by an already-modified
  `navigation.overview` translation in `ca.js`); `tt-data-league-import`
  (8 pre-existing processor-test failures); `tt-data-league-import-runtime`
  `AppTest` (`StackOverflowError`, pre-existing recursive test config issue).
