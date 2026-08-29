# Build Plan

## 1. Confirm the remaining player history contract

1. Preserve the existing canonical-player search and detail contracts from
   FEAT-009 and FEAT-011, including UUID navigation and explicit source and
   season context.
2. Define the remaining read-model fields for source, season, competition,
   match date, opponent, result, score, and the three history metrics:
   matches played, win percentage, and average score.
3. Define explicit behavior for unlinked `FederatedPlayer` records. They must
   remain distinct and source-scoped, but must not produce a fabricated
   canonical-player detail URL.
4. Define explicit API behavior for malformed or unavailable statistics,
   invalid filters, unauthorized access, missing players, and failed reads.

## 2. Complete domain queries and persistence history reads

1. Inspect and extend the player detail application query and immutable read
   models in
   `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/application/player/find/`.
2. Add repository-port methods only where the required player, source, season,
   and competition scope is explicit. Reuse existing `PlayerSeason`, `Team`,
   `Match`, and `Lineup` ports and preserve their identity and references.
3. Implement deterministic JPA projections/queries in
   `tt-data-league-core-repository-jpa` for complete eligible history. Remove
   the current arbitrary `MAX_MATCHES` truncation while avoiding N+1 entity
   traversal.
4. Keep all-source and all-season aggregation source-scoped and stable under
   filtering. Do not add external identifiers to canonical or federated player
   entities.
5. Update
   `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` only if the
   persistence mapping or query-supporting schema changes.

## 3. Align REST contracts and error handling

1. Update
   `tt-data-league-api-rest/src/main/java/org/cttelsamicsterrassa/data/api/rest/player/PlayerController.java`
   and the related DTOs/read-model mappers to expose the complete match history
   and statistics without leaking persistence entities.
2. Serialize unavailable scores as an absent value for the mapper to render as
   an em dash, never as an invented numeric score.
3. Preserve backend authorization and add explicit responses for invalid
   identifiers, invalid source/season/competition combinations, unavailable
   statistics, unauthorized access, and not-found players.
4. Add focused REST and application tests for statistics serialization,
   source/season/competition filtering, unlinked records, complete aggregation,
   and failure responses.

## 4. Complete the frontend player detail visualization

1. Update
   `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx` and its existing
   API boundary/hooks to consume normalized detail data and preserve loading,
   empty, error, unauthorized, and not-found states.
2. Keep source, season, and competition filters URL-persisted and interdependent;
   every change must update both the plot and match-history table.
3. Add a keyboard-accessible chart-type selector with line, bar, stacked-bar,
   scatter, bubble, and heatmap options. Every type must use the same selected
   season and metric data.
4. Add an explicit legend, axis labels, units, and a textual alternative that
   identifies the selected chart type and reports its displayed values.
5. Complete the responsive match-history table with date, competition,
   opponent, result, and score columns. Render unavailable scores as an em
   dash and retain source and season context.
6. Keep user-facing copy in Catalan, use semantic controls with visible focus
   states, and preserve responsive behavior without introducing a second global
   state mechanism.

## 5. Verify end to end

1. Add focused domain and persistence tests for complete, deterministic,
   source-scoped history, all-source/all-season aggregation, unlinked records,
   and invalid statistics.
2. Add REST regression tests for read-model shape, filtering, serialization,
   authorization, not-found behavior, and explicit unavailable-statistics
   responses.
3. Add frontend coverage for chart-type keyboard interaction, legend/axes and
   textual fallback content, URL filter synchronization, plot/table updates,
   unavailable scores, and loading/empty/error states.
4. Run the frontend lint/build, affected Maven module tests, and the full
   reactor with `mvn test`. Manually verify duplicate names across sources,
   unlinked search results, direct and refreshed detail URLs, all-source and
   all-season views, and representative screen sizes.
5. Check the FEAT-012 acceptance criteria in
   `docs/sdd/FEATURES.md` only after implementation evidence exists; keep the
   feature `in-progress` until the remaining criteria are complete.

# Implementation Guidelines

- Keep dependencies directed inward: domain read models and repository ports
  remain framework-light, JPA remains an adapter, REST owns mapping and
  authorization wiring, and the frontend consumes HTTP DTOs only.
- Preserve canonical `Player`, source-scoped `FederatedPlayer`, and
  season-specific `PlayerSeason` identity. Do not retarget historical match or
  lineup references and do not add `externalId` fields to player entities.
- Scope every lookup and aggregation by the relevant source, season, and
  competition. Never resolve a player by an unscoped name when source identity
  matters.
- Prefer existing application queries, API request helpers, route metadata,
  CSS layers, and UI primitives. Do not add a dependency solely for charting or
  state management without an explicit contract decision.
- Surface invalid or unavailable data explicitly; do not silently coerce
  malformed statistics, scores, or filters into valid-looking values.

# Notes

- FEAT-009 and FEAT-011 are prerequisites and remain listed as dependencies in
  `docs/sdd/FEATURES.md`.
- The registry currently records the search/detail foundation and the initial
  visualization work as complete. The remaining scope is the enhanced chart
  selector and accessible metadata, complete match-history presentation,
  unlinked-record handling, untruncated aggregation, and explicit invalid-data
  behavior.
- Match history is returned without an arbitrary record cap. The detail view
  provides all required chart modes, accessible metadata, and a textual match
  history fallback while retaining source and season filters.
