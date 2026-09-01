# Build Plan

1. **Resolve the canonical identity and compatibility contract.**
   - Confirm that club search results are identified by the canonical `Club`
     UUID rather than a source-specific `FederatedClub` UUID.
   - Define the source and season context returned when one canonical club has
     multiple federated records.
   - Decide whether existing federated-ID URLs require a compatibility lookup
     or may return `404`.
   - Preserve existing filters, authentication, authorization, and approved
     response compatibility.

2. **Add canonical club search and detail application contracts.**
   - Add or update application queries and read models under
     `../../../../tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/application/club/find`.
   - Return one search result per canonical `Club` UUID.
   - Include the source or season context needed to interpret associated data.
   - Define canonical detail results with associated federated sources,
     season-specific teams and players, competitions, and match summaries.
   - Keep source-dependent import resolution on `FederatedClub`.

3. **Implement repository and JPA support.**
   - Extend domain repository ports with deterministic canonical club search and
     detail operations.
   - Implement the operations in
     `tt-data-league-core-repository-jpa`, using explicit mappers and stable
     ordering.
   - Deduplicate federated records by canonical club identity without
     suppressing relevant source or season context.
   - Keep JPA entities out of application and REST response contracts.
   - Update
     `../../../../tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` only if
     persistence relationships, indexes, constraints, or table behavior change.

4. **Update the REST API.**
   - Update club controllers, application wiring, DTOs, and mappers under
     `tt-data-league-api-rest`.
   - Expose canonical club identity in search responses.
   - Resolve detail requests using canonical identity and ensure a result cannot
     load another club's details.
   - Use explicit validation and clear not-found behavior.
   - Preserve public routes and response field names unless the approved
     canonical-ID contract explicitly changes them.

5. **Update frontend search and navigation.**
   - Adapt the club API client and response normalization.
   - Update hooks, search pages, routes, and detail pages to use canonical
     identity.
   - Render source and season context for deduplicated canonical results.
   - Preserve loading, empty, error, cancellation, responsive, and existing
     filter behavior.
   - Handle malformed or incomplete payloads explicitly using existing frontend
     conventions.

6. **Add focused tests.**
   - Domain tests for canonical aggregation, deduplication, deterministic
     ordering, and source/season context.
   - Repository tests for multiple federated records linked to one canonical
     club, source filtering, canonical clubs without federated records, and
     persistence failures.
   - REST tests for canonical UUID resolution, stable serialization,
     validation, authorization, and missing-club behavior.
   - Frontend tests for canonical navigation, duplicate suppression, source
     context, malformed payloads, loading, empty, error, and cancellation
     states.
   - Add regression coverage for existing competition and season filters.

7. **Synchronize documentation and validate the reactor.**
   - Update `../../FEATURES.md` when the canonical response contract is
     approved, keeping the feature status and acceptance criteria synchronized.
   - Update API/OpenAPI documentation for the final response shape.
   - Confirm the FEAT-00008 schema migration and canonical club data are available
     before relying on the new queries; add a migration only if this feature
     introduces a schema change.
   - Run `mvn -pl tt-data-league-api-rest -am test` and then `mvn test`.
   - Run the existing frontend install, lint, test, and build commands if
     frontend contract changes are made.

# Implementation Guidelines

- Keep dependencies directed inward: domain code must not depend on Spring,
  JPA, or frontend code; REST must consume application contracts; frontend must
  consume HTTP DTOs.
- Keep canonical `Club` identity separate from source-scoped
  `FederatedClub` identity.
- Do not resolve clubs by an unscoped name where source identity affects
  matching.
- Do not add external identifiers to `Club` or `FederatedClub`.
- Prefer existing repository ports, read models, mappers, hooks, and UI state
  conventions before introducing new abstractions.
- Use stable, deterministic ordering for search results and nested context.
- Preserve duplicate federated records as context while returning only one
  canonical search result.
- Surface invalid identifiers, missing scope, authorization failures, and
  persistence errors explicitly. Do not silently select the first match or
  return success-shaped fallbacks.
- Do not modify import reconciliation behavior unless the canonical search
  contract exposes a directly related defect.

# Notes

- FEAT-00010 is marked `done` in `../../FEATURES.md`.
- The registry currently requires canonical `Club` UUIDs for search results,
  while FEAT-00008 documentation states that existing route and DTO identifiers
  remain `FederatedClub` UUIDs. This compatibility decision must be resolved
  before implementation.
- The FEAT-00008 schema migration is a prerequisite for canonical club data. This
  feature should not rename or redirect PlayerSeason, match, lineup, or other
  historical foreign-key columns.
- Canonical club search and detail responses use `Club` UUIDs and retain all
  attached federated records as source context. Legacy federated UUID detail
  URLs remain supported, while canonical UUIDs resolve to aggregated details.
- The compatibility decision is implemented: canonical IDs resolve aggregate
  details and federated IDs continue through the existing source-scoped path.
