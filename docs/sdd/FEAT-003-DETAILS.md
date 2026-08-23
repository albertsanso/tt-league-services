# Build Plan

## 1. Establish the club API and data contract

1. Preserve `GET /api/v1/club/search_in_name?name=...` as the compatible
   search endpoint. Validate and trim the query, reject terms shorter than two
   characters with a clear `400` response, return deterministic name ordering,
   and keep `source` visible in every result so same-named clubs from different
   federations are not ambiguous. Add an optional source filter only through an
   explicit request parameter.
2. Add a canonical authenticated detail endpoint,
   `GET /api/v1/club/{id}`, returning a dedicated `ClubDetailsDto` rather than
   changing the existing `ClubDto` shape. The response contains the club
   `id`, `name`, and `source`, season-specific team registrations, and
   match-derived competition summaries grouped by season. A missing UUID returns
   `404`; malformed UUIDs and invalid filters return `400`.
3. Define the detail payload with stable display-oriented fields:
   `teams[]` contains `id`, `name`, `source`, and `season`; `competitions[]`
   contains `name`, `season`, and the available match count/result totals.
   Do not expose persistence entities or source-system identifiers in the
   REST response.
4. Add an authenticated admin-only club update endpoint for the supported
   editable field, the club name, and map it to the existing
   `ModifyClubNameCommand`. Use explicit validation and return `404` for a
   missing club, `400` for an invalid name, and `403` for non-admin users.
   Keep this separate from read permission checks.

## 2. Add domain application queries and persistence adapters

1. Add a focused `FindClubDetailsQuery` and immutable application read models
   under `tt-data-league-core-domain/.../application/club/find`. Compose the
   existing `ClubRepository` result with all season-specific `Team` rows
   associated with the club and the competition summaries needed by the
   response.
2. Extend `TeamRepository` and `MatchRepository` with read methods that fetch
   registrations and matches by the canonical club/team UUIDs. Keep source and
   season filters explicit where they are part of the request; never resolve a
   club through an unscoped name during detail loading.
3. Implement the new methods in
   `tt-data-league-core-repository-jpa` with bounded, sorted queries and
   projections or lightweight mapping where possible. Avoid N+1 traversal of
   lazy `TeamJPA` and `MatchJPA` relationships, and preserve season-specific
   team identity and existing match/lineup references.
4. Reuse the current club mappers and command handlers. Do not add
   `externalId` fields to `Club`, `Player`, `ClubJPA`, or `PlayerJPA`, and do
   not add a club location column based on match `city` or `venue`; those are
   match-event data. No relational schema change is expected for this plan,
   so update `rfetm-datamodel.md` only if implementation changes columns,
   relationships, indexes, or constraints.

## 3. Wire REST authorization and error handling

1. Update the club controller and OpenAPI annotations for search, detail, and
   update contracts. Return explicit `400`, `403`, `404`, and `500` responses
   rather than converting missing data or failed queries into empty success
   responses.
2. Protect all club reads with `clubs:read`. Protect the update operation with
   the `ADMIN` role, not only the broader `clubs:write` permission, because the
   feature explicitly limits editing to administrators. Keep backend
   authorization authoritative even when the frontend hides the action.
3. Add focused tests for query validation, source disambiguation, details
   composition, missing clubs, admin/non-admin update authorization, and
   serialization of the stable DTOs. Add repository integration coverage for
   club-to-team and team-to-match filtering if the existing persistence test
   setup supports it.

## 4. Implement the frontend club search experience

1. Add `tt-data-league-frontend/src/api/clubs.js` and focused hooks such as
   `useClubSearch` and `useClubDetails`. Keep requests behind `apiRequest`,
   attach the session token, abort on unmount or query replacement, normalize
   the response at the API boundary, and surface non-success responses.
2. Replace `src/pages/ClubsSearchPage.jsx` with a search form driven by the
   URL query parameter (for example, `/clubs?q=terrassa`). Debounce requests,
   enforce the two-character minimum, encode the query, and render accessible
   loading, empty, error, retry, and result states. Show each result's club
   name and source and navigate by UUID rather than by name.
3. Add a lazy `ClubDetailPage` at `/clubs/:clubId`, centralize its route and
   breadcrumb metadata, and render the identity block, season registrations,
   competition summaries, loading state, not-found state, and retryable error
   state. Keep the layout responsive at the existing desktop, tablet, and
   mobile breakpoints and use Catalan copy and semantic headings.
4. Add reusable detail components only where they serve this feature, keeping
   page composition separate from API normalization. Do not use FEAT-001 mock
   data as a fallback for a failed club request.

## 5. Wire club actions and edit flow

1. Add detail actions for players and matches that navigate to
   `/jugadors?clubId={id}` and `/partits?clubId={id}`. Update those existing
   pages to consume and display the selected-club filter context without
   duplicating club identity logic; their broader search behavior remains
   owned by their respective features.
2. Add `/clubs/:clubId/edit` as a lazy route guarded by an admin-role
   boundary. Render a prefilled, validated club-name form, submit through the
   update API, show pending/success/failure states, and return to the detail
   view only after a confirmed successful response.
3. Hide or disable the edit action for non-admin users while preserving the
   detail view for every user with `clubs:read`. Handle a direct unauthorized
   URL with the existing forbidden state and rely on the API for final
   authorization.
4. Preserve existing lazy loading, `Suspense` fallback, catch-all routing,
   keyboard interaction, focus-visible styling, and mobile drawer behavior.

## 6. Verify the feature end to end

1. Add the minimum approved frontend test setup if none exists (a test script
   plus focused React/DOM test dependencies) and cover search debounce and
   cancellation, URL navigation, loading/empty/error states, detail rendering,
   action visibility, admin edit behavior, `401` session clearing, and
   responsive-accessible controls.
2. Run frontend `npm ci`, `npm run lint`, and `npm run build`, the affected
   Maven modules, and the full reactor with `mvn test`.
3. Manually verify direct search and detail URLs, duplicate names from
   different sources, two-character and invalid queries, no results, API
   failures, missing clubs, refresh with an expired session, `403` edit
   attempts, successful edits, keyboard navigation, screen-reader labels, and
   representative desktop/tablet/mobile layouts.

# Implementation Guidelines

- Treat `Club` as the season-independent identity `(UUID, source, name)`.
  Season-specific league participation belongs to `Team`; match competition,
  venue, and city remain match data.
- Keep dependencies inward: frontend code consumes HTTP DTOs only, REST maps
  to domain/application contracts, and domain code remains independent of
  Spring, JPA, and browser concerns.
- Follow the frontend module conventions in
  `tt-data-league-frontend/AGENTS.md`: JavaScript/JSX, two-space indentation,
  single quotes, no semicolons, Catalan copy, existing Context/auth helpers,
  and CSS layers already used by the shell.
- Keep route checks and hidden actions as UX behavior only. Every protected
  read and write must be enforced by Spring Security, with `401` and `403`
  remaining distinguishable to the client.
- Use UUIDs for detail and update navigation. Never construct links or lookup
  requests from a non-unique club name, and never add external identifiers to
  the club or player entities.
- Keep the first version bounded to name/source search, club identity,
  season registrations, competition summaries, and club-name editing. Do not
  add club geography, player search implementation, match search
  implementation, pagination, or analytics unless a separate feature expands
  the contract.

# Notes

- FEAT-001 provides the shell, centralized routes, responsive primitives, and
  placeholder player/match pages. FEAT-002 provides the authenticated session,
  permission checks, and forbidden state.
- At planning time, the backend already exposed `ClubDto`, name search, UUID
  lookup, and the existing club-name command, but it did not expose a combined
  club detail read model or a club update REST endpoint.
- `Club` currently has no location field. Match `city` and `venue` must not be
  presented or persisted as club location; adding authoritative club
  geography requires a separate domain and schema decision.
- The feature added the minimum frontend component test setup and updated
  `package-lock.json` through npm.
- FEAT-003 implementation is complete across the REST API, domain/JPA adapters,
  and frontend routes and views. Frontend lint, tests, and build pass, and the
  affected backend Maven reactor passes.
- Full `mvn test` remains blocked by seven existing import fixture assertions
  that report zero stored records in `tt-data-league-import`; those failures
  are outside the FEAT-003 implementation scope.
