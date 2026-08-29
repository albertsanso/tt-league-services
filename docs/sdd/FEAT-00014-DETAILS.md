# Build Plan

## 1. Confirm the Player detail content and navigation contract

1. Preserve the existing canonical-player route, `/players/:playerId`, and the
   player detail selector area containing source, season, and competition
   filters. These filters remain URL-persisted and interdependent; no tab may
   maintain an independent copy of filter state.
2. Add a `view` query parameter for the player detail content area. Define
   `statistics` as the default and valid values `statistics`, `matches`, and
   `opponents`. Normalize a missing or invalid value to `statistics` with a
   replace navigation so direct links, refreshes, and browser navigation retain
   a stable selected tab.
3. Keep the identity header and selector area above the tablist. Render the
   three content views below it in this order: `Estadístiques`, `Partits`, and
   `Anàlisi d'oponents`. Preserve the existing federated registrations, season
   registrations, related clubs, and competition context as a separate related
   data section rather than dropping it when the history views are tabbed.
4. Define "opponent" as the opposing **team** for this feature. The current
   player-detail response describes team matches, not head-to-head player
   games. Do not label or represent it as an individual opposing player unless
   a later API contract provides that identity.

## 2. Preserve and complete the player-detail read contract

1. Review the framework-light player-detail read models under
   `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/application/player/find/dto/`
   and the `FindPlayerDetailsQueryHandler` to confirm each match carries the
   source, season, competition, player result, scores, player team, and
   opposing team needed by all three tabs.
2. The current `PlayerMatchReadModel` exposes both match teams and a
   player-relative result but not the player's participating team. Add a
   stable, explicit player-team or opponent-team field only if required to
   determine the opposing team correctly for wins, losses, and draws. Derive
   it from the matched lineup's team while retaining the existing source,
   season, match, and lineup identities.
3. Map any approved read-model addition through
   `tt-data-league-api-rest/src/main/java/org/cttelsamicsterrassa/data/api/rest/player/PlayerDetailsDto.java`
   and normalize it strictly in
   `tt-data-league-frontend/src/api/players.js`. Invalid or absent data must
   surface through the existing API error state instead of inventing an
   opponent.
4. Keep the existing `GET /api/v1/player/{id}` endpoint and authorization
   behavior. The client derives tab-specific filtered rows and aggregate
   display values from its normalized complete detail response; do not add an
   endpoint or persistence schema for a presentation-only grouping.

## 3. Implement the Player detail tabbed layout

1. Refactor `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx` into a
   page shell with an identity header, a shared selector area, an accessible
   tablist, one labelled tabpanel, and the existing related-data sections.
   Reuse the complete normalized response as the sole input for the filter
   options and all tab panels.
2. Keep `HistorySection` as the `Estadístiques` panel. It retains the approved
   line, bar, and connected-scatter selector, plot, legend, and statistics
   table. Move the match-history table out of this panel so it does not appear
   redundantly under the statistics view.
3. Add the `Partits` panel with a responsive semantic table containing date,
   competition, opponent, result, and score. Continue to include source and
   season context where the all-source or all-season filters can produce mixed
   rows. Format unavailable scores as an em dash and use the player-relative
   result labels already established by the page.
4. Add the `Anàlisi d'oponents` panel. Group the same filtered match rows by
   explicit opposing-team name and display a deterministic table with opponent,
   matches played, wins, draws, losses, and win percentage. Count draws
   separately; calculate win percentage from decided matches only and render an
   em dash when there are no wins or losses. Sort opponents predictably by
   case-insensitive name with a stable secondary key.
5. Apply source, season, and competition filters before rendering statistics,
   matches, or opponent aggregates. Changing a source continues to reset
   incompatible season and competition values; changing a season continues to
   reset incompatible competition values. Changing tabs does not alter filters
   or chart selection.

## 4. Apply responsive and accessibility behavior

1. Extend `tt-data-league-frontend/src/app.css` using the existing
   `club-controls`, `club-tabs`, `club-tab`, `club-filters`, and table layers
   where they match the Player detail layout. Add focused Player-specific
   selectors only when the shared styles cannot express the required behavior.
2. Use `<button role="tab">` controls in a labelled `tablist`, synchronized
   `aria-selected`, `aria-controls`, and `role="tabpanel"` attributes, with
   visible focus states. Keyboard activation must work through native buttons;
   do not create clickable non-interactive elements.
3. Preserve usable small-screen behavior: stack shared controls at the existing
   breakpoint, keep tab labels operable without clipping, and wrap tables in
   the existing horizontal-scroll container rather than compressing or hiding
   columns.
4. Keep all new copy in Catalan and retain explicit loading, empty, not-found,
   unauthorized, retry, and malformed-response states. Each empty tab must say
   which filtered view has no data.

## 5. Add regression coverage and validate

1. Add `PlayerDetailPage` tests using the existing Vitest and Testing Library
   patterns in `tt-data-league-frontend/src/pages/`. Cover default and
   URL-selected tabs, invalid-tab normalization, keyboard-accessible tab
   selection, and preservation of source, season, competition, and chart query
   parameters across tab changes.
2. Cover each panel with mixed-source and mixed-season fixture rows: statistics
   remains in its panel, match rows expose the required columns and unavailable
   scores, and opponent totals correctly distinguish wins, draws, losses, and
   no-decided-match percentages.
3. If the match read contract is extended, add focused domain and REST tests
   for player-team/opponent resolution, including draw results, and frontend
   normalization tests for malformed values.
4. Run `npm ci`, `npm test`, `npm run lint`, and `npm run build` from
   `tt-data-league-frontend`. Run affected Maven module tests and `mvn test`
   from the repository root if domain or REST code changes.
5. Manually verify a direct and refreshed tab URL, tab switching with each
   filter combination, all-source/all-season mixed tables, a draw-only
   opponent row, an unavailable score, keyboard navigation, and mobile,
   tablet, and desktop layouts.

# Implementation Guidelines

- Keep the frontend dependent on normalized HTTP DTOs. Domain code remains
  framework-light, REST owns mapping and authorization, and JPA remains a
  persistence adapter.
- Preserve canonical `Player`, source-scoped `FederatedPlayer`, and
  season-specific `PlayerSeason` identities. Do not add external identifiers,
  merge records by unscoped name, or retarget historical match or lineup
  references.
- Reuse `usePlayerDetails`, `apiRequest`, route helpers, existing CSS tokens,
  and the native React Router query-state APIs. Do not introduce a global state
  store, a chart library, or a second player-detail endpoint for this layout.
- Derive every tab from the same explicitly source-, season-, and
  competition-filtered records. Do not let an all-source or all-season view
  mix unscoped player records.
- Preserve the previously shipped chart contract: only line, bar, and
  connected-scatter visualizations; accessible legend and textual statistics;
  proportional responsive SVG behavior.
- Follow frontend conventions: React JSX with two-space indentation, single
  quotes, no semicolons, semantic controls, visible focus styles, and Catalan
  user-facing text.

# Notes

- FEAT-00011, FEAT-00012, and FEAT-00013 provide the canonical player detail,
  source/season/competition filtering, and statistics visualization foundation
  for this layout work.
- This feature introduces presentation tabs, not a new player identity,
  navigation route, persistence model, or default import behavior.
- The current match rows expose home and away team names but do not explicitly
  identify which one is the player's team. The implementation must establish
  this relationship before opponent analysis is marked complete, especially to
  handle drawn matches without guessing.
- Implemented with a source- and season-scoped `playerTeam` match field derived
  from the player's lineup. The REST DTO and strict frontend normalization carry
  that field to the player detail tabs.
- Frontend tests, linting, and production build pass, as does the focused
  domain test. The full Maven reactor remains blocked by pre-existing import
  processor test failures and a club-controller type-cast failure.
