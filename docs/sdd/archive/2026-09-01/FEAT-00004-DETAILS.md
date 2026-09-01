# Build Plan

## 1. Confirm the view and navigation contract

1. Use `../../../frontend/club-detail-view-mockup-spec.md` as the visual source of
   truth for the redesigned Club detail page: the identity header, source
   label, administrator edit action, `Jugadors` and `Partits` tabs, linked
   season and competition selectors, and competition summary cards.
2. Keep the existing canonical Club detail URL,
   `/clubs/:clubId`, and represent the selected tab and filters in query
   parameters. Use stable names such as `view`, `source`, `season`, and
   `competition` so refreshes and browser navigation reproduce the same state.
   Club search links must initialize `source=all` and `season=all`, and omit
   `competition`, so the detail view starts with all sources, all seasons, and
   all competitions selected.
3. Add a canonical Competition detail route that contains the club UUID,
   season, and URL-encoded competition name. Preserve the originating Club
   detail query string in the back link rather than relying only on browser
   history.
4. Treat `clubId + season + competition name` as the competition identity for
   this feature. Do not add an external identifier or a new persistence entity
   for competitions unless the existing match model proves insufficient.

## 2. Define the read contracts for both tabs

1. Reuse the existing authenticated `GET /api/v1/club/{id}` response for Club
   identity, season registrations, and competition summaries. The current
   `ClubDetailsDto` already exposes the required summary fields:
   competition name, season, match count, and win/draw/loss totals.
2. Add a focused competition-detail read model and REST endpoint for a
   selected club, season, and competition. The response must include the
   competition identity and the match rows needed by the dedicated detail
   view; malformed UUIDs or filters return `400`, missing club/competition
   data returns `404`, and authorization remains `clubs:read`/`matches:read`
   according to the approved permission matrix.
3. Define the `Jugadors` tab contract before implementation. It should return
   the club’s player roster for the selected season, with player identity and
   season-registration data only. Do not infer club membership from an
   unscoped player or club name.
4. Keep source and season scoping explicit in every query. Map domain read
   models to REST DTOs and never expose JPA entities, persistence joins, or
   source-system identifiers to the frontend.

## 3. Extend domain and persistence read paths

1. In `tt-data-league-core-domain`, add focused competition-detail and roster
   queries/read models under the existing `application/.../find` packages.
   Reuse `ClubRepository`, `TeamRepository`, `MatchRepository`, and
   `PlayerSeasonRepository` ports where they provide the required data; extend
   ports only with methods whose club, season, source, and competition scope is
   explicit.
2. Implement the new read methods in
   `tt-data-league-core-repository-jpa` with bounded, deterministic queries.
   Avoid N+1 traversal of teams, matches, lineups, and player seasons, and
   preserve existing season-specific team/player identity and match history.
3. Add REST DTOs, controller methods, OpenAPI responses, and focused tests in
   `tt-data-league-api-rest`. Preserve the existing Club detail and edit
   contracts; do not change `Club` or `Player` identity or add `externalId`
   fields.
4. Update
   `../../../../tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` only if the
   implementation changes schema columns, relationships, indexes, constraints,
   or table behavior. Read-only projections should require no schema change.

## 4. Implement the Club detail page redesign

1. Update `../../../../tt-data-league-frontend/src/pages/ClubDetailPage.jsx` into a
   semantic page with:
   - an identity header containing the club name, source, and admin-only edit
     link;
   - an accessible tablist for `Jugadors` and `Partits`;
   - source, season, and competition selects;
   - the active tab content, competition summaries, and player season and
     competition references;
   - explicit loading, empty, error, retry, and not-found states.
2. Derive available sources and seasons from the complete detail response and
   available competitions from the selected source and season. Include
   `Totes les fonts` and `Totes les temporades` as explicit all-options choices.
   Selecting a source must restrict the available seasons, competitions,
   players, and match summaries to that import source. Each player-season
   registration must expose its season and related competition names so
   selecting a competition also filters the Players tab. Selecting a season must clear or
   normalize an incompatible competition; selecting a competition must
   restrict the season choices to seasons where it is present and restrict the
   competition list to the selected source and season.
3. Apply filters without losing the complete source data needed to recalculate
   options. Preserve selected values in the URL, use stable keys for
   competition cards, and show a clear empty state when no filtered results
   remain.
4. Make each competition summary a keyboard-accessible link to the dedicated
   Competition detail route, carrying the selected club, season, competition,
   and return filters.
5. Remove `Equips i inscripcions` from the `Partits` view. Keep team
   registration data available only where the approved `Jugadors` view
   contract requires it.

## 5. Add the Competition detail view and preserve return state

1. Add a lazy `CompetitionDetailPage` under
   `../../../../tt-data-league-frontend/src/pages`, the route helper and breadcrumb
   metadata in `src/config/routes.js`, and the route composition in
   `src/App.jsx`. Keep the existing auth, permission, suspense, and catch-all
   behavior.
2. Add an API helper and hook under `src/api/` and `src/hooks/` for the
   competition-detail contract. Normalize the response at the API boundary,
   attach the session token, abort requests on unmount, and surface failures
   instead of returning mock data.
3. Render the competition identity, season, match list/results, loading,
   empty, not-found, and retryable error states. Provide a visible, keyboard
   accessible back link to the Club detail URL with the original `view`,
   `season`, and `competition` parameters intact.
4. Keep edit visibility and backend enforcement unchanged: only administrators
   see the edit action, while authorized users can read the club and permitted
   competition data.

## 6. Apply the visual, responsive, and accessibility work

1. Update `../../../../tt-data-league-frontend/src/app.css` using the existing design
   tokens and component layers. Add styles for the header, tab states,
   selectors, summary cards, links, match/roster content, and empty/error
   states without changing unrelated pages.
2. Preserve the existing desktop, tablet, and mobile breakpoints. On narrow
   screens, stack header actions and card metadata while keeping controls
   usable without horizontal scrolling.
3. Use semantic headings, `aria-selected`, `aria-controls`, labelled selects,
   visible focus styles, and status/error announcements. Do not implement tabs
   or cards as clickable non-interactive elements.
4. Keep all new user-facing copy in Catalan and reuse existing icons and
   typography before introducing new dependencies.

## 7. Verify the feature end to end

1. Add frontend tests alongside the existing Vitest setup for:
   - tab and filter URL synchronization;
   - season/competition interdependency and incompatible-value reset;
   - summary-card navigation and preserved return filters;
   - normalized API payloads, cancellation, loading, empty, error, and
     unauthorized states;
   - admin edit visibility and keyboard-accessible tab/select/card behavior.
2. Add domain, JPA, and REST tests for competition scoping, roster scoping,
   result totals, missing data, malformed filters, serialization, and
   authorization. Include integration coverage for teams, matches, lineups,
   and player seasons where the existing persistence fixtures support it.
3. Run `npm ci`, `npm run lint`, and `npm run build` from
   `tt-data-league-frontend`, then run the affected Maven module tests and the
   full reactor with `mvn test`.
4. Manually verify direct and refreshed URLs, duplicate competition names in
   different seasons, filter changes in both directions, empty/error states,
   competition back navigation, expired sessions, admin/non-admin views,
   keyboard navigation, screen-reader labels, and representative screen
   sizes.

# Implementation Guidelines

- Keep the frontend dependent on HTTP DTOs only. Domain code remains
  framework-light, REST performs mapping and authorization wiring, and JPA
  remains an adapter.
- Reuse the existing `useClubDetails`, `apiRequest`, auth context, route
  metadata, lazy route loading, and CSS tokens. Do not add another global state
  mechanism.
- The complete Club detail response is the source for filter options; URL
  parameters are navigation state, not a second server-side source of truth.
- Never resolve a club or player by an unscoped name. Use UUIDs for club
  navigation and explicit season/source scope for roster and match queries.
- Preserve the existing Club edit endpoint and behavior. FEAT-00004 changes its
  presentation and return navigation, not its authorization or domain
  semantics.
- No schema migration is expected for read-only competition summaries or
  details. Any schema change requires an update to the persistence data-model
  documentation and corresponding migration/integration coverage.

# Notes

- FEAT-00004 depends on FEAT-00003, which already provides the Club search/detail
  API, frontend API normalization, `ClubDetailPage`, admin edit flow, route
  guards, and the frontend test/build setup.
- The current `ClubDetailPage` still renders action links and the
  `Equips i inscripcions` section rather than the mockup’s tabs and filters.
  Its existing competition summaries are not links and there is no
  competition-detail route or API.
- The current backend detail read model can calculate competition summaries
  from matches, but no dedicated competition-detail or club-roster response is
  exposed. Confirm the exact roster and match row fields before moving the
  feature from `planned` to `ready`.
- `../../FEATURES.md` remains authoritative for this feature’s status and acceptance
  criteria. Keep FEAT-00004 under `Backlog` while the read contracts and the
  roster/match tab scope remain unresolved; move it to `ready` only after the
  contract is approved.
- The scoped roster and competition-detail read contracts are now implemented
  without changing Club or Player identity or the persistence schema. The
  feature registry records FEAT-00004 as shipped.
- The Club detail response is source-scoped by the selected Club identity. The
  source filter still exposes an explicit all-sources choice and derives its
  available values from the complete normalized response, so source-scoped
  filtering remains consistent with season and competition interdependencies.
