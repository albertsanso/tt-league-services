# Build Plan

1. Resolve and record the remaining product decisions before implementation:
   treat source, season, and competition as mandatory filters; make date
   bounds inclusive; define player-name matching as case-insensitive and
   source-scoped; use a deterministic newest-first ordering; and agree on the
   complete set of detail fields available from the persisted match model.
2. Define the domain read contracts under
   `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/match`:
   immutable search criteria, paginated search results, match detail read
   models, and repository/application queries. Keep source and season identity
   explicit and do not add external identifiers to `FederatedClub` or
   `FederatedPlayer`.
3. Extend the match repository port and implement the persistence adapter in
   `tt-data-league-core-repository-jpa`. Add source-scoped predicates for
   season, competition, inclusive date range, player location, and player
   name; add deterministic pagination; and load the related teams, lineups,
   player-season records, games, doubles pairs, venue, referee, and protest
   information without introducing N+1 queries.
4. Add focused domain and JPA tests for filter validation, mandatory-filter
   behavior, source scoping, date boundaries, player matching, ordering,
   pagination, empty results, and complete match-detail assembly. Update
   `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` if the
   persistence mapping or fetch behavior changes.
5. Add authenticated REST search and detail endpoints in
   `tt-data-league-api-rest`, using the existing query-bus/application
   patterns, DTO mappers, validation, OpenAPI annotations, and the existing
   `matches:read` permission. Return explicit client errors for malformed
   filters and preserve not-found and unauthorized semantics.
6. Replace the placeholder
   `tt-data-league-frontend/src/pages/MatchesSearchPage.jsx` with the filter
   and result workflow. Add URL-backed filter state, dependent metadata
   loading, abort-aware requests, explicit loading/empty/error/unauthorized
   states, ten-result initial pages, and a "Load more" action that adds ten
   matches at a time.
7. Add the match detail page and route, including navigation from a result,
   back navigation, all available match/player/lineup/game/score metadata, and
   responsive accessible presentation. Add Catalan, Spanish, and English
   translations for new labels and states, following the existing frontend
   localization conventions.
8. Add focused REST and frontend tests for the search workflow, dependent
   filters, request cancellation/stale-result protection, pagination,
   navigation, detail rendering, and all user-visible states. Run the
   affected Maven module tests and the existing frontend test/lint/build
   commands.

# Implementation Guidelines

- Keep dependencies directed inward: domain code must not depend on Spring or
  JPA, import code must not depend on persistence adapters, and runtime code
  must wire the public APIs rather than duplicate match-search logic.
- Reuse existing repository ports, query buses, mappers, API error handling,
  frontend API helpers, route metadata, and loading/error components.
- Scope every lookup by federation/source and, where applicable, season
  registration. Never resolve a club or player by an unscoped name.
- Prefer batch loading or explicit fetch plans for paginated match results;
  do not hide persistence failures behind empty or partial success responses.
- Preserve the existing authentication boundary and require `matches:read`
  for both search and detail operations.
- Keep search result models lightweight and reserve complete related data for
  the detail request where possible.

# Notes

- Product decisions approved for implementation: source, season, and
  competition are mandatory; date bounds are inclusive; player-name matching
  is case-insensitive and source-scoped; results are deterministic newest-first;
  and detail responses expose all complete fields available in the persisted
  match model.
- Implementation is finalized and the feature is `done` following explicit user
  approval on 2026-08-30.
- GraphQL is out of scope unless explicitly added to the feature requirements;
  the initial API surface is REST.
- The frontend match search page follows the documented mockup layout, with
  compact first-row filters, Home/Away location controls, and the player-name
  field below; the layout wraps responsively on smaller screens.
- Match filter metadata reads seasons and competitions directly from the
  source-scoped match inventory, so the season selector is populated before a
  season is selected.
