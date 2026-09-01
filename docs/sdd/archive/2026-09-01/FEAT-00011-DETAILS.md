# Build Plan

## 1. Confirm the player search and detail contract

1. Preserve or establish a compatible authenticated player-name search endpoint
   with trimmed and validated queries, a documented minimum length, deterministic
   ordering, and source visibility in every result. Any source filter must be
   an explicit request parameter.
2. Add a canonical player detail endpoint addressed by Player UUID. Return a
   dedicated detail read model containing the canonical identity, source-scoped
   federated records, season registrations, associated clubs, competition
   references, and bounded match summaries.
3. Keep player-season registration identity, licences, seasons, UUIDs, lineup
   references, and match history intact. Do not expose JPA entities or source
   system identifiers in the REST response.
4. Define explicit `400`, `401`, `403`, `404`, and server-error behavior for
   malformed UUIDs, invalid filters, missing players, unauthorized requests,
   and failed reads.

## 2. Add domain queries and persistence adapters

1. Add focused player search and player-detail application queries and immutable
   read models under `tt-data-league-core-domain`. Compose canonical Player
   identity with explicitly scoped FederatedPlayer and PlayerSeason data.
2. Extend repository ports only with methods whose player, source, season, club,
   and competition scope is explicit. Reuse existing PlayerSeason, Team, Match,
   Lineup, and related ports where they already provide the required data.
3. Implement bounded, deterministic JPA queries and lightweight projections in
   `tt-data-league-core-repository-jpa`. Avoid N+1 traversal of player seasons,
   teams, matches, and lineups.
4. Preserve the existing schema and relationships unless implementation proves a
   schema change necessary. If columns, relationships, indexes, constraints, or
   table behavior change, update `docs/rfetm-datamodel.md` and provide matching
   persistence coverage.

## 3. Wire the REST API and authorization

1. Add player search/detail DTOs, controller methods, OpenAPI responses, and
   mappings in `tt-data-league-api-rest` without changing existing player
   registration semantics or response fields unintentionally.
2. Protect reads with the established player-read permission and keep backend
   authorization authoritative. Do not rely on hidden frontend navigation.
3. Add focused tests for query validation, source disambiguation, detail
   composition, season and competition scoping, missing players, serialization,
   and authorization/error responses.

## 4. Implement the frontend search experience

1. Add player API helpers and hooks behind the existing `apiRequest` boundary.
   Attach the session token, normalize responses at the API boundary, abort
   obsolete requests, and surface non-success responses.
2. Replace the placeholder Players search page with a URL-driven search form,
   debounced requests, accessible results, canonical UUID navigation, and
   explicit loading, empty, validation, error, retry, and unauthorized states.
3. Display each result's player name and source context so same-named records
   cannot be mistaken for one another. Support an explicit source filter when
   the backend contract provides it.

## 5. Implement the Player detail view

1. Add a lazy Player detail route addressed by UUID and centralized breadcrumb
   metadata. Preserve authentication, permission, suspense, and catch-all route
   behavior.
2. Render the identity header, federated/source context, season registrations,
   associated clubs, competition references, match summaries, and links to
   relevant filtered club or match views.
3. Represent source, season, and competition selection in URL parameters.
   Derive available options from the complete detail response, clear
   incompatible selections, and keep filtering explicitly scoped.
4. Provide visible loading, empty, not-found, retryable error, and unauthorized
   states. Keep all user-facing copy in Catalan and use semantic, responsive
   controls with visible focus behavior.

## 6. Verify the feature end to end

1. Add frontend tests for debounce and cancellation, URL synchronization,
   source/season/competition interdependency, UUID navigation, normalized API
   payloads, loading/empty/error/not-found states, and accessible responsive
   controls.
2. Add domain, JPA, and REST tests for canonical-player composition, explicit
   source and season scoping, registration preservation, competition and match
   summaries, malformed filters, serialization, and authorization.
3. Run the frontend lint/build and affected Maven module tests, followed by the
   full reactor with `mvn test`. Manually verify duplicate names across sources,
   direct and refreshed URLs, incompatible filters, expired sessions, missing
   players, keyboard navigation, and representative screen sizes.

# Implementation Guidelines

- Treat Player as the canonical season-independent identity and FederatedPlayer
  as the source-dependent representation. Keep PlayerSeason as the
  season-specific registration identity.
- Never resolve a player by an unscoped name when source identity matters. Use
  UUIDs for detail navigation and explicit source/season scope for registrations,
  clubs, competitions, matches, and lineups.
- Keep dependencies directed inward: domain contracts remain framework-light,
  REST performs mapping and authorization wiring, persistence remains an adapter,
  and frontend code consumes HTTP DTOs only.
- Do not add `externalId` fields to Player, FederatedPlayer, Club, or related
  entities. Source-system identifiers belong to import or registration identity
  handling.
- Reuse the existing authentication context, API request helper, route metadata,
  lazy loading, CSS tokens, and frontend test setup. Do not add another global
  state mechanism or fallback mock data for failed requests.
- Preserve existing match history, lineup references, club associations, and
  season registration data. Any schema change requires synchronized schema
  documentation and migration/integration coverage.

# Notes

- This feature follows the search/detail and filter/navigation patterns delivered
  by FEAT-00003 and FEAT-00004, adapting them to canonical Player and
  source-scoped FederatedPlayer/PlayerSeason data.
- FEAT-00009 provides the canonical Player entity and is a prerequisite for
  UUID-based player detail navigation.
- The exact player match-summary and roster fields should be confirmed against
  the existing domain and persistence contracts before implementation begins.
