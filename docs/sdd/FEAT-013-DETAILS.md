# Build Plan

## 1. Reproduce and catalogue the remaining detail-page defects

1. Review the current player-detail route, API normalization, read models, and
   REST DTOs to identify discrepancies between the documented FEAT-011/FEAT-012
   contract and the behavior currently presented to users.
2. Reproduce each defect with source-, season-, and competition-scoped data,
   including all-source/all-season selections, players with no history, and
   records with unavailable values.
3. Record the expected result for identity, registrations, clubs,
   competitions, matches, statistics, navigation, and each loading/error state
   before changing implementation code.

## 2. Correct the backend player-detail contract

1. Update the framework-light player read models and query handlers under
   `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/application/player/find/`
   so every returned collection and statistic uses explicit canonical-player,
   source, season, and competition context.
2. Update the REST player controller and DTO/mapping classes under
   `tt-data-league-api-rest/src/main/java/org/cttelsamicsterrassa/data/api/rest/player/`
   to return stable fields for identity, registrations, related clubs and
   competitions, match history, and statistics.
3. Preserve UUID-based canonical `Player` navigation and source-scoped
   `FederatedPlayer` context. Keep unlinked federated records distinct and
   never fabricate a canonical detail identifier.
4. Surface invalid identifiers, missing players, unauthorized requests,
   invalid filter combinations, unavailable statistics, and unavailable match
   scores explicitly through the existing API error conventions.
5. Change persistence queries only when required by the contract, under
   `tt-data-league-core-repository-jpa`; preserve season registrations and all
   match, lineup, and historical references. Update
   `docs/rfetm-datamodel.md` only if mappings or schema behavior change.

## 3. Fix frontend rendering and navigation

1. Update `tt-data-league-frontend/src/api/players.js` and the existing player
   hooks to normalize the corrected response shape at the API boundary and
   reject malformed values rather than rendering misleading defaults.
2. Update
   `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx` so the displayed
   identity and related sections always match the selected player and active
   source, season, and competition filters.
3. Keep filter state URL-persisted and interdependent: changing source resets
   incompatible season and competition values, changing season resets
   incompatible competitions, and direct or refreshed URLs resolve to valid
   selections without silently mixing scopes.
4. Ensure the chart, statistics table, and match-history table consume the same
   filtered data. Keep all chart modes, legends, labels, textual alternatives,
   unavailable scores, and responsive behavior consistent. The connected
   scatter mode must render one combined plot: each selected season contributes
   to the unfilled line series on the shared plot, never a separate plot per
   season or a filled area between points. The plot must be compact, its labels
   must match the surrounding UI typography, its series lines must be thin, and
   its aspect ratio must remain proportional without stretched or cropped
   labels.
5. Preserve accessible Catalan loading, empty, error, unauthorized, and
   not-found states, with semantic controls, usable keyboard focus, and a
   clear route back to player search.
6. Keep route metadata and shared navigation in the existing frontend
   configuration; do not duplicate route definitions in the page.

## 4. Add regression coverage and verify the complete flow

1. Add focused domain and persistence tests for canonical identity,
   source/season/competition filtering, complete aggregation, unlinked
   records, unavailable statistics, and preservation of historical references.
2. Extend REST tests for response shape, invalid identifiers, authorization,
   not-found behavior, filter combinations, null scores, and explicit
   unavailable-statistics responses.
3. Extend frontend API/page coverage for normalization failures, URL filter
   synchronization, chart and table updates, accessible alternatives, and all
   loading/empty/error states.
4. Run `npm run lint`, `npm run build`, the affected Maven module tests, and the
   full reactor with `mvn test`.
5. Manually verify search-to-detail navigation, direct and refreshed detail
   URLs, duplicate names across sources, unlinked results, all-source and
   all-season views, competition filtering, missing scores, and representative
   mobile and desktop layouts.

# Implementation Guidelines

- Keep dependencies directed inward: domain read models and ports remain
  framework-light, JPA remains an adapter, REST owns DTO mapping and
  authorization, and the frontend consumes HTTP DTOs only.
- Do not add `externalId` properties to `Player` or `FederatedPlayer`; source
  identifiers and licences remain in source- or season-registration context.
- Never resolve or merge players by an unscoped name when source identity
  matters. Preserve `PlayerSeason`, `Team`, `Match`, and `Lineup` identity.
- Reuse the existing query handlers, API client, route helpers, hooks, UI
  primitives, and CSS layers before adding abstractions or dependencies.
- Keep invalid or unavailable data explicit. Do not silently coerce malformed
  identifiers, filters, statistics, or scores into valid-looking values.
- Keep user-facing copy in Catalan and follow the module-specific formatting
  conventions: Java uses four spaces; frontend JSX uses two spaces, single
  quotes, and no semicolons.

# Notes

- FEAT-011 and FEAT-012 provide the player search/detail and canonical-player
  foundations; this feature is limited to correcting remaining detail behavior
  and regressions rather than redesigning the player domain.
- The detail visualization exposes only line, bar, and connected scatter
  choices. Connected scatter is implemented as a single shared plot with line
  series spanning all selected seasons; it does not create one plot per season
  or fill the area below/between the series. Keep the chart height compact,
  align label sizing with the surrounding interface, and avoid heavy strokes.
  Preserve the SVG aspect ratio when sizing the chart responsively; do not use
  stretching that distorts the series or clips the season labels.
- Frontend lint, tests, and build pass. The full Maven reactor is currently
  blocked by eight unrelated existing import/consolidation test failures in
  `tt-data-league-import`.
